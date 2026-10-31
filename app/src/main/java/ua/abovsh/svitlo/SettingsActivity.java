package ua.abovsh.svitlo;

import android.app.NotificationManager;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.provider.Settings;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.List;

/** Places, notifications, look. */
public class SettingsActivity extends Base {
    private int built = -1;

    @Override
    void render() {
        // rebuild only when something here could have changed (back from a place, from system settings)
        StringBuilder b = new StringBuilder(notificationsAllowed() ? "1" : "0");
        Logic.Alerts a = Prefs.alerts(this);
        b.append(a.off).append(a.on).append(a.leadOff).append(a.leadOn);
        for (Logic.Place p : Prefs.places(this)) {
            b.append('|').append(p.id).append(p.name).append(p.group).append(Prefs.muted(this, p.id));
        }
        int sig = b.toString().hashCode();
        if (sig == built) return;
        built = sig;
        build();
    }

    private void rebuild() {
        built = -1;
        render();
    }

    private boolean notificationsAllowed() {
        return getSystemService(NotificationManager.class).areNotificationsEnabled();
    }

    private void build() {
        int y = scroll.getScrollY();
        page.removeAllViews();
        page.addView(topBar("Налаштування"), fill());

        placesSection();
        notificationsSection();
        lookSection();
        updatesSection();
        aboutSection();
        scroll.post(() -> scroll.scrollTo(0, y));
    }

    private void placesSection() {
        page.addView(heading("Місця"));
        LinearLayout places = box();
        List<Logic.Place> list = Prefs.places(this);
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) places.addView(divider());
            places.addView(placeRow(list.get(i)), fill());
        }
        if (list.size() < Prefs.MAX_PLACES) {
            if (!list.isEmpty()) places.addView(divider());
            LinearLayout add = Ui.row(this);
            add.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
            ImageView plus = new ImageView(this);
            plus.setImageResource(R.drawable.ic_plus);
            plus.setColorFilter(pal.accentInk, PorterDuff.Mode.SRC_IN);
            plus.setScaleType(ImageView.ScaleType.CENTER);
            plus.setBackground(Ui.round(pal.accent, Ui.px(this, 100)));
            LinearLayout.LayoutParams pl = new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36));
            pl.rightMargin = Ui.dp(this, 14);
            add.addView(plus, pl);
            TextView t = Ui.text(this, 16, Ui.heavy(this), pal.text);
            t.setText("Додати місце");
            add.addView(t, weight());
            add.setOnClickListener(v -> startActivity(new Intent(this, SetupActivity.class)));
            places.addView(add, fill());
        }
        page.addView(places, fill());
        page.addView(note("Дача, батьки, робота: до " + Prefs.MAX_PLACES + " місць з будь-якими чергами. "
                + "Дзвіночок вимикає сповіщення одного місця."));
    }

    private void notificationsSection() {
        page.addView(heading("Сповіщення"));
        LinearLayout n = box();
        if (!notificationsAllowed()) {
            n.addView(warning(), fill());
            n.addView(divider());
        }
        Logic.Alerts a = Prefs.alerts(this);
        alertRow(n, OFF_ALERT, a.off, a.leadOff);
        n.addView(divider());
        alertRow(n, ON_ALERT, a.on, a.leadOn);
        n.addView(divider());
        TextView test = Ui.text(this, 15, Ui.heavy(this), pal.accentText);
        test.setText("Надіслати пробне сповіщення");
        test.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 14));
        test.setOnClickListener(v -> {
            if (!notificationsAllowed()) {
                openNotificationSettings();
                return;
            }
            Sync.testNotification(this);
        });
        n.addView(test, fill());
        page.addView(n, fill());

        page.addView(heading("Додатково"));
        LinearLayout x = box();
        x.addView(toggle("Графік на завтра", "Щойно обленерго його опублікує",
                Prefs.tomorrowNotes(this), on -> Prefs.setFlag(this, "note_tomorrow", on)), fill());
        x.addView(divider());
        x.addView(toggle("Зміни графіка", "Якщо змінилися години на сьогодні або завтра",
                Prefs.changeNotes(this), on -> Prefs.setFlag(this, "note_change", on)), fill());
        x.addView(divider());
        x.addView(toggle("Тиша вночі", "З 23:00 до 07:00 сповіщення приходять без звуку",
                Prefs.quietNights(this), on -> Prefs.setFlag(this, "quiet", on)), fill());
        page.addView(x, fill());
        page.addView(note("Нагадування приходять точно у свій час. Новий графік Світлячок перевіряє "
                + "приблизно раз на 15 хвилин."));
    }

    private void lookSection() {
        page.addView(heading("Вигляд"));
        LinearLayout look = box();
        look.setPadding(Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14), Ui.dp(this, 14));
        LinearLayout seg = Ui.row(this);
        String[][] themes = {{"system", "Як у системі"}, {"light", "Світла"}, {"dark", "Темна"}};
        for (int i = 0; i < themes.length; i++) {
            String id = themes[i][0];
            TextView t = Ui.pill(this, pal, themes[i][1], id.equals(Prefs.theme(this)), 14);
            t.setPadding(Ui.dp(this, 6), Ui.dp(this, 12), Ui.dp(this, 6), Ui.dp(this, 12));
            t.setOnClickListener(v -> {
                Prefs.setTheme(this, id);
                Sync.apply(getApplicationContext());  // the widget follows
                recreate();
            });
            LinearLayout.LayoutParams l = weight();
            if (i == 0) l.weight = 1.6f;  // "Як у системі" is the longest
            if (i > 0) l.leftMargin = Ui.dp(this, 8);
            seg.addView(t, l);
        }
        look.addView(seg, fill());
        page.addView(look, fill());
    }

    private void updatesSection() {
        page.addView(heading("Оновлення"));
        LinearLayout u = box();
        u.addView(toggle("Перевіряти нові версії", "Коли ви відкриваєте Світлячок, не частіше двох разів на день",
                Updates.enabled(this), on -> Updates.setEnabled(this, on)), fill());
        u.addView(divider());
        u.addView(updateRow(), fill());
        page.addView(u, fill());
        page.addView(note("Світлячок питає GitHub лише, яка версія остання. Що встановлювати і коли, вирішуєте ви."));
    }

    private void aboutSection() {
        page.addView(heading("Про застосунок"));
        TextView about = Ui.text(this, 14, Ui.body(this), pal.muted);
        about.setLineSpacing(0, 1.1f);
        about.setText("Світлячок " + BuildConfig.VERSION_NAME + "\n\n"
                + "Показує графік погодинних відключень, який публікує обленерго. Застосунок нічого не "
                + "надсилає з телефона: він лише завантажує з GitHub відкритий файл графіка вашої області й "
                + "перевіряє, чи вийшла нова версія. "
                + "Місця, черги й налаштування зберігаються тільки на цьому телефоні. Без реєстрації, реклами "
                + "й аналітики.\n\n"
                + "Застосунок не знає, чи є світло у вашому будинку насправді, і не пов'язаний з ДТЕК "
                + "чи іншими обленерго. Код відкритий: github.com/ABovsh/svitlyachok\n\n"
                + "Щоб нагадування приходили вчасно, дозвольте Світлячку працювати без обмежень батареї.");
        about.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 6), 0);
        page.addView(about, fill());
        TextView battery = Ui.text(this, 15, Ui.heavy(this), pal.accentText);
        battery.setText("Налаштування батареї ›");
        battery.setPadding(Ui.dp(this, 6), Ui.dp(this, 14), 0, Ui.dp(this, 14));
        battery.setOnClickListener(v -> {
            try {
                startActivity(new Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS));
            } catch (Exception e) {
                Toast.makeText(this, "Відкрийте налаштування батареї телефону", Toast.LENGTH_SHORT).show();
            }
        });
        page.addView(battery, fill());
    }

    /** "Завантажити версію 1.3.0 ›" when a newer one is known, otherwise "Перевірити зараз". */
    private View updateRow() {
        TextView t = Ui.text(this, 15, Ui.heavy(this), pal.accentText);
        t.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 14));
        Logic.Release r = Updates.latest(this);
        if (r != null) {
            t.setText("Завантажити версію " + r.version + " ›");
            t.setOnClickListener(v -> Updates.open(this, r.apk));
            return t;
        }
        t.setText("Перевірити зараз");
        t.setOnClickListener(v -> {
            t.setText("Перевіряю…");
            t.setOnClickListener(null);
            new Thread(() -> {
                int result = Updates.check(getApplicationContext());
                runOnUiThread(() -> {
                    if (isFinishing()) return;
                    if (result == Updates.CURRENT) {
                        Toast.makeText(this, "У вас остання версія " + BuildConfig.VERSION_NAME, Toast.LENGTH_SHORT).show();
                    } else if (result == Updates.FAILED) {
                        Toast.makeText(this, "Не вдалося перевірити: немає зв'язку з GitHub", Toast.LENGTH_SHORT).show();
                    }
                    rebuild();
                });
            }).start();
        });
        return t;
    }

    private View placeRow(Logic.Place p) {
        LinearLayout r = Ui.row(this);
        r.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
        LinearLayout t = Ui.column(this);
        TextView name = Ui.text(this, 16, Ui.heavy(this), pal.text);
        name.setText(p.name);
        t.addView(name);
        TextView w = Ui.text(this, 13, Ui.body(this), pal.muted);
        w.setText(where(p));
        w.setPadding(0, Ui.dp(this, 3), 0, 0);
        t.addView(w);
        r.addView(t, weight());
        boolean muted = Prefs.muted(this, p.id);
        ImageView bell = iconButton(muted ? R.drawable.ic_bell_off : R.drawable.ic_bell,
                muted ? "Сповіщення вимкнено" : "Сповіщення увімкнено", v -> {
                    Prefs.setMuted(this, p.id, !muted);
                    Sync.apply(getApplicationContext());
                    rebuild();
                });
        bell.setColorFilter(muted ? pal.dim : pal.text, PorterDuff.Mode.SRC_IN);
        if (muted) bell.setBackground(Ui.outline(pal.line, Ui.px(this, 100), Ui.dp(this, 1)));
        r.addView(bell);
        ImageView go = new ImageView(this);
        go.setImageResource(R.drawable.ic_chevron);
        go.setColorFilter(pal.dim, PorterDuff.Mode.SRC_IN);
        go.setScaleType(ImageView.ScaleType.CENTER);
        r.addView(go, new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 44)));
        r.setOnClickListener(v -> startActivity(new Intent(this, SetupActivity.class).putExtra("id", p.id)));
        return r;
    }

    /** One of the two main notifications, as the settings screen shows it. */
    private static final class Alert {
        final String title;
        final String desc;
        final String key;
        final String leadKey;
        final int[] leads;

        Alert(String title, String desc, String key, String leadKey, int[] leads) {
            this.title = title;
            this.desc = desc;
            this.key = key;
            this.leadKey = leadKey;
            this.leads = leads;
        }
    }

    private static final Alert OFF_ALERT = new Alert("Вимкнення світла", "Попередити, що світло скоро вимкнуть",
            "n_off", "lead_off", Prefs.LEADS_OFF);
    private static final Alert ON_ALERT = new Alert("Увімкнення світла", "Повідомити, що світло скоро з'явиться",
            "n_on", "lead_on", Prefs.LEADS_ON);

    /** A switch and, while it is on, how early. */
    private void alertRow(LinearLayout box, Alert al, boolean on, int lead) {
        int[] leads = al.leads;
        String leadKey = al.leadKey;
        box.addView(toggle(al.title, al.desc, on, v -> {
            Prefs.setFlag(this, al.key, v);
            rebuild();
        }), fill());
        if (!on) return;
        TextView when = Ui.text(this, 13, Ui.bold(this), pal.muted);
        when.setText("Коли нагадати");
        when.setPadding(0, 0, 0, Ui.dp(this, 8));
        box.addView(when, fill());
        LinearLayout row = Ui.row(this);
        for (int i = 0; i < leads.length; i++) {
            int v = leads[i];
            TextView t = Ui.pill(this, pal, v == 0 ? "Одразу" : v + " хв", v == lead, 13.5f);
            t.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
            t.setContentDescription(v == 0 ? "у момент зміни" : "за " + v + " хвилин до зміни");
            t.setOnClickListener(c -> {
                Prefs.setInt(this, leadKey, v);
                Sync.apply(getApplicationContext());
                rebuild();
            });
            LinearLayout.LayoutParams lp = weight();
            if (v == 0) lp.weight = 1.4f;  // "Одразу" is the longest word
            if (i > 0) lp.leftMargin = Ui.dp(this, 6);
            row.addView(t, lp);
        }
        LinearLayout.LayoutParams rl = fill();
        rl.bottomMargin = Ui.dp(this, 14);
        box.addView(row, rl);
    }

    private View toggle(String title, String desc, boolean on, ToggleView.Listener l) {
        LinearLayout r = Ui.row(this);
        r.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
        r.addView(label(title, desc), weight());
        ToggleView tv = new ToggleView(this, pal, on, v -> {
            l.changed(v);
            Sync.apply(getApplicationContext());
        });
        tv.setContentDescription(title);
        LinearLayout.LayoutParams tl = wrap();
        tl.leftMargin = Ui.dp(this, 12);
        r.addView(tv, tl);
        r.setOnClickListener(v -> tv.performClick());
        return r;
    }

    private View label(String title, String desc) {
        LinearLayout t = Ui.column(this);
        TextView n = Ui.text(this, 16, Ui.heavy(this), pal.text);
        n.setText(title);
        t.addView(n);
        TextView d = Ui.text(this, 13, Ui.body(this), pal.muted);
        d.setText(desc);
        d.setLineSpacing(0, 1.1f);
        d.setPadding(0, Ui.dp(this, 4), 0, 0);
        t.addView(d);
        return t;
    }

    private View warning() {
        LinearLayout r = Ui.column(this);
        r.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
        TextView t = Ui.text(this, 15, Ui.heavy(this), pal.text);
        t.setText("Сповіщення Світлячка вимкнені в телефоні");
        r.addView(t);
        TextView b = Ui.text(this, 15, Ui.heavy(this), pal.accentText);
        b.setText("Увімкнути ›");
        b.setPadding(0, Ui.dp(this, 8), 0, 0);
        r.addView(b);
        r.setOnClickListener(v -> openNotificationSettings());
        return r;
    }

    private void openNotificationSettings() {
        try {
            startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                    .putExtra(Settings.EXTRA_APP_PACKAGE, getPackageName()));
        } catch (Exception ignored) {
            // very old firmware
        }
    }

    private TextView note(String s) {
        TextView t = Ui.text(this, 13, Ui.body(this), pal.dim);
        t.setLineSpacing(0, 1.1f);
        t.setText(s);
        t.setPadding(Ui.dp(this, 6), Ui.dp(this, 10), Ui.dp(this, 6), 0);
        return t;
    }
}
