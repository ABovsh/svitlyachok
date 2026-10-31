package ua.abovsh.svitlo;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

/** The user's places and choices, kept on the phone only. */
final class Prefs {
    static final int MAX_PLACES = 4;
    static final int[] LEADS_OFF = {0, 10, 15, 30, 60};
    static final int[] LEADS_ON = {0, 5, 10, 15, 30};

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

    static boolean muted(Context c, String place) {
        return p(c).getBoolean("mute_" + place, false);
    }

    static void setMuted(Context c, String place, boolean v) {
        p(c).edit().putBoolean("mute_" + place, v).apply();
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

    static Logic.Alerts alerts(Context c) {
        Logic.Alerts a = new Logic.Alerts();
        SharedPreferences sp = p(c);
        a.leadOff = sp.getInt("lead_off", a.leadOff);
        a.leadOn = sp.getInt("lead_on", a.leadOn);
        // 1.1.0 had no switches: a lead of 0 meant "never", unless "at the moment" was on
        boolean atChange = sp.getBoolean("at_change", false);
        a.off = sp.getBoolean("n_off", a.leadOff > 0 || atChange);
        a.on = sp.getBoolean("n_on", a.leadOn > 0 || atChange);
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

    static boolean tomorrowNotes(Context c) {
        return flag(c, "note_tomorrow", Logic.DEFAULT_TOMORROW);
    }

    static boolean changeNotes(Context c) {
        return flag(c, "note_change", Logic.DEFAULT_CHANGES);
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
