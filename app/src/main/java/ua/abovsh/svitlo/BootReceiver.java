package ua.abovsh.svitlo;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** After a reboot or an app update: the periodic check, alarms and a fetch come back. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context c, Intent i) {
        // exported for the system broadcasts only: anything else sent here is ignored
        String a = i.getAction();
        if (!Intent.ACTION_BOOT_COMPLETED.equals(a) && !Intent.ACTION_MY_PACKAGE_REPLACED.equals(a)) return;
        if (Prefs.places(c).isEmpty()) return;
        Sync.schedulePeriodic(c);
        Sync.apply(c);
        Sync.fetchSoon(c);
    }
}
