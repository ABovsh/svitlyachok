package ua.abovsh.svitlo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** REFRESH: screen text changes by itself, and a poll. NOTIFY: reminders that are due. */
public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        if (Sync.ACTION_DISMISS.equals(i.getAction())) {
            Sync.updateNotificationGroup(c);
            return;
        }
        Sync.fireDue(c);
        Sync.apply(c);
        Sync.fetchNow(c, goAsync());
    }
}
