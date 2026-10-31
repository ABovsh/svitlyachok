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
    private final Brush b;
    private final Paint p;
    private final Paint tp = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Ui.Pal pal;
    private final int barH;
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
        barH = Ui.dp(c, 14);
        labelH = Ui.dp(c, 16);
        tp.setTypeface(Ui.bold(c));
        tp.setTextSize(Ui.px(c, 10.5f));
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
        float w = (float) getWidth();
        float top = Ui.px(getContext(), 3);
        float sw = w / 48f;
        box.set(0, top, w, top + barH);
        runs(b.on(cv), slots, 0, 48, 0, box, Ui.px(getContext(), 2));
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
        float ty = top + barH + labelH - Ui.px(getContext(), 2);
        for (int h = 0; h <= 24; h += 6) {
            String lab = String.format(Locale.ROOT, "%02d", h);
            float x = h * 2 * sw;
            float half = tp.measureText(lab) / 2;
            cv.drawText(lab, Math.max(half, Math.min(w - half, x)), ty, tp);
        }
        if (nowRel >= 0 && nowRel <= 48) {
            float x = nowRel * sw;
            float mw = Ui.px(getContext(), 5);
            float rr = Ui.px(getContext(), 1.75f);
            float out = Ui.px(getContext(), 3);
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
