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

/**
 * One bar per day, the whole bar 24 hours: green with light, amber a possible outage, red
 * without light, from the bottom up; a week also writes the hours of light into each bar. A
 * day with no schedule seen is a faint outline.
 */
final class StatsChart extends View {
    private static final String[] WEEKDAYS = {"пн", "вт", "ср", "чт", "пт", "сб", "нд"};
    private final Ui.Pal pal;
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint label = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint value = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF bar = new RectF();
    private final Path clip = new Path();
    private final int chartH;
    private final int labelH;
    private List<Logic.DayStat> days = new ArrayList<>();

    StatsChart(Context c, Ui.Pal pal) {
        super(c);
        this.pal = pal;
        chartH = Ui.dp(c, 150);
        labelH = Ui.dp(c, 22);
        label.setTypeface(Ui.bold(c));
        label.setTextSize(Ui.px(c, 11));
        label.setTextAlign(Paint.Align.CENTER);
        value.setTypeface(Ui.heavy(c));
        value.setTextSize(Ui.px(c, 13));
        value.setTextAlign(Paint.Align.CENTER);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);  // the average line says it
    }

    void set(List<Logic.DayStat> days) {
        this.days = days;
        invalidate();
    }

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(MeasureSpec.getSize(w), chartH + labelH);
    }

    @Override
    protected void onDraw(Canvas cv) {
        int n = days.size();
        if (n == 0) return;
        float w = (float) getWidth();
        float slot = w / n;
        float gap = Math.min(slot * 0.3f, Ui.px(getContext(), 8));
        float r = Math.min((slot - gap) / 2f, Ui.px(getContext(), 6));
        // 12 hours: a faint line across
        p.setColor(pal.line);
        cv.drawRect(0f, chartH / 2f - 1f, w, chartH / 2f + 1f, p);
        for (int i = 0; i < n; i++) {
            Logic.DayStat d = days.get(i);
            float x0 = i * slot + gap / 2f;
            float x1 = (i + 1) * slot - gap / 2f;
            bar.set(x0, 0f, x1, (float) chartH);
            if (!d.known) {
                p.setStyle(Paint.Style.STROKE);
                p.setStrokeWidth(Ui.px(getContext(), 1));
                p.setColor(pal.line);
                cv.drawRoundRect(bar, r, r, p);
                p.setStyle(Paint.Style.FILL);
            } else {
                stack(cv, d, r);
                if (n <= 7) hours(cv, d, x0 + (x1 - x0) / 2f);
            }
            drawLabel(cv, d, i, n, x0 + (x1 - x0) / 2f);
        }
    }

    /** Light from the bottom, then a possible outage, then none: the share of 48 half hours. */
    private void stack(Canvas cv, Logic.DayStat d, float r) {
        float all = (float) (d.on + d.maybe + d.off);
        if (all <= 0) return;
        cv.save();
        bar.top = 0f;
        clip.reset();
        clip.addRoundRect(bar, r, r, Path.Direction.CW);
        cv.clipPath(clip);
        float h = bar.height();
        float top = bar.bottom - h * d.on / all;
        p.setColor(pal.on);
        cv.drawRect(bar.left, top, bar.right, bar.bottom, p);
        float maybeTop = top - h * d.maybe / all;
        p.setColor(pal.maybe);
        cv.drawRect(bar.left, maybeTop, bar.right, top, p);
        p.setColor(Ui.fade(pal.off, 0.85f));
        cv.drawRect(bar.left, bar.top, bar.right, maybeTop, p);
        cv.restore();
    }

    /** A week is wide enough to write the hours of light into each bar. */
    private void hours(Canvas cv, Logic.DayStat d, float cx) {
        String h = String.format(Locale.ROOT, d.on % 2 == 0 ? "%d" : "%d½", d.on / 2);
        value.setColor(pal.ink);
        cv.drawText(h, cx, chartH - Ui.px(getContext(), 8), value);
    }

    private void drawLabel(Canvas cv, Logic.DayStat d, int i, int n, float cx) {
        boolean today = i == n - 1;
        String text;
        if (n <= 7) text = today ? "сьог." : WEEKDAYS[d.date.getDayOfWeek().getValue() - 1];
        else if (today || d.date.getDayOfMonth() % 5 == 0 && i < n - 3) {  // no label crowding today's
            text = String.format(Locale.ROOT, "%d", d.date.getDayOfMonth());
        } else {
            return;
        }
        label.setColor(today ? pal.text : pal.muted);
        cv.drawText(text, cx, chartH + labelH * 0.8f, label);
    }
}
