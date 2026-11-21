package ua.abovsh.svitlo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import android.app.Activity;
import android.appwidget.AppWidgetManager;
import android.appwidget.AppWidgetProviderInfo;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import java.util.List;
import org.junit.Test;
import org.robolectric.Robolectric;
import org.robolectric.Shadows;

public class AndroidPrefsTest extends AndroidRegressionFixture {
    private static final String FIRST = "p1";
    private static final String SECOND = "p2";

    @Test
    public void oldSharedReminderSettingsMigrateWithoutOverwritingPerPlaceSettings() {
        context.getSharedPreferences("prefs", Context.MODE_PRIVATE).edit()
                .putInt("lead_off", 5).putBoolean("n_off", true)
                .putBoolean("n_on", false).putBoolean("note_change", true).apply();
        Logic.Alerts migrated = Prefs.alerts(context, FIRST);
        assertTrue(migrated.off);
        assertFalse(migrated.on);
        assertEquals(10, migrated.leadOff);
        assertTrue(migrated.schedule());

        Logic.Alerts individual = new Logic.Alerts();
        individual.schedule(false);
        Prefs.setAlerts(context, SECOND, individual);
        assertFalse(Prefs.alerts(context, SECOND).schedule());
        assertTrue(Prefs.alerts(context, FIRST).schedule());
    }

    @Test
    public void cancellingConfigurationOfSecondWidgetKeepsFirstWidgetChoice() {
        var manager = Shadows.shadowOf(AppWidgetManager.getInstance(context));
        int firstWidget = 101;
        int secondWidget = 102;
        AppWidgetProviderInfo info = new AppWidgetProviderInfo();
        info.provider = new ComponentName(context, LightWidget.class);
        manager.addBoundWidget(firstWidget, info);
        manager.addBoundWidget(secondWidget, info);
        Prefs.savePlaces(context, List.of(place(FIRST, "Бібліотека"), place(SECOND, "Музей")));
        Prefs.setWidgetChoice(context, firstWidget, FIRST);
        Intent input = new Intent(context, WidgetSetupActivity.class)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, secondWidget);
        var activity = Robolectric.buildActivity(WidgetSetupActivity.class, input).create();
        try {
            assertFalse("The second widget must reach the configuration screen", activity.get().isFinishing());
            activity.get().finish();
            assertEquals(Activity.RESULT_CANCELED, Shadows.shadowOf(activity.get()).getResultCode());
            assertEquals(FIRST, Prefs.savedWidgetChoice(context, firstWidget));
            assertNull(Prefs.savedWidgetChoice(context, secondWidget));
        } finally {
            activity.destroy();
        }
    }
}
