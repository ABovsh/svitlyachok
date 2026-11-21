package ua.abovsh.svitlo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import android.content.Context;
import android.os.SystemClock;
import java.lang.reflect.Method;
import java.time.LocalDate;
import org.junit.Test;

public class AndroidStoreTest extends AndroidRegressionFixture {
    @Test
    public void fallbackWithOnlyTomorrowDoesNotReplaceToday() throws Exception {
        long now = Sync.now();
        LocalDate today = Logic.today(now);
        context.getSharedPreferences(FEED, Context.MODE_PRIVATE).edit()
                .putString(STORED_FEED, feed(today, ALL_LIGHT, now)).apply();
        String before = stored();
        assertEquals(Store.FAILED, save(feed(today.plusDays(1), ALL_LIGHT, now),
                SystemClock.elapsedRealtime() + 1000));
        assertEquals(before, stored());
        assertEquals(0, Store.checkedAt(context, REGION));
    }

    @Test
    public void eligibleFallbackWithTodayIsStored() throws Exception {
        long now = Sync.now();
        LocalDate today = Logic.today(now);
        String changed = "0".repeat(2) + "1".repeat(46);
        assertEquals(Store.NEW, save(feed(today, changed, now), SystemClock.elapsedRealtime() + 1000));
        assertEquals(changed, Logic.day(Store.data(context, REGION), QUEUE, today));
        long checked = Store.checkedAt(context, REGION);
        assertTrue("Cache timestamp must fall within the save", checked >= now && checked <= Sync.now());
    }

    @Test
    public void expiredFallbackCannotWriteTheCacheOrItsTimestamp() throws Exception {
        long now = Sync.now();
        String current = feed(Logic.today(now), ALL_LIGHT, now);
        assertEquals(Store.FAILED, save(current, SystemClock.elapsedRealtime()));
        assertFalse(context.getSharedPreferences(FEED, Context.MODE_PRIVATE).contains(STORED_FEED));
        assertEquals(0, Store.checkedAt(context, REGION));
    }

    @Test
    public void officialSourceLeavesAThirdOfTheBudgetForFallback() {
        long start = SystemClock.elapsedRealtime();
        long total = 30_000;
        long official = RegionalFeed.officialEnd(start, start + total);
        assertEquals(20_000, official - start);
        assertTrue(official < start + total);
    }

    private String stored() {
        return context.getSharedPreferences(FEED, Context.MODE_PRIVATE).getString(STORED_FEED, null);
    }

    private int save(String body, long end) throws Exception {
        // Enter at the real persistence boundary, without a network dependency.
        Method save = Store.class.getDeclaredMethod("save", Context.class, String.class, String.class,
                String.class, String.class, long.class, Store.Request.class);
        save.setAccessible(true);
        return (int) save.invoke(null, context, REGION, "https://example.org/schedule.json",
                body, null, end, new Store.Request());
    }
}
