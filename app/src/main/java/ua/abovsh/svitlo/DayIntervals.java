package ua.abovsh.svitlo;

import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.time.LocalDate;

/** Read the same half-hour schedule as continuous intervals in a scrollable panel. */
final class DayIntervals {
    private DayIntervals() {}

    static void show(Base page, Logic.Place place, LocalDate date, String day, Runnable closed) {
        LinearLayout content = Ui.column(page);
        BottomSheet.grip(page, content);
        long now = Sync.now();
        LocalDate today = Logic.today(now);
        int current = date.equals(today) ? Logic.index(date, now) : -1;
        String name = "";
        if (current >= 0) name = "Сьогодні";
        else if (date.equals(today.plusDays(1))) name = "Завтра";
        String fullDate = date.getDayOfMonth() + " " + Ui.MONTHS[date.getMonthValue() - 1];

        LinearLayout header = Ui.row(page);
        header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = Ui.text(page, 18, Ui.heavy(page), page.pal.text);
        title.setText(name.isEmpty() ? fullDate : name + ", " + fullDate);
        if (android.os.Build.VERSION.SDK_INT >= 28) title.setAccessibilityHeading(true);
        header.addView(title, Base.weight());
        ImageView close = page.iconButton(R.drawable.ic_close, "Закрити", null);
        header.addView(close);
        content.addView(header, Base.fill());

        TextView where = page.note(place.name + " · " + Base.where(place));
        content.addView(where, page.gap(10));
        LinearLayout intervals = page.box();
        int from = 0;
        while (from < day.length()) {
            int to = Logic.barRun(day, from)[1];
            int state = Logic.state(day.charAt(from));
            if (from > 0) intervals.addView(page.divider());
            intervals.addView(row(page, state, from, to, current >= from && current < to), Base.fill());
            from = to;
        }
        content.addView(intervals, Base.fill());
        android.app.Dialog dialog = BottomSheet.show(page, content, closed);
        dialog.setCanceledOnTouchOutside(true);
        close.setOnClickListener(v -> dialog.dismiss());
    }

    private static View row(Base page, int state, int from, int to, boolean current) {
        FlowRow line = new FlowRow(page, Ui.dp(page, 8), Ui.dp(page, 4));
        line.setPadding(Ui.dp(page, 8), Ui.dp(page, 9), Ui.dp(page, 8), Ui.dp(page, 9));
        if (current) line.setBackground(Ui.round(page.pal.soft(state), Ui.px(page, 10)));
        String time = Logic.clock(from) + "–" + Logic.clock(to);
        String status = state == Logic.UNKNOWN ? "Даних немає" : Logic.title(state);
        TextView range = Ui.text(page, 15, Ui.heavy(page), page.pal.text);
        range.setText(time);
        line.addView(range);
        TextView label = Ui.text(page, 15, Ui.bold(page), page.pal.text);
        label.setText(status);
        line.addView(label);
        if (current) {
            TextView marker = Ui.text(page, 12, Ui.heavy(page), page.pal.muted);
            marker.setText("Зараз");
            line.addView(marker);
        }
        line.setContentDescription(time + ". " + status + (current ? ". Зараз" : ""));
        line.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
        if (android.os.Build.VERSION.SDK_INT >= 28) line.setScreenReaderFocusable(true);
        for (int child = 0; child < line.getChildCount(); child++) {
            line.getChildAt(child).setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        }
        return line;
    }
}
