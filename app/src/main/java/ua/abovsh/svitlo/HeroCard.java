package ua.abovsh.svitlo;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.util.TypedValue;
import android.view.ViewGroup;
import android.widget.TextView;

/**
 * Two independent information columns, measured as shared typographic rows. Every row stands one
 * gap from the next, from a baseline to the cap top of the row under it, and the same gap lies above
 * the first row and under the last: the card reads evenly whatever the fonts.
 */
final class HeroCard extends ViewGroup {
    private final Ui.Pal pal;
    private final String location;
    private final Logic.Now initial;
    private final boolean highlighted;
    private final BulbView bulb;
    private final TextView label;
    private final TextView currentTitle;
    private final TextView futureTitle;
    private final TextView currentClock;
    private final TextView futureClock;
    private final TextView currentSide;
    private final TextView elapsed;
    private final TextView remaining;
    private final Paint fill = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint measuring = new Paint();
    private final Paint.FontMetrics titleMetrics = new Paint.FontMetrics();
    private final android.graphics.Rect glyph = new android.graphics.Rect();
    private final RectF futureBounds = new RectF();
    private boolean horizontal;
    private int leftRoom;
    private int rightRoom;
    private int half;
    private int gap;
    private int[] baselines = new int[0];
    private final TextView[][] wideRows;
    private final TextView[][] narrowRows;
    private final int[][] wide = new int[3][4];    // cap heights, extra lines, room before: per row
    private final int[][] narrow = new int[3][7];
    private final String currentEnd;
    private boolean currentMoved;
    private Logic.Now last;

    HeroCard(Context c, Ui.Pal colours, String name, Logic.Now now, String[] next, boolean lit) {
        super(c); pal = colours; location = name; initial = now; highlighted = lit;
        setPadding(Ui.dp(c, 11), 0, Ui.dp(c, 11), 0);  // the vertical room is the gap of the rows
        setWillNotDraw(false); setFocusable(true);
        if (Build.VERSION.SDK_INT >= 28) setScreenReaderFocusable(true);
        String[] start = Logic.widgetStart(now);
        currentEnd = start[1]; last = now;
        int nextInk = now.next == Logic.UNKNOWN ? pal.muted : pal.status(now.next);
        if (lit) nextInk = now.next == Logic.MAYBE ? pal.accentInk : pal.ink;
        FitText place = new FitText(c, 13, 12, 1, Ui.heavy(c), pal.text).compact(1.1f);
        place.setText(name); place.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); addView(place); label = place;
        currentTitle = text(now.title, 20, Ui.heavy(c), pal.status(now.state));
        futureTitle = text(next[0], 20, Ui.heavy(c), nextInk);
        currentClock = text(start[0], 27, start[0].contains(":") ? Ui.display(c) : Ui.bold(c), pal.text);
        futureClock = text(next[1], 27, next[1].contains(":") ? Ui.display(c) : Ui.bold(c), nextInk);
        currentSide = text(start[1], 13, Ui.bold(c), pal.muted);
        elapsed = text(now.already, 13.5f, Ui.body(c), pal.muted);
        remaining = text(Logic.heroCountdown(now), 13.5f, Ui.bold(c), lit ? nextInk : pal.text);
        bulb = new BulbView(c, pal); bulb.bare(); bulb.still(); bulb.set(now.state);
        bulb.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); addView(bulb);
        int right = Logic.widgetRightState(now, next[1]);
        int[] glow = new int[17];
        for (int i = 0; i < glow.length; i++) glow[i] = Logic.widgetGlow(pal.soft(now.state), pal.soft(right), pal.card, .5f, i / 16f);
        GradientDrawable bg = new GradientDrawable(GradientDrawable.Orientation.LEFT_RIGHT, glow);
        bg.setCornerRadius(Ui.px(c, 18)); setBackground(bg);
        wideRows = new TextView[][] {{label}, {currentTitle, futureTitle}, {currentClock, currentSide, futureClock}, {elapsed, remaining}};
        narrowRows = new TextView[][] {{label}, {currentTitle}, {currentClock, currentSide}, {elapsed}, {futureTitle}, {futureClock}, {remaining}};
        update(now);
    }

    private TextView text(String value, float sp, android.graphics.Typeface face, int colour) {
        TextView t = Ui.text(getContext(), sp, face, colour); t.setText(value);
        t.setHyphenationFrequency(android.text.Layout.HYPHENATION_FREQUENCY_NONE);
        t.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO); addView(t); return t;
    }

    void update(Logic.Now now) {
        last = now;
        details();
        setContentDescription(location + ". " + Logic.heroDescription(now));
    }

    /** The duration rows; the day of a start that has no room beside its clock goes under the duration. */
    private void details() {
        String left = under(last.already, currentMoved ? currentEnd : "");
        String right = Logic.heroCountdown(last);
        if (!android.text.TextUtils.equals(elapsed.getText(), left)) elapsed.setText(left);
        if (!android.text.TextUtils.equals(remaining.getText(), right)) remaining.setText(right);
    }

    private static String under(String first, String second) {
        if (android.text.TextUtils.isEmpty(second)) return first;
        return android.text.TextUtils.isEmpty(first) ? second : first + "\n" + second;
    }

    private void size(TextView t, float pixels) { t.setTextSize(TypedValue.COMPLEX_UNIT_PX, pixels); }

    private float width(TextView t, float pixels) {
        Paint p = measuring; p.set(t.getPaint()); p.setTextSize(pixels);
        float widest = 0;
        for (String line : t.getText().toString().split("\n")) widest = Math.max(widest, p.measureText(line));
        return widest;
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
        boolean visible = !android.text.TextUtils.isEmpty(side.getText()) && Math.ceil(width(clock, pixels)) + 1 + extra <= room;
        side.setVisibility(visible ? VISIBLE : GONE);
    }

    private void measureText(TextView t, int room, boolean intrinsic) {
        if (android.text.TextUtils.isEmpty(t.getText()) || t.getVisibility() == GONE) {
            t.setVisibility(GONE); return;
        }
        int w = intrinsic ? Math.min(room, (int) Math.ceil(width(t, t.getTextSize())) + 1) : room;
        t.measure(MeasureSpec.makeMeasureSpec(Math.max(1, w), MeasureSpec.EXACTLY), MeasureSpec.UNSPECIFIED);
    }

    private float fitPair(TextView left, TextView right, float preferred, float minimum, int lw, int rw) {
        float low = minimum;
        float high = preferred;
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
        gap = Math.round(Ui.compactSp(c, 14, 1.1f));
        TextView[][] rows = horizontal ? wideRows : narrowRows;
        int[][] metrics = horizontal ? wide : narrow;
        int[] caps = metrics[0];
        int[] extra = metrics[1];
        int[] before = metrics[2];
        for (int i = 0; i < rows.length; i++) {
            caps[i] = 0; extra[i] = 0;
            for (TextView t : rows[i]) if (t.getVisibility() != GONE) {
                caps[i] = Math.max(caps[i], cap(t)); extra[i] = Math.max(extra[i], lastLine(t));
            }
        }
        if (!horizontal) before[4] = Ui.dp(c, 8);  // the second panel of a narrow card stands apart
        baselines = Logic.rhythm(gap, caps, extra, before);
        setMeasuredDimension(resolveSize(inner + getPaddingLeft() + getPaddingRight(), width), resolveSize(baselines[rows.length], height));
    }

    /** The height of a capital in this text's font: what the eye takes for the top of a row. */
    private int cap(TextView t) {
        t.getPaint().getTextBounds("Н", 0, 1, glyph);
        return -glyph.top;
    }

    /** How far the last line of a wrapped text lies under its first. */
    private static int lastLine(TextView t) {
        android.text.Layout layout = t.getLayout();
        return layout == null || layout.getLineCount() < 2 ? 0 : layout.getLineBaseline(layout.getLineCount() - 1) - layout.getLineBaseline(0);
    }

    private void configureClocks() {
        Context c = getContext();
        size(currentSide, Ui.sp(c, 13));
        float minimumClock = Ui.compactSp(c, 17, 1.1f);
        float preferredClock = Ui.compactSp(c, 27, 1.1f);
        // The day of a start shows beside its clock while the clock may shrink to 80 %, else under the duration.
        optionalSide(currentClock, currentSide, preferredClock * .8f, leftRoom);
        currentMoved = !android.text.TextUtils.isEmpty(currentEnd) && currentSide.getVisibility() == GONE;
        details();
        float low = minimumClock;
        float high = preferredClock;
        for (int i = 0; i < 10; i++) {
            float mid = (low + high) / 2;
            if (clockFits(currentClock, currentSide, mid, leftRoom) && primaryFits(futureClock, mid, rightRoom)) low = mid;
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
        String title = Logic.fittedState(initial.title, titlePaint.measureText(initial.title), (float) titleRoom);
        if (!android.text.TextUtils.equals(currentTitle.getText(), title)) currentTitle.setText(title);
        float titleSize = fitPair(currentTitle, futureTitle, titleMax, Ui.compactSp(c, 14, 1), titleRoom, rightRoom);
        size(currentTitle, titleSize); size(futureTitle, titleSize);
        float detail = fitPair(elapsed, remaining, Ui.sp(c, 13.5f), Ui.compactSp(c, 12.5f, 1), leftRoom, rightRoom);
        size(elapsed, detail); size(remaining, detail);
    }

    private void measureWords(int inner) {
        int titleRoom = Math.max(1, leftRoom - Ui.dp(getContext(), 30));
        measureText(label, inner, false);
        measureText(currentTitle, titleRoom, false); measureText(futureTitle, rightRoom, false);
        measureText(currentSide, leftRoom, true);
        measureText(currentClock, Math.max(1, leftRoom - (int) Math.ceil(sideWidth(currentSide))), currentClock.getText().toString().contains(":"));
        measureText(futureClock, rightRoom, futureClock.getText().toString().contains(":"));
        measureText(elapsed, leftRoom, false); measureText(remaining, rightRoom, false);
    }

    /** A text whose first baseline is at `baseline`. */
    private void place(TextView text, int x, int baseline) {
        if (text.getVisibility() == GONE) return;
        int top = baseline - text.getBaseline();
        text.layout(x, top, x + text.getMeasuredWidth(), top + text.getMeasuredHeight());
    }

    /** One panel from its row of titles: `row` is the index of that row in the baselines. */
    private void panel(boolean future, int x, int row) {
        TextView title = future ? futureTitle : currentTitle;
        TextView clock = future ? futureClock : currentClock;
        place(title, x + (future ? 0 : Ui.dp(getContext(), 30)), baselines[row]);
        if (!future) {
            Paint.FontMetrics fm = titleMetrics; currentTitle.getPaint().getFontMetrics(fm);
            int top = Math.round(baselines[row] + (fm.ascent + fm.descent) / 2 - bulb.getMeasuredHeight() / 2f);
            bulb.layout(x, top, x + bulb.getMeasuredWidth(), top + bulb.getMeasuredHeight());
        }
        place(clock, x, baselines[row + 1]);
        if (!future) place(currentSide, x + clock.getMeasuredWidth() + Ui.dp(getContext(), 6), baselines[row + 1]);
        place(future ? remaining : elapsed, x, baselines[row + 2]);
    }

    @Override protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int x = getPaddingLeft();
        place(label, x, baselines[0]);
        float edge = (float) right - left - getPaddingRight();
        int height = baselines[baselines.length - 1];
        if (horizontal) {
            panel(false, x, 1);
            panel(true, x + half + Ui.dp(getContext(), 9), 1);
            futureBounds.set((float) x + half, baselines[0] + gap / 2f, edge, height - gap / 2f);
        } else {
            panel(false, x, 1);
            panel(true, x + Ui.dp(getContext(), 9), 4);
            float apart = gap + Ui.dp(getContext(), 8);  // the room between the panels, as measured
            futureBounds.set((float) x, baselines[4] - cap(futureTitle) - apart / 2f, edge, height - gap / 2f);
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
