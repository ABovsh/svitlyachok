package ua.abovsh.svitlo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;

/** An on/off switch in the app's colours. */
final class ToggleView extends View {
    interface Listener {
        void changed(boolean on);
    }

    private final Paint p = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Ui.Pal pal;
    private boolean on;

    ToggleView(Context c, Ui.Pal pal, boolean on, Listener l) {
        super(c);
        this.pal = pal;
        this.on = on;
        setOnClickListener(v -> {
            this.on = !this.on;
            invalidate();
            if (l != null) l.changed(this.on);
        });
    }

    @Override
    public void onInitializeAccessibilityNodeInfo(AccessibilityNodeInfo info) {
        super.onInitializeAccessibilityNodeInfo(info);
        info.setCheckable(true);
        info.setChecked(on);
        info.setClassName("android.widget.Switch");
    }

    private final RectF box = new RectF();

    @Override
    protected void onMeasure(int w, int h) {
        setMeasuredDimension(Ui.dp(getContext(), 52), Ui.dp(getContext(), 32));
    }

    @Override
    protected void onDraw(Canvas cv) {
        float w = getWidth();
        float h = getHeight();
        float r = h / 2f;
        float pad = Ui.dp(getContext(), 4);
        p.setColor(on ? pal.accent : pal.raised);
        box.set(0, 0, w, h);
        cv.drawRoundRect(box, r, r, p);
        p.setColor(on ? pal.accentInk : pal.dim);
        cv.drawCircle(on ? w - r : r, r, r - pad, p);
    }
}
