package ua.abovsh.svitlo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import java.util.List;

/**
 * One day as a bar of runs, 00–24: the hours of each run written inside it, a thin line every
 * hour, the past veiled, a marker at now; under it the clock times of the changes (red when the
 * power goes, green when it comes back) and grey hours where there is room.
 */
final class BarView extends View {
    private final Brush b;
    private final Paint p;
    private final Paint hours = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint clock = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Ui.Pal pal;
    private final int barH;
    private final int knob;
    private final int labelH;
    private final RectF box = new RectF();

    private final RectF mark = new RectF();
    private final Path clip = new Path();
    private final Paint picked = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String slots;
    private float nowRel = -1;
    private int chosenFrom = -1;
    private int chosenTo = -1;
    private int touched = -1;
    private boolean pressed;

    BarView(Context c, Ui.Pal pal) {
        this(c, pal, 7);
    }

    /** knobDp: the room over the bar for the dot of now; a day that is not today needs none. */
    BarView(Context c, Ui.Pal pal, int knobDp) {
        super(c);
        this.pal = pal;
        b = new Brush(pal, pal.card);
        p = b.p;
        barH = Ui.dp(c, 28);
        knob = Ui.dp(c, knobDp);
        labelH = Math.round(Ui.sp(c, 13) * 1.25f + Ui.px(c, 6));
        hours.setTypeface(Ui.heavy(c));
        hours.setTextSize(Ui.sp(c, 14));
        hours.setTextAlign(Paint.Align.CENTER);
        clock.setTextSize(Ui.sp(c, 13));
        clock.setTextAlign(Paint.Align.CENTER);
        clock.setTypeface(Ui.heavy(c));
        picked.setStyle(Paint.Style.STROKE);
        picked.setStrokeWidth(Ui.px(c, 2));
        picked.setColor(pal.text);
    }

    /** Where the bar itself begins, for what is drawn over it. */
    int barTop() {
        return knob;
    }

    /** The run shown as chosen; -1 for none. */
    void choose(int from, int to) {
        chosenFrom = from;
        chosenTo = to;
        invalidate();
    }

    /** The half hour of the last touch. */
    int touchedSlot() {
        return touched;
    }

    @Override
    public boolean onTouchEvent(android.view.MotionEvent e) {
        if (!hasOnClickListeners() || slots == null) return false;
        switch (e.getActionMasked()) {
            case android.view.MotionEvent.ACTION_DOWN:
                pressed = e.getY() <= knob + barH + Ui.px(getContext(), 4);
                return pressed;
            case android.view.MotionEvent.ACTION_UP:
                if (pressed) {
                    touched = (int) (e.getX() / (float) getWidth() * 48);
                    performClick();
                }
                pressed = false;
                return true;
            case android.view.MotionEvent.ACTION_CANCEL:
                pressed = false;
                return true;
            default:
                return pressed;
        }
    }

    @Override
    public boolean performClick() {
        pressed = false;
        return super.performClick();
    }

    /** nowRel: position of now in slots, -1 for a day that is not today. */
    void set(String slots, float nowRel) {
        boolean remeasure = this.slots == null || !this.slots.equals(slots);
        this.slots = slots;
        this.nowRel = nowRel;
        if (remeasure) requestLayout();
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        int width = MeasureSpec.getSize(w);
        int lines = slots == null ? 1 : Logic.barLabelLines(labels((float) width));
        setMeasuredDimension(width, knob + barH + (lines == 0 ? Ui.dp(getContext(), 3) : lines * labelH));
    }

    @Override
    protected void onDraw(Canvas cv) {
        if (slots == null) return;
        float w = (float) getWidth();
        float sw = w / 48f;
        float top = (float) knob;
        box.set(0, top, w, top + barH);
        float r = Ui.px(getContext(), 10);
        clip.reset();
        clip.addRoundRect(box, r, r, Path.Direction.CW);
        cv.save();
        cv.clipPath(clip);
        fills(cv, sw, top);
        lines(cv, sw, top);
        if (nowRel > 0) {  // the past of today, veiled
            p.setColor(Ui.fade(pal.card, pal.veil));
            cv.drawRect(0, top, Math.min(w, nowRel * sw), top + barH, p);
        }
        cv.restore();
        clocks(cv, sw, top);
        if (nowRel >= 0 && nowRel <= 48) marker(cv, nowRel * sw, top);
        int i = 0;
        while (i < 48) {  // above the marker: a number is never hidden by it
            int j = end(i);
            if (at(slots, i) != '2') runHours(cv, i, j, sw, top);
            i = j;
        }
        if (chosenFrom >= 0) {
            float inset = picked.getStrokeWidth() / 2;
            box.set(chosenFrom * sw + inset, top + inset, chosenTo * sw - inset, top + barH - inset);
            float rr = Ui.px(getContext(), 6);
            cv.drawRoundRect(box, rr, rr, picked);
        }
    }

    private List<Logic.Label> labels(float width) {
        Context c = getContext();
        return Logic.barLabelRows(slots, nowRel >= 0 ? (int) nowRel : -1, width, clock.measureText("00:00"), Ui.px(c, 5));
    }

    /** Where the run starting at slot i ends. */
    private int end(int i) {
        char ch = at(slots, i);
        int j = i;
        while (j < 48 && at(slots, j) == ch) j++;
        return j;
    }

    /** Each run in its colour, softened over the card; a possible outage striped. */
    private void fills(Canvas cv, float sw, float top) {
        p.setStyle(Paint.Style.FILL);
        int i = 0;
        while (i < 48) {
            char ch = at(slots, i);
            int j = end(i);
            p.setColor(Ui.blend(pal.card, Ui.fade(pal.fill(Logic.state(ch)), 0.96f)));
            cv.drawRect(i * sw, top, j * sw, top + barH, p);
            if (ch == '3') {
                b.r2.set(i * sw, top, j * sw, top + barH);
                stripes(b.on(cv), b.r2);
            }
            i = j;
        }
    }

    /** A thin line every hour, a little stronger every six; a darker one between runs. */
    private void lines(Canvas cv, float sw, float top) {
        Context c = getContext();
        int hour = pal.dark ? 0x1EFFFFFF : 0x1CFFFFFF;
        int six = pal.dark ? 0x38FFFFFF : 0x30FFFFFF;
        p.setStrokeWidth(Math.max(1f, Ui.px(c, 0.8f)));
        for (int h = 1; h < 24; h++) {
            p.setColor(h % 6 == 0 ? six : hour);
            cv.drawLine(h * 2 * sw, top, h * 2 * sw, top + barH, p);
        }
        p.setStrokeWidth(Ui.px(c, 1.2f));
        p.setColor(Ui.fade(pal.card, 0.55f));
        for (int k = 1; k < 48; k++) {
            if (at(slots, k) != at(slots, k - 1)) cv.drawLine(k * sw, top, k * sw, top + barH, p);
        }
    }

    /** Now: a line across the bar with a dot on top. */
    private void marker(Canvas cv, float x, float top) {
        Context c = getContext();
        float rr = Ui.px(c, 1.5f);
        float out = Ui.px(c, 2);
        float edge = Ui.px(c, 1);
        p.setColor(Ui.Pal.MARKER_EDGE);
        mark.set(x - rr - edge, top - out - edge, x + rr + edge, top + barH + out + edge);
        cv.drawRoundRect(mark, rr + edge, rr + edge, p);
        cv.drawCircle(x, top - out, Ui.px(c, 5) + edge, p);
        p.setColor(Ui.Pal.MARKER);
        mark.set(x - rr, top - out, x + rr, top + barH + out);
        cv.drawRoundRect(mark, rr, rr, p);
        cv.drawCircle(x, top - out, Ui.px(c, 5), p);
    }

    /** The hours of the whole run, in its middle; the marker may pass right under them. */
    private void runHours(Canvas cv, int i, int j, float sw, float top) {
        float room = Ui.px(getContext(), 8);
        String t = Logic.barHoursWord(j - i);
        if ((j - i) * sw < hours.measureText(t) + room) t = Logic.barHours(j - i);
        if ((j - i) * sw < hours.measureText(t) + room) return;
        float centre = Logic.barHoursCentre(i, j);
        float x = centre * sw;
        hours.setColor(hoursInk(Logic.barHoursInk(at(slots, i), nowRel >= 0 && centre < nowRel, Logic.barHoursActive(i, j, nowRel), pal.dark)));
        float base = top + barH / 2f - (hours.descent() + hours.ascent()) / 2f;
        cv.drawText(t, x, base, hours);
    }

    private int hoursInk(int ink) {
        if (ink == Logic.INK_QUIET) return pal.muted;
        return ink == Logic.INK_DARK ? pal.accentInk : 0xFFFFFFFF;
    }

    /** The clock times of the changes under the bar, each with a short tick at its exact place. */
    private void clocks(Canvas cv, float sw, float top) {
        Context c = getContext();
        float y = top + barH + Ui.px(c, 5) - clock.ascent();
        for (Logic.Label l : labels((float) getWidth())) {
            int colour = pal.status(l.kind);
            clock.setColor(colour);
            float line = (float) l.row * labelH;
            cv.drawText(Logic.clock(l.slot), l.x, y + line, clock);
            if (l.slot > 0 && l.slot < 48) {
                p.setColor(colour);
                float x = l.slot * sw;
                cv.drawRect(x - Ui.px(c, 0.75f), top + barH + Ui.px(c, 1), x + Ui.px(c, 0.75f), top + barH + Ui.px(c, 4) + line, p);
            }
        }
    }

    /**
     * Runs of `count` slots from `from` (past the data: unknown), shifted left by `frac` of a
     * slot, inside a rounded box. A possible outage is striped so it reads without colour.
     */
    static void runs(Brush b, String slots, int from, int count, float frac, RectF box, float gap) {
        Canvas cv = b.cv;
        Paint p = b.p;
        float sw = box.width() / count;
        float r = box.height() / 2f;
        b.path.reset();
        b.path.addRoundRect(box, r, r, Path.Direction.CW);
        cv.save();
        cv.clipPath(b.path);
        p.setStyle(Paint.Style.FILL);
        int i = 0;
        int n = count + (frac > 0 ? 1 : 0);
        while (i < n) {
            char c = at(slots, from + i);
            int j = i;
            while (j < n && at(slots, from + j) == c) j++;
            float x0 = Math.max(box.left, box.left + (i - frac) * sw + (i > 0 ? gap / 2f : 0));
            float x1 = box.left + (j - frac) * sw - (j < n ? gap / 2f : 0);
            p.setColor(b.pal.slot(c));
            cv.drawRect(x0, box.top, x1, box.bottom, p);
            if (c == '3') {
                b.r2.set(x0, box.top, x1, box.bottom);
                stripes(b, b.r2);
            }
            i = j;
        }
        cv.restore();
    }

    private static char at(String s, int i) {
        return i >= 0 && i < s.length() ? s.charAt(i) : '2';
    }

    /** A possible outage reads without colour too: amber stripes. */
    static void stripes(Brush b, RectF r) {
        Paint p = b.p;
        b.cv.save();
        b.cv.clipRect(r);
        p.setColor(Ui.fade(b.pal.maybe, 0.85f));
        p.setStyle(Paint.Style.STROKE);
        float h = r.height();
        float step = Math.max(5f, h * 0.55f);
        p.setStrokeWidth(step * 0.32f);
        for (float x = r.left - h; x < r.right + h; x += step) b.cv.drawLine(x, r.bottom, x + h, r.top, p);
        p.setStyle(Paint.Style.FILL);
        b.cv.restore();
    }
}
