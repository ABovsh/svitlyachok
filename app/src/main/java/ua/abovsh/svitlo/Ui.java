package ua.abovsh.svitlo;

import android.content.Context;
import android.content.res.Configuration;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.util.TypedValue;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Fonts, the two palettes and small view builders. One idea for colour: power is the
 * warm light of a firefly, an outage is the night around it. Red is never used for a
 * scheduled outage — it is not a fault.
 */
final class Ui {
    /**
     * A palette: the app, the widget and the notifications draw from it. State colours are the
     * familiar ones: green power, red outage, amber possible outage, grey no schedule. The
     * firefly gold is only the accent of buttons and switches.
     */
    static final class Pal {
        final boolean dark;
        final int bg;
        final int card;
        final int raised;
        final int line;
        final int text;
        final int muted;
        final int dim;
        final int accent;
        final int accentText;
        final int accentInk;
        final int on;
        final int onText;
        final int off;
        final int offText;
        final int maybe;
        final int maybeText;
        final int unknown;
        final int ink;
        final int glow;

        Pal(boolean dark) {
            this.dark = dark;
            if (dark) {
                bg = 0xFF0A0E17; card = 0xFF121826; raised = 0xFF1A2233; line = 0x17FFFFFF;
                text = 0xFFF3F5F9; muted = 0xFF8D96A9; dim = 0xFF5B6478;
                accent = 0xFFFFC53D; accentText = 0xFFFFC53D; accentInk = 0xFF231800;
                on = 0xFF2DB86C; onText = 0xFF4ADE8F; off = 0xFFE5484D; offText = 0xFFFF6E72;
                maybe = 0xFFF5A524; maybeText = 0xFFFFB84D; unknown = 0xFF343C4D; ink = 0xFFFFFFFF; glow = 0x55;
            } else {
                bg = 0xFFF4F2EC; card = 0xFFFFFFFF; raised = 0xFFEDEAE2; line = 0x14000000;
                text = 0xFF141821; muted = 0xFF656C7B; dim = 0xFF9AA0AC;
                accent = 0xFFF5B000; accentText = 0xFFA86F00; accentInk = 0xFF231800;
                on = 0xFF1FA463; onText = 0xFF137A43; off = 0xFFDC3E42; offText = 0xFFB4232A;
                maybe = 0xFFF0A020; maybeText = 0xFF9A5B00; unknown = 0xFFD9D5CC; ink = 0xFFFFFFFF; glow = 0x40;
            }
        }

        /** The colour of a state: lines of text and the bulb. */
        int status(int state) {
            switch (state) {
                case Logic.ON: return onText;
                case Logic.OFF: return offText;
                case Logic.MAYBE: return maybeText;
                default: return muted;
            }
        }

        /** The fill of a state: bars, glows, chips. */
        int fill(int state) {
            switch (state) {
                case Logic.ON: return on;
                case Logic.OFF: return off;
                case Logic.MAYBE: return maybe;
                default: return unknown;
            }
        }

        /** A half hour on a bar: a possible outage is a paler amber, striped on top. */
        int slot(char c) {
            int state = Logic.state(c);
            return state == Logic.MAYBE ? Ui.fade(maybe, 0.45f) : fill(state);
        }
    }

    private static final String UNBOUNDED = "Unbounded.ttf";
    private static final String MANROPE = "Manrope.ttf";
    private static Typeface display;
    private static Typeface displayMid;
    private static Typeface body;
    private static Typeface bold;
    private static Typeface heavy;

    private Ui() {}

    static Pal pal(Context c) {
        String t = Prefs.theme(c);
        boolean dark = "dark".equals(t) || !"light".equals(t)
                && (c.getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK)
                        == Configuration.UI_MODE_NIGHT_YES;
        return new Pal(dark);
    }

    private static Typeface variable(Context c, String file, int weight) {
        return new Typeface.Builder(c.getAssets(), "fonts/" + file)
                .setFontVariationSettings("'wght' " + weight).build();
    }

    static Typeface display(Context c) {
        if (display == null) display = variable(c, UNBOUNDED, 700);
        return display;
    }

    static Typeface displayMid(Context c) {
        if (displayMid == null) displayMid = variable(c, UNBOUNDED, 500);
        return displayMid;
    }

    static Typeface body(Context c) {
        if (body == null) body = variable(c, MANROPE, 600);
        return body;
    }

    static Typeface bold(Context c) {
        if (bold == null) bold = variable(c, MANROPE, 700);
        return bold;
    }

    static Typeface heavy(Context c) {
        if (heavy == null) heavy = variable(c, MANROPE, 800);
        return heavy;
    }

    /** dp as exact pixels, for drawing. */
    static float px(Context c, float v) {
        return TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v, c.getResources().getDisplayMetrics());
    }

    /** dp as whole pixels, for layout. */
    static int dp(Context c, float v) {
        return Math.round(TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v,
                c.getResources().getDisplayMetrics()));
    }

    static TextView text(Context c, float sp, Typeface tf, int colour) {
        TextView t = new TextView(c);
        t.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        t.setTypeface(tf);
        t.setTextColor(colour);
        t.setIncludeFontPadding(false);
        return t;
    }

    static GradientDrawable round(int colour, float radiusPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(colour);
        d.setCornerRadius(radiusPx);
        return d;
    }

    static GradientDrawable outline(int stroke, float radiusPx, int widthPx) {
        GradientDrawable d = new GradientDrawable();
        d.setColor(0);
        d.setCornerRadius(radiusPx);
        d.setStroke(widthPx, stroke);
        return d;
    }

    /** A card; with a tint, a soft glow of that colour from the top. */
    static Drawable card(Context c, Pal p, int tint) {
        float r = dp(c, 28);
        GradientDrawable base = round(p.card, r);
        if (!p.dark) base.setStroke(dp(c, 1), p.line);
        if (tint == 0) return base;
        GradientDrawable glow = new GradientDrawable();
        glow.setGradientType(GradientDrawable.RADIAL_GRADIENT);
        glow.setGradientCenter(0.5f, 0f);
        glow.setGradientRadius(dp(c, 340));
        glow.setColors(new int[] {(tint & 0x00FFFFFF) | (p.glow << 24), 0x00000000});
        glow.setCornerRadius(r);
        return new LayerDrawable(new Drawable[] {base, glow});
    }

    static LinearLayout row(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.HORIZONTAL);
        l.setGravity(Gravity.CENTER_VERTICAL);
        return l;
    }

    static LinearLayout column(Context c) {
        LinearLayout l = new LinearLayout(c);
        l.setOrientation(LinearLayout.VERTICAL);
        return l;
    }

    /** A rounded label: filled for the chosen option, quiet otherwise. */
    static TextView pill(Context c, Pal p, String s, boolean chosen, float sp) {
        TextView t = text(c, sp, chosen ? heavy(c) : bold(c), chosen ? p.accentInk : p.text);
        t.setText(s);
        t.setGravity(Gravity.CENTER);
        t.setMaxLines(1);
        t.setPadding(dp(c, 16), dp(c, 10), dp(c, 16), dp(c, 10));
        t.setBackground(round(chosen ? p.accent : p.raised, dp(c, 100)));
        return t;
    }

    static int fade(int colour, float alpha) {
        int a = Math.round(((colour >>> 24) & 0xFF) * alpha);
        return (colour & 0x00FFFFFF) | (a << 24);
    }

    /** `top` (with alpha) laid over an opaque `under`. */
    static int blend(int under, int top) {
        float a = ((top >>> 24) & 0xFF) / 255f;
        int r = Math.round(((top >> 16) & 0xFF) * a + ((under >> 16) & 0xFF) * (1 - a));
        int g = Math.round(((top >> 8) & 0xFF) * a + ((under >> 8) & 0xFF) * (1 - a));
        int b = Math.round((top & 0xFF) * a + (under & 0xFF) * (1 - a));
        return 0xFF000000 | (r << 16) | (g << 8) | b;
    }

    /** A small sample of a bar colour for the legend, striped for a possible outage. */
    static android.view.View swatch(Context c, Pal p, char slot) {
        android.view.View v = new android.view.View(c);
        v.setBackground(new Drawable() {
            private final Brush b = new Brush(p, p.card);
            private final android.graphics.RectF box = new android.graphics.RectF();

            @Override
            public void draw(android.graphics.Canvas cv) {
                box.set(getBounds());
                BarView.runs(b.on(cv), String.valueOf(slot), 0, 1, 0, box, 0);
            }

            @Override
            public void setAlpha(int a) {
                // a legend sample is always drawn opaque
            }

            @Override
            public void setColorFilter(android.graphics.ColorFilter f) {
                // the sample shows the real bar colours, never tinted
            }

            @Override
            public int getOpacity() {
                return android.graphics.PixelFormat.TRANSLUCENT;
            }
        });
        v.setImportantForAccessibility(android.view.View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        return v;
    }

    static final java.util.Locale UK = java.util.Locale.forLanguageTag("uk-UA");

    static final String[] MONTHS = {"січня", "лютого", "березня", "квітня", "травня", "червня",
        "липня", "серпня", "вересня", "жовтня", "листопада", "грудня"};
    static final String[] DAYS = {"понеділок", "вівторок", "середа", "четвер", "п'ятниця", "субота", "неділя"};

    /** "п'ятниця, 30 жовтня" */
    static String date(java.time.LocalDate d) {
        return DAYS[d.getDayOfWeek().getValue() - 1] + ", " + d.getDayOfMonth() + " " + MONTHS[d.getMonthValue() - 1];
    }
}
