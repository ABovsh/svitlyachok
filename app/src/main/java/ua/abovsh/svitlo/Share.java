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
 * "Поділитися": the schedule of a place as a picture, for a messenger the person picks. The
 * picture names the region and queue, not the place; the words under it say where the app is.
 * The app sends nothing itself: the picture goes through Android's own share sheet.
 */
final class Share {
    static final String FILE = "schedule.png";
    private static final int W = 360;  // dp of the picture; drawn at 3x

    private Share() {}

    static void send(Activity a, Logic.Place p) {
        try {
            File f = file(a);
            Bitmap bm = draw(a, p, Sync.now());
            try (OutputStream out = new FileOutputStream(f)) {
                bm.compress(Bitmap.CompressFormat.PNG, 100, out);
            }
            bm.recycle();
            Uri uri = ShareProvider.uri();
            Intent send = new Intent(Intent.ACTION_SEND)
                    .setType("image/png")
                    .putExtra(Intent.EXTRA_STREAM, uri)
                    .putExtra(Intent.EXTRA_TEXT, Logic.shareText(p))
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            send.setClipData(ClipData.newRawUri("", uri));
            a.startActivity(Intent.createChooser(send, "Поділитися графіком"));
        } catch (IOException | RuntimeException e) {
            Toast.makeText(a, "Не вдалося підготувати картинку", Toast.LENGTH_SHORT).show();
        }
    }

    /** The one file the provider serves, in the app's own cache. */
    static File file(Context c) {
        File dir = new File(c.getCacheDir(), "share");
        if (!dir.isDirectory() && !dir.mkdirs()) return new File(c.getCacheDir(), FILE);
        return new File(dir, FILE);
    }

    /** Today and tomorrow of one place, as on the main screen, on a picture 1080 px wide. */
    static Bitmap draw(Context c, Logic.Place p, long now) {
        Ui.Pal pal = Ui.pal(c);
        float s = 3f;
        int w = Math.round(W * s);
        Bitmap tall = Bitmap.createBitmap(w, Math.round(900 * s), Bitmap.Config.ARGB_8888);
        Canvas cv = new Canvas(tall);
        cv.drawColor(pal.card);
        Brush b = new Brush(pal, pal.card).on(cv);
        Logic.Data d = Store.data(c, p.region);
        LocalDate base = Logic.today(now);
        Logic.Region r = Logic.region(p.region);
        float pad = 20 * s;
        float right = w - pad;

        float y = 22 * s;
        Paint title = text(Ui.display(c), 22 * s, pal.text);
        Paint sub = text(Ui.body(c), 13 * s, pal.muted);
        y = line(cv, "Черга " + Logic.queue(p.group), pad, y, title);
        y = line(cv, (r == null ? "" : r.name + " · ") + Ui.date(base), pad, y + 6 * s, sub);
        y += 18 * s;

        int cur = Logic.index(base, now);
        float nowRel = cur + (Logic.z(now).getMinute() % 30) / 30f;
        y = day(c, b, "Сьогодні", Logic.day(d, p.group, base), cur, nowRel, pad, right, y, s);
        b.p.setColor(pal.line);
        cv.drawRect(pad, y + 14 * s, right, y + 15 * s, b.p);
        y += 29 * s;
        LocalDate t = base.plusDays(1);
        y = day(c, b, "Завтра, " + t.getDayOfMonth() + " " + Ui.MONTHS[t.getMonthValue() - 1],
                Logic.day(d, p.group, t), -1, -1, pad, right, y, s);

        y += 24 * s;
        y = legend(c, b, pad, right, y, s);
        y += 18 * s;
        Paint foot = text(Ui.body(c), 11.5f * s, pal.dim);
        y = line(cv, "Це графік обленерго, а не фактична наявність світла.", pad, y, foot);
        Paint link = text(Ui.heavy(c), 13 * s, pal.accentText);
        y = line(cv, "Світлячок · github.com/ABovsh/svitlyachok", pad, y + 6 * s, link);
        // drawn on a tall sheet, then cut to what was used
        Bitmap out = Bitmap.createBitmap(tall, 0, 0, w, Math.min(tall.getHeight(), Math.round(y + 22 * s)));
        tall.recycle();
        return out;
    }

    /** A day: its title and the light to expect, the bar with hours, the outages as chips. */
    private static float day(Context c, Brush b, String title, String slots, int cur, float nowRel,
            float left, float right, float y, float s) {
        Ui.Pal pal = b.pal;
        Canvas cv = b.cv;
        Paint head = text(Ui.display(c), 16 * s, pal.text);
        float top = y;
        y = line(cv, title, left, y, head);
        if (slots == null) {
            return line(cv, "Графік ще не опубліковано", left, y + 8 * s, text(Ui.body(c), 14 * s, pal.muted));
        }
        // "очікується 17 год світла · 71 %" in a green pill at the right, level with the title
        String light = Logic.dayLight(slots);
        Paint lp = text(Ui.heavy(c), 12.5f * s, pal.onText);
        float lw = lp.measureText(light) + 20 * s;
        float lh = 26 * s;
        float lt = top + (head.getTextSize() - lh) / 2f;
        if (left + head.measureText(title) + 10 * s + lw > right) {  // narrow: under the title
            lt = y + 6 * s;
            y = lt + lh;
            b.r.set(left, lt, left + lw, lt + lh);
        } else {
            b.r.set(right - lw, lt, right, lt + lh);
        }
        b.p.setColor(Ui.blend(pal.card, Ui.fade(pal.on, pal.dark ? 0.18f : 0.12f)));
        cv.drawRoundRect(b.r, lh / 2, lh / 2, b.p);
        cv.drawText(light, b.r.left + 10 * s, b.r.centerY() + lp.getTextSize() * 0.36f, lp);

        float barTop = y + 12 * s;
        RectF bar = new RectF(left, barTop, right, barTop + 14 * s);
        BarView.runs(b, slots, 0, 48, 0, bar, 2 * s);
        Paint lab = text(Ui.bold(c), 10.5f * s, pal.dim);
        lab.setTextAlign(Paint.Align.CENTER);
        float sw = bar.width() / 48f;
        for (int hr = 0; hr <= 24; hr += 6) {
            String l = String.format(Locale.ROOT, "%02d", hr);
            float half = lab.measureText(l) / 2;
            cv.drawText(l, Math.max(left + half, Math.min(right - half, left + hr * 2 * sw)), bar.bottom + 15 * s, lab);
        }
        if (nowRel >= 0 && nowRel <= 48) {  // now, as a white stroke
            float x = left + nowRel * sw;
            b.p.setColor(pal.card);
            b.r.set(x - 2.5f * s, bar.top - 3 * s, x + 2.5f * s, bar.bottom + 3 * s);
            cv.drawRoundRect(b.r, 2.5f * s, 2.5f * s, b.p);
            b.p.setColor(pal.text);
            b.r.set(x - 1.2f * s, bar.top - 2 * s, x + 1.2f * s, bar.bottom + 2 * s);
            cv.drawRoundRect(b.r, 1.2f * s, 1.2f * s, b.p);
        }
        y = bar.bottom + 20 * s;

        List<Logic.Outage> list = Logic.outages(slots, cur);
        Paint chip = text(Ui.heavy(c), 14 * s, pal.text);
        float x = left;
        float ch = 30 * s;
        float cy = y + 8 * s;
        for (Logic.Outage o : list) {
            boolean now = o.when == Logic.Outage.NOW;
            String label = (now ? "зараз " : "") + (o.maybe ? "можливо " : "") + o.label.replace(" – ", "–");
            float cw = chip.measureText(label) + 22 * s;
            if (x > left && x + cw > right) {
                x = left;
                cy += ch + 6 * s;
            }
            b.r.set(x, cy, x + cw, cy + ch);
            if (now) {
                b.p.setColor(o.maybe ? pal.maybe : pal.off);
            } else if (o.maybe) {
                b.p.setColor(Ui.fade(pal.maybe, 0.35f));
            } else {
                b.p.setColor(Ui.blend(pal.card, Ui.fade(pal.off, pal.dark ? 0.24f : 0.14f)));
            }
            cv.drawRoundRect(b.r, ch / 2, ch / 2, b.p);
            chip.setColor(now ? pal.ink : pal.text);
            chip.setAlpha(o.when == Logic.Outage.PAST ? 120 : 255);
            cv.drawText(label, x + 11 * s, cy + ch / 2 + chip.getTextSize() * 0.36f, chip);
            x += cw + 6 * s;
        }
        return list.isEmpty() ? y : cy + ch;
    }

    /** What the colours mean, wrapping to a second line on the narrow picture. */
    private static float legend(Context c, Brush b, float left, float right, float y, float s) {
        Paint t = text(Ui.bold(c), 12 * s, b.pal.muted);
        String[][] items = {{"1", "світло є"}, {"0", "світла немає"}, {"3", "можливе вимкнення"}};
        float x = left;
        for (String[] it : items) {
            if (x > left && x + 24 * s + t.measureText(it[1]) > right) {
                x = left;
                y += 22 * s;
            }
            b.r.set(x, y + 2 * s, x + 18 * s, y + 12 * s);
            BarView.runs(b, it[0], 0, 1, 0, b.r, 0);
            x += 24 * s;
            b.cv.drawText(it[1], x, y + 11.5f * s, t);
            x += t.measureText(it[1]) + 16 * s;
        }
        return y + 16 * s;
    }

    private static float line(Canvas cv, String s, float x, float y, Paint p) {
        cv.drawText(s, x, y + p.getTextSize() * 0.8f, p);
        return y + p.getTextSize();
    }

    private static Paint text(android.graphics.Typeface tf, float size, int colour) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(tf);
        p.setTextSize(size);
        p.setColor(colour);
        return p;
    }
}
