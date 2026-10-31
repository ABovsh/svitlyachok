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
import android.widget.FrameLayout;
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

    /** The round buttons of the header, dp. */
    private static final int HERO_BUTTON = 44;
    /** The round buttons of the place card, dp. */
    private static final int PLACE_BUTTON = 34;
    /** The status lines of the place card: the largest size, the size under which a line takes
     *  its shorter form, the least, all sp; and the brackets, as a share of the size. */
    private static final float STATUS_MAX = 24;
    private static final float STATUS_SHORT = 16;
    private static final float STATUS_MIN = 12;
    private static final float STATUS_IN = 0.75f;
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
        if (update != null) body.addView(updateCard(update), gap(8));
        boolean many = places.size() > 1;
        if (many) body.addView(tiles(places, p, base, now), gap(8));
        body.addView(hero(p, d, tl, base, now), gap(8));
        View stale = stale(p, d, now);
        if (stale != null) body.addView(stale, gap(8));
        body.addView(schedule(p, d, base, cur, nowRel), gap(8));
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
        text.setText("У вас " + BuildConfig.VERSION_NAME + ". Встановлюється поверх, місця й налаштування збережуться.");
        text.setPadding(0, Ui.dp(this, 8), 0, 0);
        card.addView(text, fill());

        FlowRow buttons = new FlowRow(this, Ui.dp(this, 8), Ui.dp(this, 8));
        TextView get = Ui.text(this, 15, Ui.heavy(this), pal.accentInk);
        get.setText(r.version.equals(downloading) ? downloadText() : "Завантажити");
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
        t.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
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
        big.setText(n.big.isEmpty() ? " " : n.big.replace(' ', '\u00A0'));  // three lines in every tile: rows stay even
        // "до завтра 12:00" with large fonts: smaller letters, not "до завтра 12…"
        big.setMaxLines(1);
        big.setAutoSizeTextTypeUniformWithConfiguration(8, 13, 1, android.util.TypedValue.COMPLEX_UNIT_SP);
        big.setPadding(0, Ui.dp(this, 2), 0, 0);
        w.addView(big, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT,
                Math.round(big.getTextSize() * 1.35f) + Ui.dp(this, 2)));
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

    /**
     * Three lines: the place with its buttons, what is now and what comes next. The two status
     * lines share the largest size at which both fit, so the card never grows.
     */
    private View hero(Logic.Place p, Logic.Data d, String tl, LocalDate base, long now) {
        Logic.Now n = Logic.now(tl, base, now, d != null && d.none, yesterday(p, d, base));
        if (d == null) {
            n.title = "Завантажую графік";
            n.big = "";
            n.hint = "Інтернет потрібен лише для першого завантаження";
            n.hintIn = "";
        }
        int tint = n.state == Logic.UNKNOWN ? 0 : pal.fill(n.state);
        LinearLayout card = Ui.column(this);
        // the sides are padded per line: the last line fills to 4 dp from the edge when lit
        int pad = Ui.dp(this, 7);
        card.setPadding(Ui.dp(this, 4), Ui.dp(this, 10), Ui.dp(this, 4), Ui.dp(this, 12));
        card.setBackground(Ui.card(this, pal, tint));

        // the place, and at the right its statistics and notifications
        LinearLayout top = Ui.row(this);
        top.setPadding(pad, 0, pad, 0);
        card.addView(top, fill());
        TextView where = Ui.text(this, 19, Ui.heavy(this), pal.text);
        android.text.SpannableStringBuilder w = new android.text.SpannableStringBuilder(p.name);
        int from = w.length();
        w.append(" · черга ").append(Logic.queue(p.group));
        w.setSpan(new android.text.style.ForegroundColorSpan(pal.muted), from, w.length(), 0);
        w.setSpan(new android.text.style.AbsoluteSizeSpan(14, true), from, w.length(), 0);
        w.setSpan(new Ui.Face(Ui.bold(this)), from, w.length(), 0);
        where.setText(w);
        where.setMaxLines(1);
        where.setEllipsize(android.text.TextUtils.TruncateAt.END);
        top.addView(where, weight());
        View stats = iconButton(R.drawable.ic_stats, "Статистика за тиждень і місяць",
                v -> startActivity(new Intent(this, StatsActivity.class)));
        top.addView(small(stats, 6));
        top.addView(small(bell(p), 6));

        Line present = presentLine(n, pad);
        Line next = nextLine(p, n, now, pad);
        LinearLayout.LayoutParams pl = fill();
        pl.topMargin = Ui.dp(this, 4);
        card.addView(present.view, pl);
        LinearLayout.LayoutParams nl = fill();
        nl.topMargin = Ui.dp(this, 2);
        card.addView(next.view, nl);
        present.view.addOnLayoutChangeListener((v, l, t, r, bt, ol, ot, or, ob) -> {
            if (r - l != or - ol) v.post(() -> fit(present, next));
        });
        card.setContentDescription(describe(n));
        return card;
    }

    /** A status line of the place card: its forms from the longest, its colours. */
    private static final class Line {
        TextView view;
        String[][] forms;
        int colour;
        int in;
    }

    /** "Світло є з 14:30 (уже 3 год 08 хв)" in the colour of the state. */
    private Line presentLine(Logic.Now n, int pad) {
        Line l = new Line();
        l.view = status();
        l.view.setPadding(pad, 0, pad, 0);
        l.forms = new String[][] {{Logic.nowLine(n), Logic.nowLineIn(n)}, {Logic.nowLineShort(n), ""}};
        l.colour = n.state == Logic.UNKNOWN ? pal.text : pal.status(n.state);
        l.in = pal.muted;
        return l;
    }

    /**
     * "Вимкнуть о 21:00 (через 1 год 51 хв)" in the colour of what comes; when the reminder time
     * of the place has come, the whole line fills with that colour.
     */
    private Line nextLine(Logic.Place p, Logic.Now n, long now, int pad) {
        Line l = new Line();
        l.view = status();
        l.view.setPadding(pad, 0, pad, 0);
        l.forms = new String[][] {{n.hint, n.hintIn}, {n.hint, ""}};
        l.in = pal.muted;
        if (n.next == Logic.UNKNOWN) {  // nothing changes: "no outages" in green, "not published yet" in grey
            boolean grey = n.hint.startsWith("Графік") || n.state == Logic.UNKNOWN;
            l.colour = grey ? pal.muted : pal.status(n.state);
            return l;
        }
        l.colour = Ui.blend(pal.text, Ui.fade(pal.status(n.next), 0.8f));
        if (n.change > now && n.change - now <= Logic.soonS(Prefs.alerts(this, p.id), n.next)) {
            l.colour = n.next == Logic.MAYBE ? pal.accentInk : pal.ink;
            l.in = Ui.fade(l.colour, 0.85f);
            l.view.setBackground(Ui.round(pal.fill(n.next), Ui.px(this, 11)));
        }
        return l;
    }

    private static String describe(Logic.Now n) {
        return "Зараз за графіком: " + n.title
                + (n.since.isEmpty() ? "" : " " + n.since) + (n.already.isEmpty() ? "" : ", " + n.already)
                + (n.hint.isEmpty() ? "" : ". " + n.hint) + (n.hintIn.isEmpty() ? "" : " " + n.hintIn);
    }

    /** A round button of the place card, a little smaller than those of the header. */
    private View small(View b, int gap) {
        int size = Ui.dp(this, PLACE_BUTTON);
        if (b instanceof ImageView image) {
            image.setScaleType(ImageView.ScaleType.FIT_CENTER);
            int in = Ui.dp(this, 8);
            b.setPadding(in, in, in, in);
        }
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(size, size);
        l.leftMargin = Ui.dp(this, gap);
        b.setLayoutParams(l);
        return b;
    }

    private TextView status() {
        TextView t = Ui.text(this, 20, Ui.heavy(this), pal.text);
        t.setMaxLines(1);
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setIncludeFontPadding(false);
        // the height of the largest size: the card keeps its height whatever the words
        t.setMinHeight(Math.round(Ui.sp(this, STATUS_MAX * 1.45f)));
        return t;
    }

    /**
     * Both status lines at one size: the largest, up to STATUS_MAX sp, at which each fits; a line
     * that would fall under STATUS_SHORT sp takes its shorter form first.
     */
    private void fit(Line a, Line b) {
        TextView v = a.view;
        float width = (float) v.getWidth() - v.getPaddingLeft() - v.getPaddingRight();
        if (width <= 0) return;
        float max = Ui.sp(this, STATUS_MAX);
        float shortAt = Ui.sp(this, STATUS_SHORT);
        android.text.TextPaint tp = new android.text.TextPaint(v.getPaint());
        String[] af = form(tp, a.forms, width, max, shortAt);
        String[] bf = form(tp, b.forms, width, max, shortAt);
        float size = Math.max(Ui.sp(this, STATUS_MIN), Math.min(largest(tp, af, width, max), largest(tp, bf, width, max)));
        for (Line l : new Line[] {a, b}) l.view.setTextSize(android.util.TypedValue.COMPLEX_UNIT_PX, size);
        a.view.setText(styled(af, a.colour, a.in));
        b.view.setText(styled(bf, b.colour, b.in));
    }

    private String[] form(android.text.TextPaint tp, String[][] forms, float width, float max, float shortAt) {
        for (String[] f : forms) if (largest(tp, f, width, max) >= shortAt) return f;
        return forms[forms.length - 1];
    }

    /** The largest size, in half-sp steps from `max`, at which the words and the smaller brackets fit. */
    private float largest(android.text.TextPaint tp, String[] f, float width, float max) {
        float step = Ui.sp(this, 0.5f);
        for (float s = max; s > step; s -= step) {
            tp.setTypeface(Ui.heavy(this));
            tp.setTextSize(s);
            float w = tp.measureText(f[0]);
            if (!f[1].isEmpty()) {
                tp.setTypeface(Ui.body(this));
                tp.setTextSize(s * STATUS_IN);
                w += tp.measureText(" " + f[1]);
            }
            if (w <= width) return s;
        }
        return step;
    }

    /** "Світло є з 14:30" in the colour of the state, " (уже 3 год 08 хв)" smaller and grey. */
    private CharSequence styled(String[] f, int colour, int in) {
        android.text.SpannableStringBuilder s = new android.text.SpannableStringBuilder(f[0]);
        s.setSpan(new android.text.style.ForegroundColorSpan(colour), 0, s.length(), 0);
        if (!f[1].isEmpty()) {
            int from = s.length();
            s.append(" ").append(f[1]);
            s.setSpan(new android.text.style.ForegroundColorSpan(in), from, s.length(), 0);
            s.setSpan(new android.text.style.RelativeSizeSpan(STATUS_IN), from, s.length(), 0);
            s.setSpan(new Ui.Face(Ui.body(this)), from, s.length(), 0);
        }
        return s;
    }

    /** Yesterday of a place: from the source while it still has it, else from the statistics. */
    private String yesterday(Logic.Place p, Logic.Data d, LocalDate base) {
        LocalDate y = base.minusDays(1);
        String day = Logic.day(d, p.group, y);
        return day != null ? day : Prefs.history(this, p.id).get(y);
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
        card.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        card.setBackground(Ui.card(this, pal, 0));
        day(card, p, base, "Сьогодні", Logic.day(d, p.group, base), cur, nowRel);
        View line = divider();
        LinearLayout.LayoutParams ll = fill();
        ll.height = Ui.dp(this, 1);
        ll.topMargin = Ui.dp(this, 12);
        ll.bottomMargin = Ui.dp(this, 12);
        card.addView(line, ll);
        LocalDate t = base.plusDays(1);
        day(card, p, t, "Завтра, " + t.getDayOfMonth() + " " + Ui.MONTHS[t.getMonthValue() - 1],
                Logic.day(d, p.group, t), -1, -1);
        return card;
    }

    private void day(LinearLayout card, Logic.Place p, LocalDate date, String title, String slots, int cur, float nowRel) {
        // the title; at the right how much light to expect, as long as it fits, and the day to send
        LinearLayout head = Ui.row(this);
        TextView t = Ui.text(this, 16, Ui.display(this), pal.text);
        t.setText(title);
        t.setMaxLines(1);
        head.addView(t);
        FrameLayout room = new FrameLayout(this);
        head.addView(room, weight());
        if (slots != null) {
            light(room, Logic.dayLights(slots));
            head.addView(shareButton(p, date, title));
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
        bl.topMargin = Ui.dp(this, 6);
        card.addView(bar, bl);

        List<Logic.Outage> outages = Logic.outages(slots, cur);
        if (outages.isEmpty()) return;
        LinearLayout.LayoutParams cl = fill();
        cl.topMargin = Ui.dp(this, 8);
        card.addView(chips(outages), cl);
    }

    /** "16 год світла · 67 %" at the right of the title, in the longest form that fits. */
    private void light(FrameLayout room, List<String> light) {
        boolean clean = light.get(0).equals(Logic.NO_OUTAGES);
        TextView tot = Ui.text(this, 13, Ui.heavy(this), clean ? pal.ink : pal.onText);
        tot.setMaxLines(1);
        tot.setText(light.get(0));
        tot.setPadding(Ui.dp(this, 9), Ui.dp(this, 4), Ui.dp(this, 9), Ui.dp(this, 4));
        int tint = Ui.fade(pal.on, pal.dark ? 0.18f : 0.12f);
        tot.setBackground(Ui.round(clean ? pal.on : tint, Ui.px(this, 100)));
        FrameLayout.LayoutParams tl = new FrameLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT,
                ViewGroup.LayoutParams.WRAP_CONTENT, Gravity.END | Gravity.CENTER_VERTICAL);
        tl.leftMargin = Ui.dp(this, 8);
        room.addView(tot, tl);
        room.addOnLayoutChangeListener((v, l, tp, r, bt, ol, ot, or, ob) -> {
            if (r - l == or - ol) return;
            float space = (float) r - l - tl.leftMargin - tot.getPaddingLeft() - tot.getPaddingRight();
            String pick = light.get(light.size() - 1);
            for (String s : light) {
                if (tot.getPaint().measureText(s) <= space) {
                    pick = s;
                    break;
                }
            }
            String shown = pick;
            tot.post(() -> {
                tot.setText(shown);
                tot.setVisibility(tot.getPaint().measureText(shown) <= space ? View.VISIBLE : View.INVISIBLE);
            });
        });
    }

    /**
     * The outages as one row that scrolls sideways: it opens at the outage now or the next one,
     * the past ones to the left; arrows show that there is more and move by one outage.
     */
    private View chips(List<Logic.Outage> outages) {
        LinearLayout row = Ui.row(this);
        android.widget.HorizontalScrollView scroll = new android.widget.HorizontalScrollView(this);
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.setOverScrollMode(View.OVER_SCROLL_NEVER);
        scroll.setHorizontalFadingEdgeEnabled(true);
        scroll.setFadingEdgeLength(Ui.dp(this, 28));
        LinearLayout line = Ui.row(this);
        scroll.addView(line);
        View first = null;
        for (Logic.Outage o : outages) {
            View c = chip(o);
            LinearLayout.LayoutParams l = wrap();
            if (line.getChildCount() > 0) l.leftMargin = Ui.dp(this, 6);
            line.addView(c, l);
            if (first == null && o.when != Logic.Outage.PAST) first = c;
        }
        ImageView back = arrow(true);
        ImageView more = arrow(false);
        row.addView(back);
        row.addView(scroll, weight());
        row.addView(more);
        Runnable arrows = () -> {
            back.setVisibility(scroll.canScrollHorizontally(-1) ? View.VISIBLE : View.GONE);
            more.setVisibility(scroll.canScrollHorizontally(1) ? View.VISIBLE : View.GONE);
        };
        scroll.setOnScrollChangeListener((v, x, y, ox, oy) -> arrows.run());
        int step = Ui.dp(this, 110);
        back.setOnClickListener(v -> scroll.smoothScrollBy(-step, 0));
        more.setOnClickListener(v -> scroll.smoothScrollBy(step, 0));
        View open = first;
        scroll.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l == or - ol) return;
            scroll.post(() -> {
                if (open != null) scroll.scrollTo(open.getLeft(), 0);
                arrows.run();
            });
        });
        return row;
    }

    private ImageView arrow(boolean back) {
        ImageView a = iconButton(R.drawable.ic_chevron, back ? "Попередні вимкнення" : "Наступні вимкнення", null);
        if (back) a.setRotation(180);
        a.setScaleType(ImageView.ScaleType.CENTER);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(Ui.dp(this, 30), Ui.dp(this, 30));
        if (back) l.rightMargin = Ui.dp(this, 6);
        else l.leftMargin = Ui.dp(this, 6);
        a.setLayoutParams(l);
        a.setVisibility(View.GONE);
        return a;
    }

    /** A small round button at the end of a day's title: this day as a picture, to send. */
    private View shareButton(Logic.Place p, LocalDate date, String title) {
        ImageView b = iconButton(R.drawable.ic_share, "Поділитися графіком: " + title, v -> Share.send(this, p, date));
        b.setScaleType(ImageView.ScaleType.FIT_CENTER);
        int pad = Ui.dp(this, 8);
        b.setPadding(pad, pad, pad, pad);
        LinearLayout.LayoutParams l = new LinearLayout.LayoutParams(Ui.dp(this, 32), Ui.dp(this, 32));
        l.leftMargin = Ui.dp(this, 8);
        b.setLayoutParams(l);
        return b;
    }

    private String notPublished(Logic.Place p, boolean tomorrow) {
        if (!tomorrow) return "Обленерго ще не опублікувало графік на сьогодні.";
        boolean told = Prefs.alerts(this, p.id).tomorrow;
        return "Графік ще не опубліковано." + (told ? " Сповіщення прийде, щойно він з'явиться." : "");
    }

    /** "06:00–08:00": filled now, tinted later (red, amber for a possible outage), faded when past. */
    private View chip(Logic.Outage o) {
        boolean now = o.when == Logic.Outage.NOW;
        int fill = o.maybe ? pal.maybe : pal.off;
        int ink = o.maybe ? pal.accentInk : pal.ink;
        int text = o.maybe ? pal.maybeText : pal.offText;
        TextView c = Ui.text(this, 15, Ui.heavy(this), now ? ink : text);
        c.setText(o.label.replace(" – ", "–"));
        c.setPadding(Ui.dp(this, 12), Ui.dp(this, 7), Ui.dp(this, 12), Ui.dp(this, 7));
        int tint = Ui.fade(fill, pal.dark ? 0.22f : 0.14f);
        c.setBackground(Ui.round(now ? fill : tint, Ui.px(this, 12)));
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
