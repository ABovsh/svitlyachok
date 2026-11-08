package ua.abovsh.svitlo;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.time.LocalDate;
import java.util.List;

/** A place: its region, its queue, a name. Also the first screen of the app. */
public class SetupActivity extends Base {
    private static final String[] DEFAULT_GROUPS = {"GPV1.1", "GPV1.2", "GPV2.1", "GPV2.2", "GPV3.1", "GPV3.2",
        "GPV4.1", "GPV4.2", "GPV5.1", "GPV5.2", "GPV6.1", "GPV6.2"};
    private static final String[] NAMES = {"Дім", "Дача", "Робота", "Батьки"};
    private static final int MAX_NAME = 24;

    private boolean first;
    private String editId;
    private String region = "kyiv_region";
    private String group = "";
    private EditText name;
    private LinearLayout regions;
    private LinearLayout queues;
    private LinearLayout preview;
    private boolean loading;
    private boolean loadFailed;
    private Logic.Place editing;
    private boolean confirmedQueueChange;
    private Store.Request loadRequest;
    private TextView queueError;
    private TextView regionHeading;
    private LinearLayout regionLine;
    private TextView regionValue;
    private LinearLayout nameBox;
    private LinearLayout nameLine;
    private TextView nameValue;

    @Override boolean hasClock() { return false; }

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        first = Prefs.places(this).isEmpty();
        editId = getIntent().getStringExtra("id");
        Logic.Place edit = editId == null ? null : Prefs.place(this, editId);
        editing = edit;
        if (edit != null) {
            region = edit.region;
            group = edit.group;
        }
        build(edit);
        load();
    }

    @Override
    void render() {
        // nothing here changes with the clock
    }

    private void build(Logic.Place edit) {
        if (first) page.addView(welcome(), fill());
        else page.addView(topBar(edit == null ? "Нове місце" : "Місце"), fill());

        regionHeading = heading("Область");
        page.addView(regionHeading);
        regions = box();
        regions.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        page.addView(regions, fill());
        regionValue = Ui.text(this, 16, Ui.heavy(this), pal.text);
        regionLine = line("Область", regionValue, "Змінити", this::openRegions);
        page.addView(regionLine, gapTop(first ? 14 : 4));
        fillRegions();
        showRegions(edit == null);

        page.addView(heading("Черга"));
        LinearLayout qbox = box();
        qbox.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        queues = Ui.column(this);
        qbox.addView(queues, fill());
        preview = Ui.column(this);
        qbox.addView(preview, fill());
        page.addView(qbox, fill());
        fillQueues();

        nameValue = Ui.text(this, 16, Ui.heavy(this), pal.text);
        nameLine = line("Назва", nameValue, null, this::openName);
        page.addView(nameLine, gapTop(8));
        LinearLayout nbox = box();
        nameBox = nbox;
        nbox.setVisibility(View.GONE);
        nbox.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        name = new EditText(this);
        if (edit != null) name.setText(edit.name);
        else if (first) name.setText("Дім");
        name.setHint("наприклад, Дім");
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 17);
        name.setTypeface(Ui.bold(this));
        name.setTextColor(pal.text);
        name.setHintTextColor(pal.dim);
        name.setSingleLine(true);
        // the limit shows while typing, not as a name cut after saving
        name.setFilters(new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(MAX_NAME)});
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        name.setImeOptions(EditorInfo.IME_ACTION_DONE);
        name.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        name.setBackground(Ui.round(pal.raised, Ui.px(this, 16)));
        nbox.addView(name, fill());
        FlowRow quick = new FlowRow(this, Ui.dp(this, 8));
        for (String n : NAMES) {
            TextView t = Ui.pill(this, pal, n, false, 14);
            t.setOnClickListener(v -> {
                name.setText(n);
                name.setSelection(n.length());
            });
            quick.addView(t);
        }
        LinearLayout.LayoutParams ql = fill();
        ql.topMargin = Ui.dp(this, 12);
        nbox.addView(quick, ql);
        page.addView(nbox, gapTop(8));
        showName();

        LinearLayout.LayoutParams bl = fill();
        bl.topMargin = Ui.dp(this, 12);
        page.addView(primary(first ? "Почати" : "Зберегти", v -> save()), bl);

        if (edit != null && Prefs.places(this).size() > 1) {
            TextView del = Ui.text(this, 15, Ui.heavy(this), pal.offText);
            del.setText("Видалити це місце");
            del.setGravity(Gravity.CENTER);
            del.setMinHeight(Ui.dp(this, 52));
            del.setOnClickListener(v -> {
                Ui.dialog(this).setTitle("Видалити «" + edit.name + "»?")
                        .setMessage("Разом із місцем буде видалено його збережену статистику.")
                        .setPositiveButton("Видалити",(dialog,which) -> {
                            Prefs.removePlace(this,edit.id); Sync.forget(getApplicationContext(),edit.id);
                            Sync.apply(getApplicationContext()); finish();
                        }).setNegativeButton("Скасувати",null).show();
            });
            LinearLayout.LayoutParams dl = fill();
            dl.topMargin = Ui.dp(this, 8);
            page.addView(del, dl);
        }
    }

    private View welcome() {
        LinearLayout w = Ui.column(this);
        w.setGravity(Gravity.CENTER_HORIZONTAL);
        w.setPadding(Ui.dp(this, 8), Ui.dp(this, 20), Ui.dp(this, 8), 0);
        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_launcher_fg);
        logo.setBackground(getDrawable(R.drawable.ic_launcher_bg));
        logo.setClipToOutline(true);
        logo.setOutlineProvider(new android.view.ViewOutlineProvider() {
            @Override
            public void getOutline(View v, android.graphics.Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), Ui.px(SetupActivity.this, 20));
            }
        });
        w.addView(logo, new LinearLayout.LayoutParams(Ui.dp(this, 72), Ui.dp(this, 72)));
        TextView t = Ui.text(this, 24, Ui.display(this), pal.text);
        t.setText("Світлячок");
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 8));
        w.addView(t);
        TextView s = Ui.text(this, 15, Ui.body(this), pal.muted);
        s.setGravity(Gravity.CENTER);
        s.setLineSpacing(0, 1.1f);
        s.setText("Графік вимкнень вашої черги: чи є світло зараз, до котрої години і що буде завтра. "
                + "Сповіщає заздалегідь.");
        w.addView(s);
        return w;
    }

    private void fillRegions() {
        regions.removeAllViews();
        FlowRow f = new FlowRow(this, Ui.dp(this, 6));
        for (Logic.Region r : Logic.regions()) {
            TextView t = Ui.pill(this, pal, r.name.replace(" область", ""), r.id.equals(region), 13.5f);
            t.setPadding(Ui.dp(this, 13), Ui.dp(this, 8), Ui.dp(this, 13), Ui.dp(this, 8));
            t.setContentDescription(r.name);
            t.setOnClickListener(v -> {
                showRegions(false);
                if (r.id.equals(region)) return;
                region = r.id;
                group = "";
                fillRegions();
                fillQueues();
                load();
            });
            f.addView(t);
        }
        regions.addView(f, fill());
        Logic.Region chosen = Logic.region(region);
        regionValue.setText(chosen == null ? "" : chosen.name.replace(" область", ""));
    }

    /** A choice already made, on one line, with the way to change it. */
    private LinearLayout line(String label, TextView value, String action, Runnable open) {
        LinearLayout r = Ui.row(this);
        r.setPadding(Ui.dp(this, 14), Ui.dp(this, 10), Ui.dp(this, 14), Ui.dp(this, 10));
        r.setBackground(Ui.card(this, pal, 0));
        LinearLayout t = Ui.column(this);
        TextView l = Ui.text(this, 12.5f, Ui.bold(this), pal.muted);
        l.setText(label);
        t.addView(l);
        t.addView(value);
        r.addView(t, weight());
        if (action != null) {
            TextView a = Ui.text(this, 14, Ui.heavy(this), pal.accentText);
            a.setText(action);
            r.addView(a, wrap());
        } else {
            ImageView pen = new ImageView(this);
            pen.setImageResource(R.drawable.ic_edit);
            pen.setColorFilter(pal.accentText, android.graphics.PorterDuff.Mode.SRC_IN);
            pen.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
            r.addView(pen, new LinearLayout.LayoutParams(Ui.dp(this, 20), Ui.dp(this, 20)));
        }
        r.setContentDescription(label + ", змінити");
        r.setOnClickListener(v -> open.run());
        return r;
    }

    private void showRegions(boolean open) {
        regionHeading.setVisibility(open ? View.VISIBLE : View.GONE);
        regions.setVisibility(open ? View.VISIBLE : View.GONE);
        regionLine.setVisibility(open ? View.GONE : View.VISIBLE);
    }

    private void openRegions() {
        showRegions(true);
    }

    private void openName() {
        nameLine.setVisibility(View.GONE);
        nameBox.setVisibility(View.VISIBLE);
        name.requestFocus();
    }

    /** The line says the name the place will get: the one typed, or the one given by its queue. */
    private void showName() {
        if (nameValue == null || name == null) return;
        String typed = name.getText().toString().trim();
        if (!typed.isEmpty()) nameValue.setText(typed);
        else if (!group.isEmpty()) nameValue.setText(Logic.placeName("", editing, group));
        else nameValue.setText("за чергою");
    }

    private void fillQueues() {
        queues.removeAllViews();
        Logic.Data d = Store.data(this, region);
        List<String> groups = d != null && !d.groups.isEmpty() ? d.groups : java.util.Arrays.asList(DEFAULT_GROUPS);
        LinearLayout row = null;
        for (int i = 0; i < groups.size(); i++) {
            if (i % 4 == 0) {
                row = Ui.row(this);
                LinearLayout.LayoutParams rl = fill();
                if (i > 0) rl.topMargin = Ui.dp(this, 8);
                queues.addView(row, rl);
            }
            String g = groups.get(i);
            TextView t = Ui.pill(this, pal, Logic.queue(g), g.equals(group), 16);
            t.setTypeface(Ui.displayMid(this));
            t.setPadding(0, Ui.dp(this, 11), 0, Ui.dp(this, 11));
            t.setContentDescription("черга " + Logic.queue(g));
            t.setOnClickListener(v -> {
                group = g;
                showRegions(false);
                fillQueues();
                showName();
            });
            LinearLayout.LayoutParams tl = weight();
            if (i % 4 > 0) tl.leftMargin = Ui.dp(this, 8);
            row.addView(t, tl);
        }
        if (row != null) for (int k = groups.size() % 4; k > 0 && k < 4; k++) {
            View sp = new View(this);
            LinearLayout.LayoutParams sl = weight();
            sl.leftMargin = Ui.dp(this, 8);
            row.addView(sp, sl);
        }
        fillPreview(d);
    }

    private void fillPreview(Logic.Data d) {
        preview.removeAllViews();
        Logic.Region r = Logic.region(region);
        if (!group.isEmpty() && d != null) {
            // the queue's own days, as the main screen shows them: what this choice means at a glance
            long now = Sync.now();
            LocalDate base = Logic.today(now);
            int cur = Logic.index(base, now);
            previewDay("Сьогодні", Logic.day(d, group, base), cur + (Logic.z(now).getMinute() % 30) / 30f);
            LocalDate tomorrow = base.plusDays(1);
            previewDay("Завтра, " + tomorrow.getDayOfMonth() + "." + tomorrow.getMonthValue(), Logic.day(d, group, tomorrow), -1);
        } else if (loading) {
            TextView t = Ui.text(this, 14, Ui.body(this), pal.muted);
            t.setText("Завантажую список черг…");
            t.setPadding(Ui.dp(this, 4), Ui.dp(this, 16), 0, 0);
            preview.addView(t, fill());
        } else if (loadFailed) {
            preview.addView(loadError(), fill());
        }
        TextView help = Ui.text(this, 14, Ui.bold(this), r != null && !r.dso.isEmpty() ? pal.accentText : pal.muted);
        help.setLineSpacing(0, 1.1f);
        if (r != null && !r.dso.isEmpty()) {
            help.setText(Logic.queueHelp(r));
            help.setOnClickListener(v -> {
                try {
                    startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(r.dso)));
                } catch (Exception ignored) {
                    // no browser
                }
            });
        } else {
            help.setText("Свою чергу можна знайти за адресою на сайті вашого обленерго.");
        }
        help.setPadding(Ui.dp(this, 4), Ui.dp(this, 12), Ui.dp(this, 4), Ui.dp(this, 2));
        preview.addView(help, fill());
    }

    /** The list above is only the usual one: the region's own did not arrive. */
    private View loadError() {
        TextView t = Ui.text(this, 14, Ui.bold(this), pal.offText);
        t.setText("Не вдалося завантажити список черг, показано типові. Спробувати ще раз ›");
        t.setLineSpacing(0, 1.1f);
        t.setPadding(Ui.dp(this, 4), Ui.dp(this, 14), Ui.dp(this, 4), Ui.dp(this, 2));
        t.setMinHeight(Ui.dp(this, 48));
        t.setOnClickListener(v -> load());
        return t;
    }

    /** One day of the chosen queue: its title and light, and the bar; a day not published says so. */
    private void previewDay(String title, String slots, float nowRel) {
        String none = nowRel < 0 ? "графік ще не опубліковано" : "графіка немає";
        DayHeader head = new DayHeader(this, pal, title, slots != null ? Logic.daySummaries(slots)
                : java.util.Collections.singletonList(none), slots != null ? pal.onText : pal.muted, null);
        head.setContentDescription(Logic.describe(title, slots));
        LinearLayout.LayoutParams hl = fill();
        hl.topMargin = Ui.dp(this, 10);
        preview.addView(head, hl);
        if (slots == null) return;
        BarView bar = new BarView(this, pal);
        bar.set(slots, nowRel);
        bar.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
        preview.addView(bar, fill());
    }

    /** The region's file: its queues and the preview. */
    private void load() {
        String r = region;
        loadFailed = false;
        if (loadRequest != null) loadRequest.cancel();
        Store.Request request = new Store.Request(); loadRequest = request;
        loading = true;
        if (Store.data(this, r) == null) fillPreview(null);
        new Thread(() -> {
            // a file checked minutes ago needs no network: the queues of a region hardly ever change
            int result = Sync.fetchRegion(getApplicationContext(), r, request);
            runOnUiThread(() -> {
                if (loadRequest != request || isFinishing()) return;
                loading = false;
                if (!r.equals(region)) return;
                Logic.Data d = Store.data(this, r);
                loadFailed = result == Store.FAILED && d == null;
                dropQueueNotOffered(d);
                fillQueues();
            });
        }).start();
    }

    /** Picked from the usual list while the region's own was coming, and the region does not have it. */
    private void dropQueueNotOffered(Logic.Data d) {
        if (d == null || d.groups.isEmpty()) return;
        String saved = editing != null && editing.region.equals(region) ? editing.group : "";
        if (!Logic.queueOffered(group, d.groups, saved)) group = "";
    }

    @Override protected void onDestroy() {
        if (loadRequest != null) loadRequest.cancel();
        // regions looked at here, or left by a place just changed or removed, are not kept
        Store.keepOnly(getApplicationContext(), Prefs.regions(this));
        super.onDestroy();
    }

    private void showQueueError() {
        if (queueError != null) queues.removeView(queueError);
        queueError = Ui.text(this,14,Ui.heavy(this),pal.offText);
        queueError.setText("Оберіть чергу, щоб зберегти місце");
        queues.addView(queueError,fill()); queueError.announceForAccessibility(queueError.getText());
        scroll.smoothScrollTo(0,queues.getTop());
    }

    private void save() {
        if (group.isEmpty()) { showQueueError(); return; }
        String n = Logic.placeName(name.getText().toString(), editId == null ? null : Prefs.place(this, editId), group);
        if (n.length() > MAX_NAME) n = n.substring(0, MAX_NAME);
        List<Logic.Place> places = Prefs.places(this);
        Logic.Place candidate = new Logic.Place();
        candidate.region = region;
        candidate.group = group;
        Logic.Place twin = Logic.duplicateOf(places, candidate, editId == null ? null : Prefs.place(this, editId));
        if (twin != null) {
            Ui.dialog(this).setTitle("Таке місце вже додано")
                    .setMessage(Logic.duplicateMessage(twin)).setPositiveButton("Зрозуміло", null).show();
            return;
        }
        if (editId == null && places.size() >= Prefs.MAX_PLACES) {
            Toast.makeText(this, "Можна додати до " + Prefs.MAX_PLACES + " місць", Toast.LENGTH_SHORT).show();
            return;
        }
        Logic.Place p = new Logic.Place();
        p.id = editId != null ? editId : "p" + System.currentTimeMillis();
        p.name = n;
        p.region = region;
        p.group = group;
        Logic.Place before = editId == null ? null : Prefs.place(this, editId);
        if (before != null && !Logic.sameQueue(before,p) && !confirmedQueueChange) {
            Ui.dialog(this).setTitle("Змінити чергу?")
                    .setMessage("Збережену статистику цього місця буде видалено: вона стосується попередньої черги.")
                    .setPositiveButton("Змінити",(dialog,which) -> {confirmedQueueChange=true;save();})
                    .setNegativeButton("Скасувати",null).show();
            return;
        }
        if (before == null) inheritAlerts(p);
        Prefs.putPlace(this, p);
        Prefs.setCurrent(this, p.id);
        if (before != null && !Logic.sameQueue(before, p)) {  // all of this belongs to the old queue
            Prefs.forgetHistory(this, p.id);
            Prefs.forgetNotices(this, p.id);
            Sync.forget(getApplicationContext(), p.id);
            Sync.rememberDays(getApplicationContext(), Sync.now());
        }
        Sync.schedulePeriodic(this);
        Sync.apply(getApplicationContext());
        leave(first, p);
    }

    /** A new place notifies as the place on screen does: one choice made once, not again for every place. */
    private void inheritAlerts(Logic.Place p) {
        List<Logic.Place> places = Prefs.places(this);
        if (places.isEmpty()) return;
        Logic.Place from = places.get(0);
        for (Logic.Place q : places) if (q.id.equals(Prefs.current(this))) from = q;
        Prefs.setAlerts(this, p.id, Prefs.alerts(this, from.id));
    }

    private void leave(boolean first, Logic.Place p) {
        if (first) startActivity(new Intent(this, MainActivity.class));
        if (first || editId != null) {
            finish();
            return;
        }
        // The main screen shows the new place, wherever adding began, and offers its widget. The offer
        // is made there: opening a screen after asking the launcher would cover the launcher's window.
        Intent main = new Intent(this, MainActivity.class);
        if (Widgets.canOffer(this, p)) main.putExtra(MainActivity.EXTRA_OFFER_WIDGET, p.id);
        startActivity(main);
        finish();
    }
}
