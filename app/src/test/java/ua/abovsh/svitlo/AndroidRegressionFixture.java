package ua.abovsh.svitlo;

import android.content.Context;
import android.os.SystemClock;
import java.time.LocalDate;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 35, qualifiers = "uk-rUA-w360dp-h800dp-notnight-mdpi")
public abstract class AndroidRegressionFixture {
    static final LocalDate DAY = LocalDate.of(2026, 11, 20);
    static final long NOW = DAY.atTime(10, 0).atZone(Logic.KYIV).toEpochSecond();
    static final String QUEUE = "GPV1.1";
    static final String REGION = RegionalFeed.IF;
    static final String FEED = "feed";
    static final String STORED_FEED = "json_" + REGION;
    static final String ALL_LIGHT = "1".repeat(48);
    Context context;

    @Before
    public void fixClock() {
        context = RuntimeEnvironment.getApplication();
        SystemClock.setCurrentTimeMillis(NOW * 1000);
    }

    static Logic.Place place(String id, String name) {
        Logic.Place result = new Logic.Place();
        result.id = id;
        result.name = name;
        result.region = REGION;
        result.group = QUEUE;
        return result;
    }

    static String feed(LocalDate date, String slots) throws Exception {
        return feed(date, slots, NOW);
    }

    static String feed(LocalDate date, String slots, long updated) throws Exception {
        return new JSONObject().put("region", REGION)
                .put("updated", Long.toString(updated))
                .put("days", new JSONObject().put(date.toString(), new JSONObject().put("1.1", slots)))
                .toString();
    }
}
