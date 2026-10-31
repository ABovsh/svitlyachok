package ua.abovsh.svitlo;

import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/**
 * The launcher placed a widget that the app offered for one place: remember which place it shows.
 * Not exported: only this app's own PendingIntent (from Widgets.offer) can reach it.
 */
public class PinReceiver extends BroadcastReceiver {
    static final String PLACE = "place";

    @Override
    public void onReceive(Context c, Intent i) {
        int widget = i.getIntExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, AppWidgetManager.INVALID_APPWIDGET_ID);
        String place = i.getStringExtra(PLACE);
        if (widget == AppWidgetManager.INVALID_APPWIDGET_ID || place == null || Prefs.place(c, place) == null) return;
        Prefs.setWidgetChoice(c, widget, place);
        Sync.forgetWidgets();
        Sync.apply(c);
    }
}
