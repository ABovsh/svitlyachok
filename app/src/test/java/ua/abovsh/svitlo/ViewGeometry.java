package ua.abovsh.svitlo;

import static org.junit.Assert.assertTrue;

import android.graphics.Rect;
import android.text.Layout;
import android.view.View;
import android.view.ViewGroup;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Predicate;

final class ViewGeometry {
    private ViewGeometry() {}

    static void layout(View view, int width, int height, int heightMode) {
        view.measure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(height, heightMode));
        view.layout(0, 0, view.getMeasuredWidth(), view.getMeasuredHeight());
    }

    static void inspect(View root, List<String> failures) {
        if (root.getVisibility() != View.VISIBLE) return;
        if (root instanceof TextView text && !(text instanceof EditText) && text.length() > 0) {
            inspectText(text, failures);
        }
        if (!(root instanceof ViewGroup group)) return;
        List<View> children = new ArrayList<>();
        for (int i = 0; i < group.getChildCount(); i++) {
            View child = group.getChildAt(i);
            if (child.getVisibility() != View.VISIBLE) continue;
            if (child.getLeft() < -1 || child.getTop() < -1
                    || (!(group instanceof HorizontalScrollView) && child.getRight() > group.getWidth() + 1)
                    || (!(group instanceof ScrollView) && child.getBottom() > group.getHeight() + 1)) {
                failures.add("outside parent " + describe(child) + " in " + describe(group));
            }
            if (child.getWidth() > 0 && child.getHeight() > 0) children.add(child);
            inspect(child, failures);
        }
        for (int i = 0; i < children.size(); i++) {
            View a = children.get(i);
            for (View b : children.subList(i + 1, children.size())) {
                if (!(a instanceof TextView) && !(b instanceof TextView)) continue;
                Rect box = bounds(a);
                if (box.intersect(bounds(b)) && box.width() > 1 && box.height() > 1) {
                    failures.add("overlap " + describe(a) + " / " + describe(b));
                }
            }
        }
    }

    private static void inspectText(TextView view, List<String> failures) {
        Layout layout = view.getLayout();
        int width = view.getWidth() - view.getCompoundPaddingLeft() - view.getCompoundPaddingRight();
        int height = view.getHeight() - view.getCompoundPaddingTop() - view.getCompoundPaddingBottom();
        if (layout == null || layout.getLineCount() == 0 || width <= 0) {
            failures.add("no room for " + describe(view));
            return;
        }
        if (layout.getHeight() > height + 2 || layout.getLineEnd(layout.getLineCount() - 1) < view.length()) {
            failures.add("clipped height " + describe(view) + ", layout " + layout.getHeight() + "/" + height);
        }
        for (int line = 0; line < layout.getLineCount(); line++) {
            if (layout.getEllipsisCount(line) != 0 || layout.getLineMax(line) > width + 2) {
                failures.add("clipped width " + describe(view) + ", line " + layout.getLineMax(line) + "/" + width);
            }
        }
    }

    static <T extends View> List<T> visible(View root, Class<T> type, Predicate<T> predicate) {
        List<T> matches = new ArrayList<>();
        collect(root, type, predicate, matches);
        return matches;
    }

    private static <T extends View> void collect(View view, Class<T> type, Predicate<T> predicate, List<T> matches) {
        if (view.getVisibility() != View.VISIBLE) return;
        if (type.isInstance(view) && predicate.test(type.cast(view))) matches.add(type.cast(view));
        if (view instanceof ViewGroup group) {
            for (int i = 0; i < group.getChildCount(); i++) collect(group.getChildAt(i), type, predicate, matches);
        }
    }

    static <T extends View> T one(View root, Class<T> type, Predicate<T> predicate, String label) {
        List<T> matches = visible(root, type, predicate);
        assertTrue(label + " expected one " + type.getSimpleName() + ", found " + matches.size(), matches.size() == 1);
        return matches.get(0);
    }

    static Rect bounds(View view) {
        return new Rect(view.getLeft(), view.getTop(), view.getRight(), view.getBottom());
    }

    static String describe(View view) {
        String label = view instanceof TextView text ? text.getText().toString()
                : String.valueOf(view.getContentDescription());
        return view.getClass().getSimpleName() + " [" + label + "] " + bounds(view);
    }
}
