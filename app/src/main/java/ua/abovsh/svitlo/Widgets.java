package ua.abovsh.svitlo;

import android.app.PendingIntent;
import android.app.AlertDialog;
import android.appwidget.AppWidgetManager;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
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

    /** Whether the app can offer a widget for this place: it has none yet and the launcher can pin. */
    static boolean canOffer(Context c, Logic.Place p) {
        return AppWidgetManager.getInstance(c).isRequestPinAppWidgetSupported() && Logic.needsWidget(p.id, places(c));
    }

    /**
     * The launcher's own "add to home screen" dialog for a widget of this place. The person
     * confirms or cancels there; when placed, PinReceiver records the place for the new widget.
     */
    static void offer(Context c, Logic.Place p) {
        offer(c, p, () -> {});
    }

    /** Setup stays alive until the size chooser is dismissed; main needs no completion hook. */
    static void offer(Context c, Logic.Place p, Runnable closed) {
        new AlertDialog.Builder(c).setTitle("Віджет для «" + p.name + "»")
                .setItems(new String[] {"Малий · 2×1", "Великий · 4×2 · з графіком"},
                        (dialog, which) -> pin(c, p, which == 1))
                .setNegativeButton("Скасувати", null)
                .setOnDismissListener(dialog -> closed.run()).show();
    }

    private static void pin(Context c, Logic.Place p, boolean large) {
        Intent placed = new Intent(c, PinReceiver.class).putExtra(PinReceiver.PLACE, p.id);
        // mutable, so the system can add the new widget's id; explicit, so nothing else can be aimed at
        int mutable = Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0;
        PendingIntent done = PendingIntent.getBroadcast(c, p.id.hashCode() * 2 + (large ? 1 : 0), placed,
                mutable | PendingIntent.FLAG_UPDATE_CURRENT);
        AppWidgetManager.getInstance(c).requestPinAppWidget(new ComponentName(c,
                large ? LargeLightWidget.class : LightWidget.class), null, done);
    }
}
