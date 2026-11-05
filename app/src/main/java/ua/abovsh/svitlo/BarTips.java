package ua.abovsh.svitlo;

import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.TextView;

/**
 * The hint of a run of the bar. It lies over the row of the day's title, so nothing on the screen
 * moves. The same run touched again takes it away, as do a touch beside it and a few seconds.
 */
final class BarTips {
    private static final long SHOWN_MS = 4000;
    private final Base page;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable hideLater = this::hide;
    private FrameLayout host;
    private BarView bar;
    private TextView words;
    private View arrow;
    private int from = -1;

    BarTips(Base page) {
        this.page = page;
    }

    /** A touch of a run: shown, moved to it, or taken away when it was this one. */
    void touched(FrameLayout section, BarView touched, String day) {
        int[] run = Logic.barRun(day, touched.touchedSlot());
        boolean same = touched == bar && run[0] == from;
        hide();
        if (same) return;
        host = section;
        bar = touched;
        from = run[0];
        bar.choose(run[0], run[1]);
        show(Logic.barTip(day, run[0], run[1]), (run[0] + run[1]) / 2f);
        handler.postDelayed(hideLater, SHOWN_MS);
    }

    void hide() {
        handler.removeCallbacks(hideLater);
        if (host != null) {
            host.removeView(words);
            host.removeView(arrow);
        }
        if (bar != null) bar.choose(-1, -1);
        host = null;
        bar = null;
        words = null;
        arrow = null;
        from = -1;
    }

    private void show(String text, float slot) {
        Ui.Pal pal = page.pal;
        int width = bar.getWidth();
        int margin = Ui.dp(page, 4);
        words = Ui.text(page, 13, Ui.heavy(page), pal.card);
        words.setText(text);
        words.setGravity(Gravity.CENTER);
        words.setPadding(Ui.dp(page, 10), Ui.dp(page, 4), Ui.dp(page, 10), Ui.dp(page, 4));
        words.setBackground(Ui.round(pal.text, Ui.px(page, 9)));
        words.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        words.setOnClickListener(v -> hide());
        words.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.AT_MOST), View.MeasureSpec.UNSPECIFIED);
        int top = ((View) bar.getParent()).getTop() + bar.getTop();  // of the bar itself
        float centre = bar.slotX(slot);
        FrameLayout.LayoutParams at = new FrameLayout.LayoutParams(words.getMeasuredWidth(), words.getMeasuredHeight());
        at.gravity = Gravity.TOP | Gravity.START;
        at.leftMargin = Math.max(0, Math.min(width - words.getMeasuredWidth(), Math.round(centre - words.getMeasuredWidth() / 2f)));
        at.topMargin = Math.max(0, top - words.getMeasuredHeight() - margin);
        int side = Ui.dp(page, 9);
        arrow = new View(page);
        arrow.setBackgroundColor(pal.text);
        arrow.setRotation(45);
        arrow.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        FrameLayout.LayoutParams point = new FrameLayout.LayoutParams(side, side);
        point.gravity = Gravity.TOP | Gravity.START;
        point.leftMargin = Math.round(centre - side / 2f);
        point.topMargin = at.topMargin + words.getMeasuredHeight() - side / 2 - 1;
        host.addView(arrow, point);
        host.addView(words, at);
    }
}
