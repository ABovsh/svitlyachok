package ua.abovsh.svitlo;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Chips left to right, wrapping to the next line when they do not fit. */
final class FlowRow extends ViewGroup {
    private final int gap, rowGap;

    FlowRow(Context c, int gap) {
        this(c, gap, gap);
    }

    FlowRow(Context c, int gap, int rowGap) {
        super(c);
        this.gap = gap;
        this.rowGap = rowGap;
    }

    @Override
    protected void onMeasure(int ws, int hs) {
        int max = MeasureSpec.getSize(ws), x = 0, y = 0, line = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            v.measure(MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST), MeasureSpec.UNSPECIFIED);
            if (x > 0 && x + v.getMeasuredWidth() > max) {
                x = 0;
                y += line + rowGap;
                line = 0;
            }
            x += v.getMeasuredWidth() + gap;
            line = Math.max(line, v.getMeasuredHeight());
        }
        setMeasuredDimension(max, y + line);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int max = r - l, x = 0, y = 0, line = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (x > 0 && x + v.getMeasuredWidth() > max) {
                x = 0;
                y += line + rowGap;
                line = 0;
            }
            v.layout(x, y, x + v.getMeasuredWidth(), y + v.getMeasuredHeight());
            x += v.getMeasuredWidth() + gap;
            line = Math.max(line, v.getMeasuredHeight());
        }
    }
}
