package ua.abovsh.svitlo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.text.Layout;
import android.view.Gravity;
import android.widget.TextView;

/** A button whose icon stands right before its words, the two centred together, however wide it is. */
final class ActionButton extends TextView {
    ActionButton(Context c) {
        super(c);
        setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        canvas.save();
        canvas.translate(Math.max(0f, (getWidth() - getPaddingLeft() - getPaddingRight() - contentWidth()) / 2f), 0f);
        super.onDraw(canvas);
        canvas.restore();
    }

    private float contentWidth() {
        float words = 0;
        Layout layout = getLayout();
        if (layout != null) for (int i = 0; i < layout.getLineCount(); i++) words = Math.max(words, layout.getLineWidth(i));
        Drawable icon = getCompoundDrawables()[0];
        return words + (icon == null ? 0 : icon.getBounds().width() + getCompoundDrawablePadding());
    }
}
