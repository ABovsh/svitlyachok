package ua.abovsh.svitlo;

import android.app.Dialog;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.ScrollView;

/** Common layout for scrollable panels at the bottom of the screen. */
final class BottomSheet {
    private BottomSheet() {}

    static void grip(Base a, LinearLayout col) {
        View grip = new View(a);
        grip.setBackground(Ui.round(a.pal.line, Ui.px(a, 4)));
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(Ui.dp(a, 40), Ui.dp(a, 4));
        gl.gravity = Gravity.CENTER_HORIZONTAL;
        gl.bottomMargin = Ui.dp(a, 12);
        col.addView(grip, gl);
    }

    static Dialog show(Base a, LinearLayout col, Runnable closed) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView sv = new ScrollView(a);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        int side = Ui.dp(a, 16);
        col.setPadding(side, Ui.dp(a, 10), side, Ui.dp(a, 18));
        sv.addView(col);
        if (Build.VERSION.SDK_INT >= 30) {  // above the navigation bar, not under it
            sv.setOnApplyWindowInsetsListener((v, in) -> {
                v.setPadding(0, 0, 0, in.getInsets(WindowInsets.Type.navigationBars()).bottom);
                return in;
            });
        }
        d.setContentView(sv);
        Window w = d.getWindow();
        if (w != null) {
            float r = Ui.px(a, 28);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(a.pal.bg);
            bg.setCornerRadii(new float[] {r, r, r, r, 0, 0, 0, 0});
            w.setBackgroundDrawable(bg);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
        }
        d.setOnDismissListener(x -> closed.run());
        d.show();
        return d;
    }
}
