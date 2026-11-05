package ua.abovsh.svitlo;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/**
 * Chips left to right, wrapping to the next line when they do not fit, each centred on its line.
 */
final class FlowRow extends ViewGroup {
    private final int gap;
    private final int rowGap;
    private int[] lines = new int[1];  // the height of each line; a line per child at most

    FlowRow(Context c, int gap) {
        this(c, gap, gap);
    }

    FlowRow(Context c, int gap, int rowGap) {
        super(c);
        this.gap = gap;
        this.rowGap = rowGap;
    }

    @Override
    public void onViewAdded(View child) {
        super.onViewAdded(child);
        if (lines.length < getChildCount() + 1) lines = new int[getChildCount() + 1];
    }

    @Override
    protected void onMeasure(int ws, int hs) {
        boolean unlimited = MeasureSpec.getMode(ws) == MeasureSpec.UNSPECIFIED;
        int max = unlimited ? Integer.MAX_VALUE / 4 : Math.max(1, MeasureSpec.getSize(ws) - getPaddingLeft() - getPaddingRight());
        java.util.Arrays.fill(lines, 0);
        int x = 0;
        int y = 0;
        int row = 0;
        int used = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v.getVisibility() == GONE) continue;
            v.measure(unlimited ? MeasureSpec.UNSPECIFIED : MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST), MeasureSpec.UNSPECIFIED);
            if (x > 0 && x + v.getMeasuredWidth() > max) {
                x = 0;
                y += lines[row] + rowGap;
                row++;
            }
            x += v.getMeasuredWidth() + gap;
            used = Math.max(used, x - gap);
            lines[row] = Math.max(lines[row], v.getMeasuredHeight());
        }
        setMeasuredDimension(resolveSize(used + getPaddingLeft() + getPaddingRight(), ws),
                resolveSize(y + lines[row] + getPaddingTop() + getPaddingBottom(), hs));
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int max = Math.max(1, r - l - getPaddingLeft() - getPaddingRight());
        int x = 0;
        int y = 0;
        int row = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            if (v.getVisibility() == GONE) continue;
            int w = v.getMeasuredWidth();
            if (x > 0 && x + w > max) {
                x = 0;
                y += lines[row] + rowGap;
                row++;
            }
            int left = getPaddingLeft() + x;
            int top = getPaddingTop() + y + (lines[row] - v.getMeasuredHeight()) / 2;
            v.layout(left, top, left + w, top + v.getMeasuredHeight());
            x += w + gap;
        }
    }
}
