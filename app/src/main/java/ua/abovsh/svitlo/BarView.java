package ua.abovsh.svitlo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import java.util.Locale;

/** One day as a bar of half-hour runs, 00–24, with the hours already past veiled and a now marker. */
final class BarView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Ui.Pal pal;
    private final int barH, labelH;
    private final RectF box = new RectF(), mark = new RectF();
    private final Path clip = new Path();
    private String slots;
    private float nowRel = -1;

    BarView(Context c, Ui.Pal pal) {
        super(c);
        this.pal = pal;
        barH = Ui.dp(c, 14);
        labelH = Ui.dp(c, 16);
        tp.setTypeface(Ui.bold(c));
        tp.setTextSize(Ui.dp(c, 10.5f));
        tp.setTextAlign(Paint.Align.CENTER);
        tp.setColor(pal.dim);
    }

    /** nowRel: position of now in slots, -1 for a day that is not today. */
    void set(String slots, float nowRel) {
        this.slots = slots;
        this.nowRel = nowRel;
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), Ui.dp(getContext(), 4) + barH + labelH);
    }

    @Override
    protected void onDraw(Canvas cv) {
        if (slots == null) return;
        float w = getWidth(), top = Ui.dp(getContext(), 3), sw = w / 48f;
        box.set(0, top, w, top + barH);
        runs(cv, p, pal, slots, 0, 48, 0, box, Ui.dp(getContext(), 2));
        if (nowRel > 0) {  // the past of today, veiled
            float r = barH / 2f;
            clip.reset();
            clip.addRoundRect(box, r, r, Path.Direction.CW);
            cv.save();
            cv.clipPath(clip);
            p.setColor((pal.card & 0x00FFFFFF) | (pal.dark ? 0x99000000 : 0x8C000000));
            cv.drawRect(0, top, Math.min(w, nowRel * sw), top + barH, p);
            cv.restore();
        }
        float ty = top + barH + labelH - Ui.dp(getContext(), 2);
        for (int h = 0; h <= 24; h += 6) {
            String lab = String.format(Locale.ROOT, "%02d", h);
            float x = h * 2 * sw, half = tp.measureText(lab) / 2;
            cv.drawText(lab, Math.max(half, Math.min(w - half, x)), ty, tp);
        }
        if (nowRel >= 0 && nowRel <= 48) {
            float x = nowRel * sw, mw = Ui.dp(getContext(), 5), rr = Ui.dp(getContext(), 1.75f);
            float out = Ui.dp(getContext(), 3);
            p.setColor(pal.card);
            mark.set(x - mw / 2, top - out, x + mw / 2, top + barH + out);
            cv.drawRoundRect(mark, mw, mw, p);
            p.setColor(pal.text);
            mark.set(x - rr, top - out + 1, x + rr, top + barH + out - 1);
            cv.drawRoundRect(mark, rr, rr, p);
        }
    }

    /**
     * Runs of `count` slots from `from` (past the data: unknown), shifted left by `frac` of a
     * slot, inside a rounded box. A possible outage is striped so it reads without colour.
     */
    static void runs(Canvas cv, Paint p, Ui.Pal pal, String slots, int from, int count, float frac, RectF box, float gap) {
        float sw = box.width() / count, r = box.height() / 2f;
        Path clip = new Path();
        clip.addRoundRect(box, r, r, Path.Direction.CW);
        cv.save();
        cv.clipPath(clip);
        p.setStyle(Paint.Style.FILL);
        int i = 0, n = count + (frac > 0 ? 1 : 0);
        while (i < n) {
            char c = at(slots, from + i);
            int j = i;
            while (j < n && at(slots, from + j) == c) j++;
            float x0 = box.left + (i - frac) * sw + (i > 0 ? gap / 2f : 0);
            float x1 = box.left + (j - frac) * sw - (j < n ? gap / 2f : 0);
            p.setColor(pal.slot(c));
            cv.drawRect(Math.max(box.left, x0), box.top, x1, box.bottom, p);
            if (c == '3') stripes(cv, p, pal, new RectF(Math.max(box.left, x0), box.top, x1, box.bottom));
            i = j;
        }
        cv.restore();
    }

    private static char at(String s, int i) {
        return i >= 0 && i < s.length() ? s.charAt(i) : '2';
    }

    static void stripes(Canvas cv, Paint p, Ui.Pal pal, RectF r) {
        cv.save();
        cv.clipRect(r);
        p.setColor(Ui.fade(pal.maybe, 0.85f));
        p.setStyle(Paint.Style.STROKE);
        float h = r.height(), step = Math.max(5f, h * 0.55f);
        p.setStrokeWidth(step * 0.32f);
        for (float x = r.left - h; x < r.right + h; x += step) cv.drawLine(x, r.bottom, x + h, r.top, p);
        p.setStyle(Paint.Style.FILL);
        cv.restore();
    }
}
