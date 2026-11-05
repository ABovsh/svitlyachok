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
    private static final String CH_GROUP = "group";
    static final String ACTION_REFRESH = BuildConfig.APPLICATION_ID + ".REFRESH";
    static final String ACTION_NOTIFY = BuildConfig.APPLICATION_ID + ".NOTIFY";
    static final String ACTION_DISMISS = BuildConfig.APPLICATION_ID + ".DISMISS_NOTICE";
    private static final String NOTICE_GROUP = "places";
    private static final int SUMMARY_ID = 900;

    private Sync() {}

    static long now() {
        return System.currentTimeMillis() / 1000;
    }

    /** Blocking (network): call off the main thread. True when every region answered. */
    static boolean fetchAndApply(Context c) {
        return fetchAndApply(c, true, Long.MAX_VALUE, new Store.Request());
    }

    /** A background check: no network when the source answered moments ago (see Logic.dueForFetch). */
    static boolean refreshInBackground(Context c) {
        return refreshInBackground(c, new Store.Request());
    }

    /**
     * Inside a short broadcast budget: unfinished regions are left to a network-constrained job.
     */
    static boolean refreshWithin(Context c, long deadlineMs) {
        return fetchAndApply(c, false, deadlineMs, new Store.Request());
    }

    static boolean refreshInBackground(Context c, Store.Request request) {
        return fetchAndApply(c, false, Long.MAX_VALUE, request);
    }

    private static boolean fetchAndApply(Context c, boolean always, long deadlineMs, Store.Request request) {
        long now = now();
        if (!always && !Logic.dueForFetch(checkedAt(c), now)) {
            apply(c);
            return true;
        }
        boolean ok = true;
        for (String region : Prefs.regions(c)) {
            if (request.stopped || System.currentTimeMillis() > deadlineMs) {
                ok = false;
                break;
            }
            Logic.Data before = Store.data(c, region);
            int r = Store.fetch(c, region, deadlineMs, always, request);
            if (r == Store.FAILED) ok = false;
            if (r == Store.NEW) {
                reconcile(c, region);
                notifyNotes(c, region, before, Store.data(c, region), now);
            }
        }
        if (request.stopped) return false;
        rememberDays(c, now);
        fireDue(c);
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
        schedulePeriodic(c);
        for (String region : Prefs.regions(c)) reconcile(c, region);
        updateNotificationGroup(c);
        Runnable screen = openScreen.get();
        if (screen != null) screen.run();
    }

    /** The screen on view, told at once when an alarm or a new schedule changes what it shows. */
    private static final java.util.concurrent.atomic.AtomicReference<Runnable> openScreen = new java.util.concurrent.atomic.AtomicReference<>();

    static void onChanged(Runnable screen) {
        openScreen.set(screen);
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
        NotificationChannel remind = new NotificationChannel(CH_REMIND, "Сповіщення перед вимкненням і увімкненням",
                NotificationManager.IMPORTANCE_HIGH);
        nm.createNotificationChannel(remind);
        nm.createNotificationChannel(new NotificationChannel(CH_SCHEDULE, "Новий графік і зміни графіка",
                NotificationManager.IMPORTANCE_DEFAULT));
        nm.createNotificationChannel(new NotificationChannel(CH_QUIET, "Уночі без звуку",
                NotificationManager.IMPORTANCE_LOW));
        nm.createNotificationChannel(new NotificationChannel(CH_GROUP, "Групування сповіщень",
                NotificationManager.IMPORTANCE_LOW));
    }

    static final int RED = 0xFFE5484D;
    static final int GREEN = 0xFF2DB86C;
    private static final int GOLD = 0xFFF5B000;

    /** A place's schedule notice: gold, it stays until opened or swiped away. */
    static void post(Context c, String place, String channel, String title, String text) {
        Logic.Notice n = new Logic.Notice();
        n.title = title;
        n.text = text;
        post(c, place, channel, n, false);
    }

    /** A removed place takes its notification with it. */
    static void forget(Context c, String place) {
        NotificationManager manager = c.getSystemService(NotificationManager.class);
        manager.cancel(Logic.noticeSlot(place, true));
        manager.cancel(Logic.noticeSlot(place, false));
        updateNotificationGroup(c);
    }

    /**
     * A reminder and a schedule summary have independent slots for each place.
     * A new schedule must never hide an active countdown. A tap opens that place.
     */
    static void post(Context c, String place, String channel, Logic.Notice n, boolean reminder) {
        if (!reminder) clearLegacySchedule(c, place);
        show(c, Logic.noticeSlot(place, reminder), place, channel, n, reminder);
    }

    /** Previous versions used the reminder slot for schedules; keep actual reminders. */
    private static void clearLegacySchedule(Context c, String place) {
        NotificationManager manager = c.getSystemService(NotificationManager.class);
        for (android.service.notification.StatusBarNotification active : manager.getActiveNotifications()) {
            if (active.getId() == Logic.slot(place) && !active.getNotification().extras.containsKey("reminder_place")) {
                manager.cancel(active.getId());
            }
        }
    }

    /**
     * A reminder's bulb and colour tell off from on before a word is read: Samsung paints the
     * circle with it. `n.until`: when the notice stops being true it goes by itself.
     */
    private static void show(Context c, int id, String place, String channel, Logic.Notice n, boolean reminder) {
        show(c, id, place, channel, n, reminder, false);
    }

    private static void show(Context c, int id, String place, String channel, Logic.Notice n, boolean reminder, boolean silent) {
        int icon = R.drawable.ic_notify_calendar;
        int colour = GOLD;
        if (reminder) {
            icon = n.on ? R.drawable.ic_notify_on : R.drawable.ic_notify_off;
            if (n.state == Logic.MAYBE) icon = R.drawable.ic_notify_maybe;
            colour = reminderColour(n);
        }
        ensureChannels(c);
        long now = now();
        if (Prefs.quietNights(c) && Logic.quiet(now)) channel = CH_QUIET;
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(MainActivity.EXTRA_PLACE, place);
        Intent dismissed = new Intent(c, AlarmReceiver.class).setAction(ACTION_DISMISS);
        Bundle identity = new Bundle();
        identity.putString("notice_place", place);
        Notification.Builder b = new Notification.Builder(c, channel)
                .setSmallIcon(icon)
                .setColor(colour)
                .setContentTitle(tinted(n.title, colour))
                .setContentText(n.text)
                .setWhen(now * 1000)
                .setShowWhen(true)
                .setAutoCancel(true)
                .addExtras(identity)
                .setDeleteIntent(PendingIntent.getBroadcast(c, id, dismissed, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT))
                .setContentIntent(PendingIntent.getActivity(c, id, open,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        if (Prefs.places(c).size() > 1) b.setGroup(NOTICE_GROUP).setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN);
        if (silent) b.setOnlyAlertOnce(true);
        if (reminder) decorateReminder(b, place, n);
        b.setStyle(new Notification.BigTextStyle().bigText(n.text));
        if (n.until > now) b.setTimeoutAfter((n.until - now) * 1000);
        try {
            c.getSystemService(NotificationManager.class).notify(id, b.build());
            updateNotificationGroup(c);
        } catch (SecurityException ignored) {
            // notifications not allowed: the app still works
        }
    }

    /** A silent native group when several places have messages; tests and expired children do not count. */
    static void updateNotificationGroup(Context c) {
        NotificationManager manager = c.getSystemService(NotificationManager.class);
        List<android.service.notification.StatusBarNotification> children = new ArrayList<>();
        Set<String> places = new java.util.HashSet<>();
        for (android.service.notification.StatusBarNotification active : manager.getActiveNotifications()) {
            String place = active.getNotification().extras.getString("notice_place", "");
            if (active.getId() != SUMMARY_ID && !place.isEmpty() && active.getId() != 998 && active.getId() != 999) {
                children.add(active);
                places.add(place);
            }
        }
        if (Prefs.places(c).size() < 2 || children.size() < 2) {
            manager.cancel(SUMMARY_ID);
            return;
        }
        ensureChannels(c);
        children.sort(java.util.Comparator.comparingInt(android.service.notification.StatusBarNotification::getId));
        StringBuilder signature = new StringBuilder();
        long until = 0;
        boolean persistent = false;
        Notification.InboxStyle style = new Notification.InboxStyle();
        for (android.service.notification.StatusBarNotification child : children) {
            Bundle extras = child.getNotification().extras;
            style.addLine(extras.getCharSequence(Notification.EXTRA_TEXT, ""));
            signature.append(child.getId()).append(extras.getCharSequence(Notification.EXTRA_TITLE, ""))
                    .append(extras.getCharSequence(Notification.EXTRA_TEXT, "")).append('\n');
            long timeout = child.getNotification().getTimeoutAfter();
            if (timeout == 0) persistent = true;
            else until = Math.max(until, child.getPostTime() + timeout);
        }
        for (android.service.notification.StatusBarNotification active : manager.getActiveNotifications()) {
            if (active.getId() == SUMMARY_ID && signature.toString().equals(active.getNotification().extras.getString("group_content"))) return;
        }
        Bundle content = new Bundle();
        content.putString("group_content", signature.toString());
        Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        Notification.Builder summary = new Notification.Builder(c, CH_GROUP)
                .setSmallIcon(R.drawable.ic_notify).setColor(GOLD)
                .setContentTitle("Сповіщення місць").setContentText("Місць: " + places.size())
                .setStyle(style).setGroup(NOTICE_GROUP).setGroupSummary(true)
                .setGroupAlertBehavior(Notification.GROUP_ALERT_CHILDREN).setOnlyAlertOnce(true)
                .addExtras(content)
                .setAutoCancel(true).setContentIntent(PendingIntent.getActivity(c, SUMMARY_ID, open,
                        PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT));
        if (!persistent && until > 0) summary.setTimeoutAfter(Math.max(1, until - System.currentTimeMillis()));
        try {
            manager.notify(SUMMARY_ID, summary.build());
        } catch (SecurityException ignored) {
            // Notifications are disabled; the app remains usable.
        }
    }

    private static int reminderColour(Logic.Notice n) {
        if (n.state == Logic.MAYBE) return GOLD;
        return n.on ? GREEN : RED;
    }

    private static void decorateReminder(Notification.Builder b, String place, Logic.Notice n) {
        if (n.change <= 0) return;
        Bundle extras = new Bundle();
        extras.putString("reminder_place", place); extras.putLong("reminder_change", n.change);
        extras.putLong("reminder_planned", n.planned); extras.putInt("reminder_kind", n.kind);
        b.addExtras(extras);
        if (n.planned < n.change) b.setWhen(n.change * 1000).setUsesChronometer(true).setChronometerCountDown(true);
    }

    /** Only active reminders are eligible: a dismissed reminder is never reconstructed. */
    private static void reconcile(Context c, String region) {
        NotificationManager nm = c.getSystemService(NotificationManager.class);
        for (android.service.notification.StatusBarNotification posted : nm.getActiveNotifications()) {
            reconcileOne(c, region, posted, nm);
        }
    }

    private static void reconcileOne(Context c, String region, android.service.notification.StatusBarNotification posted, NotificationManager nm) {
        Bundle extras = posted.getNotification().extras;
        String id = extras.getString("reminder_place", "");
        Logic.Place p = Prefs.place(c, id);
        if (p == null || !p.region.equals(region)) return;
        LocalDate base = Logic.today(now()).minusDays(1);
        Logic.Data data = Store.data(c, region);
        String yesterday = Prefs.knownHistory(c, p, data).get(base);
        String tl = (yesterday == null ? Logic.repeat('2',48) : yesterday) + timeline(c,p,base.plusDays(1));
        Logic.Event match = Logic.shownEvent(Logic.inNotices(p, Prefs.places(c).size()),tl,base,Prefs.alerts(c,p.id),
                extras.getLong("reminder_change"),extras.getInt("reminder_kind"),extras.getLong("reminder_planned"));
        if (match == null) { nm.cancel(posted.getId()); return; }
        Logic.Notice note = Logic.eventText(match,tl,base,now());
        CharSequence previous = extras.getCharSequence(Notification.EXTRA_TITLE, "");
        CharSequence previousText = extras.getCharSequence(Notification.EXTRA_TEXT, "");
        boolean changed = !note.title.contentEquals(previous) || !note.text.contentEquals(previousText);
        if (changed && stillActive(nm,posted.getId())) {
            show(c, posted.getId(), id, posted.getNotification().getChannelId(),note,true,true);
        }
    }

    private static boolean stillActive(NotificationManager nm, int id) {
        for (android.service.notification.StatusBarNotification n : nm.getActiveNotifications()) if (n.getId()==id) return true;
        return false;
    }

    /** The title in the colour of its kind, where the phone keeps colours in notifications. */
    private static CharSequence tinted(String title, int colour) {
        if (colour == GOLD) return title;
        android.text.SpannableString s = new android.text.SpannableString(title);
        s.setSpan(new android.text.style.ForegroundColorSpan(colour), 0, title.length(), 0);
        return s;
    }

    static void notifyNotes(Context c, String region, Logic.Data before, Logic.Data after, long now) {
        List<Logic.Place> places = Prefs.places(c);
        for (Logic.Place p : places) {
            if (!p.region.equals(region)) continue;
            Logic.Alerts a = Prefs.alerts(c, p.id);
            List<Logic.Note> changed = new ArrayList<>();
            for (Logic.Note n : Logic.notes(Logic.inNotices(p, places.size()), before, after, now)) {
                boolean wanted = n.kind == Logic.Note.PUBLISHED ? a.tomorrow : a.changes;
                // the source may drop a day and bring it back unchanged: that is nothing new to the person
                if (!wanted || Logic.noteSeen(Prefs.notes(c, p.id), n.day, n.key)) continue;
                Prefs.setNotes(c, p.id, Logic.rememberNote(Prefs.notes(c, p.id), n.day, n.key));
                changed.add(n);
            }
            Logic.Notice notice = Logic.scheduleNotice(Logic.inNotices(p, places.size()), changed);
            if (notice != null) post(c, p.id, CH_SCHEDULE, notice, false);
        }
    }

    /** The next notification of one place, or null. */
    static Logic.Event nextAlert(Context c, String place, long now) {
        List<Logic.Event> own = new ArrayList<>();
        for (Object[] o : upcoming(c, now)) {
            Logic.Event e = (Logic.Event) o[0];
            if (e.place.equals(place)) own.add(e);
        }
        return Logic.nextAlert(own, now);
    }

    /** All reminders still to come, earliest first, without those already shown. */
    static List<Object[]> upcoming(Context c, long now) {
        List<Object[]> out = new ArrayList<>();
        LocalDate base = Logic.today(now);
        Set<String> fired = Prefs.fired(c);
        List<Logic.Place> places = Prefs.places(c);
        for (Logic.Place p : places) {
            Logic.Alerts a = Prefs.alerts(c, p.id);
            if (!a.off && !a.on) continue;
            LocalDate eventBase = base.minusDays(1);
            Logic.Data data = Store.data(c, p.region);
            String yesterday = Prefs.knownHistory(c, p, data).get(eventBase);
            String tl = (yesterday == null ? Logic.repeat('2', 48) : yesterday) + timeline(c, p, base);
            for (Logic.Event e : Logic.events(Logic.inNotices(p, places.size()), tl, eventBase, now, a)) {
                if (!fired.contains(e.key())) out.add(new Object[] {e, tl});
            }
        }
        out.sort((x, y) -> Long.compare(((Logic.Event) x[0]).at, ((Logic.Event) y[0]).at));
        return out;
    }

    /** Show the reminders that are due now (the NOTIFY alarm). */
    static synchronized void fireDue(Context c) {
        long now = now();
        LocalDate base = Logic.today(now);
        for (Object[] x : upcoming(c, now)) {
            Logic.Event e = (Logic.Event) x[0];
            if (e.at > now) break;
            Logic.Notice n = Logic.eventText(e, (String) x[1], base.minusDays(1), now);
            post(c, e.place, CH_REMIND, n, true);
            Prefs.addFired(c, e.key(), now);
            Prefs.reminderTiming(c,e.at,now);
        }
    }

    /** Two examples in the real format, for the place on screen; they go by themselves in 10 minutes. */
    static void testNotification(Context c) {
        List<Logic.Place> ps = Prefs.places(c);
        Logic.Place at = ps.isEmpty() ? null : ps.get(0);
        for (Logic.Place p : ps) if (p.id.equals(Prefs.current(c))) at = p;
        String name = at == null || ps.size() < 2 ? "" : at.name;
        String id = at == null ? "" : at.id;
        long gone = now() + 600;
        show(c, 998, id, CH_REMIND, Logic.probeNotice(name, false, gone), true);
        show(c, 999, id, CH_REMIND, Logic.probeNotice(name, true, gone), true);
    }

    // ---- widget --------------------------------------------------------------------

    /** What each widget shows now; an unchanged picture is not sent to the launcher again. */
    private static final Map<Integer, String> drawn = new HashMap<>();

    static synchronized void renderWidgets(Context c, long now) {
        AppWidgetManager m = AppWidgetManager.getInstance(c);
        int[] ids = Widgets.ids(c);
        Map<String, WidgetArt.Model> models = new HashMap<>();
        for (int id : ids) {
            String choice = Prefs.widgetChoice(c, id);
            if (!models.containsKey(choice)) models.put(choice, WidgetArt.model(c, now, choice));
            WidgetArt.Model model = models.get(choice);
            Bundle o = m.getAppWidgetOptions(id);
            // Portrait: the real cell is MIN_WIDTH x MAX_HEIGHT.
            android.appwidget.AppWidgetProviderInfo info = m.getAppWidgetInfo(id);
            String provider = info == null ? "" : info.provider.getClassName();
            int[] cell = Logic.widgetDefaultSize(LargeLightWidget.class.getName().equals(provider));
            int wDp = Math.max(110, o.getInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, cell[0]));
            int hDp = Math.min(260, Math.max(40, o.getInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, cell[1])));
            String key = BuildConfig.VERSION_CODE + "|" + c.getResources().getConfiguration().fontScale + "|" + WidgetArt.key(c, model, now, wDp, hDp);
            if (key.equals(drawn.get(id))) continue;
            RemoteViews v = new RemoteViews(c.getPackageName(), R.layout.widget);
            v.setImageViewBitmap(R.id.w_img, WidgetArt.draw(c, model, now, wDp, hDp));
            v.setContentDescription(R.id.w_img, WidgetArt.describe(model));
            Intent open = new Intent(c, MainActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            if (model != null && model.place != null) open.putExtra(MainActivity.EXTRA_PLACE, model.place.id);
            v.setOnClickPendingIntent(R.id.w_root, PendingIntent.getActivity(c, 10_000 + id, open,
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
        boolean consumers = Widgets.ids(c).length > 0;
        for (Logic.Place p : Prefs.places(c)) consumers |= Prefs.alerts(c,p.id).any();
        if (!consumers) at = now + 6 * 3600;
        // A nearby reminder wakes us for both jobs; a visual poll must not consume its idle quota.
        List<Object[]> reminders = upcoming(c, now);
        if (!reminders.isEmpty()) {
            long notify = ((Logic.Event) reminders.get(0)[0]).at;
            if (notify >= at && notify - at <= 15 * 60) at = Math.max(now + 1, notify);
        }
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
        long interval = 6 * 3600_000L;
        boolean consumers = Widgets.ids(c).length > 0;
        for (Logic.Place p : Prefs.places(c)) consumers |= Prefs.alerts(c,p.id).any();
        if (consumers) interval = 15 * 60_000L;
        JobInfo existing=js.getPendingJob(JOB_PERIODIC);
        if (existing != null && existing.getIntervalMillis()==interval) return;
        js.schedule(new JobInfo.Builder(JOB_PERIODIC, new ComponentName(c, FetchJob.class))
                .setPeriodic(interval)
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
                if (!refreshWithin(app, System.currentTimeMillis() + 7_000)) fetchSoon(app);
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
}
