package ua.abovsh.svitlo;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.Shader;
import android.view.View;

/**
 * The state at a glance, without reading: a lit green bulb with rays when power is scheduled,
 * a crossed-out red one for an outage, a half-lit amber one for a possible outage, grey
 * without a schedule. The glow breathes slowly while power is on, only while the view is visible
 * on screen.
 */
final class BulbView extends View {
    private final Brush b;
    private int state = Logic.UNKNOWN;
    private ValueAnimator pulse;
    private float glow = 1f;
    private boolean breathes = true;

    BulbView(Context c, Ui.Pal pal) {
        super(c);
        b = new Brush(pal, pal.card);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);  // the words next to it say it
    }

    /** Small bulbs (one per place) stay still: one moving thing on screen is enough. */
    void still() {
        breathes = false;
        animate(false);
    }

    void set(int state) {
        this.state = state;
        invalidate();
        animate(isShown());
    }

    @Override
    public void onVisibilityAggregated(boolean visible) {
        super.onVisibilityAggregated(visible);
        animate(visible);
    }

    @Override
    protected void onDetachedFromWindow() {
        animate(false);
        super.onDetachedFromWindow();
    }

    private void animate(boolean on) {
        boolean want = on && breathes && state == Logic.ON && ValueAnimator.areAnimatorsEnabled();
        if (want && pulse == null) {
            pulse = ValueAnimator.ofFloat(0.55f, 1f);
            pulse.setDuration(2600);
            pulse.setRepeatMode(ValueAnimator.REVERSE);
            pulse.setRepeatCount(ValueAnimator.INFINITE);
            pulse.addUpdateListener(a -> {
                glow = (float) a.getAnimatedValue();
                invalidate();
            });
            pulse.start();
        } else if (!want && pulse != null) {
            pulse.cancel();
            pulse = null;
            glow = 1f;
        }
    }

    @Override
    protected void onDraw(Canvas cv) {
        float size = Math.min(getWidth(), getHeight());
        draw(b.on(cv), state, getWidth() / 2f, getHeight() / 2f, size, glow);
    }

    /** The disc and the bulb, also drawn into the widget; the brush knows the colour behind the disc. */
    static void draw(Brush b, int state, float cx, float cy, float size, float glow) {
        disc(b, state, cx, cy, size, glow);
        float line = size * 0.045f;
        glass(b, state, cx, cy, size, line);
        cap(b, state, cx, cy, size, line);
        if (state == Logic.ON) rays(b, cx, cy - size * 0.09f, size, line);
        else if (state == Logic.OFF) crossOut(b, cx, cy, size, line);
        b.p.setStyle(Paint.Style.FILL);
    }

    private static void disc(Brush b, int state, float cx, float cy, float size, float glow) {
        Paint p = b.p;
        float r = size / 2f;
        int lit = b.pal.fill(state);
        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        p.setColor(Ui.fade(lit, b.pal.dark ? 0.18f : 0.16f));
        b.cv.drawCircle(cx, cy, r, p);
        if (state != Logic.ON) return;
        if (b.halo == null || b.haloX != cx || b.haloY != cy || b.haloR != r || b.haloColour != lit) {
            int a = b.pal.dark ? 0x70 : 0x50;
            b.halo = new RadialGradient(cx, cy - size * 0.08f, r,
                    new int[] {(lit & 0x00FFFFFF) | (a << 24), lit & 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
            b.haloX = cx;
            b.haloY = cy;
            b.haloR = r;
            b.haloColour = lit;
        }
        p.setShader(b.halo);
        p.setAlpha((int) (255 * glow));  // the breathing: one gradient, only its strength changes
        b.cv.drawCircle(cx, cy, r, p);
        p.setShader(null);
        p.setAlpha(255);
    }

    /** A round top narrowing into the neck: filled when lit, outlined otherwise. */
    private static void glass(Brush b, int state, float cx, float cy, float s, float line) {
        Paint p = b.p;
        int lit = b.pal.fill(state);
        float gy = cy - s * 0.09f;
        float gr = s * 0.21f;
        Path glass = b.path;
        glass.reset();
        b.r.set(cx - gr, gy - gr, cx + gr, gy + gr);
        glass.arcTo(b.r, 140, 260);
        glass.lineTo(cx + s * 0.09f, cy + s * 0.17f);
        glass.lineTo(cx - s * 0.09f, cy + s * 0.17f);
        glass.close();
        if (state == Logic.ON || state == Logic.MAYBE) {
            p.setColor(state == Logic.ON ? lit : Ui.fade(lit, 0.55f));
            b.cv.drawPath(glass, p);
        }
        if (state != Logic.ON) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(line);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setColor(b.pal.status(state));
            b.cv.drawPath(glass, p);
            p.setStyle(Paint.Style.FILL);
        }
    }

    private static void cap(Brush b, int state, float cx, float cy, float s, float line) {
        b.p.setColor(b.pal.status(state));
        float cw = s * 0.10f;
        b.r.set(cx - cw, cy + s * 0.20f, cx + cw, cy + s * 0.245f);
        b.cv.drawRoundRect(b.r, line, line, b.p);
        b.r.set(cx - cw * 0.7f, cy + s * 0.27f, cx + cw * 0.7f, cy + s * 0.31f);
        b.cv.drawRoundRect(b.r, line, line, b.p);
    }

    private static void rays(Brush b, float cx, float gy, float s, float line) {
        Paint p = b.p;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(line);
        p.setColor(b.pal.fill(Logic.ON));
        for (int a = 200; a <= 340; a += 35) {
            double rad = Math.toRadians(a);
            float c = (float) Math.cos(rad);
            float sn = (float) Math.sin(rad);
            b.cv.drawLine(cx + c * s * 0.29f, gy + sn * s * 0.29f, cx + c * s * 0.37f, gy + sn * s * 0.37f, p);
        }
    }

    /** Crossed out, with a gap in the colour behind so the line reads apart from the bulb. */
    private static void crossOut(Brush b, float cx, float cy, float s, float line) {
        Paint p = b.p;
        float x0 = cx - s * 0.25f;
        float y0 = cy - s * 0.30f;
        float x1 = cx + s * 0.25f;
        float y1 = cy + s * 0.30f;
        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        p.setStrokeWidth(line * 2.6f);
        p.setColor(Ui.blend(b.under, Ui.fade(b.pal.fill(Logic.OFF), b.pal.dark ? 0.18f : 0.16f)));
        b.cv.drawLine(x0, y0, x1, y1, p);
        p.setStrokeWidth(line);
        p.setColor(b.pal.status(Logic.OFF));
        b.cv.drawLine(x0, y0, x1, y1, p);
    }
}
