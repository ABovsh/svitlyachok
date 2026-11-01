package ua.abovsh.svitlo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.Shader;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * The widget as one bitmap (RemoteViews cannot use our fonts). Each widget shows one place: from
 * 2 x 1 the bulb, what is scheduled now and until when; with room, the place's name and what
 * comes after; at 4 x 2, today’s schedule. Only clock times, never "in N minutes": the widget is
 * redrawn at changes, not every minute.
 */
final class WidgetArt {
    private WidgetArt() {}

    /** What one widget shows: its place, as it is now. */
    static final class Model {
        Logic.Place place;
        boolean many;  // the app follows several places: name the one shown
        boolean gone;  // the place of this widget was removed
        LocalDate base;
        String tl;
        Logic.Now now;
    }

    /** The place chosen for one widget (by id; the first place when none was chosen). */
    static Model model(Context c, long now, String choice) {
        List<Logic.Place> all = Prefs.places(c);
        Logic.Place p = Logic.widgetPlace(all, choice);
        if (p == null) {
            if (all.isEmpty()) return null;
            Model gone = new Model();  // its place was removed: say so rather than show another
            gone.gone = true;
            return gone;
        }
        Model m = new Model();
        m.place = p;
        m.many = Prefs.places(c).size() > 1;
        m.base = Logic.today(now);
        Logic.Data data = Store.data(c, p.region);
        m.tl = Logic.timeline(data, p.group, m.base);
        m.now = Logic.nowWithHistory(m.tl, m.base, now, data != null && data.none, Prefs.knownHistory(c, p, data));
        if (data == null) {
            m.now.title = "Немає графіка";
            m.now.big = "відкрийте застосунок";
            m.now.then = "";
        }
        return m;
    }

    /** Same key, same picture: the widget is not redrawn. */
    static String key(Context c, Model m, long now, int wDp, int hDp) {
        StringBuilder b = new StringBuilder().append(wDp).append('x').append(hDp).append('|').append(Ui.pal(c).dark);
        if (m == null) return b.append("|empty").toString();
        if (m.gone) return b.append("|gone").toString();
        Logic.Now n = m.now;
        b.append('|').append(m.many).append('|').append(m.place.name).append('|').append(m.place.group).append('|').append(n.state).append('|')
                .append(n.title).append('|').append(n.big).append('|').append(n.then).append('|').append(n.soon);
        if (Logic.widgetForm(wDp, hDp) == Logic.W_FULL) {  // the bar moves every half hour
            b.append('|').append(m.tl).append('|').append(Logic.index(m.base, now)).append('|').append(n.since).append('|').append(n.hint).append('|').append(n.next);
        }
        return b.toString();
    }

    static String describe(Model m) {
        if (m == null) return "Світлячок: відкрийте застосунок і оберіть чергу";
        if (m.gone) return "Світлячок: місце цього віджета видалено. Утримуйте віджет, щоб обрати інше";
        Logic.Now n = m.now;
        return m.place.name + ", зараз за графіком: " + n.title + (n.since.isEmpty() ? "" : ", " + n.since) + (n.big.isEmpty() ? "" : ", " + n.big)
                + (n.then.isEmpty() ? "" : ", " + n.then);
    }

    /** The size of the picture and its scale; `pad` is the margin inside the rounded card. */
    private static final class Frame {
        final int w;
        final int h;
        final float s;
        final float pad;

        Frame(int w, int h, float s, float pad) {
            this.w = w;
            this.h = h;
            this.s = s;
            this.pad = pad;
        }
    }

    static Bitmap draw(Context c, Model m, long now, int wDp, int hDp) {
        Ui.Pal pal = Ui.pal(c);
        float d = c.getResources().getDisplayMetrics().density;
        float s = Math.min(d, 1100f / wDp);  // keep the bitmap small
        Frame f = new Frame(Math.round(wDp * s), Math.round(hDp * s), s, Math.max(8f, Math.min(14f, hDp * 0.14f)) * s);
        Bitmap bm = Bitmap.createBitmap(f.w, f.h, Bitmap.Config.ARGB_8888);
        Brush b = new Brush(pal, pal.card).on(new Canvas(bm));
        float r = Math.min(24 * s, f.h / 2.6f);
        b.p.setColor((pal.card & 0x00FFFFFF) | 0xF5000000);
        b.r.set(0f, 0f, (float) f.w, (float) f.h);
        if (m != null && !m.gone) {
            b.p.setShader(new LinearGradient(0, 0, f.w * .88f, 0,
                    new int[] {pal.soft(m.now.state), pal.card}, null, Shader.TileMode.CLAMP));
        }
        b.cv.drawRoundRect(b.r, r, r, b.p);
        b.p.setShader(null);
        if (m == null) {
            Paint t = text(Ui.bold(c), 13 * s, pal.muted, Paint.Align.CENTER);
            String msg = "Оберіть чергу в Світлячку";
            b.cv.drawText(msg, f.w / 2f, f.h / 2f + 4 * s, fit(t, msg, f.w - 2 * f.pad));
        } else if (m.gone) {
            Paint t = text(Ui.heavy(c), 14 * s, pal.text, Paint.Align.CENTER);
            Paint u = text(Ui.bold(c), 11 * s, pal.muted, Paint.Align.CENTER);
            String a = "Місце видалено";
            String hint = "утримуйте віджет, щоб обрати інше";
            b.cv.drawText(a, f.w / 2f, f.h / 2f - 2 * s, fit(t, a, f.w - 2 * f.pad));
            b.cv.drawText(hint, f.w / 2f, f.h / 2f + 14 * s, fit(u, hint, f.w - 2 * f.pad));
        } else {
            one(c, b, m, now, f, Logic.widgetForm(wDp, hDp) == Logic.W_FULL);
        }
        return bm;
    }

    /** One place: the bulb and the words; at 4 x 2, today’s schedule under them. */
    private static void one(Context c, Brush b, Model m, long now, Frame f, boolean full) {
        if (full) { large(c, b, m, now, f); return; }
        Logic.Now n = m.now;
        float s = f.s;
        float rowH = f.h - 2 * f.pad;
        float bulb = Math.min(Math.min(rowH * 0.85f, 60 * s), f.w * 0.27f);  // narrow: the words come first
        BulbView.draw(b, n.state, f.pad + bulb / 2f, f.pad + rowH / 2f, bulb, 1f);

        float tx = f.pad + bulb + Math.max(8 * s, bulb * 0.18f);
        float tw = f.w - tx - f.pad * 0.8f;
        float ts = Math.max(12f, Math.min(19f, f.h / s * 0.24f)) * s;
        // with several places the name is always there: a widget must say whose schedule it shows
        boolean head = m.many;
        boolean then = !n.then.isEmpty() && rowH >= (head ? 84 : 62) * s;
        Ui.Pal pal = b.pal;
        Paint ph = fit(text(Ui.heavy(c), ts * 0.8f, Ui.fade(pal.text, 0.8f), Paint.Align.LEFT), m.place.name, tw);
        Paint pt = fit(text(Ui.heavy(c), ts, pal.status(n.state), Paint.Align.LEFT), n.title, tw);
        Paint pb = fit(text(Ui.display(c), ts * 1.05f, pal.text, Paint.Align.LEFT), n.big, tw);
        Paint pn = fit(text(Ui.bold(c), ts * 0.68f, n.soon ? pal.maybeText : pal.muted, Paint.Align.LEFT), n.then, tw);
        float g = ts * 0.32f;
        float block = (head ? ph.getTextSize() + g : 0) + pt.getTextSize()
                + (n.big.isEmpty() ? 0 : g + pb.getTextSize()) + (then ? g + pn.getTextSize() : 0);
        if (block > rowH * 0.94f) {  // a low widget: every line shrinks alike, none is dropped
            float k = rowH * 0.94f / block;
            for (Paint p : new Paint[] {ph, pt, pb, pn}) p.setTextSize(p.getTextSize() * k);
            g *= k;
            block *= k;
        }
        float y = f.pad + (rowH - block) / 2f;
        if (head) y = line(b, m.place.name, tx, y, ph) + g;
        y = line(b, n.title, tx, y, pt);
        if (!n.big.isEmpty()) y = line(b, n.big, tx, y + g, pb);
        if (then) line(b, n.then, tx, y + g, pn);
    }

    /** Native fonts in both columns; no full widget silently falls back to the old row. */
    private static void large(Context c, Brush b, Model m, long now, Frame f) {
        Logic.WidgetLayout w = Logic.widgetLayout(Math.round(f.w / f.s), Math.round(f.h / f.s), true);
        float s = f.s;
        float pad = w.pad * s;
        float width = w.columnWidth * s;
        float right = pad + width + 8 * s;
        Paint name = fit(text(Ui.heavy(c), w.textSize * .8f * s, b.pal.text, Paint.Align.LEFT),
                m.place.name + " · черга " + Logic.queue(m.place.group), f.w - 2 * pad);
        line(b, m.place.name + " · черга " + Logic.queue(m.place.group), pad, pad, name);
        String[] start = Logic.widgetStart(m.now);
        String[] next = Logic.widgetFuture(m.now);
        float bulb = (w.compact ? 22 : 40) * s;
        String title = compactTitle(m.now.state);
        Paint titleSize = text(Ui.heavy(c), w.textSize * s, b.pal.status(m.now.state), Paint.Align.LEFT);
        if (titleSize.measureText(m.now.title) <= width - bulb - 5 * s) title = m.now.title;
        if (w.compact && m.now.next != Logic.UNKNOWN) next[0] = compactEvent(m.now);
        Panel present = new Panel(c, new String[] {title, start[0], start[1]}, new float[] {width, width - bulb - 5 * s, w.textSize * s},
                new int[] {b.pal.status(m.now.state), b.pal.text, b.pal.muted}, bulb, s);
        Panel future = new Panel(c, new String[] {next[0], next[1], next[2]}, new float[] {width, width, w.textSize * s},
                new int[] {b.pal.muted, b.pal.status(m.now.next), b.pal.muted}, 0, s);
        float rowTop = w.rowTop * s;
        float rowHeight = w.rowHeight * s;
        present.fitHeight(rowHeight);
        future.fitHeight(rowHeight);
        present.draw(b, pad, rowTop + (rowHeight - present.height()) / 2, m.now.state);
        future.draw(b, right, rowTop + (rowHeight - future.height()) / 2, Logic.UNKNOWN);
        b.p.setColor(b.pal.line);
        b.cv.drawRect(right - 5 * s, rowTop, right - 4 * s, rowTop + rowHeight, b.p);
        b.r2.set(pad, w.barTop * s, f.w - pad, (w.barTop + w.barHeight) * s);
        bar(c, b, m.base, m.tl, now, b.r2, s);
    }

    private static String compactTitle(int state) {
        if (state == Logic.ON) return "Є";
        if (state == Logic.OFF) return "Немає";
        return state == Logic.MAYBE ? "Можливо" : "Невідомо";
    }

    private static String compactEvent(Logic.Now now) {
        if (now.next == Logic.ON) return now.state == Logic.OFF ? "Увімкнуть" : "Буде світло";
        return now.next == Logic.OFF ? "Вимкнуть" : "Можливо";
    }

    /** Measured text blocks reflow within a column, preserving all schedule information. */
    private static final class Panel {
        private final String[] strings;
        private final TextPaint[] paints = new TextPaint[3];
        private final StaticLayout[] lines = new StaticLayout[3];
        private final float width;
        private final float titleWidth;
        private float bulb;
        private float gap;

        /** words: title, value, detail; sizes: width, title width, text size; inks in the order of the words. */
        Panel(Context c, String[] words, float[] sizes, int[] inks, float bulb, float scale) {
            strings = words;
            String value = words[1];
            float size = sizes[2];
            this.width = sizes[0]; this.titleWidth = sizes[1]; this.bulb = bulb; gap = 2 * scale;
            int titleInk = inks[0];
            int valueInk = inks[1];
            int detailInk = inks[2];
            paints[0] = new TextPaint(text(Ui.heavy(c), bulb > 0 ? size : size * .74f, titleInk, Paint.Align.LEFT));
            paints[1] = new TextPaint(text(value.contains(":") ? Ui.display(c) : Ui.bold(c),
                    size * (value.contains(":") ? 1.05f : .74f), valueInk, Paint.Align.LEFT));
            paints[2] = new TextPaint(text(Ui.bold(c), size * .68f, detailInk, Paint.Align.LEFT));
            if (value.contains(":")) fit(paints[1], value, sizes[0]);
            reflow();
        }

        private void reflow() {
            for (int i = 0; i < lines.length; i++) {
                if (strings[i].isEmpty()) { lines[i] = null; continue; }
                lines[i] = StaticLayout.Builder.obtain(strings[i], 0, strings[i].length(), paints[i],
                        Math.max(1, Math.round(i == 0 ? titleWidth : width)))
                        .setAlignment(Layout.Alignment.ALIGN_NORMAL).setIncludePad(false).build();
            }
        }

        private float titleHeight() {
            return lines[0] == null ? bulb : Math.max(bulb, (float) lines[0].getHeight());
        }

        float height() {
            float h = titleHeight();
            for (int i = 1; i < lines.length; i++) if (lines[i] != null) h += gap + lines[i].getHeight();
            return h;
        }

        void fitHeight(float height) {
            // A launcher may report a shorter cell: keep both panes and all text, never the old row.
            for (int attempt = 0; attempt < 6 && height() > height; attempt++) {
                float ratio = Math.min(.99f, (height - 1) / height());
                for (TextPaint paint : paints) paint.setTextSize(paint.getTextSize() * ratio);
                bulb *= ratio; gap *= ratio;
                reflow();
            }
        }

        void draw(Brush b, float x, float y, int state) {
            float titleHeight = titleHeight();
            if (bulb > 0) BulbView.draw(b, state, x + bulb / 2, y + titleHeight / 2, bulb, 1);
            drawLine(b.cv, lines[0], x + (bulb > 0 ? bulb + 5 * gap / 2 : 0), y);
            y += titleHeight;
            for (int i = 1; i < lines.length; i++) if (lines[i] != null) {
                y += gap; drawLine(b.cv, lines[i], x, y); y += lines[i].getHeight();
            }
        }

        private static void drawLine(Canvas cv, StaticLayout line, float x, float y) {
            if (line == null) return;
            cv.save(); cv.translate(x, y); line.draw(cv); cv.restore();
        }
    }

    /** A line of text whose top is at y; returns the y under it. */
    private static float line(Brush b, String text, float x, float y, Paint p) {
        b.cv.drawText(text, x, y + p.getTextSize() * 0.8f, p);
        return y + p.getTextSize();
    }

    /** Today 00–24, fixed clock labels, ordinary past dimming and a marker at now. */
    private static void bar(Context c, Brush b, LocalDate base, String tl, long now, RectF area, float s) {
        // runs() uses r2 for possible outages; keep a separate stable bounds object.
        RectF bounds = new RectF(area);
        BarView.runs(b, tl, 0, 48, 0, bounds, 1.5f * s);
        float at = Math.max(0, Math.min(48, Logic.index(base, now) + (Logic.z(now).getMinute() % 30) / 30f));
        float marker = bounds.left + bounds.width() * at / 48f;
        b.path.reset(); b.path.addRoundRect(bounds, bounds.height()/2, bounds.height()/2, android.graphics.Path.Direction.CW);
        b.cv.save(); b.cv.clipPath(b.path);
        b.p.setColor(Ui.fade(b.pal.card, b.pal.dark ? .68f : .65f));
        b.cv.drawRect(bounds.left, bounds.top, marker, bounds.bottom, b.p);
        b.p.setColor(Ui.fade(b.pal.text, .14f)); b.p.setStrokeWidth(Math.max(1, s * .7f));
        for (int hour = 1; hour < 24; hour++) {
            float x = bounds.left + bounds.width() * hour / 24;
            b.cv.drawLine(x, bounds.top, x, bounds.bottom, b.p);
        }
        b.cv.restore();
        b.p.setColor(b.pal.text);
        b.cv.drawRect(marker - s, bounds.top - 2*s, marker + s, bounds.bottom + 2*s, b.p);
        Paint lab = text(Ui.bold(c), 10 * s, b.pal.muted, Paint.Align.CENTER);
        for (int hour = 0; hour <= 24; hour += 6) {
            lab.setTextAlign(hourAlign(hour));
            b.cv.drawText(String.format(Locale.ROOT, "%02d", hour), bounds.left + bounds.width() * hour/24,
                    bounds.bottom + 13*s, lab);
        }
    }

    private static Paint.Align hourAlign(int hour) {
        if (hour == 0) return Paint.Align.LEFT;
        return hour == 24 ? Paint.Align.RIGHT : Paint.Align.CENTER;
    }

    private static Paint text(Typeface tf, float size, int colour, Paint.Align a) {
        Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
        p.setTypeface(tf);
        p.setTextSize(size);
        p.setColor(colour);
        p.setTextAlign(a);
        return p;
    }

    private static Paint fit(Paint p, String s, float max) {
        float wd = p.measureText(s);
        if (wd > max && max > 0) p.setTextSize(p.getTextSize() * max / wd);
        return p;
    }
}
