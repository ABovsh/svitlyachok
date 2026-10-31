package ua.abovsh.svitlo;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Which places a widget shows: one or several. Opens when a widget is added, and to change it. */
public class WidgetSetupActivity extends Base {
    private int widget = AppWidgetManager.INVALID_APPWIDGET_ID;
    private final Set<String> chosen = new LinkedHashSet<>();
    private boolean built;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        setResult(RESULT_CANCELED);
        Bundle extras = getIntent().getExtras();
        if (extras != null) widget = extras.getInt(AppWidgetManager.EXTRA_APPWIDGET_ID, widget);
        // only a widget of this app, created by the launcher, may be set up here
        AppWidgetProviderInfo info = AppWidgetManager.getInstance(this).getAppWidgetInfo(widget);
        if (info == null || !getPackageName().equals(info.provider.getPackageName())) {
            finish();
            return;
        }
        List<Logic.Place> places = Prefs.places(this);
        if (places.size() <= 1) {  // nothing to choose
            done("");
            return;
        }
        for (Logic.Place p : Logic.widgetPlaces(places, Prefs.widgetChoice(this, widget))) chosen.add(p.id);
    }

    @Override
    void render() {
        if (built || isFinishing()) return;
        built = true;
        page.addView(topBar("Віджет"), fill());
        TextView ask = Ui.text(this, 15, Ui.body(this), pal.muted);
        ask.setText("Які місця показувати на цьому віджеті? Можна одне або кілька.");
        ask.setLineSpacing(0, 1.1f);
        ask.setPadding(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 12));
        page.addView(ask, fill());
        LinearLayout box = box();
        List<Logic.Place> places = Prefs.places(this);
        for (int i = 0; i < places.size(); i++) {
            Logic.Place p = places.get(i);
            if (i > 0) box.addView(divider());
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
            ToggleView tv = new ToggleView(this, pal, chosen.contains(p.id), on -> {
                if (on) chosen.add(p.id);
                else chosen.remove(p.id);
            });
            tv.setContentDescription(p.name);
            LinearLayout.LayoutParams tl = wrap();
            tl.leftMargin = Ui.dp(this, 12);
            r.addView(tv, tl);
            r.setOnClickListener(v -> tv.performClick());
            box.addView(r, fill());
        }
        page.addView(box, fill());
        LinearLayout.LayoutParams bl = fill();
        bl.topMargin = Ui.dp(this, 18);
        page.addView(primary("Готово", v -> {
            if (chosen.isEmpty()) {
                Toast.makeText(this, "Оберіть хоча б одне місце", Toast.LENGTH_SHORT).show();
                return;
            }
            done(String.join(",", chosen));
        }), bl);
    }

    private void done(String ids) {
        Prefs.setWidgetChoice(this, widget, ids);
        Sync.forgetWidgets();
        Sync.apply(getApplicationContext());
        setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widget));
        finish();
    }
}
