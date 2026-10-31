package ua.abovsh.svitlo;

import android.animation.ValueAnimator;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.Shader;
import android.view.View;

/**
 * The state at a glance, without reading: a lit green bulb with rays when power is scheduled,
 * a crossed-out red one for an outage, a half-lit amber one for a possible outage, grey
 * without a schedule. The glow
 * breathes slowly while power is on, only while the view is visible on screen.
 */
final class BulbView extends View {
    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Ui.Pal pal;
    private int state = Logic.UNKNOWN;
    private ValueAnimator pulse;
    private float glow = 1f;
    private boolean breathes = true;
    private final Scratch k = new Scratch();

    /** Reused between frames: the glow breathes at the display rate, nothing is allocated then. */
    static final class Scratch {
        final Path glass = new Path();
        final RectF r = new RectF();
        RadialGradient halo;
        float hx, hy, hr;
        int hc;
    }

    BulbView(Context c, Ui.Pal pal) {
        super(c);
        this.pal = pal;
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
        draw(cv, p, pal, state, getWidth() / 2f, getHeight() / 2f, size, glow, pal.card, k);
    }

    /** The disc and the bulb, also drawn into the widget. `under` is the colour behind the disc. */
    static void draw(Canvas cv, Paint p, Ui.Pal pal, int state, float cx, float cy, float size, float glow, int under,
            Scratch k) {
        float r = size / 2f, s = size;
        int lit = pal.fill(state), ink = pal.status(state);

        p.setStyle(Paint.Style.FILL);
        p.setShader(null);
        p.setColor(Ui.fade(lit, pal.dark ? 0.18f : 0.16f));
        cv.drawCircle(cx, cy, r, p);
        if (state == Logic.ON) {
            int a = pal.dark ? 0x70 : 0x50;
            if (k.halo == null || k.hx != cx || k.hy != cy || k.hr != r || k.hc != lit) {
                k.halo = new RadialGradient(cx, cy - s * 0.08f, r, new int[] {(lit & 0x00FFFFFF) | (a << 24),
                        lit & 0x00FFFFFF}, null, Shader.TileMode.CLAMP);
                k.hx = cx;
                k.hy = cy;
                k.hr = r;
                k.hc = lit;
            }
            p.setShader(k.halo);
            p.setAlpha((int) (255 * glow));
            cv.drawCircle(cx, cy, r, p);
            p.setShader(null);
            p.setAlpha(255);
        }

        // glass: a round top narrowing into the neck
        float gy = cy - s * 0.09f, gr = s * 0.21f;
        Path glass = k.glass;
        glass.reset();
        k.r.set(cx - gr, gy - gr, cx + gr, gy + gr);
        glass.arcTo(k.r, 140, 260);
        glass.lineTo(cx + s * 0.09f, cy + s * 0.17f);
        glass.lineTo(cx - s * 0.09f, cy + s * 0.17f);
        glass.close();
        float line = s * 0.045f;
        if (state == Logic.ON || state == Logic.MAYBE) {
            p.setColor(state == Logic.ON ? lit : Ui.fade(lit, 0.55f));
            cv.drawPath(glass, p);
        }
        if (state != Logic.ON) {
            p.setStyle(Paint.Style.STROKE);
            p.setStrokeWidth(line);
            p.setStrokeJoin(Paint.Join.ROUND);
            p.setColor(ink);
            cv.drawPath(glass, p);
            p.setStyle(Paint.Style.FILL);
        }
        // the cap
        p.setColor(ink);
        float cw = s * 0.10f;
        k.r.set(cx - cw, cy + s * 0.20f, cx + cw, cy + s * 0.245f);
        cv.drawRoundRect(k.r, line, line, p);
        k.r.set(cx - cw * 0.7f, cy + s * 0.27f, cx + cw * 0.7f, cy + s * 0.31f);
        cv.drawRoundRect(k.r, line, line, p);

        p.setStyle(Paint.Style.STROKE);
        p.setStrokeCap(Paint.Cap.ROUND);
        if (state == Logic.ON) {  // rays
            p.setStrokeWidth(line);
            p.setColor(lit);
            for (int a = 200; a <= 340; a += 35) {
                double rad = Math.toRadians(a);
                float c = (float) Math.cos(rad), sn = (float) Math.sin(rad);
                cv.drawLine(cx + c * s * 0.29f, gy + sn * s * 0.29f, cx + c * s * 0.37f, gy + sn * s * 0.37f, p);
            }
        } else if (state == Logic.OFF) {  // crossed out, with a gap in the colour behind
            float x0 = cx - s * 0.25f, y0 = cy - s * 0.30f, x1 = cx + s * 0.25f, y1 = cy + s * 0.30f;
            p.setStrokeWidth(line * 2.6f);
            p.setColor(Ui.blend(under, Ui.fade(lit, pal.dark ? 0.18f : 0.16f)));
            cv.drawLine(x0, y0, x1, y1, p);
            p.setStrokeWidth(line);
            p.setColor(ink);
            cv.drawLine(x0, y0, x1, y1, p);
        }
        p.setStyle(Paint.Style.FILL);
    }
}
