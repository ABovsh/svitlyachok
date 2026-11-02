package ua.abovsh.svitlo;

import android.app.Dialog;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowInsets;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

/**
 * The notifications of one place, from its bell or from the settings: a sheet that rises from the
 * bottom. Every change is saved at once and re-arms the reminders.
 */
final class AlertsSheet {
    private final Base a;
    private final Logic.Place place;
    private final LinearLayout col;

    private AlertsSheet(Base a, Logic.Place place, LinearLayout col) {
        this.a = a;
        this.place = place;
        this.col = col;
    }

    /** `closed` runs when the sheet goes, so the page under it shows the new state of the bell. */
    static void show(Base a, Logic.Place place, Runnable closed) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView sv = new ScrollView(a);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        LinearLayout col = Ui.column(a);
        int side = Ui.dp(a, 16);
        col.setPadding(side, Ui.dp(a, 10), side, Ui.dp(a, 24));
        sv.addView(col);
        if (Build.VERSION.SDK_INT >= 30) {  // above the navigation bar, not under it
            sv.setOnApplyWindowInsetsListener((v, in) -> {
                v.setPadding(0, 0, 0, in.getInsets(WindowInsets.Type.navigationBars()).bottom);
                return in;
            });
        }
        new AlertsSheet(a, place, col).build();
        d.setContentView(sv);
        Window w = d.getWindow();
        if (w != null) {
            float r = Ui.px(a, 28);
            GradientDrawable bg = new GradientDrawable();
            bg.setColor(a.pal.bg);
            bg.setCornerRadii(new float[] {r, r, r, r, 0, 0, 0, 0});
            w.setBackgroundDrawable(bg);
            w.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            w.setGravity(Gravity.BOTTOM);
        }
        d.setOnDismissListener(x -> closed.run());
        d.show();
    }

    private void build() {
        col.removeAllViews();
        View grip = new View(a);
        grip.setBackground(Ui.round(a.pal.line, Ui.px(a, 4)));
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(Ui.dp(a, 40), Ui.dp(a, 4));
        gl.gravity = Gravity.CENTER_HORIZONTAL;
        gl.bottomMargin = Ui.dp(a, 14);
        col.addView(grip, gl);

        TextView title = Ui.text(a, 19, Ui.display(a), a.pal.text);
        title.setText("Сповіщення · " + place.name);
        title.setPadding(Ui.dp(a, 6), 0, Ui.dp(a, 6), 0);
        col.addView(title, Base.fill());
        TextView where = Ui.text(a, 13, Ui.body(a), a.pal.muted);
        where.setText(Base.where(place));
        where.setPadding(Ui.dp(a, 6), Ui.dp(a, 4), Ui.dp(a, 6), Ui.dp(a, 12));
        col.addView(where, Base.fill());

        Logic.Alerts al = Prefs.alerts(a, place.id);
        LinearLayout box = a.box();
        box.addView(a.toggle("Вимкнення світла", "Попередити, що світло скоро вимкнуть", al.off,
                v -> save(x -> x.off = v)), Base.fill());
        if (al.off) box.addView(a.leads(Prefs.LEADS_OFF, al.leadOff, m -> save(x -> x.leadOff = m)), Base.fill());
        box.addView(a.divider());
        box.addView(a.toggle("Увімкнення світла", "Повідомити, що світло скоро з'явиться", al.on,
                v -> save(x -> x.on = v)), Base.fill());
        if (al.on) box.addView(a.leads(Prefs.LEADS_ON, al.leadOn, m -> save(x -> x.leadOn = m)), Base.fill());
        box.addView(a.divider());
        box.addView(a.toggle("Графік на завтра", "Щойно обленерго його опублікує", al.tomorrow,
                v -> save(x -> x.tomorrow = v)), Base.fill());
        box.addView(a.divider());
        box.addView(a.toggle("Зміни графіка", "Якщо змінилися години на сьогодні або завтра", al.changes,
                v -> save(x -> x.changes = v)), Base.fill());
        col.addView(box, Base.fill());

        TextView note = Ui.text(a, 13, Ui.body(a), a.pal.dim);
        note.setLineSpacing(0, 1.1f);
        note.setText("Лише для місця «" + place.name + "». Тиша вночі й пробне сповіщення — у налаштуваннях.");
        note.setPadding(Ui.dp(a, 6), Ui.dp(a, 12), Ui.dp(a, 6), 0);
        col.addView(note, Base.fill());
    }

    private void save(java.util.function.Consumer<Logic.Alerts> change) {
        Logic.Alerts al = Prefs.alerts(a, place.id);
        change.accept(al);
        Prefs.setAlerts(a, place.id, al);
        Sync.apply(a.getApplicationContext());
        build();
    }
}
