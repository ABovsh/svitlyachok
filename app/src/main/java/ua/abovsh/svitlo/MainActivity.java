package ua.abovsh.svitlo;

import android.Manifest;
import android.annotation.SuppressLint;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.PorterDuff;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

/** All places at a glance (two or more), then the chosen one: now, today and tomorrow in full. */
public class MainActivity extends Base {
    /** A widget or a notification names the place to open. */
    static final String EXTRA_PLACE = "place";
    /** The round buttons of the place card, dp. */
    private static final int HERO_BUTTON = 44;
    private TextView sub;
    private String shown = "";
    private View addWidget;
    private LinearLayout body;
    private boolean loading;
    private float pullFrom = -1;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        if (Prefs.places(this).isEmpty()) {
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }
        page.addView(header());
        body = Ui.column(this);
        page.addView(body, fill());
        pullToRefresh();
        Sync.ensureChannels(this);
        Sync.schedulePeriodic(this);
        askNotifications();
    }

    @Override
    protected void onNewIntent(Intent i) {
        super.onNewIntent(i);
        setIntent(i);  // a widget or a notification of another place, while the app is open
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (body == null) return;
        if (Prefs.places(this).isEmpty()) {
            startActivity(new Intent(this, SetupActivity.class));
            finish();
            return;
        }
        String want = getIntent().getStringExtra(EXTRA_PLACE);
        if (want != null) {
            if (Prefs.place(this, want) != null) Prefs.setCurrent(this, want);
            getIntent().removeExtra(EXTRA_PLACE);
        }
        // the source answered moments ago (the alarm, the job): the cache is fresh, no network
        if (Logic.dueOnOpen(Sync.checkedAt(this), Sync.now())) refresh();
        else render();
        Updates.checkIfDue(this, () -> runOnUiThread(() -> {
            if (!isFinishing()) render();
        }));
    }

    private View header() {
        LinearLayout r = Ui.row(this);
        r.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 10));
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_logo);
        logo.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int lp = Ui.dp(this, 5);
        logo.setPadding(lp, lp, lp, lp);
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.TL_BR, new int[] {0xFFFFDD7A, 0xFFF5A800});
        g.setCornerRadius(Ui.px(this, 12));
        logo.setBackground(g);
        logo.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        r.addView(logo, new LinearLayout.LayoutParams(Ui.dp(this, 38), Ui.dp(this, 38)));
        LinearLayout t = Ui.column(this);
        t.setPadding(Ui.dp(this, 12), 0, Ui.dp(this, 8), 0);
        TextView name = Ui.text(this, 18, Ui.display(this), pal.text);
        name.setText("Світлячок");
        t.addView(name);
        sub = Ui.text(this, 13, Ui.body(this), pal.muted);
        sub.setPadding(0, Ui.dp(this, 3), 0, 0);
        t.addView(sub);
        r.addView(t, weight());
        addWidget = iconButton(R.drawable.ic_widget, "Додати віджет на головний екран", v -> {
            List<Logic.Place> places = Prefs.places(this);
            if (!places.isEmpty()) Widgets.offer(this, current(places));
        });
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(Ui.dp(this, 48), Ui.dp(this, 48));
        wl.rightMargin = Ui.dp(this, 8);
        r.addView(addWidget, wl);
        r.addView(iconButton(R.drawable.ic_gear, "Налаштування", v -> startActivity(new Intent(this, SettingsActivity.class))));
        return r;
    }

    // ---- data ------------------------------------------------------------------

    private void refresh() {
        if (loading) return;
        loading = true;
        render();
        new Thread(() -> {
            boolean ok = Sync.fetchAndApply(getApplicationContext());
            runOnUiThread(() -> {
                loading = false;
                if (isFinishing()) return;
                if (!ok) Toast.makeText(this, "Не вдалося оновити графік: немає зв'язку", Toast.LENGTH_SHORT).show();
                render();
            });
        }).start();
    }

    @SuppressLint("ClickableViewAccessibility")
    private void pullToRefresh() {
        int need = Ui.dp(this, 90);
        scroll.setOnTouchListener((v, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN:
                    pullFrom = scroll.getScrollY() == 0 ? e.getRawY() : -1;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (pullFrom >= 0 && e.getRawY() - pullFrom > need) {
                        pullFrom = -1;
                        refresh();
                    }
                    break;
                default:
                    pullFrom = -1;
            }
            return false;
        });
    }

    private Logic.Place current(List<Logic.Place> places) {
        String id = Prefs.current(this);
        for (Logic.Place p : places) if (p.id.equals(id)) return p;
        return places.get(0);
    }

    /**
     * The screen depends on the minute, the cached files, the places and their settings: when
     * none of these changed since the last drawing, nothing is rebuilt.
     */
    private String state() {
        StringBuilder b = new StringBuilder().append(Sync.now() / 60).append(loading).append(Prefs.current(this));
        for (Logic.Place p : Prefs.places(this)) {
            b.append('|').append(p.id).append(p.name).append(p.group).append(Store.checkedAt(this, p.region))
                    .append(System.identityHashCode(Store.data(this, p.region)))
                    .append(Logic.alertsJson(Prefs.alerts(this, p.id)));
        }
        Logic.Release r = Updates.offered(this);
        return b.append(r == null ? "" : r.version).toString();
    }

    @Override
    void onTick() {
        if (!state().equals(shown)) render();
    }

    @Override
    void render() {
        if (body == null) return;
        shown = state();
        List<Logic.Place> places = Prefs.places(this);
        if (places.isEmpty()) return;
        long now = Sync.now();
        LocalDate base = Logic.today(now);
        sub.setText(loading ? "оновлюю графік…" : Ui.date(base));
        Logic.Place p = current(places);
        Logic.Data d = Store.data(this, p.region);
        String tl = Logic.timeline(d, p.group, base);
        int cur = Logic.index(base, now);
        float nowRel = cur + (Logic.z(now).getMinute() % 30) / 30f;

        body.removeAllViews();
        Logic.Release update = Updates.offered(this);
        if (update != null) body.addView(updateCard(update), gap(10));
        boolean many = places.size() > 1;
        if (many) body.addView(tiles(places, p, base, now), gap(10));
        body.addView(hero(p, d, tl, base, now), gap(10));
        View stale = stale(p, d, now);
        if (stale != null) body.addView(stale, gap(10));
        body.addView(schedule(p, d, base, cur, nowRel), gap(10));
        LinearLayout.LayoutParams lg = gap(14);
        lg.leftMargin = lg.rightMargin = Ui.dp(this, 6);
        body.addView(legend(), lg);
        body.addView(footer(p, d, now), fill());

        // one widget per place: offered for the place on screen until it has one
        boolean offer = Widgets.canOffer(this, p);
        addWidget.setVisibility(offer ? View.VISIBLE : View.GONE);
        addWidget.setContentDescription("Додати віджет «" + p.name + "» на головний екран");
    }

    /** A newer version on GitHub: what it is, and two ways on. The user decides; "later" hides it. */
    private View updateCard(Logic.Release r) {
        LinearLayout card = Ui.column(this);
        card.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
        android.graphics.drawable.GradientDrawable bg = Ui.round(Ui.blend(pal.card, Ui.fade(pal.accent, 0.12f)),
                Ui.px(this, 24));
        bg.setStroke(Ui.dp(this, 1.5f), Ui.fade(pal.accent, 0.7f));
        card.setBackground(bg);

        LinearLayout top = Ui.row(this);
        top.setGravity(Gravity.CENTER_VERTICAL);
        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_download);
        icon.setColorFilter(pal.accentInk, PorterDuff.Mode.SRC_IN);
        icon.setScaleType(ImageView.ScaleType.CENTER);
        icon.setBackground(Ui.round(pal.accent, Ui.px(this, 100)));
        icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams il = new LinearLayout.LayoutParams(Ui.dp(this, 40), Ui.dp(this, 40));
        il.rightMargin = Ui.dp(this, 12);
        top.addView(icon, il);
        TextView title = Ui.text(this, 17, Ui.display(this), pal.text);
        title.setText("Нова версія " + r.version);
        top.addView(title, weight());
        card.addView(top, fill());

        TextView text = Ui.text(this, 14, Ui.body(this), pal.muted);
        text.setLineSpacing(0, 1.1f);
        text.setText("У вас " + BuildConfig.VERSION_NAME + ". Встановлюється поверх, місця й налаштування збережуться.");
        text.setPadding(0, Ui.dp(this, 8), 0, 0);
        card.addView(text, fill());

        FlowRow buttons = new FlowRow(this, Ui.dp(this, 8), Ui.dp(this, 8));
        TextView get = Ui.text(this, 15, Ui.heavy(this), pal.accentInk);
        get.setText("Завантажити");
        get.setGravity(Gravity.CENTER);
        get.setMinHeight(Ui.dp(this, 46));
        get.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 18), 0);
        get.setBackground(Ui.round(pal.accent, Ui.px(this, 100)));
        get.setOnClickListener(v -> Updates.open(this, r.apk));
        buttons.addView(get);
        TextView what = Ui.text(this, 15, Ui.heavy(this), pal.text);
        what.setText("Що нового");
        what.setGravity(Gravity.CENTER);
        what.setMinHeight(Ui.dp(this, 46));
        what.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 18), 0);
        what.setBackground(Ui.outline(pal.line, Ui.px(this, 100), Ui.dp(this, 1.5f)));
        what.setOnClickListener(v -> Updates.open(this, r.page));
        buttons.addView(what);
        // skipped, this version is not offered again; the next one is
        TextView skip = Ui.text(this, 15, Ui.heavy(this), pal.muted);
        skip.setText("Пропустити цю версію");
        skip.setGravity(Gravity.CENTER);
        skip.setMinHeight(Ui.dp(this, 46));
        skip.setPadding(Ui.dp(this, 10), 0, Ui.dp(this, 10), 0);
        skip.setOnClickListener(v -> {
            Updates.dismiss(this, r.version);
            render();
        });
        buttons.addView(skip);
        LinearLayout.LayoutParams bl = fill();
        bl.topMargin = Ui.dp(this, 14);
        card.addView(buttons, bl);
        return card;
    }

    /** Every place as a tile: its state and until when. A tap shows it in full below. */
    private View tiles(List<Logic.Place> places, Logic.Place chosen, LocalDate base, long now) {
        LinearLayout grid = Ui.column(this);
        LinearLayout row = null;
        for (int i = 0; i < places.size(); i++) {
            if (i % 2 == 0) {
                row = Ui.row(this);
                row.setBaselineAligned(false);
                LinearLayout.LayoutParams rl = fill();
                if (i > 0) rl.topMargin = Ui.dp(this, 8);
                grid.addView(row, rl);
            }
            LinearLayout.LayoutParams tl = weight();
            if (i % 2 == 1) tl.leftMargin = Ui.dp(this, 8);
            row.addView(tile(places.get(i), places.get(i).id.equals(chosen.id), base, now), tl);
        }
        if (places.size() % 2 == 1) {  // the odd one keeps half the width
            LinearLayout.LayoutParams fl = weight();
            fl.leftMargin = Ui.dp(this, 8);
            row.addView(new View(this), fl);
        }
        return grid;
    }

    private View tile(Logic.Place p, boolean chosen, LocalDate base, long now) {
        Logic.Data d = Store.data(this, p.region);
        Logic.Now n = Logic.now(Logic.timeline(d, p.group, base), base, now, d != null && d.none);
        if (d == null) {
            n.title = "Завантажую";
            n.big = "";
        }
        LinearLayout t = Ui.row(this);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setPadding(Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10), Ui.dp(this, 10));
        android.graphics.drawable.GradientDrawable bg = Ui.round(
                Ui.blend(pal.card, Ui.fade(pal.fill(n.state), pal.dark ? 0.14f : 0.10f)), Ui.px(this, 20));
        bg.setStroke(Ui.dp(this, chosen ? 2 : 1), chosen ? pal.text : pal.line);
        t.setBackground(bg);
        BulbView bulb = new BulbView(this, pal);
        bulb.still();
        bulb.set(n.state);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36));
        bl.rightMargin = Ui.dp(this, 8);
        t.addView(bulb, bl);
        LinearLayout w = Ui.column(this);
        TextView name = Ui.text(this, 13, Ui.bold(this), pal.muted);
        name.setText(p.name);
        name.setSingleLine(true);
        name.setEllipsize(android.text.TextUtils.TruncateAt.END);
        w.addView(name);
        TextView title = Ui.text(this, 15, Ui.display(this), pal.status(n.state));
        // no-break spaces: auto-size shrinks the words instead of dropping "немає" to a hidden line
        title.setText(n.title.replace(' ', '\u00A0'));
        title.setMaxLines(1);
        title.setGravity(Gravity.CENTER_VERTICAL);
        // shrinks to fit a narrow tile instead of cutting the state off ("Світла н…")
        title.setAutoSizeTextTypeUniformWithConfiguration(8, 15, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        title.setPadding(0, Ui.dp(this, 2), 0, 0);
        w.addView(title, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.round(title.getTextSize() * 1.35f) + Ui.dp(this, 2)));  // auto-size needs a fixed height
        TextView big = Ui.text(this, 13, Ui.heavy(this), pal.text);
        big.setText(n.big.isEmpty() ? " " : n.big);  // three lines in every tile: rows stay even
        big.setSingleLine(true);
        big.setEllipsize(android.text.TextUtils.TruncateAt.END);
        big.setPadding(0, Ui.dp(this, 2), 0, 0);
        w.addView(big);
        t.addView(w, weight());
        t.setContentDescription(p.name + ": " + n.title + (n.big.isEmpty() ? "" : ", " + n.big)
                + (chosen ? ", показано нижче" : ", натисніть, щоб показати графік"));
        t.setOnClickListener(v -> {
            Prefs.setCurrent(this, p.id);
            render();
            scroll.smoothScrollTo(0, 0);
        });
        return t;
    }

    private View hero(Logic.Place p, Logic.Data d, String tl, LocalDate base, long now) {
        Logic.Now n = Logic.now(tl, base, now, d != null && d.none, yesterday(p, d, base));
        if (d == null) {
            n.title = "Завантажую графік";
            n.big = "";
            n.hint = "інтернет потрібен лише для першого завантаження";
        }
        int tint = n.state == Logic.UNKNOWN ? 0 : pal.fill(n.state);
        // the words on the left; on the right, round buttons: notifications and statistics;
        // under both, across the whole card, what comes next
        LinearLayout card = Ui.column(this);
        card.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 16));
        card.setBackground(Ui.card(this, pal, tint));
        LinearLayout top = Ui.row(this);
        top.setBaselineAligned(false);
        top.setGravity(Gravity.TOP);  // the words start level with the bell, however tall the buttons
        card.addView(top, fill());
        LinearLayout words = Ui.column(this);
        top.addView(words, weight());
        LinearLayout buttons = Ui.column(this);
        buttons.addView(bell(p), new LinearLayout.LayoutParams(Ui.dp(this, HERO_BUTTON), Ui.dp(this, HERO_BUTTON)));
        View stats = iconButton(R.drawable.ic_stats, "Статистика за тиждень і місяць",
                v -> startActivity(new Intent(this, StatsActivity.class)));
        LinearLayout.LayoutParams sl = new LinearLayout.LayoutParams(Ui.dp(this, HERO_BUTTON), Ui.dp(this, HERO_BUTTON));
        sl.topMargin = Ui.dp(this, 8);
        buttons.addView(stats, sl);
        LinearLayout.LayoutParams bl = wrap();
        bl.leftMargin = Ui.dp(this, 8);
        top.addView(buttons, bl);

        TextView where = Ui.text(this, 14, Ui.bold(this), pal.muted);
        android.text.SpannableStringBuilder w = new android.text.SpannableStringBuilder(p.name);
        w.setSpan(new android.text.style.ForegroundColorSpan(pal.text), 0, p.name.length(), 0);
        w.append(" · черга ").append(Logic.queue(p.group));
        where.setText(w);
        where.setMaxLines(1);
        where.setEllipsize(android.text.TextUtils.TruncateAt.END);
        where.setPadding(0, Ui.dp(this, 11), 0, 0);  // level with the middle of the bell
        words.addView(where, fill());

        card.setContentDescription("Зараз за графіком: " + n.title
                + (n.since.isEmpty() ? "" : " " + n.since + ", " + n.already) + ", " + n.hint);
        // the same card for one place or several: with one place there are no tiles above it
        since(words, n);
        hint(card, n);
        return card;
    }

    /** Yesterday of a place: from the source while it still has it, else from the statistics. */
    private String yesterday(Logic.Place p, Logic.Data d, LocalDate base) {
        LocalDate y = base.minusDays(1);
        String day = Logic.day(d, p.group, y);
        return day != null ? day : Prefs.history(this, p.id).get(y);
    }

    /** "Світло є з 14:30" in the colour of the state, "уже 3 год 08 хв" under it. */
    private void since(LinearLayout card, Logic.Now n) {
        int colour = n.state == Logic.UNKNOWN ? pal.text : pal.status(n.state);
        TextView t = Ui.text(this, 17, Ui.heavy(this), colour);
        t.setText(n.since.isEmpty() ? n.title : n.title + " " + n.since);
        card.addView(t, withTop(8));
        if (n.already.isEmpty()) return;
        TextView a = Ui.text(this, 14, Ui.bold(this), pal.muted);
        a.setText(n.already);
        a.setPadding(0, Ui.dp(this, 2), 0, 0);
        card.addView(a, fill());
    }

    private LinearLayout.LayoutParams withTop(int dp) {
        LinearLayout.LayoutParams l = fill();
        l.topMargin = Ui.dp(this, dp);
        return l;
    }

    /**
     * "вимкнуть через 1 год 37 хв (21:00)" in a pill, in the colour of what comes: reddish before
     * an outage, greenish before power; under 30 minutes the pill fills with that colour.
     */
    private void hint(LinearLayout card, Logic.Now n) {
        if (n.hint.isEmpty()) return;
        boolean known = n.next != Logic.UNKNOWN;
        // the colour of what comes, softened towards the text: a hint, not an alarm
        int colour = known ? Ui.blend(pal.text, Ui.fade(pal.status(n.next), 0.8f)) : pal.text;
        int bg = pal.raised;
        if (known && n.soon) {
            colour = n.next == Logic.MAYBE ? pal.accentInk : pal.ink;
            bg = pal.fill(n.next);
        } else if (known) {
            bg = Ui.blend(pal.raised, Ui.fade(pal.fill(n.next), pal.dark ? 0.16f : 0.12f));
        }
        TextView hint = Ui.text(this, 15, Ui.bold(this), colour);
        hint.setText(n.hint);
        // one line: on a narrow screen or with large fonts the letters get smaller, not the line longer
        hint.setMaxLines(1);
        hint.setAutoSizeTextTypeUniformWithConfiguration(10, 15, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        hint.setPadding(Ui.dp(this, 12), Ui.dp(this, 9), Ui.dp(this, 12), Ui.dp(this, 9));
        hint.setBackground(Ui.round(bg, Ui.px(this, 14)));
        LinearLayout.LayoutParams hl = wrap();
        hl.topMargin = Ui.dp(this, 10);
        hl.rightMargin = Ui.dp(this, 4);
        card.addView(hint, hl);
    }

    /** The place's notifications: crossed out when none is on; a tap opens them. */
    private View bell(Logic.Place p) {
        Logic.Alerts a = Prefs.alerts(this, p.id);
        boolean any = a.any();
        ImageView b = iconButton(any ? R.drawable.ic_bell : R.drawable.ic_bell_off,
                "Сповіщення, " + p.name + ": " + Logic.alertsSummary(a),
                v -> AlertsSheet.show(this, p, this::render));
        b.setColorFilter(any ? pal.text : pal.dim, PorterDuff.Mode.SRC_IN);
        if (!any) b.setBackground(Ui.outline(pal.line, Ui.px(this, 100), Ui.dp(this, 1)));
        return b;
    }

    /** A line when the source has not answered for a while. */
    private View stale(Logic.Place p, Logic.Data d, long now) {
        long checked = Store.checkedAt(this, p.region);
        if (d == null || checked == 0 || now - checked <= Logic.STALE_S) return null;
        TextView t = Ui.text(this, 14, Ui.bold(this), pal.text);
        t.setText("Немає зв'язку з джерелом графіка. Показано дані, перевірені "
                + Logic.updated(checked, now).replace("графік оновлено ", "") + ".");
        t.setLineSpacing(0, 1.1f);
        t.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 14));
        t.setBackground(Ui.outline(pal.maybeText, Ui.px(this, 22), Ui.dp(this, 1)));
        return t;
    }

    /** Today and tomorrow in one card: a bar each and the outages as time ranges. */
    private View schedule(Logic.Place p, Logic.Data d, LocalDate base, int cur, float nowRel) {
        LinearLayout card = Ui.column(this);
        card.setPadding(Ui.dp(this, 16), Ui.dp(this, 14), Ui.dp(this, 16), Ui.dp(this, 14));
        card.setBackground(Ui.card(this, pal, 0));
        day(card, p, base, "Сьогодні", Logic.day(d, p.group, base), cur, nowRel);
        View line = divider();
        LinearLayout.LayoutParams ll = fill();
        ll.height = Ui.dp(this, 1);
        ll.topMargin = Ui.dp(this, 14);
        ll.bottomMargin = Ui.dp(this, 14);
        card.addView(line, ll);
        LocalDate t = base.plusDays(1);
        day(card, p, t, "Завтра, " + t.getDayOfMonth() + " " + Ui.MONTHS[t.getMonthValue() - 1],
                Logic.day(d, p.group, t), -1, -1);
        return card;
    }

    private void day(LinearLayout card, Logic.Place p, LocalDate date, String title, String slots, int cur, float nowRel) {
        // the title and how much light to expect, at the right edge; on a narrow screen it goes under
        FlowRow head = new FlowRow(this, Ui.dp(this, 10), Ui.dp(this, 6));
        head.spread();
        TextView t = Ui.text(this, 16, Ui.display(this), pal.text);
        t.setText(title);
        head.addView(t);
        if (slots != null) {
            String light = Logic.dayLight(slots);
            boolean clean = light.equals(Logic.NO_OUTAGES);
            TextView tot = Ui.text(this, 13, Ui.heavy(this), clean ? pal.ink : pal.onText);
            tot.setText(light);
            tot.setPadding(Ui.dp(this, 10), Ui.dp(this, 5), Ui.dp(this, 10), Ui.dp(this, 5));
            int tint = Ui.fade(pal.on, pal.dark ? 0.18f : 0.12f);
            tot.setBackground(Ui.round(clean ? pal.on : tint, Ui.px(this, 100)));
            head.addView(tot);
        }
        head.setContentDescription(Logic.describe(title, slots));
        card.addView(head, fill());

        if (slots == null) {
            TextView none = Ui.text(this, 14, Ui.body(this), pal.muted);
            none.setLineSpacing(0, 1.1f);
            none.setText(notPublished(p, title.startsWith("Завтра")));
            none.setPadding(0, Ui.dp(this, 8), 0, 0);
            card.addView(none, fill());
            return;
        }

        BarView bar = new BarView(this, pal);
        bar.set(slots, cur >= 0 ? nowRel : -1);
        bar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        LinearLayout.LayoutParams bl = fill();
        bl.topMargin = Ui.dp(this, 10);
        card.addView(bar, bl);

        // the outages, and at the right the day as a picture to send
        LinearLayout row = Ui.row(this);
        row.setGravity(Gravity.TOP);
        FlowRow chips = new FlowRow(this, Ui.dp(this, 6));
        for (Logic.Outage o : Logic.outages(slots, cur)) chips.addView(chip(o));
        row.addView(chips, weight());
        row.addView(shareButton(p, date, title));
        LinearLayout.LayoutParams cl = fill();
        cl.topMargin = Ui.dp(this, 8);
        card.addView(row, cl);
    }

    /** A small round button at the end of a day's title: this day as a picture, to send. */
    private View shareButton(Logic.Place p, LocalDate date, String title) {
        ImageView b = iconButton(R.drawable.ic_share, "Поділитися графіком: " + title, v -> Share.send(this, p, date));
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int pad = Ui.dp(this, 9);
        b.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(Ui.dp(this, 36), Ui.dp(this, 36));
        l.leftMargin = Ui.dp(this, 8);
        b.setLayoutParams(l);
        return b;
    }

    private String notPublished(Logic.Place p, boolean tomorrow) {
        if (!tomorrow) return "Обленерго ще не опублікувало графік на сьогодні.";
        boolean told = Prefs.alerts(this, p.id).tomorrow;
        return "Графік ще не опубліковано." + (told ? " Сповіщення прийде, щойно він з'явиться." : "");
    }

    /** "06:00 – 08:00": filled red now, amber outline for a possible outage, faded when past. */
    private View chip(Logic.Outage o) {
        boolean now = o.when == Logic.Outage.NOW;
        TextView c = Ui.text(this, 14, Ui.heavy(this), now ? pal.ink : pal.text);
        c.setText((now ? "зараз " : "") + (o.maybe ? "можливо " : "") + o.label.replace(" – ", "–"));
        c.setPadding(Ui.dp(this, 11), Ui.dp(this, 6), Ui.dp(this, 11), Ui.dp(this, 6));
        if (now) c.setBackground(Ui.round(o.maybe ? pal.maybe : pal.off, Ui.px(this, 100)));
        else if (o.maybe) c.setBackground(Ui.outline(pal.maybe, Ui.px(this, 100), Ui.dp(this, 1.5f)));
        else c.setBackground(Ui.round(Ui.fade(pal.off, pal.dark ? 0.22f : 0.12f), Ui.px(this, 100)));
        if (o.when == Logic.Outage.PAST) c.setAlpha(0.45f);
        String when = "";
        if (o.when == Logic.Outage.PAST) when = ", минуло";
        else if (now) when = ", триває зараз";
        c.setContentDescription((o.maybe ? "можливе вимкнення " : "вимкнення ") + o.label + ", " + o.length + when);
        return c;
    }

    /** What the colours of the bars mean. */
    private View legend() {
        FlowRow row = new FlowRow(this, Ui.dp(this, 12), Ui.dp(this, 6));
        String[][] items = {{"1", "світло є"}, {"0", "світла немає"}, {"3", "можливе вимкнення"}, {"2", "графіка немає"}};
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

    private View footer(Logic.Place p, Logic.Data d, long now) {
        TextView t = Ui.text(this, 12, Ui.body(this), pal.dim);
        t.setLineSpacing(0, 1.1f);
        List<String> when = new ArrayList<>();
        if (d != null && d.updated > 0) when.add(cap(Logic.updated(d.updated, now)));
        long checked = Store.checkedAt(this, p.region);
        if (checked > 0) when.add("перевірено о\u00A0" + Logic.hm(checked));
        t.setText((when.isEmpty() ? "" : String.join(" · ", when) + ".\n")
                + "Це графік обленерго, а не фактична наявність світла. Новий графік з'являється сам.");
        t.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 6), 0);
        return t;
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // First start: notifications (Android 13+), then exemption from battery saving so the
    // widget and reminders keep working on Samsung and Xiaomi.
    private void askNotifications() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS}, 1);
        } else {
            askBattery();
        }
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        askBattery();
    }

    private void askBattery() {
        PowerManager pm = getSystemService(PowerManager.class);
        if (pm.isIgnoringBatteryOptimizations(getPackageName())) return;
        if (!Prefs.once(this, "asked_battery")) return;
        try {
            startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName())));
        } catch (Exception ignored) {
            // some builds hide the dialog; reminders then may come a little late
        }
    }
}
