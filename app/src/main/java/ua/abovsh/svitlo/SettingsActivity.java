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
        for (Logic.Place p : Prefs.places(this)) {
            b.append('|').append(p.id).append(p.name).append(p.group).append(Logic.alertsJson(Prefs.alerts(this, p.id)));
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
        helpSection();
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
        page.addView(note("Дача, батьки, робота: до " + Prefs.MAX_PLACES + " місць з будь-якими чергами."));
    }

    /** A row per place, opening its own notifications; then what is common to all places. */
    private void notificationsSection() {
        page.addView(heading("Сповіщення"));
        LinearLayout n = box();
        if (!notificationsAllowed()) {
            n.addView(warning(), fill());
            n.addView(divider());
        }
        for (Logic.Place p : Prefs.places(this)) {
            n.addView(alertsRow(p), fill());
            n.addView(divider());
        }
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

        LinearLayout x = box();
        x.addView(toggle("Тиша вночі", "З 23:00 до 07:00 сповіщення приходять без звуку",
                Prefs.quietNights(this), on -> Prefs.setFlag(this, "quiet", on)), fill());
        LinearLayout.LayoutParams xl = fill();
        xl.topMargin = Ui.dp(this, 10);
        page.addView(x, xl);
        page.addView(note("Для кожного місця — свої сповіщення; їх відкриває і дзвіночок на картці місця. "
                + "Одне сповіщення на місце: нове замінює попереднє. Зміни графіка застосунок перевіряє "
                + "приблизно раз на 15 хвилин."));
    }

    /** "Дім" and what it notifies about, with its bell; opens the place's own settings. */
    private View alertsRow(Logic.Place p) {
        Logic.Alerts a = Prefs.alerts(this, p.id);
        LinearLayout r = Ui.row(this);
        r.setGravity(android.view.Gravity.CENTER_VERTICAL);
        r.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
        ImageView bell = new ImageView(this);
        bell.setImageResource(a.any() ? R.drawable.ic_bell : R.drawable.ic_bell_off);
        bell.setColorFilter(a.any() ? pal.text : pal.dim, PorterDuff.Mode.SRC_IN);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Ui.dp(this, 22), Ui.dp(this, 22));
        bl.rightMargin = Ui.dp(this, 14);
        r.addView(bell, bl);
        r.addView(label(p.name, Logic.alertsSummary(a)), weight());
        ImageView go = new ImageView(this);
        go.setImageResource(R.drawable.ic_chevron);
        go.setColorFilter(pal.dim, PorterDuff.Mode.SRC_IN);
        go.setScaleType(ImageView.ScaleType.CENTER);
        r.addView(go, new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 44)));
        r.setContentDescription("Сповіщення, " + p.name + ": " + Logic.alertsSummary(a));
        r.setOnClickListener(v -> AlertsSheet.show(this, p, this::rebuild));
        return r;
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
        u.addView(toggle("Перевіряти нові версії", "Коли ви відкриваєте застосунок, не частіше двох разів на день",
                Updates.enabled(this), on -> Updates.setEnabled(this, on)), fill());
        u.addView(divider());
        u.addView(updateRow(), fill());
        page.addView(u, fill());
        page.addView(note("Застосунок питає GitHub лише, яка версія остання. Що встановлювати і коли, вирішуєте ви."));
    }

    private void helpSection() {
        page.addView(heading("Допомога"));
        LinearLayout h = box();
        h.addView(link("Повідомити про проблему на GitHub",
                "Відкриється форма з технічними даними. Потрібен обліковий запис GitHub.", v -> Report.onGitHub(this)), fill());
        page.addView(h, fill());
        page.addView(note("Застосунок нічого не надсилає сам: форма відкривається в браузері, і ви бачите текст "
                + "до надсилання. Технічні дані — версія, телефон, область і черга, без назв ваших місць."));
    }

    /** A row that opens something: a title, a line under it, a chevron. */
    private View link(String title, String desc, View.OnClickListener l) {
        LinearLayout r = Ui.row(this);
        r.setPadding(0, Ui.dp(this, 12), 0, Ui.dp(this, 12));
        r.addView(label(title, desc), weight());
        ImageView go = new ImageView(this);
        go.setImageResource(R.drawable.ic_chevron);
        go.setColorFilter(pal.dim, PorterDuff.Mode.SRC_IN);
        go.setScaleType(ImageView.ScaleType.CENTER);
        r.addView(go, new LinearLayout.LayoutParams(Ui.dp(this, 28), Ui.dp(this, 44)));
        r.setContentDescription(title);
        r.setOnClickListener(l);
        return r;
    }

    private void aboutSection() {
        page.addView(heading("Про застосунок"));
        TextView about = Ui.text(this, 14, Ui.body(this), pal.muted);
        about.setLineSpacing(0, 1.1f);
        about.setText("Світлячок " + BuildConfig.VERSION_NAME + "\n\n"
                + "Графік погодинних відключень світла для одного або кількох місць: що зараз, до котрої "
                + "години і що буде завтра. Нагадує перед вимкненням і увімкненням, має віджети й "
                + "статистику за тиждень і місяць.\n\n"
                + "Нічого не надсилає з телефона. Застосунок лише завантажує з GitHub відкритий файл графіка "
                + "вашої області й перевіряє, чи вийшла нова версія. Місця, черги й налаштування "
                + "зберігаються тільки на цьому телефоні. Без облікового запису, реклами й аналітики.\n\n"
                + "Показує графік обленерго, а не фактичну наявність світла у вашому будинку. "
                + "Не пов'язаний з ДТЕК чи іншими обленерго.");
        about.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 6), 0);
        page.addView(about, fill());
        TextView code = Ui.text(this, 15, Ui.heavy(this), pal.accentText);
        code.setText("Відкритий код на GitHub ›");
        code.setPadding(Ui.dp(this, 6), Ui.dp(this, 14), 0, Ui.dp(this, 4));
        code.setOnClickListener(v -> Updates.open(this, Logic.REPO));
        page.addView(code, fill());
        TextView why = Ui.text(this, 14, Ui.body(this), pal.muted);
        why.setLineSpacing(0, 1.1f);
        why.setText("Щоб нагадування приходили вчасно, дозвольте застосунку працювати без обмежень батареї.");
        why.setPadding(Ui.dp(this, 6), Ui.dp(this, 14), Ui.dp(this, 6), 0);
        page.addView(why, fill());
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
        ImageView go = new ImageView(this);
        go.setImageResource(R.drawable.ic_chevron);
        go.setColorFilter(pal.dim, PorterDuff.Mode.SRC_IN);
        go.setScaleType(ImageView.ScaleType.CENTER);
        r.addView(go, new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 44)));
        r.setOnClickListener(v -> startActivity(new Intent(this, SetupActivity.class).putExtra("id", p.id)));
        return r;
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
