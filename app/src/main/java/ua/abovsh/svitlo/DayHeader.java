package ua.abovsh.svitlo;

import android.content.Context;
import android.graphics.Matrix;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.accessibility.AccessibilityNodeInfo;
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
    private final int row;
    private static final float ICON_SCALE = 20f / 24;
    private Drawable chevron;
    private boolean markerVisible;
    private boolean split;
    private int firstHeight;

    /** share may be null: a day with no schedule has nothing to share. */
    DayHeader(Context c, Ui.Pal pal, String day, List<String> summaries, int ink, ImageView share) {
        super(c);
        this.share = share;
        button = share == null ? 0 : Ui.dp(c, 48); gap = Ui.dp(c, 6); row = Ui.dp(c, 30);
        formats = summaries;
        title = Ui.text(c, 16, Ui.heavy(c), pal.text); title.setText(day);
        // One line wherever it fits; under the title it may wrap by words rather than run off the left edge.
        total = Ui.text(c, 13, Ui.heavy(c), ink); total.setGravity(android.view.Gravity.END);
        title.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.compactSp(c, 16, 1.1f));
        total.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, Ui.compactSp(c, 13, 1.1f));
        total.setContentDescription(formats.get(0));
        // The vector's visible bounds end at 21 of 24. Keep the 48 dp touch target while
        // placing that visible edge exactly at the timeline edge, rather than centring it.
        addView(title); addView(total);
        if (share == null) return;
        share.setScaleType(ImageView.ScaleType.MATRIX);
        Matrix m = new Matrix();
        m.setScale(ICON_SCALE, ICON_SCALE);
        m.postTranslate(button - Ui.px(c, 21 * ICON_SCALE), (row - Ui.px(c, 24 * ICON_SCALE)) / 2);
        share.setImageMatrix(m);
        share.setPadding(0, 0, 0, 0);
        addView(share);
    }

    /**
     * The whole row opens the list: the title, the summary and the room between them. Sharing keeps
     * its own target; for TalkBack and keyboards the title stays the one button.
     */
    void openIntervals(Ui.Pal pal, View.OnClickListener click) {
        setOnClickListener(click);
        setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        android.util.TypedValue ripple = new android.util.TypedValue();
        if (getContext().getTheme().resolveAttribute(android.R.attr.selectableItemBackground, ripple, true)) {
            setForeground(getContext().getDrawable(ripple.resourceId));
        }
        chevron = getContext().getDrawable(R.drawable.ic_chevron).mutate();
        chevron.setTint(pal.accentText);
        int size = Ui.dp(getContext(), 13);
        chevron.setBounds(0, 0, size, size);
        title.setCompoundDrawablePadding(Ui.dp(getContext(), 3));
        title.setContentDescription(title.getText() + ". Показати інтервали");
        title.setOnClickListener(click);
        title.setFocusable(true);
        title.setAccessibilityDelegate(new View.AccessibilityDelegate() {
            @Override public void onInitializeAccessibilityNodeInfo(View view, AccessibilityNodeInfo info) {
                super.onInitializeAccessibilityNodeInfo(view, info);
                info.setClassName(android.widget.Button.class.getName());
            }
        });
    }

    void restoreKeyboardFocus() {
        title.requestFocus();
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
        if (share != null) share.measure(MeasureSpec.makeMeasureSpec(button, MeasureSpec.EXACTLY),
                MeasureSpec.makeMeasureSpec(row, MeasureSpec.EXACTLY));
        int titleRoom = Math.max(1, width - button - gap);
        int titleSpec = MeasureSpec.makeMeasureSpec(titleRoom, MeasureSpec.AT_MOST);
        int textWidth = (int) Math.ceil(title.getPaint().measureText(title.getText().toString()));
        int titleWidth = Math.min(textWidth, titleRoom);
        if (chevron == null) {
            title.measure(titleSpec, MeasureSpec.UNSPECIFIED);
            titleWidth = title.getMeasuredWidth();
        }
        split = !summaryFits(titleRoom - titleWidth);
        if (split && !summaryFits(width)) {
            total.setText(formats.get(formats.size() - 1));
            total.measure(MeasureSpec.makeMeasureSpec(width, MeasureSpec.AT_MOST), MeasureSpec.UNSPECIFIED);
        }
        if (chevron != null) {
            int markerWidth = chevron.getBounds().width() + title.getCompoundDrawablePadding();
            int available = titleRoom - (split ? 0 : total.getMeasuredWidth());
            boolean show = textWidth + markerWidth <= available;
            if (show != markerVisible) {
                markerVisible = show;
                title.setCompoundDrawablesRelative(null, null, show ? chevron : null, null);
            }
        }
        if (chevron != null) title.measure(titleSpec, MeasureSpec.UNSPECIFIED);
        firstHeight = Math.max(row, Math.max(title.getMeasuredHeight(), split ? 0 : total.getMeasuredHeight()));
        setMeasuredDimension(width, firstHeight + (split ? total.getMeasuredHeight() + Ui.dp(getContext(), 2) : 0));
    }

    @Override protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int width = r - l;
        int ty = (firstHeight - title.getMeasuredHeight()) / 2;
        title.layout(0, ty, title.getMeasuredWidth(), ty + title.getMeasuredHeight());
        int sy = (firstHeight - row) / 2;
        if (share != null) share.layout(width - button, sy, width, sy + row);
        int y = split ? firstHeight + Ui.dp(getContext(), 2) : (firstHeight - total.getMeasuredHeight()) / 2;
        int right = split ? width : width - button;
        total.layout(right - total.getMeasuredWidth(), y, right, y + total.getMeasuredHeight());
    }
}
