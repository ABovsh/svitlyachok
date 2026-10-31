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
        labelH = Math.round(Ui.sp(c, 12) * 1.25f + Ui.px(c, 6));
        hours.setTypeface(Ui.heavy(c));
        hours.setTextSize(Ui.sp(c, 14));
        hours.setTextAlign(Paint.Align.CENTER);
        clock.setTextSize(Ui.sp(c, 12));
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
        Context c = getContext();
        float w = (float) getWidth();
        float sw = w / 48f;
        float top = knob;
        box.set(0, top, w, top + barH);
        float r = Ui.px(c, 10);
        clip.reset();
        clip.addRoundRect(box, r, r, Path.Direction.CW);
        cv.save();
        cv.clipPath(clip);
        p.setStyle(Paint.Style.FILL);
        int i = 0;
        while (i < 48) {
            char ch = at(slots, i);
            int j = i;
            while (j < 48 && at(slots, j) == ch) j++;
            p.setColor(Ui.blend(pal.card, Ui.fade(pal.fill(Logic.state(ch)), 0.82f)));
            cv.drawRect(i * sw, top, j * sw, top + barH, p);
            if (ch == '3') {
                b.r2.set(i * sw, top, j * sw, top + barH);
                stripes(b.on(cv), b.r2);
            }
            i = j;
        }
        // a thin line every hour, a little stronger every six; a darker one between runs
        p.setStrokeWidth(Math.max(1f, Ui.px(c, 0.8f)));
        for (int h = 1; h < 24; h++) {
            p.setColor(pal.dark ? (h % 6 == 0 ? 0x38FFFFFF : 0x1EFFFFFF) : (h % 6 == 0 ? 0x30FFFFFF : 0x1CFFFFFF));
            cv.drawLine(h * 2 * sw, top, h * 2 * sw, top + barH, p);
        }
        p.setStrokeWidth(Ui.px(c, 1.2f));
        p.setColor(Ui.fade(pal.card, 0.55f));
        for (int k = 1; k < 48; k++) {
            if (at(slots, k) != at(slots, k - 1)) cv.drawLine(k * sw, top, k * sw, top + barH, p);
        }
        if (nowRel > 0) {  // the past of today, veiled
            p.setColor(Ui.fade(pal.card, pal.dark ? 0.5f : 0.45f));
            cv.drawRect(0, top, Math.min(w, nowRel * sw), top + barH, p);
        }
        cv.restore();
        runHours(cv, sw, top);
        clocks(cv, w, sw, top);
        if (nowRel >= 0 && nowRel <= 48) {
            float x = nowRel * sw;
            float rr = Ui.px(c, 1.5f);
            p.setColor(pal.card);
            mark.set(x - rr - 1, top - Ui.px(c, 2), x + rr + 1, top + barH + Ui.px(c, 2));
            cv.drawRoundRect(mark, rr, rr, p);
            p.setColor(pal.text);
            mark.set(x - rr, top - Ui.px(c, 2), x + rr, top + barH + Ui.px(c, 2));
            cv.drawRoundRect(mark, rr, rr, p);
            cv.drawCircle(x, top - Ui.px(c, 2), Ui.px(c, 5), p);
        }
    }

    /** "3,5г" inside every run that has room; in the run of now, on its part still ahead. */
    private void runHours(Canvas cv, float sw, float top) {
        Context c = getContext();
        float room = Ui.px(c, 8);
        float base = top + barH / 2f - (hours.descent() + hours.ascent()) / 2f;
        int i = 0;
        while (i < 48) {
            char ch = at(slots, i);
            int j = i;
            while (j < 48 && at(slots, j) == ch) j++;
            if (ch != '2') {
                String t = Logic.barHours(j - i);
                float tw = hours.measureText(t);
                float x0 = i * sw;
                float x1 = j * sw;
                float nx = nowRel * sw;
                if (nowRel > i && nowRel < j) {
                    if (x1 - nx >= tw + room) x0 = nx;
                    else if (nx - x0 >= tw + room) x1 = nx;
                }
                if (x1 - x0 >= tw + room) {
                    int ink = ch == '3' ? pal.accentInk : 0xFFFFFFFF;
                    boolean past = nowRel >= j || nowRel > i && x1 <= nowRel * sw + 0.5f;
                    hours.setColor(past ? Ui.fade(ink, 0.5f) : ink);
                    cv.drawText(t, (x0 + x1) / 2f, base, hours);
                }
            }
            i = j;
        }
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
