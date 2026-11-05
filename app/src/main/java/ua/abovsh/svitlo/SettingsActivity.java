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

    @Override boolean hasClock() { return false; }

    @Override
    void render() {
        // rebuild only when something here could have changed (back from a place, from system settings)
        StringBuilder b = new StringBuilder(notificationsAllowed() ? "1" : "0");
        for (Logic.Place p : Prefs.places(this)) {
            b.append('|').append(p.id).append(p.name).append(p.region).append(p.group).append(Logic.alertsJson(Prefs.alerts(this, p.id)));
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

    /** One row per place: its name opens the place, its bell opens the notifications of the place. */
    private void placesSection() {
        page.addView(heading("Місця й сповіщення"));
        LinearLayout places = box();
        List<Logic.Place> list = Prefs.places(this);
        for (int i = 0; i < list.size(); i++) {
            if (i > 0) places.addView(divider());
            places.addView(placeRow(list.get(i)), fill());
        }
        if (list.size() < Prefs.MAX_PLACES) {
            if (!list.isEmpty()) places.addView(divider());
            LinearLayout add = Ui.row(this);
            add.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
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
    }

    /** What is common to all places. */
    private void notificationsSection() {
        LinearLayout n = box();
        if (!notificationsAllowed()) {
            n.addView(warning(), fill());
            n.addView(divider());
        }
        n.addView(toggle("Тиша вночі", "З 23:00 до 07:00 сповіщення приходять без звуку",
                Prefs.quietNights(this), on -> Prefs.setFlag(this, "quiet", on)), fill());
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
        LinearLayout.LayoutParams nl = fill();
        nl.topMargin = Ui.dp(this, 10);
        page.addView(n, nl);
        page.addView(note("Одне сповіщення на місце: нове замінює попереднє. Графік перевіряється приблизно раз на 15 хвилин."));
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
        if (BuildConfig.IS_RC) {
            page.addView(heading("Тестова версія " + BuildConfig.VERSION_NAME));
            page.addView(note("Світлячок RC працює окремо від основного застосунку. Наступний RC встановіть поверх цього APK."));
            return;
        }
        page.addView(heading("Оновлення"));
        LinearLayout u = box();
        u.addView(updateRow(), fill());
        page.addView(u, fill());
        page.addView(note("Коли ви відкриваєте застосунок, не частіше двох разів на день, він питає GitHub лише, "
                + "яка версія остання. Що встановлювати і коли, вирішуєте ви; непотрібну версію можна пропустити."));
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
                + "Графік погодинних вимкнень світла для одного або кількох місць: що зараз, до котрої "
                + "години і що буде завтра. Сповіщає перед вимкненням і увімкненням, має віджети й "
                + "статистику за тиждень і 4 тижні.\n\n"
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
        why.setText("Щоб сповіщення приходили вчасно, дозвольте застосунку працювати без обмежень батареї.");
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

    /** "Оновити до версії 1.3.0 ›" when a newer one is known, otherwise "Перевірити зараз". */
    private View updateRow() {
        TextView t = Ui.text(this, 15, Ui.heavy(this), pal.accentText);
        t.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 14));
        Logic.Release r = Updates.latest(this);
        if (r != null) {
            t.setText("Оновити до версії " + r.version + " ›");
            t.setOnClickListener(v -> {
                t.setOnClickListener(null);
                t.setText("Завантажую… 0 %");
                Updates.get(this, r, p -> t.setText("Завантажую… " + p + " %"), this::rebuild);
            });
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
        r.setBaselineAligned(false);
        r.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        LinearLayout t = Ui.column(this);
        t.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        TextView name = Ui.text(this, 16, Ui.heavy(this), pal.text);
        name.setText(p.name);
        t.addView(name);
        TextView w = Ui.text(this, 13, Ui.body(this), pal.muted);
        w.setText(where(p));
        w.setPadding(0, Ui.dp(this, 2), 0, 0);
        t.addView(w);
        t.setOnClickListener(v -> startActivity(new Intent(this, SetupActivity.class).putExtra("id", p.id)));
        r.addView(t, weight());
        r.addView(bell(p), wrap());
        return r;
    }

    /** 48 dp to touch, 36 to see; grey when nothing is sent for this place. */
    private View bell(Logic.Place p) {
        Logic.Alerts a = Prefs.alerts(this, p.id);
        int ink = a.any() ? pal.text : pal.quiet;
        TextView b = Ui.text(this, 13, Ui.heavy(this), ink);
        b.setText(Logic.alertsShort(a));
        b.setMinHeight(Ui.dp(this, 48));
        b.setGravity(android.view.Gravity.CENTER_VERTICAL);
        b.setPadding(Ui.dp(this, 9), 0, Ui.dp(this, 11), 0);
        int inset = Ui.dp(this, 6);
        b.setBackground(new android.graphics.drawable.InsetDrawable(Ui.round(pal.raised, Ui.px(this, 18)), 0, inset, 0, inset));
        android.graphics.drawable.Drawable icon = getDrawable(a.any() ? R.drawable.ic_bell : R.drawable.ic_bell_off);
        if (icon != null) {
            icon = icon.mutate(); icon.setTint(ink); icon.setBounds(0, 0, Ui.dp(this, 16), Ui.dp(this, 16));
            b.setCompoundDrawablesRelative(icon, null, null, null);
            b.setCompoundDrawablePadding(Ui.dp(this, 5));
        }
        b.setContentDescription("Сповіщення, " + p.name + ": " + Logic.alertsSummary(a));
        b.setOnClickListener(v -> AlertsSheet.show(this, p, this::rebuild));
        return b;
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
}
