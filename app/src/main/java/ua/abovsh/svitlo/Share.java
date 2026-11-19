package ua.abovsh.svitlo;

import android.app.Activity;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.net.Uri;
import android.widget.Toast;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.time.LocalDate;
import java.util.List;

/**
 * "Поділитися": one day of a place as a picture, for a messenger the person picks. The picture
 * names the region and queue, not the place, and says where to get the app: no text goes with it.
 * The app sends nothing itself: the picture goes through Android's own share sheet.
 */
final class Share {
    static final String FILE = "schedule.png";
    private static final int W = 360;  // dp of the picture; drawn at 3x

    private Share() {}

    static void send(Activity a, Logic.Place p, LocalDate day) {
        try {
            File f = picture(a);
            File partial = new File(f.getParentFile(),"schedule.part");
            Bitmap bm = draw(a, p, day, Sync.now());
            try (OutputStream out = new FileOutputStream(partial)) {
                bm.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            bm.recycle();
            java.nio.file.Files.move(partial.toPath(),f.toPath(),java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                    java.nio.file.StandardCopyOption.ATOMIC_MOVE);
            Uri uri = ShareProvider.uri();
            Intent send = new Intent(Intent.ACTION_SEND)
                    .setType("image/png")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            send.setClipData(ClipData.newRawUri("", uri));
            a.startActivity(Intent.createChooser(send, "Поділитися графіком"));
        } catch (IOException | RuntimeException e) {
            Toast.makeText(a, "Не вдалося підготувати картинку", Toast.LENGTH_SHORT).show();
        }
    }

    /** The one file the provider serves, in the app's own cache. */
    static File picture(Context c) {
        File dir = new File(c.getCacheDir(), "share");
        if (!dir.isDirectory() && !dir.mkdirs()) return new File(c.getCacheDir(), FILE);
        return new File(dir, FILE);
    }

    /**
     * One day of one place on a picture 1080 px wide, as compact as the main screen: the queue and its
     * region on one line, the day and its light, the grid, the outages in one row, and a footer.
     */
    static Bitmap draw(Context c, Logic.Place p, LocalDate day, long now) {
        Ui.Pal pal = Ui.pal(c);
        int w = Math.round(W * Sheet.S);
        Bitmap tall = Bitmap.createBitmap(w, Math.round(420 * Sheet.S), Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(tall);
        cv.drawColor(pal.card);
        Sheet sh = new Sheet(c, new Brush(pal, pal.card).on(cv), w);
        float s = Sheet.S;
        Logic.Data d = Store.data(c, p.region);
        Logic.Region r = Logic.region(p.region);

        String queue = "Черга " + Logic.queue(p.group);
        String region = r == null ? "" : r.name;
        Paint queueInk = sh.text(Ui.display(c), 17, pal.text);
        Paint regionInk = sh.text(Ui.body(c), 13, pal.muted);
        float y = sh.pair(queue, queueInk, region, regionInk, false, 14 * s);
        // the date in words: the picture is read later, when "today" is another day
        String date = Ui.date(day);
        boolean today = day.equals(Logic.today(now));
        // the yellow frame of now means something to whoever gets the picture later only with its time
        String stamp = today ? "станом на " + String.format(java.util.Locale.ROOT, "%02d:%02d", Logic.z(now).getHour(), Logic.z(now).getMinute()) : "";
        boolean stampAbove = false;
        if (today) {
            Paint ink = sh.text(Ui.bold(c), 12, pal.muted);
            float used = sh.left + queueInk.measureText(queue) + (region.isEmpty() ? 0 : 8 * s + regionInk.measureText(region));
            if (y < 14 * s + 18 * s && used + 10 * s + ink.measureText(stamp) <= sh.right) {
                cv.drawText(stamp, sh.right - ink.measureText(stamp), 14 * s + 17 * s * 0.8f, ink);
                stampAbove = true;
            }
        }
        int cur = today ? Logic.index(day, now) : -1;
        float nowRel = today ? cur + (Logic.z(now).getMinute() % 30) / 30f : -1;
        y = sh.day(Character.toUpperCase(date.charAt(0)) + date.substring(1), Logic.day(d, p.group, day), cur, nowRel, y + 10 * s);
        y = sh.footer(y + 10 * s);
        if (today && !stampAbove) {  // a long region name took the room beside the queue
            Paint ink = sh.text(Ui.bold(c), 12, pal.muted);
            cv.drawText(stamp, sh.left, y + 6 * s + 12 * s * 0.8f, ink);
            y += 6 * s + 12 * s;
        }
        // drawn on a tall sheet, then cut to what was used
        Bitmap out = Bitmap.createBitmap(tall, 0, 0, w, Math.min(tall.getHeight(), Math.round(y + 12 * s)));
        tall.recycle();
        return out;
    }

    /** The picture being drawn: its canvas and margins, at 3 pixels to a dp. */
    private static final class Sheet {
        static final float S = 3f;
        final Context c;
        final Brush b;
        final Ui.Pal pal;
        final float left;
        final float right;

        Sheet(Context c, Brush b, int width) {
            this.c = c;
            this.b = b;
            this.pal = b.pal;
            left = 16 * S;
            right = width - 16 * S;
        }

        /**
         * Two texts on one line, the second after the first or at the right edge; when they do not fit,
         * the second goes under the first. Returns the y under them.
         */
        float pair(String first, Paint fp, String second, Paint sp, boolean atRight, float top) {
            float fh = fp.getTextSize();
            b.cv.drawText(first, left, top + fh * 0.8f, fp);
            if (second.isEmpty()) return top + fh;
            float gap = 8 * S;
            float sw = sp.measureText(second);
            float fw = fp.measureText(first);
            if (left + fw + gap + sw <= right) {
                float x = atRight ? right - sw : left + fw + gap;
                b.cv.drawText(second, x, top + fh * 0.8f, sp);
                return top + fh;
            }
            float y = top + fh + 4 * S;
            b.cv.drawText(second, left, y + sp.getTextSize() * 0.8f, sp);
            return y + sp.getTextSize();
        }

        /** A day: its title and light, the grid, the outages in one row. */
        float day(String title, String slots, int cur, float nowRel, float top) {
            Paint head = text(Ui.heavy(c), 15, pal.text);
            if (slots == null) return pair(title, head, "графік ще не опубліковано", text(Ui.bold(c), 14, pal.muted), true, top);
            float y = pair(title, head, Logic.daySummaries(slots).get(0), text(Ui.heavy(c), 14, pal.onText), true, top);
            RectF cells = new RectF(left, y + 8 * S, right, y + 8 * S + 55 * S);
            BarView.grid(b, slots, nowRel, cells, text(Ui.heavy(c), 14, pal.text), -1, -1);
            return tray(Logic.outages(slots, cur), cells.bottom + 8 * S);
        }

        /**
         * The outages on a tray of their own under the grid, «Без світла» at its left and up to three
         * a row, so none is ever hidden: over ones dark as the hours gone by, the one going on in the
         * yellow frame of now, the coming ones in plain red.
         */
        private float tray(List<Logic.Outage> list, float top) {
            if (list.isEmpty()) return top - 8 * S;
            float pad = 6 * S;
            float gap = 5 * S;
            Paint head = text(Ui.heavy(c), 11.5f, pal.muted);
            String[] words = {"Без", "світла"};
            float headW = Math.max(head.measureText(words[0]), head.measureText(words[1]));
            float x0 = left + pad + 3 * S + headW + gap + 2 * S;
            float room = right - pad - x0;
            float size = 12.5f;
            Paint chip = text(Ui.heavy(c), size, pal.ink);
            float widest = 0;
            for (Logic.Outage o : list) widest = Math.max(widest, chip.measureText(Logic.shareChip(o)));
            int columns = Logic.trayColumns(widest + 12 * S, room, gap, 3);
            while (size > 10 && (widest + 8 * S) * columns + gap * (columns - 1) > room) {
                size -= .5f;
                chip = text(Ui.heavy(c), size, pal.ink);
                widest = 0;
                for (Logic.Outage o : list) widest = Math.max(widest, chip.measureText(Logic.shareChip(o)));
            }
            float cw = (room - gap * (columns - 1)) / columns;
            float ch = (size + 13) * S;
            int rows = (list.size() + columns - 1) / columns;
            float bottom = top + pad * 2 + rows * ch + (rows - 1) * gap;
            b.r.set(left, top, right, bottom);
            b.p.setStyle(Paint.Style.FILL);
            b.p.setColor(pal.raised);
            b.cv.drawRoundRect(b.r, 12 * S, 12 * S, b.p);
            float firstMid = top + pad + ch / 2;
            float line = head.getTextSize() * 1.1f;
            b.cv.drawText(words[0], left + pad + 3 * S, firstMid - line / 2 + head.getTextSize() * .36f, head);
            b.cv.drawText(words[1], left + pad + 3 * S, firstMid + line / 2 + head.getTextSize() * .36f, head);
            chip.setTextAlign(Paint.Align.CENTER);
            for (int i = 0; i < list.size(); i++) {
                Logic.Outage o = list.get(i);
                float x = x0 + (i % columns) * (cw + gap);
                float y = top + pad + (i / columns) * (ch + gap);
                b.r.set(x, y, x + cw, y + ch);
                b.p.setStyle(Paint.Style.FILL);
                b.p.setColor(chipColour(o));
                b.cv.drawRoundRect(b.r, 9 * S, 9 * S, b.p);
                if (o.when == Logic.Outage.NOW) {
                    float stroke = 2.5f * S;
                    b.p.setStyle(Paint.Style.STROKE);
                    b.p.setStrokeWidth(stroke);
                    b.p.setColor(Ui.Pal.MARKER);
                    b.r.inset(stroke / 2, stroke / 2);
                    b.cv.drawRoundRect(b.r, 9 * S - stroke / 2, 9 * S - stroke / 2, b.p);
                    b.p.setStyle(Paint.Style.FILL);
                }
                chip.setColor(o.maybe && o.when != Logic.Outage.PAST ? pal.accentInk : (o.when == Logic.Outage.PAST ? 0xFFE9DCDF : pal.ink));
                b.cv.drawText(Logic.shareChip(o), x + cw / 2, y + ch / 2 + chip.getTextSize() * .36f, chip);
            }
            return bottom;
        }

        /** As the grid draws them: a past run under the same veil, the rest in the colour of its state. */
        private int chipColour(Logic.Outage o) {
            int fill = o.maybe ? pal.maybe : pal.off;
            return o.when == Logic.Outage.PAST ? Ui.blend(fill, Ui.fade(0xFF172428, BarView.PAST_VEIL)) : fill;
        }

        /** "Графік обленерго" and where the app is, on one line, smaller when the picture is narrow. */
        float footer(float y) {
            String what = "Графік обленерго";
            float size = 12;
            Paint a = text(Ui.body(c), size, pal.dim);
            Paint sig = text(Ui.heavy(c), size, pal.accentText);
            while (size > 9 && a.measureText(what) + 10 * S + sig.measureText(Logic.SHARE_SIGNATURE) > right - left) {
                size -= 0.25f;
                a = text(Ui.body(c), size, pal.dim);
                sig = text(Ui.heavy(c), size, pal.accentText);
            }
            b.cv.drawText(what, left, y + a.getTextSize() * 0.8f, a);
            b.cv.drawText(Logic.SHARE_SIGNATURE, right - sig.measureText(Logic.SHARE_SIGNATURE), y + sig.getTextSize() * 0.8f, sig);
            return y + size * S;
        }

        /** A paint for text of `sizeDp`. */
        Paint text(android.graphics.Typeface tf, float sizeDp, int colour) {
            Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
            p.setTypeface(tf);
            p.setTextSize(sizeDp * S);
            p.setColor(colour);
            return p;
        }
    }
}
