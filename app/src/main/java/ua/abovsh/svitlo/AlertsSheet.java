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
 * bottom. Every change is saved at once and re-arms the reminders; "Застосувати" closes the sheet,
 * for whoever does not expect a switch to be saved by itself.
 */
final class AlertsSheet {
    private final Base a;
    private final Logic.Place place;
    private final LinearLayout col;
    private Dialog shown;

    private AlertsSheet(Base a, Logic.Place place, LinearLayout col) {
        this.a = a;
        this.place = place;
        this.col = col;
    }

    /** `closed` runs when the sheet goes, so the page under it shows the new state of the bell. */
    static void show(Base a, Logic.Place place, Runnable closed) {
        LinearLayout col = Ui.column(a);
        AlertsSheet sheet = new AlertsSheet(a, place, col);
        sheet.build();
        sheet.shown = sheet(a, col, closed);
    }

    /** Why there may be no power although the schedule says there is, and where to look. */
    static void help(Base a, Logic.Place place) {
        LinearLayout col = Ui.column(a);
        grip(a, col);
        TextView title = Ui.text(a, 17, Ui.display(a), a.pal.text);
        title.setText("Світла немає, хоча за графіком є");
        title.setPadding(Ui.dp(a, 6), 0, Ui.dp(a, 6), 0);
        col.addView(title, Base.fill());
        TextView why = Ui.text(a, 14, Ui.body(a), a.pal.muted);
        why.setLineSpacing(0, 1.1f);
        why.setText("Графік не вимірює стан мережі. Можливі екстрені вимкнення, аварія, ремонт, затримка даних або несправність вводу чи автомата.");
        why.setPadding(Ui.dp(a, 6), Ui.dp(a, 8), Ui.dp(a, 6), Ui.dp(a, 12));
        col.addView(why, Base.fill());
        Logic.Region r = Logic.region(place.region);
        if (r != null && !r.dso.isEmpty()) {
            col.addView(a.primary(Logic.helpSite(r), v -> {
                try {
                    a.startActivity(new android.content.Intent(android.content.Intent.ACTION_VIEW, android.net.Uri.parse(r.dso)));
                } catch (Exception ignored) {
                    // no browser
                }
            }), Base.fill());
        }
        TextView note = Ui.text(a, 13, Ui.body(a), a.pal.muted);
        note.setLineSpacing(0, 1.1f);
        note.setText("Якщо проблема лише у вашому будинку, зверніться до електрика.");
        note.setPadding(Ui.dp(a, 6), Ui.dp(a, 10), Ui.dp(a, 6), 0);
        col.addView(note, Base.fill());
        sheet(a, col, () -> { });
    }

    private static void grip(Base a, LinearLayout col) {
        View grip = new View(a);
        grip.setBackground(Ui.round(a.pal.line, Ui.px(a, 4)));
        LinearLayout.LayoutParams gl = new LinearLayout.LayoutParams(Ui.dp(a, 40), Ui.dp(a, 4));
        gl.gravity = Gravity.CENTER_HORIZONTAL;
        gl.bottomMargin = Ui.dp(a, 12);
        col.addView(grip, gl);
    }

    private static Dialog sheet(Base a, LinearLayout col, Runnable closed) {
        Dialog d = new Dialog(a);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        ScrollView sv = new ScrollView(a);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        int side = Ui.dp(a, 16);
        col.setPadding(side, Ui.dp(a, 10), side, Ui.dp(a, 18));
        sv.addView(col);
        if (Build.VERSION.SDK_INT >= 30) {  // above the navigation bar, not under it
            sv.setOnApplyWindowInsetsListener((v, in) -> {
                v.setPadding(0, 0, 0, in.getInsets(WindowInsets.Type.navigationBars()).bottom);
                return in;
            });
        }
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
        return d;
    }

    private void close() {
        if (shown != null) shown.dismiss();
    }

    private void build() {
        col.removeAllViews();
        grip(a, col);
        TextView title = Ui.text(a, 19, Ui.display(a), a.pal.text);
        title.setText("Сповіщення · " + place.name);
        title.setPadding(Ui.dp(a, 6), 0, Ui.dp(a, 6), 0);
        col.addView(title, Base.fill());
        TextView where = Ui.text(a, 13, Ui.body(a), a.pal.muted);
        where.setText(Base.where(place));
        where.setPadding(Ui.dp(a, 6), Ui.dp(a, 3), Ui.dp(a, 6), Ui.dp(a, 10));
        col.addView(where, Base.fill());

        Logic.Alerts al = Prefs.alerts(a, place.id);
        LinearLayout box = a.box();
        box.addView(a.toggle("Перед вимкненням", "", al.off, a.pal.off, v -> save(x -> x.off = v)), Base.fill());
        if (al.off) box.addView(a.leads(Prefs.LEADS_OFF, al.leadOff, m -> save(x -> x.leadOff = m)), Base.fill());
        box.addView(a.divider());
        box.addView(a.toggle("Перед увімкненням", "", al.on, a.pal.on, v -> save(x -> x.on = v)), Base.fill());
        if (al.on) box.addView(a.leads(Prefs.LEADS_ON, al.leadOn, m -> save(x -> x.leadOn = m)), Base.fill());
        box.addView(a.divider());
        box.addView(a.toggle("Графік на завтра", "", al.tomorrow, v -> save(x -> x.tomorrow = v)), Base.fill());
        box.addView(a.divider());
        box.addView(a.toggle("Зміни графіка", "", al.changes, v -> save(x -> x.changes = v)), Base.fill());
        col.addView(box, Base.fill());

        long now = Sync.now();
        Logic.Event next = Sync.nextAlert(a, place.id, now);
        if (next != null) col.addView(nextBox(next, now), a.gapTop(8));
        col.addView(a.primary("Застосувати", v -> close()), a.gapTop(14));
        if (Prefs.places(a).size() > 1) {
            TextView all = Ui.text(a, 15, Ui.heavy(a), a.pal.accentText);
            all.setText("Застосувати до всіх місць");
            all.setGravity(Gravity.CENTER);
            all.setMinHeight(Ui.dp(a, 48));
            all.setOnClickListener(v -> confirmAll());
            col.addView(all, Base.fill());
        }
    }

    private View nextBox(Logic.Event next, long now) {
        boolean on = next.kind == Logic.Event.SOON_ON || next.kind == Logic.Event.AT_ON;
        LinearLayout box = Ui.column(a);
        box.setPadding(Ui.dp(a, 12), Ui.dp(a, 9), Ui.dp(a, 12), Ui.dp(a, 9));
        box.setBackground(Ui.round(a.pal.soft(on ? Logic.ON : Logic.OFF), Ui.px(a, 14)));
        TextView when = Ui.text(a, 14, Ui.heavy(a), a.pal.text);
        when.setText(Logic.alertSheetTitle(next, now));
        box.addView(when, Base.fill());
        TextView what = Ui.text(a, 13, Ui.body(a), a.pal.muted);
        what.setText(Logic.alertSheetDetail(next));
        what.setPadding(0, Ui.dp(a, 2), 0, 0);
        box.addView(what, Base.fill());
        return box;
    }

    private void confirmAll() {
        Ui.dialog(a).setTitle("Застосувати до всіх місць?")
                .setMessage("Сповіщення інших місць стануть такими самими, як у місця «" + place.name + "».")
                .setPositiveButton("Застосувати", (dialog, which) -> {
                    Logic.Alerts al = Prefs.alerts(a, place.id);
                    for (Logic.Place p : Prefs.places(a)) Prefs.setAlerts(a, p.id, al);
                    Sync.apply(a.getApplicationContext());
                    close();
                }).setNegativeButton("Скасувати", null).show();
    }

    private void save(java.util.function.Consumer<Logic.Alerts> change) {
        Logic.Alerts al = Prefs.alerts(a, place.id);
        change.accept(al);
        Prefs.setAlerts(a, place.id, al);
        Sync.apply(a.getApplicationContext());
        build();
    }
}
