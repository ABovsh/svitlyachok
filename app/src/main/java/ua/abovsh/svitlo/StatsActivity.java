package ua.abovsh.svitlo;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;
import java.util.Locale;

/** Daily detail for a week, positive daily averages for four completed weeks. */
public class StatsActivity extends Base {
    private int days = 7;
    private String place = "";
    private int built;
    private static final String FOUR_WEEKS_LABEL = "4 тижні";
    private static final int WEEK_DP = 80;
    private static final int MONTH_DP = 48;
    private static final int CHANGE_DP = 88;

    @Override
    protected void onCreate(Bundle state) { super.onCreate(state); place = Prefs.current(this); }

    @Override
    void render() {
        List<Logic.Place> places = Prefs.places(this);
        if (places.isEmpty()) return;
        Logic.Place p = Logic.widgetPlace(places, place); place = p.id;
        StringBuilder all = new StringBuilder(place).append(days).append(Sync.now() / 3600);
        for (Logic.Place each : places) all.append(Prefs.history(this, each.id));
        int sig = all.toString().hashCode();
        if (sig == built) return;
        built = sig;
        page.removeAllViews();
        page.addView(topBar("Статистика"), fill());
        page.addView(table(places), gap(8));
        page.addView(card(p, Prefs.history(this, p.id)), fill());
        TextView note = Ui.text(this, 13, Ui.body(this), pal.muted);
        note.setText("Це графіки обленерго, а не виміряна наявність світла. Історія — на цьому телефоні.");
        note.setLineSpacing(0, 1.1f); note.setPadding(Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4), 0);
        page.addView(note, fill());
    }

    private boolean tableStacked() {
        float nameRoom = getResources().getConfiguration().screenWidthDp - 32f - 8 - 18 - WEEK_DP - MONTH_DP - CHANGE_DP;
        android.graphics.Paint value = new android.graphics.Paint();
        value.setTypeface(Ui.bold(this)); value.setTextSize(Ui.compactSp(this, 13, 1));
        return nameRoom < 80 || value.measureText("100 %") > Ui.px(this, MONTH_DP);
    }

    /** All places at once in percent: this week, four weeks, the change of the last week. */
    private View table(List<Logic.Place> places) {
        LinearLayout box = Ui.column(this);
        box.setPadding(Ui.dp(this, 4), Ui.dp(this, 7), Ui.dp(this, 4), Ui.dp(this, 5));
        box.setBackground(Ui.card(this, pal, 0));
        if (!tableStacked()) box.addView(columns(), fill());
        for (Logic.Place p : places) box.addView(row(p), fill());
        return box;
    }

    private LinearLayout.LayoutParams cell(int dp) {
        return new LinearLayout.LayoutParams(Ui.dp(this, dp), ViewGroup.LayoutParams.WRAP_CONTENT);
    }

    private View columns() {
        LinearLayout r = Ui.row(this);
        r.setPadding(Ui.dp(this, 9), 0, Ui.dp(this, 9), Ui.dp(this, 3));
        r.setGravity(android.view.Gravity.BOTTOM);
        TextView title = head("Світла за графіком", android.view.Gravity.START);
        title.setSingleLine(false);  // beside three fixed columns on a narrow screen it takes two lines
        r.addView(title, weight());
        r.addView(head("тиждень", android.view.Gravity.START), cell(WEEK_DP));
        r.addView(head(FOUR_WEEKS_LABEL, android.view.Gravity.START), cell(MONTH_DP));
        r.addView(head("зміна", android.view.Gravity.END), cell(CHANGE_DP));
        return r;
    }

    private TextView head(String words, int gravity) {
        FitText t = new FitText(this, 12, 11, 1, Ui.bold(this), pal.muted).compact(1.1f);
        t.setText(words); t.setSingleLine(true); t.setGravity(gravity);
        return t;
    }

    private View row(Logic.Place p) {
        java.util.Map<java.time.LocalDate,String> history = Prefs.history(this, p.id);
        java.time.LocalDate today = Logic.today(Sync.now());
        List<Logic.WeekStat> weeks = Logic.fourWeeks(history, today);
        List<Logic.DayStat> four = new java.util.ArrayList<>();
        for (Logic.WeekStat w : weeks) four.addAll(w.days);
        int week = Logic.statShare(Logic.window(history, today, 7), Logic.ON);
        int month = Logic.statShare(four, Logic.ON);
        int change = Logic.weekChange(weeks);
        boolean chosen = p.id.equals(place);
        View name = rowName(p);
        View weekBox = weekCell(week);
        FitText monthText = new FitText(this, 15, 13, 1, Ui.bold(this), pal.muted).compact(1.1f);
        monthText.setText(percent(month));
        FitText changeText = new FitText(this, 13, 12, 2, Ui.heavy(this), changeInk(change)).compact(1.1f);
        changeText.setText(Logic.changeWords(change));
        changeText.setGravity(android.view.Gravity.END);
        LinearLayout r = tableStacked() ? Ui.column(this) : Ui.row(this);
        r.setPadding(Ui.dp(this, 9), Ui.dp(this, 7), Ui.dp(this, 9), Ui.dp(this, 7));
        if (chosen) r.setBackground(Ui.outline(pal.accentText, Ui.px(this, 12), Ui.dp(this, 2)));
        if (tableStacked()) {
            r.addView(name, fill());
            LinearLayout values = Ui.row(this);
            values.addView(metric("тиждень", weekBox), weight());
            values.addView(metric(FOUR_WEEKS_LABEL, monthText), weight());
            values.addView(metric("зміна", changeText), weight());
            r.addView(values, gapTop(4));
        } else {
            r.addView(name, weight());
            r.addView(weekBox, cell(WEEK_DP));
            r.addView(monthText, cell(MONTH_DP));
            r.addView(changeText, cell(CHANGE_DP));
        }
        r.setContentDescription(p.name + ", черга " + Logic.queue(p.group) + ": за тиждень " + spoken(week)
                + ", за 4 тижні " + spoken(month) + ", зміна " + Logic.changeWords(change) + (chosen ? ", вибрано" : ", показати графік"));
        r.setOnClickListener(v -> { place = p.id; render(); });
        return r;
    }

    private View metric(String title, View value) {
        LinearLayout column = Ui.column(this);
        column.addView(head(title, android.view.Gravity.START), fill());
        column.addView(value, fill());
        return column;
    }

    private static String percent(int share) {
        return share < 0 ? "—" : share + " %";
    }

    private static String spoken(int share) {
        return share < 0 ? "немає даних" : share + " відсотків";
    }

    private int changeInk(int change) {
        if (change == Integer.MIN_VALUE || change == 0) return pal.muted;
        return change > 0 ? pal.onText : pal.offText;
    }

    /** A long name wraps: it is never cut. */
    private View rowName(Logic.Place p) {
        FlowRow name = new FlowRow(this, Ui.dp(this, 5), Ui.dp(this, 2));
        FitText title = new FitText(this, 15, 13, 2, Ui.heavy(this), pal.text).compact(1.1f);
        title.setText(p.name);
        name.addView(title);
        if (!Logic.autoNamed(p)) {
            TextView q = Ui.text(this, 12.5f, Ui.body(this), pal.muted);
            q.setText(Logic.queue(p.group));
            name.addView(q);
        }
        return name;
    }

    private View weekCell(int week) {
        LinearLayout box = Ui.column(this);
        box.setPadding(0, 0, Ui.dp(this, 12), 0);
        FitText text = new FitText(this, 16, 14, 1, Ui.heavy(this), pal.onText).compact(1.1f);
        text.setText(percent(week));
        box.addView(text, fill());
        if (week < 0) return box;
        LinearLayout meter = Ui.row(this);
        meter.setClipToOutline(true);
        meter.setBackground(Ui.round(pal.soft(Logic.OFF), Ui.px(this, 3)));
        View filled = new View(this);
        filled.setBackgroundColor(pal.on);
        meter.addView(filled, new LinearLayout.LayoutParams(0, Ui.dp(this, 5), (float) Math.max(week, 1)));
        meter.addView(new View(this), new LinearLayout.LayoutParams(0, Ui.dp(this, 5), (float) Math.max(100 - week, 0)));
        LinearLayout.LayoutParams ml = fill(); ml.topMargin = Ui.dp(this, 2);
        box.addView(meter, ml);
        return box;
    }

    private View card(Logic.Place p, java.util.Map<java.time.LocalDate,String> history) {
        List<Logic.DayStat> daily = Logic.window(history, Logic.today(Sync.now()), 7);
        List<Logic.WeekStat> weeks = Logic.fourWeeks(history, Logic.today(Sync.now()));
        List<Logic.DayStat> all = new java.util.ArrayList<>();
        for (Logic.WeekStat w : weeks) all.addAll(w.days);
        List<Logic.DayStat> shown = days == 7 ? daily : all;
        LinearLayout card = Ui.column(this);
        card.setPadding(Ui.dp(this, 12), Ui.dp(this, 9), Ui.dp(this, 12), Ui.dp(this, 10));
        card.setBackground(Ui.card(this, pal, 0));
        LinearLayout top = Ui.row(this); top.setBaselineAligned(false);
        LinearLayout words = Ui.column(this);
        FitText head = new FitText(this, 16, 14, 2, Ui.heavy(this), pal.text).compact(1.1f);
        head.setText(p.name); words.addView(head, fill());
        FitText range = new FitText(this, 12.5f, 12, 2, Ui.body(this), pal.muted);
        range.setText(days == 7 ? date(daily.get(0)) + " — " + date(daily.get(6)) + " · годин на добу"
                : shortDate(weeks.get(0).from) + " — " + shortDate(weeks.get(3).to) + " · у середньому за добу");
        CharSequence fullRange = range.getText();
        range.formats(fullRange, fullRange.toString().replace(" — ", "–").replace(" · годин на добу", " · год/добу").replace(" · у середньому за добу", " · год/добу"));
        range.setContentDescription(fullRange);
        words.addView(range, fill());
        top.addView(words, weight());
        top.addView(period(), wrap());
        card.addView(top, gap(7));
        StatsChart chart = new StatsChart(this, pal);
        if (days == 7) chart.set(daily); else chart.setWeeks(weeks);
        // At very large system fonts, allow room for every week rather than clip its labels.
        android.widget.HorizontalScrollView chartScroll = new android.widget.HorizontalScrollView(this);
        chartScroll.setHorizontalScrollBarEnabled(true); chartScroll.setFillViewport(true); chartScroll.addView(chart);
        card.addView(chartScroll, fill());
        int share = Logic.statShare(shown, Logic.ON);
        int minutes = Logic.statAverageMinutes(shown, Logic.ON);
        if (share >= 0) {
            TextView average = Ui.text(this, 12.5f, Ui.bold(this), pal.accentText);
            average.setText("┄ у середньому " + Logic.barHours(Math.round(minutes / 30f)) + " год · " + share + " % зі світлом");
            card.addView(average, gapTop(5));
        }
        View rare = legend(shown, days != 7 && Logic.weeksMissData(weeks));
        if (rare != null) card.addView(rare, gapTop(5));
        String note = coverage(daily, weeks, shown);
        if (!note.isEmpty()) {
            TextView coverage = Ui.text(this, 12.5f, Ui.body(this), pal.muted);
            coverage.setText(note);
            card.addView(coverage, gapTop(5));
        }
        return card;
    }

    /** Said only when something is missing: seven full days of seven need no line. */
    private String coverage(List<Logic.DayStat> daily, List<Logic.WeekStat> weeks, List<Logic.DayStat> shown) {
        String text = "";
        if (days == 7) {
            int known = Logic.statKnownDays(daily);
            if (known < 7) text = "Днів із графіком на всі 24 години: " + known + " із 7. Решта не враховані.";
        } else {
            List<String> counts = new java.util.ArrayList<>();
            boolean missing = false;
            for (Logic.WeekStat w : weeks) { counts.add(w.known + "/7"); missing |= w.known < 7; }
            if (missing) text = "Днів із графіком на всі 24 години: " + String.join(" · ", counts) + ". Решта не враховані.";
        }
        int possible = Logic.statAverageMinutes(shown, Logic.MAYBE);
        if (possible <= 0) return text;
        return (text.isEmpty() ? "" : text + "\n") + "Можливе вимкнення: " + Logic.hoursMinutes(possible) + " на день";
    }

    private static String shortDate(java.time.LocalDate date) {
        return String.format(Locale.ROOT, "%d.%02d", date.getDayOfMonth(), date.getMonthValue());
    }

    private static String date(Logic.DayStat d) {
        return String.format(Locale.ROOT, "%d.%02d", d.date.getDayOfMonth(), d.date.getMonthValue());
    }

    private View period() {
        LinearLayout row = Ui.row(this); row.setBaselineAligned(false);
        for (int n : new int[] {7, 28}) {
            TextView b = Ui.pill(this, pal, n == 7 ? "Тиждень" : FOUR_WEEKS_LABEL, n == days, 13);
            b.setMinHeight(Ui.dp(this, 36));
            b.setPadding(Ui.dp(this, 11), Ui.dp(this, 4), Ui.dp(this, 11), Ui.dp(this, 4));
            b.setContentDescription(n == 7 ? "Графік за тиждень" : "Графік за 4 тижні");
            b.setOnClickListener(v -> { days = n; render(); });
            LinearLayout.LayoutParams lp = wrap();
            if (n == 28) lp.leftMargin = Ui.dp(this, 5);
            row.addView(b, lp);
        }
        return row;
    }

    /** Only what the colours alone do not say: a possible outage, a day without data. */
    private View legend(List<Logic.DayStat> days, boolean missing) {
        boolean possible = false;
        boolean unknown = missing;
        for (Logic.DayStat d : days) { possible |= d.maybe > 0; unknown |= !Logic.statKnown(d) || d.on + d.off + d.maybe < 48; }
        if (!possible && !unknown) return null;
        FlowRow row = new FlowRow(this, Ui.dp(this, 10), Ui.dp(this, 5));
        if (possible) legendItem(row, '3', "Можливе вимкнення");
        if (unknown) legendItem(row, '2', "Немає даних");
        return row;
    }

    private void legendItem(FlowRow parent, char state, String name) {
        LinearLayout row = Ui.row(this);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(Ui.dp(this, 13), Ui.dp(this, 9)); lp.rightMargin = Ui.dp(this, 5);
        row.addView(Ui.swatch(this, pal, state), lp);
        TextView t = Ui.text(this, 13, Ui.bold(this), pal.muted); t.setText(name); row.addView(t);
        parent.addView(row);
    }
}
