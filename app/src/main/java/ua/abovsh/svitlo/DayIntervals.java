package ua.abovsh.svitlo;

import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.time.LocalDate;

/**
 * Read the same half-hour schedule as continuous intervals in a scrollable panel. Each row says its
 * state three ways (a coloured mark, the bulb, the coloured word); outages lie on their tint, what is
 * over is faded, the interval going on has the yellow frame of the hour of now. An open panel moves its
 * mark at the end of an interval and at midnight, and counts down the minutes left.
 */
final class DayIntervals {
    private DayIntervals() {}

    static void show(Base page, Logic.Place place, LocalDate date, String day, Runnable closed) {
        LinearLayout content = Ui.column(page);
        BottomSheet.grip(page, content);

        LinearLayout header = Ui.row(page);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.text(page, 18, Ui.heavy(page), page.pal.text);
        if (android.os.Build.VERSION.SDK_INT >= 28) title.setAccessibilityHeading(true);
        header.addView(title, Base.weight());
        ImageView close = page.iconButton(R.drawable.ic_close, "Закрити", null);
        header.addView(close);
        content.addView(header, Base.fill());

        TextView where = page.note(place.name + " · " + Base.where(place));
        where.setPadding(where.getPaddingLeft(), Ui.dp(page, 4), where.getPaddingRight(), 0);
        content.addView(where, Base.fill());
        TextView summary = Ui.text(page, 13.5f, Ui.heavy(page), page.pal.onText);
        summary.setText(Logic.intervalsSummary(day));
        summary.setPadding(Ui.dp(page, 6), Ui.dp(page, 2), Ui.dp(page, 6), 0);
        content.addView(summary, page.gap(10));
        LinearLayout intervals = Ui.column(page);
        content.addView(intervals, Base.fill());

        Panel panel = new Panel(page, date, day, title, intervals);
        panel.render();
        android.app.Dialog dialog = BottomSheet.show(page, content, () -> { panel.stop(); closed.run(); });
        dialog.setCanceledOnTouchOutside(true);
        close.setOnClickListener(v -> dialog.dismiss());
        panel.tick();
        panel.reveal();
    }

    /** The rows of one open panel and the clock that keeps its mark of now true. */
    private static final class Panel {
        private final Base page;
        private final LocalDate date;
        private final String day;
        private final TextView title;
        private final LinearLayout intervals;
        private final Handler clock = new Handler(Looper.getMainLooper());
        private final Runnable next = this::tick;
        private long change = -1;
        private int end = -1;
        private TextView left;
        private View runningRow;
        private boolean stopped;

        Panel(Base page, LocalDate date, String day, TextView title, LinearLayout intervals) {
            this.page = page; this.date = date; this.day = day; this.title = title; this.intervals = intervals;
        }

        void render() {
            long now = Sync.now();
            LocalDate today = Logic.today(now);
            int current = date.equals(today) ? Logic.index(date, now) : -1;
            String name = "";
            if (current >= 0) name = "Сьогодні";
            else if (date.equals(today.plusDays(1))) name = "Завтра";
            String fullDate = date.getDayOfMonth() + " " + Ui.MONTHS[date.getMonthValue() - 1];
            title.setText(name.isEmpty() ? fullDate : name + ", " + fullDate);
            intervals.removeAllViews();
            left = null; end = -1; runningRow = null;
            int from = 0;
            while (from < day.length()) {
                int to = Logic.barRun(day, from)[1];
                boolean running = current >= from && current < to;
                if (running) end = to;
                LinearLayout.LayoutParams lp = Base.fill();
                if (from > 0) lp.topMargin = Ui.dp(page, 5);
                intervals.addView(row(Logic.state(day.charAt(from)), from, to, running, current >= to, now), lp);
                from = to;
            }
            change = Logic.nextIntervalChange(day, date, now);
        }

        /** Every minute: the minutes left; at a change or midnight: the rows anew. */
        void tick() {
            if (stopped) return;
            long now = Sync.now();
            if (change > 0 && now >= change) render();
            if (left != null && end > 0) {
                String words = Logic.intervalLeft(Logic.slotStart(date, end), now);
                left.setText("Зараз · " + words);
                runningRow.setContentDescription(describe(runningRow) + words);
            }
            clock.postDelayed(next, Logic.toNextMinute(System.currentTimeMillis()) + 200);
        }

        /** A long day opens where it is now: the interval going on in sight, with the one before it. */
        void reveal() {
            if (runningRow == null || !(intervals.getParent() instanceof View column)
                    || !(column.getParent() instanceof android.widget.ScrollView scroll)) return;
            scroll.post(() -> scroll.scrollTo(0, Math.max(0, intervals.getTop() + runningRow.getTop() - Ui.dp(page, 64))));
        }

        void stop() {
            stopped = true;
            clock.removeCallbacks(next);
        }

        private static String describe(View row) {
            Object tag = row.getTag();
            return tag instanceof String spoken ? spoken : "";
        }

        private View row(int state, int from, int to, boolean running, boolean past, long now) {
            Ui.Pal pal = page.pal;
            LinearLayout line = Ui.row(page);
            line.setGravity(Gravity.CENTER_VERTICAL);
            line.setMinimumHeight(Ui.dp(page, 46));
            line.setPadding(Ui.dp(page, 6), Ui.dp(page, 7), Ui.dp(page, 10), Ui.dp(page, 7));
            boolean light = state == Logic.ON;
            GradientDrawable bg = Ui.round(light ? pal.card : pal.soft(state), Ui.px(page, 12));
            if (running) bg.setStroke(Ui.dp(page, 2) + Ui.dp(page, 1) / 2, Ui.Pal.MARKER);
            else if (light) bg.setStroke(Ui.dp(page, 1), pal.line);
            line.setBackground(bg);
            if (past) line.setAlpha(.5f);

            View rail = new View(page);
            rail.setBackground(Ui.round(pal.fill(state), Ui.px(page, 2)));
            LinearLayout.LayoutParams rp = new LinearLayout.LayoutParams(Ui.dp(page, 4), Ui.dp(page, 28));
            rp.rightMargin = Ui.dp(page, 8);
            line.addView(rail, rp);
            BulbView bulb = new BulbView(page, pal);
            bulb.bare(); bulb.still(); bulb.set(state);
            LinearLayout.LayoutParams bp = new LinearLayout.LayoutParams(Ui.dp(page, 22), Ui.dp(page, 22));
            bp.rightMargin = Ui.dp(page, 8);
            line.addView(bulb, bp);

            String time = Logic.clock(from) + "–" + Logic.clock(to);
            String status = state == Logic.UNKNOWN ? "Даних немає" : Logic.title(state);
            FlowRow words = new FlowRow(page, Ui.dp(page, 8), Ui.dp(page, 2));
            TextView range = Ui.text(page, 15.5f, Ui.heavy(page), pal.text);
            range.setText(time);
            range.setSingleLine(true);  // a time never breaks at its dash
            words.addView(range);
            TextView label = Ui.text(page, 14, Ui.heavy(page), pal.status(state));
            label.setText(status);
            words.addView(label);
            line.addView(words, Base.weight());

            String spoken = time + ". " + status + ". ";
            if (running) {  // among the words: with large text it goes to a line of its own, the time stays whole
                TextView mark = Ui.text(page, 12, Ui.heavy(page), pal.accentInk);
                mark.setText("Зараз · " + Logic.intervalLeft(Logic.slotStart(date, to), now));
                mark.setBackground(Ui.round(Ui.Pal.MARKER, Ui.px(page, 7)));
                mark.setPadding(Ui.dp(page, 7), Ui.dp(page, 2), Ui.dp(page, 7), Ui.dp(page, 2));
                words.addView(mark);
                spoken += "Зараз, ";
                left = mark;
                runningRow = line;
            } else {
                TextView aside = Ui.text(page, 13, Ui.bold(page), pal.muted);
                aside.setText(Logic.length(to - from));
                spoken += Logic.length(to - from) + (past ? ". Минуло" : "");
                LinearLayout.LayoutParams ap = Base.wrap();
                ap.leftMargin = Ui.dp(page, 6);
                line.addView(aside, ap);
            }

            line.setTag(running ? spoken : null);
            line.setContentDescription(running ? spoken + Logic.intervalLeft(Logic.slotStart(date, to), now) : spoken);
            line.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
            if (android.os.Build.VERSION.SDK_INT >= 28) line.setScreenReaderFocusable(true);
            for (int child = 0; child < line.getChildCount(); child++) {
                line.getChildAt(child).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
            }
            return line;
        }
    }
}
