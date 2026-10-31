package ua.abovsh.svitlo;

import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RadialGradient;
import android.graphics.RectF;

/**
 * What the drawing code shares: the canvas, one paint, the palette, the colour behind, and
 * shapes reused between frames, so a redraw allocates nothing.
 */
final class Brush {
    Canvas cv;
    final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    final Ui.Pal pal;
    final int under;
    final Path path = new Path();
    final RectF r = new RectF();
    final RectF r2 = new RectF();
    RadialGradient halo;
    float haloX;
    float haloY;
    float haloR;
    int haloColour;

    Brush(Ui.Pal pal, int under) {
        this.pal = pal;
        this.under = under;
    }

    Brush on(Canvas cv) {
        this.cv = cv;
        return this;
    }
}
