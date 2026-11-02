package ua.abovsh.svitlo;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.Typeface;
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
        String lastDay = "";  // the last day with a schedule, for a widget without one
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
        m.lastDay = Logic.lastScheduleDay(data);
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
                .append(n.title).append('|').append(n.big).append('|').append(n.then).append('|').append(n.soon).append('|').append(m.lastDay);
        b.append('|').append(n.hint).append('|').append(n.next).append('|').append(n.change);
        if (Logic.widgetForm(wDp, hDp) == Logic.W_FULL) {  // the bar moves every half hour
            b.append('|').append(m.tl).append('|').append(Logic.index(m.base, now)).append('|').append(n.since);
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
        boolean live = m != null && !m.gone;
        int fill = live ? pal.soft(m.now.state) : pal.card;
        Brush b = new Brush(pal, fill).on(new Canvas(bm));
        float r = Math.min(24 * s, f.h / 2.6f);
        b.p.setColor(fill);  // one fill for the small and the large widget, as on the cards of the app
        b.r.set(0f, 0f, (float) f.w, (float) f.h);
        b.cv.drawRoundRect(b.r, r, r, b.p);
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

    /** Two segments, as the large widget has: on the left what is now, on the right the place and what comes next. */
    private static void one(Context c, Brush b, Model m, long now, Frame f, boolean full) {
        if (full) { large(c, b, m, now, f); return; }
        Logic.Now n = m.now;
        float s = f.s;
        Ui.Pal pal = b.pal;
        float pad = f.h * .13f;
        float rowH = f.h - 2 * pad;
        float left = Logic.widgetBulbSegment(Math.round(f.w / s)) * s;
        String[] next = future(m);

        float wordSize = Math.max(10 * s, Math.min(14 * s, rowH * .19f));
        Paint word = text(Ui.heavy(c), wordSize, pal.status(n.state), Paint.Align.CENTER);
        String state = Panes.compactTitle(n.state);
        fit(word, state, left - 6 * s);
        float base = f.h - pad;
        float bulb = Math.min(left * .8f, rowH - wordSize * 1.1f);
        BulbView.plain(b, n.state, left / 2 + 2 * s, pad + (rowH - wordSize) / 2, bulb);
        b.cv.drawText(state, left / 2 + 2 * s, base, word);
        b.p.setColor(Ui.fade(pal.text, .18f));
        b.cv.drawRect(left + 3 * s, pad, left + 4 * s, f.h - pad, b.p);

        float tx = left + 11 * s;
        float tw = f.w - tx - pad * .8f;
        boolean clock = next[1].contains(":");
        Paint value = text(clock ? Ui.display(c) : Ui.heavy(c), 100, pal.status(n.next), Paint.Align.LEFT);
        float[] sized = Logic.widgetRowSizes(rowH, clock ? sizeToFit(value, next[1], tw) : Float.MAX_VALUE);
        value.setTextSize(clock ? sized[2] : sized[0] * 1.2f);
        fit(value, next[1], tw);
        if (!clock && n.next == Logic.UNKNOWN) value.setColor(n.state == Logic.ON ? pal.status(Logic.ON) : pal.muted);
        Paint name = fit(text(Ui.heavy(c), sized[0], pal.text, Paint.Align.LEFT), widgetName(m), tw);
        Paint event = fit(text(Ui.heavy(c), sized[1], n.next == Logic.UNKNOWN ? pal.muted : pal.status(n.next), Paint.Align.LEFT), next[0], tw);
        float block = Logic.widgetRowBlock(sized);
        float y = (f.h - block) / 2f;
        capLine(b, widgetName(m), tx, y, name);
        y += .74f * sized[0] + sized[3];
        capLine(b, next[0], tx, y, event);
        b.cv.drawText(next[1], tx, (f.h + block) / 2f, value);
    }

    /** What comes next, for both forms of the widget: a day of no schedule names the last one it knows. */
    private static String[] future(Model m) {
        if (m.now.big.equals("відкрийте застосунок")) return new String[] {m.now.title, m.now.big, ""};
        if (m.now.state == Logic.UNKNOWN && !m.lastDay.isEmpty()) return new String[] {"Останній графік", m.lastDay, ""};
        return Logic.widgetFuture(m.now);
    }

    /** A line whose capitals start at y, measured by the size it was meant to have; returns its baseline. */
    private static float capLine(Brush b, String words, float x, float y, Paint p) {
        float baseline = y + p.getTextSize() * .74f;
        b.cv.drawText(words, x, baseline, p);
        return baseline;
    }

    /** The whole card works: the name and today's total, both panes as large as they fit, tomorrow, the bar. */
    private static void large(Context c, Brush b, Model m, long now, Frame f) {
        Logic.WidgetLayout w = Logic.widgetLayout(Math.round(f.w / f.s), Math.round(f.h / f.s), true);
        float s = f.s;
        float pad = w.pad * s;
        String[] next = future(m);
        boolean said = next[0].startsWith("Завтра") || next[0].startsWith("Сьогодні й завтра");
        boolean tomorrow = w.tomorrowHeight > 0 && !said;
        float top = w.rowTop * s;
        float freed = tomorrow ? 0 : w.tomorrowHeight * s;  // no line under the bar: everything above takes its place
        float room = w.rowHeight * s + freed;
        header(c, b, m, f, w);
        panes(c, b, m, next, new float[] {pad, top, w.columnWidth * s, room, s});
        boolean secondBar = tomorrow && !Logic.widgetTomorrowMissing(dayOf(m.tl, 48));
        if (tomorrow && !secondBar) tomorrowLine(c, b, m, new float[] {pad, w.tomorrowTop * s, f.w - 2 * pad, w.tomorrowHeight * s});
        float labels = (secondBar ? w.tomorrowBarTop + w.tomorrowBarHeight : w.barTop + w.barHeight) * s + freed;
        b.r2.set(pad, w.barTop * s + freed, f.w - pad, (w.barTop + w.barHeight) * s + freed);
        bar(c, b, m.base, m.tl, now, b.r2, new float[] {s, w.labelSize * s, labels});
        if (secondBar) tomorrowBar(c, b, m, new RectF(pad, w.tomorrowBarTop * s, f.w - pad, (w.tomorrowBarTop + w.tomorrowBarHeight) * s), s);
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
        Paint side = text(Ui.bold(c), w.headerSize * .9f * s, b.pal.muted, Paint.Align.RIGHT);
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
        new Panes(c, b, m, next, box).draw();
    }

    /** The two columns of a large widget: first the largest sizes that fit, then the drawing. */
    private static final class Panes {
        private final Brush b;
        private final Logic.Now now;
        private final String[] start;
        private final String[] next;
        private final float x;
        private final float top;
        private final float width;
        private final float room;
        private final float s;
        private final float gapX;
        private final Paint small;
        private final boolean inline;
        private final Paint title;
        private final Paint label;
        private final Paint left;
        private final Paint right;
        private final float leftSpare;
        private final float rightSpare;

        Panes(Context c, Brush b, Model m, String[] next, float[] box) {
            this.b = b;
            this.now = m.now;
            this.next = next;
            start = Logic.widgetStart(m.now);
            x = box[0];
            top = box[1];
            width = box[2];
            room = box[3];
            s = box[4];
            gapX = 6 * s;
            float detail = Math.max(12 * s, Math.min(16 * s, room * .2f));
            small = text(Ui.bold(c), detail, b.pal.muted, Paint.Align.LEFT);
            leftSpare = spare(start[1]);
            rightSpare = spare(next[2]);
            boolean leftClock = start[0].contains(":");
            boolean rightClock = next[1].contains(":");
            Paint clock = text(Ui.display(c), 100, b.pal.text, Paint.Align.LEFT);
            float beside = Math.min(room * .54f, Math.min(clockSize(clock, leftClock, start[0], leftSpare),
                    clockSize(clock, rightClock, next[1], rightSpare)));
            float stacked = Math.min(room * .42f, Math.min(clockSize(clock, leftClock, start[0], 0),
                    clockSize(clock, rightClock, next[1], 0)));
            inline = leftSpare + rightSpare == 0 || beside >= stacked;
            float value = inline ? beside : stacked;
            float head = Math.min(value * .8f, room * (inline ? .30f : .24f));
            title = text(Ui.heavy(c), head, b.pal.status(now.state), Paint.Align.LEFT);
            label = text(Ui.heavy(c), head, m.now.next == Logic.UNKNOWN ? b.pal.muted : b.pal.status(m.now.next), Paint.Align.LEFT);
            left = text(leftClock ? Ui.display(c) : Ui.bold(c), leftClock ? value : head, b.pal.text, Paint.Align.LEFT);
            right = text(rightClock ? Ui.display(c) : Ui.bold(c), rightClock ? value : head, b.pal.status(now.next), Paint.Align.LEFT);
        }

        private float spare(String words) {
            return words.isEmpty() ? 0 : small.measureText(words) + gapX;
        }

        /** The size at which a clock fits beside `spare`; words that are no clock do not limit it. */
        private float clockSize(Paint clock, boolean isClock, String words, float spare) {
            return isClock ? sizeToFit(clock, words, Math.max(1, width - spare)) : Float.MAX_VALUE;
        }

        void draw() {
            float tall = Math.max(height(left), height(right));
            float first = height(title);
            float third = inline ? 0 : height(small);
            float used = first + tall + third;
            if (used > room) {  // never past the box: everything shrinks alike
                float k = room / used;
                for (Paint p : new Paint[] {title, label, left, right, small}) p.setTextSize(p.getTextSize() * k);
                first *= k;
                tall *= k;
                third *= k;
                used = room;
            }
            float spare = room - used;
            int gaps = third > 0 ? 2 : 1;
            float gap = Math.min(spare / (gaps + 2), first * .35f);
            float y = top + (spare - gap * gaps) / 2;
            float rightX = x + width + 8 * s;
            firstRow(y, first, rightX);
            y += first + gap;
            float baseline = y + tall - Math.max(left.descent(), right.descent());
            clocks(baseline, rightX);
            if (third > 0) details(y + tall + gap, rightX);
            b.p.setColor(b.pal.line);
            b.cv.drawRect(rightX - 5 * s, top, rightX - 4 * s, top + room, b.p);
        }

        /** The bulb and the state on the left, what comes next on the right. */
        private void firstRow(float y, float rowHeight, float rightX) {
            float bulb = Math.min(rowHeight * 1.25f, room * .4f);
            float titleRoom = width - bulb * 1.16f;
            String state = sizeToFit(title, now.title, titleRoom) >= 13 * s ? now.title : compactTitle(now.state);
            BulbView.plain(b, now.state, x + bulb / 2, y + rowHeight / 2, bulb);
            fit(title, state, titleRoom);
            drawCentred(b, state, x + bulb * 1.16f, y, rowHeight, title);
            String event = next[0];
            if (sizeToFit(label, event, width) < 12 * s && now.next != Logic.UNKNOWN) {
                event = compactEvent(now) + (now.tomorrow ? " завтра" : "");
            }
            fit(label, event, width);
            drawCentred(b, event, rightX, y, rowHeight, label);
        }

        private void clocks(float baseline, float rightX) {
            fit(left, start[0], width);
            fit(right, next[1], width);
            b.cv.drawText(start[0], x, baseline, left);
            b.cv.drawText(next[1], rightX, baseline, right);
            if (!inline) return;
            if (leftSpare > 0) b.cv.drawText(start[1], x + left.measureText(start[0]) + gapX, baseline, small);
            if (rightSpare > 0) b.cv.drawText(next[2], rightX + right.measureText(next[1]) + gapX, baseline, small);
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

        private void details(float y, float rightX) {
            if (leftSpare > 0) line(b, start[1], x, y, fit(small, start[1], width));
            if (rightSpare > 0) line(b, next[2], rightX, y, fit(small, next[2], width));
        }
    }

    private static void drawCentred(Brush b, String words, float x, float y, float rowHeight, Paint p) {
        b.cv.drawText(words, x, y + (rowHeight - height(p)) / 2 - p.ascent(), p);
    }

    /** box: x, y, width, height. All of tomorrow's outages when they fit, otherwise their count and total. */
    private static void tomorrowLine(Context c, Brush b, Model m, float[] box) {
        String day = dayOf(m.tl, 48);
        String[] words = Logic.widgetTomorrow(day);
        boolean missing = Logic.widgetTomorrowMissing(day);  // nothing to read here: it stays out of the way
        Paint p = text(Ui.body(c), box[3] * (missing ? .56f : .7f), missing ? Ui.fade(b.pal.muted, .6f) : b.pal.text, Paint.Align.LEFT);
        String shown = words[sizeToFit(p, words[0], box[2]) >= p.getTextSize() * .8f ? 0 : 1];
        fit(p, shown, box[2]);
        drawCentred(b, shown, box[0], box[1], box[3], p);
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
        b.path.reset();
        b.path.addRoundRect(bounds, bounds.height() / 2, bounds.height() / 2, android.graphics.Path.Direction.CW);
        b.cv.save();
        b.cv.clipPath(b.path);
        b.p.setColor(Ui.fade(b.pal.card, b.pal.dark ? 0.68f : 0.65f));
        b.cv.drawRect(bounds.left, bounds.top, marker, bounds.bottom, b.p);
        b.cv.restore();
        hourLines(b, bounds, s);
        b.p.setColor(b.pal.text);
        b.cv.drawRect(marker - s, bounds.top - 2*s, marker + s, bounds.bottom + 2*s, b.p);
        Paint lab = text(Ui.bold(c), scale[1], b.pal.muted, Paint.Align.CENTER);
        for (int hour = 0; hour <= 24; hour += 6) {
            lab.setTextAlign(hourAlign(hour));
            b.cv.drawText(String.format(Locale.ROOT, "%02d", hour), bounds.left + bounds.width() * hour/24,
                    scale[2] + scale[1] + 1*s, lab);
        }
    }

    /** Tomorrow under today, on the same hours; it names itself, since it has no marker of now. */
    private static void tomorrowBar(Context c, Brush b, Model m, RectF bounds, float s) {
        BarView.runs(b, m.tl, 48, 48, 0, new RectF(bounds), 1.5f * s);
        hourLines(b, bounds, s);
        char first = m.tl.charAt(48);
        int ink = first == '3' ? b.pal.accentInk : b.pal.ink;
        if (first == '2') ink = b.pal.muted;
        Paint p = text(Ui.heavy(c), bounds.height() * .72f, ink, Paint.Align.LEFT);
        b.cv.drawText("завтра", bounds.left + bounds.height() * .5f, bounds.centerY() - (p.descent() + p.ascent()) / 2f, p);
    }

    private static void hourLines(Brush b, RectF bounds, float s) {
        b.path.reset();
        b.path.addRoundRect(bounds, bounds.height() / 2, bounds.height() / 2, android.graphics.Path.Direction.CW);
        b.cv.save();
        b.cv.clipPath(b.path);
        b.p.setColor(Ui.fade(b.pal.text, .14f));
        b.p.setStrokeWidth(Math.max(1, s * .7f));
        for (int hour = 1; hour < 24; hour++) {
            float x = bounds.left + bounds.width() * hour / 24;
            b.cv.drawLine(x, bounds.top, x, bounds.bottom, b.p);
        }
        b.cv.restore();
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
