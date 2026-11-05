package ua.abovsh.svitlo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

/** Two independent information columns, measured as shared typographic rows. */
final class HeroCard extends ViewGroup {
    private final Ui.Pal pal;
    private final String location;
    private final Logic.Now initial;
    private final boolean highlighted;
    private final BulbView bulb;
    private final TextView label, currentTitle, futureTitle, currentClock, futureClock;
    private final TextView currentSide, futureSide, elapsed, remaining;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint measuring = new Paint();
    private final Paint.FontMetrics titleMetrics = new Paint.FontMetrics();
    private final RectF futureBounds = new RectF();
    private boolean horizontal;
    private int leftRoom, rightRoom, half, bodyTop;
    private final Band[] shared, leftBands, rightBands;

    HeroCard(Context c, Ui.Pal colours, String name, Logic.Now now, String[] next, boolean lit) {
        super(c); pal = colours; location = name; initial = now; highlighted = lit;
        setPadding(Ui.dp(c, 11), Ui.dp(c, 7), Ui.dp(c, 11), Ui.dp(c, 9));
        setWillNotDraw(false); setFocusable(true);
        if (Build.VERSION.SDK_INT >= 28) setScreenReaderFocusable(true);
        String[] start = Logic.widgetStart(now);
        int nextInk = now.next == Logic.UNKNOWN ? pal.muted : pal.status(now.next);
        if (lit) nextInk = now.next == Logic.MAYBE ? pal.accentInk : pal.ink;
        FitText place = new FitText(c, 13, 12, 1, Ui.heavy(c), pal.text).compact(1.1f);
        place.setText(name); place.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); addView(place); label = place;
        currentTitle = text(now.title, 20, Ui.heavy(c), pal.status(now.state));
        futureTitle = text(next[0], 20, Ui.heavy(c), nextInk);
        currentClock = text(start[0], 27, start[0].contains(":") ? Ui.display(c) : Ui.bold(c), pal.text);
        futureClock = text(next[1], 27, next[1].contains(":") ? Ui.display(c) : Ui.bold(c), nextInk);
        currentSide = text(start[1], 13, Ui.bold(c), pal.muted);
        futureSide = text(next[2], 13, Ui.bold(c), lit ? nextInk : pal.muted);
        elapsed = text(now.already, 13.5f, Ui.body(c), pal.muted);
        remaining = text(Logic.heroCountdown(now), 13.5f, Ui.bold(c), lit ? nextInk : pal.text);
        bulb = new BulbView(c, pal); bulb.bare(); bulb.still(); bulb.set(now.state);
        bulb.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); addView(bulb);
        int right = Logic.widgetRightState(now, next[1]);
        int[] glow = new int[17];
        for (int i = 0; i < glow.length; i++) glow[i] = Logic.widgetGlow(pal.soft(now.state), pal.soft(right), pal.card, .5f, i / 16f);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, glow);
        bg.setCornerRadius(Ui.px(c, 18)); setBackground(bg);
        shared = new Band[] {new Band(26, currentTitle, futureTitle),
                new Band(0, currentClock, currentSide, futureClock, futureSide), new Band(0, elapsed, remaining)};
        leftBands = new Band[] {new Band(26, currentTitle), new Band(0, currentClock, currentSide), new Band(0, elapsed)};
        rightBands = new Band[] {new Band(26, futureTitle), new Band(0, futureClock, futureSide), new Band(0, remaining)};
        update(now);
    }

    private TextView text(String value, float sp, android.graphics.Typeface face, int colour) {
        TextView t = Ui.text(getContext(), sp, face, colour); t.setText(value);
        t.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE);
        t.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); addView(t); return t;
    }

    void update(Logic.Now now) {
        elapsed.setText(now.already); remaining.setText(Logic.heroCountdown(now));
        setContentDescription(location + ". " + Logic.heroDescription(now));
    }

    private void size(TextView t, float pixels) { t.setTextSize(TypedValue.COMPLEX_UNIT_PX, pixels); }

    private float width(TextView t, float pixels) {
        Paint p = measuring; p.set(t.getPaint()); p.setTextSize(pixels);
        return p.measureText(t.getText().toString());
    }

    private float sideWidth(TextView side) {
        return side.getVisibility() == GONE ? 0 : (float) Math.ceil(width(side, side.getTextSize())) + 1 + Ui.px(getContext(), 6);
    }

    private boolean clockFits(TextView clock, TextView side, float pixels, int room) {
        return !clock.getText().toString().contains(":") || Math.ceil(width(clock, pixels)) + 1 + sideWidth(side) <= room;
    }

    private boolean primaryFits(TextView clock, float pixels, int room) {
        return !clock.getText().toString().contains(":") || Math.ceil(width(clock, pixels)) + 1 <= room;
    }

    private void optionalSide(TextView clock, TextView side, float pixels, int room) {
        float extra = (float) Math.ceil(width(side, side.getTextSize())) + 1 + Ui.px(getContext(), 6);
        boolean visible = side.getText().length() > 0 && Math.ceil(width(clock, pixels)) + 1 + extra <= room;
        side.setVisibility(visible ? VISIBLE : GONE);
    }

    private void measureText(TextView t, int room, boolean intrinsic) {
        if (t.getText().length() == 0 || t.getVisibility() == GONE) {
            t.setVisibility(GONE); return;
        }
        int w = intrinsic ? Math.min(room, (int) Math.ceil(width(t, t.getTextSize())) + 1) : room;
        t.measure(MeasureSpec.makeMeasureSpec(Math.max(1, w), MeasureSpec.EXACTLY), MeasureSpec.UNSPECIFIED);
    }

    private float fitPair(TextView left, TextView right, float preferred, float minimum, int lw, int rw) {
        float low = minimum, high = preferred;
        if (width(left, high) <= lw && width(right, high) <= rw) return high;
        for (int i = 0; i < 9; i++) {
            float mid = (low + high) / 2;
            if (width(left, mid) <= lw && width(right, mid) <= rw) low = mid; else high = mid;
        }
        return low;
    }

    @Override protected void onMeasure(int width, int height) {
        Context c = getContext();
        int inner = Math.max(1, MeasureSpec.getSize(width) - getPaddingLeft() - getPaddingRight());
        half = inner / 2;
        int proposedLeft = Math.max(1, half - Ui.dp(c, 8));
        int proposedRight = Math.max(1, inner - half - Ui.dp(c, 14));
        float minimumClock = Ui.compactSp(c, 17, 1.1f);
        horizontal = primaryFits(currentClock, minimumClock, proposedLeft)
                && primaryFits(futureClock, minimumClock, proposedRight);
        leftRoom = horizontal ? proposedLeft : Math.max(1, inner - Ui.dp(c, 8));
        rightRoom = horizontal ? proposedRight : Math.max(1, inner - Ui.dp(c, 14));
        configureClocks();
        configureHeadings();
        measureWords(inner);
        int icon = Ui.dp(c, 25); bulb.measure(MeasureSpec.makeMeasureSpec(icon, MeasureSpec.EXACTLY), MeasureSpec.makeMeasureSpec(icon, MeasureSpec.EXACTLY));
        bodyTop = getPaddingTop() + label.getMeasuredHeight() + Ui.dp(c, 4);
        int content;
        measureBands(shared); measureBands(leftBands); measureBands(rightBands);
        if (horizontal) content = total(shared);
        else content = total(leftBands) + Ui.dp(c, 8) + total(rightBands);
        setMeasuredDimension(resolveSize(inner + getPaddingLeft() + getPaddingRight(), width), resolveSize(bodyTop + content + getPaddingBottom(), height));
    }

    private void configureClocks() {
        Context c = getContext();
        size(currentSide, Ui.sp(c, 13)); size(futureSide, Ui.sp(c, 13));
        float minimumClock = Ui.compactSp(c, 17, 1.1f);
        float preferredClock = Ui.compactSp(c, 27, 1.1f);
        optionalSide(currentClock, currentSide, preferredClock, leftRoom);
        optionalSide(futureClock, futureSide, preferredClock, rightRoom);
        float low = minimumClock, high = preferredClock;
        for (int i = 0; i < 10; i++) {
            float mid = (low + high) / 2;
            if (clockFits(currentClock, currentSide, mid, leftRoom) && clockFits(futureClock, futureSide, mid, rightRoom)) low = mid;
            else high = mid;
        }
        size(currentClock, currentClock.getText().toString().contains(":") ? low : Ui.compactSp(c, 15, 1.1f));
        size(futureClock, futureClock.getText().toString().contains(":") ? low : Ui.compactSp(c, 15, 1.1f));
    }

    private void configureHeadings() {
        Context c = getContext();
        int titleRoom = Math.max(1, leftRoom - Ui.dp(c, 30));
        float titleMax = Ui.compactSp(c, 20, 1.1f);
        Paint titlePaint = measuring; titlePaint.set(currentTitle.getPaint()); titlePaint.setTextSize(titleMax);
        String title = Logic.fittedState(initial.title, titlePaint.measureText(initial.title), titleRoom);
        if (!android.text.TextUtils.equals(currentTitle.getText(), title)) currentTitle.setText(title);
        float titleSize = fitPair(currentTitle, futureTitle, titleMax, Ui.px(c, 14), titleRoom, rightRoom);
        size(currentTitle, titleSize); size(futureTitle, titleSize);
        float detail = fitPair(elapsed, remaining, Ui.sp(c, 13.5f), Ui.px(c, 12.5f), leftRoom, rightRoom);
        size(elapsed, detail); size(remaining, detail);
    }

    private void measureWords(int inner) {
        int titleRoom = Math.max(1, leftRoom - Ui.dp(getContext(), 30));
        measureText(label, inner, false);
        measureText(currentTitle, titleRoom, false); measureText(futureTitle, rightRoom, false);
        measureText(currentSide, leftRoom, true); measureText(futureSide, rightRoom, true);
        measureText(currentClock, Math.max(1, leftRoom - (int) Math.ceil(sideWidth(currentSide))), currentClock.getText().toString().contains(":"));
        measureText(futureClock, Math.max(1, rightRoom - (int) Math.ceil(sideWidth(futureSide))), futureClock.getText().toString().contains(":"));
        measureText(elapsed, leftRoom, false); measureText(remaining, rightRoom, false);
    }

    private void measureBands(Band[] rows) { for (Band row : rows) row.measure(); }

    private int total(Band[] rows) {
        int sum = Ui.dp(getContext(), 2); // breathing room before the duration row, shared by both sides
        for (Band row : rows) sum += row.height;
        return sum;
    }

    private final class Band {
        private final int minimumDp;
        private final TextView[] texts;
        int height;
        int baseline;
        Band(int minimum, TextView... views) { minimumDp = minimum; texts = views; }
        void measure() {
            int ascent = 0, descent = 0;
            for (TextView t : texts) if (t.getVisibility() != GONE) {
                ascent = Math.max(ascent, t.getBaseline()); descent = Math.max(descent, t.getMeasuredHeight() - t.getBaseline());
            }
            height = Math.max(Ui.dp(getContext(), minimumDp), ascent + descent);
            baseline = ascent + (height - ascent - descent) / 2;
        }
    }

    private void place(TextView text, int x, int y, Band band) {
        if (text.getVisibility() == GONE) return;
        int top = y + band.baseline - text.getBaseline();
        text.layout(x, top, x + text.getMeasuredWidth(), top + text.getMeasuredHeight());
    }

    private void panel(boolean future, int x, int y, Band[] bands) {
        TextView title = future ? futureTitle : currentTitle;
        TextView clock = future ? futureClock : currentClock;
        TextView side = future ? futureSide : currentSide;
        place(title, x + (future ? 0 : Ui.dp(getContext(), 30)), y, bands[0]);
        if (!future) {
            Paint.FontMetrics fm = titleMetrics; currentTitle.getPaint().getFontMetrics(fm);
            int top = Math.round(y + bands[0].baseline + (fm.ascent + fm.descent) / 2 - bulb.getMeasuredHeight() / 2f);
            bulb.layout(x, top, x + bulb.getMeasuredWidth(), top + bulb.getMeasuredHeight());
        }
        y += bands[0].height;
        place(clock, x, y, bands[1]);
        place(side, x + clock.getMeasuredWidth() + Ui.dp(getContext(), 6), y, bands[1]);
        y += bands[1].height + Ui.dp(getContext(), 2);
        place(future ? remaining : elapsed, x, y, bands[2]);
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int x = getPaddingLeft();
        label.layout(x, getPaddingTop(), right - left - getPaddingRight(), getPaddingTop() + label.getMeasuredHeight());
        if (horizontal) {
            panel(false, x, bodyTop, shared);
            panel(true, x + half + Ui.dp(getContext(), 9), bodyTop, shared);
            futureBounds.set(x + half, bodyTop, right - left - getPaddingRight(), bodyTop + total(shared));
        } else {
            panel(false, x, bodyTop, leftBands);
            int next = bodyTop + total(leftBands) + Ui.dp(getContext(), 8);
            panel(true, x + Ui.dp(getContext(), 9), next, rightBands);
            futureBounds.set(x, next, right - left - getPaddingRight(), next + total(rightBands));
        }
    }

    @Override protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (highlighted) {
            fill.setColor(pal.fill(initial.next));
            canvas.drawRoundRect(futureBounds, Ui.px(getContext(), 14), Ui.px(getContext(), 14), fill);
        } else if (horizontal) {
            fill.setColor(Ui.fade(pal.text, .16f));
            canvas.drawRect(futureBounds.left, futureBounds.top, futureBounds.left + Ui.px(getContext(), 1), futureBounds.bottom, fill);
        }
    }
}
