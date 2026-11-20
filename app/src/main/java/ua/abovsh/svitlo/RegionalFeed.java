package ua.abovsh.svitlo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;

/** Regional feeds retain the operator's dates and every published half hour. */
final class RegionalFeed {
    static final String CV = "chernivtsi";
    static final String IF = "ivano_frankivsk";
    static final String API = "https://be-svitlo.oe.if.ua/schedule-by-queue?queue=";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu")
            .withResolverStyle(ResolverStyle.STRICT);

    /** Our collector refreshes "checked" at least hourly; half an hour more covers its cadence and caches. */
    static final long CV_DOWN_S = 90 * 60L;

    private RegionalFeed() {}

    static Logic.Data parse(JSONObject file) throws JSONException, Logic.BadFile {
        String region = file.getString("region");
        boolean numbered = CV.equals(region);
        if (!numbered && !IF.equals(region)) throw new Logic.BadFile("unknown region");
        Logic.Data data = new Logic.Data();
        if (numbered) {
            // our collector: "updated" is the last change, "checked" its last successful look at the site
            data.updated = time(file.getString("updated"));
            data.sourceTime = time(file.getString("checked"));
            data.sourceOk = false;
            data.downAfter = CV_DOWN_S;
        } else {
            data.updated = Logic.sourceTime(file.getString("updated"));
            data.sourceTime = data.updated;
        }
        JSONObject days = file.getJSONObject("days");
        if (days.length() == 0 || days.length() > Logic.MAX_DAYS) throw new Logic.BadFile("invalid dates");
        TreeSet<String> groups = new TreeSet<>(Logic::compareGroups);
        for (Iterator<String> it = days.keys(); it.hasNext();) {
            String key = it.next();
            LocalDate day;
            try { day = LocalDate.parse(key); }
            catch (java.time.DateTimeException e) { throw new Logic.BadFile("invalid date"); }
            data.days.put(day, readGroups(days.getJSONObject(key), groups, numbered));
        }
        data.groups.addAll(groups);
        return data;
    }

    private static Map<String, String> readGroups(JSONObject rows, Set<String> groups, boolean numbered)
            throws JSONException, Logic.BadFile {
        Map<String, String> slots = new HashMap<>();
        for (Iterator<String> it = rows.keys(); it.hasNext();) {
            String group = it.next();
            String value = rows.getString(group);
            if (!group.matches(numbered ? "[1-9]|1[0-2]" : "[1-6]\\.[12]") || !value.matches("[013]{48}"))
                throw new Logic.BadFile("invalid queue or half hours");
            String id = numbered ? group : "GPV" + group;
            groups.add(id);
            slots.put(id, value);
        }
        // the collector publishes a day only with all twelve groups; fewer is a broken file
        if (slots.isEmpty() || (numbered && slots.size() != 12)) throw new Logic.BadFile("incomplete day");
        return slots;
    }

    private static long time(String value) throws Logic.BadFile {
        try { return OffsetDateTime.parse(value).toEpochSecond(); }
        catch (java.time.DateTimeException e) { throw new Logic.BadFile("invalid time"); }
    }

    /** Every queue asked for has today: tomorrow alone would hide a missing today from the reserve file. */
    static boolean covers(JSONObject days, Set<String> queues, LocalDate today) {
        JSONObject current = days.optJSONObject(today.toString());
        for (String group : queues) if (current == null || !current.has(group)) return false;
        return !queues.isEmpty();
    }

    /** The operator gets two thirds of the time left: a slow answer must leave the reserve file its turn. */
    static long officialEnd(long now, long end) { return now + (end - now) * 2 / 3; }

    static String queue(int i) { return (1 + i / 2) + "." + (1 + i % 2); }

    static void addQueue(JSONObject days, String group, String response) throws JSONException {
        JSONArray list = new JSONArray(response);
        for (int i = 0; i < list.length(); i++) {
            JSONObject entry = list.getJSONObject(i);
            String date = LocalDate.parse(entry.getString("eventDate"), DATE).toString();
            JSONObject rows = days.optJSONObject(date);
            if (rows == null) { rows = new JSONObject(); days.put(date, rows); }
            JSONArray intervals = entry.getJSONObject("queues").getJSONArray(group);
            char[] slots = new char[48];
            Arrays.fill(slots, '1');
            for (int j = 0; j < intervals.length(); j++) mark(slots, intervals.getJSONObject(j));
            rows.put(group, new String(slots));
        }
    }

    private static void mark(char[] slots, JSONObject interval) throws JSONException {
        int status = interval.getInt("status");
        int from = halfHour(interval.getString("from"));
        String to = interval.getString("to");
        int end = Arrays.asList("00:00", "23:59", "24:00").contains(to) ? 48 : halfHour(to);
        if (from >= end) throw new JSONException("Invalid interval");
        // Only 1 is known: the operator's site shows every interval as an outage and ignores the status.
        // Any other value is a format we do not understand, so the reserve file answers instead.
        if (status != 1) throw new JSONException("Unknown status " + status);
        Arrays.fill(slots, from, end, '0');
    }

    private static int halfHour(String time) throws JSONException {
        if (!time.matches("(?:[01]\\d|2[0-3]):[03]0")) throw new JSONException("Invalid time");
        return Integer.parseInt(time.substring(0, 2)) * 2 + (time.endsWith("30") ? 1 : 0);
    }
}
