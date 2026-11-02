package ua.abovsh.svitlo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
import android.graphics.Shader;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;

/**
 * The widget as one bitmap (RemoteViews cannot use our fonts). Each widget shows one place: from
 * 2 x 1 the bulb, what is scheduled now and until when; with room, the place's name and what
 * comes after; at 4 x 2, today's total, tomorrow and today's schedule. Only clock times, never "in N minutes": the widget is
 * redrawn at changes, not every minute.
 */
final class WidgetArt {
    private WidgetArt() {}

    /** What one widget shows: its place, as it is now. */
    static final class Model {
        Logic.Place place;
        boolean many;  // the app follows several places: name the one shown
        boolean stale;
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
        m.stale = !dataFresh(c, p, data, now);
        m.tl = Logic.timeline(data, p.group, m.base);
        m.now = Logic.nowWithHistory(m.tl, m.base, now, data != null && data.none, Prefs.knownHistory(c, p, data));
        if (data == null) {
            m.now.title = "Немає графіка";
            m.now.big = "відкрийте застосунок";
            m.now.then = "";
        }
        return m;
    }

    private static boolean dataFresh(Context c, Logic.Place p, Logic.Data d, long now) {
        long checked = Store.checkedAt(c, p.region);
        return d != null && d.sourceOk && checked > 0 && now - checked <= Logic.STALE_S
                && (d.sourceTime <= 0 || now - d.sourceTime <= 48 * 3600L || Logic.currentData(d, now));
    }

    private static String widgetName(Model m) { return (m.stale ? "⚠ " : "") + m.place.name; }

    /** Same key, same picture: the widget is not redrawn. */
    static String key(Context c, Model m, long now, int wDp, int hDp) {
        StringBuilder b = new StringBuilder().append(wDp).append('x').append(hDp).append('|').append(Ui.pal(c).dark);
        if (m == null) return b.append("|empty").toString();
        if (m.gone) return b.append("|gone").toString();
        Logic.Now n = m.now;
        b.append('|').append(m.stale).append('|').append(m.many).append('|').append(m.place.name).append('|').append(m.place.group).append('|').append(n.state).append('|')
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
        return (m.stale ? "Дані можуть бути застарілі. " : "") + m.place.name + ", зараз за графіком: " + n.title + (n.since.isEmpty() ? "" : ", " + n.since) + (n.big.isEmpty() ? "" : ", " + n.big)
                + (n.then.isEmpty() ? "" : ", " + n.then) + ". " + Logic.widgetTomorrow(dayOf(m.tl, 48))[0];
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
        boolean head = m.many || m.stale;
        boolean then = !n.then.isEmpty() && rowH >= (head ? 84 : 62) * s;
        Ui.Pal pal = b.pal;
        Paint ph = fit(text(Ui.heavy(c), ts * 0.8f, Ui.fade(pal.text, 0.8f), Paint.Align.LEFT), widgetName(m), tw);
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
        if (head) y = line(b, widgetName(m), tx, y, ph) + g;
        y = line(b, n.title, tx, y, pt);
        if (!n.big.isEmpty()) y = line(b, n.big, tx, y + g, pb);
        if (then) line(b, n.then, tx, y + g, pn);
    }

    /** The whole card works: the name and today's total, both panes as large as they fit, tomorrow, the bar. */
    private static void large(Context c, Brush b, Model m, long now, Frame f) {
        Logic.WidgetLayout w = Logic.widgetLayout(Math.round(f.w / f.s), Math.round(f.h / f.s), true);
        float s = f.s;
        float pad = w.pad * s;
        String[] next = Logic.widgetFuture(m.now);
        boolean said = next[0].startsWith("Завтра") || next[0].startsWith("Сьогодні й завтра");
        boolean tomorrow = w.tomorrowHeight > 0 && !said;
        float top = w.rowTop * s;
        float room = (w.rowHeight + (tomorrow ? 0 : w.tomorrowHeight)) * s;
        header(c, b, m, f, w);
        panes(c, b, m, next, new float[] {pad, top, w.columnWidth * s, room, s});
        if (tomorrow) tomorrowLine(c, b, m, new float[] {pad, top + room, f.w - 2 * pad, w.tomorrowHeight * s});
        b.r2.set(pad, w.barTop * s, f.w - pad, (w.barTop + w.barHeight) * s);
        bar(c, b, m.base, m.tl, now, b.r2, new float[] {s, w.labelSize * s});
    }

    private static String dayOf(String tl, int from) {
        String day = tl.substring(from, from + 48);
        return day.replace("2", "").isEmpty() ? null : day;
    }

    private static void header(Context c, Brush b, Model m, Frame f, Logic.WidgetLayout w) {
        float s = f.s;
        float pad = w.pad * s;
        float full = f.w - 2 * pad;
        String title = widgetName(m) + " · черга " + Logic.queue(m.place.group);
        Paint name = fit(text(Ui.heavy(c), w.headerSize * s, b.pal.text, Paint.Align.LEFT), title, full);
        line(b, title, pad, pad, name);
        String total = Logic.widgetToday(dayOf(m.tl, 0));
        if (total.isEmpty()) return;
        Paint side = text(Ui.bold(c), w.headerSize * .78f * s, b.pal.muted, Paint.Align.RIGHT);
        float free = full - name.measureText(title) - 10 * s;
        String wide = "сьогодні " + total;
        String words = side.measureText(wide) <= free ? wide : total;
        if (side.measureText(words) > free) return;  // the name matters more
        float baseline = pad - name.ascent();
        b.cv.drawText(words, f.w - pad, baseline, side);
    }

    private static float height(Paint p) {
        return p.descent() - p.ascent();
    }

    /** The largest size at which `words` fit `width`. */
    private static float sizeToFit(Paint p, String words, float width) {
        if (words.isEmpty()) return Float.MAX_VALUE;
        return p.getTextSize() * width / p.measureText(words);
    }

    /** box: x, y, column width, height, scale. Both columns share their rows, so the clocks stand on one line. */
    private static void panes(Context c, Brush b, Model m, String[] next, float[] box) {
        float width = box[2];
        float room = box[3];
        String[] start = Logic.widgetStart(m.now);
        boolean leftClock = start[0].contains(":");
        boolean rightClock = next[1].contains(":");
        Paint clock = text(Ui.display(c), 100, b.pal.text, Paint.Align.LEFT);
        Paint plain = text(Ui.bold(c), 100, b.pal.text, Paint.Align.LEFT);
        float s = box[4];
        float gapX = 6 * s;
        float detail = Math.max(12 * s, Math.min(16 * s, room * .2f));
        Paint small = text(Ui.bold(c), detail, b.pal.muted, Paint.Align.LEFT);
        float leftSpare = start[1].isEmpty() ? 0 : small.measureText(start[1]) + gapX;
        float rightSpare = next[2].isEmpty() ? 0 : small.measureText(next[2]) + gapX;
        float wholeLeft = leftClock ? sizeToFit(clock, start[0], width) : Float.MAX_VALUE;
        float wholeRight = rightClock ? sizeToFit(clock, next[1], width) : Float.MAX_VALUE;
        float besideLeft = leftClock ? sizeToFit(clock, start[0], Math.max(1, width - leftSpare)) : Float.MAX_VALUE;
        float besideRight = rightClock ? sizeToFit(clock, next[1], Math.max(1, width - rightSpare)) : Float.MAX_VALUE;
        float beside = Math.min(room * .54f, Math.min(besideLeft, besideRight));
        float stacked = Math.min(room * .42f, Math.min(wholeLeft, wholeRight));
        boolean details = leftSpare + rightSpare > 0;
        boolean inline = !details || beside >= stacked;
        float value = inline ? beside : stacked;
        float head = Math.min(value * .8f, room * (inline ? .30f : .24f));

        Paint title = text(Ui.heavy(c), head, b.pal.status(m.now.state), Paint.Align.LEFT);
        Paint label = text(Ui.heavy(c), head, b.pal.muted, Paint.Align.LEFT);
        Paint left = text(leftClock ? Ui.display(c) : Ui.bold(c), leftClock ? value : head, b.pal.text, Paint.Align.LEFT);
        Paint right = text(rightClock ? Ui.display(c) : Ui.bold(c), rightClock ? value : head, b.pal.status(m.now.next), Paint.Align.LEFT);
        float tall = Math.max(height(left), height(right));
        float first = height(title);
        float third = inline || !details ? 0 : height(small);
        float used = first + tall + third;
        if (used > room) {  // never past the box: everything shrinks alike
            float k = room / used;
            for (Paint p : new Paint[] {title, label, left, right, small}) p.setTextSize(p.getTextSize() * k);
            first *= k; tall *= k; third *= k; used = room;
        }
        float spare = room - used;
        int gaps = third > 0 ? 2 : 1;
        float gap = Math.min(spare / (gaps + 2), first * .35f);
        float y = box[1] + (spare - gap * gaps) / 2;

        // the first row: the bulb and the state, and what comes next
        float bulb = Math.min(first * 1.25f, room * .4f);
        float titleX = box[0] + bulb + bulb * .16f;
        float titleRoom = width - bulb - bulb * .16f;
        String state = sizeToFit(title, m.now.title, titleRoom) >= 13 * s ? m.now.title : compactTitle(m.now.state);
        BulbView.draw(b, m.now.state, box[0] + bulb / 2, y + first / 2, bulb, 1);
        fit(title, state, titleRoom);
        drawCentred(b, state, titleX, y, first, title);
        float rightX = box[0] + width + 8 * s;
        String event = next[0];
        if (sizeToFit(label, event, width) < 12 * s && m.now.next != Logic.UNKNOWN) event = compactEvent(m.now) + (m.now.tomorrow ? " завтра" : "");
        fit(label, event, width);
        drawCentred(b, event, rightX, y, first, label);

        // the second row: the clocks on one baseline
        y += first + gap;
        float baseline = y + tall - Math.max(left.descent(), right.descent());
        if (!leftClock) fit(left, start[0], width);
        if (!rightClock) fit(right, next[1], width);
        b.cv.drawText(start[0], box[0], baseline, left);
        b.cv.drawText(next[1], rightX, baseline, right);
        if (inline) {
            if (leftSpare > 0) b.cv.drawText(start[1], box[0] + left.measureText(start[0]) + gapX, baseline, small);
            if (rightSpare > 0) b.cv.drawText(next[2], rightX + right.measureText(next[1]) + gapX, baseline, small);
        } else {
            y += tall + gap;
            if (leftSpare > 0) { fit(small, start[1], width); line(b, start[1], box[0], y, small); }
            if (rightSpare > 0) { fit(small, next[2], width); line(b, next[2], rightX, y, small); }
        }
        b.p.setColor(b.pal.line);
        b.cv.drawRect(rightX - 5 * s, box[1], rightX - 4 * s, box[1] + room, b.p);
    }

    private static void drawCentred(Brush b, String words, float x, float y, float rowHeight, Paint p) {
        b.cv.drawText(words, x, y + (rowHeight - height(p)) / 2 - p.ascent(), p);
    }

    /** box: x, y, width, height. All of tomorrow's outages when they fit, otherwise their count and total. */
    private static void tomorrowLine(Context c, Brush b, Model m, float[] box) {
        String[] words = Logic.widgetTomorrow(dayOf(m.tl, 48));
        Paint p = text(Ui.bold(c), box[3] * .72f, b.pal.text, Paint.Align.LEFT);
        String shown = sizeToFit(p, words[0], box[2]) >= p.getTextSize() * .8f ? words[0] : words[1];
        fit(p, shown, box[2]);
        drawCentred(b, shown, box[0], box[1], box[3], p);
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

    /** A line of text whose top is at y; returns the y under it. */
    private static float line(Brush b, String text, float x, float y, Paint p) {
        b.cv.drawText(text, x, y - p.ascent(), p);
        return y + p.descent() - p.ascent();
    }

    /** Today 00–24, fixed clock labels, ordinary past dimming and a marker at now. */
    private static void bar(Context c, Brush b, LocalDate base, String tl, long now, RectF area, float[] scale) {
        float s = scale[0];
        // runs() uses r2 for possible outages; keep a separate stable bounds object.
        RectF bounds = new RectF(area);
        BarView.runs(b, tl, 0, 48, 0, bounds, 1.5f * s);
        float at = Math.max(0, Math.min(48f, (float) Logic.index(base, now)));
        float marker = bounds.left + bounds.width() * at / 48f;
        b.path.reset(); b.path.addRoundRect(bounds, bounds.height()/2, bounds.height()/2, android.graphics.Path.Direction.CW);
        b.cv.save(); b.cv.clipPath(b.path);
        b.p.setColor(Ui.fade(b.pal.card, Ui.PAST_VEIL));
        b.cv.drawRect(bounds.left, bounds.top, marker, bounds.bottom, b.p);
        b.p.setColor(Ui.fade(b.pal.text, .14f)); b.p.setStrokeWidth(Math.max(1, s * .7f));
        for (int hour = 1; hour < 24; hour++) {
            float x = bounds.left + bounds.width() * hour / 24;
            b.cv.drawLine(x, bounds.top, x, bounds.bottom, b.p);
        }
        b.cv.restore();
        b.p.setColor(b.pal.text);
        b.cv.drawRect(marker - s, bounds.top - 2*s, marker + s, bounds.bottom + 2*s, b.p);
        Paint lab = text(Ui.bold(c), scale[1], b.pal.muted, Paint.Align.CENTER);
        for (int hour = 0; hour <= 24; hour += 6) {
            lab.setTextAlign(hourAlign(hour));
            b.cv.drawText(String.format(Locale.ROOT, "%02d", hour), bounds.left + bounds.width() * hour/24,
                    bounds.bottom + scale[1] + 1*s, lab);
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
