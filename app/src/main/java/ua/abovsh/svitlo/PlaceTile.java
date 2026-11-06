package ua.abovsh.svitlo;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/** A compact place has its own width budget; the state card cannot change its grid. */
final class PlaceTile extends ViewGroup {
    private final FitText status;
    private int wordsLeft;
    private int wordsRoom;
    private int headerBaseline;
    private final BulbView bulb;
    private final FitText name;
    private final TextView queue;
    private final TextView percent;
    private final boolean namedQueue;
    private final android.graphics.Paint labelMeasure = new android.graphics.Paint();

    PlaceTile(Context c, Ui.Pal pal, Logic.Place place, Logic.Now now, boolean chosen,
            boolean soon, String share) {
        super(c);
        GradientDrawable bg = Ui.round(pal.soft(now.state), Ui.px(c, 15));
        bg.setStroke(Ui.dp(c, chosen ? 2 : 1), chosen ? pal.accentText : pal.line);
        setBackground(bg);
        // The selection stroke must not alter the content width.
        setPadding(0, 0, 0, 0);
        bulb = new BulbView(c, pal); bulb.still(); bulb.set(now.state); addView(bulb);
        name = new FitText(c, 14, 14, 2, Ui.heavy(c), pal.text).compact(1);
        name.setText(place.name); addView(name);
        namedQueue = chosen && !Logic.autoNamed(place);
        queue = Ui.text(c, 12.5f, Ui.heavy(c), pal.accentText);
        queue.setText(Logic.queue(place.group));
        queue.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.compactSp(c, 12.5f, 1));
        queue.setPadding(Ui.dp(c, 4), 0, Ui.dp(c, 4), 0);
        queue.setBackground(Ui.round(Ui.blend(pal.card, Ui.fade(pal.accent, .18f)), Ui.px(c, 6)));
        addView(queue);
        percent = Ui.text(c, 11, Ui.heavy(c), pal.muted);
        percent.setText(share); percent.setSingleLine(true);
        percent.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.compactSp(c, 11, 1));
        addView(percent);
        int ink = pal.status(now.state);
        if (soon) ink = now.next == Logic.MAYBE ? pal.accentInk : pal.ink;
        status = new FitText(c, 14, 12.5f, now.state == Logic.MAYBE ? 2 : 1, Ui.bold(c), ink).compact(1.1f);
        status.formats(Logic.tileFormats(now).toArray(new CharSequence[0]));
        status.setPadding(Ui.dp(c, 4), Ui.dp(c, 1), Ui.dp(c, 4), Ui.dp(c, 1));
        if (soon) {
            status.setBackground(Ui.round(pal.fill(now.next), Ui.px(c, 7)));
        }
        addView(status);
        android.graphics.Paint essential = new android.graphics.Paint(status.getPaint());
        essential.setTextSize(Ui.compactSp(c, 14, 1.1f));
        setMinimumWidth(Math.max(Ui.dp(c, 128), Math.round(essential.measureText("до 24:00")) + Ui.dp(c, 40)));
        String location = Logic.autoNamed(place) ? Base.where(place) : place.name + ", " + Base.where(place);
        String dayInfo = share.equals("—") ? "частка часу зі світлом поки невідома" : "очікується світла сьогодні " + share;
        setContentDescription(location + ": за графіком " + now.title
                + (now.big.isEmpty() ? "" : ", " + now.big) + ", " + dayInfo
                + (chosen ? ", вибрано" : ", показати графік"));
        for (int i = 0; i < getChildCount(); i++) getChildAt(i).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        setFocusable(true);
    }

    private void measureText(TextView text, int room) {
        text.measure(MeasureSpec.makeMeasureSpec(Math.max(1, room), MeasureSpec.EXACTLY), MeasureSpec.UNSPECIFIED);
    }

    @Override protected void onMeasure(int width, int height) {
        Context c = getContext();
        int px = MeasureSpec.getSize(width);
        int icon = Ui.dp(c, px < Ui.px(c, 160) ? 24 : 30);
        bulb.measure(MeasureSpec.makeMeasureSpec(icon, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(icon, MeasureSpec.EXACTLY));
        wordsLeft = Ui.dp(c, 10) + icon;
        wordsRoom = Math.max(1, px - wordsLeft - Ui.dp(c, 4));
        percent.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        queue.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        int nameRoom = Math.max(1, wordsRoom - percent.getMeasuredWidth() - Ui.dp(c, 4));
        android.graphics.Paint label = labelMeasure; label.set(name.getPaint());
        label.setTextSize(Ui.compactSp(c, 14, 1));
        boolean showQueue = namedQueue && Math.ceil(label.measureText(name.getText().toString())) + 1
                + queue.getMeasuredWidth() + Ui.dp(c, 4) <= nameRoom;
        queue.setVisibility(showQueue ? VISIBLE : GONE);
        if (showQueue) nameRoom -= queue.getMeasuredWidth() + Ui.dp(c, 4);
        measureText(name, nameRoom); measureText(status, wordsRoom);
        headerBaseline = Math.max(name.getBaseline(), percent.getBaseline());
        int descent = Math.max(name.getMeasuredHeight() - name.getBaseline(), percent.getMeasuredHeight() - percent.getBaseline());
        if (showQueue) {
            headerBaseline = Math.max(headerBaseline, queue.getBaseline());
            descent = Math.max(descent, queue.getMeasuredHeight() - queue.getBaseline());
        }
        int wordsHeight = headerBaseline + descent + Ui.dp(c, 2) + status.getMeasuredHeight();
        int natural = Math.max(Ui.dp(c, 56), Ui.dp(c, 12) + Math.max(icon, wordsHeight));
        setMeasuredDimension(resolveSize(px, width), resolveSize(natural, height));
    }

    private void layoutHeader(TextView text, int x) {
        int top = Ui.dp(getContext(), 6) + headerBaseline - text.getBaseline();
        text.layout(x, top, x + text.getMeasuredWidth(), top + text.getMeasuredHeight());
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int x = Ui.dp(getContext(), 6);
        int y = (getHeight() - bulb.getMeasuredHeight()) / 2;
        bulb.layout(x, y, x + bulb.getMeasuredWidth(), y + bulb.getMeasuredHeight());
        layoutHeader(name, wordsLeft);
        int percentLeft = getWidth() - Ui.dp(getContext(), 4) - percent.getMeasuredWidth();
        layoutHeader(percent, percentLeft);
        if (queue.getVisibility() == VISIBLE) layoutHeader(queue, percentLeft - Ui.dp(getContext(), 4) - queue.getMeasuredWidth());
        int statusTop = getHeight() - Ui.dp(getContext(), 6) - status.getMeasuredHeight();
        status.layout(wordsLeft, statusTop, wordsLeft + wordsRoom, statusTop + status.getMeasuredHeight());
    }
}
