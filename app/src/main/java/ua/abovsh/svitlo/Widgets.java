package ua.abovsh.svitlo;

import android.app.PendingIntent;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.widget.Toast;
import java.util.ArrayList;
import java.util.List;

/** One widget per place: which places have one, and offering a widget for a place. */
final class Widgets {
    private Widgets() {}

    static int[] ids(Context c) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] small = m.getAppWidgetIds(new ComponentName(c, LightWidget.class));
        int[] large = m.getAppWidgetIds(new ComponentName(c, LargeLightWidget.class));
        int[] all = java.util.Arrays.copyOf(small, small.length + large.length);
        System.arraycopy(large, 0, all, small.length, large.length);
        return all;
    }

    /** The place each placed widget shows. */
    static List<String> places(Context c) {
        List<Logic.Place> all = Prefs.places(c);
        List<String> out = new ArrayList<>();
        for (int id : ids(c)) {
            Logic.Place p = Logic.widgetPlace(all, Prefs.widgetChoice(c, id));
            if (p != null) out.add(p.id);
        }
        return out;
    }

    /** The places shown by all widgets except this one. */
    static List<String> placesOfOthers(Context c, int widget) {
        List<Logic.Place> all = Prefs.places(c);
        List<String> out = new ArrayList<>();
        for (int id : ids(c)) {
            Logic.Place p = id == widget ? null : Logic.widgetPlace(all, Prefs.widgetChoice(c, id));
            if (p != null) out.add(p.id);
        }
        return out;
    }

    /** Whether the app can offer a widget for this place: it has none yet and the launcher can pin. */
    static boolean canOffer(Context c, Logic.Place p) {
        return AppWidgetManager.getInstance(c).isRequestPinAppWidgetSupported() && Logic.needsWidget(p.id, places(c));
    }

    /**
     * The launcher's own "add to home screen" dialog for a widget of this place. The person
     * confirms or cancels there; when placed, PinReceiver records the place for the new widget.
     */
    static void offer(Context c, Logic.Place p) {
        offer(c, p, "Скасувати");
    }

    /** Nothing may open a screen after this: it would cover the launcher's window, and no widget would be placed. */
    static void offer(Context c, Logic.Place p, String decline) {
        new AlertDialog.Builder(c).setTitle("Віджет для «" + p.name + "»")
                .setItems(new String[] {"Малий · 2×1", "Великий · 4×2 · з графіком"},
                        (dialog, which) -> pin(c, p, which == 1))
                .setNegativeButton(decline, null).show();
    }

    private static void pin(Context c, Logic.Place p, boolean large) {
        Intent placed = new Intent(c, PinReceiver.class).putExtra(PinReceiver.PLACE, p.id);
        // mutable, so the system can add the new widget's id; explicit, so nothing else can be aimed at
        int mutable = Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0;
        PendingIntent done = PendingIntent.getBroadcast(c, p.id.hashCode() * 2 + (large ? 1 : 0), placed,
                mutable | PendingIntent.FLAG_UPDATE_CURRENT);
        boolean asked = AppWidgetManager.getInstance(c).requestPinAppWidget(new ComponentName(c,
                large ? LargeLightWidget.class : LightWidget.class), null, done);
        if (!asked) Toast.makeText(c, "Не вдалося додати віджет. Додайте його зі списку віджетів на головному екрані.",
                Toast.LENGTH_LONG).show();
    }
}
