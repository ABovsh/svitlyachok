package ua.abovsh.svitlo;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

/** A compact place has its own width budget; the state card cannot change its grid. */
final class PlaceTile extends FrameLayout {
    private final LinearLayout body;
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
        body = Ui.row(c); body.setBaselineAligned(false);
        body.setGravity(android.view.Gravity.TOP);
        body.setMinimumHeight(Ui.dp(c, 56));
        int pad = Ui.dp(c, 6);
        body.setPadding(pad, pad, Ui.dp(c, 4), pad);
        bulb = new BulbView(c, pal); bulb.still(); bulb.set(now.state);
        LinearLayout.LayoutParams bulbSize = new LinearLayout.LayoutParams(Ui.dp(c, 30), Ui.dp(c, 30));
        bulbSize.gravity = android.view.Gravity.CENTER_VERTICAL;
        bulbSize.rightMargin = Ui.dp(c, 4); body.addView(bulb, bulbSize);
        LinearLayout words = Ui.column(c);
        LinearLayout header = Ui.row(c); header.setBaselineAligned(true);
        header.setGravity(android.view.Gravity.TOP);
        name = new FitText(c, 14, 14, 2, Ui.heavy(c), pal.text).compact(1);
        name.setText(place.name);
        header.addView(name, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        namedQueue = chosen && !Logic.autoNamed(place);
        queue = Ui.text(c, 12.5f, Ui.heavy(c), pal.accentText);
        queue.setText(Logic.queue(place.group));
        queue.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.compactSp(c, 12.5f, 1));
        queue.setPadding(Ui.dp(c, 4), 0, Ui.dp(c, 4), 0);
        queue.setBackground(Ui.round(Ui.blend(pal.card, Ui.fade(pal.accent, .18f)), Ui.px(c, 6)));
        LinearLayout.LayoutParams qp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        qp.leftMargin = Ui.dp(c, 4); header.addView(queue, qp);
        percent = Ui.text(c, 11, Ui.heavy(c), pal.muted);
        percent.setText(share); percent.setSingleLine(true);
        percent.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.compactSp(c, 11, 1));
        LinearLayout.LayoutParams pp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        pp.leftMargin = Ui.dp(c, 4); header.addView(percent, pp);
        words.addView(header, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 1));
        int ink = pal.status(now.state);
        if (soon) ink = now.next == Logic.MAYBE ? pal.accentInk : pal.ink;
        FitText status = new FitText(c, 14, 12.5f, now.state == Logic.MAYBE ? 2 : 1, Ui.bold(c), ink).compact(1.1f);
        status.formats(Logic.tileFormats(now).toArray(new CharSequence[0]));
        status.setPadding(Ui.dp(c, 4), Ui.dp(c, 1), Ui.dp(c, 4), Ui.dp(c, 1));
        if (soon) {
            status.setBackground(Ui.round(pal.fill(now.next), Ui.px(c, 7)));
        }
        LinearLayout.LayoutParams sp = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        sp.topMargin = Ui.dp(c, 2); words.addView(status, sp);
        body.addView(words, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1));
        addView(body, new FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        android.graphics.Paint essential = new android.graphics.Paint(status.getPaint());
        essential.setTextSize(Ui.compactSp(c, 14, 1.1f));
        setMinimumWidth(Math.max(Ui.dp(c, 128), Math.round(essential.measureText("до 24:00")) + Ui.dp(c, 40)));
        String location = Logic.autoNamed(place) ? Base.where(place) : place.name + ", " + Base.where(place);
        String dayInfo = share.equals("—") ? "частка часу зі світлом поки невідома" : "очікується світла сьогодні " + share;
        setContentDescription(location + ": за графіком " + now.title
                + (now.big.isEmpty() ? "" : ", " + now.big) + ", " + dayInfo
                + (chosen ? ", вибрано" : ", показати графік"));
        body.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        setFocusable(true);
    }

    @Override protected void onMeasure(int width, int height) {
        int px = MeasureSpec.getSize(width);
        int icon = Ui.dp(getContext(), px < Ui.px(getContext(), 160) ? 24 : 30);
        ViewGroup.LayoutParams bp = bulb.getLayoutParams(); bp.width = icon; bp.height = icon;
        percent.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        queue.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
        float room = (float) px - body.getPaddingLeft() - body.getPaddingRight() - icon - Ui.dp(getContext(), 8) - percent.getMeasuredWidth();
        android.graphics.Paint label = labelMeasure; label.set(name.getPaint());
        label.setTextSize(Ui.compactSp(getContext(), 14, 1));
        boolean showQueue = namedQueue && label.measureText(name.getText().toString()) + queue.getMeasuredWidth() + Ui.px(getContext(), 4) <= room;
        queue.setVisibility(showQueue ? VISIBLE : GONE);
        super.onMeasure(width, height);
    }
}
