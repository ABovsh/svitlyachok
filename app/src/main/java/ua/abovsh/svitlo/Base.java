package ua.abovsh.svitlo;

import android.app.Activity;
import android.graphics.PorterDuff;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.view.WindowInsetsController;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/** A scrolling page in the chosen palette, edge to edge, redrawn every 30 s while visible. */
abstract class Base extends Activity {
    final Handler handler = new Handler(Looper.getMainLooper());
    Ui.Pal pal;
    ScrollView scroll;
    LinearLayout page;
    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            render();
            handler.postDelayed(this, 30_000);
        }
    };

    abstract void render();

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        pal = Ui.pal(this);
        getWindow().setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(pal.bg));
        FrameLayout root = new FrameLayout(this);
        root.setBackgroundColor(pal.bg);
        scroll = new ScrollView(this);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setVerticalScrollBarEnabled(false);
        page = Ui.column(this);
        int side = Ui.dp(this, 16);
        page.setPadding(side, 0, side, Ui.dp(this, 28));
        scroll.addView(page);
        root.addView(scroll);
        if (Build.VERSION.SDK_INT >= 35) {  // edge-to-edge is forced from Android 15
            root.setOnApplyWindowInsetsListener((v, in) -> {
                android.graphics.Insets b = in.getInsets(WindowInsets.Type.systemBars());
                scroll.setPadding(b.left, b.top, b.right, b.bottom);
                return in;
            });
        } else {
            getWindow().setStatusBarColor(pal.bg);
            getWindow().setNavigationBarColor(pal.bg);
        }
        setContentView(root);
        systemBars();
    }

    @Override
    public void onWindowFocusChanged(boolean focus) {
        super.onWindowFocusChanged(focus);
        if (focus) systemBars();  // the theme's own bar colours may be applied after onCreate
    }

    /** Dark icons in the status bar on the light palette, light icons on the dark one. */
    @SuppressWarnings("deprecation")
    private void systemBars() {
        if (Build.VERSION.SDK_INT >= 30) {
            int light = WindowInsetsController.APPEARANCE_LIGHT_STATUS_BARS
                    | WindowInsetsController.APPEARANCE_LIGHT_NAVIGATION_BARS;
            WindowInsetsController c = getWindow().getInsetsController();
            if (c != null) c.setSystemBarsAppearance(pal.dark ? 0 : light, light);
        } else if (!pal.dark) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (Ui.pal(this).dark != pal.dark) {  // the theme changed while away
            recreate();
            return;
        }
        handler.post(tick);
    }

    @Override
    protected void onPause() {
        handler.removeCallbacks(tick);
        super.onPause();
    }

    /** A round 48 dp button with a tinted icon. */
    ImageView iconButton(int icon, String desc, View.OnClickListener l) {
        ImageView b = new ImageView(this);
        b.setImageResource(icon);
        b.setColorFilter(pal.text, PorterDuff.Mode.SRC_IN);
        b.setScaleType(ImageView.ScaleType.CENTER);
        b.setBackground(Ui.round(pal.raised, Ui.dp(this, 100)));
        b.setContentDescription(desc);
        b.setOnClickListener(l);
        b.setLayoutParams(new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48)));
        return b;
    }

    /** Back button + title for the inner pages. */
    LinearLayout topBar(String title) {
        LinearLayout r = Ui.row(this);
        r.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 6));
        r.addView(iconButton(R.drawable.ic_back, "Назад", v -> finish()));
        TextView t = Ui.text(this, 21, Ui.display(this), pal.text);
        t.setText(title);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(Ui.dp(this, 14), 0, 0, 0);
        r.addView(t, weight());
        return r;
    }

    /** A small caps heading over a card. */
    TextView heading(String s) {
        TextView t = Ui.text(this, 12, Ui.heavy(this), pal.muted);
        t.setText(s.toUpperCase(Ui.UK));
        t.setLetterSpacing(0.12f);
        t.setPadding(Ui.dp(this, 6), Ui.dp(this, 16), 0, Ui.dp(this, 8));
        return t;
    }

    LinearLayout box() {
        LinearLayout l = Ui.column(this);
        l.setPadding(Ui.dp(this, 16), Ui.dp(this, 4), Ui.dp(this, 16), Ui.dp(this, 4));
        l.setBackground(Ui.card(this, pal, 0));
        return l;
    }

    View divider() {
        View v = new View(this);
        v.setBackgroundColor(pal.line);
        v.setLayoutParams(new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, Ui.dp(this, 1)));
        return v;
    }

    /** The big amber button. */
    TextView primary(String s, View.OnClickListener l) {
        TextView b = Ui.text(this, 16, Ui.heavy(this), pal.accentInk);
        b.setText(s);
        b.setGravity(Gravity.CENTER);
        b.setMinHeight(Ui.dp(this, 54));
        b.setBackground(Ui.round(pal.accent, Ui.dp(this, 100)));
        b.setOnClickListener(l);
        return b;
    }

    static LinearLayout.LayoutParams wrap() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    static LinearLayout.LayoutParams fill() {
        return new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    static LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f);
    }

    LinearLayout.LayoutParams gap(int bottomDp) {
        LinearLayout.LayoutParams lp = fill();
        lp.bottomMargin = Ui.dp(this, bottomDp);
        return lp;
    }

    /** "Київська область · черга 1.1" */
    static String where(Logic.Place p) {
        Logic.Region r = Logic.region(p.region);
        return (r == null ? "" : r.name + " · ") + "черга " + Logic.queue(p.group);
    }
}
