package ua.abovsh.svitlo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import android.Manifest;
import android.app.NotificationManager;
import android.app.job.JobInfo;
import android.app.job.JobScheduler;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.ConnectivityManager;
import android.os.Looper;
import android.service.notification.StatusBarNotification;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import org.junit.Ignore;
import org.junit.Test;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.Shadows;
import org.robolectric.shadows.ShadowPausedLooper;

public class AndroidSyncTest extends AndroidRegressionFixture {
    private static final String FIRST = "p1";
    private static final String SECOND = "p2";
    private static final String PLACE_EXTRA = "notice_place";

    @Test
    public void oneScheduleNotificationPerPlaceEvenWhenTheSameFeedArrivesTwice() {
        Shadows.shadowOf(RuntimeEnvironment.getApplication()).grantPermissions(Manifest.permission.POST_NOTIFICATIONS);
        Logic.Place first = place(FIRST, "Бібліотека");
        Logic.Place second = place(SECOND, "Музей");
        Logic.Place unrelated = place("p3", "Клуб");
        unrelated.region = RegionalFeed.CV;
        Prefs.savePlaces(context, List.of(first, second, unrelated));
        for (Logic.Place place : Prefs.places(context)) Prefs.setAlerts(context, place.id, Logic.newPlaceAlerts());

        Logic.Data before = new Logic.Data();
        Logic.Data after = new Logic.Data();
        after.days.put(DAY.plusDays(1), Map.of(QUEUE, "0".repeat(48)));
        Sync.notifyNotes(context, REGION, before, after, NOW);
        Map<String, Integer> delivered = children();
        assertEquals(Set.of(FIRST, SECOND), delivered.keySet());
        assertNotEquals(delivered.get(FIRST), delivered.get(SECOND));

        for (int id : delivered.values()) context.getSystemService(NotificationManager.class).cancel(id);
        Sync.notifyNotes(context, REGION, before, after, NOW);
        assertTrue("The same schedule must stay seen after dismissal", children().isEmpty());
    }

    @Test
    public void orderedAlarmFinishesAndRequestsANetworkConstrainedRetryOffline() throws Exception {
        Shadows.shadowOf(context.getSystemService(ConnectivityManager.class)).setActiveNetworkInfo(null);
        assertFalse(Store.online(context));
        Prefs.savePlaces(context, List.of(place(FIRST, "Бібліотека")));
        AlarmReceiver alarm = new AlarmReceiver();
        boolean[] finished = {false};
        BroadcastReceiver completion = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent intent) {
                finished[0] = true;
            }
        };
        context.registerReceiver(alarm, new IntentFilter(Sync.ACTION_REFRESH), Context.RECEIVER_NOT_EXPORTED);
        try {
            context.sendOrderedBroadcast(new Intent(Sync.ACTION_REFRESH).setPackage(context.getPackageName()),
                    null, completion, null, 0, null, null);
            ShadowPausedLooper main = (ShadowPausedLooper) Shadows.shadowOf(Looper.getMainLooper());
            long stop = System.nanoTime() + 2_000_000_000L;
            main.idle();
            while (!finished[0]) {
                long remainingMillis = TimeUnit.NANOSECONDS.toMillis(stop - System.nanoTime());
                if (remainingMillis <= 0) break;
                main.poll(remainingMillis);
                main.idle();
            }
            assertTrue("The ordered broadcast must complete after goAsync", finished[0]);
            JobInfo retry = context.getSystemService(JobScheduler.class).getPendingJob(Sync.JOB_NOW);
            assertNotNull(retry);
            assertEquals(FetchJob.class.getName(), retry.getService().getClassName());
            assertNotNull(retry.getRequiredNetwork());
        } finally {
            context.unregisterReceiver(alarm);
        }
    }

    @Ignore("R13: the definite restoration reminder is missing after possible restoration")
    @Test
    public void definiteRestorationFollowsAPossibleOne() {
        String slots = "0".repeat(24) + "3".repeat(4) + "1".repeat(20);
        Logic.Place place = place(FIRST, "Бібліотека");
        Logic.Alerts alerts = new Logic.Alerts();
        long restoration = DAY.atTime(14, 0).atZone(Logic.KYIV).toEpochSecond();
        String definiteOutage = slots.replace('3', '0');
        assertTrue("Fixture must send a reminder for an uninterrupted outage",
                hasRestorationReminder(place, definiteOutage, alerts, restoration));
        assertTrue("Missing the 14:00 restoration reminder after a possible restoration",
                hasRestorationReminder(place, slots, alerts, restoration));
    }

    private static boolean hasRestorationReminder(Logic.Place place, String slots, Logic.Alerts alerts,
            long restoration) {
        return Logic.events(place, slots, DAY, NOW, alerts).stream()
                .anyMatch(event -> event.kind == Logic.Event.SOON_ON && event.change == restoration);
    }

    private Map<String, Integer> children() {
        Map<String, Integer> ids = new HashMap<>();
        for (StatusBarNotification active : context.getSystemService(NotificationManager.class).getActiveNotifications()) {
            String place = active.getNotification().extras.getString(PLACE_EXTRA);
            if (place != null) {
                assertFalse("Duplicate notification for " + place, ids.containsKey(place));
                ids.put(place, active.getId());
            }
        }
        return ids;
    }
}
