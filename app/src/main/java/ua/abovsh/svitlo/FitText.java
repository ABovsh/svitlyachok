package ua.abovsh.svitlo;

import android.content.Context;
import android.graphics.Typeface;
import android.text.Layout;
import android.text.StaticLayout;
import android.text.TextPaint;
import android.text.TextUtils;
import android.util.TypedValue;
import android.widget.TextView;

/** Fits a local width budget; the rest of the application keeps normal system-scaled text. */
class FitText extends TextView {
    private final float preferredSp;
    private final float minimumDp;
    private final int preferredLines;
    private float growth;
    private CharSequence[] formats;
    private Boolean single;
    private final CharSequence[] current = new CharSequence[1];
    private final TextPaint measuring = new TextPaint();

    FitText(Context context, float sp, float minDp, int lines, Typeface face, int ink) {
        super(context);
        preferredSp = sp; minimumDp = minDp; preferredLines = lines;
        setTypeface(face); setTextColor(ink); setIncludeFontPadding(false);
        setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE);
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
    }

    FitText compact(float maxGrowth) { growth = maxGrowth; return this; }

    void formats(CharSequence... values) {
        formats = values.clone();
        setText(values[0]);
    }

    private boolean fits(CharSequence text, float px, int room) {
        TextPaint paint = measuring; paint.set(getPaint()); paint.setTextSize(px);
        if (preferredLines == 1) return Layout.getDesiredWidth(text, paint) <= room;
        StaticLayout layout = StaticLayout.Builder.obtain(text, 0, text.length(), paint, Math.max(1, room))
                .setIncludePad(false).setBreakStrategy(getBreakStrategy())
                .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NONE).build();
        return layout.getLineCount() <= preferredLines;
    }

    private int firstFit(CharSequence[] choices, float size, int room) {
        for (int i = 0; i < choices.length; i++) if (fits(choices[i], size, room)) return i;
        return -1;
    }

    private float largestFit(CharSequence text, float low, float high, int room) {
        for (int i = 0; i < 8; i++) {
            float mid = (low + high) / 2;
            if (fits(text, mid, room)) low = mid; else high = mid;
        }
        return low;
    }

    @Override protected void onMeasure(int width, int height) {
        float largest = growth > 0 ? Ui.compactSp(getContext(), preferredSp, growth) : Ui.sp(getContext(), preferredSp);
        float smallest = Math.min(largest, Ui.px(getContext(), minimumDp));
        current[0] = getText();
        CharSequence[] choices = formats == null ? current : formats;
        int room = MeasureSpec.getMode(width) == MeasureSpec.UNSPECIFIED ? Integer.MAX_VALUE
                : Math.max(1, MeasureSpec.getSize(width) - getCompoundPaddingLeft() - getCompoundPaddingRight());
        int atPreferred = firstFit(choices, largest, room);
        int atMinimum = atPreferred >= 0 ? atPreferred : firstFit(choices, smallest, room);
        boolean found = atMinimum >= 0;
        CharSequence chosen = choices[found ? atMinimum : choices.length - 1];
        float size = largest;
        if (atPreferred < 0) size = found ? largestFit(chosen, smallest, largest, room) : smallest;
        if (!TextUtils.equals(getText(), chosen)) setText(chosen);
        boolean oneLine = preferredLines == 1 && found;
        if (single == null || single != oneLine) { setSingleLine(oneLine); single = oneLine; }
        setHorizontallyScrolling(false);
        setTextSize(TypedValue.COMPLEX_UNIT_PX, size);
        super.onMeasure(width, height);
    }
}
