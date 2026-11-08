package ua.abovsh.svitlo;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
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
    public static final ZoneId KYIV = kyivZone(ZoneId::of);
    static ZoneId kyivZone(java.util.function.Function<String, ZoneId> find) {
        try { return find.apply("Europe/Kyiv"); }
        catch (java.time.DateTimeException e) { return find.apply("Europe/Kiev"); }
    }
    public static final int OFF = 0;
    private static final String TOMORROW = "Завтра";
    private static final String TOMORROW_LABEL = "Завтра: ";
    private static final String UNTIL_TOMORROW = "до завтра ";
    private static final String POSSIBLE_OUTAGE = "Можливе вимкнення";
    public static final int ON = 1;
    public static final int UNKNOWN = 2;
    public static final int MAYBE = 3;
    public static final long POLL_S = 15 * 60L;   // the widget fetches by itself this often
    /** How early a place lights up when its notifications do not say otherwise. */
    public static final long HIGHLIGHT_S = 15 * 60L;
    public static final long SOON_S = 30 * 60L;   // the screen says "soon" this close to a change
    public static final long LATE_S = 10 * 60L;   // a delayed notification still worth showing
    public static final long STALE_S = 3 * 3600L; // no successful check this long: say so
    public static final int QUIET_FROM_H = 23;
    public static final int QUIET_TO_H = 7;
    static final String NO_OUTAGES = "без вимкнень";
    private static final String NO_OUTAGES_WIDGET = "Без вимкнень";
    private static final String NO_CHANGE = "Без змін";
    private static final String TO_DAY_END = "до кінця дня";
    static final String NOT_PUBLISHED = "ще не опубліковано";
    static final String NO_SCHEDULE = "Графіка немає";

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
    private static final String KYIV_REGION = "kyiv_region";
    private static final Region[] REGIONS = {
        new Region("kyiv", "Місто Київ", "https://bezsvitla.com.ua/kyiv#street-search-widget", B42 + "kyiv.json"),
        new Region(KYIV_REGION, "Київська область", "https://www.dtek-krem.com.ua/ua/shutdowns", B42 + "kyiv-region.json"),
        new Region("vinnytsia", "Вінницька область", "https://www.voe.com.ua/",
                "https://raw.githubusercontent.com/vn-progr/gpv-voe-vinnytsia/main/data/Vinnytsiaoblenerho.json",
                "https://raw.githubusercontent.com/olnet93/gpv-voe-vinnytsia/main/data/Vinnytsiaoblenerho.json"),
        new Region("dnipro", "Дніпропетровська область", "https://www.dtek-dnem.com.ua/ua/shutdowns", B42 + "dnipro.json"),
        new Region("zhytomyr", "Житомирська область", "https://www.ztoe.com.ua/", Y29 + "Zhytomyroblenergo.json"),
        new Region("uzhhorod", "Закарпатська область", "https://zakarpat.energy/", Y29 + "Zakarpattiaoblenerho.json"),
        new Region("zaporizhzhia", "Запорізька область", "https://www.zoe.com.ua/",
                "https://raw.githubusercontent.com/milkmann/gpv-zoe-zaporizhzhia/main/data/Zaporizhzhiaoblenergo.json",
                Y29 + "Zaporizhzhiaoblenergo.json"),
        new Region("ivano_frankivsk", "Івано-Франківська область", "https://www.oe.if.ua/", Y29 + "Prykarpattiaoblenerho.json"),
        new Region("lviv", "Львівська область", "https://loe.lviv.ua/", Y29 + "Lvivoblenerho.json"),
        new Region("odesa", "Одеська область", "https://www.dtek-oem.com.ua/ua/shutdowns", B42 + "odesa.json"),
        new Region("poltava", "Полтавська область", "https://www.poe.pl.ua/", Y29 + "Poltavaoblenergo.json"),
        new Region("rivne", "Рівненська область", "https://www.roe.vsei.ua/", Y29 + "Rivneoblenergo.json"),
        new Region("ternopil", "Тернопільська область", "https://www.toe.com.ua/", Y29 + "Ternopiloblenerho.json"),
        new Region("khmelnytskyi", "Хмельницька область", "https://hoe.com.ua/", Y29 + "Khmelnytskoblenerho.json"),
        new Region("cherkasy", "Черкаська область", "https://www.cherkasyoblenergo.com/", Y29 + "Cherkasyoblenergo.json"),
        new Region("kharkiv", "Харківська область", "https://oblenergo.kharkiv.ua/uk", Y29 + "Kharkivoblenerho.json"),
        new Region("chernihiv", "Чернігівська область", "https://chernihivoblenergo.com.ua/", Y29 + "Chernihivoblenergo.json"),
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
        public long sourceTime;
        public boolean sourceOk = true;
        private Data expiredView;
        private Data draftView;
        private String draftKey = "";
        public long updated;          // when the oblenergo last changed it, 0 unknown
        public String hash = "";      // the source's own hash of its schedule, empty when it has none
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

    /** Queues in the order people count them: 1.1, 1.2, 2.1, 10.1. */
    static int compareGroups(String a, String b) {
        String[] x = queue(a).split("\\.");
        String[] y = queue(b).split("\\.");
        for (int i = 0; i < Math.min(x.length, y.length); i++) {
            int order = x[i].matches("\\d{1,6}") && y[i].matches("\\d{1,6}")
                    ? Integer.compare(Integer.parseInt(x[i]), Integer.parseInt(y[i])) : x[i].compareTo(y[i]);
            if (order != 0) return order;
        }
        return x.length == y.length ? a.compareTo(b) : Integer.compare(x.length, y.length);
    }

    /** Parse a region file; throws on any shape it does not know, so garbage never replaces good data. */
    public static Data parse(String raw) throws BadFile {
        if (nesting(raw) > MAX_NESTING) throw new BadFile("nested too deep");
        try {
            JSONObject file = new JSONObject(raw);
            JSONObject fact = file.getJSONObject("fact");
            Data d = new Data();
            TreeSet<String> groups = new TreeSet<>(Logic::compareGroups);
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
            d.sourceTime = sourceTime(file.optString(LAST_UPDATED, ""));
            // a schedule cannot be published after its file was written (Khmelnytskyi gave 23:32 at 00:02)
            if (d.sourceTime > 0 && d.updated > d.sourceTime) d.updated = d.sourceTime;
            JSONObject status = file.optJSONObject(STATUS);
            d.sourceOk = status == null || status.optBoolean("ok", true);
            JSONObject meta = file.optJSONObject("meta");
            String hash = meta == null ? "" : meta.optString(CONTENT_HASH, "");
            d.hash = hash.matches(HASH) ? hash : "";
            return d;
        } catch (JSONException e) {
            throw new BadFile("not a schedule: " + e.getMessage());
        }
    }

    private static final String CONTENT_HASH = "contentHash";
    private static final String HASH = "[0-9a-f]{16,128}";
    private static final String LAST_UPDATED = "lastUpdated";
    private static final String STATUS = "lastUpdateStatus";
    private static final String SCH_NAMES = "sch_names";
    private static final String PRESET = "preset";
    private static final String QUEUE_WORD = "Черга ";

    /** Most of a source file is a template the app never reads: only what parse() uses is stored. */
    public static String compactFile(String raw) throws BadFile {
        try {
            JSONObject file = new JSONObject(raw);
            JSONObject out = new JSONObject().put("fact", file.getJSONObject("fact"));
            if (file.has(LAST_UPDATED)) out.put(LAST_UPDATED, file.get(LAST_UPDATED));
            if (file.has(STATUS)) out.put(STATUS, file.get(STATUS));
            JSONObject preset = file.optJSONObject(PRESET);
            if (preset != null && preset.has(SCH_NAMES)) out.put(PRESET, new JSONObject().put(SCH_NAMES, preset.get(SCH_NAMES)));
            JSONObject meta = file.optJSONObject("meta");
            if (meta != null && meta.has(CONTENT_HASH)) out.put("meta", new JSONObject().put(CONTENT_HASH, meta.get(CONTENT_HASH)));
            return out.toString();
        } catch (JSONException e) {
            throw new BadFile("not a schedule: " + e.getMessage());
        }
    }

    /** What the last bytes of a source file prove: its schedule's hash, and that the source answered when. */
    public static final class Tail {
        public String hash = "";
        public long at;
    }

    private static final java.util.regex.Pattern TAIL_HASH = java.util.regex.Pattern.compile("\"" + CONTENT_HASH + "\"\\s*:\\s*\"(" + HASH + ")\"");
    private static final java.util.regex.Pattern TAIL_OK = java.util.regex.Pattern.compile("\"ok\"\\s*:\\s*(true|false)");
    private static final java.util.regex.Pattern TAIL_AT = java.util.regex.Pattern.compile("\"at\"\\s*:\\s*\"?([0-9TZ:.\\-]+)\"?");

    private static String lastMatch(java.util.regex.Pattern p, String text) {
        java.util.regex.Matcher m = p.matcher(text);
        String found = null;
        while (m.find()) found = m.group(1);
        return found;
    }

    /** Null unless the end of the file shows a working source and the hash: then the whole file decides. */
    public static Tail tail(String end) {
        String hash = lastMatch(TAIL_HASH, end);
        String at = lastMatch(TAIL_AT, end);
        if (hash == null || at == null || !"true".equals(lastMatch(TAIL_OK, end))) return null;
        Tail t = new Tail();
        t.hash = hash;
        t.at = sourceTime(at);
        return t.at > 0 ? t : null;
    }

    /** The source answered at this time, with the schedule already stored. */
    static synchronized void touch(Data d, long at) {
        if (d == null || at <= d.sourceTime) return;
        d.sourceTime = at;
        d.sourceOk = true;
    }

    /** The end of the file is enough while a hash exists; now and then the whole file, should the hash ever mislead. */
    static boolean needsWholeFile(String hash, long wholeAt, long now) {
        return hash.isEmpty() || now < wholeAt || now - wholeAt >= WHOLE_FILE_S;
    }

    private static final long WHOLE_FILE_S = 6 * 3600L;
    private static final java.util.Set<String> CACHE_KEYS = new java.util.HashSet<>(java.util.Arrays.asList("json", "checked", "etag", "url", "full", "source"));

    /** The stored keys of regions no place uses ("json_kyiv"); a region id may itself contain "_". */
    static List<String> cacheKeysOutside(java.util.Set<String> keys, java.util.Set<String> regions) {
        List<String> out = new ArrayList<>();
        for (String key : keys) {
            int at = key.indexOf('_');
            if (at > 0 && CACHE_KEYS.contains(key.substring(0, at)) && !regions.contains(key.substring(at + 1))) out.add(key);
        }
        return out;
    }

    static long sourceTime(String value) {
        try { return Instant.parse(value).getEpochSecond(); }
        catch (java.time.format.DateTimeParseException e) {
            try { long t = Long.parseLong(value); return t > 10_000_000_000L ? t / 1000 : t; }
            catch (NumberFormatException ignored) { return 0; }
        }
    }

    static boolean noneFresh(Data d, long now) {
        return d != null && d.none && d.sourceTime > 0 && now >= d.sourceTime - 300
                && now - d.sourceTime <= 48 * 3600L;
    }

    static synchronized Data validAt(Data d, long now) {
        if (d == null || !d.none || noneFresh(d, now)) return d;
        if (d.expiredView == null) {
            Data out = new Data(); out.updated = d.updated; out.sourceTime = d.sourceTime;
            out.sourceOk = d.sourceOk; out.groups = d.groups; out.hash = d.hash; d.expiredView = out;
        }
        return d.expiredView;
    }

    /** A draft stands no longer than this after the source's last change; then it is taken as published. */
    static final long DRAFT_S = 3 * 3600L;
    /** Only DTEK of Kyiv region posts a draft; elsewhere a tomorrow without daytime outages is real. */
    static final String DRAFT_REGION = KYIV_REGION;
    /** Slot 12 = 06:00: a draft of tomorrow knows only the night. */
    private static final int DRAFT_FROM = 12;

    /**
     * DTEK of Kyiv region first posts tomorrow as a draft: only the night outage, light for the rest of
     * the day; the schedule follows 20–80 minutes later. A day is a draft when no queue has an outage
     * from 06:00 on while today has daytime outages. It is left out (not published yet) for at most
     * DRAFT_S after the source's last change.
     */
    static synchronized Data withoutDraft(Data d, String region, long now) {
        if (d == null || d.none || d.days.isEmpty() || !DRAFT_REGION.equals(region)) return d;
        LocalDate today = today(now);
        Map<String, String> current = d.days.get(today);
        if (current == null || !daytimeOutages(current) || (d.updated > 0 && now - d.updated >= DRAFT_S)) return d;
        List<LocalDate> drafts = new ArrayList<>();
        for (Map.Entry<LocalDate, Map<String, String>> e : d.days.entrySet()) {
            if (e.getKey().isAfter(today) && !e.getValue().isEmpty() && !daytimeOutages(e.getValue())) drafts.add(e.getKey());
        }
        if (drafts.isEmpty()) return d;
        String key = drafts + "|" + d.sourceTime;
        if (d.draftView == null || !key.equals(d.draftKey)) {
            Data out = new Data();
            out.days = new HashMap<>(d.days);
            for (LocalDate day : drafts) out.days.remove(day);
            out.updated = d.updated; out.sourceTime = d.sourceTime; out.sourceOk = d.sourceOk;
            out.groups = d.groups; out.hash = d.hash;
            d.draftView = out; d.draftKey = key;
        }
        return d.draftView;
    }

    private static boolean daytimeOutages(Map<String, String> groups) {
        for (String slots : groups.values()) {
            String day = slots.substring(Math.min(DRAFT_FROM, slots.length()));
            if (day.indexOf('0') >= 0 || day.indexOf('3') >= 0) return true;
        }
        return false;
    }

    static LocalDate latestDay(Data d) {
        if (d == null || d.days.isEmpty()) return null;
        return java.util.Collections.max(d.days.keySet());
    }

    static boolean currentData(Data d, long now) {
        if (d == null) return false;
        if (d.none) return noneFresh(d, now);
        LocalDate today = today(now);
        for (LocalDate day : new LocalDate[] {today, today.plusDays(1)}) {
            Map<String, String> groups = d.days.get(day);
            if (groups != null) for (String slots : groups.values()) if (count(slots, '2') < 48) return true;
        }
        return false;
    }

    static boolean preferData(Data candidate, Data cached, long now) {
        if (cached == null) return true;
        if (currentData(candidate, now)) return true;
        if (currentData(cached, now)) return false;
        LocalDate a = latestDay(candidate);
        LocalDate b = latestDay(cached);
        return a != null && (b == null || !a.isBefore(b));
    }

    /** "17.10": the last day the source has a schedule for, empty when it has none. */
    public static String lastScheduleDay(Data d) {
        LocalDate last = latestDay(d);
        return last == null ? "" : String.format(Locale.ROOT, "%d.%02d", last.getDayOfMonth(), last.getMonthValue());
    }

    static String missingSchedule(Data d) {
        LocalDate last = latestDay(d);
        return last == null ? "Нового графіка немає" : "Нового графіка немає · останній був " + lastScheduleDay(d);
    }

    /** What to say about a day without a schedule: a queue the region does not have is not "no schedule yet". */
    static String missingSchedule(Data d, String group) {
        boolean absent = d != null && !d.none && !d.groups.isEmpty() && !d.groups.contains(group);
        return absent ? "Черги " + queue(group) + " немає в останньому графіку області. Перевірте чергу в налаштуваннях."
                : missingSchedule(d) + ".";
    }

    /** Whether a chosen queue stays chosen once the region's list is known; `saved`: the place's own. */
    static boolean queueOffered(String group, List<String> groups, String saved) {
        return group.isEmpty() || groups.contains(group) || group.equals(saved);
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
        JSONObject preset = file.optJSONObject(PRESET);
        JSONObject names = preset == null ? null : preset.optJSONObject(SCH_NAMES);
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
            String v = h.optString(String.valueOf(zero ? hour : hour + 1), "unknown");
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

    /** The same queue of the same region: its saved schedules still describe this place. */
    public static boolean sameQueue(Place a, Place b) {
        return a.region.equals(b.region) && a.group.equals(b.group);
    }

    /**
     * The place that already follows this queue of this region, or null. `before` is the place being
     * edited: it never clashes with itself, and one saved twice by an older version can still be renamed.
     */
    public static Place duplicateOf(List<Place> places, Place candidate, Place before) {
        if (before != null && sameQueue(before, candidate)) return null;
        for (Place p : places) if (sameQueue(p, candidate) && (before == null || !p.id.equals(before.id))) return p;
        return null;
    }

    /** "Черга 3.1, Київська область, уже додана як «Дім»." */
    public static String duplicateMessage(Place existing) {
        Region r = region(existing.region);
        return QUEUE_WORD + queue(existing.group) + (r == null ? "" : ", " + r.name) + ", уже додана як «" + existing.name + "».";
    }

    /** A place the person left without a name is called by its queue. */
    public static boolean autoNamed(Place p) {
        return p.name.equals(queueName(p.group));
    }

    private static String queueName(String group) {
        return QUEUE_WORD + queue(group);
    }

    /** The name to save: what was typed; a name taken from the old queue follows the new one. */
    public static String placeName(String typed, Place before, String group) {
        String name = typed.trim();
        if (name.isEmpty()) return queueName(group);
        return before != null && autoNamed(before) && name.equals(before.name) ? queueName(group) : name;
    }

    /** "Дім · черга 3.2"; a place called by its queue says it once. */
    public static String withQueue(Place p, String shownName) {
        return autoNamed(p) ? shownName : shownName + " · черга " + queue(p.group);
    }

    /** The reminders already shown, without those of one place. */
    static java.util.Set<String> firedWithout(java.util.Set<String> fired, String place) {
        java.util.Set<String> out = new java.util.HashSet<>(fired);
        out.removeIf(key -> key.startsWith(place + "|"));
        return out;
    }

    /** The 48 slots of a day, or null while it is not published. */
    public static String day(Data d, String group, LocalDate date) {
        if (d == null) return null;
        if (d.none) {
            LocalDate published = d.sourceTime > 0 ? today(d.sourceTime) : null;
            return published != null && !date.isBefore(published) && date.atStartOfDay(KYIV).toEpochSecond() <= d.sourceTime + 48 * 3600L ? repeat('1', 48) : null;
        }
        Map<String, String> m = d.days.get(date);
        String slots = m == null ? null : m.get(group);
        // a day of nothing but unknown statuses says nothing: it is not published, not "0 % of power"
        return slots == null || count(slots, '2') == slots.length() ? null : slots;
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
        LocalDateTime local = base.plusDays(idx / 48).atTime((idx % 48) / 2, (idx % 2) * 30);
        java.util.List<java.time.ZoneOffset> offsets = KYIV.getRules().getValidOffsets(local);
        // There is no event at a wall-clock time skipped by the spring transition.
        return offsets.isEmpty() ? -1 : local.atOffset(offsets.get(0)).toEpochSecond();
    }

    /** Changes on actual instants; missing spring slots cannot hide the first state at 04:00. */
    private static List<int[]> clockChanges(String tl, LocalDate base, int from) {
        List<int[]> out = new ArrayList<>();
        if (from < 0 || from >= tl.length() || tl.charAt(from)=='2') return out;
        char state = tl.charAt(from);
        for (int i=from+1;i<tl.length();i++) {
            if (slotStart(base,i)>=0) {
                char next=tl.charAt(i);
                if (next=='2') break;
                if (next!=state) { out.add(new int[] {i,next}); state=next; }
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

    /** "1 год 05 хв", "42 хв", "1 день 3 год": a span of at least a minute, see comingIn and goingOn. */
    public static String waitText(long s) {
        long d = s / 86400;
        long h = (s % 86400) / 3600;
        long m = (s % 3600) / 60;
        if (d > 0) return days(d) + (h > 0 ? " " + h + " год" : "");
        return h > 0 ? String.format(Locale.ROOT, "%d год %02d хв", h, m) : m + " хв";
    }

    private static String days(long d) {
        long tens = d % 100;
        long ones = d % 10;
        if (tens >= 11 && tens <= 14) return d + " днів";
        if (ones == 1) return d + " день";
        return d + (ones >= 2 && ones <= 4 ? " дні" : " днів");
    }

    /** How soon: "через 15 хв", and under a minute "менш ніж за хвилину". */
    static String comingIn(long s) {
        return s < 60 ? "менш ніж за хвилину" : "через " + waitText(s);
    }

    /** How long so far: "уже 15 хв", and under a minute "щойно". */
    static String goingOn(long s) {
        return s < 60 ? "щойно" : "уже " + waitText(s);
    }

    /** The end of a day as a clock. */
    static final String MIDNIGHT = "24:00";
    static final String NO_POWER = "без світла ";

    /** The end of an interval; a smaller clock after its start already means the next day. */
    public static String until(long t, long now) {
        long d = dayDiff(now, t);
        if (d == 1 && hm(t).equals("00:00")) return MIDNIGHT;
        return hm(t);
    }

    public static boolean quiet(long t) {
        int h = z(t).getHour();
        return h >= QUIET_FROM_H || h < QUIET_TO_H;
    }

    /** "графік опубліковано сьогодні о 07:10", "… вчора о 20:25", "… 27.10 о 07:10" */
    public static String updated(long updated, long now) {
        if (updated <= 0) return "";
        return "графік опубліковано " + dayWord(updated, now) + " о " + hm(updated);
    }

    private static final String TODAY_WORD = "сьогодні";
    private static final String TOMORROW_WORD = "завтра";

    private static String dayWord(long t, long now) {
        long d = dayDiff(t, now);
        if (d == 0) return TODAY_WORD;
        if (d == 1) return "вчора";
        return String.format(Locale.ROOT, "%02d.%02d", z(t).getDayOfMonth(), z(t).getMonthValue());
    }

    // ---- now -------------------------------------------------------------------

    public static final class Now {
        public int state = UNKNOWN;
        public String title = "";
        public String big = "";
        public String hint = "";
        public String hintIn = "";
        public String then = "";
        public String since = "";
        public String already = "";
        public boolean soon;
        public long change;
        public long knownUntil;
        public int next = UNKNOWN;
        public boolean tomorrow;
        public String changeClock = "";
        public String endDetail = "";
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
            case MAYBE: return POSSIBLE_OUTAGE;
            default: return NO_SCHEDULE;
        }
    }

    /** "без світла", "світло", "можливе вимкнення": what comes after a change. */
    private static String coming(char slot) {
        if (slot == '0') return "без світла";
        return slot == '1' ? "світло" : "можливе вимкнення";
    }

    public static Now now(String tl, LocalDate base, long now, boolean none) {
        return now(tl, base, now, none, null);
    }

    public static Now now(String tl, LocalDate base, long now, boolean none, String yesterday) {
        Map<LocalDate, String> history = new HashMap<>();
        if (yesterday != null) history.put(base.minusDays(1), yesterday);
        return nowWithHistory(tl, base, now, none, history);
    }

    public static Now nowWithHistory(String tl, LocalDate base, long now, boolean none,
                                     Map<LocalDate, String> history) {
        Now n = new Now();
        int cur = index(base, now);
        char c = cur >= 0 && cur < tl.length() ? tl.charAt(cur) : '2';
        n.state = state(c);
        n.title = title(n.state);
        if (n.state == UNKNOWN) {
            n.big = NOT_PUBLISHED;
            n.hint = "Щойно обленерго його оприлюднить, він з'явиться тут";
        } else if (none) {
            n.big = NO_OUTAGES;
            n.hint = "Вимкнень за графіком не заплановано";
        } else {
            List<int[]> ch = clockChanges(tl, base, cur);
            if (ch.isEmpty()) steady(n, tl, cur, c, base, now);
            else changing(n, ch, c, base, now);
        }
        if (n.state != UNKNOWN) since(n, tl, cur, c, history, base, now);
        return n;
    }

    /** Walk the contiguous saved schedules back to where this state began, as far as they are known. */
    private static void since(Now n, String tl, int cur, char c, Map<LocalDate, String> history,
                              LocalDate base, long now) {
        int i = cur;
        while (i > 0 && tl.charAt(i - 1) == c) i--;
        if (i > 0) {
            began(n, base, i, base, now);
            return;
        }
        LocalDate start = base;
        for (int back = 1; back <= HISTORY_DAYS; back++) {
            LocalDate day = base.minusDays(back);
            String slots = history.get(day);
            if (slots == null || slots.length() != 48) break;
            int j = 48;
            while (j > 0 && slots.charAt(j - 1) == c) j--;
            if (j > 0) {
                // j == 48 means the state began at the midnight after that day.
                began(n, day, j, base, now);
                return;
            }
            start = day;
        }
        began(n, start, 0, base, now);
    }

    private static void began(Now n, LocalDate day, int slot, LocalDate base, long now) {
        long start = slotStart(day, slot);
        while (start < 0 && slot < 48) start=slotStart(day,++slot);
        LocalDate date = z(start).toLocalDate();
        String suffix = "";
        if (date.equals(base.minusDays(1))) suffix = " учора";
        else if (date.isBefore(base)) suffix = String.format(Locale.ROOT, " %d.%02d", date.getDayOfMonth(), date.getMonthValue())
                + (date.getYear() == base.getYear() ? "" : "." + date.getYear());
        n.since = "з " + hm(start) + suffix;
        n.already = goingOn(now - start);
    }

    /** No change while the data lasts: either tomorrow is not published yet, or nothing changes. */
    private static void steady(Now n, String tl, int cur, char c, LocalDate base, long now) {
        int j = cur;
        while (j < tl.length() && tl.charAt(j) == c) j++;
        if (j < 48 && j < tl.length()) {
            n.knownUntil = slotStart(base,j);
            while (n.knownUntil < 0 && j < 48) n.knownUntil=slotStart(base,++j);
            n.big = "до " + until(n.knownUntil,now);
            n.hint = "Далі графіка немає";
        } else if (j < tl.length()) {
            n.big = TO_DAY_END;
            n.hint = "Графік на завтра ще не опубліковано";
        } else if (c == '1') {
            n.big = NO_OUTAGES;
            n.hint = "Сьогодні й завтра вимкнень немає";
        } else {
            n.big = "понад добу";
            n.hint = "За графіком і завтра";
        }
    }

    private static void changing(Now n, List<int[]> ch, char c, LocalDate base, long now) {
        long change = slotStart(base, ch.get(0)[0]);
        char next = (char) ch.get(0)[1];
        n.change = change;
        n.tomorrow = dayDiff(now, change) > 0 && !hm(change).equals("00:00");
        n.changeClock = until(change, now).equals(MIDNIGHT) ? MIDNIGHT : hm(change);
        n.next = state(next);
        n.big = "до " + until(change, now);
        n.soon = change - now <= SOON_S;
        if (ch.size() > 1 || next != '1') {  // "потім світло" with no end tells nothing new
            n.then = "потім " + coming(next) + (ch.size() > 1 ? " до " + until(slotStart(base, ch.get(1)[0]), now) : "");
        }
        if (ch.size() > 1) n.endDetail = "до " + until(slotStart(base, ch.get(1)[0]), now);
        // the clock first, how long in brackets: "Вимкнуть о 22:30", "(через 1 год 44 хв)"
        String at = until(change, now);
        String when = n.tomorrow ? " завтра о " + hm(change) : " о " + at;
        if (next == '0') n.hint = "Вимкнуть" + when;
        else if (next == '3') n.hint = "Можливо вимкнуть" + when;
        else if (c == '0') n.hint = "Увімкнуть" + when;
        else n.hint = "Буде світло" + when;
        n.hintIn = "(" + comingIn(change - now) + ")";
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
        return slot >= 48 ? MIDNIGHT : String.format(Locale.ROOT, "%02d:%02d", slot / 2, (slot % 2) * 30);
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
        if (day == null) return NOT_PUBLISHED;
        int off = 0;
        for (int i = 0; i < day.length(); i++) if (day.charAt(i) == '0') off++;
        if (off == 0) return NO_OUTAGES;
        return NO_POWER + length(off);
    }

    static final int HISTORY_DAYS = 31;
    private static final String LIGHT_SHARE = " світла · ";
    /** The last line of a shared picture: where the app comes from. */
    public static final String SHARE_SIGNATURE = "Світлячок · abovsh.github.io/svitlyachok";

    /**
     * "очікується 18 год світла · 75 %": the positive side of a day, as a share of all 24 hours;
     * "очікується", so nobody reads the schedule as a record of what happened.
     */
    public static String dayLight(String day) {
        if (day == null) return NOT_PUBLISHED;
        int on = count(day, '1');
        if (on == day.length()) return NO_OUTAGES;
        return "очікується " + length(on) + LIGHT_SHARE + Math.round(on * 100f / day.length()) + " %";
    }

    private static int count(String day, char c) {
        int n = 0;
        for (int i = 0; i < day.length(); i++) if (day.charAt(i) == c) n++;
        return n;
    }

    /** One day of the statistics, in half hours; `known` is false when no schedule was seen. */
    public static final class DayStat {
        public LocalDate date;
        public boolean known;
        public int on;
        public int off;
        public int maybe;
    }

    /**
     * The schedules the phone has seen, by day: the latest one of each day is kept, from a month
     * ago to tomorrow. The sources publish only today and tomorrow, so this is the only history.
     */
    public static Map<LocalDate, String> remember(Map<LocalDate, String> hist, LocalDate date, String slots, LocalDate today) {
        Map<LocalDate, String> out = new HashMap<>();
        LocalDate oldest = today.minusDays(HISTORY_DAYS - 1L);
        LocalDate newest = today.plusDays(1);
        for (Map.Entry<LocalDate, String> e : hist.entrySet()) {
            if (!e.getKey().isBefore(oldest) && !e.getKey().isAfter(newest)) out.put(e.getKey(), e.getValue());
        }
        if (slots != null && slots.length() == 48 && !date.isBefore(oldest) && !date.isAfter(newest)) out.put(date, slots);
        return out;
    }

    /** The last `days` days up to today (a month at most), oldest first. */
    public static List<DayStat> window(Map<LocalDate, String> hist, LocalDate today, int days) {
        int n = Math.min(days, HISTORY_DAYS);
        List<DayStat> out = new ArrayList<>();
        for (int i = n - 1; i >= 0; i--) {
            DayStat d = new DayStat();
            d.date = today.minusDays(i);
            String slots = hist.get(d.date);
            if (slots != null) {
                d.known = true;
                d.on = count(slots, '1');
                d.off = count(slots, '0');
                d.maybe = count(slots, '3');
            }
            out.add(d);
        }
        return out;
    }

    /** Four completed seven-day periods; unknown days never mean zero hours of power. */
    public static final class WeekStat {
        public LocalDate from;
        public LocalDate to;
        public int known;
        public int onMinutes = -1;
        public int onPercent = -1;
        public int possible;
        public List<DayStat> days = new ArrayList<>();
    }

    public static List<WeekStat> fourWeeks(Map<LocalDate, String> history, LocalDate today) {
        List<WeekStat> out = new ArrayList<>();
        List<DayStat> days = window(history, today.minusDays(1), 28);
        for (int i = 0; i < 4; i++) {
            WeekStat w = new WeekStat();
            w.from = days.get(i * 7).date;
            w.to = days.get(i * 7 + 6).date;
            for (DayStat d : days.subList(i * 7, i * 7 + 7)) {
                if (statFull(d)) {
                    w.days.add(d); w.possible += d.maybe;
                }
            }
            w.known = w.days.size();
            w.onMinutes = statAverageMinutes(w.days, ON);
            w.onPercent = statShare(w.days, ON);
            out.add(w);
        }
        return out;
    }

    /** Minutes of power gained per day vs the previous week; no claim on incomplete data. */
    public static int weekChange(List<WeekStat> weeks) {
        if (weeks.size() < 2) return Integer.MIN_VALUE;
        WeekStat a = weeks.get(weeks.size() - 2);
        WeekStat b = weeks.get(weeks.size() - 1);
        if (a.known != 7 || b.known != 7 || a.possible > 0 || b.possible > 0) return Integer.MIN_VALUE;
        return b.onMinutes - a.onMinutes;
    }

    /** The gap between day bars: narrower than designed only when a number would otherwise vanish. */
    public static float statGap(float slot, float preferred, float needed, float least) {
        return Math.max(least, Math.min(Math.min(slot * .25f, preferred), slot - needed));
    }

    /** A week with no full day draws an empty frame, which the legend has to name. */
    public static boolean weeksMissData(List<WeekStat> weeks) {
        for (WeekStat w : weeks) if (w.onMinutes < 0) return true;
        return false;
    }

    /** Keep the normal type size: shorten only the long outage state when it needs the room. */
    public static String fittedState(String text, float measured, float available) {
        return text.equals("Світла немає") && measured > available ? "Немає" : text;
    }

    /** Only a complete known day has a precise daily percentage; possible outages are not counted as power. */
    public static String dayPercent(String day) {
        if (day == null || day.length() != 48 || count(day, '2') > 0) return "—";
        return (count(day, '3') > 0 ? "≥" : "") + Math.round(count(day, '1') * 100f / 48) + "%";
    }

    /** A tap on the two rows of twelve hours, including which half of its hour it hits. */
    public static int hourGridSlot(float x, float y, float width, float height) {
        float gap = height * 3 / 55;
        float cell = Math.max(1, (width - gap * 11) / 12);
        int column = Math.max(0, Math.min(11, (int) (x / (cell + gap))));
        int hour = (y >= height / 2 ? 12 : 0) + column;
        return hour * 2 + (x - column * (cell + gap) >= cell / 2 ? 1 : 0);
    }

    /** At noon the visible 24 hours can cross midnight; undersized legacy cells keep the full day. */
    public static boolean widgetSplitTomorrow(int height, String tomorrow, int nowSlot) {
        return height >= 140 && nowSlot >= 24 && nowSlot < 48 && !widgetTomorrowMissing(tomorrow);
    }

    /** A range label belongs only to a partial day, never to the ordinary full-day view. */
    public static String widgetDayTitle(boolean tomorrow, boolean split) {
        String day = tomorrow ? TOMORROW : "Сьогодні";
        if (!split) return day;
        return day + (tomorrow ? " · 00–11" : " · 12–23");
    }

    /** Name, two state panes, and either one full day or two half-day rows. */
    public static float[] widgetGridLayout(int height, boolean split) {
        float top = 27;
        float sections = split ? 2f : 1f;
        float section = Math.min(split ? 34f : 59f, ((float) height - 8 - top - 40) / sections);
        float panes = (float) height - 8 - top - sections * section;
        return new float[] {top, panes, split ? 1 : 0, section};
    }

    /** Exact positive summaries, from full wording to the narrowest single-line form. */
    public static List<String> daySummaries(String slots) {
        int on = count(slots, '1');
        if (count(slots, '2') > 0) return java.util.Collections.nCopies(3, "Графік неповний");
        String pct = dayPercent(slots).replace("%", " %");
        String hours = (on % 2 == 0 ? String.valueOf(on / 2) : on / 2 + ",5") + " год";
        List<String> out = new ArrayList<>();
        out.add(hoursMinutes(on * 30) + LIGHT_SHARE + pct);
        out.add(hours + LIGHT_SHARE + pct);
        out.add(hours + " · " + pct);
        return out;
    }

    /** "18 год 20 хв", "18 год", "45 хв" */
    static String hoursMinutes(int minutes) {
        int h = minutes / 60;
        int m = minutes % 60;
        if (h == 0) return m == 0 ? "0 год" : m + " хв";
        return m == 0 ? h + " год" : h + " год " + m + " хв";
    }

    /** A day for a screen reader: "Сьогодні: без світла 06:00 – 08:00; можливе вимкнення 20:00 – 22:00". */
    public static String describe(String title, String day) {
        if (day == null) return title + ": графік ще не опубліковано";
        String off = list(day, false);
        String maybe = list(day, true);
        return title + ": " + (off.isEmpty() ? NO_OUTAGES : NO_POWER + off)
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
        public boolean tomorrow;
        public boolean changes;

        /** Anything at all: otherwise the bell of the place is crossed out. */
        public boolean any() {
            return off || on || tomorrow || changes;
        }
    }

    /** A place just added notifies of everything; a place saved before keeps its own choice. */
    static Alerts newPlaceAlerts() {
        Alerts a = new Alerts();
        a.tomorrow = true;
        a.changes = true;
        return a;
    }

    /**
     * The notifications of one place: its own record, or, for a place from before 1.3, the old
     * common settings (switched off where the old bell was off); defaults for a new place.
     */
    static Alerts alertsFrom(String json, Alerts legacy, boolean muted) {
        Alerts a = new Alerts();
        if (json != null) {
            try {
                JSONObject o = new JSONObject(json);
                a.off = o.optBoolean("off", a.off);
                a.on = o.optBoolean("on", a.on);
                a.leadOff = Math.max(0, Math.min(120, o.optInt("leadOff", a.leadOff)));
                a.leadOn = Math.max(0, Math.min(120, o.optInt("leadOn", a.leadOn)));
                a.tomorrow = o.optBoolean("tomorrow", a.tomorrow);
                a.changes = o.optBoolean("changes", a.changes);
            } catch (JSONException e) {
                return new Alerts();
            }
            return a;
        }
        if (legacy != null) {
            a.off = legacy.off;
            a.on = legacy.on;
            a.leadOff = legacy.leadOff;
            a.leadOn = legacy.leadOn;
            a.tomorrow = legacy.tomorrow;
            a.changes = legacy.changes;
        }
        if (muted) {
            a.off = false;
            a.on = false;
            a.tomorrow = false;
            a.changes = false;
        }
        return a;
    }

    static String alertsJson(Alerts a) {
        try {
            return new JSONObject().put("off", a.off).put("on", a.on).put("leadOff", a.leadOff)
                    .put("leadOn", a.leadOn).put("tomorrow", a.tomorrow).put("changes", a.changes).toString();
        } catch (JSONException e) {
            return "{}";  // only for a non-finite number, which none of these is
        }
    }

    /** "За 15 хв до вимкнення й увімкнення", "У момент вимкнення · графік на завтра", "Вимкнено". */
    static String alertsSummary(Alerts a) {
        List<String> main = new ArrayList<>();
        if (a.off && a.on && a.leadOff == a.leadOn) {
            main.add(ahead(a.leadOff, "вимкнення й увімкнення"));
        } else {
            if (a.off) main.add(ahead(a.leadOff, "вимкнення"));
            if (a.on) main.add(ahead(a.leadOn, "увімкнення"));
        }
        List<String> parts = new ArrayList<>();
        if (!main.isEmpty()) parts.add(String.join(", ", main));
        if (a.tomorrow) parts.add("графік на завтра");
        if (a.changes) parts.add("зміни графіка");
        if (parts.isEmpty()) return "Вимкнено";
        String s = String.join(" · ", parts);
        return s.substring(0, 1).toUpperCase(Locale.ROOT) + s.substring(1);
    }

    private static String ahead(int lead, String what) {
        return lead == 0 ? "у момент " + what : "за " + lead + " хв до " + what;
    }

    /** The notification id of a place: the same for as long as the place exists. */
    static int slot(String placeId) {
        return 1000 + (placeId.hashCode() & 0x3FFF);
    }

    /** The old id stays with reminders; schedule notices use a disjoint range. */
    static int noticeSlot(String placeId, boolean reminder) {
        return slot(placeId) + (reminder ? 0 : 0x4000);
    }

    /** Opening the app goes to the network unless the source answered in the last 2 minutes. */
    static boolean dueOnOpen(long checkedAt, long now) {
        return checkedAt <= 0 || now < checkedAt || now - checkedAt >= 120;
    }

    /** An outage on the shared picture: its time; "зараз" is told by the filled chip. */
    static String shareChip(Outage o) {
        return (o.maybe ? "можливо " : "") + o.label.replace(" – ", "–");
    }

    /**
     * The place as notifications name it: with one place a name tells nothing, so it is left out;
     * from the second place on every notification says which place it is about.
     */
    static Place inNotices(Place p, int places) {
        if (places > 1) return p;
        Place q = new Place();
        q.id = p.id;
        q.region = p.region;
        q.group = p.group;
        return q;
    }

    /** Hosts GitHub serves release files from, after the redirect from the release address. */
    private static final String[] FILE_HOSTS = {"release-assets.githubusercontent.com", "objects.githubusercontent.com"};

    /** An update is downloaded only from a release of this repository and GitHub's file hosts. */
    static boolean trustedDownload(String url) {
        if (url == null) return false;
        if (url.startsWith(REPO + "releases/download/")) return true;
        for (String h : FILE_HOSTS) if (url.startsWith("https://" + h + "/")) return true;
        return false;
    }

    /** A title: the mark and what happens, "🔴 Скоро вимкнуть світло". */
    private static String headed(String mark, String what) {
        return mark + Character.toUpperCase(what.charAt(0)) + what.substring(1);
    }

    /** The second line, with the place first when there are several: "Дім · о 18:30, через 15 хв". */
    private static String named(String name, String text) {
        return name.isEmpty() ? text : name + " · " + text;
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
        // Scan the known edges, including yesterday supplied by the caller at midnight.
        char prev = tl.isEmpty() ? '2' : tl.charAt(0);
        for (int slot = 1; slot < tl.length(); slot++) {
            long change = slotStart(base, slot);
            if (change < 0) continue;
            char next=tl.charAt(slot);
            if (prev != next && prev != '2' && next != '2') {
                Event e=event(p,prev,next,change,a);
                if (e != null && (e.at < e.change ? e.change > now : e.at >= now-LATE_S)) out.add(e);
            }
            prev=next;
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
        for (int[] c : clockChanges(tl, base, i)) if (c[1] == '1') return slotStart(base, c[0]);
        return 0;
    }

    /** A notification: its one line, when, red or green, and when it is no longer true. */
    public static final class Notice {
        public String title = "";
        public String text = "";
        public boolean on;
        public int state = UNKNOWN;
        public long change;
        public long planned;
        public int kind;
        public long until;
    }

    /**
     * A coloured circle first: most phones drop text colours in notifications, the circle stays.
     * Red — no power, green — power, amber — a possible outage.
     */
    static String mark(int state) {
        if (state == ON) return "\uD83D\uDFE2 ";
        return state == MAYBE ? "\uD83D\uDFE0 " : "\uD83D\uDD34 ";
    }

    /**
     * A reminder: a coloured mark and what happens; under it the place (when there are several)
     * and when. It is no
     * longer true once the outage ends (or 4 hours on, end unknown), an hour after power.
     */
    public static Notice eventText(Event e, String tl, LocalDate base) {
        return eventText(e, tl, base, e.at);
    }

    public static Notice eventText(Event e, String tl, LocalDate base, long now) {
        Notice n = new Notice();
        n.on = e.kind == Event.SOON_ON || e.kind == Event.AT_ON;
        int i = index(base, e.change);
        boolean maybe = !n.on && i >= 0 && i < tl.length() && tl.charAt(i) == '3';
        long end = n.on ? lightEnd(tl, base, e.change) : outageEnd(tl, base, e.change);
        int state = OFF;
        if (n.on) state = ON;
        else if (maybe) state = MAYBE;
        n.state = state; n.change = e.change; n.planned = e.at; n.kind = e.kind;
        String what = n.on ? "Увімкнуть світло" : "Вимкнуть світло";
        if (maybe) what = POSSIBLE_OUTAGE;
        n.title = headed(mark(state), what);
        int detailState = state;
        if (state == OFF && end > 0 && tl.substring(i, index(base, end)).indexOf('3') >= 0) detailState = MAYBE;
        n.text = named(e.name, eventWhen(e, end, detailState, now));
        if (e.at < e.change) n.until = e.change;
        else if (n.on) n.until = e.change + 3600;
        else n.until = end > 0 ? end : e.change + 4 * 3600;
        return n;
    }

    /** Guaranteed light ends at the first definite or possible outage, never beyond a gap. */
    private static long lightEnd(String tl, LocalDate base, long change) {
        for (int[] c : clockChanges(tl, base, index(base, change))) {
            if (c[1] == '0' || c[1] == '3') return slotStart(base, c[0]);
        }
        return 0;
    }

    private static String noticeClock(long at, long now) {
        long days = dayDiff(now, at);
        if (days == 0) return hm(at);
        return (days == 1 ? TOMORROW_WORD : dayWord(at, now)) + " " + hm(at);
    }

    /** Both on and off carry their full known interval; missing data is never a guessed end. */
    private static String eventWhen(Event e, long end, int state, long now) {
        String start = noticeClock(e.change, now);
        if (end <= e.change) {
            String unknown = "час увімкнення невідомий";
            if (state == ON) unknown = "час вимкнення невідомий";
            else if (state == MAYBE) unknown = "кінець поки невідомий";
            return "з " + start + " · " + unknown;
        }
        String stop = dayDiff(e.change, end) > 1 ? noticeClock(end, e.change) : until(end, e.change);
        String duration = hoursMinutes((int) ((end - e.change) / 60));
        String suffix = state == ON ? " світла" : " без світла";
        String detail = state == MAYBE ? "до " + duration : duration + suffix;
        return start + "–" + stop + " · " + detail;
    }

    static Notice probeNotice(String name, boolean on, long until) {
        Notice n = new Notice();
        n.state = on ? ON : OFF;
        n.title = headed(mark(n.state), on ? "Пробне: ввімкнення" : "Пробне: вимкнення");
        n.text = named(name, "Приклад: подія о 18:00");
        n.on = on;
        n.until = until;
        return n;
    }

    /** A calendar first: a schedule, not a change of power. */
    static final String SCHEDULE_MARK = "\uD83D\uDCC5 ";

    public static final class Note {
        public static final int PUBLISHED = 0;
        public static final int CHANGED = 1;
        public int kind;
        public String title = "";
        public String day = "";
        public String key = "";  // schedule content, independent of its short visible summary
        public String text = "";
        public String summary = "";
        public boolean today;
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
            if (n != null) {
                n.day = date.toString();
                out.add(n);
            }
        }
        return out;
    }

    /** One day's note; `from` = the first slot still ahead. Null when nothing worth saying changed. */
    private static Note note(Place p, String b, String a, int from, boolean today) {
        if (a == null) return null;  // not published, or a glitch of the source
        Note n = new Note();
        if (b != null && !outagesChanged(b, a, from)) return null;
        n.kind = b == null ? Note.PUBLISHED : Note.CHANGED;
        if (n.kind == Note.PUBLISHED) n.title = "Графік на " + (today ? TODAY_WORD : TOMORROW_WORD);
        else n.title = today ? "Зміни на " + TODAY_WORD : "Графік на " + TOMORROW_WORD + " оновлено";
        n.key = a;
        n.today = today;
        n.summary = noticeSummary(a);
        n.text = named(p.name, n.summary);
        return n;
    }

    static String noticeSummary(String day) {
        boolean maybe = count(day, '3') > 0;
        if (count(day, '2') > 0) return "Графік неповний" + (maybe ? " · можливі вимкнення" : "");
        int on = count(day, '1');
        if (maybe && on == 0) return count(day, '3') == 48 ? "Можливі вимкнення протягом доби" : "Є можливі вимкнення";
        if (maybe) return "Від " + hoursMinutes(on * 30) + " світла · можливі вимкнення";
        if (on == 0) return "Без світла усю добу";
        if (on == 48) return "Без планових вимкнень · 24 год світла";
        return daySummaries(day).get(0);
    }

    /**
     * Whether a person asked for this note: "Графік на завтра" covers tomorrow's first schedule and its
     * revisions until midnight; "Зміни графіка" covers changes of the day already going on.
     */
    static boolean wanted(Note n, Alerts a) {
        if (n.kind == Note.PUBLISHED) return a.tomorrow;
        return n.today ? a.changes : a.tomorrow || a.changes;
    }

    /** Merge only the days that passed the place's preferences and duplicate checks. */
    static Notice scheduleNotice(Place p, List<Note> notes) {
        if (notes.isEmpty()) return null;
        Notice n = new Notice();
        if (notes.size() == 1) {
            n.title = notes.get(0).title;
            n.text = named(p.name, notes.get(0).summary);
            return n;
        }
        n.title = "Графік оновлено";
        List<String> days = new ArrayList<>();
        for (Note note : notes) days.add((note.today ? "Сьогодні: " : TOMORROW_LABEL) + note.summary);
        n.text = named(p.name, String.join("\n", days));
        return n;
    }

    /** Whether the outages still ahead moved: a slot that only became known or unknown moves none. */
    static boolean outagesChanged(String before, String after, int from) {
        return !aheadOutages(before, from).equals(aheadOutages(after, from));
    }

    private static String aheadOutages(String day, int from) {
        return day.substring(from).replace('2', '1');
    }

    private static final int NOTE_DAYS = 2;

    /** What was said last about each of the latest days, "date=text" on a line each. */
    static String rememberNote(String memory, String date, String text) {
        java.util.TreeMap<String, String> days = new java.util.TreeMap<>();
        for (String line : memory.split("\n")) {
            int at = line.indexOf('=');
            if (at > 0) days.put(line.substring(0, at), line.substring(at + 1));
        }
        days.put(date, text.replace('\n', ' '));
        while (days.size() > NOTE_DAYS) days.pollFirstEntry();
        StringBuilder out = new StringBuilder();
        for (Map.Entry<String, String> e : days.entrySet()) out.append(e.getKey()).append('=').append(e.getValue()).append('\n');
        return out.toString();
    }

    static boolean noteSeen(String memory, String date, String text) {
        return java.util.Arrays.asList(memory.split("\n")).contains(date + "=" + text.replace('\n', ' '));
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
            for (int[] c : clockChanges(tl, base, index(base, now))) {
                long t = slotStart(base, c[0]);
                for (long x : new long[] {t - SOON_S, t - HIGHLIGHT_S, t}) if (x > now && x < best) best = x;
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
            for (int[] c : clockChanges(tl, base, index(base, now))) {
                long t = slotStart(base, c[0]);
                for (long x : new long[] {t - SOON_S, t - HIGHLIGHT_S, t}) if (x >= at && x - at < POLL_S) return true;
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
    /** What each version brought: opened from the version line at the bottom of the main screen. */
    static final String CHANGELOG = REPO + "blob/main/CHANGELOG.md";
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

    /** What a problem report says about the app and the phone. */
    public static final class ReportInfo {
        public String version = "";
        public String android = "";
        public int api;
        public String phone = "";
        public List<Place> places = new ArrayList<>();
        public boolean notifications;
        public List<Alerts> alerts = new ArrayList<>();
        public boolean batteryUnlimited;
        public long checked;
        public long now;
        public int widgets;
    }

    /** The technical lines of a problem report: versions, phone, regions and queues, notifications. */
    public static String reportText(ReportInfo r) {
        return "Світлячок " + r.version + "\n"
                + "Android " + r.android + " (API " + r.api + "), " + r.phone + "\n"
                + "Місця: " + placesSummary(r.places) + "\n"
                + "Сповіщення: " + (r.notifications ? "дозволені" : "вимкнені в телефоні") + "\n"
                + placeAlerts(r)
                + "Обмеження батареї: " + (r.batteryUnlimited ? "немає" : "є") + "\n"
                + "Графік перевірено: " + (r.checked > 0 ? dayWord(r.checked, r.now) + " о " + hm(r.checked) : "ще ні") + "\n"
                + "Віджетів: " + r.widgets;
    }

    /** "Черга 1.1: за 15 хв до вимкнення й увімкнення", a line per place, without its name. */
    private static String placeAlerts(ReportInfo r) {
        StringBuilder b = new StringBuilder();
        for (int i = 0; i < r.places.size() && i < r.alerts.size(); i++) {
            String s = alertsSummary(r.alerts.get(i));
            b.append(QUEUE_WORD).append(queue(r.places.get(i).group)).append(": ")
                    .append(s.substring(0, 1).toLowerCase(Locale.ROOT)).append(s.substring(1)).append('\n');
        }
        return b.toString();
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

    /** One row (2 x 1 and wider), or with today's schedule when there is room. */
    public static final int W_ROW = 0;
    public static final int W_FULL = 1;

    /**
     * The place a widget shows: the one chosen for it, the first place when none was chosen, or
     * null when its place was removed (the widget says so instead of showing another one).
     */
    public static Place widgetPlace(List<Place> all, String chosen) {
        if (chosen.isEmpty()) return all.isEmpty() ? null : all.get(0);
        for (Place p : all) if (p.id.equals(chosen)) return p;
        return null;
    }

    /** Whether a place still has no widget of its own (`widgetPlaces`: the place of each widget). */
    public static boolean needsWidget(String place, List<String> widgetPlaces) {
        return !widgetPlaces.contains(place);
    }

    public static int widgetForm(int wDp, int hDp) {
        if (wDp >= 200 && hDp >= 110) return W_FULL;
        return W_ROW;
    }

    /** The cell until the launcher reports the real one; a large widget must not start as a row. */
    public static int[] widgetDefaultSize(boolean large) {
        return large ? new int[] {250, 150} : new int[] {180, 70};
    }

    /** Layout in dp: every full widget keeps both current and future information visible. */
    public static final class WidgetLayout {
        public boolean twoParts;
        public float pad;
        public float columnWidth;
        public float rowTop;
        public float rowHeight;
        public float barTop;
        public float barHeight;
        public float labelHeight;
        public float headerSize;
        public float labelSize;
        public float tomorrowHeight;
        public float tomorrowTop;
        public float tomorrowBarTop;
        public float tomorrowBarHeight;
        /** Tall enough for both days as on the card: a title, a bar and the times of its changes each. */
        public boolean sections;
        public float tomorrowTitleTop;
    }

    private static final float CAP = .74f;  // the height of a capital against the font size

    /** Sizes of a small widget {name, event, clock, gap}: the words keep their size, the clock takes what the width gives. */
    public static float[] widgetRowSizes(float rowHeight, float clockFit) {
        float word = Math.max(9, rowHeight * .21f);
        float[] sized = {word, word * .97f, Math.min(clockFit, rowHeight * .5f), 0};
        float caps = widgetRowBlock(sized);
        if (caps > rowHeight) {
            for (int i = 0; i < 3; i++) sized[i] *= rowHeight / caps;
            caps = rowHeight;
        }
        sized[3] = Math.min((rowHeight - caps) / 2, sized[2] * .5f);
        return sized;
    }

    /** The height of those lines from the top of the first capital to the last baseline. */
    public static float widgetRowBlock(float[] sizes) {
        return CAP * (sizes[0] + sizes[1] + sizes[2]) + 2 * sizes[3];
    }

    /** The width of the segment with the bulb, in dp. */
    public static float widgetBulbSegment(int width) {
        return Math.min(64, Math.max(36, width * .31f));
    }

    public static boolean widgetTomorrowMissing(String day) {
        return day == null || count(day, '2') == day.length();
    }

    public static final int WIDGET_FREE = 0;
    public static final int WIDGET_THIS = 1;
    public static final int WIDGET_HAS_OTHER = 2;

    /** How a place is marked when a widget is set up; `saved` is null for a widget just added. */
    public static int widgetMark(Place p, String saved, List<Place> all, List<String> others) {
        Place shown = saved == null ? null : widgetPlace(all, saved);
        if (shown != null && shown.id.equals(p.id)) return WIDGET_THIS;
        return others.contains(p.id) ? WIDGET_HAS_OTHER : WIDGET_FREE;
    }

    /** Tomorrow in one line, the fullest form first and the shorter one when it does not fit. */
    public static String[] widgetTomorrow(String day) {
        String none = "Завтра: графіка ще немає";
        if (widgetTomorrowMissing(day)) return new String[] {none, none};
        List<String> labels = new ArrayList<>();
        for (Outage o : outages(day, -1)) labels.add(o.label.replace(" – ", "–"));
        if (labels.isEmpty()) {
            String clear = count(day, '2') > 0 ? "Завтра: графік неповний" : "Завтра без вимкнень";
            return new String[] {clear, clear};
        }
        int certain = offCount(day);
        if (certain == 0) {
            return new String[] {"Завтра можливо: " + String.join(", ", labels), "Завтра можливо: " + length(count(day, '3'))};
        }
        return new String[] {TOMORROW_LABEL + String.join(", ", labels), TOMORROW_LABEL + outagesWord(certain) + " · " + length(count(day, '0'))};
    }

    /** "без світла 10 год" for a day with an outage, otherwise nothing: the bar says the rest. */
    public static String widgetToday(String day) {
        return day == null ? "" : daySummaries(day).get(1);
    }

    /** The event of a reminder already on screen, for as long as the schedule keeps it. */
    public static Event shownEvent(Place p, String tl, LocalDate base, Alerts a, long change, int kind, long planned) {
        for (Event e : events(p, tl, base, planned, a)) if (e.change == change && e.kind == kind) return e;
        return null;
    }

    private static float widgetBarHeight(int height) {
        if (height < 130) return 10;
        if (height < 160) return 14;
        return height < 200 ? 18f : 22f;
    }

    /** Every dp of a large widget has a job: name, two panes, tomorrow when it fits, today's bar. */
    public static WidgetLayout widgetLayout(int width, int height, boolean titled) {
        WidgetLayout w = new WidgetLayout();
        w.twoParts = widgetForm(width, height) == W_FULL;
        w.pad = Math.max(8f, Math.min(10f, 8 + (height - 110) * .05f));
        w.headerSize = Math.max(11, Math.min(13, height * .08f));
        w.labelSize = Math.max(10, Math.min(13, height * .07f));
        w.columnWidth = (width - 2 * w.pad - 8) / 2;
        w.labelHeight = w.labelSize + 4;
        w.barHeight = widgetBarHeight(height);
        w.tomorrowHeight = height >= 135 && width >= 250 ? Math.min(20, height * .11f) : 0;
        w.tomorrowTop = height - w.pad - w.tomorrowHeight;
        w.barTop = w.tomorrowTop - w.labelHeight - w.barHeight;
        w.tomorrowBarTop = w.barTop + w.barHeight + 2;
        w.tomorrowBarHeight = w.tomorrowHeight - 2;
        w.rowTop = w.pad + (titled ? w.headerSize * 1.45f : 0);
        w.rowHeight = w.barTop - 2 - w.rowTop;
        if (height >= SECTIONS_FROM && w.twoParts) sections(w, height);
        return w;
    }

    private static final int SECTIONS_FROM = 185;  // lower, the halves would have to give up their size

    private static void sections(WidgetLayout w, int height) {
        w.sections = true;
        w.barHeight = height < 200 ? 14f : 22f;
        w.tomorrowBarHeight = w.barHeight;
        w.labelHeight = w.labelSize + 4;
        w.tomorrowHeight = w.headerSize + 3;
        w.tomorrowTop = height - w.pad - w.tomorrowHeight;
        w.tomorrowBarTop = height - w.pad - w.labelHeight - w.barHeight;
        w.tomorrowTitleTop = w.tomorrowBarTop - w.tomorrowHeight;
        w.barTop = w.tomorrowTitleTop - w.labelHeight - w.barHeight;
        w.rowHeight = w.barTop - 2 - w.rowTop;
    }

    // ---- the place card and the bar of a day ---------------------------------------

    /** Where to find the queue: the site of Kyiv's operator does not show it, another site does. */
    public static String queueHelp(Region r) {
        return r.id.equals("kyiv") ? "Не знаєте свою чергу? Знайдіть її за адресою на сайті «Без світла» ›"
                : "Не знаєте свою чергу? Перевірте за адресою на сайті обленерго ›";
    }

    /** Whether now stands in this run: its number reads as the window that is going on. */
    public static boolean barHoursActive(int from, int to, float now) {
        return now >= from && now < to;
    }

    /** Milliseconds to the next whole minute: every change and every "через N хв" falls on one. */
    public static long toNextMinute(long nowMs) {
        return 60_000L - nowMs % 60_000L;
    }

    /** The button of a place, which says so when nothing will be sent for it. */
    public static String alertsLabel(Alerts a) {
        return a.any() ? "Сповіщення" : "Без сповіщень";
    }

    /** The widget's fill at t across it: each state glows from its edge, the card between them. */
    public static int widgetGlow(int left, int right, int middle, float centre, float t) {
        if (left == right) return left;
        if (t < centre) return mixColour(left, middle, smooth(t / centre) * .9f);
        return mixColour(middle, right, .1f + .9f * smooth((t - centre) / (1 - centre)));
    }

    private static float smooth(float t) {
        float v = Math.max(0f, Math.min(1f, t));
        return v * v * (3 - 2 * v);
    }

    private static int mixColour(int a, int b, float t) {
        int out = 0xFF000000;
        for (int shift = 0; shift <= 16; shift += 8) {
            int from = (a >> shift) & 0xFF;
            int to = (b >> shift) & 0xFF;
            out |= Math.round(from + (to - from) * t) << shift;
        }
        return out;
    }

    /** The state the right of a widget is coloured by: what comes next; when nothing changes, the state itself. */
    public static int widgetRightState(Now n, String value) {
        if (n.next != UNKNOWN) return n.next;
        return value.equals(NO_OUTAGES_WIDGET) || value.equals(NO_CHANGE) ? n.state : UNKNOWN;
    }

    /** Where the capitals of the event start when words stand in place of a clock: the lines at equal distances. */
    public static float widgetEventTop(float block, float name, float event, float value) {
        return CAP * name + (block - CAP * (name + event + value)) / 2;
    }

    /** Where the hours of a window stand, in slots: the middle of all of it, so they read as its whole length. */
    public static float barHoursCentre(int from, int to) {
        return (from + to) / 2f;
    }

    public static final int INK_WHITE = 0;
    public static final int INK_QUIET = 1;
    public static final int INK_DARK = 2;

    /**
     * The colour of the hours in a run of the bar. White, and on the dark theme a window that is over
     * steps back; a possible outage is pale amber, where white would not read.
     */
    public static int barHoursInk(char slot, boolean past, boolean active, boolean dark) {
        if (past && !active && dark) return INK_QUIET;
        return slot == '3' ? INK_DARK : INK_WHITE;
    }

    /** "69% · 16,5 год": the share and the hours on equal terms, to the half hour the schedule is made of. */
    public static String statLine(int share, int minutes) {
        if (share < 0 || minutes < 0) return "—";
        return share + "% · " + barHours(Math.round(minutes / 30f)) + " год";
    }

    /** Hours inside a run of the bar, digits only: "3,5", "4". */
    public static String barHours(int halves) {
        return halves % 2 == 0 ? String.valueOf(halves / 2) : halves / 2 + ",5";
    }

    /**
     * How close to a change the card lights its last line: as early as the place's reminder for
     * it; without a reminder, or one at the very moment, half an hour.
     */
    public static long soonS(Alerts a, int next) {
        int lead = -1;
        if (next == ON) {
            if (a.on) lead = a.leadOn;
        } else if (a.off) {
            lead = a.leadOff;
        }
        return lead > 0 ? lead * 60L : HIGHLIGHT_S;
    }

    public static boolean highlight(Now n, Alerts alerts, long now) {
        return n.next != UNKNOWN && n.change > now && n.change - now <= soonS(alerts, n.next);
    }

    /** A clock time under the bar: its slot (0..48), what begins there, its centre. */
    public static final class Label {
        public int slot;
        public int kind;  // OFF, ON, MAYBE
        public float x;
        public int row;   // 0, or 1 when the day has more changes than one line holds
    }

    /**
     * The clock times under the bar of a day, `width` wide, each label `labelW` wide with `gap`
     * between: the next change and the next outage with its end always (moved aside if they are close),
     * then the later changes and the past ones where there is room. Midnight is no change.
     * `cur` is the slot of now, -1 for a day that is not today.
     */
    public static List<Label> barLabels(String day, int cur, float width, float labelW, float gap) {
        List<int[]> ch = changesInside(day);
        List<int[]> order = new ArrayList<>();
        int must = mustShow(ch, cur, order);
        for (int[] c : ch) if (c[0] > cur && !order.contains(c)) order.add(c);
        for (int k = ch.size() - 1; k >= 0; k--) if (ch.get(k)[0] <= cur) order.add(ch.get(k));
        List<Label> out = new ArrayList<>();
        float half = labelW / 2;
        float sw = width / 48f;
        for (int k = 0; k < order.size(); k++) {
            int[] c = order.get(k);
            float x = clampX(c[0] * sw, half, width);
            if (k < must) x = aside(out, x, labelW + gap, half, width);
            if (!has(out, c[0]) && free(out, x, labelW + gap)) {
                Label l = new Label();
                l.slot = c[0];
                l.kind = c[1];
                l.x = x;
                out.add(l);
            }
        }
        return out;
    }

    private static List<int[]> changesInside(String day) {
        List<int[]> ch = changes(day);
        ch.removeIf(c -> c[0] == 0);
        return ch;
    }

    /** All the changes when they fit one line; otherwise outages on one line and returns of power on another. */
    public static List<Label> barLabelRows(String day, int cur, float width, float labelW, float gap) {
        List<Label> one = barLabels(day, cur, width, labelW, gap);
        List<int[]> ch = changesInside(day);
        if (one.size() >= ch.size()) return one;
        List<Label> off = new ArrayList<>();
        List<Label> on = new ArrayList<>();
        for (int[] c : ch) {
            List<Label> line = c[1] == ON ? on : off;
            float x = clampX(c[0] * width / 48f, labelW / 2, width);
            if (!free(line, x, labelW + gap)) continue;
            Label l = new Label();
            l.slot = c[0];
            l.kind = c[1];
            l.x = x;
            l.row = c[1] == ON ? 1 : 0;
            line.add(l);
        }
        off.addAll(on);
        return off;
    }

    /** How many lines of times stand under a bar. */
    public static int barLabelLines(List<Label> labels) {
        int lines = 0;
        for (Label l : labels) lines = Math.max(lines, l.row + 1);
        return lines;
    }

    /** The whole run a touch falls in: its first slot and the slot after its last. */
    public static int[] barRun(String day, int slot) {
        int at = Math.max(0, Math.min(day.length() - 1, slot));
        char c = day.charAt(at);
        int from = at;
        int to = at + 1;
        while (from > 0 && day.charAt(from - 1) == c) from--;
        while (to < day.length() && day.charAt(to) == c) to++;
        return new int[] {from, to};
    }

    /**
     * The chosen run of the hour grid as one frame per row of twelve: {first hour, last hour}. A run
     * from 11:00 to 13:00 is two frames, the end of the first row and the start of the second.
     */
    static int[][] barFrames(int from, int to) {
        List<int[]> out = new ArrayList<>();
        if (from < 0 || to <= from) return new int[0][];
        for (int row = 0; row < 2; row++) {
            int a = Math.max(from, row * 24);
            int b = Math.min(to, row * 24 + 24);
            if (a < b) out.add(new int[] {a / 2, (b - 1) / 2});
        }
        return out.toArray(new int[0][]);
    }

    /** Where the hint above the grid points: the middle of the run's part in its upper row. */
    static float barTipSlot(int from, int to) {
        return from < 24 && to > 24 ? (from + 24) / 2f : (from + to) / 2f;
    }

    /**
     * Rows of text one gap apart, measured from a baseline to the cap top of the next row, and the
     * same gap above the first row and under the last. caps: cap height of each row; extra: how far
     * the last line of a row lies under its first; before: more room before a row. Returns the
     * baseline of each row and, last, the height of the whole.
     */
    static int[] rhythm(int gap, int[] caps, int[] extra, int[] before) {
        int[] out = new int[caps.length + 1];
        int y = 0;
        for (int i = 0; i < caps.length; i++) {
            y += gap + before[i] + caps[i];
            out[i] = y;
            y += extra[i];
        }
        out[caps.length] = y + gap;
        return out;
    }

    /** "15:30–19:30 · без світла · 4 год": what a touched run is. */
    public static String barTip(String day, int from, int to) {
        return clock(from) + "–" + clock(to) + " · " + tipWord(day.charAt(from)) + " · " + length(to - from);
    }

    private static String tipWord(char c) {
        switch (state(c)) {
            case ON: return "зі світлом";
            case OFF: return "без світла";
            case MAYBE: return "можливе вимкнення";
            default: return "графіка немає";
        }
    }

    /** Hours of a run with the word, where it fits: "4 год". */
    public static String barHoursWord(int halves) {
        return barHours(halves) + " год";
    }

    /** "через 2 год 29 хв": how long until the event of the card, in words a person would say. */
    public static String heroCountdown(Now n) {
        String text = n.hintIn;
        return text.startsWith("(") && text.endsWith(")") ? text.substring(1, text.length() - 1) : text;
    }

    /** What the right half says: the widget's words, and a day with no schedule names the last one known. */
    public static String[] heroFuture(Now n, String lastDay) {
        if (n.state == UNKNOWN && !lastDay.isEmpty()) return new String[] {"Останній графік", lastDay, ""};
        String[] next = widgetFuture(n);
        // only the clock: the end of the window is on the grid right below
        next[2] = "";
        return next;
    }

    /** The next notification still to come, from a sorted list; one already late is on its way. */
    public static Event nextAlert(List<Event> events, long now) {
        for (Event e : events) if (e.at >= now) return e;
        return null;
    }

    private static String dayOfAlert(Event e, long now) {
        long d = dayDiff(now, e.at);
        if (d == 0) return TODAY_WORD;
        if (d == 1) return TOMORROW_WORD;
        return String.format(Locale.ROOT, "%02d.%02d", z(e.at).getDayOfMonth(), z(e.at).getMonthValue());
    }

    /** Under the button: "наступне о 15:15", or for another day "завтра о 07:45". */
    public static String alertButtonLine(Event e, long now) {
        return (dayDiff(now, e.at) == 0 ? "наступне" : dayOfAlert(e, now)) + " о " + hm(e.at);
    }

    public static String alertSheetTitle(Event e, long now) {
        return "Наступне сповіщення: " + dayOfAlert(e, now) + " о " + hm(e.at);
    }

    /** "За 15 хв до вимкнення о 15:30", "У момент увімкнення о 19:30". */
    public static String alertSheetDetail(Event e) {
        boolean on = e.kind == Event.SOON_ON || e.kind == Event.AT_ON;
        long lead = (e.change - e.at) / 60;
        return (lead > 0 ? "За " + lead + " хв до " : "У момент ") + (on ? "увімкнення" : "вимкнення") + " о " + hm(e.change);
    }

    private static String leadWords(int lead) {
        return lead == 0 ? "одразу" : "за " + lead + " хв";
    }

    /** The notifications of a place on its row: "за 15 хв", "за 15 і 30 хв", "вимкнено". */
    public static String alertsShort(Alerts a) {
        if (a.off && a.on) {
            if (a.leadOff == a.leadOn) return leadWords(a.leadOff);
            if (a.leadOff > 0 && a.leadOn > 0) return "за " + a.leadOff + " і " + a.leadOn + " хв";
            return leadWords(a.leadOff) + ", " + leadWords(a.leadOn);
        }
        if (a.off) return leadWords(a.leadOff);
        if (a.on) return leadWords(a.leadOn);
        return a.any() ? "графік" : "вимкнено";
    }

    /** One line under the card: when the schedule was checked and, when known, published. */
    public static String checkedLine(long checked, long updated, long now) {
        if (checked <= 0) return "";
        if (updated <= 0) return "Графік перевірено: " + hm(checked);
        String day = dayDiff(updated, now) == 0 ? "" : dayWord(updated, now) + " ";
        return "Графік оновлено: " + day + hm(updated) + " · перевірено: " + hm(checked);
    }

    /** The button of the help on a power cut the schedule does not show. */
    public static String helpSite(Region r) {
        return r.id.equals("kyiv") ? "Сайт «Без світла» ›" : "Сайт для " + r.name.replace("а область", "ої області") + " ›";
    }

    /** The difference of two weeks in the places table; no claim without data. */
    public static String changeWords(int minutes) {
        if (minutes == Integer.MIN_VALUE) return "—";
        if (minutes == 0) return "без змін";
        return (minutes > 0 ? "↑ " : "↓ ") + hoursMinutes(Math.abs(minutes));
    }

    /** Compact tiles keep a literal state; an unpublished day is never treated as power. */
    public static String tileLine(Now n) {
        if (!n.big.startsWith("до ") || (n.state != ON && n.state != OFF)) return n.title;
        String until = n.big.equals(TO_DAY_END) ? "до " + MIDNIGHT : n.big.replace(UNTIL_TOMORROW, "до ");
        return (n.state == ON ? "Є " : "Немає ") + until;
    }

    /** Secondary formats may omit a repeated state word; the bulb and spoken text retain it. */
    public static List<String> tileFormats(Now n) {
        if (n.state == MAYBE && n.big.startsWith("до ")) return Collections.singletonList("Можливо\n" + n.big);
        String full = tileLine(n);
        if ((n.state == ON || n.state == OFF) && n.big.startsWith("до ")) {
            String compact = n.big.equals(TO_DAY_END) ? "до " + MIDNIGHT : n.big.replace(UNTIL_TOMORROW, "до ");
            return Arrays.asList(full, compact);
        }
        return Collections.singletonList(full);
    }

    /** The state card for a screen reader, without pauses for the parts it does not have. */
    public static String describeNow(Now n, String detail) {
        StringBuilder b = new StringBuilder("Зараз за графіком: ").append(n.title);
        if (!n.since.isEmpty()) b.append(' ').append(n.since);
        if (!n.already.isEmpty()) b.append(", ").append(n.already);
        if (!n.hint.isEmpty()) b.append(". ").append(n.hint);
        if (!detail.isEmpty()) b.append(". ").append(detail);
        return b.append('.').toString();
    }

    /** Optional visual details remain available to a screen reader at every font size. */
    public static String heroDescription(Now n) {
        String detail = nextDetail(n);
        if (n.next == ON && !n.endDetail.isEmpty()) detail += (detail.isEmpty() ? "" : ". ") + "Світло " + n.endDetail;
        return describeNow(n, detail);
    }

    /** A coming change with its clock; anything else is a statement about the schedule. */
    public static boolean hintIsEvent(Now n) {
        return n.next != UNKNOWN && n.change != 0;
    }

    /** Secondary line without brackets, with the known end of the next outage when available. */
    public static String nextDetail(Now n) {
        String text = n.hintIn;
        if (text.startsWith("(") && text.endsWith(")")) text = text.substring(1, text.length() - 1);
        int end = n.then.lastIndexOf(" до ");
        if (end >= 0 && (n.next == OFF || n.next == MAYBE)) text += (text.isEmpty() ? "" : " · ") + (n.next == MAYBE ? "можливе вимкнення " : "світла не буде ") + n.then.substring(end + 1);
        return text;
    }

    /** Label, exact clock (including tomorrow), and known end; no countdown on a widget. */
    public static String[] widgetNext(Now n) {
        if (n.next == UNKNOWN || n.change <= 0) return new String[] {"", "", ""};
        String event = "Буде світло";
        if (n.next == OFF) event = "Вимкнуть";
        else if (n.next == MAYBE) event = "Можливо вимкнуть";
        else if (n.state == OFF) event = "Увімкнуть";
        return new String[] {event + (n.tomorrow ? " завтра" : ""), n.changeClock.isEmpty() ? hm(n.change) : n.changeClock, n.endDetail};
    }

    /** Start clock plus its day, without squeezing the clock on a widget. */
    public static String[] widgetStart(Now n) {
        String value = n.since.isEmpty() ? n.big : n.since;
        int colon = value.indexOf(':');
        if (colon < 0) return new String[] {value, ""};
        int end = colon + 3;
        return new String[] {value.substring(0, end), value.substring(end).trim()};
    }

    public static String[] widgetFuture(Now n) {
        String[] next = widgetNext(n);
        if (!next[0].isEmpty()) return next;
        if (n.knownUntil > 0) return new String[] {"Після " + hm(n.knownUntil), NO_SCHEDULE, ""};
        if (n.state == UNKNOWN) return new String[] {"Далі", NO_SCHEDULE, ""};
        if (n.hint.equals("Графік на завтра ще не опубліковано")) return new String[] {TOMORROW, "Графіка ще немає", ""};
        if (n.state == ON && n.big.equals(NO_OUTAGES)) return new String[] {"Сьогодні й завтра", NO_OUTAGES_WIDGET, ""};
        return new String[] {"Далі", NO_CHANGE, "до кінця графіка"};
    }

    static int statValue(DayStat d, int state) {
        if (state == ON) return d.on;
        if (state == OFF) return d.off;
        return state == MAYBE ? d.maybe : 0;
    }

    /** A number on a bar: a zero has no segment and no number. */
    public static String statLabel(DayStat d, int state) {
        return statKnown(d) && statValue(d, state) == 0 ? "" : statNumber(d, state);
    }

    /** The last line of the main screen; `latest` is null until the release was checked. */
    public static String versionLine(String current, String latest) {
        String line = "Версія " + current;
        return latest != null && newer(latest, current) ? line + " · є нова " + latest + ", оновити ›" : line + " · що нового ›";
    }

    static boolean statKnown(DayStat d) {
        return d.known && d.on + d.off + d.maybe > 0;
    }

    /** Only a full published day supports a daily average or a share of 24 hours. */
    static boolean statFull(DayStat d) {
        return d.known && d.on + d.off + d.maybe == 48;
    }

    public static int statKnownDays(List<DayStat> days) {
        int count = 0;
        for (DayStat d : days) if (statFull(d)) count++;
        return count;
    }

    public static int statAverageMinutes(List<DayStat> days, int state) {
        int count = 0;
        int halves = 0;
        for (DayStat d : days) if (statFull(d)) { count++; halves += statValue(d, state); }
        return count == 0 ? -1 : Math.round(halves * 30f / count);
    }

    public static int statShare(List<DayStat> days, int state) {
        int value = 0;
        int total = 0;
        for (DayStat d : days) if (statFull(d)) {
            value += statValue(d, state);
            total += d.on + d.off + d.maybe;
        }
        return total == 0 ? -1 : Math.round(value * 100f / total);
    }

    public static String statNumber(DayStat d, int state) {
        if (!statKnown(d)) return "—";
        int halves = statValue(d, state);
        return halves % 2 == 0 ? String.valueOf(halves / 2) : halves / 2 + ",5";
    }

    public static String describeStats(List<DayStat> days) {
        StringBuilder text = new StringBuilder("За графіком. ");
        for (DayStat d : days) {
            text.append(d.date.getDayOfMonth()).append('.').append(d.date.getMonthValue()).append(": ");
            if (!statKnown(d)) text.append("графіка немає");
            else {
                text.append("зі світлом ").append(hoursMinutes(d.on * 30)).append(", без світла ").append(hoursMinutes(d.off * 30));
                if (d.maybe > 0) text.append(", можливе вимкнення ").append(hoursMinutes(d.maybe * 30));
            }
            text.append(". ");
        }
        return text.toString().trim();
    }

    /** Where a run of an outage begins (OFF or MAYBE) and where the power comes back after it (ON). */
    private static List<int[]> changes(String day) {
        List<int[]> ch = new ArrayList<>();  // slot, kind
        int i = 0;
        while (i < day.length()) {
            char c = day.charAt(i);
            int j = i;
            while (j < day.length() && day.charAt(j) == c) j++;
            if (c == '0' || c == '3') {
                ch.add(new int[] {i, state(c)});
                if (j >= day.length() || day.charAt(j) == '1') ch.add(new int[] {j, ON});
            }
            i = j;
        }
        return ch;
    }

    /** The next change, the next outage and its end, into `order`; how many they are. */
    private static int mustShow(List<int[]> ch, int cur, List<int[]> order) {
        int[] next = null;
        int[] nextOff = null;
        for (int[] c : ch) {
            if (c[0] > cur && next == null) next = c;
            if (c[0] > cur && nextOff == null && c[1] != ON) nextOff = c;
        }
        if (next != null) order.add(next);
        if (nextOff != null && nextOff != next) order.add(nextOff);
        int off = ch.indexOf(nextOff);
        if (off >= 0 && off + 1 < ch.size() && ch.get(off + 1)[1] == ON) order.add(ch.get(off + 1));
        return order.size();
    }

    private static float clampX(float x, float half, float width) {
        return Math.max(half, Math.min(width - half, x));
    }

    private static boolean has(List<Label> l, int slot) {
        for (Label x : l) if (x.slot == slot) return true;
        return false;
    }

    private static boolean free(List<Label> l, float x, float span) {
        for (Label o : l) if (Math.abs(o.x - x) < span - 0.01f) return false;
        return true;
    }

    /** A label that must be shown, moved away from the one it touches, by at most its own width. */
    private static float aside(List<Label> l, float x, float span, float half, float width) {
        for (Label o : l) {
            if (Math.abs(o.x - x) >= span) continue;
            float moved = clampX(x >= o.x ? o.x + span : o.x - span, half, width);
            if (Math.abs(moved - x) <= span && free(l, moved, span)) return moved;
        }
        return x;
    }
}
