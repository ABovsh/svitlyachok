package ua.abovsh.svitlo;

import android.content.Context;
import android.graphics.Matrix;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.TextView;
import java.util.List;

/** A measured single-line summary, with its own full row when large text needs the space. */
final class DayHeader extends ViewGroup {
    private final TextView title;
    private final TextView total;
    private final ImageView share;
    private final List<String> formats;
    private final int button;
    private final int gap;
    private boolean split;
    private int firstHeight;

    DayHeader(Context c, Ui.Pal pal, String day, String slots, ImageView share) {
        super(c);
        this.share = share;
        button = Ui.dp(c, 48); gap = Ui.dp(c, 6);
        formats = Logic.daySummaries(slots);
        title = Ui.text(c, 18, Ui.heavy(c), pal.text); title.setText(day);
        total = Ui.text(c, 13, Ui.heavy(c), pal.onText); total.setSingleLine(true);
        total.setContentDescription(formats.get(0));
        // The vector's visible bounds end at 21 of 24. Keep the 48 dp touch target while
        // placing that visible edge exactly at the timeline edge, rather than centring it.
        share.setScaleType(ImageView.ScaleType.MATRIX);
        Matrix m = new Matrix();
        m.setTranslate(button - Ui.px(c, 21), (button - Ui.px(c, 24)) / 2);
        share.setImageMatrix(m);
        share.setPadding(0, 0, 0, 0);
        addView(title); addView(total); addView(share);
    }

    private boolean summaryFits(int room) {
        for (String format : formats) {
            total.setText(format);
            total.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
            if (total.getMeasuredWidth() <= room) return true;
        }
        return false;
    }

    @Override protected void onMeasure(int ws, int hs) {
        int width = MeasureSpec.getSize(ws);
        share.measure(MeasureSpec.makeMeasureSpec(button, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(button, MeasureSpec.EXACTLY));
        title.measure(MeasureSpec.makeMeasureSpec(Math.max(1, width - button - gap), MeasureSpec.AT_MOST), MeasureSpec.UNSPECIFIED);
        split = !summaryFits(width - title.getMeasuredWidth() - button - gap);
        if (split) summaryFits(width);
        firstHeight = Math.max(button, Math.max(title.getMeasuredHeight(), split ? 0 : total.getMeasuredHeight()));
        setMeasuredDimension(width, firstHeight + (split ? total.getMeasuredHeight() + Ui.dp(getContext(), 2) : 0));
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = r - l;
        int ty = (firstHeight - title.getMeasuredHeight()) / 2;
        title.layout(0, ty, title.getMeasuredWidth(), ty + title.getMeasuredHeight());
        int sy = (firstHeight - button) / 2;
        share.layout(width - button, sy, width, sy + button);
        int y = split ? firstHeight + Ui.dp(getContext(), 2) : (firstHeight - total.getMeasuredHeight()) / 2;
        int right = split ? width : width - button;
        total.layout(right - total.getMeasuredWidth(), y, right, y + total.getMeasuredHeight());
    }
}
