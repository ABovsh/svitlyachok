package ua.abovsh.svitlo;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.TreeSet;
import org.json.JSONArray;
import org.json.JSONException;
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
    public static final int OFF = 0;
    public static final int ON = 1;
    public static final int UNKNOWN = 2;
    public static final int MAYBE = 3;
    public static final long POLL_S = 15 * 60L;   // the widget fetches by itself this often
    public static final long SOON_S = 30 * 60L;   // the screen says "soon" this close to a change
    public static final long LATE_S = 10 * 60L;   // a delayed notification still worth showing
    public static final long STALE_S = 3 * 3600L; // no successful check this long: say so
    public static final int QUIET_FROM_H = 23;
    public static final int QUIET_TO_H = 7;
    static final String NO_OUTAGES = "без вимкнень";

    private Logic() {}

    /** A region file this app does not accept: it never replaces good data. */
    public static final class BadFile extends Exception {
        BadFile(String why) {
            super(why);
        }
    }

    // ---- regions ---------------------------------------------------------------

    public static final class Region {
        public final String id;
        public final String name;
        public final String dso;
        final String[] urls;

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
    private static final Region[] REGIONS = {
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

    public static List<Region> regions() {
        return java.util.Collections.unmodifiableList(java.util.Arrays.asList(REGIONS));
    }

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
        int depth = 0;
        int max = 0;
        boolean text = false;
        int i = 0;
        while (i < raw.length()) {
            char c = raw.charAt(i);
            if (text) {
                if (c == '\\') i++;  // the escaped character is part of the text
                else if (c == '"') text = false;
            } else if (c == '"') {
                text = true;
            } else if (c == '{' || c == '[') {
                max = Math.max(max, ++depth);
            } else if (c == '}' || c == ']') {
                depth--;
            }
            i++;
        }
        return max;
    }

    /** Parse a region file; throws on any shape it does not know, so garbage never replaces good data. */
    public static Data parse(String raw) throws BadFile {
        if (nesting(raw) > MAX_NESTING) throw new BadFile("nested too deep");
        try {
            JSONObject file = new JSONObject(raw);
            JSONObject fact = file.getJSONObject("fact");
            Data d = new Data();
            TreeSet<String> groups = new TreeSet<>();
            Object data = fact.get("data");
            if (data instanceof JSONArray list) {
                if (list.length() > 0) throw new BadFile("unknown data list");
                d.none = true;
            } else if (data instanceof JSONObject days) {
                readDays(days, d, groups);
            } else {
                throw new BadFile("unknown data");
            }
            if (groups.isEmpty()) presetGroups(file, groups);
            d.groups = new ArrayList<>(groups);
            d.updated = updatedAt(fact.optString("update", ""));
            return d;
        } catch (JSONException e) {
            throw new BadFile("not a schedule: " + e.getMessage());
        }
    }

    private static void readDays(JSONObject days, Data d, Set<String> groups) throws BadFile, JSONException {
        if (days.length() > MAX_DAYS) throw new BadFile("too many days");
        for (Iterator<String> it = days.keys(); it.hasNext(); ) {
            String key = it.next();
            if (!key.matches("\\d{6,12}")) throw new BadFile("bad day " + key);
            LocalDate date = Instant.ofEpochSecond(Long.parseLong(key)).atZone(KYIV).toLocalDate();
            JSONObject gs = days.getJSONObject(key);
            Map<String, String> m = new HashMap<>();
            for (Iterator<String> g = gs.keys(); g.hasNext(); ) {
                String group = g.next();
                if (!group.matches(GROUP_NAME)) throw new BadFile("bad group " + group);
                m.put(group, hours(gs.getJSONObject(group)));
                groups.add(group);
            }
            d.days.put(date, m);
        }
    }

    /** No outages published: the queues still come from the preset, for the setup screen. */
    private static void presetGroups(JSONObject file, Set<String> groups) {
        JSONObject preset = file.optJSONObject("preset");
        JSONObject names = preset == null ? null : preset.optJSONObject("sch_names");
        if (names == null) return;
        for (Iterator<String> g = names.keys(); g.hasNext(); ) {
            String group = g.next();
            if (group.matches(GROUP_NAME)) groups.add(group);
        }
    }

    /** When the oblenergo last changed the schedule, 0 when unknown. */
    private static long updatedAt(String up) {
        for (DateTimeFormatter f : UPDATE_FORMATS) {
            try {
                return LocalDateTime.parse(up, f).atZone(KYIV).toEpochSecond();
            } catch (java.time.format.DateTimeParseException ignored) {
                // try the other format
            }
        }
        return 0;
    }

    /** Hours "1".."24" (or "0".."23") of yes/no/first/second/maybe/mfirst/msecond as 48 slots. */
    static String hours(JSONObject h) {
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
        public String id = "";
        public String name = "";
        public String region = "";
        public String group = "";
    }

    /** The 48 slots of a day, or null while it is not published. */
    public static String day(Data d, String group, LocalDate date) {
        if (d == null) return null;
        if (d.none) return repeat('1', 48);
        Map<String, String> m = d.days.get(date);
        return m == null ? null : m.get(group);
    }

    public static String timeline(Data d, String group, LocalDate today) {
        String a = day(d, group, today);
        String b = day(d, group, today.plusDays(1));
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
        long d = s / 86400;
        long h = (s % 86400) / 3600;
        long m = (s % 3600) / 60;
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
        return "графік оновлено " + dayWord(updated, now) + " о " + hm(updated);
    }

    private static String dayWord(long t, long now) {
        long d = dayDiff(t, now);
        if (d == 0) return "сьогодні";
        if (d == 1) return "вчора";
        return String.format(Locale.ROOT, "%02d.%02d", z(t).getDayOfMonth(), z(t).getMonthValue());
    }

    // ---- now -------------------------------------------------------------------

    public static final class Now {
        public int state = UNKNOWN;
        public String title = "";
        public String big = "";
        public String hint = "";
        public String then = "";
        public boolean soon;
        public long change;
    }

    static int state(char slot) {
        switch (slot) {
            case '1': return ON;
            case '0': return OFF;
            case '3': return MAYBE;
            default: return UNKNOWN;
        }
    }

    static String title(int state) {
        switch (state) {
            case ON: return "Світло є";
            case OFF: return "Світла немає";
            case MAYBE: return "Можливе вимкнення";
            default: return "Графіка немає";
        }
    }

    /** "без світла", "світло", "можливе вимкнення": what comes after a change. */
    private static String coming(char slot) {
        if (slot == '0') return "без світла";
        return slot == '1' ? "світло" : "можливе вимкнення";
    }

    public static Now now(String tl, LocalDate base, long now, boolean none) {
        Now n = new Now();
        int cur = index(base, now);
        char c = cur >= 0 && cur < tl.length() ? tl.charAt(cur) : '2';
        n.state = state(c);
        n.title = title(n.state);
        if (n.state == UNKNOWN) {
            n.big = "ще не опубліковано";
            n.hint = "щойно обленерго його оприлюднить, він з'явиться тут";
        } else if (none) {
            n.big = NO_OUTAGES;
            n.hint = "відключень за графіком не заплановано";
        } else {
            List<int[]> ch = changesFrom(tl, cur);
            if (ch.isEmpty()) steady(n, tl, cur, c);
            else changing(n, ch, c, base, now);
        }
        return n;
    }

    /** No change while the data lasts: either tomorrow is not published yet, or nothing changes. */
    private static void steady(Now n, String tl, int cur, char c) {
        int j = cur;
        while (j < tl.length() && tl.charAt(j) == c) j++;
        if (j < tl.length()) {
            n.big = "до кінця дня";
            n.hint = "графік на завтра ще не опубліковано";
        } else if (c == '1') {
            n.big = NO_OUTAGES;
            n.hint = "сьогодні й завтра вимкнень немає";
        } else {
            n.big = "понад добу";
            n.hint = "за графіком і завтра";
        }
    }

    private static void changing(Now n, List<int[]> ch, char c, LocalDate base, long now) {
        long change = slotStart(base, ch.get(0)[0]);
        char next = (char) ch.get(0)[1];
        n.change = change;
        n.big = "до " + until(change, now);
        n.soon = change - now <= SOON_S;
        if (ch.size() > 1 || next != '1') {  // "потім світло" with no end tells nothing new
            n.then = "потім " + coming(next) + (ch.size() > 1 ? " до " + until(slotStart(base, ch.get(1)[0]), now) : "");
        }
        String in = " через " + waitText(change - now);
        if (next == '0') n.hint = "вимкнуть" + in;
        else if (next == '3') n.hint = "можливе вимкнення" + in;
        else if (c == '0') n.hint = "увімкнуть" + in;
        else n.hint = "можливе вимкнення закінчиться" + in;
    }

    // ---- day lists -------------------------------------------------------------

    public static final class Outage {
        public static final int PAST = 0;
        public static final int NOW = 1;
        public static final int LATER = 2;
        public String label = "";
        public String length = "";
        public int when;
        public boolean maybe;
        public int from;
        public int to;  // slots, end excluded
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
        int i = 0;
        while (i < day.length()) {
            char c = day.charAt(i);
            int j = i;
            while (j < day.length() && day.charAt(j) == c) j++;
            if (c == '0' || c == '3') out.add(outage(i, j, c == '3', cur));
            i = j;
        }
        return out;
    }

    private static Outage outage(int from, int to, boolean maybe, int cur) {
        Outage o = new Outage();
        o.from = from;
        o.to = to;
        o.maybe = maybe;
        o.label = clock(from) + " – " + clock(to);
        o.length = length(to - from);
        if (cur >= to) o.when = Outage.PAST;
        else if (cur >= from) o.when = Outage.NOW;
        else o.when = Outage.LATER;  // also another day (cur = -1)
        return o;
    }

    public static String dayTotal(String day) {
        if (day == null) return "ще не опубліковано";
        int off = 0;
        for (int i = 0; i < day.length(); i++) if (day.charAt(i) == '0') off++;
        if (off == 0) return NO_OUTAGES;
        return "без світла " + length(off);
    }

    /** A day for a screen reader: "Сьогодні: без світла 06:00 – 08:00; можливе вимкнення 20:00 – 22:00". */
    public static String describe(String title, String day) {
        if (day == null) return title + ": графік ще не опубліковано";
        String off = list(day, false);
        String maybe = list(day, true);
        return title + ": " + (off.isEmpty() ? NO_OUTAGES : "без світла " + off)
                + (maybe.isEmpty() ? "" : "; можливе вимкнення " + maybe);
    }

    /** The outages of a day as "06:00 – 08:00, 14:30 – 18:30": certain ones, or possible ones. */
    static String list(String day, boolean maybe) {
        List<String> labels = new ArrayList<>();
        for (Outage o : outages(day, -1)) if (o.maybe == maybe) labels.add(o.label);
        return String.join(", ", labels);
    }

    // ---- notifications ---------------------------------------------------------

    /**
     * What the user wants to hear. Out of the box only two: before the power goes off and before
     * it comes back. A lead of 0 minutes means at the moment of the change.
     */
    public static final class Alerts {
        public boolean off = true;
        public boolean on = true;
        public int leadOff = 15;
        public int leadOn = 15;
    }

    /** Everything else is opt-in. */
    public static final boolean DEFAULT_TOMORROW = false;
    public static final boolean DEFAULT_CHANGES = false;
    public static final boolean DEFAULT_QUIET = false;

    public static final class Event {
        public static final int SOON_OFF = 0;
        public static final int SOON_ON = 1;
        public static final int AT_OFF = 2;
        public static final int AT_ON = 3;
        public int kind;
        public long at;
        public long change;
        public String place = "";
        public String name = "";

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
            Event e = event(p, tl.charAt(c[0] - 1), (char) c[1], slotStart(base, c[0]), a);
            if (e != null && e.at >= now - LATE_S && (e.at == e.change || e.change > now)) out.add(e);
        }
        out.sort((x, y) -> Long.compare(x.at, y.at));
        return out;
    }

    /** The reminder for one change, or null when it is not a power change or not wanted. */
    private static Event event(Place p, char prev, char next, long change, Alerts a) {
        boolean off = next == '0' && prev != '0' || next == '3' && prev == '1';
        boolean on = next == '1' && prev == '0';
        if (off ? !a.off : !(on && a.on)) return null;
        int lead = off ? a.leadOff : a.leadOn;
        Event e = new Event();
        if (lead > 0) e.kind = off ? Event.SOON_OFF : Event.SOON_ON;
        else e.kind = off ? Event.AT_OFF : Event.AT_ON;
        e.at = change - lead * 60L;
        e.change = change;
        e.place = p.id;
        e.name = p.name;
        return e;
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
        public static final int PUBLISHED = 0;
        public static final int CHANGED = 1;
        public int kind;
        public String title = "";
        public String text = "";
    }

    static String list(String day) {
        return list(day, false);
    }

    static int offCount(String day) {
        int n = 0;
        for (Outage o : outages(day, -1)) if (!o.maybe) n++;
        return n;
    }

    /** "1 вимкнення", "3 вимкнення", "5 вимкнень" */
    static String outagesWord(int n) {
        int t = n % 100;
        int u = n % 10;
        return n + (u >= 1 && u <= 4 && (t < 11 || t > 14) ? " вимкнення" : " вимкнень");
    }

    /** What a new fetch changed for one place: a new day published, or a change still ahead. */
    public static List<Note> notes(Place p, Data before, Data after, long now) {
        List<Note> out = new ArrayList<>();
        if (before == null || after == null) return out;
        LocalDate today = today(now);
        for (int d = 0; d < 2; d++) {
            LocalDate date = today.plusDays(d);
            Note n = note(p, day(before, p.group, date), day(after, p.group, date), d == 0 ? index(today, now) : 0, d == 0);
            if (n != null) out.add(n);
        }
        return out;
    }

    /** One day's note; `from` = the first slot still ahead. Null when nothing worth saying changed. */
    private static Note note(Place p, String b, String a, int from, boolean today) {
        if (a == null) return null;  // not published, or a glitch of the source
        Note n = new Note();
        if (b == null) {
            n.kind = Note.PUBLISHED;
            n.title = p.name + ": графік на " + (today ? "сьогодні" : "завтра");
            int k = offCount(a);
            n.text = k == 0 ? "Відключень не заплановано" : outagesWord(k) + ", " + dayTotal(a) + ": " + list(a);
            return n;
        }
        if (a.substring(from).equals(b.substring(from))) return null;
        n.kind = Note.CHANGED;
        n.title = p.name + ": графік змінився";
        String w = today ? "Сьогодні" : "Завтра";
        n.text = offCount(a) == 0 ? w + " " + NO_OUTAGES : w + " " + dayTotal(a) + ": " + list(a);
        return n;
    }

    /**
     * Whether a background check should go to the network: not when the source answered less than
     * a poll ago (minus 2 minutes of slack), so the alarm and the periodic job never both wake the
     * radio. A region that failed keeps its old time and is tried again.
     */
    static boolean dueForFetch(long checkedAt, long now) {
        return checkedAt <= 0 || now < checkedAt || now - checkedAt >= POLL_S - 120;
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

    // ---- updates ---------------------------------------------------------------

    public static final class Release {
        public String version = "";
        public String page = "";
        public String apk = "";
    }

    /** Where the app is published: the only place an update may come from. */
    static final String REPO = "https://github.com/ABovsh/svitlyachok/";
    /** Answers with a redirect to the newest published release (never a draft or pre-release). */
    public static final String LATEST_RELEASE = REPO + "releases/latest";
    static final long UPDATE_CHECK_S = 12 * 3600L;

    /** "1.2.0-rc.1" -> {1, 2, 0, rc?}; empty for anything that is not a version. */
    private static int[] version(String v) {
        java.util.regex.Matcher m = java.util.regex.Pattern
                .compile("v?(\\d{1,4})(?:\\.(\\d{1,4}))?(?:\\.(\\d{1,4}))?(-.+)?").matcher(v.trim());
        if (!m.matches()) return new int[0];
        int[] out = new int[4];
        for (int i = 0; i < 3; i++) out[i] = m.group(i + 1) == null ? 0 : Integer.parseInt(m.group(i + 1));
        out[3] = m.group(4) == null ? 1 : 0;  // a release outranks its own candidates
        return out;
    }

    /** Whether `latest` is a newer version than the one installed. */
    public static boolean newer(String latest, String current) {
        int[] a = version(latest);
        int[] b = version(current);
        if (a.length == 0 || b.length == 0) return false;
        for (int i = 0; i < 4; i++) {
            if (a[i] != b[i]) return a[i] > b[i];
        }
        return false;
    }

    /**
     * The release GitHub points to from "latest". Only an exact release address of this repository
     * with a version tag is accepted; the APK is the one the release publishes under its version.
     */
    public static Release releaseAt(String location) throws BadFile {
        String prefix = REPO + "releases/tag/v";
        String v = location.startsWith(prefix) ? location.substring(prefix.length()) : "";
        if (!v.matches("\\d{1,4}(\\.\\d{1,4}){0,2}")) throw new BadFile("not a release of this app: " + location);
        Release r = new Release();
        r.version = v;
        r.page = location;
        r.apk = REPO + "releases/download/v" + v + "/Svitlyachok-" + v + ".apk";
        return r;
    }

    public static boolean dueForUpdateCheck(long checkedAt, long now) {
        return checkedAt <= 0 || now < checkedAt || now - checkedAt >= UPDATE_CHECK_S;
    }

    // ---- problem reports ---------------------------------------------------------

    /** "Київська область, черга 1.1; …": what a report says about the places, never their names. */
    public static String placesSummary(List<Place> places) {
        List<String> out = new ArrayList<>();
        for (Place p : places) {
            Region r = region(p.region);
            out.add((r == null ? p.region : r.name) + ", черга " + queue(p.group));
        }
        return String.join("; ", out);
    }

    /** A new issue in this repository with the technical details already in the form. */
    public static String issueLink(String details) {
        try {
            return REPO + "issues/new?template=problem.yml&details="
                    + java.net.URLEncoder.encode(details, java.nio.charset.StandardCharsets.UTF_8.name());
        } catch (java.io.UnsupportedEncodingException e) {
            return REPO + "issues/new?template=problem.yml";  // UTF-8 always exists
        }
    }

    // ---- widget ----------------------------------------------------------------

    /** One row (2 x 1 and wider), or with a bar of the next 24 hours when there is room. */
    public static final int W_ROW = 0;
    public static final int W_FULL = 1;

    /** The place a widget shows: the one chosen for it, or the first place when none is (left). */
    public static Place widgetPlace(List<Place> all, String chosen) {
        for (Place p : all) if (p.id.equals(chosen)) return p;
        return all.isEmpty() ? null : all.get(0);
    }

    /** Whether a place still has no widget of its own (`widgetPlaces`: the place of each widget). */
    public static boolean needsWidget(String place, List<String> widgetPlaces) {
        return !widgetPlaces.contains(place);
    }

    public static int widgetForm(int wDp, int hDp) {
        return wDp >= 200 && hDp >= 110 ? W_FULL : W_ROW;
    }
}
