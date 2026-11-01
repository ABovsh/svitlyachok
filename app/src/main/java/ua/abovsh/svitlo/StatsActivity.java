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

    @Override
    protected void onCreate(Bundle state) { super.onCreate(state); place = Prefs.current(this); }

    @Override
    void render() {
        List<Logic.Place> places = Prefs.places(this);
        if (places.isEmpty()) return;
        Logic.Place p = Logic.widgetPlace(places, place); place = p.id;
        java.util.Map<java.time.LocalDate,String> history = Prefs.history(this, p.id);
        int sig = (place + days + Sync.now() / 3600 + history.toString()).hashCode();
        if (sig == built) return;
        built = sig;
        page.removeAllViews();
        page.addView(topBar("Статистика"), fill());
        if (places.size() > 1) page.addView(choose(places, p), gap(8));
        TextView queue = Ui.text(this, 12, Ui.bold(this), pal.muted);
        queue.setText("Черга " + Logic.queue(p.group)); page.addView(queue, gap(4));
        page.addView(period(), gap(8));
        List<Logic.DayStat> daily = Logic.window(history, Logic.today(Sync.now()), 7);
        List<Logic.WeekStat> weeks = Logic.fourWeeks(history, Logic.today(Sync.now()));
        List<Logic.DayStat> all = new java.util.ArrayList<>();
        for (Logic.WeekStat w : weeks) all.addAll(w.days);
        List<Logic.DayStat> shown = days == 7 ? daily : all;
        LinearLayout card = Ui.column(this);
        card.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 12));
        card.setBackground(Ui.card(this, pal, 0));
        TextView head = Ui.text(this, 18, Ui.heavy(this), pal.text);
        head.setText(days == 7 ? "Години за добу" : "Світло за 4 тижні"); card.addView(head, fill());
        TextView range = Ui.text(this, 13, Ui.body(this), pal.muted);
        range.setText(days == 7 ? "За графіком · " + date(daily.get(0)) + " — " + date(daily.get(6))
                : "У середньому за добу · " + shortDate(weeks.get(0).from) + " — " + shortDate(weeks.get(3).to));
        range.setPadding(0, Ui.dp(this, 6), 0, 0); card.addView(range, fill());
        StatsChart chart = new StatsChart(this, pal);
        if (days == 7) chart.set(daily); else chart.setWeeks(weeks);
        // At very large system fonts, allow room for every week rather than clip its labels.
        android.widget.HorizontalScrollView chartScroll = new android.widget.HorizontalScrollView(this);
        chartScroll.setHorizontalScrollBarEnabled(true); chartScroll.setFillViewport(true); chartScroll.addView(chart);
        LinearLayout.LayoutParams cl = fill(); cl.topMargin = Ui.dp(this, 4); card.addView(chartScroll, cl);
        card.addView(legend(shown), gap(6));
        if (days != 7) card.addView(trend(weeks), gap(6));
        else {
            TextView avg = Ui.text(this, 14, Ui.heavy(this), pal.text); avg.setText("У середньому на день");
            card.addView(avg, gap(6));
        }
        LinearLayout metrics = Ui.row(this); metrics.setBaselineAligned(false);
        LinearLayout.LayoutParams ml = weight(); ml.height = ViewGroup.LayoutParams.MATCH_PARENT;
        LinearLayout.LayoutParams mr = weight(); mr.height = ViewGroup.LayoutParams.MATCH_PARENT; mr.leftMargin = Ui.dp(this, 6);
        metrics.addView(metric(days == 7 ? daily : weeks.get(3).days, Logic.ON, days == 7 ? "Зі світлом" : "Останній тиждень"), ml);
        metrics.addView(metric(shown, days == 7 ? Logic.OFF : Logic.ON, days == 7 ? "Без світла" : "За 4 тижні"), mr);
        card.addView(metrics, fill());
        TextView coverage = Ui.text(this, 12, Ui.body(this), pal.muted);
        String text;
        if (days == 7) text = "Днів із графіком: " + Logic.statKnownDays(daily) + " із 7";
        else {
            List<String> counts = new java.util.ArrayList<>();
            for (Logic.WeekStat w : weeks) counts.add(w.known + "/7");
            text = "Повних днів із графіком: " + String.join(" · ", counts);
        }
        int possible = Logic.statAverageMinutes(shown, Logic.MAYBE);
        if (possible > 0) text += "\nМожливе вимкнення: " + Logic.hoursMinutes(possible) + " на день";
        coverage.setText(text); coverage.setPadding(0, Ui.dp(this, 7), 0, 0); card.addView(coverage, fill());
        page.addView(card, fill());
        TextView note = Ui.text(this, 12, Ui.body(this), pal.muted);
        note.setText("Це графіки обленерго, а не виміряна наявність світла. Дні без графіка не вважаємо нульовими. Історія — на цьому телефоні.");
        note.setLineSpacing(0, 1.1f); note.setPadding(Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4), 0);
        page.addView(note, fill());
    }

    private View trend(List<Logic.WeekStat> weeks) {
        int delta = Logic.weekChange(weeks);
        TextView t = Ui.text(this, 14, Ui.heavy(this), delta == Integer.MIN_VALUE || delta == 0 ? pal.muted : delta > 0 ? pal.onText : pal.offText);
        String text;
        if (delta == Integer.MIN_VALUE) text = "Для тенденції потрібні два повні тижні без невизначених годин.";
        else if (delta == 0) text = "Без змін проти попереднього тижня";
        else text = (delta > 0 ? "↑ На " : "↓ На ") + Logic.hoursMinutes(Math.abs(delta))
                + (delta > 0 ? " більше" : " менше") + ", ніж тиждень тому";
        t.setText(text);
        if (delta != Integer.MIN_VALUE && delta != 0) {
            t.setPadding(Ui.dp(this, 9), Ui.dp(this, 7), Ui.dp(this, 9), Ui.dp(this, 7));
            t.setBackground(Ui.round(pal.soft(delta > 0 ? Logic.ON : Logic.OFF), Ui.px(this, 12)));
            t.setContentDescription(text + ", світла на день за графіком");
        }
        return t;
    }

    private static String shortDate(java.time.LocalDate date) {
        return String.format(Locale.ROOT, "%d.%02d", date.getDayOfMonth(), date.getMonthValue());
    }

    private static String date(Logic.DayStat d) {
        return String.format(Locale.ROOT, "%d.%02d", d.date.getDayOfMonth(), d.date.getMonthValue());
    }

    private View metric(List<Logic.DayStat> days, int state, String name) {
        LinearLayout box = Ui.column(this);
        box.setPadding(Ui.dp(this, 9), Ui.dp(this, 9), Ui.dp(this, 9), Ui.dp(this, 9));
        box.setBackground(Ui.round(pal.soft(state), Ui.px(this, 13)));
        FlowRow labels = new FlowRow(this, Ui.dp(this, 6), Ui.dp(this, 2));
        TextView title = Ui.text(this, 13, Ui.heavy(this), pal.status(state)); title.setText(name); labels.addView(title);
        int share = Logic.statShare(days, state);
        TextView pct = Ui.text(this, 12, Ui.body(this), pal.muted); pct.setText(share < 0 ? "" : share + " %"); labels.addView(pct);
        box.addView(labels, fill());
        int minutes = Logic.statAverageMinutes(days, state);
        TextView value = Ui.text(this, 22, Ui.display(this), pal.status(state));
        if (minutes < 0) value.setText("—");
        else {
            String hours = String.valueOf(minutes / 60);
            String text = hours + " год" + (minutes % 60 == 0 ? "" : String.format(Locale.ROOT, " %02d хв", minutes % 60));
            android.text.SpannableStringBuilder span = new android.text.SpannableStringBuilder(text);
            span.setSpan(new android.text.style.RelativeSizeSpan(.6f), hours.length(), text.length(), 0);
            span.setSpan(new Ui.Face(Ui.bold(this)), hours.length(), text.length(), 0);
            value.setText(span);
        }
        value.setPadding(0, Ui.dp(this, 3), 0, 0); box.addView(value, fill());
        box.setContentDescription(name + ": " + (minutes < 0 ? "немає даних" : Logic.hoursMinutes(minutes))
                + (share < 0 ? "" : ", " + share + " відсотків відомого часу"));
        return box;
    }

    private View choose(List<Logic.Place> places, Logic.Place chosen) {
        FlowRow row = new FlowRow(this, Ui.dp(this, 6), Ui.dp(this, 6));
        for (Logic.Place p : places) {
            TextView b = Ui.pill(this, pal, p.name, p.id.equals(chosen.id), 14);
            b.setOnClickListener(v -> { place = p.id; render(); }); row.addView(b);
        }
        return row;
    }

    private View period() {
        LinearLayout row = Ui.row(this); row.setBaselineAligned(false);
        for (int n : new int[] {7, 28}) {
            TextView b = Ui.pill(this, pal, n == 7 ? "Тиждень" : "4 тижні", n == days, 14);
            b.setOnClickListener(v -> { days = n; render(); });
            LinearLayout.LayoutParams lp = weight(); if (n == 28) lp.leftMargin = Ui.dp(this, 6); row.addView(b, lp);
        }
        return row;
    }

    private View legend(List<Logic.DayStat> days) {
        FlowRow row = new FlowRow(this, Ui.dp(this, 10), Ui.dp(this, 5));
        legendItem(row, '1', "Зі світлом · год");
        if (this.days == 7) legendItem(row, '0', "Без світла · год");
        TextView average = Ui.text(this, 12, Ui.bold(this), pal.accentText);
        average.setText("┄ Середнє зі світлом"); row.addView(average);
        boolean possible = false; boolean unknown = false;
        for (Logic.DayStat d : days) { possible |= d.maybe > 0; unknown |= !Logic.statKnown(d) || d.on + d.off + d.maybe < 48; }
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
