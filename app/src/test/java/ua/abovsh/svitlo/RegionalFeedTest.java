package ua.abovsh.svitlo;

import org.json.JSONObject;
import org.junit.Test;
import java.time.LocalDate;
import static org.junit.Assert.*;

public class RegionalFeedTest {
    @Test public void invalidRegionalCacheIsRejected() throws Exception {
        JSONObject rows = new JSONObject().put("1.1", "1");
        JSONObject file = new JSONObject().put("region", RegionalFeed.IF).put("updated","2026-11-20T08:00:00Z")
                .put("days",new JSONObject().put("2026-11-20",rows));
        assertThrows(Logic.BadFile.class, () -> Logic.parse(file.toString()));
        rows.put("1.1", "1".repeat(48));
        assertEquals(1, Logic.parse(Logic.compactFile(file.toString())).groups.size());
        rows.put("other", "1".repeat(48));
        assertThrows(Logic.BadFile.class, () -> Logic.parse(file.toString()));
    }

    @Test public void needsTodayOfEveryQueue() throws Exception {
        LocalDate today = LocalDate.parse("2026-11-20");
        java.util.Set<String> chosen = java.util.Collections.singleton("1.1");
        JSONObject row = new JSONObject().put("1.1","1".repeat(48));
        JSONObject days = new JSONObject().put(today.minusDays(1).toString(), row);
        assertFalse(RegionalFeed.covers(days, chosen, today));
        days.put(today.plusDays(1).toString(),row);
        assertFalse(RegionalFeed.covers(days, chosen, today));  // tomorrow alone hides a missing today
        days.put(today.toString(),row);
        assertTrue(RegionalFeed.covers(days, chosen, today));
        assertFalse(RegionalFeed.covers(days, java.util.Collections.singleton("2.2"), today));
    }

    @Test public void officialIntervalsUseTheSavedQueueIdAndMidnight() throws Exception {
        JSONObject days = new JSONObject();
        String queue = RegionalFeed.queue(11);
        String response = "[{\"eventDate\":\"20.11.2026\",\"queues\":{\"6.2\":[{\"from\":\"22:30\",\"to\":\"00:00\",\"status\":1}]}}]";
        RegionalFeed.addQueue(days, queue, response);
        JSONObject file = new JSONObject().put("region", RegionalFeed.IF).put("updated","1795161600").put("days",days);
        assertEquals("1".repeat(45) + "000", Logic.day(Logic.parse(file.toString()),"GPV6.2",LocalDate.parse("2026-11-20")));
        assertThrows(org.json.JSONException.class, () -> RegionalFeed.addQueue(days, queue, response.replace("22:30","22:10")));
    }

    @Test public void onlyAnOutageStatusIsUnderstood() throws Exception {
        // the operator's site shows every listed interval as an outage and never reads the status
        String response = "[{\"eventDate\":\"20.11.2026\",\"queues\":{\"1.1\":[{\"from\":\"10:00\",\"to\":\"12:00\",\"status\":1}]}}]";
        RegionalFeed.addQueue(new JSONObject(), "1.1", response);
        for (String other : new String[] {"0", "2", "3", "-1"}) {
            assertThrows(org.json.JSONException.class,
                    () -> RegionalFeed.addQueue(new JSONObject(), "1.1", response.replace("\"status\":1", "\"status\":" + other)));
        }
    }

    @Test public void theOperatorLeavesTimeForTheReserveFile() {
        assertEquals(20_000, RegionalFeed.officialEnd(0, 30_000));
        assertEquals(1_020_000, RegionalFeed.officialEnd(1_000_000, 1_030_000));
    }
    private static JSONObject chernivtsi(JSONObject rows) throws Exception {
        return new JSONObject().put("region", RegionalFeed.CV).put("updated", "2026-11-20T08:00:00+02:00")
                .put("checked", "2026-11-20T21:00:00+02:00").put("days", new JSONObject().put("2026-11-20", rows));
    }

    @Test public void chernivtsiKeepsAllTwelveNumberedGroups() throws Exception {
        String slots = "013".repeat(16);
        JSONObject rows = new JSONObject();
        for (int i = 1; i <= 12; i++) rows.put(Integer.toString(i), slots);
        JSONObject file = chernivtsi(rows);
        Logic.Data data = Logic.parse(Logic.compactFile(file.toString()));
        assertEquals(12, data.groups.size());
        assertEquals("1", data.groups.get(0));
        assertEquals("12", data.groups.get(11));
        assertEquals(slots, Logic.day(data, "12", LocalDate.parse("2026-11-20")));
        rows.remove("6");
        assertThrows(Logic.BadFile.class, () -> Logic.parse(file.toString()));  // a day misses a group
        rows.put("6", slots).put("1.1", slots);
        assertThrows(Logic.BadFile.class, () -> Logic.parse(file.toString()));  // queues are not Chernivtsi groups
        rows.remove("1.1");
        file.remove("checked");
        assertThrows(Logic.BadFile.class, () -> Logic.parse(file.toString()));  // the collector's check time is required
    }

    @Test public void prykarpattiaDoesNotTakeNumberedGroups() throws Exception {
        JSONObject file = new JSONObject().put("region", RegionalFeed.IF).put("updated","2026-11-20T08:00:00Z")
                .put("days",new JSONObject().put("2026-11-20",new JSONObject().put("1", "1".repeat(48))));
        assertThrows(Logic.BadFile.class, () -> Logic.parse(file.toString()));
    }

    @Test public void chernivtsiTellsWhenTheCollectorStopped() throws Exception {
        JSONObject rows = new JSONObject();
        for (int i = 1; i <= 12; i++) rows.put(Integer.toString(i), "1".repeat(48));
        Logic.Data data = Logic.parse(chernivtsi(rows).toString());
        long checked = java.time.OffsetDateTime.parse("2026-11-20T21:00:00+02:00").toEpochSecond();
        assertEquals(java.time.OffsetDateTime.parse("2026-11-20T08:00:00+02:00").toEpochSecond(), data.updated);
        // the collector refreshes its check time hourly: a quiet day is not a site that is down
        assertFalse(Logic.siteDown(data, checked + 89 * 60));
        assertTrue(Logic.siteDown(data, checked + 90 * 60));
    }

    @Test public void chernivtsiOffersItsTwelveGroupsBeforeTheFileArrives() {
        assertEquals(java.util.Arrays.asList("1","2","3","4","5","6","7","8","9","10","11","12"), Logic.defaultGroups(RegionalFeed.CV));
        assertEquals("GPV1.1", Logic.defaultGroups("kyiv").get(0));
        assertEquals(12, Logic.defaultGroups("kyiv").size());
    }
}
