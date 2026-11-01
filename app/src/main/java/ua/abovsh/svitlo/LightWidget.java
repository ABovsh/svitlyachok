package ua.abovsh.svitlo;

import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProvider;
import android.content.Context;
import android.os.Bundle;

/** One place: current and next state; a large cell also shows today's schedule. */
public class LightWidget extends AppWidgetProvider {
    @Override
    public void onUpdate(Context c, AppWidgetManager m, int[] ids) {
        Sync.forgetWidgets();
        Sync.schedulePeriodic(c);
        Sync.apply(c);
        Sync.fetchSoon(c);
    }

    @Override
    public void onDeleted(Context c, int[] ids) {
        for (int id : ids) Prefs.forgetWidget(c, id);
    }

    @Override
    public void onAppWidgetOptionsChanged(Context c, AppWidgetManager m, int id, Bundle options) {
        Sync.forgetWidgets();
        Sync.apply(c);
    }
}
