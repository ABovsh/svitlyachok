package ua.abovsh.svitlo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.DashPathEffect;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Positive charts: green power hours rise from zero; missing data is never a zero bar. */
final class StatsChart extends View {
    private static final float LEAST_GAP = 3;
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
    private final float axis;
    private final DashPathEffect averageDash;
    private List<Logic.DayStat> days = new ArrayList<>();
    private List<Logic.WeekStat> weeks = new ArrayList<>();
    private float average = -1;
    private float offEnd;  // where the red of the bar being labelled ends

    StatsChart(Context c, Ui.Pal pal) {
        super(c); this.pal = pal;
        brush = new Brush(pal, pal.card);
        chartH = Ui.px(c, 150);
        value.setTypeface(Ui.heavy(c)); value.setTextSize(Ui.sp(c, 13)); value.setTextAlign(Paint.Align.CENTER);
        label.setTypeface(Ui.bold(c)); label.setTextSize(Ui.sp(c, 11)); label.setTextAlign(Paint.Align.CENTER);
        top = value.getFontSpacing() + Ui.px(c, 8);
        bottom = 2 * label.getFontSpacing() + Ui.px(c, 12);
        axis = label.measureText("24") + Ui.px(c, 8);
        averageDash = new DashPathEffect(new float[] {Ui.px(c,5), Ui.px(c,4)}, 0);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_YES);
    }

    void set(List<Logic.DayStat> data) {
        days = new ArrayList<>(data); weeks.clear();
        average = Logic.statAverageMinutes(days, Logic.ON);
        setContentDescription(Logic.describeStats(days)); invalidate();
    }

    void setWeeks(List<Logic.WeekStat> data) {
        weeks = new ArrayList<>(data); days.clear();
        StringBuilder desc = new StringBuilder("Середній час зі світлом на день за графіком. ");
        List<Logic.DayStat> known = new ArrayList<>();
        for (Logic.WeekStat w : weeks) {
            known.addAll(w.days);
            desc.append(date(w.from)).append(" — ").append(date(w.to)).append(": ")
                    .append(w.onMinutes < 0 ? "немає даних" : Logic.hoursMinutes(w.onMinutes) + ", " + w.onPercent + " відсотків")
                    .append(", повних днів із графіком ").append(w.known).append(" із 7. ");
        }
        average = Logic.statAverageMinutes(known, Logic.ON);
        setContentDescription(desc); invalidate();
    }

    @Override protected void onMeasure(int w, int h) {
        // Only the minimum: the scroll view stretches a narrower chart to exactly its own width.
        float minimum = axis + (weeks.isEmpty()
                ? 7 * Math.max(label.measureText("28.10") + Ui.px(getContext(), 4), numberRoom() + Ui.px(getContext(), LEAST_GAP))
                : 4 * (value.measureText("100 %") + Ui.px(getContext(), 12)));
        int width = (int) Math.ceil(minimum);
        if (MeasureSpec.getMode(w) == MeasureSpec.EXACTLY) width = MeasureSpec.getSize(w);
        setMeasuredDimension(width, Math.round(top + chartH + bottom));
    }

    /** The bar width any half-hour number needs: the digits differ in width, 0 is the widest. */
    private float numberRoom() {
        return value.measureText("00,5") + Ui.px(getContext(), 2);
    }

    @Override protected void onDraw(Canvas cv) {
        int count = weeks.isEmpty() ? days.size() : weeks.size();
        if (count == 0) return;
        float slot = (getWidth() - axis) / count;
        float gap = weeks.isEmpty()
                ? Logic.statGap(slot, Ui.px(getContext(), 10), numberRoom(), Ui.px(getContext(), LEAST_GAP))
                : Math.min(slot * .25f, Ui.px(getContext(), 10));
        label.setTextAlign(Paint.Align.RIGHT);
        for (int hour = 0; hour <= 24; hour += 6) {
            float y = top + chartH * (24f - hour) / 24;
            paint.setColor(pal.line); paint.setStrokeWidth(1);
            cv.drawLine(axis, y, getWidth(), y, paint);
            label.setColor(pal.muted); cv.drawText(String.valueOf(hour), axis - Ui.px(getContext(), 6), y - (label.ascent()+label.descent())/2, label);
        }
        label.setTextAlign(Paint.Align.CENTER);
        for (int i = 0; i < count; i++) {
            place(i, slot, gap);
            if (weeks.isEmpty()) dayBar(cv, days.get(i)); else weekBar(cv, weeks.get(i));
        }
        if (average >= 0) {
            float y = top + chartH * (1 - average / 1440f);
            paint.setColor(pal.accentText); paint.setStrokeWidth(Ui.px(getContext(), 1.3f));
            paint.setPathEffect(averageDash);
            cv.drawLine(axis, y, getWidth(), y, paint); paint.setPathEffect(null);
        }
        // Text last: the average line passes behind the numbers, never through them.
        for (int i = 0; i < count; i++) {
            place(i, slot, gap);
            if (weeks.isEmpty()) dayText(cv, days.get(i)); else weekText(cv, weeks.get(i));
        }
    }

    private void place(int i, float slot, float gap) {
        bar.set(axis + i * slot + gap/2, top, axis + (i+1)*slot-gap/2, top+chartH);
    }

    private void missing(Canvas cv) {
        paint.setColor(pal.line); paint.setStyle(Paint.Style.STROKE); paint.setStrokeWidth(Ui.px(getContext(),1));
        cv.drawRoundRect(bar, Ui.px(getContext(),5),Ui.px(getContext(),5),paint); paint.setStyle(Paint.Style.FILL);
    }

    private void dayBar(Canvas cv, Logic.DayStat d) {
        if (!Logic.statKnown(d)) { missing(cv); return; }
        clip.reset(); clip.addRoundRect(bar, Ui.px(getContext(),5),Ui.px(getContext(),5),Path.Direction.CW);
        cv.save(); cv.clipPath(clip);
        paint.setColor(pal.unknown); cv.drawRect(bar,paint);
        paint.setColor(pal.on); cv.drawRect(bar.left,bar.bottom-chartH*d.on/48f,bar.right,bar.bottom,paint);
        paint.setColor(pal.off); cv.drawRect(bar.left,bar.top,bar.right,bar.top+chartH*d.off/48f,paint);
        if(d.maybe>0) {
            brush.r2.set(bar.left,bar.top+chartH*d.off/48f,bar.right,bar.top+chartH*(d.off+d.maybe)/48f);
            paint.setColor(pal.soft(Logic.MAYBE)); cv.drawRect(brush.r2,paint); BarView.stripes(brush.on(cv),brush.r2);
        }
        cv.restore();
    }

    private void dayText(Canvas cv, Logic.DayStat d) {
        float x = bar.centerX();
        if (Logic.statKnown(d)) {
            offEnd = bar.top + chartH * d.off / 48f;
            number(cv, Logic.statNumber(d,Logic.OFF),x,bar.top-value.ascent()+Ui.px(getContext(),3),chartH*d.off/48f,pal.offText,true);
            number(cv, Logic.statNumber(d,Logic.ON),x,bar.bottom-Ui.px(getContext(),5),chartH*d.on/48f,pal.onText,false);
        }
        label.setColor(pal.muted);
        float y=bar.bottom-label.ascent()+Ui.px(getContext(),5);
        cv.drawText(WEEKDAYS[d.date.getDayOfWeek().getValue()-1],x,y,label);
        cv.drawText(date(d.date),x,y+label.getFontSpacing(),label);
    }

    private void number(Canvas cv,String text,float x,float y,float segment,int outsideInk,boolean upper) {
        if(value.measureText(text)>bar.width()-Ui.px(getContext(),2)) return;
        boolean inside=segment>=value.getFontSpacing()+Ui.px(getContext(),5);
        int ink=inside?pal.ink:outsideInk;
        if(!inside) {
            y=upper?bar.top-Ui.px(getContext(),4):bar.bottom-segment-Ui.px(getContext(),4);
            // a short green segment under a tall red one: most of its number stands on the red
            if(!upper&&y+value.ascent()/2<offEnd) ink=pal.ink;
        }
        value.setColor(ink);
        cv.drawText(text,x,y,value);
    }

    private void weekBar(Canvas cv,Logic.WeekStat w) {
        if(w.onMinutes<0) { missing(cv); return; }
        float y=bar.bottom-chartH*w.onMinutes/1440f;
        paint.setColor(pal.on); cv.drawRoundRect(bar.left,y,bar.right,bar.bottom,Ui.px(getContext(),5),Ui.px(getContext(),5),paint);
    }

    private void weekText(Canvas cv,Logic.WeekStat w) {
        float x=bar.centerX();
        if(w.onMinutes>=0) {
            float y=bar.bottom-chartH*w.onMinutes/1440f;
            float above=y-Ui.px(getContext(),5);
            onCard(cv,w.onPercent+" %",x,above);
            String hours=String.format(Locale.ROOT,"%.1f",w.onMinutes/60f).replace('.',',');
            if(y+value.getFontSpacing()+Ui.px(getContext(),5)<bar.bottom) {
                value.setColor(pal.ink); cv.drawText(hours,x,bar.bottom-Ui.px(getContext(),6),value);
            } else {  // a very short bar: the hours go above it rather than disappear
                onCard(cv,hours,x,above-value.getFontSpacing());
            }
        }
        label.setColor(pal.muted);
        float y=bar.bottom-label.ascent()+Ui.px(getContext(),5);
        cv.drawText(date(w.from)+"–",x,y,label);
        cv.drawText(date(w.to),x,y+label.getFontSpacing(),label);
    }

    /** Text over the card, with a halo of the card's colour where the average line passes behind. */
    private void onCard(Canvas cv,String text,float x,float y) {
        value.setStyle(Paint.Style.STROKE); value.setStrokeWidth(Ui.px(getContext(),4)); value.setColor(pal.card);
        cv.drawText(text,x,y,value);
        value.setStyle(Paint.Style.FILL); value.setColor(pal.onText);
        cv.drawText(text,x,y,value);
    }

    private static String date(java.time.LocalDate d) {return d.getDayOfMonth()+"."+String.format(Locale.ROOT,"%02d",d.getMonthValue());}
}
