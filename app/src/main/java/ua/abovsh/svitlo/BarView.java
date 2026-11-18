package ua.abovsh.svitlo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import java.util.List;

/**
 * One day as two rows of twelve hour cells; each hour preserves both of its half-hour states. A run
 * chosen by a touch is framed whole, one frame per row it covers, and the rest of the day fades.
 */
final class BarView extends View {
    /** How much of the card covers an hour that has passed: between the bold 0.65 and none. */
    static final float PAST_VEIL = .50f;
    /** How much of the card covers what lies outside a chosen run, while its hint is shown. */
    static final float ASIDE_VEIL = .45f;
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
        digits.setTextSize(Ui.sp(c, 15.5f));
    }

    int touchedSlot() { return touched; }

    float slotX(float slot) {
        int hour = Math.min(23, (int) (slot / 2));
        float gap = columnGap((float) getHeight());
        float cell = (getWidth() - gap * 11) / 12;
        return (float) (hour % 12) * (cell + gap) + cell / 2;
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
        digits.setTextSize(Ui.sp(getContext(), 15.5f));
        grid(b, slots, nowRel, drawBounds, digits, chosenFrom, chosenTo);
    }

    /** Shared app/widget grid: exact halves, readable hour numbers, possible hatching and unknown cells. */
    static void grid(Brush b, String slots, float nowRel, RectF bounds, Paint text, int from, int to) {
        grid(b, slots, nowRel, bounds, text, new Range(0, 24, from, to));
    }

    /** A twelve-hour widget row still preserves the real hour numbers and half-hour states. */
    static void row(Brush b, String slots, float nowRel, RectF bounds, Paint text, int firstHour) {
        grid(b, slots, nowRel, bounds, text, new Range(firstHour, 12, -1, -1));
    }

    private static final class Range {
        final int first;
        final int count;
        final int from;
        final int to;

        Range(int first, int count, int from, int to) {
            this.first = first;
            this.count = count;
            this.from = from;
            this.to = to;
        }
    }

    /** The room between hours across a row: narrow, since hours of one state join into one run. */
    static float columnGap(float height) {
        return height * 1.6f / 55;
    }

    private static void grid(Brush b, String slots, float nowRel, RectF bounds, Paint text, Range range) {
        float size = text.getTextSize();
        int rows = range.count / 12;
        float rowGap = bounds.height() * 3 / (rows == 1 ? 24 : 55);
        float gap = Math.max(1f, rows == 1 ? bounds.height() * 1.6f / 24 : columnGap(bounds.height()));
        text.setTextAlign(Paint.Align.CENTER);
        float cellW = (bounds.width() - gap * 11) / 12;
        float cellH = (bounds.height() - rowGap * (rows - 1)) / rows;
        float widest = 0;
        for (String hour : HOURS) widest = Math.max(widest, text.measureText(hour));
        text.setTextSize(Math.min(size, Math.min((cellW - gap) / widest * size, cellH * (rows == 1 ? .74f : .63f))));
        float radius = Math.min(cellW, cellH) * .22f;
        Geometry g = new Geometry(bounds, range.first, cellW, cellH, gap, rowGap);
        List<int[]> runs = Logic.gridRuns(slots, nowRel, range.first, range.count);
        for (int[] run : runs) drawRun(b, slots, nowRel, range, g, run, radius);
        for (int[] run : runs) seams(b, g, run);
        for (int hour = range.first; hour < range.first + range.count; hour++) {
            g.cell(hour, b.r);
            drawHour(b, slots, nowRel, text, range, hour, radius);
        }
        // the chosen run: a frame in the gaps around its hours, one per row; at the edge it stays inside
        for (int[] frame : Logic.barFrames(range.from, range.to)) {
            int row = (frame[0] - range.first) / 12;
            float top = bounds.top + (float) row * (cellH + rowGap);
            float half = rowGap / 2;
            RectF box = b.r;
            box.set(Math.max(bounds.left + half / 2, g.x(frame[0]) - half),
                    Math.max(bounds.top + half / 2, top - half),
                    Math.min(bounds.right - half / 2, g.x(frame[1]) + cellW + half),
                    Math.min(bounds.bottom - half / 2, top + cellH + half));
            b.p.setStyle(Paint.Style.STROKE); b.p.setStrokeWidth(rowGap); b.p.setColor(b.pal.text);
            b.cv.drawRoundRect(box, radius + half, radius + half, b.p);
            b.p.setStyle(Paint.Style.FILL);
        }
    }

    /** Where the hours of a grid stand: columns of twelve, rows under each other. */
    private static final class Geometry {
        final RectF bounds;
        final int first;
        final float cellW;
        final float cellH;
        final float gap;
        final float rowGap;

        Geometry(RectF bounds, int first, float cellW, float cellH, float gap, float rowGap) {
            this.bounds = bounds; this.first = first; this.cellW = cellW; this.cellH = cellH; this.gap = gap; this.rowGap = rowGap;
        }

        float x(int hour) {
            int column = (hour - first) % 12;
            return bounds.left + (float) column * (cellW + gap);
        }

        float top(int hour) {
            int row = (hour - first) / 12;
            return bounds.top + (float) row * (cellH + rowGap);
        }

        void cell(int hour, RectF out) { out.set(x(hour), top(hour), x(hour) + cellW, top(hour) + cellH); }
    }

    /** One run as one shape: round where it meets the gap of an hour, square where a half hour changes. */
    private static void drawRun(Brush b, String slots, float nowRel, Range range, Geometry g, int[] run, float radius) {
        int firstHour = run[0] / 2;
        int lastHour = (run[1] - 1) / 2;
        float left = g.x(firstHour) + (run[0] % 2 == 1 ? g.cellW / 2 : 0);
        float right = g.x(lastHour) + (run[1] % 2 == 1 ? g.cellW / 2 : g.cellW);
        float top = g.top(firstHour);
        RectF box = b.r2;
        box.set(left, top, right, top + g.cellH);
        float l = run[0] % 2 == 0 ? radius : 0;
        float r = run[1] % 2 == 0 ? radius : 0;
        float[] corners = {l, l, r, r, r, r, l, l};
        b.path.reset();
        b.path.addRoundRect(box, corners, android.graphics.Path.Direction.CW);
        b.cv.save();
        b.cv.clipPath(b.path);
        char state = at(slots, run[0]);
        b.p.setStyle(Paint.Style.FILL);
        b.p.setColor(Ui.blend(b.pal.card, b.pal.slot(state)));
        b.cv.drawRect(box, b.p);
        if (state == '3') stripes(b, box);
        if (nowRel >= (firstHour + 1) * 2) {  // a run is past or not as a whole
            b.p.setColor(Ui.fade(0xFF172428, PAST_VEIL));
            b.cv.drawRect(box, b.p);
        }
        if (range.from >= 0) aside(b, range, g, run, box);
        b.cv.restore();
    }

    /** The halves of a run outside the chosen one fade, so its exact start and end read at once. */
    private static void aside(Brush b, Range range, Geometry g, int[] run, RectF box) {
        b.p.setColor(Ui.fade(b.pal.card, ASIDE_VEIL));
        for (int slot = run[0]; slot < run[1]; slot++) {
            if (slot >= range.from && slot < range.to) continue;
            boolean second = slot % 2 == 1;
            float x = g.x(slot / 2) + (second ? g.cellW / 2 : 0);
            float x1 = x + g.cellW / 2 + (second && slot + 1 < run[1] ? g.gap : 0);  // and the gap a joined run spans
            b.cv.drawRect(x, box.top, x1, box.bottom, b.p);
        }
    }

    /** Inside a run the hours stay countable: a hairline where one ends, a notch at each half hour. */
    private static void seams(Brush b, Geometry g, int[] run) {
        float line = Math.max(1f, g.gap * .6f);
        int seam = Ui.fade(b.pal.card, .8f);
        int tick = Ui.fade(b.pal.card, .45f);
        b.p.setStyle(Paint.Style.FILL);
        for (int slot = run[0] + 1; slot < run[1]; slot++) {
            int hour = slot / 2;
            float top = g.top(hour);
            if (slot % 2 == 0) {
                float x = g.x(hour) - g.gap / 2;
                b.p.setColor(seam);
                b.cv.drawRect(x - line / 2, top + g.cellH * .16f, x + line / 2, top + g.cellH * .84f, b.p);
            } else {
                float x = g.x(hour) + g.cellW / 2;
                float len = g.cellH * .13f;
                b.p.setColor(tick);
                b.cv.drawRect(x - line / 3, top, x + line / 3, top + len, b.p);
                b.cv.drawRect(x - line / 3, top + g.cellH - len, x + line / 3, top + g.cellH, b.p);
            }
        }
    }

    private static void drawHour(Brush b, String slots, float nowRel, Paint text, Range range, int hour, float radius) {
        RectF cell = b.r;
        boolean past = nowRel >= (hour + 1) * 2;
        boolean chosen = range.from >= 0;
        int ink = at(slots, hour * 2) == '2' && at(slots, hour * 2 + 1) == '2' ? b.pal.muted : 0xFFFFFFFF;
        ink = past ? 0xFFB9C0BA : ink;
        boolean inRun = chosen && hour * 2 < range.to && hour * 2 + 2 > range.from;
        text.setColor(chosen && !inRun ? Ui.blend(ink, Ui.fade(b.pal.card, ASIDE_VEIL)) : ink);
        float baseline = cell.centerY() - (text.ascent() + text.descent()) / 2;
        b.cv.drawText(HOURS[hour], cell.centerX(), baseline, text);
        if (nowRel >= hour * 2 && nowRel < hour * 2 + 2) {
            b.p.setStyle(Paint.Style.STROKE); b.p.setStrokeWidth(Math.max(1, text.getTextSize() * .17f));
            b.p.setColor(Ui.Pal.MARKER);
            cell.inset(b.p.getStrokeWidth() / 2, b.p.getStrokeWidth() / 2);
            b.cv.drawRoundRect(cell, radius, radius, b.p);
            b.p.setStyle(Paint.Style.FILL);
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
