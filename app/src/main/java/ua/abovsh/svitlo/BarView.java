package ua.abovsh.svitlo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** One day as two rows of twelve hour cells; each hour preserves both of its half-hour states. */
final class BarView extends View {
    private static final String[] HOURS = {"00", "01", "02", "03", "04", "05", "06", "07", "08", "09", "10", "11", "12", "13", "14", "15", "16", "17", "18", "19", "20", "21", "22", "23"};
    private final Brush b;
    private final RectF drawBounds = new RectF();
    private final Paint digits = new Paint(Paint.ANTI_ALIAS_FLAG);
    private String slots;
    private float nowRel = -1;
    private int chosenFrom = -1;
    private int chosenTo = -1;
    private int touched = -1;

    BarView(Context c, Ui.Pal pal) {
        super(c);
        b = new Brush(pal, pal.card);
        digits.setTypeface(Ui.heavy(c));
        digits.setTextSize(Ui.sp(c, 14));
    }

    int touchedSlot() { return touched; }

    float slotX(float slot) {
        int hour = Math.min(23, (int) (slot / 2));
        return (hour % 12 + .5f) * getWidth() / 12f;
    }

    void choose(int from, int to) { chosenFrom = from; chosenTo = to; invalidate(); }

    void set(String slots, float nowRel) {
        this.slots = slots;
        this.nowRel = nowRel;
        invalidate();
    }

    @Override protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), Ui.dp(getContext(), 55));
    }

    @Override public boolean onTouchEvent(android.view.MotionEvent e) {
        if (!hasOnClickListeners() || slots == null) return false;
        if (e.getActionMasked() == android.view.MotionEvent.ACTION_UP) {
            touched = Logic.hourGridSlot(e.getX(), e.getY(), (float) getWidth(), (float) getHeight());
            performClick();
        }
        return true;
    }

    @Override public boolean performClick() { super.performClick(); return true; }

    @Override protected void onDraw(Canvas cv) {
        if (slots == null) return;
        b.on(cv);
        drawBounds.set(0, 0, (float) getWidth(), (float) getHeight());
        digits.setTextSize(Ui.sp(getContext(), 14));
        grid(b, slots, nowRel, drawBounds, digits, chosenFrom, chosenTo);
    }

    /** Shared app/widget grid: exact halves, readable hour numbers, possible hatching and unknown cells. */
    static void grid(Brush b, String slots, float nowRel, RectF bounds, Paint text, int from, int to) {
        float size = text.getTextSize();
        float gap = bounds.height() * 3 / 55;
        text.setTextAlign(Paint.Align.CENTER);
        float cellW = (bounds.width() - gap * 11) / 12;
        float cellH = (bounds.height() - gap) / 2;
        float widest = 0;
        for (String hour : HOURS) widest = Math.max(widest, text.measureText(hour));
        text.setTextSize(Math.min(size, Math.min((cellW - gap) / widest * size, cellH * .63f)));
        for (int hour = 0; hour < 24; hour++) {
            float left = bounds.left + hour % 12 * (cellW + gap);
            int row = hour / 12;
            float top = bounds.top + (float) row * (cellH + gap);
            RectF cell = b.r;
            cell.set(left, top, left + cellW, top + cellH);
            float radius = Math.min(cellW, cellH) * .22f;
            b.path.reset(); b.path.addRoundRect(cell, radius, radius, android.graphics.Path.Direction.CW);
            b.cv.save(); b.cv.clipPath(b.path);
            fillHour(b, slots, hour, cell);
            b.cv.restore();
            text.setColor(at(slots, hour * 2) == '2' && at(slots, hour * 2 + 1) == '2' ? b.pal.muted : 0xFFFFFFFF);
            float baseline = cell.centerY() - (text.ascent() + text.descent()) / 2;
            b.cv.drawText(HOURS[hour], cell.centerX(), baseline, text);
            boolean current = nowRel >= hour * 2 && nowRel < hour * 2 + 2;
            boolean selected = from >= 0 && hour * 2 < to && hour * 2 + 2 > from;
            if (current || selected) {
                b.p.setStyle(Paint.Style.STROKE); b.p.setStrokeWidth(Math.max(1, gap * .55f));
                b.p.setColor(selected ? b.pal.text : Ui.Pal.MARKER);
                cell.inset(b.p.getStrokeWidth() / 2, b.p.getStrokeWidth() / 2);
                b.cv.drawRoundRect(cell, radius, radius, b.p);
                b.p.setStyle(Paint.Style.FILL);
            }
        }
    }

    private static void fillHour(Brush b, String slots, int hour, RectF cell) {
        for (int half = 0; half < 2; half++) {
            char state = at(slots, hour * 2 + half);
            RectF part = b.r2;
            part.set(cell.left + half * cell.width() / 2, cell.top, cell.left + (half + 1) * cell.width() / 2, cell.bottom);
            b.p.setStyle(Paint.Style.FILL); b.p.setColor(b.pal.fill(Logic.state(state)));
            b.cv.drawRect(part, b.p);
            if (state == '3') stripes(b, part);
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
