package ua.abovsh.svitlo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** One bar per day. Both hour counts adapt to width; all month bars remain visible. */
final class StatsChart extends View {
    private static final String[] WEEKDAYS = {"пн", "вт", "ср", "чт", "пт", "сб", "нд"};
    private final Ui.Pal pal;
    private final Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint value = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bar = new RectF();
    private final Path clip = new Path();
    private final Brush brush;
    private final float chartH;
    private final float top;
    private final float bottom;
    private List<Logic.DayStat> days = new ArrayList<>();
    private List<Logic.StatLabel> labels = new ArrayList<>();

    StatsChart(Context c, Ui.Pal pal) {
        super(c);
        this.pal = pal;
        brush = new Brush(pal, pal.card);
        chartH = Ui.px(c, 150);
        value.setTypeface(Ui.heavy(c));
        value.setTextSize(Ui.sp(c, 14));
        value.setTextAlign(Paint.Align.CENTER);
        label.setTypeface(Ui.bold(c));
        label.setTextSize(Ui.sp(c, 12));
        label.setTextAlign(Paint.Align.CENTER);
        top = value.getFontSpacing() + Ui.px(c, 6);
        bottom = value.getFontSpacing() + label.getFontSpacing() + Ui.px(c, 14);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    void set(List<Logic.DayStat> data) {
        days = new ArrayList<>(data);
        setContentDescription(Logic.describeStats(days));
        chooseLabels(getWidth());
        invalidate();
    }

    private void chooseLabels(int width) {
        float w = Math.max(value.measureText("24"), value.measureText("<1"));
        if (days.size() <= 7) w = Math.max(w, value.measureText("23,5"));
        w = Math.max(w, label.measureText("сьог."));
        w = Math.max(w, label.measureText("31.10"));
        labels = Logic.statLabels(days, width, w, Ui.px(getContext(), 6));
    }

    @Override
    protected void onSizeChanged(int w, int h, int oldw, int oldh) {
        super.onSizeChanged(w, h, oldw, oldh);
        chooseLabels(w);
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), Math.round(top + chartH + bottom));
    }

    @Override
    protected void onDraw(Canvas cv) {
        if (days.isEmpty()) return;
        float slot = (float) getWidth() / days.size();
        float gap = Math.min(slot * .3f, Ui.px(getContext(), 8));
        float radius = Math.min((slot - gap) / 2, Ui.px(getContext(), 6));
        paint.setColor(pal.line);
        cv.drawRect(0, top + chartH / 2, getWidth(), top + chartH / 2 + 1, paint);
        for (int i = 0; i < days.size(); i++) {
            bar.set(i * slot + gap / 2, top, (i + 1) * slot - gap / 2, top + chartH);
            drawDay(cv, days.get(i), radius);
        }
        for (Logic.StatLabel l : labels) drawNumbers(cv, l, slot, gap);
    }

    private void drawDay(Canvas cv, Logic.DayStat d, float radius) {
        if (!Logic.statKnown(d)) {
            paint.setColor(pal.line);
            paint.setStyle(Paint.Style.STROKE);
            paint.setStrokeWidth(Ui.px(getContext(), 1));
            cv.drawRoundRect(bar, radius, radius, paint);
            paint.setStyle(Paint.Style.FILL);
            return;
        }
        clip.reset(); clip.addRoundRect(bar, radius, radius, Path.Direction.CW);
        cv.save(); cv.clipPath(clip);
        paint.setColor(pal.unknown); cv.drawRect(bar, paint);
        paint.setColor(pal.on); cv.drawRect(bar.left, bar.bottom - chartH * d.on / 48f, bar.right, bar.bottom, paint);
        paint.setColor(pal.off); cv.drawRect(bar.left, top, bar.right, top + chartH * d.off / 48f, paint);
        if (d.maybe > 0) {
            brush.r2.set(bar.left, top + chartH * d.off / 48f, bar.right, top + chartH * (d.off + d.maybe) / 48f);
            paint.setColor(pal.soft(Logic.MAYBE)); cv.drawRect(brush.r2, paint);
            BarView.stripes(brush.on(cv), brush.r2);
        }
        cv.restore();
    }

    private void drawNumbers(Canvas cv, Logic.StatLabel l, float slot, float gap) {
        Logic.DayStat d = days.get(l.index);
        boolean month = days.size() > 7;
        float width = slot - gap;
        String on = Logic.statNumber(d, Logic.ON, month);
        String off = Logic.statNumber(d, Logic.OFF, month);
        float realX = (l.index + .5f) * slot;
        boolean outside = month || value.measureText(on) + Ui.px(getContext(), 4) > width
                || value.measureText(off) + Ui.px(getContext(), 4) > width;
        boolean offInside = !outside && chartH * d.off / 48f >= value.getFontSpacing() + Ui.px(getContext(), 6);
        boolean onInside = !outside && chartH * d.on / 48f >= value.getFontSpacing() + Ui.px(getContext(), 6);
        value.setColor(offInside ? pal.ink : pal.offText);
        float offY = offInside ? top - value.ascent() + Ui.px(getContext(), 5) : top - Ui.px(getContext(), 5);
        cv.drawText(off, l.x, offY, value);
        if (!offInside) connector(cv, realX, l.x, top, -1, pal.offText);
        value.setColor(onInside ? pal.ink : pal.onText);
        float onY = onInside ? top + chartH - Ui.px(getContext(), 8) : top + chartH - value.ascent() + Ui.px(getContext(), 4);
        cv.drawText(on, l.x, onY, value);
        if (!onInside) connector(cv, realX, l.x, top + chartH, 1, pal.onText);
        label.setColor(l.index == days.size() - 1 ? pal.text : pal.muted);
        String date = month ? String.valueOf(d.date.getDayOfMonth()) : WEEKDAYS[d.date.getDayOfWeek().getValue() - 1];
        if (l.index == days.size() - 1) date = "сьог.";
        float y = onInside ? top + chartH - label.ascent() + Ui.px(getContext(), 5) : onY + label.getFontSpacing() + Ui.px(getContext(), 3);
        cv.drawText(date, l.x, y, label);
        if (!month && onInside) cv.drawText(String.format(Locale.ROOT, "%d.%02d", d.date.getDayOfMonth(), d.date.getMonthValue()),
                l.x, y + label.getFontSpacing(), label);
    }

    private void connector(Canvas cv, float real, float text, float y, int direction, int colour) {
        paint.setColor(colour);
        paint.setStrokeWidth(Ui.px(getContext(), 1));
        cv.drawLine(real, y + direction, text, y + direction * Ui.px(getContext(), 4), paint);
    }
}
