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
import java.util.Locale;

/**
 * "Поділитися": one day of a place as a picture, for a messenger the person picks. The picture
 * names the region and queue, not the place; the words under it say where to get the app.
 * The app sends nothing itself: the picture goes through Android's own share sheet.
 */
final class Share {
    static final String FILE = "schedule.png";
    private static final int W = 360;  // dp of the picture; drawn at 3x

    private Share() {}

    static void send(Activity a, Logic.Place p, LocalDate day) {
        try {
            File f = picture(a);
            Bitmap bm = draw(a, p, day, Sync.now());
            try (OutputStream out = new FileOutputStream(f)) {
                bm.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            bm.recycle();
            Uri uri = ShareProvider.uri();
            Intent send = new Intent(Intent.ACTION_SEND)
                    .setType("image/png")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .putExtra(Intent.EXTRA_TEXT, Logic.SHARE_TEXT)
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

    /** One day of one place, as on the main screen, on a picture 1080 px wide. */
    static Bitmap draw(Context c, Logic.Place p, LocalDate day, long now) {
        Ui.Pal pal = Ui.pal(c);
        int w = Math.round(W * Sheet.S);
        Bitmap tall = Bitmap.createBitmap(w, Math.round(900 * Sheet.S), Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(tall);
        cv.drawColor(pal.card);
        Sheet sh = new Sheet(c, new Brush(pal, pal.card).on(cv), w);
        float s = Sheet.S;
        Logic.Data d = Store.data(c, p.region);
        Logic.Region r = Logic.region(p.region);

        float y = sh.line("Черга " + Logic.queue(p.group), 22 * s, sh.text(Ui.display(c), 22, pal.text));
        if (r != null) y = sh.line(r.name, y + 6 * s, sh.text(Ui.body(c), 13, pal.muted));
        y += 18 * s;

        // the date in words: the picture is read later, when "today" is another day
        String date = Ui.date(day);
        boolean today = day.equals(Logic.today(now));
        int cur = today ? Logic.index(day, now) : -1;
        float nowRel = today ? cur + (Logic.z(now).getMinute() % 30) / 30f : -1;
        y = sh.day(Character.toUpperCase(date.charAt(0)) + date.substring(1), Logic.day(d, p.group, day), cur, nowRel, y);

        y = sh.legend(y + 24 * s) + 18 * s;
        y = sh.line("Це графік обленерго, а не фактична наявність світла.", y, sh.text(Ui.body(c), 11.5f, pal.dim));
        y = sh.line(Logic.SHARE_SIGNATURE, y + 6 * s, sh.text(Ui.heavy(c), 13, pal.accentText));
        // drawn on a tall sheet, then cut to what was used
        Bitmap out = Bitmap.createBitmap(tall, 0, 0, w, Math.min(tall.getHeight(), Math.round(y + 22 * s)));
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
            left = 20 * S;
            right = width - 20 * S;
        }

        /** A day: its title and the light to expect, the bar with hours, the outages as chips. */
        float day(String title, String slots, int cur, float nowRel, float top) {
            Paint head = text(Ui.display(c), 16, pal.text);
            float y = line(title, top, head);
            if (slots == null) return line("Графік ще не опубліковано", y + 8 * S, text(Ui.body(c), 14, pal.muted));
            y = light(Logic.dayLight(slots), head.measureText(title), top, head.getTextSize(), y);
            y = bar(slots, nowRel, y + 12 * S);
            return chips(Logic.outages(slots, cur), y);
        }

        /** "очікується 17 год світла · 71 %" in a green pill at the right, level with the title. */
        private float light(String light, float titleWidth, float top, float titleSize, float y) {
            Paint lp = text(Ui.heavy(c), 12.5f, pal.onText);
            float lw = lp.measureText(light) + 20 * S;
            float lh = 26 * S;
            float lt = top + (titleSize - lh) / 2f;
            if (left + titleWidth + 10 * S + lw > right) {  // narrow: under the title
                lt = y + 6 * S;
                y = lt + lh;
                b.r.set(left, lt, left + lw, lt + lh);
            } else {
                b.r.set(right - lw, lt, right, lt + lh);
            }
            b.p.setColor(Ui.blend(pal.card, Ui.fade(pal.on, pal.dark ? 0.18f : 0.12f)));
            b.cv.drawRoundRect(b.r, lh / 2, lh / 2, b.p);
            b.cv.drawText(light, b.r.left + 10 * S, b.r.centerY() + lp.getTextSize() * 0.36f, lp);
            return y;
        }

        /** The day's bar with the hours under it and, for today, a white stroke at now. */
        private float bar(String slots, float nowRel, float top) {
            RectF bar = new RectF(left, top, right, top + 14 * S);
            BarView.runs(b, slots, 0, 48, 0, bar, 2 * S);
            Paint lab = text(Ui.bold(c), 10.5f, pal.dim);
            lab.setTextAlign(Paint.Align.CENTER);
            float sw = bar.width() / 48f;
            for (int hr = 0; hr <= 24; hr += 6) {
                String l = String.format(Locale.ROOT, "%02d", hr);
                float half = lab.measureText(l) / 2;
                b.cv.drawText(l, Math.max(left + half, Math.min(right - half, left + hr * 2 * sw)), bar.bottom + 15 * S, lab);
            }
            if (nowRel >= 0 && nowRel <= 48) {
                float x = left + nowRel * sw;
                b.p.setColor(pal.card);
                b.r.set(x - 2.5f * S, bar.top - 3 * S, x + 2.5f * S, bar.bottom + 3 * S);
                b.cv.drawRoundRect(b.r, 2.5f * S, 2.5f * S, b.p);
                b.p.setColor(pal.text);
                b.r.set(x - 1.2f * S, bar.top - 2 * S, x + 1.2f * S, bar.bottom + 2 * S);
                b.cv.drawRoundRect(b.r, 1.2f * S, 1.2f * S, b.p);
            }
            return bar.bottom + 20 * S;
        }

        /** The outages as chips: the one going on now filled red, the past ones faded. */
        private float chips(List<Logic.Outage> list, float y) {
            if (list.isEmpty()) return y;
            Paint chip = text(Ui.heavy(c), 14, pal.text);
            float x = left;
            float ch = 30 * S;
            float cy = y + 8 * S;
            for (Logic.Outage o : list) {
                boolean now = o.when == Logic.Outage.NOW;
                String label = (now ? "зараз " : "") + (o.maybe ? "можливо " : "") + o.label.replace(" – ", "–");
                float cw = chip.measureText(label) + 22 * S;
                if (x > left && x + cw > right) {
                    x = left;
                    cy += ch + 6 * S;
                }
                b.r.set(x, cy, x + cw, cy + ch);
                b.p.setColor(chipColour(o, now));
                b.cv.drawRoundRect(b.r, ch / 2, ch / 2, b.p);
                chip.setColor(now ? pal.ink : pal.text);
                chip.setAlpha(o.when == Logic.Outage.PAST ? 120 : 255);
                b.cv.drawText(label, x + 11 * S, cy + ch / 2 + chip.getTextSize() * 0.36f, chip);
                x += cw + 6 * S;
            }
            return cy + ch;
        }

        private int chipColour(Logic.Outage o, boolean now) {
            if (now) return o.maybe ? pal.maybe : pal.off;
            if (o.maybe) return Ui.fade(pal.maybe, 0.35f);
            return Ui.blend(pal.card, Ui.fade(pal.off, pal.dark ? 0.24f : 0.14f));
        }

        /** What the colours mean, wrapping to a second line on the narrow picture. */
        float legend(float y) {
            Paint t = text(Ui.bold(c), 12, pal.muted);
            String[][] items = {{"1", "світло є"}, {"0", "світла немає"}, {"3", "можливе вимкнення"}};
            float x = left;
            for (String[] it : items) {
                if (x > left && x + 24 * S + t.measureText(it[1]) > right) {
                    x = left;
                    y += 22 * S;
                }
                b.r.set(x, y + 2 * S, x + 18 * S, y + 12 * S);
                BarView.runs(b, it[0], 0, 1, 0, b.r, 0);
                x += 24 * S;
                b.cv.drawText(it[1], x, y + 11.5f * S, t);
                x += t.measureText(it[1]) + 16 * S;
            }
            return y + 16 * S;
        }

        /** A line of text whose top is at y, from the left margin; returns the y under it. */
        float line(String s, float y, Paint p) {
            b.cv.drawText(s, left, y + p.getTextSize() * 0.8f, p);
            return y + p.getTextSize();
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
