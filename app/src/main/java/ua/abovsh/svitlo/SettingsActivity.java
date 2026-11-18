package ua.abovsh.svitlo;

import android.app.NotificationManager;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.provider.Settings;
import android.view.Gravity;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Places, notifications, look. */
public class SettingsActivity extends Base {
    private int built = -1;
    private ArrayList<String> placeOrder;
    private String selectedPlace;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (state != null) {
            placeOrder = state.getStringArrayList("place_order");
            selectedPlace = state.getString("selected_place");
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle state) {
        super.onSaveInstanceState(state);
        state.putStringArrayList("place_order", placeOrder);
        state.putString("selected_place", selectedPlace);
    }

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
        List<Logic.Place> list = Prefs.places(this);
        if (list.size() < 2) {
            placeOrder = null;
            selectedPlace = null;
            page.addView(heading("Місця й сповіщення"));
        } else {
            page.addView(orderHeading(list), fill());
        }
        if (placeOrder != null) {
            ArrayList<String> ids = placeIds(list);
            placeOrder.retainAll(ids);
            for (String id : ids) if (!placeOrder.contains(id)) placeOrder.add(id);
            list.sort(java.util.Comparator.comparingInt(p -> placeOrder.indexOf(p.id)));
            if (!placeOrder.contains(selectedPlace)) selectedPlace = null;
        }
        LinearLayout places = box();
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
        if (placeOrder != null) page.addView(orderControls(list), gapTop(4));
    }

    private LinearLayout orderHeading(List<Logic.Place> list) {
        LinearLayout row = Ui.row(this);
        row.setGravity(Gravity.CENTER_VERTICAL);
        TextView label = heading("Місця й сповіщення");
        TextView action = Ui.text(this, 14, Ui.heavy(this), pal.accentText);
        action.setText(placeOrder == null ? "Змінити порядок ›" : "Готово");
        action.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        action.setMinHeight(Ui.dp(this, 48));
        action.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 6), 0);
        action.setOnClickListener(v -> {
            if (placeOrder == null) placeOrder = placeIds(list);
            else {
                if (!placeIds(Prefs.places(this)).equals(placeOrder)) Prefs.savePlaces(this, list);
                placeOrder = null;
                selectedPlace = null;
            }
            rebuild();
        });
        label.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        action.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int available = Ui.dp(this, getResources().getConfiguration().screenWidthDp - 32);
        boolean fits = label.getMeasuredWidth() + action.getMeasuredWidth() <= available;
        if (!fits) row.setOrientation(LinearLayout.VERTICAL);
        row.addView(label, fits ? weight() : fill());
        row.addView(action, fits ? wrap() : fill());
        return row;
    }

    private static ArrayList<String> placeIds(List<Logic.Place> places) {
        ArrayList<String> ids = new ArrayList<>();
        for (Logic.Place place : places) ids.add(place.id);
        return ids;
    }

    private View orderControls(List<Logic.Place> list) {
        LinearLayout box = box();
        int index = placeOrder.indexOf(selectedPlace);
        TextView title = note(index < 0 ? "Оберіть місце, щоб перемістити його" : "Перемістити «" + list.get(index).name + "»");
        title.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        title.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
        box.addView(title, fill());
        if (index < 0) return box;
        AdaptiveGrid actions = new AdaptiveGrid(this, 2, 6);
        actions.addView(moveButton("↑ Підняти", index, -1));
        actions.addView(moveButton("↓ Опустити", index, 1));
        box.addView(actions, fill());
        return box;
    }

    private View moveButton(String text, int from, int step) {
        int target = from + step;
        boolean enabled = target >= 0 && target < placeOrder.size();
        TextView button = Ui.pill(this, pal, text, false, 14);
        button.setMinimumWidth(Ui.dp(this, 112));
        button.setMinHeight(Ui.dp(this, 48));
        button.setEnabled(enabled);
        button.setAlpha(enabled ? 1f : 0.5f);
        button.setOnClickListener(v -> {
            Collections.swap(placeOrder, from, target);
            rebuild();
        });
        return button;
    }

    /** What is common to all places. */
    private void notificationsSection() {
        LinearLayout n = box();
        if (!notificationsAllowed()) {
            n.addView(warning(), fill());
            n.addView(divider());
        }
        n.addView(toggle("Тиша вночі", "З 23:00 до 07:00 сповіщення приходять без звуку", Prefs.quietNights(this),
                Ui.toned(this, R.drawable.ic_moon, pal.dark ? 0xFFC9C7F2 : 0xFF4B4A7A, pal.dark ? 0xFF2C2D4A : 0xFFE6E5F2, 32, 17),
                on -> Prefs.setFlag(this, "quiet", on)), fill());
        n.addView(divider());
        TextView test = Ui.text(this, 15, Ui.heavy(this), pal.accentText);
        test.setText("Надіслати пробне сповіщення");
        test.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
        test.setGravity(Gravity.CENTER_VERTICAL);
        test.setCompoundDrawables(Ui.toned(this, R.drawable.ic_send, pal.accentText, Ui.blend(pal.card, Ui.fade(pal.accent, .22f)), 32, 15), null, null, null);
        test.setCompoundDrawablePadding(Ui.dp(this, 10));
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
        int[] icons = {R.drawable.ic_theme_auto, R.drawable.ic_sun, R.drawable.ic_moon};
        for (int i = 0; i < themes.length; i++) {
            String id = themes[i][0];
            TextView t = Ui.pill(this, pal, themes[i][1], id.equals(Prefs.theme(this)), 14);
            t.setText(withIcon(icons[i], t.getCurrentTextColor(), themes[i][1]));
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

    /** An icon before the words, centred with them however wide the pill is. */
    private CharSequence withIcon(int icon, int ink, String words) {
        android.graphics.drawable.Drawable d = getDrawable(icon);
        if (d == null) return words;
        d = d.mutate();
        d.setTint(ink);
        int size = Ui.dp(this, 15);
        d.setBounds(0, 0, size, size);
        android.text.SpannableString s = new android.text.SpannableString("  " + words);
        int align = android.os.Build.VERSION.SDK_INT >= 29 ? android.text.style.DynamicDrawableSpan.ALIGN_CENTER
                : android.text.style.DynamicDrawableSpan.ALIGN_BASELINE;
        s.setSpan(new android.text.style.ImageSpan(d, align), 0, 1, android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
        return s;
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
            t.setText("Завантажити версію " + r.version + " на сайті ›");
            t.setOnClickListener(v -> Updates.open(this, Logic.SITE));  // downloaded there, in the browser
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
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 4));
        long now = Sync.now();
        java.time.LocalDate base = Logic.today(now);
        Logic.Data data = Store.data(this, p.region);
        BulbView bulb = new BulbView(this, pal);
        bulb.still();
        bulb.set(Logic.now(Logic.timeline(data, p.group, base), base, now, data != null && data.none).state);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Ui.dp(this, 30), Ui.dp(this, 30));
        bl.rightMargin = Ui.dp(this, 11);
        r.addView(bulb, bl);
        r.setClipChildren(false);
        LinearLayout t = Ui.column(this);
        t.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
        LinearLayout title = Ui.row(this);
        title.setGravity(Gravity.CENTER_VERTICAL);
        TextView name = Ui.text(this, 16, Ui.heavy(this), pal.text);
        name.setText(p.name);
        title.addView(name, new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        TextView queue = Ui.text(this, 12.5f, Ui.heavy(this), pal.accentText);  // as on the place's tile
        queue.setText(Logic.queue(p.group));
        queue.setPadding(Ui.dp(this, 5), Ui.dp(this, 1), Ui.dp(this, 5), Ui.dp(this, 1));
        queue.setBackground(Ui.round(Ui.blend(pal.card, Ui.fade(pal.accent, .18f)), Ui.px(this, 6)));
        LinearLayout.LayoutParams ql = wrap();
        ql.leftMargin = Ui.dp(this, 6);
        ql.rightMargin = Ui.dp(this, 6);
        title.addView(queue, ql);
        t.addView(title, fill());
        TextView w = Ui.text(this, 13, Ui.body(this), pal.muted);
        Logic.Region region = Logic.region(p.region);
        w.setText(region == null ? where(p) : region.name);
        w.setPadding(0, Ui.dp(this, 2), 0, 0);
        t.addView(w);
        if (placeOrder == null) {
            t.setOnClickListener(v -> startActivity(new Intent(this, SetupActivity.class).putExtra("id", p.id)));
        } else {
            boolean selected = p.id.equals(selectedPlace);
            t.setSelected(selected);
            t.setContentDescription(p.name + ". " + where(p));
            if (selected) r.setBackground(Ui.outline(pal.accentText, Ui.px(this, 12), Ui.dp(this, 1)));
            t.setOnClickListener(v -> {
                selectedPlace = p.id;
                rebuild();
            });
        }
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
        b.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 13), 0);
        int inset = Ui.dp(this, 6);
        b.setBackground(new android.graphics.drawable.InsetDrawable(Ui.round(pal.raised, Ui.px(this, 18)), 0, inset, 0, inset));
        android.graphics.drawable.Drawable icon = getDrawable(a.any() ? R.drawable.ic_bell : R.drawable.ic_bell_off);
        if (icon != null) {
            icon = icon.mutate(); icon.setTint(ink); icon.setBounds(0, 0, Ui.dp(this, 16), Ui.dp(this, 16));
            // not the relative form: before it is resolved its width is left out and the words reach the edge
            b.setCompoundDrawables(icon, null, null, null);
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
