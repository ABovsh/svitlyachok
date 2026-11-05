package ua.abovsh.svitlo;

import android.Manifest;
import android.annotation.SuppressLint;
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
    static final String EXTRA_OFFER_WIDGET = "offer_widget";
    /** The version being downloaded now, and how far; the card shows it after a re-render too. */
    private String downloading;
    private int percent;
    private TextView getButton;

    private static final int HERO_BUTTON = 48;
    private static final float HERO_CLOCK = 27;
    private TextView sub;
    private String shown = "";
    private View addWidget;
    private LinearLayout body;
    private boolean loading;
    private final List<Runnable> clockUpdates = new ArrayList<>();
    private final BarTips tips = new BarTips(this);
    private float pullFrom = -1;
    private boolean entering;

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
    protected void onPause() {
        tips.hide();
        Sync.onChanged(null);
        super.onPause();
    }

    @Override
    protected void onResume() {
        entering = true;  // what the screen shows is decided below, once
        super.onResume();
        entering = false;
        if (body == null) return;
        Sync.onChanged(() -> runOnUiThread(() -> {
            if (!loading) onTick();  // a running refresh draws once, when it ends
        }));
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
        // a refresh already running draws when it ends; this screen may have to show another place now
        if (!loading && Logic.dueOnOpen(Sync.checkedAt(this), Sync.now())) refresh();
        else onTick();
        offerWidgetOfNewPlace();
        Updates.checkIfDue(this, () -> runOnUiThread(() -> {
            if (!isFinishing()) render();
        }));
    }

    /** Asked once: the extra stays in the intent when the screen comes back. */
    private void offerWidgetOfNewPlace() {
        String id = getIntent().getStringExtra(EXTRA_OFFER_WIDGET);
        if (id == null) return;
        getIntent().removeExtra(EXTRA_OFFER_WIDGET);
        Logic.Place p = Prefs.place(this, id);
        if (p != null) Widgets.offer(this, p, "Пропустити");
    }

    private View header() {
        LinearLayout r = Ui.row(this);
        r.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
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
        name.setContentDescription(getString(R.string.app_name));
        name.setSingleLine(true);
        name.setHorizontallyScrolling(false);
        name.setAutoSizeTextTypeUniformWithConfiguration(16, 18, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        t.addView(name);
        sub = Ui.text(this, 13, Ui.body(this), pal.muted);
        sub.setPadding(0, Ui.dp(this, 3), 0, 0);
        t.addView(sub);
        r.addView(t, weight());
        addWidget = iconButton(R.drawable.ic_widget, "Додати віджет на головний екран", v -> {
            List<Logic.Place> places = Prefs.places(this);
            if (!places.isEmpty()) Widgets.offer(this, current(places));
        });
        LinearLayout.LayoutParams wl = new LinearLayout.LayoutParams(Ui.dp(this, HERO_BUTTON), Ui.dp(this, HERO_BUTTON));
        wl.rightMargin = Ui.dp(this, 8);
        r.addView(addWidget, wl);
        r.addView(iconButton(R.drawable.ic_gear, "Налаштування", v -> startActivity(new Intent(this, SettingsActivity.class))),
                new LinearLayout.LayoutParams(Ui.dp(this, HERO_BUTTON), Ui.dp(this, HERO_BUTTON)));
        android.graphics.Paint title = new android.graphics.Paint(name.getPaint());
        title.setTextSize(Ui.sp(this, 16));
        float titleRoom = Ui.px(this, getResources().getConfiguration().screenWidthDp - 32f - 38 - 20 - 2 * HERO_BUTTON - 8);
        if (title.measureText(name.getText().toString()) > titleRoom) {
            // Give large text the full width instead of trapping it between three icons.
            r.removeView(t); t.removeView(name); t.removeView(sub);
            View space = new View(this);
            r.addView(space, 1, new LinearLayout.LayoutParams(0, 1, 1));
            LinearLayout header = Ui.column(this);
            header.addView(r, fill()); header.addView(name, fill()); header.addView(sub, fill());
            return header;
        }
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
                if (!ok) Toast.makeText(this, "Не вдалося оновити графік", Toast.LENGTH_SHORT).show();
                onTick();
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

    private Logic.Now currentNow() {
        List<Logic.Place> places=Prefs.places(this);
        if (places.isEmpty()) return new Logic.Now();
        Logic.Place p=current(places);
        Logic.Data d=Store.data(this,p.region);
        long now=Sync.now();
        return Logic.nowWithHistory(Logic.timeline(d,p.group,Logic.today(now)),Logic.today(now),now,
                d!=null && d.none,Prefs.knownHistory(this,p,d));
    }

    /**
     * The screen depends on the minute, the cached files, the places and their settings: when
     * none of these changed since the last drawing, nothing is rebuilt.
     */
    private String state() {
        StringBuilder b = new StringBuilder().append(Logic.today(Sync.now())).append(Logic.index(Logic.today(Sync.now()),Sync.now())).append(loading).append(Prefs.current(this));
        for (Logic.Place p : Prefs.places(this)) {
            Logic.Data d = Store.data(this, p.region);
            Logic.Now n = Logic.now(Logic.timeline(d, p.group, Logic.today(Sync.now())), Logic.today(Sync.now()), Sync.now(), d != null && d.none);
            b.append('|').append(Logic.highlight(n, Prefs.alerts(this, p.id), Sync.now()));
            b.append('|').append(p.id).append(p.name).append(p.region).append(p.group).append(Store.checkedAt(this, p.region))
                    .append(System.identityHashCode(d))
                    .append(Logic.alertsJson(Prefs.alerts(this, p.id)));
        }
        Logic.Now n = currentNow();
        b.append(n.state).append(n.soon).append(n.change);
        Logic.Place selected = current(Prefs.places(this));
        b.append(Logic.highlight(n,Prefs.alerts(this,selected.id),Sync.now()));
        android.app.NotificationManager nm = getSystemService(android.app.NotificationManager.class);
        b.append(nm.areNotificationsEnabled());
        Logic.Release r = Updates.offered(this);
        return b.append(r == null ? "" : r.version).toString();
    }

    @Override
    void onTick() {
        if (entering || Prefs.places(this).isEmpty()) return;
        if (!state().equals(shown)) {
            int y=scroll.getScrollY(); render(); scroll.post(() -> scroll.scrollTo(0,y));
        } else for (Runnable update : clockUpdates) update.run();
    }

    @Override
    void render() {
        if (body == null) return;
        List<Logic.Place> places = Prefs.places(this);
        if (places.isEmpty()) return;
        shown = state();
        tips.hide();  // its views go with the page
        long now = Sync.now();
        LocalDate base = Logic.today(now);
        sub.setText(loading ? "оновлюю графік…" : Ui.date(base) + " · за графіком");
        Logic.Place p = current(places);
        Logic.Data d = Store.data(this, p.region);
        String tl = Logic.timeline(d, p.group, base);
        int cur = Logic.index(base, now);
        float nowRel = cur + (Logic.z(now).getMinute() % 30) / 30f;

        clockUpdates.clear();
        body.removeAllViews();
        Logic.Release update = Updates.offered(this);
        if (update != null) body.addView(updateCard(update), gap(8));
        boolean many = places.size() > 1;
        if (many) body.addView(tiles(places, p, base, now), gap(16));
        body.addView(hero(p, d, tl, base, now), gap(6));
        View stale = stale(p, d, now);
        if (stale != null) body.addView(stale, gap(8));
        body.addView(schedule(p, d, base, cur, nowRel), gap(8));
        body.addView(actions(p), gap(6));
        View blocked = blockedReminders(p); if (blocked != null) body.addView(blocked, gap(6));
        body.addView(footer(p, d, now), fill());

        // always offered, also beside an existing widget: this is where the other size is added
        boolean offer = android.appwidget.AppWidgetManager.getInstance(this).isRequestPinAppWidgetSupported();
        addWidget.setVisibility(offer ? View.VISIBLE : View.GONE);
        addWidget.setContentDescription("Додати віджет «" + p.name + "» на головний екран");
    }

    /** A newer version on GitHub: what it is, and two ways on. The user decides; "later" hides it. */
    private String downloadText() {
        return "Завантажую… " + percent + " %";
    }

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
        text.setText("У вас " + BuildConfig.VERSION_NAME + ". Місця й налаштування збережуться після оновлення.");
        text.setPadding(0, Ui.dp(this, 8), 0, 0);
        card.addView(text, fill());

        FlowRow buttons = new FlowRow(this, Ui.dp(this, 8), Ui.dp(this, 8));
        TextView get = Ui.text(this, 15, Ui.heavy(this), pal.accentInk);
        get.setText(r.version.equals(downloading) ? downloadText() : "Оновити");
        get.setGravity(Gravity.CENTER);
        get.setMinHeight(Ui.dp(this, 46));
        get.setPadding(Ui.dp(this, 18), 0, Ui.dp(this, 18), 0);
        get.setBackground(Ui.round(pal.accent, Ui.px(this, 100)));
        get.setOnClickListener(v -> {
            if (downloading != null) return;
            downloading = r.version;
            percent = 0;
            get.setText(downloadText());
            Updates.get(this, r, p -> {
                percent = p;
                if (getButton != null) getButton.setText(downloadText());
            }, () -> {
                downloading = null;
                render();
            });
        });
        getButton = get;
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

    /** Keep two readable columns while the actual system-scaled clock and end time fit. */
    private boolean stacked() {
        android.graphics.Paint clock = new android.graphics.Paint();
        clock.setTypeface(Ui.display(this));
        clock.setTextSize(Ui.sp(this, 18));
        android.graphics.Paint small = new android.graphics.Paint();
        small.setTypeface(Ui.bold(this));
        small.setTextSize(Ui.sp(this, 13));
        float needed = Math.max(clock.measureText("з 24:00"),
                clock.measureText("24:00") + Ui.px(this, 6) + small.measureText("до 24:00"));
        float room = Ui.px(this, (getResources().getConfiguration().screenWidthDp - 54) / 2f - 14);
        return needed > room;
    }

    private View tiles(List<Logic.Place> places, Logic.Place chosen, LocalDate base, long now) {
        LinearLayout grid = Ui.column(this);
        int columns = stacked() ? 1 : 2;
        LinearLayout row = null;
        for (int i = 0; i < places.size(); i++) {
            if (i % columns == 0) {
                row = Ui.row(this);
                row.setBaselineAligned(false);
                LinearLayout.LayoutParams rl = fill();
                if (i > 0) rl.topMargin = Ui.dp(this, 6);
                grid.addView(row, rl);
            }
            LinearLayout.LayoutParams tl = columns == 2 ? new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f) : weight();  // one height in a row
            if (i % columns != 0) tl.leftMargin = Ui.dp(this, 6);
            row.addView(tile(places.get(i), places.get(i).id.equals(chosen.id), base, now), tl);
        }
        if (columns == 2 && places.size() % 2 == 1) {
            // MATCH_PARENT too: one wrap-content child makes a row of match-parent tiles measure as zero high
            LinearLayout.LayoutParams empty = new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
            empty.leftMargin = Ui.dp(this, 6);  // so the lone tile is as wide as the others
            row.addView(new View(this), empty);
        }
        return grid;
    }

    private View tile(Logic.Place p, boolean chosen, LocalDate base, long now) {
        Logic.Data d = Store.data(this, p.region);
        Logic.Now n = Logic.now(Logic.timeline(d, p.group, base), base, now, d != null && d.none);
        if (d == null) { n.title = loading ? "Завантажую" : "Графіка немає"; n.big = ""; }
        LinearLayout t = Ui.row(this);
        t.setMinimumHeight(Ui.dp(this, 56));
        int pad = Ui.dp(this, chosen ? 5 : 6);
        t.setPadding(pad, pad, Ui.dp(this, 3), pad);  // the lit line of a coming change needs the room
        BulbView bulb = new BulbView(this, pal);
        bulb.still(); bulb.set(n.state);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Ui.dp(this, 30), Ui.dp(this, 30));
        bl.rightMargin = Ui.dp(this, 4);
        t.addView(bulb, bl);
        LinearLayout words = Ui.column(this);
        boolean named = !Logic.autoNamed(p);
        String day = Logic.day(d, p.group, base);
        words.addView(tileName(p, chosen && named, Logic.dayPercent(day)), fill());
        // the same rule as the big card: lit as soon as the notifications of this place would speak
        boolean soon = Logic.highlight(n, Prefs.alerts(this, p.id), now);
        TextView status = Ui.text(this, 14, Ui.bold(this), soon ? chipInk(n.next) : pal.status(n.state));
        status.setText(Logic.tileLine(n));
        LinearLayout.LayoutParams sl = wrap();
        sl.topMargin = Ui.dp(this, 2);
        if (soon) {
            status.setPadding(Ui.dp(this, 4), Ui.dp(this, 1), Ui.dp(this, 4), Ui.dp(this, 1));
            status.setBackground(Ui.round(pal.fill(n.next), Ui.px(this, 7)));
        }
        words.addView(status, sl);
        t.addView(words, weight());
        android.widget.FrameLayout card = new android.widget.FrameLayout(this);
        android.graphics.drawable.GradientDrawable bg = Ui.round(pal.soft(n.state), Ui.px(this, 15));
        int edge = Ui.dp(this, chosen ? 2 : 1);
        bg.setStroke(edge, chosen ? pal.accentText : pal.line);
        card.setBackground(bg);
        card.addView(t, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        card.setContentDescription((named ? p.name + ", черга " + Logic.queue(p.group) : p.name) + ": " + n.title
                + (n.big.isEmpty() ? "" : ", " + n.big) + (soon ? ", скоро зміна" : "") + (chosen ? ", вибрано" : ", показати графік"));
        card.setOnClickListener(v -> { Prefs.setCurrent(this, p.id); render(); scroll.smoothScrollTo(0, 0); });
        return card;
    }

    /** The large widget's two halves: what is now and since when, what comes and when; and how long each. */
    private View tileName(Logic.Place p, boolean withQueue, String percent) {
        FlowRow name = new FlowRow(this, Ui.dp(this, 5), Ui.dp(this, 2));
        TextView label = Ui.text(this, 14, Ui.heavy(this), pal.text);
        label.setText(p.name);
        name.addView(label);
        if (withQueue) {
            TextView queue = Ui.text(this, 13, Ui.heavy(this), pal.accentText);
            queue.setText(Logic.queue(p.group));
            queue.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
            queue.setBackground(Ui.round(Ui.blend(pal.card, Ui.fade(pal.accent, .18f)), Ui.px(this, 6)));
            name.addView(queue);
        }
        LinearLayout row = Ui.row(this);
        row.addView(name, weight());
        TextView share = Ui.text(this, 11, Ui.heavy(this), pal.muted);
        share.setText(percent);
        share.setSingleLine(true);
        share.setPadding(Ui.dp(this, 3), 0, 0, 0);
        share.setContentDescription("Очікується світла сьогодні: " + percent);
        row.addView(share, wrap());
        return row;
    }

    private View hero(Logic.Place p, Logic.Data d, String tl, LocalDate base, long now) {
        Logic.Now n = Logic.nowWithHistory(tl, base, now, d != null && d.none, Prefs.knownHistory(this, p, d));
        boolean known = d != null;
        if (!known) {
            n.title = loading ? "Завантажую графік" : "Графіка немає"; n.big = "";
            n.hint = "Інтернет потрібен для першого завантаження"; n.hintIn = "";
        }
        String[] start = Logic.widgetStart(n);
        String[] next = known ? Logic.heroFuture(n, Logic.lastScheduleDay(d)) : new String[] {"", n.hint, ""};
        int right = Logic.widgetRightState(n, next[1]);
        LinearLayout card = Ui.column(this);
        android.graphics.drawable.GradientDrawable glow = new android.graphics.drawable.GradientDrawable(
                android.graphics.drawable.GradientDrawable.Orientation.LEFT_RIGHT, glowColours(pal.soft(n.state), pal.soft(right)));
        glow.setCornerRadius(Ui.px(this, 18));
        card.setBackground(glow);
        card.setPadding(Ui.dp(this, 11), Ui.dp(this, 7), Ui.dp(this, 11), Ui.dp(this, 9));
        TextView place = Ui.text(this, 13, Ui.heavy(this), pal.text);
        place.setText(Logic.autoNamed(p) ? where(p) : p.name + " · " + where(p));
        place.setPadding(0, 0, 0, Ui.dp(this, 4));
        card.addView(place, fill());
        boolean lit = Logic.highlight(n, Prefs.alerts(this, p.id), now);
        float clock = clockSize(start, next);
        LinearLayout halves = stacked() ? Ui.column(this) : Ui.row(this);
        halves.setBaselineAligned(false);
        halves.addView(nowHalf(n, start, clock), halfParams());
        halves.addView(nextHalf(n, next, clock, lit), halfParams());
        card.addView(halves, fill());
        String detail = Logic.hintIsEvent(n) ? Logic.nextDetail(n) : "";
        card.setContentDescription(Logic.describeNow(n, detail));
        clockUpdates.add(() -> {
            Logic.Now live = currentNow();
            card.setContentDescription(Logic.describeNow(live, Logic.hintIsEvent(live) ? Logic.nextDetail(live) : ""));
        });
        return card;
    }

    private LinearLayout.LayoutParams halfParams() {
        return stacked() ? fill() : new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f);
    }

    /** The same colours across the same width as on the widget. */
    private int[] glowColours(int left, int right) {
        int[] colours = new int[17];
        for (int i = 0; i < colours.length; i++) colours[i] = Logic.widgetGlow(left, right, pal.card, .5f, i / 16f);
        return colours;
    }

    /** Both clocks of the card stand at one size: the one at which the fuller half still fits. */
    private float clockSize(String[] start, String[] next) {
        // 14 dp: the padding of the lit half, the widest one, so the clock keeps its size when the half lights up
        float room = Ui.px(this, (getResources().getConfiguration().screenWidthDp - 2 * 16 - 2 * 11) / 2f - 14);
        android.graphics.Paint clock = new android.graphics.Paint();
        clock.setTypeface(Ui.display(this));
        clock.setTextSize(Ui.sp(this, HERO_CLOCK));
        android.graphics.Paint small = new android.graphics.Paint();
        small.setTypeface(Ui.bold(this));
        small.setTextSize(Ui.sp(this, 13));
        float gap = Ui.px(this, 6);
        float fit = 1f;
        if (start[0].contains(":")) fit = Math.min(fit, (room - (start[1].isEmpty() ? 0 : small.measureText(start[1]) + gap)) / clock.measureText(start[0]));
        if (next[1].contains(":")) fit = Math.min(fit, (room - (next[2].isEmpty() ? 0 : small.measureText(next[2]) + gap)) / clock.measureText(next[1]));
        return stacked() ? HERO_CLOCK : Math.max(18, HERO_CLOCK * fit);
    }

    private TextView word(String text, int ink) {
        TextView t = new TextView(this) {
            @Override protected void onMeasure(int width, int height) {
                float room = MeasureSpec.getMode(width) == MeasureSpec.UNSPECIFIED ? Float.MAX_VALUE
                        : MeasureSpec.getSize(width) - getCompoundPaddingLeft() - getCompoundPaddingRight();
                String fitted = Logic.fittedState(text, getPaint().measureText(text), room);
                if (!fitted.contentEquals(getText())) setText(fitted);
                super.onMeasure(width, height);
            }
        };
        t.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 20);
        t.setTypeface(Ui.heavy(this));
        t.setTextColor(ink);
        t.setIncludeFontPadding(false);
        t.setText(text);
        if (stacked()) return t;  // a whole row to itself: a long word wraps rather than shrinks
        t.setSingleLine(true);
        t.setHorizontallyScrolling(false);  // autosize must use the column width, not an unlimited scroll area
        if (!text.equals("Світла немає")) t.setAutoSizeTextTypeUniformWithConfiguration(11, 20, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        return t;
    }

    /** A clock in the display face with its small words beside it; words that are no clock stand plainly. */
    private View clockLine(String value, String beside, float size, int ink, int sideInk) {
        boolean isClock = value.contains(":");
        LinearLayout line = new LinearLayout(this) {
            @Override protected void onMeasure(int width, int height) {
                int needed = 0;
                for (int i = 0; i < getChildCount(); i++) {
                    View child = getChildAt(i);
                    child.measure(MeasureSpec.UNSPECIFIED, MeasureSpec.UNSPECIFIED);
                    needed += child.getMeasuredWidth();
                }
                boolean wrap = MeasureSpec.getMode(width) != MeasureSpec.UNSPECIFIED
                        && needed > MeasureSpec.getSize(width);
                setOrientation(wrap ? VERTICAL : HORIZONTAL);
                super.onMeasure(width, height);
            }
        };
        line.setGravity(Gravity.BOTTOM);
        TextView big = Ui.text(this, isClock ? size : 15, isClock ? Ui.display(this) : Ui.bold(this), ink);
        big.setText(value);
        line.addView(big, isClock ? wrap() : weight());
        if (!beside.isEmpty()) {
            TextView side = Ui.text(this, 13, Ui.bold(this), sideInk);
            side.setText(beside);
            side.setSingleLine(true);
            side.setPadding(Ui.dp(this, 6), 0, 0, Ui.dp(this, 3));
            line.addView(side, wrap());
        }
        return line;
    }

    private View nowHalf(Logic.Now n, String[] start, float clock) {
        LinearLayout half = Ui.column(this);
        half.setPadding(0, 0, Ui.dp(this, 8), 0);
        LinearLayout head = Ui.row(this);
        head.setMinimumHeight(Ui.dp(this, 26));
        BulbView bulb = new BulbView(this, pal);
        bulb.bare(); bulb.still(); bulb.set(n.state);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Ui.dp(this, 25), Ui.dp(this, 25));
        bl.rightMargin = Ui.dp(this, 5);
        head.addView(bulb, bl);
        head.addView(word(n.title, pal.status(n.state)), weight());
        half.addView(head, fill());
        half.addView(clockLine(start[0], start[1], clock, pal.text, pal.muted), fill());
        TextView ago = Ui.text(this, 13.5f, Ui.body(this), pal.muted);
        ago.setText(n.already);
        ago.setPadding(0, Ui.dp(this, 2), 0, 0);
        clockUpdates.add(() -> ago.setText(currentNow().already));
        half.addView(ago, fill());
        return half;
    }

    private View nextHalf(Logic.Now n, String[] next, float clock, boolean lit) {
        LinearLayout half = Ui.column(this);
        int ink = n.next == Logic.UNKNOWN ? pal.muted : pal.status(n.next);
        int text = pal.text;
        int side = pal.muted;
        if (lit) {
            ink = chipInk(n.next);
            text = ink;
            side = ink;
            half.setBackground(Ui.round(pal.fill(n.next), Ui.px(this, 14)));
        } else if (!stacked()) {
            half.setBackground(leftLine());
        }
        half.setPadding(Ui.dp(this, 9), stacked() ? Ui.dp(this, 8) : 0, Ui.dp(this, 5), 0);
        if (!next[0].isEmpty()) {
            LinearLayout head = Ui.row(this);
            head.setMinimumHeight(Ui.dp(this, 26));
            head.addView(word(next[0], ink), weight());
            half.addView(head, fill());
        }
        half.addView(clockLine(next[1], next[2], clock, ink, side), fill());
        TextView left = Ui.text(this, 13.5f, Ui.bold(this), text);
        left.setText(Logic.heroCountdown(n));
        left.setPadding(0, Ui.dp(this, 2), 0, 0);
        clockUpdates.add(() -> left.setText(Logic.heroCountdown(currentNow())));
        half.addView(left, fill());
        return half;
    }

    /** The line between the halves, as on the widget. */
    private android.graphics.drawable.Drawable leftLine() {
        android.graphics.drawable.GradientDrawable line = new android.graphics.drawable.GradientDrawable();
        line.setColor(Ui.fade(pal.text, .16f));
        line.setSize(Ui.dp(this, 1), 1);
        android.graphics.drawable.LayerDrawable layer = new android.graphics.drawable.LayerDrawable(new android.graphics.drawable.Drawable[] {line});
        layer.setLayerGravity(0, Gravity.START | Gravity.FILL_VERTICAL);
        return layer;
    }

    private View actions(Logic.Place p) {
        LinearLayout row = stacked() ? Ui.column(this) : Ui.row(this);
        Logic.Alerts wanted = Prefs.alerts(this, p.id);
        long now = Sync.now();
        TextView alerts = action(Logic.alertsLabel(wanted), alertsAnswer(p, wanted, now), wanted.any() ? R.drawable.ic_bell : R.drawable.ic_bell_off, v -> AlertsSheet.show(this, p, this::render));
        if (!wanted.any()) quiet(alerts);
        alerts.setContentDescription("Сповіщення, " + p.name + ": " + Logic.alertsSummary(wanted));
        int share = Logic.statShare(Logic.window(Prefs.history(this, p.id), Logic.today(now), 7), Logic.ON);
        TextView stats = action("Статистика", share < 0 ? "" : share + " % за тиждень", R.drawable.ic_stats, v -> startActivity(new Intent(this, StatsActivity.class)));
        row.addView(alerts, stacked() ? fill() : weight());
        LinearLayout.LayoutParams lp = stacked() ? fill() : weight();
        if (stacked()) lp.topMargin = Ui.dp(this, 6); else lp.leftMargin = Ui.dp(this, 6);
        row.addView(stats, lp);
        return row;
    }

    private String alertsAnswer(Logic.Place p, Logic.Alerts wanted, long now) {
        if (!wanted.any()) return "";
        Logic.Event next = Sync.nextAlert(this, p.id, now);
        return next == null ? "" : Logic.alertButtonLine(next, now);
    }

    /** A button for something switched off: the same button, its words and icon plainly grey. */
    private void quiet(TextView button) {
        button.setTextColor(pal.quiet);
        android.graphics.drawable.Drawable image = button.getCompoundDrawables()[0];
        if (image != null) image.setTint(pal.quiet);
    }

    private View blockedReminders(Logic.Place p) {
        Logic.Alerts wanted = Prefs.alerts(this,p.id);
        android.app.NotificationManager nm = getSystemService(android.app.NotificationManager.class);
        android.app.NotificationChannel ch = nm.getNotificationChannel(Sync.CH_REMIND);
        android.app.NotificationChannel quiet = nm.getNotificationChannel(Sync.CH_QUIET);
        boolean channelBlocked = (wanted.off || wanted.on) && ch != null && ch.getImportance() == 0;
        boolean quietBlocked = Prefs.quietNights(this) && quiet != null && quiet.getImportance() == 0;
        android.app.NotificationChannel schedule = nm.getNotificationChannel(Sync.CH_SCHEDULE);
        boolean scheduleBlocked = (wanted.tomorrow || wanted.changes) && schedule != null && schedule.getImportance()==0;
        if (!wanted.any() || (nm.areNotificationsEnabled() && !channelBlocked && !quietBlocked && !scheduleBlocked)) return null;
        TextView warning = Ui.text(this,14,Ui.heavy(this),pal.offText);
        warning.setText("Сповіщення не прийдуть · Увімкнути ›"); warning.setMinHeight(Ui.dp(this,48));
        warning.setOnClickListener(v -> startActivity(new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName())));
        return warning;
    }

    /** The second line answers before the button is touched; without an answer there is none. */
    private TextView action(String text, String answer, int icon, View.OnClickListener listener) {
        TextView b = new ActionButton(this);
        b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14); b.setTypeface(Ui.heavy(this)); b.setTextColor(pal.text); b.setIncludeFontPadding(false);
        android.text.SpannableString words = new android.text.SpannableString(answer.isEmpty() ? text : text + "\n" + answer);
        if (!answer.isEmpty()) {
            words.setSpan(new android.text.style.RelativeSizeSpan(.89f), text.length(), words.length(), 0);
            words.setSpan(new android.text.style.ForegroundColorSpan(pal.muted), text.length(), words.length(), 0);
        }
        b.setText(words); b.setMinHeight(Ui.dp(this, 48));
        b.setPadding(Ui.dp(this, 7), Ui.dp(this, 6), Ui.dp(this, 7), Ui.dp(this, 6));
        b.setBackground(Ui.card(this, pal, 0));
        android.graphics.drawable.Drawable image = getDrawable(icon);
        if (image != null) { image = image.mutate(); image.setTint(pal.text); image.setBounds(0, 0, Ui.dp(this, 18), Ui.dp(this, 18)); }
        b.setCompoundDrawables(image, null, null, null);
        b.setCompoundDrawablePadding(Ui.dp(this, 7));
        b.setOnClickListener(listener);
        return b;
    }

    /** A line when the source has not answered for a while. */
    private View stale(Logic.Place p, Logic.Data d, long now) {
        long checked = Store.checkedAt(this, p.region);
        if (d == null || (d.sourceOk && checked > 0 && now - checked <= Logic.STALE_S)) return null;
        TextView t = Ui.text(this, 14, Ui.bold(this), pal.text);
        t.setText("Немає зв'язку з джерелом графіка. Показано дані, перевірені "
                + Logic.updated(checked, now).replace("графік опубліковано ", "") + ".");
        t.setLineSpacing(0, 1.1f);
        t.setPadding(Ui.dp(this, 18), Ui.dp(this, 14), Ui.dp(this, 18), Ui.dp(this, 14));
        t.setBackground(Ui.outline(pal.maybeText, Ui.px(this, 22), Ui.dp(this, 1)));
        return t;
    }

    private View schedule(Logic.Place p, Logic.Data d, LocalDate base, int cur, float nowRel) {
        LinearLayout card = Ui.column(this);
        card.setPadding(Ui.dp(this, 11), Ui.dp(this, 2), Ui.dp(this, 11), Ui.dp(this, 3));
        card.setBackground(Ui.card(this, pal, 0));
        card.setOnClickListener(v -> tips.hide());  // a touch beside the bars takes the hint away
        day(card, p, base, "Сьогодні", Logic.day(d, p.group, base), cur, nowRel);
        LinearLayout.LayoutParams line = fill(); line.height = Ui.dp(this, 1); line.topMargin = Ui.dp(this, 2);
        card.addView(divider(), line);
        LocalDate tomorrow = base.plusDays(1);
        day(card, p, tomorrow, "Завтра, " + tomorrow.getDayOfMonth() + "." + tomorrow.getMonthValue(),
                Logic.day(d, p.group, tomorrow), -1, -1);
        return card;
    }

    /** One section: the day and how much light it has, its bar, the times of its changes. */
    private void day(LinearLayout card, Logic.Place p, LocalDate date, String title, String slots, int cur, float nowRel) {
        boolean tomorrow = cur < 0;
        String noSchedule = tomorrow ? "графік ще не опубліковано" : "";
        ImageView share = null;
        if (slots != null) {
            share = iconButton(R.drawable.ic_share, "Поділитися графіком: " + title, v -> Share.send(this, p, date));
            share.setBackground(null);
        }
        DayHeader head = new DayHeader(this, pal, title, slots != null ? Logic.daySummaries(slots)
                : java.util.Collections.singletonList(noSchedule), slots != null ? pal.onText : pal.muted, share);
        head.setContentDescription(Logic.describe(title, slots));
        android.widget.FrameLayout section = new android.widget.FrameLayout(this);
        LinearLayout inner = Ui.column(this);
        inner.addView(head, fill());
        section.addView(inner, new android.widget.FrameLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        card.addView(section, fill());
        if (slots == null) {
            if (tomorrow) return;
            TextView none = Ui.text(this, 14, Ui.body(this), pal.maybeText);
            none.setText(notPublished(p));
            none.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 8));
            inner.addView(none, fill());
            return;
        }
        BarView bar = new BarView(this, pal); bar.set(slots, tomorrow ? -1 : nowRel);
        if (!tomorrow) clockUpdates.add(() -> {
            long time=Sync.now();bar.set(slots,Logic.index(date,time)+(Logic.z(time).getMinute()%30)/30f);
        });
        bar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        bar.setOnClickListener(v -> tips.touched(section, bar, slots));
        inner.addView(bar, fill());
    }

    private String notPublished(Logic.Place p) {
        return Logic.missingSchedule(Store.data(this, p.region), p.group);
    }

    /** Words on the fill of a state. */
    private int chipInk(int state) {
        return state == Logic.MAYBE ? pal.accentInk : pal.ink;
    }

    private View footer(Logic.Place p, Logic.Data d, long now) {
        LinearLayout box = Ui.column(this);
        box.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 6), 0);
        String line = Logic.checkedLine(Store.checkedAt(this, p.region), d != null ? d.updated : 0, now);
        if (stacked()) line = line.replace("Розклад оновлено:", "Оновлено:");
        if (!line.isEmpty()) {
            TextView t = Ui.text(this, 13, Ui.body(this), pal.muted);
            t.setText(line);
            t.setLineSpacing(0, 1.1f);
            android.graphics.drawable.Drawable mark = getDrawable(R.drawable.ic_check);
            if (mark != null) {
                mark = mark.mutate(); mark.setTint(pal.onText); mark.setBounds(0, 0, Ui.dp(this, 15), Ui.dp(this, 15));
                t.setCompoundDrawablesRelative(mark, null, null, null);
                t.setCompoundDrawablePadding(Ui.dp(this, 5));
            }
            box.addView(t, fill());
        }
        TextView help = Ui.text(this, 14, Ui.heavy(this), pal.accentText);
        help.setText("Світла немає, хоча за графіком є? ›");
        help.setGravity(Gravity.CENTER_VERTICAL);
        help.setMinHeight(Ui.dp(this, 44));
        help.setOnClickListener(v -> AlertsSheet.help(this, p));
        box.addView(help, fill());
        return box;
    }

    // First start: notifications (Android 13+), then exemption from battery saving so the
    // widget and notifications keep working on Samsung and Xiaomi.
    private void askNotifications() {
        // asked once: after a refusal the main screen keeps its own line with the way to allow them
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
                && Prefs.once(this, "asked_notifications")) {
            Ui.dialog(this).setTitle("Сповіщення про світло")
                    .setMessage("Дозвольте сповіщення, щоб знати заздалегідь про вимкнення й увімкнення світла.")
                    .setPositiveButton("Продовжити",(dialog,which) -> requestPermissions(new String[] {Manifest.permission.POST_NOTIFICATIONS},1))
                    .setNegativeButton("Пізніше",(dialog,which) -> askBattery()).show();
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
            Ui.dialog(this).setTitle("Вчасні сповіщення")
                    .setMessage("Дозвольте фонову роботу без обмежень батареї, щоб сповіщення могли приходити із вимкненим екраном.")
                    .setPositiveButton("Відкрити налаштування",(dialog,which) -> {
                        try { startActivity(new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                                Uri.parse("package:"+getPackageName()))); }
                        catch (RuntimeException ignored) { /* Some devices hide this screen. */ }
                    }).setNegativeButton("Пізніше",null).show();
        } catch (Exception ignored) {
            // some builds hide the dialog; notifications then may come a little late
        }
    }
}
