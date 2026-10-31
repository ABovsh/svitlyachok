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

    private boolean first;
    private String editId;
    private String region = "kyiv_region";
    private String group = "";
    private EditText name;
    private LinearLayout regions;
    private LinearLayout queues;
    private LinearLayout preview;
    private boolean loading;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        first = Prefs.places(this).isEmpty();
        editId = getIntent().getStringExtra("id");
        Logic.Place edit = editId == null ? null : Prefs.place(this, editId);
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

        page.addView(heading("Область"));
        regions = box();
        regions.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        page.addView(regions, fill());
        fillRegions();

        page.addView(heading("Черга"));
        LinearLayout qbox = box();
        qbox.setPadding(Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12), Ui.dp(this, 12));
        queues = Ui.column(this);
        qbox.addView(queues, fill());
        preview = Ui.column(this);
        qbox.addView(preview, fill());
        page.addView(qbox, fill());
        fillQueues();

        page.addView(heading("Назва"));
        LinearLayout nbox = box();
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
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        name.setImeOptions(EditorInfo.IME_ACTION_DONE);
        name.setPadding(Ui.dp(this, 16), Ui.dp(this, 12), Ui.dp(this, 16), Ui.dp(this, 12));
        name.setBackground(Ui.round(pal.raised, Ui.dp(this, 16)));
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
        page.addView(nbox, fill());

        LinearLayout.LayoutParams bl = fill();
        bl.topMargin = Ui.dp(this, 18);
        page.addView(primary(first ? "Почати" : "Зберегти", v -> save()), bl);

        if (edit != null && Prefs.places(this).size() > 1) {
            TextView del = Ui.text(this, 15, Ui.heavy(this), pal.muted);
            del.setText("Видалити це місце");
            del.setGravity(Gravity.CENTER);
            del.setMinHeight(Ui.dp(this, 52));
            del.setOnClickListener(v -> {
                Prefs.removePlace(this, edit.id);
                Sync.apply(getApplicationContext());
                finish();
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
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), Ui.dp(SetupActivity.this, 20));
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
        s.setText("Графік відключень вашої черги: чи є світло зараз, до котрої години і що буде завтра. "
                + "Нагадує заздалегідь.");
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
                fillQueues();
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
            long now = Sync.now();
            LocalDate base = Logic.today(now);
            String day = Logic.day(d, group, base);
            TextView t = Ui.text(this, 14, Ui.bold(this), pal.text);
            t.setText("Сьогодні в черзі " + Logic.queue(group) + ": " + Logic.dayTotal(day));
            t.setPadding(Ui.dp(this, 4), Ui.dp(this, 14), 0, 0);
            preview.addView(t, fill());
            if (day != null) {
                BarView b = new BarView(this, pal);
                int cur = Logic.index(base, now);
                b.set(day, cur + (Logic.z(now).getMinute() % 30) / 30f);
                LinearLayout.LayoutParams bl = fill();
                bl.topMargin = Ui.dp(this, 10);
                preview.addView(b, bl);
            }
        } else if (loading) {
            TextView t = Ui.text(this, 14, Ui.body(this), pal.muted);
            t.setText("Завантажую список черг…");
            t.setPadding(Ui.dp(this, 4), Ui.dp(this, 16), 0, 0);
            preview.addView(t, fill());
        }
        TextView help = Ui.text(this, 14, Ui.bold(this), r != null && !r.dso.isEmpty() ? pal.accentText : pal.muted);
        help.setLineSpacing(0, 1.1f);
        if (r != null && !r.dso.isEmpty()) {
            help.setText("Не знаєте свою чергу? Перевірте за адресою на сайті обленерго ›");
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

    /** The region's file: its queues and the preview. */
    private void load() {
        String r = region;
        loading = true;
        if (Store.data(this, r) == null) fillPreview(null);
        new Thread(() -> {
            Store.fetch(getApplicationContext(), r);
            runOnUiThread(() -> {
                loading = false;
                if (!isFinishing() && r.equals(region)) fillQueues();
            });
        }).start();
    }

    private void save() {
        if (group.isEmpty()) {
            Toast.makeText(this, "Оберіть чергу", Toast.LENGTH_SHORT).show();
            return;
        }
        String n = name.getText().toString().trim();
        if (n.isEmpty()) n = "Черга " + Logic.queue(group);
        if (n.length() > 24) n = n.substring(0, 24);
        List<Logic.Place> places = Prefs.places(this);
        if (editId == null && places.size() >= Prefs.MAX_PLACES) {
            Toast.makeText(this, "Можна додати до " + Prefs.MAX_PLACES + " місць", Toast.LENGTH_SHORT).show();
            return;
        }
        Logic.Place p = new Logic.Place();
        p.id = editId != null ? editId : "p" + System.currentTimeMillis();
        p.name = n;
        p.region = region;
        p.group = group;
        Prefs.putPlace(this, p);
        Prefs.setCurrent(this, p.id);
        Sync.schedulePeriodic(this);
        Sync.apply(getApplicationContext());
        if (first) startActivity(new Intent(this, MainActivity.class));
        finish();
    }
}
