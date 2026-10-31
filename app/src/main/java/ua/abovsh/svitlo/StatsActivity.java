package ua.abovsh.svitlo;

import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;

/** Light by the schedule, a bar a day, for the last week or month, with the daily average. */
public class StatsActivity extends Base {
    private int days = 7;
    private String place = "";
    private int built;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        place = Prefs.current(this);
    }

    @Override
    void render() {
        int sig = (place + days + Sync.now() / 3600).hashCode();  // the bars change by the hour at most
        if (sig == built) return;
        built = sig;
        page.removeAllViews();
        page.addView(topBar("Статистика"), fill());
        List<Logic.Place> places = Prefs.places(this);
        if (places.isEmpty()) return;
        Logic.Place p = Logic.widgetPlace(places, place);
        place = p.id;

        if (places.size() > 1) page.addView(choose(places, p), gap(10));
        page.addView(period(), gap(12));

        List<Logic.DayStat> w = Logic.window(Prefs.history(this, p.id), Logic.today(Sync.now()), days);
        LinearLayout card = Ui.column(this);
        card.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 16));
        card.setBackground(Ui.card(this, pal, 0));
        TextView head = Ui.text(this, 16, Ui.display(this), pal.text);
        head.setText(p.name + " · за графіком");
        card.addView(head, fill());
        StatsChart chart = new StatsChart(this, pal);
        chart.set(w);
        LinearLayout.LayoutParams cl = fill();
        cl.topMargin = Ui.dp(this, 14);
        card.addView(chart, cl);
        TextView avg = Ui.text(this, 15, Ui.heavy(this), pal.onText);
        String average = Logic.average(w);
        int known = 0;
        for (Logic.DayStat d : w) if (d.known) known++;
        avg.setText(average.isEmpty() ? "Ще немає даних за цей період" : average);
        avg.setLineSpacing(0, 1.1f);
        LinearLayout.LayoutParams al = fill();
        al.topMargin = Ui.dp(this, 12);
        card.addView(avg, al);
        if (known > 0 && known < w.size()) {
            TextView of = Ui.text(this, 13, Ui.body(this), pal.muted);
            of.setText("За " + known + " з " + w.size() + " днів — стільки графіків уже бачив цей телефон");
            of.setPadding(0, Ui.dp(this, 4), 0, 0);
            card.addView(of, fill());
        }
        card.setContentDescription(p.name + ". " + (average.isEmpty() ? "Ще немає даних" : average));
        page.addView(card, fill());
        LinearLayout.LayoutParams ll = fill();
        ll.topMargin = Ui.dp(this, 12);
        ll.leftMargin = Ui.dp(this, 6);
        page.addView(legend(), ll);

        TextView note = Ui.text(this, 13, Ui.body(this), pal.dim);
        note.setLineSpacing(0, 1.1f);
        note.setText("Обленерго публікує графік лише на сьогодні й завтра, тож статистика накопичується з дня, "
                + "коли застосунок з'явився на телефоні, — до місяця. Це графік, а не фактична наявність світла.");
        note.setPadding(Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6), 0);
        page.addView(note, fill());
    }

    private View choose(List<Logic.Place> places, Logic.Place chosen) {
        FlowRow row = new FlowRow(this, Ui.dp(this, 8), Ui.dp(this, 8));
        for (Logic.Place x : places) {
            TextView t = Ui.pill(this, pal, x.name, x.id.equals(chosen.id), 14);
            t.setOnClickListener(v -> {
                place = x.id;
                render();
            });
            row.addView(t);
        }
        return row;
    }

    private View period() {
        LinearLayout seg = Ui.row(this);
        int[] options = {7, 31};
        String[] names = {"Тиждень", "Місяць"};
        for (int i = 0; i < options.length; i++) {
            int n = options[i];
            TextView t = Ui.pill(this, pal, names[i], n == days, 14);
            t.setOnClickListener(v -> {
                days = n;
                render();
            });
            LinearLayout.LayoutParams l = weight();
            if (i > 0) l.leftMargin = Ui.dp(this, 8);
            seg.addView(t, l);
        }
        return seg;
    }

    private View legend() {
        FlowRow row = new FlowRow(this, Ui.dp(this, 12), Ui.dp(this, 6));
        String[][] items = {{"1", "зі світлом"}, {"0", "без світла"}, {"3", "можливе вимкнення"}};
        for (String[] it : items) {
            LinearLayout r = Ui.row(this);
            View sw = Ui.swatch(this, pal, it[0].charAt(0));
            LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(Ui.dp(this, 14), Ui.dp(this, 10));
            sl.rightMargin = Ui.dp(this, 5);
            r.addView(sw, sl);
            TextView t = Ui.text(this, 12, Ui.bold(this), pal.muted);
            t.setText(it[1]);
            r.addView(t);
            row.addView(r);
        }
        return row;
    }
}
