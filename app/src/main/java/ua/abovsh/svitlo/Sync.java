package ua.abovsh.svitlo;

import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.appwidget.AppWidgetManager;
import android.content.BroadcastReceiver;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.Bundle;
import android.widget.RemoteViews;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/** Fetch, tell about schedule changes, redraw widgets, arm the next refresh and reminder. */
final class Sync {
    static final int JOB_PERIODIC = 1;
    static final int JOB_NOW = 2;
    static final String CH_REMIND = "remind";
    static final String CH_SCHEDULE = "schedule";
    static final String CH_QUIET = "quiet";
    static final String ACTION_REFRESH = "ua.abovsh.svitlo.REFRESH";
    static final String ACTION_NOTIFY = "ua.abovsh.svitlo.NOTIFY";

    private Sync() {}

    static long now() {
        return System.currentTimeMillis() / 1000;
    }

    /** Blocking (network): call off the main thread. True when every region answered. */
    static boolean fetchAndApply(Context c) {
        return fetchAndApply(c, true);
    }

    /** A background check: no network when the source answered moments ago (see Logic.dueForFetch). */
    static boolean refreshInBackground(Context c) {
        return fetchAndApply(c, false);
    }

    private static boolean fetchAndApply(Context c, boolean always) {
        long now = now();
        if (!always && !Logic.dueForFetch(checkedAt(c), now)) {
            apply(c);
            return true;
        }
        boolean ok = true;
        for (String region : Prefs.regions(c)) {
            Logic.Data before = Store.data(c, region);
            int r = Store.fetch(c, region);
            if (r == Store.FAILED) ok = false;
            if (r == Store.NEW) notifyNotes(c, region, before, Store.data(c, region), now);
        }
        rememberDays(c, now);
        apply(c);
        return ok;
    }

    /** Today's and tomorrow's schedule of every place into its history; written only when it changed. */
    static void rememberDays(Context c, long now) {
        LocalDate today = Logic.today(now);
        for (Logic.Place p : Prefs.places(c)) {
            Logic.Data d = Store.data(c, p.region);
            Map<LocalDate, String> before = Prefs.history(c, p.id);
            Map<LocalDate, String> after = Logic.remember(before, today, Logic.day(d, p.group, today), today);
            after = Logic.remember(after, today.plusDays(1), Logic.day(d, p.group, today.plusDays(1)), today);
            if (!after.equals(before)) Prefs.saveHistory(c, p.id, after);
        }
    }

    /** Redraw from the cache and re-arm alarms; no network. */
    static void apply(Context c) {
        long now = now();
        renderWidgets(c, now);
        armRefresh(c, now);
        armNotify(c, now);
    }

    static String timeline(Context c, Logic.Place p, LocalDate base) {
        return Logic.timeline(Store.data(c, p.region), p.group, base);
    }

    static long checkedAt(Context c) {
        long t = Long.MAX_VALUE;
        for (String r : Prefs.regions(c)) t = Math.min(t, Store.checkedAt(c, r));
        return t == Long.MAX_VALUE ? 0 : t;
    }

    // ---- notifications ------------------------------------------------------------

    static void ensureChannels(Context c) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        NotificationChannel remind = new NotificationChannel(CH_REMIND, "Нагадування перед вимкненням і увімкненням",
                NotificationManager.IMPORTANCE_HIGH);
        nm.createNotificationChannel(remind);
        nm.createNotificationChannel(new NotificationChannel(CH_SCHEDULE, "Новий графік і зміни графіка",
                NotificationManager.IMPORTANCE_DEFAULT));
        nm.createNotificationChannel(new NotificationChannel(CH_QUIET, "Уночі без звуку",
                NotificationManager.IMPORTANCE_LOW));
    }

    static final int RED = 0xFFE5484D;
    static final int GREEN = 0xFF2DB86C;
    private static final int GOLD = 0xFFF5B000;

    static void post(Context c, int id, String channel, String title, String text) {
        post(c, id, channel, title, text, R.drawable.ic_notify, GOLD);
    }

    /** `icon` and `colour` tell off from on before a word is read: Samsung paints the circle with it. */
    static void post(Context c, int id, String channel, String title, String text, int icon, int colour) {
        ensureChannels(c);
        long now = now();
        if (Prefs.quietNights(c) && Logic.quiet(now)) channel = CH_QUIET;
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Notification n = new Notification.Builder(c, channel)
                .setSmallIcon(icon)
                .setColor(colour)
                .setContentTitle(tinted(title, colour))
                .setContentText(text)
                .setStyle(new Notification.BigTextStyle().bigText(text))
                .setWhen(now * 1000)
                .setShowWhen(true)
                .setAutoCancel(true)
                .setContentIntent(PendingIntent.getActivity(c, 0, open,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT))
                .build();
        try {
            c.getSystemService(NotificationManager.class).notify(id, n);
        } catch (SecurityException ignored) {
            // notifications not allowed: the app still works
        }
    }

    /**
     * A coloured mark at the start of a reminder: most phones drop text colours in notifications,
     * a coloured circle stays. Red — the power goes off, green — it comes back.
     */
    static String mark(boolean on) {
        return on ? "\uD83D\uDFE2 " : "\uD83D\uDD34 ";
    }

    /** The title in the colour of its kind, where the phone keeps colours in notifications. */
    private static CharSequence tinted(String title, int colour) {
        if (colour == GOLD) return title;
        android.text.SpannableString s = new android.text.SpannableString(title);
        s.setSpan(new android.text.style.ForegroundColorSpan(colour), 0, title.length(), 0);
        return s;
    }

    static int placeNo(Context c, String id) {
        List<Logic.Place> ps = Prefs.places(c);
        for (int i = 0; i < ps.size(); i++) if (ps.get(i).id.equals(id)) return i;
        return 9;
    }

    static void notifyNotes(Context c, String region, Logic.Data before, Logic.Data after, long now) {
        for (Logic.Place p : Prefs.places(c)) {
            if (!p.region.equals(region) || Prefs.muted(c, p.id)) continue;
            for (Logic.Note n : Logic.notes(p, before, after, now)) {
                boolean wanted = n.kind == Logic.Note.PUBLISHED ? Prefs.tomorrowNotes(c) : Prefs.changeNotes(c);
                if (!wanted) continue;
                post(c, 100 + placeNo(c, p.id) * 2 + n.kind, CH_SCHEDULE, n.title, n.text);
            }
        }
    }

    /** All reminders still to come, earliest first, without those already shown. */
    static List<Object[]> upcoming(Context c, long now) {
        List<Object[]> out = new ArrayList<>();
        LocalDate base = Logic.today(now);
        Logic.Alerts a = Prefs.alerts(c);
        Set<String> fired = Prefs.fired(c);
        for (Logic.Place p : Prefs.places(c)) {
            if (Prefs.muted(c, p.id)) continue;
            String tl = timeline(c, p, base);
            for (Logic.Event e : Logic.events(p, tl, base, now, a)) {
                if (!fired.contains(e.key())) out.add(new Object[] {e, tl});
            }
        }
        out.sort((x, y) -> Long.compare(((Logic.Event) x[0]).at, ((Logic.Event) y[0]).at));
        return out;
    }

    /** Show the reminders that are due now (the NOTIFY alarm). */
    static void fireDue(Context c) {
        long now = now();
        LocalDate base = Logic.today(now);
        for (Object[] x : upcoming(c, now)) {
            Logic.Event e = (Logic.Event) x[0];
            if (e.at > now + 30) break;
            String[] t = Logic.eventText(e, (String) x[1], base, now);
            boolean on = e.kind == Logic.Event.SOON_ON || e.kind == Logic.Event.AT_ON;
            post(c, 200 + placeNo(c, e.place) * 4 + e.kind, CH_REMIND, mark(on) + t[0], t[1],
                    on ? R.drawable.ic_notify_on : R.drawable.ic_notify_off, on ? GREEN : RED);
            Prefs.addFired(c, e.key(), now);
        }
    }

    static void testNotification(Context c) {
        post(c, 998, CH_REMIND, mark(false) + "Скоро вимкнуть світло", "Пробне нагадування · так виглядає попередження про вимкнення",
                R.drawable.ic_notify_off, RED);
        post(c, 999, CH_REMIND, mark(true) + "Скоро увімкнуть світло", "Пробне нагадування · так виглядає попередження про світло",
                R.drawable.ic_notify_on, GREEN);
    }

    // ---- widget --------------------------------------------------------------------

    /** What each widget shows now; an unchanged picture is not sent to the launcher again. */
    private static final Map<Integer, String> drawn = new HashMap<>();

    static synchronized void renderWidgets(Context c, long now) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = m.getAppWidgetIds(new ComponentName(c, LightWidget.class));
        Map<String, WidgetArt.Model> models = new HashMap<>();
        for (int id : ids) {
            String choice = Prefs.widgetChoice(c, id);
            if (!models.containsKey(choice)) models.put(choice, WidgetArt.model(c, now, choice));
            WidgetArt.Model model = models.get(choice);
            Bundle o = m.getAppWidgetOptions(id);
            // Portrait: the real cell is MIN_WIDTH x MAX_HEIGHT.
            int wDp = Math.max(110, o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, 180));
            int hDp = Math.min(180, Math.max(40, o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, 70)));
            String key = WidgetArt.key(c, model, now, wDp, hDp);
            if (key.equals(drawn.get(id))) continue;
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
            v.setImageViewBitmap(R.id.w_img, WidgetArt.draw(c, model, now, wDp, hDp));
            v.setContentDescription(R.id.w_img, WidgetArt.describe(model));
            Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            v.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(c, 1, open,
                    PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
            m.updateAppWidget(id, v);
            drawn.put(id, key);
        }
    }

    /** The launcher asked again (a resize, a restart of the launcher): draw anew. */
    static synchronized void forgetWidgets() {
        drawn.clear();
    }

    // ---- alarms and jobs -------------------------------------------------------------

    private static PendingIntent alarm(Context c, String action, int code) {
        Intent i = new Intent(c, AlarmReceiver.class).setAction(action);
        return PendingIntent.getBroadcast(c, code, i,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
    }

    private static void setAlarm(Context c, long epoch, PendingIntent pi, boolean wantExact) {
        AlarmManager am = c.getSystemService(AlarmManager.class);
        long ms = epoch * 1000;
        boolean exact = wantExact && (Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms());
        if (exact) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi);
        else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, ms, pi);
    }

    static void armRefresh(Context c, long now) {
        PendingIntent pi = alarm(c, ACTION_REFRESH, 1);
        if (Prefs.places(c).isEmpty()) {
            c.getSystemService(AlarmManager.class).cancel(pi);
            return;
        }
        LocalDate base = Logic.today(now);
        List<String> tls = new ArrayList<>();
        for (Logic.Place p : Prefs.places(c)) tls.add(timeline(c, p, base));
        long at = Logic.nextRefresh(tls, base, now, checkedAt(c));
        // a plain poll lets the system batch it with other wake-ups; a change of the text is exact
        setAlarm(c, at, pi, Logic.exactRefresh(tls, base, now, at));
    }

    static void armNotify(Context c, long now) {
        PendingIntent pi = alarm(c, ACTION_NOTIFY, 2);
        List<Object[]> up = upcoming(c, now);
        if (up.isEmpty()) {
            c.getSystemService(AlarmManager.class).cancel(pi);
            return;
        }
        setAlarm(c, Math.max(((Logic.Event) up.get(0)[0]).at, now + 1), pi, true);
    }

    static void schedulePeriodic(Context c) {
        JobScheduler js = c.getSystemService(JobScheduler.class);
        if (js.getPendingJob(JOB_PERIODIC) != null) return;
        js.schedule(new JobInfo.Builder(JOB_PERIODIC, new ComponentName(c, FetchJob.class))
                .setPeriodic(15 * 60 * 1000L)
                .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                .setPersisted(true)
                .build());
    }

    /**
     * Fetch inside the alarm's own broadcast: an exact alarm may run a short request even when
     * the app sits in a restricted standby bucket, where a job is held back for hours. A failed
     * fetch falls back to a job that waits for the network.
     */
    static void fetchNow(Context c, BroadcastReceiver.PendingResult result) {
        Context app = c.getApplicationContext();
        new Thread(() -> {
            try {
                if (!refreshInBackground(app)) fetchSoon(app);
            } finally {
                result.finish();
            }
        }).start();
    }

    static void fetchSoon(Context c) {
        c.getSystemService(JobScheduler.class).schedule(
                new JobInfo.Builder(JOB_NOW, new ComponentName(c, FetchJob.class))
                        .setRequiredNetworkType(JobInfo.NETWORK_TYPE_ANY)
                        .build());
    }

    static Map<String, Logic.Data> dataByRegion(Context c) {
        Map<String, Logic.Data> m = new HashMap<>();
        for (String r : Prefs.regions(c)) m.put(r, Store.data(c, r));
        return m;
    }
}
