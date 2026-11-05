package ua.abovsh.svitlo;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Independent width-based grid; every neighbour in a row has the same measured height. */
final class AdaptiveGrid extends ViewGroup {
    private final int maximumColumns;
    private final int gap;
    private int columns = 1;
    private int cellWidth;
    private int[] heights = new int[0];

    AdaptiveGrid(Context context, int maxColumns, int gapDp) {
        super(context); maximumColumns = maxColumns; gap = Ui.dp(context, gapDp);
    }

    @Override protected void onMeasure(int width, int height) {
        int available = Math.max(1, MeasureSpec.getSize(width) - getPaddingLeft() - getPaddingRight());
        int visible = 0;
        int minimum = 1;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            visible++; minimum = Math.max(minimum, child.getMinimumWidth());
        }
        columns = Math.max(1, Math.min(Math.min(maximumColumns, visible), (available + gap) / (minimum + gap)));
        if (visible > columns) {
            int rowsNeeded = (visible + columns - 1) / columns;
            columns = (visible + rowsNeeded - 1) / rowsNeeded;
        }
        cellWidth = Math.max(1, (available - gap * (columns - 1)) / columns);
        int rows = (visible + columns - 1) / columns;
        prepareRows(rows);
        int index = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            child.measure(MeasureSpec.makeMeasureSpec(cellWidth, MeasureSpec.EXACTLY), MeasureSpec.UNSPECIFIED);
            int row = index++ / columns;
            heights[row] = Math.max(heights[row], child.getMeasuredHeight());
        }
        int total = getPaddingTop() + getPaddingBottom() + Math.max(0, rows - 1) * gap;
        for (int h : heights) total += h;
        index = 0;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            child.measure(MeasureSpec.makeMeasureSpec(cellWidth, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(heights[index++ / columns], MeasureSpec.EXACTLY));
        }
        setMeasuredDimension(resolveSize(available + getPaddingLeft() + getPaddingRight(), width), resolveSize(total, height));
    }

    private void prepareRows(int rows) {
        if (heights.length != rows) heights = new int[rows];
        else java.util.Arrays.fill(heights, 0);
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int index = 0;
        int y = getPaddingTop();
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child.getVisibility() == GONE) continue;
            int row = index / columns;
            int col = index % columns;
            int x = getPaddingLeft() + col * (cellWidth + gap);
            child.layout(x, y, x + cellWidth, y + heights[row]);
            index++;
            if (index % columns == 0) y += heights[row] + gap;
        }
    }
}
