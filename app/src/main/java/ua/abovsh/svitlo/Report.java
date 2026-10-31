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
        Logic.ReportInfo r = new Logic.ReportInfo();
        r.version = BuildConfig.VERSION_NAME;
        r.android = Build.VERSION.RELEASE;
        r.api = Build.VERSION.SDK_INT;
        r.phone = Build.MANUFACTURER + " " + Build.MODEL;
        r.places = Prefs.places(c);
        r.notifications = c.getSystemService(NotificationManager.class).areNotificationsEnabled();
        r.alerts = Prefs.alerts(c);
        r.batteryUnlimited = c.getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(c.getPackageName());
        r.checked = Sync.checkedAt(c);
        r.now = Sync.now();
        r.widgets = Widgets.ids(c).length;
        return Logic.reportText(r);
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
