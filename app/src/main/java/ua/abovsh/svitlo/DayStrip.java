package ua.abovsh.svitlo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RectF;
import android.view.View;

/** A whole day as one thin line along the bottom of a place's tile, with the mark of now. */
final class DayStrip extends View {
    private final Ui.Pal pal;
    private final Paint paint = new Paint();
    private final String slots;
    private final float now;
    private final float corner;
    private final Path edge = new Path();
    private final RectF box = new RectF();

    /** corner: the radius of the tile's inner edge, which the ends of the line follow. */
    DayStrip(Context c, Ui.Pal pal, String slots, float now, float corner) {
        super(c);
        this.corner = corner;
        this.pal = pal;
        this.slots = slots;
        this.now = now;
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
    }

    @Override
    protected void onDraw(Canvas cv) {
        float slot = getWidth() / 48f;
        float height = (float) getHeight();
        edge.reset();
        box.set(0, height - 2 * corner, (float) getWidth(), height);
        edge.addRoundRect(box, corner, corner, Path.Direction.CW);
        cv.save();
        cv.clipPath(edge);
        int i = 0;
        while (i < 48) {
            char c = slots.charAt(i);
            int j = i;
            while (j < 48 && slots.charAt(j) == c) j++;
            paint.setColor(pal.slot(c));
            cv.drawRect(i * slot, 0, j * slot, height, paint);
            i = j;
        }
        paint.setColor(Ui.fade(pal.card, pal.veil));
        cv.drawRect(0, 0, now * slot, height, paint);
        float half = Ui.px(getContext(), 1);
        paint.setColor(Ui.Pal.MARKER_EDGE);
        cv.drawRect(now * slot - 1.5f * half, 0, now * slot + 1.5f * half, height, paint);
        paint.setColor(Ui.Pal.MARKER);
        cv.drawRect(now * slot - half, 0, now * slot + half, height, paint);
        cv.restore();
    }
}
