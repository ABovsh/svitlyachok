package ua.abovsh.svitlo;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/**
 * Chips left to right, wrapping to the next line when they do not fit, each centred on its line.
 * Spread: when all fit on one line, the last one sits at the right edge.
 */
final class FlowRow extends ViewGroup {
    private final int gap;
    private final int rowGap;
    private boolean spread;
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

    void spread() {
        spread = true;
    }

    @Override
    protected void onMeasure(int ws, int hs) {
        int max = MeasureSpec.getSize(ws);
        java.util.Arrays.fill(lines, 0);
        int x = 0;
        int y = 0;
        int row = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View v = getChildAt(i);
            v.measure(MeasureSpec.makeMeasureSpec(max, MeasureSpec.AT_MOST), MeasureSpec.UNSPECIFIED);
            if (x > 0 && x + v.getMeasuredWidth() > max) {
                x = 0;
                y += lines[row] + rowGap;
                row++;
            }
            x += v.getMeasuredWidth() + gap;
            lines[row] = Math.max(lines[row], v.getMeasuredHeight());
        }
        setMeasuredDimension(max, y + lines[row]);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int max = r - l;
        int x = 0;
        int y = 0;
        int row = 0;
        int n = getChildCount();
        for (int i = 0; i < n; i++) {
            View v = getChildAt(i);
            int w = v.getMeasuredWidth();
            if (x > 0 && x + w > max) {
                x = 0;
                y += lines[row] + rowGap;
                row++;
            }
            int left = spread && row == 0 && i == n - 1 && i > 0 ? max - w : x;
            int top = y + (lines[row] - v.getMeasuredHeight()) / 2;
            v.layout(left, top, left + w, top + v.getMeasuredHeight());
            x += w + gap;
        }
    }
}
