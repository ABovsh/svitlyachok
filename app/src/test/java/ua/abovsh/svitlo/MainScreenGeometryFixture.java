package ua.abovsh.svitlo;

import android.content.Context;
import android.os.SystemClock;
import android.os.PowerManager;
import android.appwidget.AppWidgetManager;
import org.robolectric.RuntimeEnvironment;

import static org.robolectric.Shadows.shadowOf;
import java.time.LocalDate;
import java.util.Arrays;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

final class MainScreenGeometryFixture {
    private static final LocalDate DAY = LocalDate.of(2026, 11, 1);
    private static final String REGION = "kyiv_region";
    private static final String[] GROUPS = {"GPV1.1", "GPV1.2", "GPV2.1", "GPV3.2"};
    private static final String[] NAMES = {"Дім", "Дача", "Батьки", "Робота"};
    private static final String[] LONG_NAMES = {
        "Квартира біля парку", "Батьківський будинок",
        "МайстерняВеликогоБудинку", "Будинок на другій вулиці"
    };
    private static final String NORMAL = slots(0, 18, '0', 31, 39, '0');
    private static final String FIRST_TWO = slots(0, 12, '0', 24, 32, '0', 40, 44, '0');
    private static final String NEXT_DAY = slots(16, 22, '0', 36, 42, '0');

    enum Scenario {
        NORMAL("normal", 13, 0),
        LONG("long", 13, 0),
        POSSIBLE("possible", 13, 0),
        MIDNIGHT("midnight", 13, 0),
        TOMORROW("tomorrow", 13, 0),
        NO_FEED("nofeed", 13, 0),
        NIGHT("night", 21, 10),
        SOON_OFF("soon-off", 17, 10),
        SOON_MAYBE("soon-maybe", 17, 10);

        final String id;
        final long now;

        Scenario(String id, int hour, int minute) {
            this.id = id;
            now = DAY.atTime(hour, minute).atZone(Logic.KYIV).toEpochSecond();
        }
    }

    private MainScreenGeometryFixture() {}

    static Context profile(int width, float scale, String theme, int density) {
        RuntimeEnvironment.setQualifiers("uk-rUA-w" + width + "dp-h891dp-"
                + ("dark".equals(theme) ? "night" : "notnight") + "-" + density + "dpi");
        RuntimeEnvironment.setFontScale(scale);
        var app = RuntimeEnvironment.getApplication();
        shadowOf(app).grantPermissions(android.Manifest.permission.POST_NOTIFICATIONS);
        shadowOf(app.getSystemService(PowerManager.class))
                .setIgnoringBatteryOptimizations(app.getPackageName(), true);
        shadowOf(AppWidgetManager.getInstance(app)).setRequestPinAppWidgetSupported(true);
        return app;
    }

    // Same synthetic places, operator payload and time as device.seed and AdaptationChecks.
    static void seed(Context context, String theme, Scenario scenario, int lead) throws JSONException {
        SystemClock.setCurrentTimeMillis(scenario.now * 1000);
        JSONArray places = new JSONArray();
        for (int i = 0; i < GROUPS.length; i++) {
            places.put(new JSONObject().put("id", "p" + (i + 1))
                    .put("region", REGION).put("group", GROUPS[i])
                    .put("name", scenario == Scenario.LONG ? LONG_NAMES[i] : NAMES[i]));
        }
        JSONObject alerts = new JSONObject().put("off", true).put("on", true)
                .put("tomorrow", true).put("changes", true).put("leadOff", lead).put("leadOn", lead);
        context.getSharedPreferences("prefs", 0).edit()
                .putString("places", places.toString()).putString("current", "p4")
                .putString("theme", theme).putString("alerts_p4", alerts.toString())
                .putBoolean("asked_battery", true).putBoolean("quiet", true).apply();
        context.getSharedPreferences("updates", 0).edit().putLong("upd_checked", scenario.now).apply();
        context.getSharedPreferences("feed_state", 0).edit().putLong("checked_" + REGION, scenario.now).apply();
        var feed = context.getSharedPreferences("feed", 0).edit().clear();
        if (scenario != Scenario.NO_FEED) {
            feed.putString("json_" + REGION, operatorFeed(scenario).toString());
        }
        feed.apply();
        var history = context.getSharedPreferences("history", 0).edit();
        String past = history().toString();
        for (int i = 1; i <= GROUPS.length; i++) history.putString("p" + i, past);
        history.apply();
    }

    private static JSONObject operatorFeed(Scenario scenario) throws JSONException {
        JSONObject days = new JSONObject();
        JSONObject names = new JSONObject();
        for (String group : GROUPS) names.put(group, group);
        for (int offset = -1; offset <= 1; offset++) {
            JSONObject queues = new JSONObject();
            for (int i = 0; i < GROUPS.length; i++) {
                String value = i < 2 ? FIRST_TWO : offset == 1 ? NEXT_DAY : NORMAL;
                if (i == 3) value = selectedSlots(scenario, offset, value);
                queues.put(GROUPS[i], hours(value));
            }
            days.put(Long.toString(DAY.plusDays(offset).atStartOfDay(Logic.KYIV).toEpochSecond()), queues);
        }
        return new JSONObject().put("fact", new JSONObject().put("update", "01.11.2026 12:00")
                .put("data", days)).put("preset", new JSONObject().put("sch_names", names));
    }

    private static String selectedSlots(Scenario scenario, int offset, String fallback) {
        if (offset == 1) {
            return switch (scenario) {
                case MIDNIGHT, NIGHT -> slots(1, 48, '0');
                case TOMORROW -> slots(12, 20, '0');
                default -> fallback;
            };
        }
        if (offset != 0) return fallback;
        return switch (scenario) {
            case POSSIBLE -> slots(24, 30, '3', 36, 40, '0');
            case MIDNIGHT -> slots(0, 48, '0');
            case TOMORROW -> slots();
            case NIGHT -> slots(28, 43, '0');
            case SOON_OFF -> slots(0, 18, '0', 35, 39, '0');
            case SOON_MAYBE -> slots(0, 18, '0', 35, 39, '3');
            default -> fallback;
        };
    }

    private static String slots(int... ranges) {
        char[] slots = "1".repeat(48).toCharArray();
        for (int i = 0; i < ranges.length; i += 3) {
            Arrays.fill(slots, ranges[i], ranges[i + 1], (char) ranges[i + 2]);
        }
        return new String(slots);
    }

    private static JSONObject hours(String slots) throws JSONException {
        JSONObject result = new JSONObject();
        for (int i = 0; i < 24; i++) {
            String token = switch (slots.substring(i * 2, i * 2 + 2)) {
                case "11" -> "yes";
                case "00" -> "no";
                case "01" -> "first";
                case "10" -> "second";
                case "33" -> "maybe";
                case "31" -> "mfirst";
                case "13" -> "msecond";
                default -> throw new AssertionError(slots);
            };
            result.put(Integer.toString(i + 1), token);
        }
        return result;
    }

    private static JSONObject history() throws JSONException {
        JSONObject result = new JSONObject();
        int[] light = {32, 37, 25, 34, 29, 41, 48, 0};
        for (int i = 0; i < 30; i++) {
            if (i == 3 || i == 4) continue;
            int on = light[i % light.length];
            int maybe = i % 6 == 2 ? 2 : 0;
            result.put(DAY.minusDays(30L - i).toString(),
                    "1".repeat(on) + "3".repeat(maybe) + "0".repeat(Math.max(0, 48 - on - maybe)));
        }
        return result.put(DAY.toString(), NORMAL);
    }
}
