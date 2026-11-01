package ua.abovsh.svitlo;

import android.content.Context;
import android.content.SharedPreferences;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** The user's places and choices, kept on the phone only. */
final class Prefs {
    static final int MAX_PLACES = 4;
    static final int[] LEADS_OFF = {0, 10, 15, 30, 60};
    static final int[] LEADS_ON = {0, 5, 10, 15, 30};
    private static final String ALERTS = "alerts_";

    private Prefs() {}

    private static SharedPreferences p(Context c) {
        return c.getSharedPreferences("prefs", Context.MODE_PRIVATE);
    }

    // ---- places ------------------------------------------------------------------

    static List<Logic.Place> places(Context c) {
        List<Logic.Place> out = new ArrayList<>();
        try {
            JSONArray a = new JSONArray(p(c).getString("places", "[]"));
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Logic.Place pl = new Logic.Place();
                pl.id = o.getString("id");
                pl.name = o.getString("name");
                pl.region = o.getString("region");
                pl.group = o.getString("group");
                if (Logic.region(pl.region) != null) out.add(pl);
            }
        } catch (Exception ignored) {
            // nothing usable: the setup screen starts over
        }
        return out;
    }

    static void savePlaces(Context c, List<Logic.Place> list) {
        JSONArray a = new JSONArray();
        try {
            for (Logic.Place pl : list) {
                a.put(new JSONObject().put("id", pl.id).put("name", pl.name)
                        .put("region", pl.region).put("group", pl.group));
            }
        } catch (Exception ignored) {
            return;
        }
        p(c).edit().putString("places", a.toString()).apply();
    }

    static Logic.Place place(Context c, String id) {
        for (Logic.Place pl : places(c)) if (pl.id.equals(id)) return pl;
        return null;
    }

    static void putPlace(Context c, Logic.Place pl) {
        List<Logic.Place> list = places(c);
        boolean found = false;
        for (int i = 0; i < list.size(); i++) {
            if (list.get(i).id.equals(pl.id)) {
                list.set(i, pl);
                found = true;
            }
        }
        if (!found) list.add(pl);
        savePlaces(c, list);
    }

    static void removePlace(Context c, String id) {
        List<Logic.Place> list = places(c);
        list.removeIf(pl -> pl.id.equals(id));
        savePlaces(c, list);
        h(c).edit().remove(id).apply();
        p(c).edit().remove(ALERTS + id).remove("mute_" + id).apply();
        if (id.equals(current(c))) setCurrent(c, "");
    }

    static Set<String> regions(Context c) {
        Set<String> out = new HashSet<>();
        for (Logic.Place pl : places(c)) out.add(pl.region);
        return out;
    }

    /** The place shown on the main screen. */
    static String current(Context c) {
        return p(c).getString("current", "");
    }

    static void setCurrent(Context c, String id) {
        p(c).edit().putString("current", id).apply();
    }

    /** The bell of 1.2 that switched a place off; read once, when its own settings are made. */
    private static boolean muted(Context c, String place) {
        return p(c).getBoolean("mute_" + place, false);
    }


    // ---- history -------------------------------------------------------------------

    private static SharedPreferences h(Context c) {
        return c.getSharedPreferences("history", Context.MODE_PRIVATE);
    }

    /** The schedules seen for one place, by day ({"2026-10-30": "1111…"}), for the statistics. */
    static Map<LocalDate, String> history(Context c, String place) {
        Map<LocalDate, String> out = new HashMap<>();
        try {
            JSONObject o = new JSONObject(h(c).getString(place, "{}"));
            for (java.util.Iterator<String> it = o.keys(); it.hasNext(); ) {
                String day = it.next();
                out.put(LocalDate.parse(day), o.getString(day));
            }
        } catch (Exception ignored) {
            // nothing readable: the statistics start again
        }
        return out;
    }

    /** Published older days take precedence over the phone's last saved version. */
    static Map<LocalDate, String> knownHistory(Context c, Logic.Place place, Logic.Data data) {
        Map<LocalDate, String> days = history(c, place.id);
        if (data != null && !data.none) {
            for (Map.Entry<LocalDate, Map<String, String>> day : data.days.entrySet()) {
                String slots = day.getValue().get(place.group);
                if (slots != null) days.put(day.getKey(), slots);
            }
        }
        return days;
    }

    static void saveHistory(Context c, String place, Map<LocalDate, String> days) {
        JSONObject o = new JSONObject();
        try {
            for (Map.Entry<LocalDate, String> e : days.entrySet()) o.put(e.getKey().toString(), e.getValue());
        } catch (Exception ignored) {
            return;
        }
        h(c).edit().putString(place, o.toString()).apply();
    }

    // ---- widgets -------------------------------------------------------------------

    /** The places one widget shows, as ids "p1,p3"; empty = all. */
    static String widgetChoice(Context c, int widget) {
        return p(c).getString("w_" + widget, "");
    }

    static void setWidgetChoice(Context c, int widget, String ids) {
        p(c).edit().putString("w_" + widget, ids).apply();
    }

    static void forgetWidget(Context c, int widget) {
        p(c).edit().remove("w_" + widget).apply();
    }

    // ---- notifications -------------------------------------------------------------

    /** What one place notifies about; a place from before 1.3 takes the old common settings. */
    static Logic.Alerts alerts(Context c, String place) {
        return Logic.alertsFrom(p(c).getString(ALERTS + place, null), common(c), muted(c, place));
    }

    static void setAlerts(Context c, String place, Logic.Alerts a) {
        p(c).edit().putString(ALERTS + place, Logic.alertsJson(a)).apply();
    }

    /** The settings of 1.0–1.2, shared by all places; null when none were ever changed. */
    private static Logic.Alerts common(Context c) {
        SharedPreferences sp = p(c);
        if (!sp.contains("lead_off") && !sp.contains("lead_on") && !sp.contains("n_off") && !sp.contains("n_on")
                && !sp.contains("note_tomorrow") && !sp.contains("note_change")) return null;
        Logic.Alerts a = new Logic.Alerts();
        a.leadOff = sp.getInt("lead_off", a.leadOff);
        a.leadOn = sp.getInt("lead_on", a.leadOn);
        // 1.1.0 had no switches: a lead of 0 meant "never", unless "at the moment" was on
        boolean atChange = sp.getBoolean("at_change", false);
        a.off = sp.getBoolean("n_off", a.leadOff > 0 || atChange);
        a.on = sp.getBoolean("n_on", a.leadOn > 0 || atChange);
        a.tomorrow = sp.getBoolean("note_tomorrow", Logic.DEFAULT_TOMORROW);
        a.changes = sp.getBoolean("note_change", Logic.DEFAULT_CHANGES);
        return a;
    }

    static void setInt(Context c, String key, int v) {
        p(c).edit().putInt(key, v).apply();
    }

    static boolean flag(Context c, String key, boolean def) {
        return p(c).getBoolean(key, def);
    }

    static void setFlag(Context c, String key, boolean v) {
        p(c).edit().putBoolean(key, v).apply();
    }

    static boolean quietNights(Context c) {
        return flag(c, "quiet", Logic.DEFAULT_QUIET);
    }

    /** Reminders already shown, so a refetch never repeats one. */
    static Set<String> fired(Context c) {
        return new HashSet<>(p(c).getStringSet("fired", new HashSet<>()));
    }

    static void addFired(Context c, String key, long now) {
        Set<String> s = fired(c);
        s.removeIf(k -> {
            try {
                return Long.parseLong(k.substring(k.lastIndexOf('|') + 1)) < now - 86400;
            } catch (Exception e) {
                return true;
            }
        });
        s.add(key);
        p(c).edit().putStringSet("fired", s).apply();
    }

    // ---- look --------------------------------------------------------------------

    /** "system", "light" or "dark". */
    static String theme(Context c) {
        return p(c).getString("theme", "system");
    }

    static void setTheme(Context c, String t) {
        p(c).edit().putString("theme", t).apply();
    }

    static boolean once(Context c, String key) {
        if (p(c).getBoolean(key, false)) return false;
        p(c).edit().putBoolean(key, true).apply();
        return true;
    }
}
