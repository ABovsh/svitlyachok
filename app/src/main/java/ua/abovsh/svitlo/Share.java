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

        float y = sh.pair("Черга " + Logic.queue(p.group), sh.text(Ui.display(c), 17, pal.text),
                r == null ? "" : r.name, sh.text(Ui.body(c), 13, pal.muted), false, 14 * s);
        // the date in words: the picture is read later, when "today" is another day
        String date = Ui.date(day);
        boolean today = day.equals(Logic.today(now));
        int cur = today ? Logic.index(day, now) : -1;
        float nowRel = today ? cur + (Logic.z(now).getMinute() % 30) / 30f : -1;
        y = sh.day(Character.toUpperCase(date.charAt(0)) + date.substring(1), Logic.day(d, p.group, day), cur, nowRel, y + 10 * s);
        y = sh.footer(y + 10 * s);
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
            return chips(Logic.outages(slots, cur), cells.bottom + 8 * S, cells.bottom);
        }

        /**
         * The outages in one row: the one going on now filled, past ones faded. The font gets smaller
         * to keep them on one line; what still does not fit is counted, "+2".
         */
        private float chips(List<Logic.Outage> list, float y, float none) {
            if (list.isEmpty()) return none;
            float size = 12;
            Paint chip = text(Ui.heavy(c), size, pal.text);
            while (size > 10 && rowWidth(list, list.size(), chip, size) > right - left) {
                size -= 0.5f;
                chip = text(Ui.heavy(c), size, pal.text);
            }
            int shown = list.size();
            Paint more = text(Ui.heavy(c), size, pal.muted);
            if (rowWidth(list, shown, chip, size) > right - left) {
                while (shown > 1 && rowWidth(list, shown, chip, size) + 6 * S + more.measureText("+" + (list.size() - shown)) > right - left) shown--;
            }
            float ch = (size + 14) * S;
            float x = left;
            for (int i = 0; i < shown; i++) {
                Logic.Outage o = list.get(i);
                boolean now = o.when == Logic.Outage.NOW;
                String label = Logic.shareChip(o);
                float cw = chip.measureText(label) + (size + 8) * S;
                b.r.set(x, y, x + cw, y + ch);
                b.p.setColor(chipColour(o, now));
                b.cv.drawRoundRect(b.r, ch / 2, ch / 2, b.p);
                chip.setColor(now ? pal.ink : pal.text);
                chip.setAlpha(o.when == Logic.Outage.PAST ? 120 : 255);
                b.cv.drawText(label, x + (size + 8) * S / 2, y + ch / 2 + chip.getTextSize() * 0.36f, chip);
                x += cw + 6 * S;
            }
            if (shown < list.size()) b.cv.drawText("+" + (list.size() - shown), x, y + ch / 2 + more.getTextSize() * 0.36f, more);
            return y + ch;
        }

        private float rowWidth(List<Logic.Outage> list, int count, Paint chip, float size) {
            float sum = 0;
            for (int i = 0; i < count; i++) sum += chip.measureText(Logic.shareChip(list.get(i))) + (size + 8) * S + (i > 0 ? 6 * S : 0);
            return sum;
        }

        private int chipColour(Logic.Outage o, boolean now) {
            if (now) return o.maybe ? pal.maybe : pal.off;
            if (o.maybe) return Ui.fade(pal.maybe, 0.35f);
            return Ui.blend(pal.card, Ui.fade(pal.off, pal.dark ? 0.24f : 0.14f));
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
