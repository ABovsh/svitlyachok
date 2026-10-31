package ua.abovsh.svitlo;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Everything Svitlyachok says, as pure functions (LogicTest). Schedule only: the app knows
 * what the oblenergo published, never whether a house really has power.
 *
 * A day is 48 half-hour slots by the Kyiv wall clock: '1' power, '0' outage, '3' possible
 * outage, '2' unknown. A timeline is today + tomorrow, 96 slots from today's midnight.
 */
public final class Logic {
    public static final ZoneId KYIV = ZoneId.of("Europe/Kyiv");
    public static final int OFF = 0, ON = 1, UNKNOWN = 2, MAYBE = 3;
    public static final long POLL_S = 15 * 60;   // the widget fetches by itself this often
    public static final long SOON_S = 30 * 60;   // the screen says "soon" this close to a change
    public static final long LATE_S = 10 * 60;   // a delayed notification still worth showing
    public static final long STALE_S = 3 * 3600; // no successful check this long: say so
    public static final int QUIET_FROM_H = 23, QUIET_TO_H = 7;

    // ---- regions ---------------------------------------------------------------

    public static final class Region {
        public final String id, name, dso;
        public final String[] urls;

        Region(String id, String name, String dso, String... urls) {
            this.id = id;
            this.name = name;
            this.dso = dso;
            this.urls = urls;
        }
    }

    private static final String B42 = "https://raw.githubusercontent.com/Baskerville42/outage-data-ua/main/data/";
    private static final String Y29 = "https://raw.githubusercontent.com/yaroslav2901/OE_OUTAGE_DATA/main/data/";

    /** Open community archives of the oblenergo schedules on GitHub, one JSON format for all. */
    public static final Region[] REGIONS = {
        new Region("kyiv_region", "Київська область", "https://www.dtek-krem.com.ua/ua/shutdowns", B42 + "kyiv-region.json"),
        new Region("vinnytsia", "Вінницька область", "",
                "https://raw.githubusercontent.com/olnet93/gpv-voe-vinnytsia/main/data/Vinnytsiaoblenerho.json",
                "https://raw.githubusercontent.com/vn-progr/gpv-voe-vinnytsia/main/data/Vinnytsiaoblenerho.json"),
        new Region("dnipro", "Дніпропетровська область", "https://www.dtek-dnem.com.ua/ua/shutdowns", B42 + "dnipro.json"),
        new Region("zhytomyr", "Житомирська область", "", Y29 + "Zhytomyroblenergo.json"),
        new Region("uzhhorod", "Закарпатська область", "", Y29 + "Zakarpattiaoblenerho.json"),
        new Region("zaporizhzhia", "Запорізька область", "",
                "https://raw.githubusercontent.com/milkmann/gpv-zoe-zaporizhzhia/main/data/Zaporizhzhiaoblenergo.json",
                Y29 + "Zaporizhzhiaoblenergo.json"),
        new Region("ivano_frankivsk", "Івано-Франківська область", "", Y29 + "Prykarpattiaoblenerho.json"),
        new Region("lviv", "Львівська область", "", Y29 + "Lvivoblenerho.json"),
        new Region("odesa", "Одеська область", "https://www.dtek-oem.com.ua/ua/shutdowns", B42 + "odesa.json"),
        new Region("poltava", "Полтавська область", "", Y29 + "Poltavaoblenergo.json"),
        new Region("rivne", "Рівненська область", "", Y29 + "Rivneoblenergo.json"),
        new Region("ternopil", "Тернопільська область", "", Y29 + "Ternopiloblenerho.json"),
        new Region("khmelnytskyi", "Хмельницька область", "", Y29 + "Khmelnytskoblenerho.json"),
        new Region("chernihiv", "Чернігівська область", "", Y29 + "Chernihivoblenergo.json"),
    };

    public static Region region(String id) {
        for (Region r : REGIONS) if (r.id.equals(id)) return r;
        return null;
    }

    // ---- parsing ---------------------------------------------------------------

    /** One region's published schedule. */
    public static final class Data {
        public Map<LocalDate, Map<String, String>> days = new HashMap<>();
        public boolean none;          // "data": [] — no outages published at all
        public long updated;          // when the oblenergo last changed it, 0 unknown
        public List<String> groups = new ArrayList<>();
    }

    private static final DateTimeFormatter[] UPDATE_FORMATS = {
        DateTimeFormatter.ofPattern("dd.MM.yyyy HH:mm"), DateTimeFormatter.ofPattern("HH:mm dd.MM.yyyy"),
    };

    // The files come from third-party repositories: anything outside these bounds is not a schedule.
    static final int MAX_DAYS = 14;
    static final String GROUP_NAME = "[A-Za-z0-9._-]{1,16}";

    static final int MAX_NESTING = 16;  // a schedule needs 5

    /** The deepest nesting of objects and arrays, brackets inside strings not counted. */
    static int nesting(String raw) {
        int depth = 0, max = 0;
        boolean text = false;
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            if (text) {
                if (c == '\\') i++;
                else if (c == '"') text = false;
            } else if (c == '"') {
                text = true;
            } else if (c == '{' || c == '[') {
                max = Math.max(max, ++depth);
            } else if (c == '}' || c == ']') {
                depth--;
            }
        }
        return max;
    }

    /** Parse a region file; throws on any shape it does not know, so garbage never replaces good data. */
    public static Data parse(String raw) throws Exception {
        if (nesting(raw) > MAX_NESTING) throw new Exception("nested too deep");
        JSONObject fact = new JSONObject(raw).getJSONObject("fact");
        Data d = new Data();
        Object data = fact.get("data");
        TreeSet<String> groups = new TreeSet<>();
        if (data instanceof JSONArray) {
            if (((JSONArray) data).length() > 0) throw new Exception("unknown data list");
            d.none = true;
        } else {
            JSONObject days = (JSONObject) data;
            if (days.length() > MAX_DAYS) throw new Exception("too many days");
            for (Iterator<String> it = days.keys(); it.hasNext(); ) {
                String key = it.next();
                if (!key.matches("\\d{6,12}")) throw new Exception("bad day " + key);
                LocalDate date = Instant.ofEpochSecond(Long.parseLong(key)).atZone(KYIV).toLocalDate();
                JSONObject gs = days.getJSONObject(key);
                Map<String, String> m = new HashMap<>();
                for (Iterator<String> g = gs.keys(); g.hasNext(); ) {
                    String group = g.next();
                    if (!group.matches(GROUP_NAME)) throw new Exception("bad group " + group);
                    m.put(group, hours(gs.getJSONObject(group)));
                    groups.add(group);
                }
                d.days.put(date, m);
            }
        }
        if (groups.isEmpty()) {
            JSONObject preset = new JSONObject(raw).optJSONObject("preset");
            JSONObject names = preset == null ? null : preset.optJSONObject("sch_names");
            if (names != null) for (Iterator<String> g = names.keys(); g.hasNext(); ) {
                String group = g.next();
                if (group.matches(GROUP_NAME)) groups.add(group);
            }
        }
        d.groups = new ArrayList<>(groups);
        String up = fact.optString("update", "");
        for (DateTimeFormatter f : UPDATE_FORMATS) {
            try {
                d.updated = LocalDateTime.parse(up, f).atZone(KYIV).toEpochSecond();
                break;
            } catch (Exception ignored) {
                // try the other format
            }
        }
        return d;
    }

    /** Hours "1".."24" (or "0".."23") of yes/no/first/second/maybe/mfirst/msecond as 48 slots. */
    static String hours(JSONObject h) throws Exception {
        boolean zero = h.has("0");
        StringBuilder b = new StringBuilder(48);
        for (int hour = 0; hour < 24; hour++) {
            String v = h.optString(String.valueOf(zero ? hour : hour + 1), "yes");
            switch (v) {
                case "yes": b.append("11"); break;
                case "no": b.append("00"); break;
                case "first": b.append("01"); break;
                case "second": b.append("10"); break;
                case "maybe": b.append("33"); break;
                case "mfirst": b.append("31"); break;
                case "msecond": b.append("13"); break;
                default: b.append("22");
            }
        }
        return b.toString();
    }

    /** "GPV1.1" -> "1.1" */
    public static String queue(String group) {
        return group.replaceFirst("^GPV", "");
    }

    // ---- places and timelines --------------------------------------------------

    /** A place the user follows: their own name for it, a region and a queue. */
    public static final class Place {
        public String id = "", name = "", region = "", group = "";
    }

    /** The 48 slots of a day, or null while it is not published. */
    public static String day(Data d, String group, LocalDate date) {
        if (d == null) return null;
        if (d.none) return repeat('1', 48);
        Map<String, String> m = d.days.get(date);
        return m == null ? null : m.get(group);
    }

    public static String timeline(Data d, String group, LocalDate today) {
        String a = day(d, group, today), b = day(d, group, today.plusDays(1));
        return (a != null ? a : repeat('2', 48)) + (b != null ? b : repeat('2', 48));
    }

    static String repeat(char c, int n) {
        StringBuilder b = new StringBuilder(n);
        for (int i = 0; i < n; i++) b.append(c);
        return b.toString();
    }

    static ZonedDateTime z(long t) {
        return Instant.ofEpochSecond(t).atZone(KYIV);
    }

    public static LocalDate today(long now) {
        return z(now).toLocalDate();
    }

    /** Slot of t in the timeline that starts at base, or -1 before it. */
    public static int index(LocalDate base, long t) {
        ZonedDateTime z = z(t);
        long days = ChronoUnit.DAYS.between(base, z.toLocalDate());
        if (days < 0) return -1;
        return (int) Math.min(Integer.MAX_VALUE, days * 48 + z.getHour() * 2 + (z.getMinute() >= 30 ? 1 : 0));
    }

    /** Wall-clock start of a slot (DST days keep their slots on the clock). */
    public static long slotStart(LocalDate base, int idx) {
        return base.plusDays(idx / 48).atTime((idx % 48) / 2, (idx % 2) * 30).atZone(KYIV).toEpochSecond();
    }

    /** Changes after slot `from`: {slot, new state char}, until the data ends. */
    static List<int[]> changesFrom(String tl, int from) {
        List<int[]> out = new ArrayList<>();
        if (from < 0 || from >= tl.length() || tl.charAt(from) == '2') return out;
        char cur = tl.charAt(from);
        for (int j = from + 1; j < tl.length(); j++) {
            char c = tl.charAt(j);
            if (c == '2') break;
            if (c != cur) {
                out.add(new int[] {j, c});
                cur = c;
            }
        }
        return out;
    }

    // ---- time words ------------------------------------------------------------

    public static String hm(long t) {
        ZonedDateTime z = z(t);
        return String.format(Locale.ROOT, "%02d:%02d", z.getHour(), z.getMinute());
    }

    static long dayDiff(long from, long to) {
        return ChronoUnit.DAYS.between(z(from).toLocalDate(), z(to).toLocalDate());
    }

    /** "1 год 05 хв", "42 хв", "менше хвилини", "1 д 3 год" */
    public static String waitText(long s) {
        if (s < 60) return "менше хвилини";
        long d = s / 86400, h = (s % 86400) / 3600, m = (s % 3600) / 60;
        if (d > 0) return d + " д " + h + " год";
        return h > 0 ? String.format(Locale.ROOT, "%d год %02d хв", h, m) : m + " хв";
    }

    /** The end of something as a clock: "18:30", "24:00" for tonight's midnight, "завтра 06:00". */
    public static String until(long t, long now) {
        long d = dayDiff(now, t);
        if (d == 1 && hm(t).equals("00:00")) return "24:00";
        return d == 1 ? "завтра " + hm(t) : hm(t);
    }

    public static boolean quiet(long t) {
        int h = z(t).getHour();
        return h >= QUIET_FROM_H || h < QUIET_TO_H;
    }

    /** "графік оновлено сьогодні о 07:10", "… вчора о 20:25", "… 27.10 о 07:10" */
    public static String updated(long updated, long now) {
        if (updated <= 0) return "";
        long d = dayDiff(updated, now);
        String day = d == 0 ? "сьогодні" : d == 1 ? "вчора"
                : String.format(Locale.ROOT, "%02d.%02d", z(updated).getDayOfMonth(), z(updated).getMonthValue());
        return "графік оновлено " + day + " о " + hm(updated);
    }

    // ---- now -------------------------------------------------------------------

    public static final class Now {
        public int state = UNKNOWN;
        public String title = "", big = "", hint = "", then = "";
        public boolean soon;
        public long change;
    }

    public static Now now(String tl, LocalDate base, long now, boolean none) {
        Now n = new Now();
        int cur = index(base, now);
        char c = cur >= 0 && cur < tl.length() ? tl.charAt(cur) : '2';
        if (c == '2') {
            n.title = "Графіка немає";
            n.big = "ще не опубліковано";
            n.hint = "щойно обленерго його оприлюднить, він з'явиться тут";
            return n;
        }
        n.state = c == '1' ? ON : c == '0' ? OFF : MAYBE;
        n.title = c == '1' ? "Світло є" : c == '0' ? "Світла немає" : "Можливе вимкнення";
        if (none) {
            n.big = "без вимкнень";
            n.hint = "відключень за графіком не заплановано";
            return n;
        }
        List<int[]> ch = changesFrom(tl, cur);
        if (ch.isEmpty()) {
            int j = cur;
            while (j < tl.length() && tl.charAt(j) == c) j++;
            if (j < tl.length()) {
                n.big = "до кінця дня";
                n.hint = "графік на завтра ще не опубліковано";
            } else {
                n.big = c == '1' ? "без вимкнень" : "понад добу";
                n.hint = c == '1' ? "сьогодні й завтра вимкнень немає" : "за графіком і завтра";
            }
            return n;
        }
        long change = slotStart(base, ch.get(0)[0]);
        char next = (char) ch.get(0)[1];
        n.change = change;
        n.big = "до " + until(change, now);
        n.soon = change - now <= SOON_S;
        String in = " через " + waitText(change - now);
        if (ch.size() > 1 || next != '1') {  // "потім світло" with no end tells nothing new
            n.then = "потім " + (next == '0' ? "без світла" : next == '1' ? "світло" : "можливе вимкнення")
                    + (ch.size() > 1 ? " до " + until(slotStart(base, ch.get(1)[0]), now) : "");
        }
        if (next == '0') n.hint = "вимкнуть" + in;
        else if (next == '3') n.hint = "можливе вимкнення" + in;
        else n.hint = c == '0' ? "увімкнуть" + in : "можливе вимкнення закінчиться" + in;
        return n;
    }

    // ---- day lists -------------------------------------------------------------

    public static final class Outage {
        public static final int PAST = 0, NOW = 1, LATER = 2;
        public String label = "", length = "";
        public int when;
        public boolean maybe;
        public int from, to;  // slots
    }

    static String clock(int slot) {
        return slot >= 48 ? "24:00" : String.format(Locale.ROOT, "%02d:%02d", slot / 2, (slot % 2) * 30);
    }

    static String length(int halves) {
        if (halves % 2 == 0) return halves / 2 + " год";
        return halves / 2 > 0 ? halves / 2 + " год 30 хв" : "30 хв";
    }

    /** Outages (and possible outages) of one day; cur = the slot of now in it, -1 for another day. */
    public static List<Outage> outages(String day, int cur) {
        List<Outage> out = new ArrayList<>();
        if (day == null) return out;
        for (int i = 0; i < day.length(); ) {
            char c = day.charAt(i);
            if (c != '0' && c != '3') {
                i++;
                continue;
            }
            int j = i;
            while (j < day.length() && day.charAt(j) == c) j++;
            Outage o = new Outage();
            o.from = i;
            o.to = j;
            o.maybe = c == '3';
            o.label = clock(i) + " – " + clock(j);
            o.length = length(j - i);
            o.when = cur < 0 ? Outage.LATER : cur >= j ? Outage.PAST : cur >= i ? Outage.NOW : Outage.LATER;
            out.add(o);
            i = j;
        }
        return out;
    }

    public static String dayTotal(String day) {
        if (day == null) return "ще не опубліковано";
        int off = 0;
        for (int i = 0; i < day.length(); i++) if (day.charAt(i) == '0') off++;
        if (off == 0) return "без вимкнень";
        return "без світла " + length(off);
    }

    /** A day for a screen reader: "Сьогодні: без світла 06:00 – 08:00; можливе вимкнення 20:00 – 22:00". */
    public static String describe(String title, String day) {
        if (day == null) return title + ": графік ще не опубліковано";
        StringBuilder maybe = new StringBuilder();
        for (Outage o : outages(day, -1)) if (o.maybe) maybe.append(maybe.length() > 0 ? ", " : "").append(o.label);
        String off = list(day);
        return title + ": " + (off.isEmpty() ? "без вимкнень" : "без світла " + off)
                + (maybe.length() > 0 ? "; можливе вимкнення " + maybe : "");
    }

    // ---- notifications ---------------------------------------------------------

    /**
     * What the user wants to hear. Out of the box only two: before the power goes off and before
     * it comes back. A lead of 0 minutes means at the moment of the change.
     */
    public static final class Alerts {
        public boolean off = true, on = true;
        public int leadOff = 30, leadOn = 15;
    }

    /** Everything else is opt-in. */
    public static final boolean DEFAULT_TOMORROW = false, DEFAULT_CHANGES = false, DEFAULT_QUIET = false;

    public static final class Event {
        public static final int SOON_OFF = 0, SOON_ON = 1, AT_OFF = 2, AT_ON = 3;
        public int kind;
        public long at, change;
        public String place = "", name = "";

        /** Same event across fetches: a place, a kind, a change. */
        public String key() {
            return place + "|" + kind + "|" + change;
        }
    }

    /** Upcoming reminders of one place, earliest first; a reminder up to LATE_S late is kept. */
    public static List<Event> events(Place p, String tl, LocalDate base, long now, Alerts a) {
        List<Event> out = new ArrayList<>();
        int from = Math.max(0, index(base, now - LATE_S));
        for (int[] c : changesFrom(tl, from)) {
            long change = slotStart(base, c[0]);
            char prev = tl.charAt(c[0] - 1), next = (char) c[1];
            boolean off = next == '0' && prev != '0' || next == '3' && prev == '1';
            boolean on = next == '1' && prev == '0';
            if (!off && !on) continue;
            if (off ? !a.off : !a.on) continue;
            int lead = off ? a.leadOff : a.leadOn;
            if (lead > 0) {
                if (change > now) add(out, p, off ? Event.SOON_OFF : Event.SOON_ON, change - lead * 60L, change, now);
            } else {
                add(out, p, off ? Event.AT_OFF : Event.AT_ON, change, change, now);
            }
        }
        Collections.sort(out, (x, y) -> Long.compare(x.at, y.at));
        return out;
    }

    private static void add(List<Event> out, Place p, int kind, long at, long change, long now) {
        if (at < now - LATE_S) return;
        Event e = new Event();
        e.kind = kind;
        e.at = at;
        e.change = change;
        e.place = p.id;
        e.name = p.name;
        out.add(e);
    }

    /** End of the outage that starts at `change`, or 0 when the data ends first. */
    static long outageEnd(String tl, LocalDate base, long change) {
        int i = index(base, change);
        for (int[] c : changesFrom(tl, i)) if (c[1] == '1') return slotStart(base, c[0]);
        return 0;
    }

    /** {title, text}, counted at the moment it is shown. */
    public static String[] eventText(Event e, String tl, LocalDate base, long now) {
        int i = index(base, e.change);
        boolean maybe = i >= 0 && i < tl.length() && tl.charAt(i) == '3';
        long end = outageEnd(tl, base, e.change);
        String in = ", через " + waitText(Math.max(0, e.change - now));
        switch (e.kind) {
            case Event.SOON_OFF:
                return new String[] {maybe ? "Можливе вимкнення світла" : "Скоро вимкнуть світло",
                        e.name + " · о " + hm(e.change) + in + (end > 0 ? " · до " + until(end, e.change) : "")};
            case Event.SOON_ON:
                return new String[] {"Скоро увімкнуть світло", e.name + " · о " + hm(e.change) + in};
            case Event.AT_OFF:
                return new String[] {maybe ? "Можливе вимкнення за графіком" : "Вимкнення за графіком",
                        e.name + (end > 0 ? " · до " + until(end, e.change) : " · за графіком")};
            default:
                return new String[] {"Світло має з'явитися", e.name + " · за графіком з " + hm(e.change)};
        }
    }

    public static final class Note {
        public static final int PUBLISHED = 0, CHANGED = 1;
        public int kind;
        public String title = "", text = "";
    }

    static String list(String day) {
        StringBuilder b = new StringBuilder();
        for (Outage o : outages(day, -1)) {
            if (o.maybe) continue;
            b.append(b.length() > 0 ? ", " : "").append(o.label);
        }
        return b.toString();
    }

    static int offCount(String day) {
        int n = 0;
        for (Outage o : outages(day, -1)) if (!o.maybe) n++;
        return n;
    }

    /** "1 вимкнення", "3 вимкнення", "5 вимкнень" */
    static String outagesWord(int n) {
        int t = n % 100, u = n % 10;
        return n + (u >= 1 && u <= 4 && (t < 11 || t > 14) ? " вимкнення" : " вимкнень");
    }

    /** What a new fetch changed for one place: a new day published, or a change still ahead. */
    public static List<Note> notes(Place p, Data before, Data after, long now) {
        List<Note> out = new ArrayList<>();
        if (before == null || after == null) return out;
        LocalDate today = today(now);
        for (int d = 0; d < 2; d++) {
            LocalDate date = today.plusDays(d);
            String b = day(before, p.group, date), a = day(after, p.group, date);
            if (a == null) continue;  // not published, or a glitch of the source
            String when = d == 0 ? "сьогодні" : "завтра";
            Note n = new Note();
            if (b == null) {
                n.kind = Note.PUBLISHED;
                n.title = p.name + ": графік на " + when;
                int k = offCount(a);
                n.text = k == 0 ? "Відключень не заплановано"
                        : outagesWord(k) + ", " + dayTotal(a) + ": " + list(a);
            } else {
                int from = d == 0 ? index(today, now) : 0;
                if (a.substring(from).equals(b.substring(from))) continue;
                n.kind = Note.CHANGED;
                n.title = p.name + ": графік змінився";
                String w = d == 0 ? "Сьогодні" : "Завтра";
                n.text = offCount(a) == 0 ? w + " без вимкнень" : w + " " + dayTotal(a) + ": " + list(a);
            }
            out.add(n);
        }
        return out;
    }

    /** When some text on screen next changes by itself: a "soon" threshold, a change, midnight, a poll. */
    public static long nextRefresh(List<String> tls, LocalDate base, long now, long fetchedAt) {
        long best = fetchedAt > 0 && fetchedAt + POLL_S > now ? fetchedAt + POLL_S : now + POLL_S;
        long midnight = base.plusDays(1).atStartOfDay(KYIV).toEpochSecond();
        if (midnight > now && midnight < best) best = midnight;
        for (String tl : tls) {
            for (int[] c : changesFrom(tl, index(base, now))) {
                long t = slotStart(base, c[0]);
                for (long x : new long[] {t - SOON_S, t}) if (x > now && x < best) best = x;
            }
        }
        return best;
    }

    /**
     * A poll may wait for the system to batch it with other wake-ups; a moment when the text
     * itself changes (a "soon" threshold, a change, midnight) needs an exact alarm, and so does
     * a poll that such a moment follows within POLL_S: a late poll would redraw it late.
     */
    public static boolean exactRefresh(List<String> tls, LocalDate base, long now, long at) {
        long midnight = base.plusDays(1).atStartOfDay(KYIV).toEpochSecond();
        if (midnight >= at && midnight - at < POLL_S) return true;
        for (String tl : tls) {
            for (int[] c : changesFrom(tl, index(base, now))) {
                long t = slotStart(base, c[0]);
                for (long x : new long[] {t - SOON_S, t}) if (x >= at && x - at < POLL_S) return true;
            }
        }
        return false;
    }

    // ---- widget ----------------------------------------------------------------

    /** One row (2 x 1 and wider), or with a bar of the next 24 hours when there is room. */
    public static final int W_ROW = 0, W_FULL = 1;

    /** The places a widget shows: those chosen for it ("p1,p3"), in the app's order; all when none is left. */
    public static List<Place> widgetPlaces(List<Place> all, String chosen) {
        List<String> ids = java.util.Arrays.asList(chosen.split(","));
        List<Place> out = new ArrayList<>();
        for (Place p : all) if (ids.contains(p.id)) out.add(p);
        return out.isEmpty() ? new ArrayList<>(all) : out;
    }

    public static int widgetForm(int wDp, int hDp) {
        return wDp >= 200 && hDp >= 110 ? W_FULL : W_ROW;
    }
}
