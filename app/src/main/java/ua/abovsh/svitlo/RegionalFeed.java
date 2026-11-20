package ua.abovsh.svitlo;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.time.format.ResolverStyle;
import java.util.Arrays;
import java.util.Iterator;
import java.util.TreeSet;

/** Regional feeds retain the operator's dates and every published half hour. */
final class RegionalFeed {
    static final String CV = "chernivtsi";
    static final String IF = "ivano_frankivsk";
    static final String API = "https://be-svitlo.oe.if.ua/schedule-by-queue?queue=";
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("dd.MM.uuuu")
            .withResolverStyle(ResolverStyle.STRICT);

    private RegionalFeed() {}

    static Logic.Data parse(JSONObject file) throws JSONException, Logic.BadFile {
        String region = file.getString("region");
        boolean numbered = CV.equals(region);
        if (!numbered && !IF.equals(region)) throw new Logic.BadFile("unknown region");
        Logic.Data data = new Logic.Data();
        data.updated = Logic.sourceTime(file.getString("updated"));
        data.sourceTime = data.updated;
        JSONObject days = file.getJSONObject("days");
        if (days.length() == 0 || days.length() > Logic.MAX_DAYS) throw new Logic.BadFile("invalid dates");
        TreeSet<String> groups = new TreeSet<>(Logic::compareGroups);
        for (Iterator<String> it = days.keys(); it.hasNext();) {
            String key = it.next();
            LocalDate day;
            try { day = LocalDate.parse(key); }
            catch (java.time.DateTimeException e) { throw new Logic.BadFile("invalid date"); }
            JSONObject rows = days.getJSONObject(key);
            java.util.Map<String, String> slots = new java.util.HashMap<>();
            for (int i = 0; i < 12; i++) {
                String group = numbered ? Integer.toString(i + 1) : queue(i);
                if (!numbered && !rows.has(group)) continue;
                String value = rows.getString(group);
                if (!value.matches("[013]{48}")) throw new Logic.BadFile("invalid half hours");
                String id = numbered ? group : "GPV" + group;
                groups.add(id);
                slots.put(id, value);
            }
            if (slots.isEmpty() || slots.size() != rows.length()) throw new Logic.BadFile("invalid groups");
            data.days.put(day, slots);
        }
        data.groups.addAll(groups);
        return data;
    }

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
        if (status != 0) Arrays.fill(slots, from, end, status == 1 ? '0' : '3');
    }

    private static int halfHour(String time) throws JSONException {
        if (!time.matches("(?:[01][0-9]|2[0-3]):[03]0")) throw new JSONException("Invalid time");
        return Integer.parseInt(time.substring(0, 2)) * 2 + (time.endsWith("30") ? 1 : 0);
    }
}
