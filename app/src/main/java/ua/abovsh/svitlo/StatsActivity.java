package ua.abovsh.svitlo;

import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;
import java.util.Locale;

/** One chart per period and two precise daily averages; no month paging or second chart. */
public class StatsActivity extends Base {
    private int days = 7;
    private String place = "";
    private int built;

    @Override
    protected void onCreate(Bundle state) { super.onCreate(state); place = Prefs.current(this); }

    @Override
    void render() {
        int sig = (place + days + Sync.now() / 3600).hashCode();
        if (sig == built) return;
        built = sig;
        page.removeAllViews();
        page.addView(topBar("Статистика"), fill());
        List<Logic.Place> places = Prefs.places(this);
        if (places.isEmpty()) return;
        Logic.Place p = Logic.widgetPlace(places, place); place = p.id;
        if (places.size() > 1) page.addView(choose(places, p), gap(8));
        page.addView(period(), gap(8));
        List<Logic.DayStat> w = Logic.window(Prefs.history(this, p.id), Logic.today(Sync.now()), days);
        LinearLayout card = Ui.column(this);
        card.setPadding(Ui.dp(this, 12), Ui.dp(this, 10), Ui.dp(this, 12), Ui.dp(this, 12));
        card.setBackground(Ui.card(this, pal, 0));
        TextView head = Ui.text(this, 18, Ui.heavy(this), pal.text); head.setText("Години за добу");
        card.addView(head, fill());
        TextView range = Ui.text(this, 13, Ui.body(this), pal.muted);
        range.setText("За графіком · " + date(w.get(0)) + " — " + date(w.get(w.size() - 1)));
        range.setPadding(0, Ui.dp(this, 6), 0, 0); card.addView(range, fill());
        StatsChart chart = new StatsChart(this, pal); chart.set(w);
        LinearLayout.LayoutParams cl = fill(); cl.topMargin = Ui.dp(this, 4); card.addView(chart, cl);
        if (days > 7) {
            TextView rounded = Ui.text(this, 12, Ui.body(this), pal.muted);
            rounded.setText("Числові підписи округлено до годин.");
            card.addView(rounded, gap(6));
        }
        card.addView(legend(w), gap(8));
        TextView avg = Ui.text(this, 14, Ui.heavy(this), pal.text); avg.setText("У середньому на день");
        card.addView(avg, gap(6));
        LinearLayout metrics = Ui.row(this); metrics.setBaselineAligned(false);
        LinearLayout.LayoutParams ml = weight(); ml.height = ViewGroup.LayoutParams.MATCH_PARENT;
        metrics.addView(metric(w, Logic.ON, "Зі світлом"), ml);
        LinearLayout.LayoutParams mr = weight(); mr.height = ViewGroup.LayoutParams.MATCH_PARENT; mr.leftMargin = Ui.dp(this, 6);
        metrics.addView(metric(w, Logic.OFF, "Без світла"), mr); card.addView(metrics, fill());
        TextView coverage = Ui.text(this, 12, Ui.body(this), pal.muted);
        int count = Logic.statKnownDays(w);
        String text = count == 0 ? "Ще немає даних за цей період" : "За " + count + " із " + w.size() + " днів";
        int possible = Logic.statAverageMinutes(w, Logic.MAYBE);
        if (possible > 0) text += " · можливе вимкнення: " + Logic.hoursMinutes(possible) + " на день";
        coverage.setText(text); coverage.setPadding(0, Ui.dp(this, 7), 0, 0); card.addView(coverage, fill());
        page.addView(card, fill());
        TextView note = Ui.text(this, 12, Ui.body(this), pal.muted);
        note.setText("Це графіки обленерго, а не виміряна наявність світла.\nІсторія зберігається на телефоні до 31 дня.");
        note.setLineSpacing(0, 1.1f); note.setPadding(Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 4), 0);
        page.addView(note, fill());
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
        for (int n : new int[] {7, 31}) {
            TextView b = Ui.pill(this, pal, n == 7 ? "Тиждень" : "Місяць", n == days, 14);
            b.setOnClickListener(v -> { days = n; render(); });
            LinearLayout.LayoutParams lp = weight(); if (n == 31) lp.leftMargin = Ui.dp(this, 6); row.addView(b, lp);
        }
        return row;
    }

    private View legend(List<Logic.DayStat> days) {
        FlowRow row = new FlowRow(this, Ui.dp(this, 10), Ui.dp(this, 5));
        legendItem(row, '1', "Зі світлом · год"); legendItem(row, '0', "Без світла · год");
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
