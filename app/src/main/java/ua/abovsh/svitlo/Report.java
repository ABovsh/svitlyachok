package ua.abovsh.svitlo;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.PowerManager;
import android.widget.Toast;

/**
 * "Report a problem": the technical details a fix needs, shown to the person first. Nothing is
 * sent by the app itself: either a GitHub issue form opens with the details filled in, or the
 * text goes to whatever messenger the person picks.
 */
final class Report {
    private Report() {}

    /** Version, phone, regions and queues (never the place names), notification state, last check. */
    static String details(Context c) {
        Logic.Alerts a = Prefs.alerts(c);
        boolean allowed = c.getSystemService(NotificationManager.class).areNotificationsEnabled();
        boolean unlimited = c.getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(c.getPackageName());
        long checked = Sync.checkedAt(c);
        return "Світлячок " + BuildConfig.VERSION_NAME + "\n"
                + "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + "), "
                + Build.MANUFACTURER + " " + Build.MODEL + "\n"
                + "Місця: " + Logic.placesSummary(Prefs.places(c)) + "\n"
                + "Сповіщення: " + (allowed ? "дозволені" : "вимкнені в телефоні")
                + "; вимкнення — " + lead(a.off, a.leadOff) + ", увімкнення — " + lead(a.on, a.leadOn) + "\n"
                + "Обмеження батареї: " + (unlimited ? "немає" : "є") + "\n"
                + "Графік перевірено: " + (checked > 0 ? Logic.updated(checked, Sync.now()).replace("графік оновлено ", "")
                        : "ще ні") + "\n"
                + "Віджетів: " + Widgets.ids(c).length;
    }

    private static String lead(boolean on, int minutes) {
        if (!on) return "вимкнено";
        return minutes == 0 ? "одразу" : "за " + minutes + " хв";
    }

    static void onGitHub(Activity a) {
        Updates.open(a, Logic.issueLink(details(a)));
    }

    static void share(Activity a) {
        Intent send = new Intent(Intent.ACTION_SEND)
                .setType("text/plain")
                .putExtra(Intent.EXTRA_SUBJECT, "Світлячок: проблема")
                .putExtra(Intent.EXTRA_TEXT, "Що сталося:\n\n\n— технічні дані —\n" + details(a));
        try {
            a.startActivity(Intent.createChooser(send, "Надіслати опис проблеми"));
        } catch (RuntimeException e) {
            Toast.makeText(a, "Немає застосунку, щоб надіслати текст", Toast.LENGTH_SHORT).show();
        }
    }
}
