package ua.abovsh.svitlo;

import org.json.JSONObject;
import org.junit.Test;
import java.time.LocalDate;
import static org.junit.Assert.*;

public class RegionalFeedTest {
    @Test public void numberedGroupsKeepAllHalfHours() throws Exception {
        String slots = "013".repeat(16);
        JSONObject rows = new JSONObject();
        for (int i = 1; i <= 12; i++) rows.put(Integer.toString(i), slots);
        JSONObject file = new JSONObject().put("region", "chernivtsi").put("updated","2026-11-20T08:00:00Z")
                .put("days",new JSONObject().put("2026-11-20",rows));
        Logic.Data data = Logic.parse(Logic.compactFile(file.toString()));
        assertEquals("12", data.groups.get(11));
        assertEquals(slots, Logic.day(data, "12", LocalDate.parse("2026-11-20")));
        rows.remove("6");
        assertThrows(Logic.BadFile.class, () -> Logic.parse(file.toString()));
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
}
