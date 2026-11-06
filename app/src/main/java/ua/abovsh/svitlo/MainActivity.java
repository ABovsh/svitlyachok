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
        FitText name = new FitText(this, 18, 16, 1, Ui.display(this), pal.text).compact(1.05f);
        name.setText("Світлячок");
        name.setContentDescription(getString(R.string.app_name));
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
        title.setTextSize(Ui.compactSp(this, 16, 1.05f));
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

    /** The place grid measures its own cards; optional details in the hero never affect it. */
    private View tiles(List<Logic.Place> places, Logic.Place chosen, LocalDate base, long now) {
        AdaptiveGrid grid = new AdaptiveGrid(this, 2, 6);
        for (Logic.Place place : places) {
            Logic.Data data = Store.data(this, place.region);
            Logic.Now state = Logic.now(Logic.timeline(data, place.group, base), base, now, data != null && data.none);
            if (data == null) { state.title = loading ? "Завантажую" : "Графіка немає"; state.big = ""; }
            boolean soon = Logic.highlight(state, Prefs.alerts(this, place.id), now);
            String dayShare = Logic.dayPercent(Logic.day(data, place.group, base));
            PlaceTile tile = new PlaceTile(this, pal, place, state, place.id.equals(chosen.id), soon, dayShare);
            tile.setOnClickListener(v -> { Prefs.setCurrent(this, place.id); render(); scroll.smoothScrollTo(0, 0); });
            grid.addView(tile);
        }
        return grid;
    }

    private View hero(Logic.Place p, Logic.Data d, String tl, LocalDate base, long now) {
        Logic.Now state = Logic.nowWithHistory(tl, base, now, d != null && d.none, Prefs.knownHistory(this, p, d));
        if (d == null) {
            state.title = loading ? "Завантажую графік" : "Графіка немає"; state.big = "";
            state.hint = "Інтернет потрібен для першого завантаження"; state.hintIn = "";
        }
        String[] next = d == null ? new String[] {"", state.hint, ""} : Logic.heroFuture(state, Logic.lastScheduleDay(d));
        String label = Logic.autoNamed(p) ? where(p) : p.name + " · " + where(p);
        HeroCard card = new HeroCard(this, pal, label, state, next, Logic.highlight(state, Prefs.alerts(this, p.id), now));
        clockUpdates.add(() -> card.update(currentNow()));
        return card;
    }

    private View actions(Logic.Place p) {
        AdaptiveGrid row = new AdaptiveGrid(this, 2, 6);
        Logic.Alerts wanted = Prefs.alerts(this, p.id);
        long now = Sync.now();
        TextView alerts = action(Logic.alertsLabel(wanted), alertsAnswer(p, wanted, now), wanted.any() ? R.drawable.ic_bell : R.drawable.ic_bell_off, v -> AlertsSheet.show(this, p, this::render));
        if (!wanted.any()) quiet(alerts);
        alerts.setContentDescription("Сповіщення, " + p.name + ": " + Logic.alertsSummary(wanted));
        int share = Logic.statShare(Logic.window(Prefs.history(this, p.id), Logic.today(now), 7), Logic.ON);
        TextView stats = action("Статистика", share < 0 ? "" : share + " % за тиждень", R.drawable.ic_stats, v -> startActivity(new Intent(this, StatsActivity.class)));
        alerts.setMinimumWidth(Ui.dp(this, 128)); stats.setMinimumWidth(Ui.dp(this, 128));
        row.addView(alerts); row.addView(stats);
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
        FitText warning = new FitText(this, 14, 13, 1, Ui.heavy(this), pal.offText).compact(1.1f);
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

    private View footer(Logic.Place p, Logic.Data d, long now) {
        LinearLayout box = Ui.column(this);
        box.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 6), 0);
        String line = Logic.checkedLine(Store.checkedAt(this, p.region), d != null ? d.updated : 0, now);
        line = line.replace("Розклад оновлено:", "Оновлено:");
        if (!line.isEmpty()) {
            FitText t = new FitText(this, 13, 12.5f, 1, Ui.body(this), pal.muted).compact(1.1f);
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
        FitText help = new FitText(this, 14, 13, 1, Ui.heavy(this), pal.accentText).compact(1.1f);
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
