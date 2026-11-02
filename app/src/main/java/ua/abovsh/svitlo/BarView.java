package ua.abovsh.svitlo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

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
    private static final float PATCH_ALPHA = .86f;

    private final RectF mark = new RectF();
    private final Path clip = new Path();
    private String slots;
    private float nowRel = -1;

    BarView(Context c, Ui.Pal pal) {
        super(c);
        this.pal = pal;
        b = new Brush(pal, pal.card);
        p = b.p;
        barH = Ui.dp(c, 30);
        knob = Ui.dp(c, 7);
        labelH = Math.round(Ui.sp(c, 13) * 1.25f + Ui.px(c, 6));
        hours.setTypeface(Ui.heavy(c));
        hours.setTextSize(Ui.sp(c, 14));
        hours.setTextAlign(Paint.Align.CENTER);
        clock.setTextSize(Ui.sp(c, 13));
        clock.setTextAlign(Paint.Align.CENTER);
    }

    /** nowRel: position of now in slots, -1 for a day that is not today. */
    void set(String slots, float nowRel) {
        this.slots = slots;
        this.nowRel = nowRel;
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), knob + barH + labelH);
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
            p.setColor(Ui.fade(pal.card, pal.dark ? 0.68f : 0.65f));
            cv.drawRect(0, top, Math.min(w, nowRel * sw), top + barH, p);
        }
        cv.restore();
        clocks(cv, w, sw, top);
        if (nowRel >= 0 && nowRel <= 48) marker(cv, nowRel * sw, top);
        int i = 0;
        while (i < 48) {  // above the marker: a number is never hidden by it
            int j = end(i);
            if (at(slots, i) != '2') runHours(cv, i, j, sw, top);
            i = j;
        }
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

    /** Now: a white line across the bar with a dot on top. */
    private void marker(Canvas cv, float x, float top) {
        Context c = getContext();
        float rr = Ui.px(c, 1.5f);
        float out = Ui.px(c, 2);
        p.setColor(pal.card);
        mark.set(x - rr - 1, top - out, x + rr + 1, top + barH + out);
        cv.drawRoundRect(mark, rr, rr, p);
        p.setColor(pal.text);
        mark.set(x - rr, top - out, x + rr, top + barH + out);
        cv.drawRoundRect(mark, rr, rr, p);
        cv.drawCircle(x, top - out, Ui.px(c, 5), p);
    }

    /** The hours of the whole run, in its middle; where the marker reaches them it passes behind a patch. */
    private void runHours(Canvas cv, int i, int j, float sw, float top) {
        float room = Ui.px(getContext(), 8);
        String t = Logic.barHours(j - i);
        float tw = hours.measureText(t) + room;
        if ((j - i) * sw < tw) return;
        float centre = Logic.barHoursCentre(i, j);
        boolean veiled = nowRel >= 0 && centre < nowRel;
        char ch = at(slots, i);
        float x = centre * sw;
        if (Logic.barHoursCoverNow(i, j, nowRel, tw / 2f / sw)) {
            int fill = Ui.blend(pal.card, Ui.fade(pal.fill(Logic.state(ch)), 0.96f));
            if (veiled) fill = Ui.blend(fill, Ui.fade(pal.card, pal.dark ? 0.68f : 0.65f));
            p.setColor(Ui.fade(fill, PATCH_ALPHA));  // the marker only just shows through
            float half = hours.getTextSize() * .37f + Ui.px(getContext(), 3);
            float middle = top + barH / 2f;
            mark.set(x - tw / 2f, middle - half, x + tw / 2f, middle + half);
            cv.drawRoundRect(mark, Ui.px(getContext(), 5), Ui.px(getContext(), 5), p);
        }
        hours.setColor(hoursInk(ch, veiled, Logic.barHoursActive(i, j, nowRel)));
        float base = top + barH / 2f - (hours.descent() + hours.ascent()) / 2f;
        cv.drawText(t, x, base, hours);
    }

    /** A window that is over stays quiet; the one going on reads as strongly as those to come. */
    private int hoursInk(char ch, boolean veiled, boolean active) {
        if (veiled && !active) return pal.muted;
        if (ch == '3') return pal.accentInk;
        return veiled && !pal.dark ? pal.text : 0xFFFFFFFF;  // white on a veil of the light theme would not read
    }

    /** The clock times of the changes under the bar, each with a short tick at its exact place. */
    private void clocks(Canvas cv, float w, float sw, float top) {
        Context c = getContext();
        clock.setTypeface(Ui.heavy(c));
        float labelW = clock.measureText("00:00");
        int cur = nowRel >= 0 ? (int) nowRel : -1;
        float y = top + barH + Ui.px(c, 5) - clock.ascent();
        for (Logic.Label l : Logic.barLabels(slots, cur, w, labelW, Ui.px(c, 5))) {
            int colour = l.kind == Logic.UNKNOWN ? pal.muted : pal.status(l.kind);
            clock.setTypeface(l.kind == Logic.UNKNOWN ? Ui.bold(c) : Ui.heavy(c));
            clock.setColor(colour);
            cv.drawText(Logic.clock(l.slot), l.x, y, clock);
            if (l.kind != Logic.UNKNOWN && l.slot > 0 && l.slot < 48) {
                p.setColor(colour);
                float x = l.slot * sw;
                cv.drawRect(x - Ui.px(c, 0.75f), top + barH + Ui.px(c, 1), x + Ui.px(c, 0.75f), top + barH + Ui.px(c, 4), p);
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
