package ua.abovsh.svitlo;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.Intent;
import android.graphics.PorterDuff;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.List;

/** Which place a widget shows. Opens when a widget is added from the launcher, and to change it. */
public class WidgetSetupActivity extends Base {
    private int widget = AppWidgetManager.INVALID_APPWIDGET_ID;
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
        if (places.size() <= 1) done(places.isEmpty() ? "" : places.get(0).id);  // nothing to choose
    }

    @Override
    void render() {
        if (built || isFinishing()) return;
        built = true;
        page.addView(topBar("Віджет"), fill());
        TextView ask = Ui.text(this, 15, Ui.body(this), pal.muted);
        ask.setText("Яке місце показувати на цьому віджеті? Для іншого місця додайте ще один віджет.");
        ask.setLineSpacing(0, 1.1f);
        ask.setPadding(Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 6), Ui.dp(this, 12));
        page.addView(ask, fill());
        LinearLayout box = box();
        List<Logic.Place> places = Prefs.places(this);
        Logic.Place current = Logic.widgetPlace(places, Prefs.widgetChoice(this, widget));
        for (int i = 0; i < places.size(); i++) {
            Logic.Place p = places.get(i);
            if (i > 0) box.addView(divider());
            box.addView(row(p, current != null && p.id.equals(current.id)), fill());
        }
        page.addView(box, fill());
    }

    private LinearLayout row(Logic.Place p, boolean chosen) {
        LinearLayout r = Ui.row(this);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, Ui.dp(this, 14), 0, Ui.dp(this, 14));
        LinearLayout t = Ui.column(this);
        TextView name = Ui.text(this, 16, Ui.heavy(this), pal.text);
        name.setText(p.name);
        t.addView(name);
        TextView w = Ui.text(this, 13, Ui.body(this), pal.muted);
        w.setText(where(p));
        w.setPadding(0, Ui.dp(this, 3), 0, 0);
        t.addView(w);
        r.addView(t, weight());
        if (chosen) {
            TextView now = Ui.text(this, 12, Ui.heavy(this), pal.accentInk);
            now.setText("на віджеті");
            now.setPadding(Ui.dp(this, 10), Ui.dp(this, 5), Ui.dp(this, 10), Ui.dp(this, 5));
            now.setBackground(Ui.round(pal.accent, Ui.px(this, 100)));
            r.addView(now, wrap());
        }
        ImageView go = new ImageView(this);
        go.setImageResource(R.drawable.ic_chevron);
        go.setColorFilter(pal.dim, PorterDuff.Mode.SRC_IN);
        go.setScaleType(ImageView.ScaleType.CENTER);
        r.addView(go, new LinearLayout.LayoutParams(Ui.dp(this, 32), Ui.dp(this, 44)));
        r.setContentDescription(p.name + (chosen ? ", зараз на віджеті" : "") + ", показувати на віджеті");
        r.setOnClickListener(v -> done(p.id));
        return r;
    }

    private void done(String place) {
        Prefs.setWidgetChoice(this, widget, place);
        Sync.forgetWidgets();
        Sync.apply(getApplicationContext());
        setResult(RESULT_OK, new Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, widget));
        finish();
    }
}
