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
    protected void onPause() {
        Sync.onChanged(null);
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (body == null) return;
        Sync.onChanged(() -> runOnUiThread(this::onTick));
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
        if (getResources().getConfiguration().fontScale > 1.25f) {
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
        if (Prefs.places(this).isEmpty()) return;
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
        if (many) body.addView(tiles(places, p, base, now), gap(6));
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

    /** Reflow rather than shrink readable text on a small screen or a larger system font. */
    private boolean stacked() {
        return getResources().getConfiguration().screenWidthDp < 360
                || getResources().getConfiguration().fontScale > 1.25f;
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
        t.setMinimumHeight(Ui.dp(this, 60));
        int pad = Ui.dp(this, chosen ? 7 : 8);
        t.setPadding(pad, pad, Ui.dp(this, 3), pad);  // the lit line of a coming change needs the room
        android.graphics.drawable.GradientDrawable bg = Ui.round(pal.soft(n.state), Ui.px(this, 15));
        bg.setStroke(Ui.dp(this, chosen ? 2 : 1), chosen ? pal.accentText : pal.line);
        t.setBackground(bg);
        BulbView bulb = new BulbView(this, pal);
        bulb.still(); bulb.set(n.state);
        LinearLayout.LayoutParams bl = new LinearLayout.LayoutParams(Ui.dp(this, 30), Ui.dp(this, 30));
        bl.rightMargin = Ui.dp(this, 4);
        t.addView(bulb, bl);
        LinearLayout words = Ui.column(this);
        FlowRow name = new FlowRow(this, Ui.dp(this, 5), Ui.dp(this, 2));
        TextView label = Ui.text(this, 14, Ui.heavy(this), pal.text);
        label.setText(p.name);
        name.addView(label);
        if (chosen) {
            TextView queue = Ui.text(this, 13, Ui.heavy(this), pal.accentText);
            queue.setText(Logic.queue(p.group));
            queue.setPadding(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
            queue.setBackground(Ui.round(Ui.blend(pal.card, Ui.fade(pal.accent, .18f)), Ui.px(this, 6)));
            name.addView(queue);
        }
        words.addView(name, fill());
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
        t.setContentDescription(p.name + ", черга " + Logic.queue(p.group) + ": " + n.title
                + (n.big.isEmpty() ? "" : ", " + n.big) + (soon ? ", скоро зміна" : "") + (chosen ? ", вибрано" : ", показати графік"));
        t.setOnClickListener(v -> { Prefs.setCurrent(this, p.id); render(); scroll.smoothScrollTo(0, 0); });
        return t;
    }

    private View hero(Logic.Place p, Logic.Data d, String tl, LocalDate base, long now) {
        Logic.Now n = Logic.nowWithHistory(tl, base, now, d != null && d.none, Prefs.knownHistory(this, p, d));
        if (d == null) {
            n.title = loading ? "Завантажую графік" : "Графіка немає"; n.big = "";
            n.hint = "Інтернет потрібен для першого завантаження"; n.hintIn = "";
        }
        LinearLayout card = Ui.column(this);
        card.setBackground(Ui.round(pal.soft(n.state), Ui.px(this, 18)));
        TextView queue = Ui.text(this, 13, Ui.heavy(this), pal.accentText);
        queue.setText(p.name + " · " + where(p));
        queue.setPadding(Ui.dp(this, 11), Ui.dp(this, 7), Ui.dp(this, 11), 0);
        card.addView(queue, fill());
        card.addView(present(n), fill());
        if (Logic.hintIsEvent(n)) {
            String detail = Logic.nextDetail(n);
            card.addView(nextEvent(n, detail, Logic.highlight(n, Prefs.alerts(this, p.id), now)), fill());
            card.setContentDescription(Logic.describeNow(n, detail));
            clockUpdates.add(() -> { Logic.Now live=currentNow(); card.setContentDescription(Logic.describeNow(live,Logic.nextDetail(live))); });
            return card;
        }
        if (d == null) {  // no event: the schedule below already says why, so the card stays compact; only this needs words
            TextView note = Ui.text(this, 14, Ui.body(this), pal.muted);
            note.setText(n.hint);
            note.setPadding(Ui.dp(this, 11), 0, Ui.dp(this, 11), Ui.dp(this, 10));
            card.addView(note, fill());
        }
        card.setContentDescription(Logic.describeNow(n, ""));
        clockUpdates.add(() -> card.setContentDescription(Logic.describeNow(currentNow(),"")));
        return card;
    }

    /** What is scheduled now and since when: one row, or two when the words do not fit beside it. */
    private View present(Logic.Now n) {
        TextView title = Ui.text(this, 25, Ui.heavy(this), pal.status(n.state));
        title.setText(n.title);
        LinearLayout titleBlock = Ui.row(this);
        BulbView stateIcon = new BulbView(this, pal); stateIcon.still(); stateIcon.set(n.state);
        LinearLayout.LayoutParams si = new LinearLayout.LayoutParams(Ui.dp(this, 28), Ui.dp(this, 28)); si.rightMargin = Ui.dp(this, 6);
        titleBlock.addView(stateIcon, si); titleBlock.addView(title, weight());
        TextView since = Ui.text(this, 14, Ui.body(this), pal.muted);
        boolean both = !n.since.isEmpty() && !n.already.isEmpty();
        float available = getResources().getConfiguration().screenWidthDp - 62f;
        float need = Ui.px(this, 34) + title.getPaint().measureText(n.title) + Math.max(since.getPaint().measureText(n.since), since.getPaint().measureText(n.already));
        boolean vertical = stacked() || need > Ui.px(this, available);
        LinearLayout present = vertical ? Ui.column(this) : Ui.row(this);
        present.setPadding(Ui.dp(this, 11), Ui.dp(this, 8), Ui.dp(this, 11), Ui.dp(this, 8));
        present.addView(titleBlock, vertical ? fill() : weight());
        String gap = vertical ? " · " : "\n";
        since.setText(n.since + (both ? gap : "") + n.already);
        clockUpdates.add(() -> {
            Logic.Now live=currentNow();
            since.setText(live.since + (!live.since.isEmpty() && !live.already.isEmpty() ? gap : "") + live.already);
        });
        if (!n.since.isEmpty() || !n.already.isEmpty()) {
            since.setGravity(vertical ? Gravity.START : Gravity.END);
            present.addView(since, vertical ? fill() : wrap());
        }
        return present;
    }

    /** The coming change with its clock; filled with its colour when it is close. */
    private View nextEvent(Logic.Now n, String detail, boolean soon) {
        LinearLayout next = Ui.column(this);
        next.setPadding(Ui.dp(this, 11), Ui.dp(this, 7), Ui.dp(this, 11), Ui.dp(this, 9));
        android.graphics.drawable.GradientDrawable nextBg = Ui.round(soon ? pal.fill(n.next) : pal.soft(n.next), Ui.px(this, 15));
        if (soon) nextBg.setStroke(Ui.dp(this, 2), pal.status(n.next));
        int eventInk = pal.status(n.next);
        if (soon) eventInk = n.next == Logic.MAYBE ? pal.accentInk : pal.ink;
        next.setBackground(nextBg);
        TextView event = Ui.text(this, 26, Ui.heavy(this), eventInk);
        event.setText(n.hint);
        next.addView(event, fill());
        if (!detail.isEmpty()) {
            TextView small = Ui.text(this, 14, Ui.bold(this), soon ? eventInk : pal.text);
            small.setText(detail);
            clockUpdates.add(() -> small.setText(Logic.nextDetail(currentNow())));
            small.setPadding(0, Ui.dp(this, 3), 0, 0);
            next.addView(small, fill());
        }
        return next;
    }

    private View actions(Logic.Place p) {
        LinearLayout row = stacked() ? Ui.column(this) : Ui.row(this);
        Logic.Alerts wanted = Prefs.alerts(this, p.id);
        TextView alerts = action(Logic.alertsLabel(wanted), wanted.any() ? R.drawable.ic_bell : R.drawable.ic_bell_off, v -> AlertsSheet.show(this, p, this::render));
        if (!wanted.any()) quiet(alerts);
        alerts.setContentDescription("Сповіщення, " + p.name + ": " + Logic.alertsSummary(wanted));
        TextView stats = action("Статистика", R.drawable.ic_stats, v -> startActivity(new Intent(this, StatsActivity.class)));
        row.addView(alerts, stacked() ? fill() : weight());
        LinearLayout.LayoutParams lp = stacked() ? fill() : weight();
        if (stacked()) lp.topMargin = Ui.dp(this, 6); else lp.leftMargin = Ui.dp(this, 6);
        row.addView(stats, lp);
        return row;
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

    private TextView action(String text, int icon, View.OnClickListener listener) {
        TextView b = new ActionButton(this);
        b.setTextSize(android.util.TypedValue.COMPLEX_UNIT_SP, 14); b.setTypeface(Ui.heavy(this)); b.setTextColor(pal.text); b.setIncludeFontPadding(false);
        b.setText(text); b.setMinHeight(Ui.dp(this, 48));
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
        card.setPadding(Ui.dp(this, 11), Ui.dp(this, 3), Ui.dp(this, 11), Ui.dp(this, 5));
        card.setBackground(Ui.card(this, pal, 0));
        day(card, p, base, "Сьогодні", Logic.day(d, p.group, base), cur, nowRel);
        LinearLayout.LayoutParams line = fill(); line.height = Ui.dp(this, 1); line.topMargin = Ui.dp(this, 4); line.bottomMargin = Ui.dp(this, 4);
        card.addView(divider(), line);
        LocalDate tomorrow = base.plusDays(1);
        day(card, p, tomorrow, "Завтра, " + tomorrow.getDayOfMonth() + "." + tomorrow.getMonthValue(),
                Logic.day(d, p.group, tomorrow), -1, -1);
        return card;
    }

    private void day(LinearLayout card, Logic.Place p, LocalDate date, String title, String slots, int cur, float nowRel) {
        View head;
        if (slots == null) {
            TextView name = Ui.text(this, 18, Ui.heavy(this), pal.text);
            name.setText(title); name.setMinHeight(Ui.dp(this, 40)); name.setGravity(Gravity.CENTER_VERTICAL);
            head = name;
        } else {
            ImageView share = iconButton(R.drawable.ic_share, "Поділитися графіком: " + title, v -> Share.send(this, p, date));
            share.setBackground(null);
            head = new DayHeader(this, pal, title, slots, share);
        }
        head.setContentDescription(Logic.describe(title, slots)); card.addView(head, fill());
        if (slots == null) {
            TextView none = Ui.text(this, 14, Ui.body(this), pal.maybeText);
            none.setText(notPublished(p, title.startsWith("Завтра")));
            none.setPadding(0, Ui.dp(this, 5), 0, Ui.dp(this, 8));
            card.addView(none, fill()); return;
        }
        BarView bar = new BarView(this, pal); bar.set(slots, cur >= 0 ? nowRel : -1);
        if (cur >= 0) clockUpdates.add(() -> {
            long time=Sync.now();bar.set(slots,Logic.index(date,time)+(Logic.z(time).getMinute()%30)/30f);
        });
        bar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        card.addView(bar, fill());
        List<Logic.Outage> outages = Logic.outages(slots, cur);
        if (!outages.isEmpty()) {
            TextView label = Ui.text(this,14,Ui.bold(this),pal.muted);
            boolean certain = false; for (Logic.Outage o : outages) certain |= !o.maybe;
            label.setText(certain ? "Без світла:" : "Можливі вимкнення:"); card.addView(label,fill());
            LinearLayout.LayoutParams lp = fill(); lp.topMargin = Ui.dp(this, 4); lp.bottomMargin = Ui.dp(this, 5);
            card.addView(windows(outages), lp);
        }
    }

    private String notPublished(Logic.Place p, boolean tomorrow) {
        if (!tomorrow) return Logic.missingSchedule(Store.data(this, p.region)) + ".";
        boolean told = Prefs.alerts(this, p.id).tomorrow;
        return "Графік ще не опубліковано." + (told ? " Сповіщення прийде, щойно він з'явиться." : "");
    }

    /** The windows of a day as an even grid: columns line up however many windows there are. */
    private View windows(List<Logic.Outage> outages) {
        int gap = Ui.dp(this, 6);
        TextView sample = (TextView) chip(outages.get(0));
        sample.setText("00:00–00:00");
        sample.measure(View.MeasureSpec.UNSPECIFIED, View.MeasureSpec.UNSPECIFIED);
        int room = Ui.dp(this, getResources().getConfiguration().screenWidthDp - 2 * 16 - 2 * 11);
        int columns = Logic.windowColumns(room, sample.getMeasuredWidth(), gap);
        LinearLayout grid = Ui.column(this);
        for (int i = 0; i < outages.size(); i += columns) {
            LinearLayout row = Ui.row(this);
            for (int k = 0; k < columns; k++) {
                LinearLayout.LayoutParams cell = weight();
                if (k > 0) cell.leftMargin = gap;
                row.addView(i + k < outages.size() ? chip(outages.get(i + k)) : new View(this), cell);
            }
            LinearLayout.LayoutParams rl = fill();
            if (i > 0) rl.topMargin = Ui.dp(this, 5);
            grid.addView(row, rl);
        }
        return grid;
    }

    /** "06:00–08:00": filled now, tinted later (red, amber for a possible outage), faded when past. */
    private View chip(Logic.Outage o) {
        boolean now = o.when == Logic.Outage.NOW;
        int fill = o.maybe ? pal.maybe : pal.off;
        int ink = o.maybe ? pal.accentInk : pal.ink;
        int text = o.maybe ? pal.maybeText : pal.offText;
        if (!o.maybe && pal.dark) text = 0xFFFFC3CB;  // on the stronger tint below
        TextView c = Ui.text(this, 14, Ui.heavy(this), now ? ink : text);
        c.setText(o.label.replace(" – ", "–"));
        c.setGravity(Gravity.CENTER);
        c.setSingleLine(true);
        c.setPadding(Ui.dp(this, 3), Ui.dp(this, 6), Ui.dp(this, 3), Ui.dp(this, 6));
        int tint = Ui.fade(fill, pal.dark ? 0.40f : 0.24f);
        c.setBackground(Ui.round(now ? fill : tint, Ui.px(this, 12)));
        if (o.when == Logic.Outage.PAST) { c.setTextColor(pal.muted); c.setBackground(Ui.round(pal.raised, Ui.px(this, 9))); }
        String when = "";
        if (o.when == Logic.Outage.PAST) when = ", минуло";
        else if (now) when = ", триває зараз";
        c.setContentDescription((o.maybe ? "можливе вимкнення " : "вимкнення ") + o.label + ", " + o.length + when);
        return c;
    }

    /** Words on the fill of a state. */
    private int chipInk(int state) {
        return state == Logic.MAYBE ? pal.accentInk : pal.ink;
    }

    private View footer(Logic.Place p, Logic.Data d, long now) {
        TextView t = Ui.text(this, 13, Ui.body(this), pal.muted);
        t.setLineSpacing(0, 1.1f);
        List<String> when = new ArrayList<>();
        String updated = d != null && d.updated > 0 ? cap(Logic.updated(d.updated, now)) : "";
        if (!updated.isEmpty()) when.add(updated);
        long checked = Store.checkedAt(this, p.region);
        if (checked > 0) when.add("перевірено о\u00A0" + Logic.hm(checked));
        android.text.SpannableString words = new android.text.SpannableString((when.isEmpty() ? "" : String.join(" · ", when) + ".\n")
                + "Графік обленерго, не фактична наявність світла. Оновлюється автоматично.");
        if (!updated.isEmpty()) words.setSpan(new android.text.style.ForegroundColorSpan(pal.maybeText), 0, updated.length(), 0);
        t.setText(words);
        t.setPadding(Ui.dp(this, 6), 0, Ui.dp(this, 6), 0);
        return t;
    }

    private static String cap(String s) {
        return s.isEmpty() ? s : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // First start: notifications (Android 13+), then exemption from battery saving so the
    // widget and notifications keep working on Samsung and Xiaomi.
    private void askNotifications() {
        if (Build.VERSION.SDK_INT >= 33
                && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            new android.app.AlertDialog.Builder(this).setTitle("Сповіщення про світло")
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
            new android.app.AlertDialog.Builder(this).setTitle("Вчасні сповіщення")
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
