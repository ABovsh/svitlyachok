package ua.abovsh.svitlo;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.os.Build;
import android.os.PowerManager;

/**
 * "Report a problem": the technical details a fix needs, shown to the person first. Nothing is
 * sent by the app itself: a GitHub issue form opens in the browser with the details filled in.
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
        for (Logic.Place p : r.places) r.alerts.add(Prefs.alerts(c, p.id));
        r.batteryUnlimited = c.getSystemService(PowerManager.class).isIgnoringBatteryOptimizations(c.getPackageName());
        r.checked = Sync.checkedAt(c);
        r.now = Sync.now();
        r.widgets = Widgets.ids(c).length;
        android.app.AlarmManager am=c.getSystemService(android.app.AlarmManager.class);
        android.content.SharedPreferences timing=c.getSharedPreferences("prefs",Context.MODE_PRIVATE);
        long planned=timing.getLong("reminder_planned",0);
        long actual=timing.getLong("reminder_actual",0);
        String moments=planned>0 ? "\nПлановий час нагадування: "+java.time.Instant.ofEpochSecond(planned)
                +"\nФактичний час нагадування: "+java.time.Instant.ofEpochSecond(actual) : "";
        return Logic.reportText(r) + moments + "\nТочні нагадування: " + (Build.VERSION.SDK_INT<31 || am.canScheduleExactAlarms() ? "доступні" : "недоступні")
                + "\nОстання затримка нагадування: " + c.getSharedPreferences("prefs",Context.MODE_PRIVATE).getInt("reminder_delay",0) + " с";
    }

    static void onGitHub(Activity a) {
        Updates.open(a, Logic.issueLink(details(a)));
    }
}
