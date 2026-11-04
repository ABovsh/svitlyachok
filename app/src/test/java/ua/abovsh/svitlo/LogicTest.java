package ua.abovsh.svitlo;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.time.LocalDate;
import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/** Every text and state of the app, on fixed clocks (Kyiv, winter time, UTC+2). */
public class LogicTest {
    // 2026-10-30 00:00 Kyiv and 2026-10-31 00:00 Kyiv: the day keys of the DTEK feed.
    static final long D30 = 1793311200L;
    static final long D31 = 1793397600L;
    static final LocalDate OCT30 = LocalDate.of(2026, 10, 30);

    static long at(int h, int m) {
        return D30 + h * 3600L + m * 60L;
    }

    // GPV1.1 on 30.10: on 00-06, off 06-08, on 08-14:30, off 14:30-18:30, on after.
    static final String HOURS_11 = "{\"1\":\"yes\",\"2\":\"yes\",\"3\":\"yes\",\"4\":\"yes\",\"5\":\"yes\",\"6\":\"yes\","
            + "\"7\":\"no\",\"8\":\"no\",\"9\":\"yes\",\"10\":\"yes\",\"11\":\"yes\",\"12\":\"yes\",\"13\":\"yes\",\"14\":\"yes\","
            + "\"15\":\"second\",\"16\":\"no\",\"17\":\"no\",\"18\":\"no\",\"19\":\"first\",\"20\":\"yes\",\"21\":\"yes\","
            + "\"22\":\"yes\",\"23\":\"yes\",\"24\":\"yes\"}";
    static final String DAY_11 = "111111111111000011111111111110000000011111111111";
    // 31.10: off 00-02 and 20-22.
    static final String HOURS_11_TOMORROW = "{\"1\":\"no\",\"2\":\"no\",\"3\":\"yes\",\"4\":\"yes\",\"5\":\"yes\",\"6\":\"yes\","
            + "\"7\":\"yes\",\"8\":\"yes\",\"9\":\"yes\",\"10\":\"yes\",\"11\":\"yes\",\"12\":\"yes\",\"13\":\"yes\",\"14\":\"yes\","
            + "\"15\":\"yes\",\"16\":\"yes\",\"17\":\"yes\",\"18\":\"yes\",\"19\":\"yes\",\"20\":\"yes\",\"21\":\"no\","
            + "\"22\":\"no\",\"23\":\"yes\",\"24\":\"yes\"}";

    static String feed(String days) {
        return "{\"lastUpdated\":1793311200,\"regionId\":\"kyiv-region\",\"fact\":{\"update\":\"29.10.2026 20:25\",\"today\":" + D30
                + ",\"data\":" + days + "},\"preset\":{\"sch_names\":{\"GPV1.1\":\"Черга 1.1\"}}}";
    }

    static final String TODAY_ONLY = feed("{\"" + D30 + "\":{\"GPV1.1\":" + HOURS_11 + "}}");
    static final String WITH_TOMORROW = feed("{\"" + D30 + "\":{\"GPV1.1\":" + HOURS_11 + "},\""
            + D31 + "\":{\"GPV1.1\":" + HOURS_11_TOMORROW + "}}");

    static Logic.Data data(String raw) throws Exception {
        return Logic.parse(raw);
    }

    static String tl(String raw) throws Exception {
        return Logic.timeline(data(raw), "GPV1.1", OCT30);
    }

    static Logic.Place home() {
        Logic.Place p = new Logic.Place();
        p.id = "p1";
        p.name = "Дім";
        p.region = "kyiv_region";
        p.group = "GPV1.1";
        return p;
    }

    static String repeat(char c, int n) {
        char[] a = new char[n];
        Arrays.fill(a, c);
        return new String(a);
    }

    // ---- parsing ---------------------------------------------------------------

    @Test
    public void hoursBecomeHalfHourSlots() throws Exception {
        assertEquals(DAY_11, Logic.day(data(TODAY_ONLY), "GPV1.1", OCT30));
    }

    @Test
    public void zeroBasedHoursAndMaybeStates() throws Exception {
        StringBuilder h = new StringBuilder("{");
        for (int i = 0; i < 24; i++) {
            String v = i == 0 ? "mfirst" : i == 1 ? "msecond" : i == 2 ? "maybe" : "yes";
            h.append(i > 0 ? "," : "").append('"').append(i).append("\":\"").append(v).append('"');
        }
        h.append('}');
        Logic.Data d = data(feed("{\"" + D30 + "\":{\"GPV1.1\":" + h + "}}"));
        assertEquals("311333" + repeat('1', 42), Logic.day(d, "GPV1.1", OCT30));
    }

    @Test
    public void tomorrowNotPublishedIsUnknown() throws Exception {
        Logic.Data d = data(TODAY_ONLY);
        assertNull(Logic.day(d, "GPV1.1", OCT30.plusDays(1)));
        assertEquals(DAY_11 + repeat('2', 48), Logic.timeline(d, "GPV1.1", OCT30));
    }

    @Test
    public void emptyDataMeansNoOutages() throws Exception {
        Logic.Data d = data(feed("[]"));
        assertTrue(d.none);
        assertEquals(repeat('1', 96), Logic.timeline(d, "GPV1.1", OCT30));
    }

    @Test
    public void updateTimeIsKyivTime() throws Exception {
        assertEquals(1793298300L, data(TODAY_ONLY).updated);
        assertEquals("графік опубліковано вчора о 20:25", Logic.updated(1793298300L, at(9, 0)));
        assertEquals("графік опубліковано сьогодні о 07:10", Logic.updated(at(7, 10), at(9, 0)));
        assertEquals("графік опубліковано 27.10 о 07:10", Logic.updated(at(7, 10) - 3 * 86400, at(9, 0)));
    }

    @Test
    public void groupsComeFromTheData() throws Exception {
        assertEquals(Arrays.asList("GPV1.1"), data(TODAY_ONLY).groups);
        assertEquals("1.1", Logic.queue("GPV1.1"));
    }

    @Test(expected = Exception.class)
    public void garbageIsRejected() throws Exception {
        data("{\"fact\":{\"data\":{\"x\":1}}}");
    }

    @Test
    public void everyRegionHasAName() {
        assertTrue(Logic.regions().size() >= 10);
        assertEquals("Київська область", Logic.region("kyiv_region").name);
        assertNull(Logic.region("nowhere"));
    }

    // ---- now ---------------------------------------------------------------------

    @Test
    public void onWithAnOutageLater() throws Exception {
        Logic.Now n = Logic.now(tl(TODAY_ONLY), OCT30, at(10, 0), false);
        assertEquals(Logic.ON, n.state);
        assertEquals("Світло є", n.title);
        assertEquals("до 14:30", n.big);
        assertEquals("Вимкнуть о 14:30", n.hint);
        assertEquals("(через 4 год 30 хв)", n.hintIn);
        assertEquals(Logic.OFF, n.next);  // the hint takes the colour of what comes
        assertFalse(n.soon);
    }

    @Test
    public void soonOff() throws Exception {
        Logic.Now n = Logic.now(tl(TODAY_ONLY), OCT30, at(14, 10), false);
        assertTrue(n.soon);
        assertEquals("Вимкнуть о 14:30", n.hint);
        assertEquals("(через 20 хв)", n.hintIn);
    }

    @Test
    public void offUntilBack() throws Exception {
        Logic.Now n = Logic.now(tl(TODAY_ONLY), OCT30, at(15, 0), false);
        assertEquals(Logic.OFF, n.state);
        assertEquals("Світла немає", n.title);
        assertEquals("до 18:30", n.big);
        assertEquals("Увімкнуть о 18:30", n.hint);
        assertEquals(Logic.ON, n.next);
    }

    @Test
    public void onToTheEndOfTheDayWithoutTomorrow() throws Exception {
        Logic.Now n = Logic.now(tl(TODAY_ONLY), OCT30, at(20, 0), false);
        assertEquals(Logic.ON, n.state);
        assertEquals("до кінця дня", n.big);
        assertEquals("Графік на завтра ще не опубліковано", n.hint);
        assertEquals("", n.hintIn);
        assertEquals(Logic.UNKNOWN, n.next);
    }

    @Test
    public void untilMidnightAndTomorrow() throws Exception {
        Logic.Now n = Logic.now(tl(WITH_TOMORROW), OCT30, at(20, 0), false);
        assertEquals("до 24:00", n.big);
        assertEquals("Вимкнуть о 24:00", n.hint);
        String later = DAY_11 + repeat('1', 12) + repeat('0', 36);
        assertEquals("до завтра 06:00", Logic.now(later, OCT30, at(20, 0), false).big);
    }

    @Test
    public void noOutagesAtAll() throws Exception {
        Logic.Now n = Logic.now(tl(feed("[]")), OCT30, at(12, 0), true);
        assertEquals(Logic.ON, n.state);
        assertEquals("без вимкнень", n.big);
        assertEquals("Вимкнень за графіком не заплановано", n.hint);
    }

    @Test
    public void unknownWithoutTodaysSchedule() {
        Logic.Now n = Logic.now(repeat('2', 96), OCT30, at(12, 0), false);
        assertEquals(Logic.UNKNOWN, n.state);
        assertEquals("Графіка немає", n.title);
        assertEquals("ще не опубліковано", n.big);
    }

    @Test
    public void possibleOutage() {
        String t = repeat('1', 20) + repeat('3', 8) + repeat('1', 20) + repeat('2', 48);
        Logic.Now n = Logic.now(t, OCT30, at(10, 30), false);
        assertEquals(Logic.MAYBE, n.state);
        assertEquals("Можливе вимкнення", n.title);
        assertEquals("до 14:00", n.big);
    }

    // ---- day lists ----------------------------------------------------------------

    @Test
    public void outagesOfADay() {
        List<Logic.Outage> o = Logic.outages(DAY_11, 20);
        assertEquals(2, o.size());
        assertEquals("06:00 – 08:00", o.get(0).label);
        assertEquals("2 год", o.get(0).length);
        assertEquals(Logic.Outage.PAST, o.get(0).when);
        assertEquals("14:30 – 18:30", o.get(1).label);
        assertEquals(Logic.Outage.LATER, o.get(1).when);
        assertEquals(Logic.Outage.NOW, Logic.outages(DAY_11, 30).get(1).when);
        assertEquals("22:00 – 24:00", Logic.outages(repeat('1', 44) + "0000", -1).get(0).label);
        assertEquals("30 хв", Logic.outages("0" + repeat('1', 47), -1).get(0).length);
    }

    @Test
    public void dayTotals() {
        assertEquals("без світла 6 год", Logic.dayTotal(DAY_11));
        assertEquals("без світла 1 год 30 хв", Logic.dayTotal("000" + repeat('1', 45)));
        assertEquals("без вимкнень", Logic.dayTotal(repeat('1', 48)));
        assertEquals("ще не опубліковано", Logic.dayTotal(null));
    }

    @Test
    public void remindersBeforeChanges() throws Exception {
        Logic.Alerts a = new Logic.Alerts();
        List<Logic.Event> ev = Logic.events(home(), tl(TODAY_ONLY), OCT30, at(10, 0), a);
        assertEquals(2, ev.size());
        assertEquals(Logic.Event.SOON_OFF, ev.get(0).kind);
        assertEquals(at(14, 15), ev.get(0).at);  // 15 minutes before
        assertEquals(at(14, 30), ev.get(0).change);
        assertEquals(Logic.Event.SOON_ON, ev.get(1).kind);
        assertEquals(at(18, 15), ev.get(1).at);
    }

    @Test
    public void reminderTexts() throws Exception {
        // what happens, then when; a coloured mark and the place in front
        String t = tl(TODAY_ONLY);
        Logic.Event e = Logic.events(home(), t, OCT30, at(10, 0), new Logic.Alerts()).get(0);
        Logic.Notice s = Logic.eventText(e, t, OCT30);
        // the place opens the second line: the title stays short enough for one line
        assertEquals("\uD83D\uDD34 Скоро вимкнуть світло", s.title);
        assertEquals("Дім · о 14:30", s.text);
        assertFalse(s.on);
        assertEquals(at(14, 30), s.until);  // gone by itself once the power is back
        Logic.Event on = Logic.events(home(), t, OCT30, at(15, 0), new Logic.Alerts()).get(0);
        Logic.Notice n = Logic.eventText(on, t, OCT30);
        assertEquals("\uD83D\uDFE2 Скоро увімкнуть світло", n.title);
        assertEquals("Дім · о 18:30", n.text);
        assertTrue(n.on);
        assertEquals(at(18, 30), n.until);
        String maybe = repeat('1', 20) + repeat('3', 8) + repeat('1', 20) + repeat('2', 48);
        Logic.Event m = Logic.events(home(), maybe, OCT30, at(9, 0), new Logic.Alerts()).get(0);
        assertEquals("\uD83D\uDFE0 Можливе вимкнення", Logic.eventText(m, maybe, OCT30).title);
    }

    @Test
    public void leadZeroMeansAtTheMoment() throws Exception {
        Logic.Alerts a = new Logic.Alerts();
        a.leadOff = 0;
        a.leadOn = 0;
        String t = tl(TODAY_ONLY);
        List<Logic.Event> ev = Logic.events(home(), t, OCT30, at(10, 0), a);
        assertEquals(2, ev.size());
        assertEquals(Logic.Event.AT_OFF, ev.get(0).kind);
        assertEquals(at(14, 30), ev.get(0).at);
        Logic.Notice off = Logic.eventText(ev.get(0), t, OCT30);
        assertEquals("\uD83D\uDD34 Вимкнення за графіком", off.title);
        assertEquals("Дім · до 18:30", off.text);
        assertEquals(Logic.Event.AT_ON, ev.get(1).kind);
        Logic.Notice on = Logic.eventText(ev.get(1), t, OCT30);
        assertEquals("\uD83D\uDFE2 Світло має з'явитися", on.title);
        assertEquals("Дім · за графіком з 18:30", on.text);
    }

    @Test
    public void anOutageWithoutAKnownEndIsDroppedAfterFourHours() throws Exception {
        String open = repeat('1', 12) + repeat('0', 36) + repeat('2', 48);
        Logic.Alerts a = new Logic.Alerts();
        a.leadOff = 0;
        Logic.Event e = Logic.events(home(), open, OCT30, at(3, 0), a).get(0);
        Logic.Notice s = Logic.eventText(e, open, OCT30);
        assertEquals("Дім · за графіком", s.text);
        assertEquals(at(10, 0), s.until);
    }

    // ---- 1.3: notifications per place, sharing, small fixes ------------------------

    @Test
    public void eachPlaceKeepsItsOwnNotifications() throws Exception {
        Logic.Alerts a = new Logic.Alerts();
        a.on = false;
        a.leadOff = 30;
        a.tomorrow = true;
        Logic.Alerts b = Logic.alertsFrom(Logic.alertsJson(a), null, false);
        assertFalse(b.on);
        assertTrue(b.off);
        assertEquals(30, b.leadOff);
        assertEquals(15, b.leadOn);
        assertTrue(b.tomorrow);
        assertFalse(b.changes);
    }

    @Test
    public void aPlaceWithoutItsOwnSettingsTakesTheOldCommonOnes() {
        Logic.Alerts old = new Logic.Alerts();
        old.leadOff = 60;
        old.changes = true;
        Logic.Alerts a = Logic.alertsFrom(null, old, false);
        assertEquals(60, a.leadOff);
        assertTrue(a.off && a.on && a.changes);
        // the bell of 1.2 switched a place off: it stays off
        Logic.Alerts m = Logic.alertsFrom(null, old, true);
        assertFalse(m.any());
        assertEquals(60, m.leadOff);  // the chosen lead is kept for when it is switched on again
        // a new place, or a broken record: the defaults, two reminders 15 minutes ahead
        Logic.Alerts d = Logic.alertsFrom("{broken", null, false);
        assertTrue(d.off && d.on && !d.tomorrow && !d.changes);
        assertEquals(15, d.leadOff);
    }

    @Test
    public void notificationSummaryOfAPlace() {
        Logic.Alerts a = new Logic.Alerts();
        assertTrue(a.any());
        assertEquals("За 15 хв до вимкнення й увімкнення", Logic.alertsSummary(a));
        a.leadOff = 30;
        assertEquals("За 30 хв до вимкнення, за 15 хв до увімкнення", Logic.alertsSummary(a));
        a.leadOff = 0;
        a.on = false;
        a.tomorrow = true;
        assertEquals("У момент вимкнення · графік на завтра", Logic.alertsSummary(a));
        a.off = false;
        a.tomorrow = false;
        a.changes = true;
        assertEquals("Зміни графіка", Logic.alertsSummary(a));
        a.changes = false;
        assertFalse(a.any());
        assertEquals("Вимкнено", Logic.alertsSummary(a));
    }

    @Test
    public void aNotificationKeepsItsPlaceSlot() {
        assertEquals(Logic.slot("p1730000000000"), Logic.slot("p1730000000000"));
        assertTrue(Logic.slot("p1") != Logic.slot("p2"));
        assertTrue(Logic.slot("p1") >= 1000);  // clear of the two test notifications (998, 999)
    }

    @Test
    public void openingTheAppSkipsTheNetworkRightAfterACheck() {
        assertTrue(Logic.dueOnOpen(0, at(10, 0)));
        assertFalse(Logic.dueOnOpen(at(9, 59), at(10, 0)));
        assertTrue(Logic.dueOnOpen(at(9, 58), at(10, 0)));
        assertTrue(Logic.dueOnOpen(at(10, 5), at(10, 0)));  // a clock set back
    }

    @Test
    public void summerTimeDaysKeepTheirSlotsOnTheClock() {
        LocalDate spring = LocalDate.of(2027, 3, 28);  // 03:00 -> 04:00
        LocalDate autumn = LocalDate.of(2027, 10, 31);  // 04:00 -> 03:00
        assertEquals(java.time.ZonedDateTime.of(2027, 3, 28, 18, 0, 0, 0, Logic.KYIV).toEpochSecond(),
                Logic.slotStart(spring, 36));
        assertEquals(36, Logic.index(spring, Logic.slotStart(spring, 36)));
        assertEquals(java.time.ZonedDateTime.of(2027, 10, 31, 18, 0, 0, 0, Logic.KYIV).toEpochSecond(),
                Logic.slotStart(autumn, 36));
        assertEquals(36, Logic.index(autumn, Logic.slotStart(autumn, 36)));
        // the evening outage is reminded 15 minutes ahead by the clock, not an hour off
        String day = repeat('1', 36) + repeat('0', 8) + repeat('1', 4) + repeat('2', 48);
        Logic.Event e = Logic.events(home(), day, spring, Logic.slotStart(spring, 20), new Logic.Alerts()).get(0);
        assertEquals(Logic.slotStart(spring, 36) - 15 * 60, e.at);
    }

    @Test
    public void aSharedPictureSaysWhereToGetTheApp() {
        // the picture says everything; the words only where to get the app, no place
        assertEquals("Завантажити застосунок: https://abovsh.github.io/svitlyachok/", Logic.SHARE_TEXT);
    }

    @Test
    public void aSwitchedOffKindIsNeverSent() throws Exception {
        String t = tl(TODAY_ONLY);
        Logic.Alerts a = new Logic.Alerts();
        a.off = false;
        List<Logic.Event> ev = Logic.events(home(), t, OCT30, at(10, 0), a);
        assertEquals(1, ev.size());
        assertEquals(Logic.Event.SOON_ON, ev.get(0).kind);
        a.off = true;
        a.on = false;
        ev = Logic.events(home(), t, OCT30, at(10, 0), a);
        assertEquals(1, ev.size());
        assertEquals(Logic.Event.SOON_OFF, ev.get(0).kind);
    }

    @Test
    public void clockTimesKeepLatinDigitsInAnyPhoneLanguage() {
        java.util.Locale was = java.util.Locale.getDefault();
        try {
            java.util.Locale.setDefault(java.util.Locale.forLanguageTag("ar-EG"));  // Arabic-Indic digits
            assertEquals("14:30", Logic.hm(at(14, 30)));
            assertEquals("1 год 05 хв", Logic.waitText(3900));
            assertEquals("06:00 – 08:00", Logic.outages(DAY_11, -1).get(0).label);
            assertEquals("графік опубліковано 27.10 о 07:10", Logic.updated(at(7, 10) - 3 * 86400, at(9, 0)));
        } finally {
            java.util.Locale.setDefault(was);
        }
    }

    @Test
    public void nestingIsMeasuredOutsideText() {
        // Android's own org.json has no depth limit: a deeply nested file would overflow its stack
        assertEquals(3, Logic.nesting("{\"a\":[{\"b\":1}]}"));
        assertEquals(1, Logic.nesting("{\"a\":\"[[[[{{{{\"}"));
        assertEquals(1, Logic.nesting("{\"a\":\"\\\"[[[\"}"));  // an escaped quote stays inside the text
        assertEquals(5, Logic.nesting(TODAY_ONLY));  // file, fact, data, day, queue
    }

    @Test(expected = Exception.class)
    public void deeplyNestedFileIsRejected() throws Exception {
        StringBuilder b = new StringBuilder("{\"fact\":{\"data\":");
        for (int i = 0; i < 100; i++) b.append('[');
        Logic.parse(b.toString());
    }

    @Test
    public void aBackgroundCheckRightAfterAnotherSkipsTheNetwork() {
        // the 15-minute alarm and the 15-minute job must not both wake the radio
        assertTrue(Logic.dueForFetch(0, at(10, 0)));                // never checked
        assertFalse(Logic.dueForFetch(at(10, 0), at(10, 1)));       // the job just after the alarm
        assertFalse(Logic.dueForFetch(at(10, 0), at(10, 12)));
        assertTrue(Logic.dueForFetch(at(10, 0), at(10, 13)));       // the next round
        assertTrue(Logic.dueForFetch(at(10, 0), at(14, 0)));        // a failed region keeps its old time
        assertTrue(Logic.dueForFetch(at(10, 30), at(10, 0)));       // the clock went back
    }

    // ---- updates -------------------------------------------------------------

    static final String LATEST = "https://github.com/ABovsh/svitlyachok/releases/tag/v1.3.0";

    @Test
    public void versionsCompareLikePeopleExpect() {
        assertTrue(Logic.newer("1.3.0", "1.2.0"));
        assertTrue(Logic.newer("1.10.0", "1.9.3"));
        assertTrue(Logic.newer("1.2.0", "1.2.0-rc.1"));  // the release after its candidate
        assertTrue(Logic.newer("v2.0", "1.9.9"));
        assertFalse(Logic.newer("1.2.0", "1.2.0"));
        assertFalse(Logic.newer("1.2.0-rc.2", "1.2.0"));
        assertFalse(Logic.newer("1.1.9", "1.2.0"));
        assertFalse(Logic.newer("not a version", "1.2.0"));
        assertFalse(Logic.newer("", "1.2.0"));
    }

    @Test
    public void latestReleaseIsReadFromWhereGitHubPoints() throws Exception {
        Logic.Release r = Logic.releaseAt(LATEST);
        assertEquals("1.3.0", r.version);
        assertEquals(LATEST, r.page);
        assertEquals("https://github.com/ABovsh/svitlyachok/releases/download/v1.3.0/Svitlyachok-1.3.0.apk", r.apk);
    }

    @Test
    public void onlyAReleaseOfThisRepositoryIsOffered() {
        String[] refused = {
            "https://example.com/ABovsh/svitlyachok/releases/tag/v1.3.0",   // another site
            "https://github.com/someone/svitlyachok/releases/tag/v1.3.0",  // another repository
            "https://github.com/ABovsh/svitlyachok/releases",               // nothing published yet
            "https://github.com/ABovsh/svitlyachok/releases/tag/latest",   // not a version
            "https://github.com/ABovsh/svitlyachok/releases/tag/v1.3.0/../../x",
            "http://github.com/ABovsh/svitlyachok/releases/tag/v1.3.0",
        };
        for (String url : refused) {
            try {
                Logic.releaseAt(url);
                throw new AssertionError("accepted " + url);
            } catch (Logic.BadFile expected) {
                // refused, as it should be
            }
        }
    }

    @Test
    public void updatesAreCheckedTwiceADayAtMost() {
        assertTrue(Logic.dueForUpdateCheck(0, at(10, 0)));
        assertFalse(Logic.dueForUpdateCheck(at(10, 0), at(21, 59)));
        assertTrue(Logic.dueForUpdateCheck(at(10, 0), at(22, 0)));
        assertTrue(Logic.dueForUpdateCheck(at(10, 0), at(9, 0)));  // the clock went back
    }

    // ---- problem reports -----------------------------------------------------

    @Test
    public void aReportNamesRegionsAndQueuesButNotThePlaces() {
        Logic.Place p1 = home();
        Logic.Place p2 = place("p2");
        p2.name = "Вулиця Садова 5";  // the user's own words stay on the phone
        p2.group = "GPV3.2";
        String s = Logic.placesSummary(Arrays.asList(p1, p2));
        assertEquals("Київська область, черга 1.1; Київська область, черга 3.2", s);
        assertFalse(s.contains("Дім"));
        assertFalse(s.contains("Садова"));
    }

    @Test
    public void aReportSaysWhatAFixNeeds() {
        Logic.ReportInfo r = new Logic.ReportInfo();
        r.version = "1.2.0";
        r.android = "15";
        r.api = 35;
        r.phone = "Samsung SM-A155F";
        r.places = Arrays.asList(home());
        r.notifications = true;
        Logic.Alerts a = new Logic.Alerts();
        a.on = false;
        r.alerts = Arrays.asList(a);
        r.batteryUnlimited = false;
        r.checked = at(9, 0);
        r.now = at(10, 0);
        r.widgets = 2;
        assertEquals("""
                Світлячок 1.2.0
                Android 15 (API 35), Samsung SM-A155F
                Місця: Київська область, черга 1.1
                Сповіщення: дозволені
                Черга 1.1: за 15 хв до вимкнення
                Обмеження батареї: є
                Графік перевірено: сьогодні о 09:00
                Віджетів: 2""", Logic.reportText(r));
        r.checked = 0;
        r.notifications = false;
        a.leadOff = 0;
        String s = Logic.reportText(r);
        assertTrue(s.contains("Сповіщення: вимкнені в телефоні\nЧерга 1.1: у момент вимкнення"));
        assertTrue(s.contains("Графік перевірено: ще ні"));
    }

    @Test
    public void aReportOpensAPrefilledIssueOfThisRepository() {
        String url = Logic.issueLink("Світлячок 1.2.0\nAndroid 15");
        assertTrue(url.startsWith("https://github.com/ABovsh/svitlyachok/issues/new?template=problem.yml&details="));
        assertTrue(url.endsWith("%D0%A1%D0%B2%D1%96%D1%82%D0%BB%D1%8F%D1%87%D0%BE%D0%BA+1.2.0%0AAndroid+15"));
    }

    // ---- light in hours, history, statistics ---------------------------------

    @Test
    public void aDaySaysHowLongThereIsLight() {
        // a forecast, not a record: "очікується"
        assertEquals("очікується 18 год світла · 75 %", Logic.dayLight(DAY_11));  // off 06-08 and 14:30-18:30
        assertEquals("очікується 22 год 30 хв світла · 94 %", Logic.dayLight("000" + repeat('1', 45)));
        assertEquals("без вимкнень", Logic.dayLight(repeat('1', 48)));
        assertEquals("ще не опубліковано", Logic.dayLight(null));
        assertEquals("очікується 0 год світла · 0 %", Logic.dayLight(repeat('0', 48)));
    }

    @Test
    public void historyKeepsAMonthAndTomorrow() {
        java.util.Map<LocalDate, String> h = new java.util.HashMap<>();
        h = Logic.remember(h, OCT30, DAY_11, OCT30);
        h = Logic.remember(h, OCT30.plusDays(1), repeat('1', 48), OCT30);     // tomorrow, planned
        h = Logic.remember(h, OCT30.plusDays(2), repeat('1', 48), OCT30);     // beyond tomorrow: not a schedule
        h = Logic.remember(h, OCT30, null, OCT30);                           // nothing published: kept
        h = Logic.remember(h, OCT30.minusDays(30), DAY_11, OCT30);            // 31 days ago, counting today: kept
        h = Logic.remember(h, OCT30.minusDays(31), DAY_11, OCT30);            // older: not kept
        assertEquals(3, h.size());
        assertEquals(DAY_11, h.get(OCT30));
        assertFalse(h.containsKey(OCT30.minusDays(31)));
        h = Logic.remember(h, OCT30, repeat('0', 48), OCT30);                // a change of today replaces it
        assertEquals(repeat('0', 48), h.get(OCT30));
    }

    @Test
    public void aWeekIsSevenBarsEndingToday() {
        java.util.Map<LocalDate, String> h = new java.util.HashMap<>();
        h.put(OCT30, DAY_11);                       // 18 h of light
        h.put(OCT30.minusDays(1), repeat('1', 48)); // 24 h
        h.put(OCT30.plusDays(1), repeat('0', 48));  // tomorrow is not in the past week
        List<Logic.DayStat> w = Logic.window(h, OCT30, 7);
        assertEquals(7, w.size());
        assertEquals(OCT30.minusDays(6), w.get(0).date);
        assertEquals(OCT30, w.get(6).date);
        assertFalse(w.get(0).known);
        assertEquals(36, w.get(6).on);               // halves
        assertEquals(12, w.get(6).off);
        assertEquals(31, Logic.window(h, OCT30, 40).size());  // a month at most
    }

    @Test
    public void onlyOffAndOnAreOnByDefault() {
        Logic.Alerts a = new Logic.Alerts();
        assertTrue(a.off);
        assertTrue(a.on);
        assertEquals(15, a.leadOff);  // 15 minutes before a blackout
        assertEquals(15, a.leadOn);
        assertFalse(Logic.DEFAULT_TOMORROW);
        assertFalse(Logic.DEFAULT_CHANGES);
        assertFalse(Logic.DEFAULT_QUIET);
    }

    static Logic.Place place(String id) {
        Logic.Place p = home();
        p.id = id;
        p.name = id;
        return p;
    }

    @Test
    public void aWidgetShowsItsOwnPlace() {
        List<Logic.Place> all = Arrays.asList(place("p1"), place("p2"), place("p3"));
        assertEquals("p2", Logic.widgetPlace(all, "p2").id);
        assertEquals("p1", Logic.widgetPlace(all, "").id);      // not chosen yet: the first place
        assertNull(Logic.widgetPlace(all, "gone"));  // its place was removed: say so, not show another
        assertNull(Logic.widgetPlace(Arrays.asList(), "p1"));   // no places at all
    }

    @Test
    public void aPlaceWithoutAWidgetIsOfferedOne() {
        List<Logic.Place> all = Arrays.asList(place("p1"), place("p2"));
        assertTrue(Logic.needsWidget("p2", Arrays.asList("p1")));
        assertFalse(Logic.needsWidget("p1", Arrays.asList("p1", "")));
        assertTrue(Logic.needsWidget("p1", Arrays.asList()));
        assertEquals(2, all.size());
    }

    static String ids(List<Logic.Place> ps) {
        List<String> out = new java.util.ArrayList<>();
        for (Logic.Place p : ps) out.add(p.id);
        return String.join(",", out);
    }

    @Test
    public void lateReminderKeptBriefly() throws Exception {
        String t = tl(TODAY_ONLY);
        // published at 14:24: the 14:15 reminder is 9 min late, still worth it
        assertEquals(at(14, 15), Logic.events(home(), t, OCT30, at(14, 24), new Logic.Alerts()).get(0).at);
        // at 14:26 it is 11 min late: dropped, the next one is the reminder before the power returns
        assertEquals(Logic.Event.SOON_OFF,
                Logic.events(home(), t, OCT30, at(14, 26), new Logic.Alerts()).get(0).kind);
        assertEquals(Logic.Event.SOON_ON,
                Logic.events(home(), t, OCT30, at(14, 30), new Logic.Alerts()).get(0).kind);
    }

    @Test
    public void tomorrowPublishedNote() throws Exception {
        List<Logic.Note> n = Logic.notes(home(), data(TODAY_ONLY), data(WITH_TOMORROW), at(19, 0));
        assertEquals(1, n.size());
        assertEquals(Logic.Note.PUBLISHED, n.get(0).kind);
        assertEquals("\uD83D\uDCC5 Графік на завтра", n.get(0).title);
        assertEquals("Дім · 2 вимкнення, без світла 4 год: 00:00 – 02:00, 20:00 – 22:00", n.get(0).text);
    }

    @Test
    public void changedTodayNote() throws Exception {
        String changed = HOURS_11.replace("\"20\":\"yes\"", "\"20\":\"no\"");
        Logic.Data after = data(feed("{\"" + D30 + "\":{\"GPV1.1\":" + changed + "}}"));
        List<Logic.Note> n = Logic.notes(home(), data(TODAY_ONLY), after, at(12, 0));
        assertEquals(1, n.size());
        assertEquals(Logic.Note.CHANGED, n.get(0).kind);
        assertEquals("\uD83D\uDCC5 Графік змінився", n.get(0).title);
        assertEquals("Дім · Сьогодні без світла 7 год: 06:00 – 08:00, 14:30 – 18:30, 19:00 – 20:00", n.get(0).text);
    }

    @Test
    public void noNoteForPastChangesSameDataOrFirstFetch() throws Exception {
        String pastOnly = HOURS_11.replace("\"1\":\"yes\"", "\"1\":\"no\"");
        Logic.Data after = data(feed("{\"" + D30 + "\":{\"GPV1.1\":" + pastOnly + "}}"));
        assertTrue(Logic.notes(home(), data(TODAY_ONLY), after, at(12, 0)).isEmpty());
        assertTrue(Logic.notes(home(), data(TODAY_ONLY), data(TODAY_ONLY), at(12, 0)).isEmpty());
        assertTrue(Logic.notes(home(), null, data(WITH_TOMORROW), at(12, 0)).isEmpty());
        // a source glitch that drops tomorrow says nothing
        assertTrue(Logic.notes(home(), data(WITH_TOMORROW), data(TODAY_ONLY), at(19, 0)).isEmpty());
    }

    @Test
    public void tomorrowWithoutOutages() throws Exception {
        String clean = HOURS_11_TOMORROW.replace("\"no\"", "\"yes\"");
        Logic.Data after = data(feed("{\"" + D30 + "\":{\"GPV1.1\":" + HOURS_11 + "},\"" + D31
                + "\":{\"GPV1.1\":" + clean + "}}"));
        assertEquals("Дім · Вимкнень не заплановано", Logic.notes(home(), data(TODAY_ONLY), after, at(19, 0)).get(0).text);
    }

    @Test
    public void quietNights() {
        assertTrue(Logic.quiet(at(23, 0)));
        assertTrue(Logic.quiet(at(6, 59)));
        assertFalse(Logic.quiet(at(7, 0)));
    }

    @Test
    public void nextRefreshIsTheNearestThresholdOrPoll() throws Exception {
        String t = tl(TODAY_ONLY);
        // 14:30 change: the "soon" threshold at 14:00 comes before the 15 min poll from 13:50
        assertEquals(at(14, 0), Logic.nextRefresh(Arrays.asList(t), OCT30, at(13, 50), at(13, 50)));
        assertEquals(at(10, 15), Logic.nextRefresh(Arrays.asList(t), OCT30, at(10, 0), at(10, 0)));
        assertEquals(at(10, 15), Logic.nextRefresh(Arrays.asList(t), OCT30, at(10, 0), 0));
    }

    @Test
    public void onlyThresholdsNeedAnExactAlarm() throws Exception {
        List<String> t = Arrays.asList(tl(TODAY_ONLY));
        assertTrue(Logic.exactRefresh(t, OCT30, at(13, 50), at(14, 0)));    // "soon" before 14:30
        assertTrue(Logic.exactRefresh(t, OCT30, at(14, 20), at(14, 30)));   // the change itself
        assertTrue(Logic.exactRefresh(t, OCT30, at(23, 50), D31));          // midnight
        assertFalse(Logic.exactRefresh(t, OCT30, at(10, 0), at(10, 15)));  // a plain poll
        // a poll just before a change must not drift past it
        assertTrue(Logic.exactRefresh(t, OCT30, at(13, 40), at(13, 55)));
    }

    @Test
    public void waitTexts() {
        assertEquals("42 хв", Logic.waitText(42 * 60));
        assertEquals("1 год 05 хв", Logic.waitText(3900));
    }

    // ---- 1.1: what comes next, safety, words for TalkBack, widget forms -------------

    @Test
    public void thenSaysWhatFollowsTheChange() throws Exception {
        assertEquals("потім без світла до 18:30", Logic.now(tl(TODAY_ONLY), OCT30, at(10, 0), false).then);
        // power after an outage with no known end says nothing new: no line
        assertEquals("", Logic.now(tl(TODAY_ONLY), OCT30, at(15, 0), false).then);
        assertEquals("потім світло до 24:00", Logic.now(tl(WITH_TOMORROW), OCT30, at(15, 0), false).then);
        // tomorrow not published: the outage after 06:00 has no known end
        String open = repeat('1', 12) + repeat('0', 36) + repeat('2', 48);
        assertEquals("потім без світла", Logic.now(open, OCT30, at(3, 0), false).then);
        String maybe = repeat('1', 20) + repeat('3', 8) + repeat('1', 20) + repeat('2', 48);
        assertEquals("потім можливе вимкнення до 14:00", Logic.now(maybe, OCT30, at(9, 0), false).then);
        assertEquals("", Logic.now(tl(TODAY_ONLY), OCT30, at(20, 0), false).then);
        assertEquals("", Logic.now(tl(feed("[]")), OCT30, at(12, 0), true).then);
    }

    @Test(expected = Exception.class)
    public void absurdGroupNameIsRejected() throws Exception {
        data(feed("{\"" + D30 + "\":{\"<script>" + repeat('x', 60) + "\":" + HOURS_11 + "}}"));
    }

    @Test(expected = Exception.class)
    public void tooManyDaysAreRejected() throws Exception {
        StringBuilder b = new StringBuilder("{");
        for (int i = 0; i < 40; i++) b.append(i > 0 ? "," : "").append('"').append(D30 + i * 86400L).append("\":{}");
        data(feed(b.append('}').toString()));
    }

    @Test
    public void dayInWordsForScreenReaders() {
        assertEquals("Сьогодні: без світла 06:00 – 08:00, 14:30 – 18:30, 20:00 – 21:00",
                Logic.describe("Сьогодні", "111111111111000011111111111110000000011100111111"));
        String m = repeat('1', 20) + repeat('3', 8) + repeat('1', 20);
        assertEquals("Завтра: без вимкнень; можливе вимкнення 10:00 – 14:00", Logic.describe("Завтра", m));
        assertEquals("Завтра: графік ще не опубліковано", Logic.describe("Завтра", null));
    }

    @Test
    public void widgetFormFollowsItsSize() {
        assertEquals(Logic.W_ROW, Logic.widgetForm(130, 60));    // 2 x 1
        assertEquals(Logic.W_WIDE, Logic.widgetForm(300, 70));   // 4 x 1: both halves and the day
        assertEquals(Logic.W_FULL, Logic.widgetForm(300, 130));  // 4 x 2: with the next 24 hours
        assertEquals(Logic.W_ROW, Logic.widgetForm(130, 130));   // 2 x 2: too narrow for the bar
    }

    // ---- 1.3: the card says since when; one place needs no name in notifications ------

    @Test
    public void theCardSaysSinceWhenAndHowLong() throws Exception {
        Logic.Now on = Logic.now(tl(TODAY_ONLY), OCT30, at(10, 0), false, null);
        assertEquals("з 08:00", on.since);
        assertEquals("уже 2 год 00 хв", on.already);
        Logic.Now off = Logic.now(tl(TODAY_ONLY), OCT30, at(15, 0), false, null);
        assertEquals("з 14:30", off.since);
        assertEquals("уже 30 хв", off.already);
        // A known run from midnight remains visible; a missing preceding slot is a lower bound.
        Logic.Now early = Logic.now(tl(TODAY_ONLY), OCT30, at(3, 0), false, null);
        assertEquals("з 00:00", early.since);
        assertEquals("уже 3 год 00 хв", early.already);
        String lateOn = repeat('0', 44) + repeat('1', 4);
        Logic.Now fromYesterday = Logic.now(tl(TODAY_ONLY), OCT30, at(3, 0), false, lateOn);
        assertEquals("з 22:00 учора", fromYesterday.since);
        assertEquals("уже 5 год 00 хв", fromYesterday.already);
        assertEquals("з 00:00", Logic.now(tl(TODAY_ONLY), OCT30, at(3, 0), false, repeat('0', 48)).since);
        // yesterday has hours without a schedule right before the run: the start is not known
        assertEquals("з 22:00 учора", Logic.now(tl(TODAY_ONLY), OCT30, at(3, 0), false, repeat('2', 44) + repeat('1', 4)).since);
        assertEquals("з 00:00", Logic.now(tl(TODAY_ONLY), OCT30, at(3, 0), false, repeat('1', 40) + repeat('2', 8)).since);
        // the same all yesterday: longer than a day, the start is not known
        Logic.Now allDay = Logic.now(tl(TODAY_ONLY), OCT30, at(3, 0), false, repeat('1', 48));
        assertEquals("з 00:00 учора", allDay.since);
        assertEquals("уже 1 день 3 год", allDay.already);
        // no schedule, or no outages at all: nothing to count from
        assertEquals("", Logic.now(repeat('2', 96), OCT30, at(12, 0), false, null).since);
        assertEquals("з 00:00", Logic.now(tl(feed("[]")), OCT30, at(12, 0), true, null).since);
    }

    @Test
    public void theHintSaysTheClockTimeThenHowLong() throws Exception {
        String later = DAY_11 + repeat('1', 12) + repeat('0', 36);
        Logic.Now n = Logic.now(later, OCT30, at(20, 0), false);
        assertEquals("Вимкнуть завтра о 06:00", n.hint);
        assertEquals("(через 10 год 00 хв)", n.hintIn);
    }

    @Test
    public void onePlaceNeedsNoNameInNotifications() throws Exception {
        assertEquals("", Logic.inNotices(home(), 1).name);
        assertEquals("Дім", Logic.inNotices(home(), 2).name);
        assertEquals("Дім", home().name);  // the place itself keeps its name
        Logic.Place only = Logic.inNotices(home(), 1);
        String t = tl(TODAY_ONLY);
        Logic.Event e = Logic.events(only, t, OCT30, at(10, 0), new Logic.Alerts()).get(0);
        assertEquals("p1", e.place);
        Logic.Notice n = Logic.eventText(e, t, OCT30);
        assertEquals("\uD83D\uDD34 Скоро вимкнуть світло", n.title);
        assertEquals("о 14:30", n.text);
        List<Logic.Note> tomorrow = Logic.notes(only, data(TODAY_ONLY), data(WITH_TOMORROW), at(19, 0));
        assertEquals("\uD83D\uDCC5 Графік на завтра", tomorrow.get(0).title);
        String changed = HOURS_11.replace("\"20\":\"yes\"", "\"20\":\"no\"");
        Logic.Data after = data(feed("{\"" + D30 + "\":{\"GPV1.1\":" + changed + "}}"));
        assertEquals("\uD83D\uDCC5 Графік змінився", Logic.notes(only, data(TODAY_ONLY), after, at(12, 0)).get(0).title);
    }

    // ---- 1.3.1: the update downloads inside the app ----------------------------------

    @Test
    public void anUpdateDownloadsOnlyFromThisReleaseAndGithubFiles() {
        assertTrue(Logic.trustedDownload("https://github.com/ABovsh/svitlyachok/releases/download/v1.3.1/Svitlyachok-1.3.1.apk"));
        // GitHub sends the file itself from its file hosts
        assertTrue(Logic.trustedDownload("https://release-assets.githubusercontent.com/github-production-release-asset/1/2?sp=r"));
        assertTrue(Logic.trustedDownload("https://objects.githubusercontent.com/github-production-release-asset-2e65be/1/2"));
        assertFalse(Logic.trustedDownload("http://github.com/ABovsh/svitlyachok/releases/download/v1.3.1/Svitlyachok-1.3.1.apk"));
        assertFalse(Logic.trustedDownload("https://github.com/someone/svitlyachok/releases/download/v1.3.1/x.apk"));
        assertFalse(Logic.trustedDownload("https://github.com/ABovsh/svitlyachok/archive/main.zip"));
        assertFalse(Logic.trustedDownload("https://evil.example/objects.githubusercontent.com/x"));
        assertFalse(Logic.trustedDownload("https://objects.githubusercontent.com.evil.example/x"));
        assertFalse(Logic.trustedDownload(null));
    }

    // ---- 1.4: three lines in the place card, a bar with clock times ---------------------

    @Test
    public void theNextLineSaysWhatComesWithACapital() throws Exception {
        String t = DAY_11 + repeat('1', 48);
        // a possible outage ahead, and the end of one
        String maybe = repeat('1', 20) + repeat('3', 8) + repeat('1', 68);
        assertEquals("Можливо вимкнуть о 10:00", Logic.now(maybe, OCT30, at(9, 0), false).hint);
        assertEquals("Буде світло о 14:00", Logic.now(maybe, OCT30, at(11, 0), false).hint);
        assertEquals("(через 3 год 00 хв)", Logic.now(maybe, OCT30, at(11, 0), false).hintIn);
        assertEquals("Сьогодні й завтра вимкнень немає", Logic.now(repeat('1', 96), OCT30, at(9, 0), false).hint);
        assertEquals("За графіком і завтра", Logic.now(repeat('0', 96), OCT30, at(9, 0), false).hint);
        assertEquals("Увімкнуть о 18:30", Logic.now(t, OCT30, at(15, 0), false).hint);
    }

    @Test
    public void thePresentSaysSinceWhenAndHowLong() throws Exception {
        Logic.Now on = Logic.now(tl(TODAY_ONLY), OCT30, at(10, 0), false, null);
        assertEquals("з 08:00", on.since);
        assertEquals("уже 2 год 00 хв", on.already);
        Logic.Now allDay = Logic.now(tl(TODAY_ONLY), OCT30, at(3, 0), false, repeat('1', 48));
        assertEquals("з 00:00 учора", allDay.since);
        assertEquals("уже 1 день 3 год", allDay.already);
        Logic.Now unknown = Logic.now(tl(TODAY_ONLY), OCT30, at(3, 0), false, null);
        assertEquals("з 00:00", unknown.since);
        assertEquals("уже 3 год 00 хв", unknown.already);
    }

    @Test
    public void hoursInsideTheBarToTenths() {
        assertEquals("3,5", Logic.barHours(7));
        assertEquals("4", Logic.barHours(8));
        assertEquals("0,5", Logic.barHours(1));
        assertEquals("24", Logic.barHours(48));
    }

    @Test
    public void theHighlightComesWithTheReminderOfThePlace() {
        Logic.Alerts a = new Logic.Alerts();
        a.leadOff = 15;
        a.leadOn = 10;
        assertEquals(15 * 60L, Logic.soonS(a, Logic.OFF));
        assertEquals(15 * 60L, Logic.soonS(a, Logic.MAYBE));
        assertEquals(10 * 60L, Logic.soonS(a, Logic.ON));
        a.on = false;  // no notification: a quarter of an hour, so the screen still warns
        assertEquals(15 * 60L, Logic.soonS(a, Logic.ON));
        a.leadOff = 0;  // a notification at the very moment: the same quarter of an hour ahead
        assertEquals(15 * 60L, Logic.soonS(a, Logic.OFF));
    }

    @Test
    public void barLabelsAlwaysShowTheNextChangeAndTheNextOutage() {
        // today: off 02-05:30, 09-13, 15:30-17, 18:30-22; now 10:30; 318 dp wide, labels 30 dp
        String day = repeat('1', 4) + repeat('0', 7) + repeat('1', 7) + repeat('0', 8) + repeat('1', 5)
                + repeat('0', 3) + repeat('1', 3) + repeat('0', 7) + repeat('1', 4);
        List<Logic.Label> l = Logic.barLabels(day, 21, 318, 30, 5);
        assertEquals(26, l.get(0).slot);  // 13:00, the next change
        assertEquals(Logic.ON, l.get(0).kind);
        assertEquals(31, l.get(1).slot);  // 15:30, the next outage: shown even 2.5 hours after 13:00
        assertEquals(Logic.OFF, l.get(1).kind);
        assertTrue(has(l, 34));           // 17:00, when it ends
        assertTrue(has(l, 4));            // 02:00, past: shown while there is room
        for (int i = 0; i < l.size(); i++) {  // never on top of each other, never past the edges
            assertTrue(l.get(i).x >= 15 && l.get(i).x <= 318 - 15);
            for (int j = i + 1; j < l.size(); j++) assertTrue(Math.abs(l.get(i).x - l.get(j).x) >= 35 - 0.01f);
        }
        // an outage to the end of the day: power back at 24:00; midnight itself is no change
        List<Logic.Label> t = Logic.barLabels(repeat('0', 4) + repeat('1', 36) + repeat('3', 8), -1, 318, 30, 5);
        assertFalse(has(t, 0));
        assertEquals(Logic.ON, kind(t, 4));
        assertEquals(Logic.MAYBE, kind(t, 40));
        assertEquals(Logic.ON, kind(t, 48));
        // no outages: nothing to say under the bar
        assertTrue(Logic.barLabels(repeat('1', 48), 10, 318, 30, 5).isEmpty());
    }

    @Test
    public void compactLinesPreserveStateAndKnownEnd() throws Exception {
        Logic.Now n = Logic.now(tl(TODAY_ONLY), OCT30, at(10, 0), false);
        assertEquals("Є до 14:30", Logic.tileLine(n));
        assertEquals("через 4 год 30 хв · світла не буде до 18:30", Logic.nextDetail(n));
        assertEquals(Arrays.asList("Вимкнуть", "14:30", "до 18:30"), Arrays.asList(Logic.widgetNext(n)));
        n = Logic.now(tl(TODAY_ONLY), OCT30, at(15, 0), false);
        assertEquals("Немає до 18:30", Logic.tileLine(n));
        assertEquals("через 3 год 30 хв", Logic.nextDetail(n));
        n = Logic.now(tl(feed("[]")), OCT30, at(10, 0), true);
        assertEquals("Світло є", Logic.tileLine(n));
        assertEquals(Arrays.asList("", "", ""), Arrays.asList(Logic.widgetNext(n)));
        Logic.Now unknown = new Logic.Now();
        unknown.title = "Графіка немає";
        assertEquals("Графіка немає", Logic.tileLine(unknown));
        assertEquals("", Logic.nextDetail(unknown));
        n.state = Logic.MAYBE;
        n.title = "Можливе вимкнення";
        assertEquals("Можливе вимкнення", Logic.tileLine(n));
    }

    @Test
    public void statisticsKeepOffPossibleUnknownAndZeroSeparate() {
        java.util.Map<LocalDate, String> h = new java.util.HashMap<>();
        h.put(OCT30.minusDays(2), repeat('1', 48));
        h.put(OCT30.minusDays(1), repeat('0', 48));
        h.put(OCT30, repeat('1', 33) + repeat('0', 12) + repeat('3', 3));
        List<Logic.DayStat> days = Logic.window(h, OCT30, 7);
        assertEquals(3, Logic.statKnownDays(days));
        assertEquals(810, Logic.statAverageMinutes(days, Logic.ON));
        assertEquals(600, Logic.statAverageMinutes(days, Logic.OFF));
        assertEquals(30, Logic.statAverageMinutes(days, Logic.MAYBE));
        assertEquals(56, Logic.statShare(days, Logic.ON));
        assertEquals(42, Logic.statShare(days, Logic.OFF));
        assertEquals("24", Logic.statNumber(days.get(4), Logic.ON));
        assertEquals("0", Logic.statNumber(days.get(4), Logic.OFF));
        assertEquals("—", Logic.statNumber(days.get(0), Logic.OFF));
        assertEquals("16,5", Logic.statNumber(days.get(6), Logic.ON));
        assertEquals(-1, Logic.statAverageMinutes(Logic.window(new java.util.HashMap<>(), OCT30, 7), Logic.ON));
        assertEquals(-1, Logic.statShare(Logic.window(new java.util.HashMap<>(), OCT30, 7), Logic.OFF));
    }

    @Test
    public void statisticsDescriptionKeepsUnknownDaysUnknown() {
        java.util.Map<LocalDate, String> h = new java.util.HashMap<>();
        for (int i = 0; i < 31; i++) h.put(OCT30.minusDays(i), repeat('1', 32) + repeat('0', 16));
        List<Logic.DayStat> days = Logic.window(h, OCT30, 31);
        assertTrue(Logic.describeStats(days).contains("без світла 8 год"));
        assertTrue(Logic.describeStats(Logic.window(new java.util.HashMap<>(), OCT30, 7)).contains("графіка немає"));
    }

    @Test public void compactEventsKeepTomorrowMidnightAndPossibleOutagesExplicit() {
        String tl=repeat('1',60)+repeat('3',6)+repeat('1',30);
        Logic.Now n=Logic.now(tl,OCT30,at(22,0),false);
        assertEquals("через 8 год 00 хв · можливе вимкнення до завтра 09:00",Logic.nextDetail(n));
        assertEquals(Arrays.asList("Можливо вимкнуть завтра","06:00","до завтра 09:00"),Arrays.asList(Logic.widgetNext(n)));
        n=Logic.now(repeat('0',48)+repeat('1',48),OCT30,at(23,30),false);
        assertEquals(Arrays.asList("Увімкнуть","24:00",""),Arrays.asList(Logic.widgetNext(n)));
        n.change=0;assertEquals(Arrays.asList("","",""),Arrays.asList(Logic.widgetNext(n)));
    }

    @Test
    public void incompleteStatisticsDoNotInventACompleteDay() {
        java.util.Map<LocalDate, String> history = new java.util.HashMap<>();
        history.put(OCT30, repeat('1', 12) + repeat('0', 10) + repeat('3', 3) + repeat('2', 23));
        history.put(OCT30.minusDays(1), repeat('2', 48));
        List<Logic.DayStat> days = Logic.window(history, OCT30, 7);
        // A part of a day supports neither a daily average nor a share: its hours would read as a whole day's.
        assertEquals(0, Logic.statKnownDays(days));
        assertEquals(-1, Logic.statAverageMinutes(days, Logic.ON));
        assertEquals(-1, Logic.statShare(days, Logic.ON));
        assertEquals("1,5", Logic.statNumber(days.get(6), Logic.MAYBE));
        assertEquals("5", Logic.statNumber(days.get(6), Logic.OFF));
        assertEquals("—", Logic.statNumber(days.get(5), Logic.ON));
        assertTrue(Logic.describeStats(days).contains("можливе вимкнення 1 год 30 хв"));
        history.put(OCT30.minusDays(2), repeat('1', 36) + repeat('0', 12));
        days = Logic.window(history, OCT30, 7);
        assertEquals(1, Logic.statKnownDays(days));
        assertEquals(1080, Logic.statAverageMinutes(days, Logic.ON));
        assertEquals(360, Logic.statAverageMinutes(days, Logic.OFF));
        assertEquals(75, Logic.statShare(days, Logic.ON));
    }

    @Test
    public void startWalksSavedDaysAndStopsAtUnknownHistory() {
        java.util.Map<LocalDate, String> history = new java.util.HashMap<>();
        history.put(OCT30.minusDays(1), repeat('1', 48));
        history.put(OCT30.minusDays(2), repeat('0', 36) + repeat('1', 12));
        Logic.Now n = Logic.nowWithHistory(repeat('1', 96), OCT30, at(12, 0), false, history);
        assertEquals("з 18:00 28.10", n.since);
        assertEquals("уже 1 день 18 год", n.already);
        history.put(OCT30.minusDays(2), repeat('2', 36) + repeat('1', 12));
        assertEquals("з 18:00 28.10", Logic.nowWithHistory(repeat('1', 96), OCT30, at(12, 0), false, history).since);
        history.remove(OCT30.minusDays(1));
        assertEquals("з 00:00", Logic.nowWithHistory(repeat('1', 96), OCT30, at(12, 0), false, history).since);
        history.put(OCT30.minusDays(1), repeat('0', 48));
        assertEquals("з 00:00", Logic.nowWithHistory(repeat('1', 96), OCT30, at(12, 0), false, history).since);
        assertEquals("", Logic.nowWithHistory(repeat('2', 96), OCT30, at(12, 0), false, history).since);
        assertEquals("з 10:00", Logic.nowWithHistory(repeat('2', 20) + repeat('1', 76), OCT30, at(12, 0), false, history).since);
    }

    @Test
    public void urgencyUsesTheExactReminderBoundaryAndEndsAtTheEvent() {
        Logic.Now n = new Logic.Now(); n.next = Logic.OFF; n.change = at(15, 30);
        Logic.Alerts alerts = new Logic.Alerts();
        assertFalse(Logic.highlight(n, alerts, at(15, 15) - 1));
        assertTrue(Logic.highlight(n, alerts, at(15, 15)));
        assertTrue(Logic.highlight(n, alerts, at(15, 30) - 1));
        assertFalse(Logic.highlight(n, alerts, at(15, 30)));
        assertFalse(Logic.highlight(n, alerts, at(16, 0)));
        alerts.leadOn = 10; n.next = Logic.ON;
        assertFalse(Logic.highlight(n, alerts, at(15, 19)));
        assertTrue(Logic.highlight(n, alerts, at(15, 20)));
        n.next = Logic.UNKNOWN;
        assertFalse(Logic.highlight(n, alerts, at(15, 25)));
    }

    @Test
    public void everyFullWidgetUsesTheTwoPartLayoutEvenWithALowLauncherHeight() {
        for (int[] size : new int[][] {{200,110},{250,120},{300,130},{300,160},{360,180},{420,220}}) {
            Logic.WidgetLayout w = Logic.widgetLayout(size[0], size[1], true);
            assertTrue(w.twoParts);
            assertTrue(w.columnWidth > 0);
            assertTrue(w.rowHeight > 0);
            assertTrue(w.rowTop + w.rowHeight <= w.barTop);
            assertTrue(w.barTop + w.barHeight + w.labelHeight <= size[1] - w.pad + .01f);
            assertTrue(w.headerSize >= 11 && w.headerSize <= 13);
            assertTrue(w.labelSize >= 10 && w.labelSize <= 13);
            assertEquals(size[1] - w.pad - w.tomorrowHeight, w.tomorrowTop, .01f);
            assertTrue(w.barTop + w.barHeight + w.labelHeight <= w.tomorrowTop + .01f);
        }
        assertFalse(Logic.widgetLayout(180,80,true).twoParts);
        assertFalse(Logic.widgetLayout(360,80,true).twoParts);
    }

    @Test
    public void aRoomyLargeWidgetGivesItsSpaceToALineAboutTomorrowAndALargerBar() {
        Logic.WidgetLayout low = Logic.widgetLayout(250, 120, true);
        assertEquals(0f, low.tomorrowHeight, 0f);
        Logic.WidgetLayout usual = Logic.widgetLayout(300, 176, true);
        assertTrue(usual.tomorrowHeight >= 16);
        assertTrue(usual.barHeight >= 16);
        assertTrue(usual.rowHeight >= 70);
        Logic.WidgetLayout tall = Logic.widgetLayout(360, 260, true);
        assertTrue(tall.barHeight > usual.barHeight);
        assertTrue(tall.rowHeight > usual.rowHeight);
    }

    @Test
    public void widgetTomorrowLineTellsTheDayInTheShortestHonestWords() {
        assertEquals(Arrays.asList("Завтра без вимкнень", "Завтра без вимкнень"),
                Arrays.asList(Logic.widgetTomorrow(repeat('1', 48))));
        assertEquals(Arrays.asList("Завтра: графіка ще немає", "Завтра: графіка ще немає"),
                Arrays.asList(Logic.widgetTomorrow(repeat('2', 48))));
        assertEquals(Arrays.asList("Завтра: графіка ще немає", "Завтра: графіка ще немає"),
                Arrays.asList(Logic.widgetTomorrow(null)));
        String two = repeat('1', 12) + repeat('0', 6) + repeat('1', 14) + repeat('0', 14) + repeat('1', 2);
        assertEquals(Arrays.asList("Завтра: 06:00–09:00, 16:00–23:00", "Завтра: 2 вимкнення · 10 год"),
                Arrays.asList(Logic.widgetTomorrow(two)));
        String possible = repeat('1', 20) + repeat('3', 10) + repeat('1', 18);
        assertEquals(Arrays.asList("Завтра можливо: 10:00–15:00", "Завтра можливо: 5 год"),
                Arrays.asList(Logic.widgetTomorrow(possible)));
        String partial = repeat('1', 24) + repeat('2', 24);
        assertEquals(Arrays.asList("Завтра: графік неповний", "Завтра: графік неповний"),
                Arrays.asList(Logic.widgetTomorrow(partial)));
    }

    @Test
    public void widgetTodaySaysHowMuchLightAsTheCardDoes() {
        assertEquals("24 год світла · 100 %", Logic.widgetToday(repeat('1', 48)));
        assertEquals("", Logic.widgetToday(null));
        assertEquals("14 год світла · 58 %",
                Logic.widgetToday(repeat('1', 12) + repeat('0', 6) + repeat('1', 14) + repeat('0', 14) + repeat('1', 2)));
    }

    @Test
    public void aNewWidgetIsNotMarkedAsAlreadyShowingAPlace() {
        Logic.Place home = new Logic.Place(); home.id = "p1";
        Logic.Place work = new Logic.Place(); work.id = "p2";
        List<Logic.Place> all = Arrays.asList(home, work);
        // the launcher has just added it: nothing saved, the other widget shows home
        assertEquals(Logic.WIDGET_HAS_OTHER, Logic.widgetMark(home, null, all, Arrays.asList("p1")));
        assertEquals(Logic.WIDGET_FREE, Logic.widgetMark(work, null, all, Arrays.asList("p1")));
        // reconfiguring an existing widget: its own place is marked
        assertEquals(Logic.WIDGET_THIS, Logic.widgetMark(work, "p2", all, Arrays.asList("p1")));
        assertEquals(Logic.WIDGET_HAS_OTHER, Logic.widgetMark(home, "p2", all, Arrays.asList("p1")));
        // saved empty: an old widget that always showed the first place
        assertEquals(Logic.WIDGET_THIS, Logic.widgetMark(home, "", all, Arrays.<String>asList()));
    }

    @Test
    public void aShownReminderStaysMatchedAfterItsLateWindowWhileTheScheduleKeepsIt() {
        Logic.Place p = new Logic.Place(); p.id = "p1"; p.name = "Дім";
        Logic.Alerts a = new Logic.Alerts(); a.off = true; a.leadOff = 0;
        String tl = repeat('1', 28) + repeat('0', 8) + repeat('1', 60);
        long change = at(14, 0);
        // two hours into the outage the event is no longer "upcoming", but the notice is still true
        boolean upcoming = false;
        for (Logic.Event e : Logic.events(p, tl, OCT30, at(16, 0), a)) upcoming |= e.change == change;
        assertFalse(upcoming);
        Logic.Event found = Logic.shownEvent(p, tl, OCT30, a, change, Logic.Event.AT_OFF, change);
        assertEquals(change, found.change);
        // the schedule moved the outage away: it is false now
        assertNull(Logic.shownEvent(p, repeat('1', 96), OCT30, a, change, Logic.Event.AT_OFF, change));
    }

    @Test
    public void aWidgetWithoutAScheduleNamesTheDayOfTheLastOne() {
        Logic.Data d = new Logic.Data();
        assertEquals("", Logic.lastScheduleDay(d));
        assertEquals("", Logic.lastScheduleDay(null));
        d.days.put(LocalDate.of(2026, 10, 3), new java.util.HashMap<>());
        d.days.put(LocalDate.of(2026, 10, 17), new java.util.HashMap<>());
        assertEquals("17.10", Logic.lastScheduleDay(d));
    }

    @Test
    public void aSmallWidgetFillsItsRightSegmentWithTheClockLeading() {
        // 190 x 100: name and event alike, the clock largest, the lines spread over the height
        float[] sized = Logic.widgetRowSizes(74, 40);
        assertEquals(37f, sized[2], .01f);
        assertEquals(sized[0], sized[1], 1f);
        assertTrue(sized[1] < sized[2]);
        assertTrue(Logic.widgetRowBlock(sized) <= 74.01f);
        assertTrue(Logic.widgetRowBlock(sized) >= 70);
        // a narrow widget: the width stops the clock, the words keep their size
        assertEquals(26f, Logic.widgetRowSizes(74, 26)[2], .01f);
        assertEquals(sized[0], Logic.widgetRowSizes(74, 26)[0], .01f);
        // a low widget shrinks everything alike rather than losing a line
        float[] low = Logic.widgetRowSizes(26, 40);
        assertTrue(Logic.widgetRowBlock(low) <= 26.01f);
        assertTrue(low[2] > low[1]);
    }

    @Test
    public void aSmallWidgetKeepsItsBulbSegmentNarrow() {
        assertEquals(59f, Logic.widgetBulbSegment(190), .5f);
        assertEquals(64f, Logic.widgetBulbSegment(360), .01f);  // a wide row does not grow it for nothing
        assertEquals(36f, Logic.widgetBulbSegment(110), .5f);
    }

    @Test
    public void aMissingTomorrowIsRecognisedSoItCanStayQuiet() {
        assertTrue(Logic.widgetTomorrowMissing(null));
        assertTrue(Logic.widgetTomorrowMissing(repeat('2', 48)));
        assertFalse(Logic.widgetTomorrowMissing(repeat('1', 48)));
        assertFalse(Logic.widgetTomorrowMissing(repeat('1', 24) + repeat('2', 24)));
    }

    @Test
    public void hoursOfAWindowStandInTheMiddleOfTheWholeWindow() {
        // the number is the length of all of it, wherever now is
        assertEquals(24f, Logic.barHoursCentre(0, 48), 0f);
        assertEquals(15f, Logic.barHoursCentre(10, 20), 0f);
    }

    @Test
    public void tomorrowGetsItsBarAsSoonAsItsScheduleExists() {
        Logic.WidgetLayout w = Logic.widgetLayout(300, 176, true);
        // the second bar takes the place of the line about tomorrow, the hours stay at the bottom
        assertEquals(w.barTop + w.barHeight + 2, w.tomorrowBarTop, .01f);
        assertEquals(w.tomorrowHeight - 2, w.tomorrowBarHeight, .01f);
        assertTrue(w.tomorrowBarHeight >= 12);
        assertEquals(176 - w.pad - w.labelHeight, w.tomorrowBarTop + w.tomorrowBarHeight, .01f);
    }

    @Test
    public void widgetPanesKeepTheKnownStartAndHonestFutureWhenNoChangeIsPublished() {
        Logic.Now n = Logic.now(repeat('1',96), OCT30, at(12,0), false);
        assertEquals(Arrays.asList("з 00:00", ""), Arrays.asList(Logic.widgetStart(n)));
        assertEquals(Arrays.asList("Сьогодні й завтра", "Без вимкнень", ""), Arrays.asList(Logic.widgetFuture(n)));
        n = Logic.now(repeat('1',48) + repeat('2',48), OCT30, at(12,0), false);
        assertEquals(Arrays.asList("Завтра", "Графіка ще немає", ""), Arrays.asList(Logic.widgetFuture(n)));
        n = Logic.now(repeat('2',96), OCT30, at(12,0), false);
        assertEquals(Arrays.asList("Далі", "Графіка немає", ""), Arrays.asList(Logic.widgetFuture(n)));
        n.since = "з 22:00 учора";
        assertEquals(Arrays.asList("з 22:00", "учора"), Arrays.asList(Logic.widgetStart(n)));
        n.since = "з 22:00 28.10";
        assertEquals(Arrays.asList("з 22:00", "28.10"), Arrays.asList(Logic.widgetStart(n)));
        n = Logic.now(repeat('0',96), OCT30, at(12,0), false);
        assertEquals(Arrays.asList("Далі", "Без змін", "до кінця графіка"), Arrays.asList(Logic.widgetFuture(n)));
    }

    @Test
    public void fourWeeksUseCompletedDaysAndPositiveDailyAverages() {
        java.util.Map<LocalDate,String> h = new java.util.HashMap<>();
        LocalDate today = LocalDate.of(2026, 11, 1);
        for (int i = 1; i <= 28; i++) h.put(today.minusDays(i), repeat('1', 36) + repeat('0', 12));
        h.put(today, repeat('0', 48)); // today is still changing; exclude it
        List<Logic.WeekStat> weeks = Logic.fourWeeks(h, today);
        assertEquals(4, weeks.size());
        assertEquals(LocalDate.of(2026,10,4), weeks.get(0).from);
        assertEquals(today.minusDays(1), weeks.get(3).to);
        for (Logic.WeekStat w : weeks) {
            assertEquals(7, w.known);
            assertEquals(1080, w.onMinutes);
            assertEquals(75, w.onPercent);
        }
        assertEquals(0, Logic.weekChange(weeks));
        h.put(today.minusDays(1), repeat('1',48));
        assertEquals(51, Logic.weekChange(Logic.fourWeeks(h,today)));
        h.put(today.minusDays(1), repeat('0',48));
        assertEquals(-154, Logic.weekChange(Logic.fourWeeks(h,today)));
    }

    @Test
    public void fourWeeksNeverTreatMissingOrPartialSchedulesAsZeroPower() {
        java.util.Map<LocalDate,String> h = new java.util.HashMap<>();
        LocalDate today = LocalDate.of(2027,1,2);
        for (int i = 1; i <= 28; i++) h.put(today.minusDays(i), repeat('1',48));
        h.remove(today.minusDays(2));
        h.put(today.minusDays(3), repeat('1',12) + repeat('2',36));
        List<Logic.WeekStat> weeks = Logic.fourWeeks(h,today);
        assertEquals(5, weeks.get(3).known);
        assertEquals(1440, weeks.get(3).onMinutes);
        assertEquals(100, weeks.get(3).onPercent);
        assertEquals(Integer.MIN_VALUE, Logic.weekChange(weeks));
        assertEquals(-1, Logic.fourWeeks(new java.util.HashMap<>(),today).get(0).onMinutes);
        h.put(today.minusDays(2), repeat('0',48));
        h.put(today.minusDays(3), repeat('1',46) + repeat('3',2));
        assertEquals(7, Logic.fourWeeks(h,today).get(3).known);
        assertEquals(Integer.MIN_VALUE, Logic.weekChange(Logic.fourWeeks(h,today)));
    }

    @Test
    public void daySummariesKeepHoursAndShareEvenWhenCompact() {
        assertEquals("18,5 год · 77 %", Logic.daySummaries(repeat('1',37)+repeat('0',11)).get(2));
        assertEquals("0 год · 0 %", Logic.daySummaries(repeat('0',48)).get(2));
        assertEquals("24 год · 100 %", Logic.daySummaries(repeat('1',48)).get(2));
    }

    @Test
    public void wholeDayOfUnknownStatusesIsNotPublishedAndNotZeroPower() {
        Logic.Data d = new Logic.Data();
        java.util.Map<String, String> day = new java.util.HashMap<>();
        day.put("GPV1.1", repeat('2', 48));
        day.put("GPV2.1", repeat('2', 47) + '1');
        d.days.put(OCT30, day);
        assertNull(Logic.day(d, "GPV1.1", OCT30));
        assertEquals(repeat('2', 47) + '1', Logic.day(d, "GPV2.1", OCT30));
    }

    @Test
    public void zeroPowerReadsAsHoursLikeEveryOtherDay() {
        assertEquals("0 год світла · 0 %", Logic.daySummaries(repeat('0', 48)).get(0));
        assertEquals("0 год", Logic.hoursMinutes(0));
        assertEquals("45 хв", Logic.hoursMinutes(45));
    }

    @Test
    public void barGapNarrowsOnlyWhenAHalfHourNumberWouldOtherwiseVanish() {
        assertEquals(10f, Logic.statGap(48, 10, 29, 3), 0.01f);   // roomy: as designed
        assertEquals(6f, Logic.statGap(35, 10, 29, 3), 0.01f);    // tight: the bar keeps the number
        assertEquals(3f, Logic.statGap(29, 10, 29, 3), 0.01f);    // never bars touching
    }

    @Test
    public void legendNamesMissingDataForAWeekWithoutAnyFullDay() {
        java.util.Map<LocalDate, String> h = new java.util.HashMap<>();
        for (int i = 1; i <= 28; i++) h.put(OCT30.minusDays(i), repeat('1', 48));
        assertFalse(Logic.weeksMissData(Logic.fourWeeks(h, OCT30)));
        for (int i = 22; i <= 28; i++) h.remove(OCT30.minusDays(i));
        assertTrue(Logic.weeksMissData(Logic.fourWeeks(h, OCT30)));
    }

    @Test
    public void anotherQueueIsAnotherHistory() {
        Logic.Place a = new Logic.Place();
        a.region = "kyiv_region"; a.group = "GPV1.1"; a.name = "Дім";
        Logic.Place b = new Logic.Place();
        b.region = "kyiv_region"; b.group = "GPV1.1"; b.name = "Дача";
        assertTrue(Logic.sameQueue(a, b));     // a new name keeps the history
        b.group = "GPV3.2";
        assertFalse(Logic.sameQueue(a, b));
        b.group = "GPV1.1"; b.region = "lvivska";
        assertFalse(Logic.sameQueue(a, b));
    }

    @Test
    public void largeWidgetIsLargeBeforeTheLauncherReportsItsCell() {
        assertEquals(Logic.W_FULL, Logic.widgetForm(Logic.widgetDefaultSize(true)[0], Logic.widgetDefaultSize(true)[1]));
        assertEquals(Logic.W_ROW, Logic.widgetForm(Logic.widgetDefaultSize(false)[0], Logic.widgetDefaultSize(false)[1]));
    }

    @Test
    public void spokenStateHasNoEmptyPauses() throws Exception {
        Logic.Now n = new Logic.Now();
        n.title = "Графіка немає"; n.hint = "Щойно обленерго його оприлюднить, він з'явиться тут";
        assertEquals("Зараз за графіком: Графіка немає. Щойно обленерго його оприлюднить, він з'явиться тут.", Logic.describeNow(n, ""));
        n = Logic.now(tl(TODAY_ONLY), OCT30, at(3, 0), false);
        String spoken = Logic.describeNow(n, "через 2 год");
        assertTrue(spoken, spoken.startsWith("Зараз за графіком: " + n.title + " " + n.since + ", " + n.already + ". "));
        assertTrue(spoken, spoken.endsWith(". через 2 год."));
    }

    @Test
    public void onlyARealEventWithATimeIsShownBig() throws Exception {
        // a change with a clock: the big line of the card
        assertTrue(Logic.hintIsEvent(Logic.now(tl(TODAY_ONLY), OCT30, at(3, 0), false)));
        // statements about the schedule: a quiet line
        assertFalse(Logic.hintIsEvent(Logic.now(repeat('1', 48) + repeat('2', 48), OCT30, at(12, 0), false)));
        assertFalse(Logic.hintIsEvent(Logic.now(repeat('1', 96), OCT30, at(12, 0), false)));
        assertFalse(Logic.hintIsEvent(Logic.now(repeat('0', 96), OCT30, at(12, 0), false)));
        assertFalse(Logic.hintIsEvent(Logic.now(repeat('2', 96), OCT30, at(12, 0), false)));
        assertFalse(Logic.hintIsEvent(Logic.now(repeat('1', 96), OCT30, at(12, 0), true)));
    }

    @Test
    public void timePhrasesAreWhatPeopleSay() throws Exception {
        String tl = repeat('0', 20) + repeat('1', 76);
        assertEquals("менш ніж за хвилину", Logic.nextDetail(Logic.now(tl, OCT30, at(10, 0) - 30, false)));
        assertEquals("через 1 хв", Logic.nextDetail(Logic.now(tl, OCT30, at(10, 0) - 60, false)));
        assertEquals("щойно", Logic.now(tl, OCT30, at(10, 0) + 30, false).already);
        assertEquals("уже 1 хв", Logic.now(tl, OCT30, at(10, 1), false).already);
        assertEquals("1 день 3 год", Logic.waitText(86400 + 3 * 3600));
        assertEquals("2 дні 1 год", Logic.waitText(2 * 86400 + 3600));
        assertEquals("5 днів 1 год", Logic.waitText(5 * 86400 + 3600));
        assertEquals("11 днів 1 год", Logic.waitText(11 * 86400 + 3600));
        assertEquals("21 день 1 год", Logic.waitText(21 * 86400 + 3600));
        assertEquals("2 дні", Logic.waitText(2 * 86400 + 60));
    }

    @Test
    public void sharedPictureNamesTheSite() {
        assertEquals("Світлячок · abovsh.github.io/svitlyachok", Logic.SHARE_SIGNATURE);
    }

    private static boolean has(List<Logic.Label> l, int slot) {
        for (Logic.Label x : l) if (x.slot == slot) return true;
        return false;
    }

    private static int kind(List<Logic.Label> l, int slot) {
        for (Logic.Label x : l) if (x.slot == slot) return x.kind;
        return -99;
    }

    @Test public void missingHoursStayUnknown() throws Exception {
        String raw = feed("{\"" + D30 + "\":{\"GPV1.1\":{\"1\":\"no\"}}}");
        assertEquals("00" + repeat('2', 46), Logic.day(data(raw), "GPV1.1", OCT30));
    }
    @Test public void emptyHoursNeverMeanPower() throws Exception {
        assertNull(Logic.day(data(feed("{\"" + D30 + "\":{\"GPV1.1\":{}}}")), "GPV1.1", OCT30));
    }
    @Test public void emptyFeedCannotCreateFutureHistory() throws Exception {
        assertNull(Logic.day(data(feed("[]")), "GPV1.1", OCT30.plusDays(30)));
    }
    @Test public void internalGapHasAnExactBoundary() {
        Logic.Now n = Logic.now(repeat('1',20)+repeat('2',2)+repeat('1',26)+repeat('2',48), OCT30,at(9,0),false);
        assertEquals("до 10:00", n.big);
        assertEquals(Logic.UNKNOWN,n.next);
        assertEquals(Arrays.asList("Після 10:00","Графіка немає",""),Arrays.asList(Logic.widgetFuture(n)));
    }
    @Test public void overdueAdvanceReminderSurvivesUntilChange() throws Exception {
        Logic.Alerts a = new Logic.Alerts();a.leadOff=60;
        assertEquals(at(14,30),Logic.events(home(),tl(TODAY_ONLY),OCT30,at(14,20),a).get(0).change);
    }
    @Test public void midnightEventSurvivesTheBoundary() {
        Logic.Alerts a=new Logic.Alerts();a.leadOff=0;a.on=false;
        List<Logic.Event> events=Logic.events(home(),repeat('1',48)+repeat('0',48),OCT30,D31+1,a);
        assertEquals(1,events.size());assertEquals(D31,events.get(0).change);
    }
    @Test public void possibleOnlyPublicationKeepsUncertainty() throws Exception {
        String raw=feed("{\""+D31+"\":{\"GPV1.1\":{\"1\":\"maybe\"}}}");
        assertTrue(Logic.notes(home(),data(TODAY_ONLY),data(raw),at(19,0)).get(0).text.contains("Можлив"));
    }
    @Test public void advanceReminderExpiresAtItsChange() throws Exception {
        Logic.Event e=Logic.events(home(),tl(TODAY_ONLY),OCT30,at(12,0),new Logic.Alerts()).get(0);
        assertEquals(e.change,Logic.eventText(e,tl(TODAY_ONLY),OCT30).until);
    }
    @Test public void supportedRegionsIncludeCherkasyAndKharkiv() {
        assertEquals(17,Logic.regions().size());assertTrue(Logic.region("cherkasy")!=null);assertTrue(Logic.region("kharkiv")!=null);
        assertEquals("Місто Київ",Logic.regions().get(0).name);assertEquals("kyiv",Logic.regions().get(0).id);
    }

    @Test public void aDayWhoseOutagesStayTheSameIsNotAChange() {
        String day = repeat('1', 28) + repeat('0', 8) + repeat('1', 12);
        // a slot that only became known or unknown moves no outage
        assertFalse(Logic.outagesChanged(day, repeat('1', 28) + repeat('0', 8) + repeat('2', 12), 0));
        assertFalse(Logic.outagesChanged(day, day, 0));
        // a real move, a possible outage, an outage ahead that appeared
        assertTrue(Logic.outagesChanged(day, repeat('1', 30) + repeat('0', 8) + repeat('1', 10), 0));
        assertTrue(Logic.outagesChanged(day, repeat('1', 28) + repeat('3', 8) + repeat('1', 12), 0));
        // a change in what has already passed says nothing
        assertFalse(Logic.outagesChanged(repeat('0', 4) + repeat('1', 44), repeat('1', 48), 10));
    }

    @Test public void theSameNoticeForTheSameDayIsNotRepeated() {
        String memory = Logic.rememberNote("", "2026-10-30", "Дім · 1 вимкнення: 14:00 – 18:00");
        assertTrue(Logic.noteSeen(memory, "2026-10-30", "Дім · 1 вимкнення: 14:00 – 18:00"));
        assertFalse(Logic.noteSeen(memory, "2026-10-31", "Дім · 1 вимкнення: 14:00 – 18:00"));
        assertFalse(Logic.noteSeen(memory, "2026-10-30", "Дім · 2 вимкнення"));
        // only the newest notice of a day and the two latest days are kept
        memory = Logic.rememberNote(memory, "2026-10-30", "інше");
        assertFalse(Logic.noteSeen(memory, "2026-10-30", "Дім · 1 вимкнення: 14:00 – 18:00"));
        memory = Logic.rememberNote(Logic.rememberNote(memory, "2026-10-31", "а"), "2026-11-01", "б");
        assertFalse(Logic.noteSeen(memory, "2026-10-30", "інше"));
        assertTrue(Logic.noteSeen(memory, "2026-10-31", "а"));
    }

    @Test public void queuesGoInTheOrderPeopleCount() throws Exception {
        String hours=HOURS_11;
        Logic.Data d=Logic.parse("{\"fact\":{\"data\":{\""+D30+"\":{\"GPV10.1\":"+hours+",\"GPV2.1\":"+hours+",\"GPV1.2\":"+hours+",\"GPV1.1\":"+hours+"}}}}");
        assertEquals(Arrays.asList("GPV1.1","GPV1.2","GPV2.1","GPV10.1"),d.groups);
    }

    @Test public void liveNewRegionFixturesParse() throws Exception {
        for (String region : new String[] {"cherkasy","kharkiv","kyiv"}) {
            try (java.io.InputStream input=getClass().getResourceAsStream("/"+region+".json")) {
                assertTrue(input!=null);
                Logic.Data d=Logic.parse(new String(input.readAllBytes(),java.nio.charset.StandardCharsets.UTF_8));
                assertTrue(!d.days.isEmpty());assertTrue(!d.groups.isEmpty());
                assertEquals(48,Logic.day(d,d.groups.get(0),Logic.latestDay(d)).length());
            }
        }
    }
    @Test public void emptyFeedExpiresAtFortyEightHours() throws Exception {
        Logic.Data d=data(feed("[]"));
        assertTrue(Logic.noneFresh(d,D30+48*3600));assertFalse(Logic.noneFresh(d,D30+48*3600+1));
        assertTrue(Logic.validAt(d,D30+100).none);assertFalse(Logic.validAt(d,D30+49*3600).none);
        assertNull(Logic.day(Logic.validAt(d,D30+49*3600),"GPV1.1",OCT30.plusDays(2)));
    }
    @Test public void primaryStaleDataNeverHidesCurrentFallback() throws Exception {
        Logic.Data current=data(TODAY_ONLY),old=data(TODAY_ONLY.replace(String.valueOf(D30),String.valueOf(D30-86400*300L)));
        assertTrue(Logic.currentData(current,at(12,0)));assertFalse(Logic.currentData(old,at(12,0)));
        assertTrue(Logic.preferData(current,old,at(12,0)));assertFalse(Logic.preferData(old,current,at(12,0)));
    }
    @Test public void sourceTimesAcceptIsoSecondsAndMilliseconds() {
        assertEquals(D30,Logic.sourceTime("2026-10-29T22:00:00Z"));
        assertEquals(D30,Logic.sourceTime(String.valueOf(D30)));assertEquals(D30,Logic.sourceTime(String.valueOf(D30*1000)));
        assertEquals(0,Logic.sourceTime("bad"));
    }
    @Test public void oldTimezoneDatabaseUsesKyivAlias() {
        assertEquals(java.time.ZoneId.of("Europe/Kiev"),Logic.kyivZone(id -> {
            if (id.equals("Europe/Kyiv")) throw new java.time.DateTimeException("old database");
            return java.time.ZoneId.of(id);
        }));
    }
    @Test public void springMissingWallTimeDoesNotCreateAnAlarm() {
        LocalDate spring=LocalDate.of(2027,3,28);
        assertEquals(-1,Logic.slotStart(spring,6));assertEquals(-1,Logic.slotStart(spring,7));
        assertTrue(Logic.slotStart(spring,8)>Logic.slotStart(spring,5));
    }

    @Test public void springJumpUsesFirstRealStateAfterTheGap() {
        LocalDate day=LocalDate.of(2027,3,28);
        long now=day.atTime(2,45).atZone(Logic.KYIV).toEpochSecond();
        String tl=repeat('1',6)+repeat('0',42)+repeat('1',48);
        Logic.Alerts a=new Logic.Alerts();a.leadOff=0;a.on=false;
        assertEquals(day.atTime(4,0).atZone(Logic.KYIV).toEpochSecond(),Logic.events(home(),tl,day,now,a).get(0).change);
        assertEquals("до 04:00",Logic.now(tl,day,now,false).big);
    }

    @Test
    public void tileLineKeepsOneShortForm() {
        Logic.Now n = new Logic.Now();
        n.state = Logic.OFF;
        n.title = "Світла немає";
        n.big = "до кінця дня";
        assertEquals("Немає до 24:00", Logic.tileLine(n));
        n.big = "до завтра 08:00";
        assertEquals("Немає до 08:00", Logic.tileLine(n));
        n.state = Logic.ON;
        n.big = "до завтра 06:30";
        assertEquals("Є до 06:30", Logic.tileLine(n));
    }

    @Test
    public void numberOfTheRunUnderNowIsActive() {
        assertTrue(Logic.barHoursActive(10, 20, 10f));
        assertTrue(Logic.barHoursActive(10, 20, 19.5f));
        assertFalse(Logic.barHoursActive(10, 20, 20f));
        assertFalse(Logic.barHoursActive(10, 20, 9.5f));
        assertFalse(Logic.barHoursActive(10, 20, -1f));
    }

    @Test
    public void alertsLabelSaysWhenThePlaceHasNone() {
        Logic.Alerts a = new Logic.Alerts();
        assertEquals("Сповіщення", Logic.alertsLabel(a));
        a.off = false;
        a.on = false;
        assertEquals("Без сповіщень", Logic.alertsLabel(a));
        a.tomorrow = true;
        assertEquals("Сповіщення", Logic.alertsLabel(a));
    }

    @Test
    public void widgetGlowsFromEachEdge() {
        int red = 0xFF4B2B37;
        int green = 0xFF1A4334;
        int card = 0xFF172428;
        assertEquals(red, Logic.widgetGlow(red, green, card, .33f, 0f));
        assertEquals(green, Logic.widgetGlow(red, green, card, .33f, 1f));
        assertEquals(red, Logic.widgetGlow(red, red, card, .33f, .33f));  // one state: one colour
        int middle = Logic.widgetGlow(red, green, card, .33f, .33f);
        int gap = Math.abs(((middle >> 16) & 0xFF) - 0x17);
        assertTrue("the middle stays near the card: " + gap, gap < 8);
    }

    @Test
    public void rightOfTheWidgetShowsWhatComesNext() {
        Logic.Now n = new Logic.Now();
        n.state = Logic.ON;
        n.next = Logic.OFF;
        assertEquals(Logic.OFF, Logic.widgetRightState(n, "16:00"));
        n.next = Logic.UNKNOWN;
        assertEquals(Logic.ON, Logic.widgetRightState(n, "Без вимкнень"));
        assertEquals(Logic.ON, Logic.widgetRightState(n, "Без змін"));
        assertEquals(Logic.UNKNOWN, Logic.widgetRightState(n, "Графіка ще немає"));
    }

    @Test
    public void wordsInsteadOfAClockStandAtEqualDistances() {
        // caps .74: name 20, event 10, value 20 in a block of 67: 30 free, half of it above the event
        assertEquals(29.8f, Logic.widgetEventTop(67f, 20f, 10f, 20f), .01f);
    }

    @Test
    public void kyivQueueIsFoundOnASiteThatShowsIt() {
        Logic.Region kyiv = null;
        Logic.Region other = null;
        for (Logic.Region r : Logic.regions()) {
            if (r.id.equals("kyiv")) kyiv = r;
            if (r.id.equals("kyiv_region")) other = r;
        }
        assertEquals("https://bezsvitla.com.ua/kyiv#street-search-widget", kyiv.dso);
        assertEquals("Не знаєте свою чергу? Знайдіть її за адресою на сайті «Без світла» ›", Logic.queueHelp(kyiv));
        assertEquals("Не знаєте свою чергу? Перевірте за адресою на сайті обленерго ›", Logic.queueHelp(other));
    }

    private static Logic.Place place(String id, String name, String region, String group) {
        Logic.Place p = new Logic.Place();
        p.id = id;
        p.name = name;
        p.region = region;
        p.group = group;
        return p;
    }

    @Test
    public void aQueueOfARegionCanBeAddedOnlyOnce() {
        Logic.Place home = place("a", "Дім", "kyiv_region", "GPV3.1");
        List<Logic.Place> places = Arrays.asList(home, place("b", "Дача", "kyiv", "GPV3.1"));
        assertEquals(home, Logic.duplicateOf(places, place("n", "Дім", "kyiv_region", "GPV3.1"), null));
        assertEquals(home, Logic.duplicateOf(places, place("n", "Батьки", "kyiv_region", "GPV3.1"), null));
        assertNull(Logic.duplicateOf(places, place("n", "Батьки", "kyiv_region", "GPV3.2"), null));
        assertNull(Logic.duplicateOf(places, place("n", "Батьки", "odesa", "GPV3.1"), null));
    }

    @Test
    public void editingAPlaceIsNotADuplicateOfItself() {
        Logic.Place home = place("a", "Дім", "kyiv_region", "GPV3.1");
        Logic.Place other = place("b", "Дача", "kyiv_region", "GPV1.1");
        List<Logic.Place> places = Arrays.asList(home, other);
        assertNull(Logic.duplicateOf(places, place("a", "Хата", "kyiv_region", "GPV3.1"), home));
        assertEquals(home, Logic.duplicateOf(places, place("b", "Дача", "kyiv_region", "GPV3.1"), other));
    }

    @Test
    public void aDuplicateSavedByAnOlderVersionCanStillBeRenamed() {
        Logic.Place home = place("a", "Дім", "kyiv_region", "GPV3.1");
        Logic.Place twin = place("b", "Дім", "kyiv_region", "GPV3.1");
        assertNull(Logic.duplicateOf(Arrays.asList(home, twin), place("b", "Батьки", "kyiv_region", "GPV3.1"), twin));
    }

    @Test
    public void theDuplicateMessageNamesThePlaceAlreadyAdded() {
        assertEquals("Черга 3.1, Київська область, уже додана як «Дім».",
                Logic.duplicateMessage(place("a", "Дім", "kyiv_region", "GPV3.1")));
    }

    @Test
    public void statLineGivesShareAndHoursOnEqualTerms() {
        assertEquals("69% · 16,5 год", Logic.statLine(69, 16 * 60 + 40));
        assertEquals("31% · 7,5 год", Logic.statLine(31, 7 * 60 + 20));
        assertEquals("100% · 24 год", Logic.statLine(100, 24 * 60));
        assertEquals("0% · 0 год", Logic.statLine(0, 0));
    }

    @Test
    public void statLineRoundsToTheHalfHourTheScheduleIsMadeOf() {
        assertEquals("50% · 12 год", Logic.statLine(50, 12 * 60 + 14));
        assertEquals("50% · 12,5 год", Logic.statLine(50, 12 * 60 + 15));
        assertEquals("50% · 12,5 год", Logic.statLine(50, 12 * 60 + 44));
        assertEquals("50% · 13 год", Logic.statLine(50, 12 * 60 + 45));
    }

    @Test
    public void statLineSaysSoWithoutData() {
        assertEquals("—", Logic.statLine(-1, -1));
        assertEquals("—", Logic.statLine(50, -1));
        assertEquals("—", Logic.statLine(-1, 60));
    }

    @Test
    public void hoursOnTheBarAreWhiteExceptWhereWhiteCannotRead() {
        // the light theme: white on every power and outage section, past or not
        assertEquals(Logic.INK_WHITE, Logic.barHoursInk('1', true, false, false));
        assertEquals(Logic.INK_WHITE, Logic.barHoursInk('0', true, true, false));
        assertEquals(Logic.INK_WHITE, Logic.barHoursInk('0', false, false, false));
        // the dark theme: a window that is over stays quiet, the one going on is white
        assertEquals(Logic.INK_QUIET, Logic.barHoursInk('1', true, false, true));
        assertEquals(Logic.INK_WHITE, Logic.barHoursInk('1', true, true, true));
        assertEquals(Logic.INK_WHITE, Logic.barHoursInk('0', false, false, true));
        // a possible outage is pale amber: white would not read on it
        assertEquals(Logic.INK_DARK, Logic.barHoursInk('3', false, false, true));
        assertEquals(Logic.INK_DARK, Logic.barHoursInk('3', true, false, false));
        assertEquals(Logic.INK_QUIET, Logic.barHoursInk('3', true, false, true));
    }

    @Test
    public void theScreenChecksAtTheStartOfEveryMinute() {
        assertEquals(60_000L, Logic.toNextMinute(1_793_311_200_000L));
        assertEquals(59_999L, Logic.toNextMinute(1_793_311_200_001L));
        assertEquals(1L, Logic.toNextMinute(1_793_311_259_999L));
        assertEquals(30_000L, Logic.toNextMinute(1_793_311_230_000L));
    }

    // ---- what a check costs -------------------------------------------------------

    static final String FULL_FILE = "{\n  \"regionId\": \"kyiv-region\",\n  \"lastUpdated\": \"2026-11-03T08:51:26.602Z\",\n"
            + "  \"fact\": {\"data\": {\"" + D30 + "\": {\"GPV1.1\": " + HOURS_11 + "}}, \"update\": \"29.10.2026 20:25\", \"today\": " + D30 + "},\n"
            + "  \"preset\": {\"days\": {\"1\": \"Понеділок\"}, \"sch_names\": {\"GPV1.1\": \"Черга 1.1\", \"GPV9.9\": \"Черга 9.9\"}, \"data\": {\"GPV1.1\": {\"1\": " + HOURS_11 + "}}},\n"
            + "  \"lastUpdateStatus\": {\"status\": \"parsed\", \"ok\": true, \"code\": 200, \"message\": null, \"at\": \"2026-11-03T08:51:26.602Z\", \"attempt\": 50934},\n"
            + "  \"meta\": {\"schemaVersion\": \"1.0.0\", \"contentHash\": \"1489d8de74b14ccd4e2fe88c34c4287b3069d136612e2a8fe96ab46c3f55c7f5\"},\n"
            + "  \"regionAffiliation\": \"Київська обл.\"\n}";

    @Test
    public void onlyWhatTheAppReadsIsKept() throws Exception {
        String kept = Logic.compactFile(FULL_FILE);
        assertFalse(kept.contains("Понеділок"));
        assertFalse(kept.contains("regionAffiliation"));
        assertTrue(kept.length() < FULL_FILE.length() * 0.8);
        Logic.Data a = Logic.parse(FULL_FILE);
        Logic.Data b = Logic.parse(kept);
        assertEquals(a.days, b.days);
        assertEquals(a.groups, b.groups);
        assertEquals(a.updated, b.updated);
        assertEquals(a.sourceTime, b.sourceTime);
        assertEquals(a.sourceOk, b.sourceOk);
        assertEquals("1489d8de74b14ccd4e2fe88c34c4287b3069d136612e2a8fe96ab46c3f55c7f5", b.hash);
    }

    @Test
    public void aFileWithNoOutagesKeepsItsQueues() throws Exception {
        String raw = "{\"lastUpdated\": \"2026-11-03T08:51:26Z\", \"fact\": {\"data\": []}, \"preset\": {\"data\": {}, \"sch_names\": {\"GPV1.1\": \"Черга 1.1\"}}, \"lastUpdateStatus\": {\"ok\": false}}";
        Logic.Data d = Logic.parse(Logic.compactFile(raw));
        assertTrue(d.none);
        assertEquals(Arrays.asList("GPV1.1"), d.groups);
        assertFalse(d.sourceOk);
        assertEquals("", d.hash);
    }

    @Test
    public void aFileWithoutTheOptionalPartsIsStillKept() throws Exception {
        Logic.Data d = Logic.parse(Logic.compactFile("{\"fact\": {\"data\": {\"" + D30 + "\": {\"GPV1.1\": " + HOURS_11 + "}}}}"));
        assertEquals(DAY_11, Logic.day(d, "GPV1.1", OCT30));
    }

    @Test(expected = Logic.BadFile.class)
    public void garbageIsNotKept() throws Exception {
        Logic.compactFile("{\"no\": 1}");
    }

    static final String TAIL_B42 = """
            24": "maybe"
                    }
                  }
                },
                "updateFact": "03.11.2026 02:39"
              },
              "lastUpdateStatus": {
                "status": "parsed",
                "ok": true,
                "code": 200,
                "message": null,
                "at": "2026-11-03T08:51:26.602Z",
                "attempt": 50934
              },
              "meta": {
                "schemaVersion": "1.0.0",
                "contentHash": "1489d8de74b14ccd4e2fe88c34c4287b3069d136612e2a8fe96ab46c3f55c7f5"
              },
              "regionAffiliation": "Київська обл."
            }""";
    static final String TAIL_VINNYTSIA = ", \"message\": null, \"at\": 1793696303, \"attempt\": 1 }, \"regionAffiliation\": \"Вінницька область\", \"meta\": { \"schemaVersion\": \"1.0.0\", "
            + "\"contentHash\": \"711bec96f6fcc1bd1127a6ed08a5fe5d8c9eacccbd3432a1118448da92b8541e\" } }";

    @Test
    public void theEndOfAFileSaysWhetherTheScheduleChanged() {
        Logic.Tail t = Logic.tail(TAIL_B42);
        assertEquals("1489d8de74b14ccd4e2fe88c34c4287b3069d136612e2a8fe96ab46c3f55c7f5", t.hash);
        assertEquals(1793695886L, t.at);
    }

    @Test
    public void aTailThatDoesNotProveItAsksForTheWholeFile() {
        assertNull(Logic.tail(TAIL_B42.replace("\"ok\": true", "\"ok\": false")));  // the source failed: read what it says
        assertNull(Logic.tail(TAIL_B42.replace("contentHash", "other")));
        assertNull(Logic.tail(TAIL_B42.replace("\"at\": \"2026-11-03T08:51:26.602Z\"", "\"at\": null")));
        assertNull(Logic.tail(TAIL_VINNYTSIA));  // cut before its status: "ok" did not fit
        assertNull(Logic.tail("garbage"));
    }

    @Test
    public void aNewerAnswerFromTheSourceKeepsANoneFileFresh() throws Exception {
        Logic.Data d = Logic.parse(Logic.compactFile("{\"lastUpdated\": \"2026-10-30T00:00:00Z\", \"fact\": {\"data\": []}, \"meta\": {\"contentHash\": \"abcdef0123456789\"}}"));
        long later = d.sourceTime + 72 * 3600L;
        assertFalse(Logic.noneFresh(d, later));
        Logic.touch(d, later - 60);
        assertTrue(Logic.noneFresh(d, later));
        assertEquals("abcdef0123456789", Logic.validAt(d, later + 72 * 3600L).hash);
        Logic.touch(d, 5);  // an older answer changes nothing
        assertEquals(later - 60, d.sourceTime);
    }

    @Test
    public void theEndOfAFileIsEnoughOnlyWithAHashAndNotForLong() {
        long now = 1_793_700_000L;
        assertFalse(Logic.needsWholeFile("abc", now - 3600, now));
        assertTrue(Logic.needsWholeFile("", now - 3600, now));  // this source has no hash
        assertTrue(Logic.needsWholeFile("abc", now - 6 * 3600, now));
        assertTrue(Logic.needsWholeFile("abc", now + 60, now));  // the clock was moved back
    }

    @Test
    public void onlyTheRegionsInUseStayCached() {
        java.util.Set<String> keys = new java.util.HashSet<>(Arrays.asList("json_kyiv", "checked_kyiv", "etag_kyiv", "url_kyiv", "full_kyiv", "source_kyiv",
                "json_kyiv_region", "checked_kyiv_region", "other"));
        java.util.Set<String> gone = new java.util.HashSet<>(Logic.cacheKeysOutside(keys, java.util.Collections.singleton("kyiv_region")));
        assertEquals(new java.util.HashSet<>(Arrays.asList("json_kyiv", "checked_kyiv", "etag_kyiv", "url_kyiv", "full_kyiv", "source_kyiv")), gone);
    }

    // ---- places ---------------------------------------------------------------------

    @Test
    public void whatWasShownForAPlaceIsForgottenWithItsOldQueue() {
        java.util.Set<String> fired = new java.util.HashSet<>(Arrays.asList("p1|0|100", "p11|0|100"));
        assertEquals(java.util.Collections.singleton("p11|0|100"), Logic.firedWithout(fired, "p1"));
    }

    @Test
    public void aQueueStaysChosenOnlyIfTheRegionHasIt() {
        List<String> groups = Arrays.asList("GPV1.1", "GPV1.2");
        assertTrue(Logic.queueOffered("GPV1.1", groups, ""));
        assertFalse(Logic.queueOffered("GPV5.2", groups, ""));
        assertTrue(Logic.queueOffered("GPV5.2", groups, "GPV5.2"));  // the place's own, while it is edited
        assertTrue(Logic.queueOffered("", groups, ""));
    }

    @Test
    public void aQueueTheScheduleDoesNotHaveIsNamed() throws Exception {
        Logic.Data d = Logic.parse(feed("{\"" + D30 + "\":{\"GPV1.1\":" + HOURS_11 + "}}"));
        assertEquals("Черги 5.2 немає в останньому графіку області. Перевірте чергу в налаштуваннях.", Logic.missingSchedule(d, "GPV5.2"));
        assertEquals("Нового графіка немає · останній був 30.10.", Logic.missingSchedule(d, "GPV1.1"));
    }

    private static Logic.Place place(String name, String group) {
        Logic.Place p = new Logic.Place();
        p.name = name;
        p.group = group;
        return p;
    }

    @Test
    public void aPlaceWithoutItsOwnNameIsCalledByItsQueue() {
        assertTrue(Logic.autoNamed(place("Черга 3.2", "GPV3.2")));
        assertFalse(Logic.autoNamed(place("Дім", "GPV3.2")));
        assertFalse(Logic.autoNamed(place("Черга 3.2", "GPV4.1")));
        assertEquals("Черга 3.2", Logic.placeName("", null, "GPV3.2"));
        assertEquals("Дім", Logic.placeName("  Дім ", null, "GPV3.2"));
        assertEquals("Черга 4.1", Logic.placeName("Черга 3.2", place("Черга 3.2", "GPV3.2"), "GPV4.1"));  // follows the queue
        assertEquals("Дім", Logic.placeName("Дім", place("Дім", "GPV3.2"), "GPV4.1"));
        assertEquals("Дача", Logic.placeName("Дача", place("Черга 3.2", "GPV3.2"), "GPV4.1"));
    }

    @Test
    public void aWidgetSaysTheQueueOnce() {
        assertEquals("Дім · черга 3.2", Logic.withQueue(place("Дім", "GPV3.2"), "Дім"));
        assertEquals("⚠ Черга 3.2", Logic.withQueue(place("Черга 3.2", "GPV3.2"), "⚠ Черга 3.2"));
    }

    // ---- the two halves, the compact sections, the hint on the bar ---------------------------

    @Test
    public void theCardCountsDownInWordsAPersonWouldSay() {
        Logic.Now n = Logic.now(DAY_11 + repeat('1', 48), OCT30, at(5, 0), false);
        assertEquals("через 1 год 00 хв", Logic.heroCountdown(n));
        n = Logic.now(DAY_11 + repeat('1', 48), OCT30, at(13, 1), false);
        assertEquals("через 1 год 29 хв", Logic.heroCountdown(n));
        assertEquals("", Logic.heroCountdown(new Logic.Now()));
    }

    @Test
    public void aRunOfTheBarWritesItsHoursWhenThereIsRoom() {
        assertEquals("4 год", Logic.barHoursWord(8));
        assertEquals("6,5 год", Logic.barHoursWord(13));
        assertEquals("0,5 год", Logic.barHoursWord(1));
    }

    @Test
    public void aTouchFindsTheWholeRun() {
        String day = repeat('0', 18) + repeat('1', 13) + repeat('0', 8) + repeat('1', 9);
        assertEquals(Arrays.asList(0, 18), asList(Logic.barRun(day, 0)));
        assertEquals(Arrays.asList(0, 18), asList(Logic.barRun(day, 17)));
        assertEquals(Arrays.asList(18, 31), asList(Logic.barRun(day, 18)));
        assertEquals(Arrays.asList(39, 48), asList(Logic.barRun(day, 47)));
        assertEquals(Arrays.asList(39, 48), asList(Logic.barRun(day, 60)));  // past the edge: the last run
        assertEquals(Arrays.asList(0, 18), asList(Logic.barRun(day, -3)));
    }

    private static List<Integer> asList(int[] run) {
        return Arrays.asList(run[0], run[1]);
    }

    @Test
    public void theHintNamesStartEndWhatAndHowLong() {
        String day = repeat('0', 18) + repeat('1', 13) + repeat('0', 8) + repeat('3', 1) + repeat('2', 8);
        assertEquals("00:00–09:00 · без світла · 9 год", Logic.barTip(day, 0, 18));
        assertEquals("09:00–15:30 · зі світлом · 6 год 30 хв", Logic.barTip(day, 18, 31));
        assertEquals("19:30–20:00 · можливе вимкнення · 30 хв", Logic.barTip(day, 39, 40));
        assertEquals("20:00–24:00 · графіка немає · 4 год", Logic.barTip(day, 40, 48));
    }

    @Test
    public void underTheBarOnlyChangesAndInTwoRowsWhenTheyDoNotFitInOne() {
        String calm = repeat('0', 18) + repeat('1', 13) + repeat('0', 8) + repeat('1', 9);
        List<Logic.Label> one = Logic.barLabelRows(calm, 26, 357, 36, 5);
        assertEquals(3, one.size());
        for (Logic.Label l : one) assertEquals(0, l.row);
        StringBuilder many = new StringBuilder();
        for (int i = 0; i < 5; i++) many.append(repeat('0', 4)).append(repeat('1', 6));
        String crowded = many.substring(0, 48);
        List<Logic.Label> two = Logic.barLabelRows(crowded, -1, 357, 36, 5);
        for (int slot : new int[] {4, 10, 14, 20, 24, 30, 34, 40, 44}) assertTrue("slot " + slot, has(two, slot));
        for (Logic.Label l : two) assertEquals(l.kind == Logic.ON ? 1 : 0, l.row);
        for (int i = 0; i < two.size(); i++) for (int j = i + 1; j < two.size(); j++) {
            if (two.get(i).row == two.get(j).row) assertTrue(Math.abs(two.get(i).x - two.get(j).x) >= 41 - .01f);
        }
        assertEquals(2, Logic.barLabelLines(two));
        assertEquals(1, Logic.barLabelLines(one));
        assertEquals(0, Logic.barLabelLines(Logic.barLabelRows(repeat('1', 48), -1, 357, 36, 5)));
    }

    @Test
    public void theNextNotificationIsSaidWithItsDay() {
        Logic.Place p = new Logic.Place(); p.id = "p1"; p.name = "Дім"; p.group = "GPV1.1";
        Logic.Alerts a = new Logic.Alerts();
        String tl = DAY_11 + repeat('1', 20) + repeat('0', 4) + repeat('1', 24);
        Logic.Event e = Logic.nextAlert(Logic.events(p, tl, OCT30, at(13, 1), a), at(13, 1));
        assertEquals("наступне о 14:15", Logic.alertButtonLine(e, at(13, 1)));
        assertEquals("Наступне сповіщення: сьогодні о 14:15", Logic.alertSheetTitle(e, at(13, 1)));
        assertEquals("За 15 хв до вимкнення о 14:30", Logic.alertSheetDetail(e));
        e = Logic.nextAlert(Logic.events(p, tl, OCT30, at(18, 20), a), at(18, 20));
        assertEquals("наступне о 18:15".replace("18:15", "18:15"), Logic.alertButtonLine(Logic.nextAlert(Logic.events(p, tl, OCT30, at(18, 0), a), at(18, 0)), at(18, 0)));
        assertEquals("За 15 хв до увімкнення о 18:30", Logic.alertSheetDetail(Logic.nextAlert(Logic.events(p, tl, OCT30, at(18, 0), a), at(18, 0))));
        assertEquals("завтра о 09:45", Logic.alertButtonLine(e, at(18, 20)));
        assertEquals("Наступне сповіщення: завтра о 09:45", Logic.alertSheetTitle(e, at(18, 20)));
        a.leadOff = 0;
        e = Logic.nextAlert(Logic.events(p, tl, OCT30, at(13, 1), a), at(13, 1));
        assertEquals("У момент вимкнення о 14:30", Logic.alertSheetDetail(e));
        assertNull(Logic.nextAlert(java.util.Collections.emptyList(), at(13, 1)));
        // one a little late is on its way already: the line names what is still to come
        a = new Logic.Alerts();
        e = Logic.nextAlert(Logic.events(p, tl, OCT30, at(14, 20), a), at(14, 20));
        assertEquals("наступне о 18:15", Logic.alertButtonLine(e, at(14, 20)));
    }

    @Test
    public void thePlaceRowSaysItsNotificationsInTwoWords() {
        Logic.Alerts a = new Logic.Alerts();
        assertEquals("за 15 хв", Logic.alertsShort(a));
        a.leadOn = 30;
        assertEquals("за 15 і 30 хв", Logic.alertsShort(a));
        a.leadOff = 0; a.leadOn = 0;
        assertEquals("одразу", Logic.alertsShort(a));
        a.leadOff = 0; a.leadOn = 15;
        assertEquals("одразу, за 15 хв", Logic.alertsShort(a));
        a = new Logic.Alerts(); a.on = false;
        assertEquals("за 15 хв", Logic.alertsShort(a));
        a.off = false; a.tomorrow = true;
        assertEquals("графік", Logic.alertsShort(a));
        a.tomorrow = false;
        assertEquals("вимкнено", Logic.alertsShort(a));
    }

    @Test
    public void theFooterSaysWhenCheckedAndPublishedInOneLine() {
        assertEquals("Перевірено о 13:00 · опубліковано о 12:00", Logic.checkedLine(at(13, 0), at(12, 0), at(13, 5)));
        assertEquals("Перевірено о 13:00", Logic.checkedLine(at(13, 0), 0, at(13, 5)));
        assertEquals("", Logic.checkedLine(0, 0, at(13, 5)));
        assertEquals("Перевірено о 13:00 · опубліковано вчора о 22:00", Logic.checkedLine(at(13, 0), at(-2, 0), at(13, 5)));
    }

    @Test
    public void theHelpButtonNamesTheSiteItOpens() {
        assertEquals("Сайт «Без світла» ›", Logic.helpSite(Logic.region("kyiv")));
        assertEquals("Сайт обленерго ›", Logic.helpSite(Logic.region("kyiv_region")));
    }

    @Test
    public void thePlacesTableComparesWeeksInWords() {
        assertEquals("↑ 1 год 30 хв", Logic.changeWords(90));
        assertEquals("↓ 30 хв", Logic.changeWords(-30));
        assertEquals("без змін", Logic.changeWords(0));
        assertEquals("—", Logic.changeWords(Integer.MIN_VALUE));
    }

    @Test
    public void aLargeWidgetKeepsItsHalvesAsTheyWereWhileItShowsTheDaysInSections() {
        Logic.WidgetLayout compact = Logic.widgetLayout(360, 150, true);
        assertFalse(compact.sections);
        for (int[] size : new int[][] {{360, 190}, {420, 220}, {300, 190}}) {
            Logic.WidgetLayout w = Logic.widgetLayout(size[0], size[1], true);
            assertTrue(w.sections);
            assertTrue(w.rowHeight >= 69);
            assertTrue(w.rowTop + w.rowHeight <= w.barTop);
            assertTrue(w.tomorrowBarTop + w.tomorrowBarHeight + w.labelHeight <= size[1] - w.pad + .01f);
            assertTrue(w.tomorrowTitleTop >= w.barTop + w.barHeight + w.labelHeight - .01f);
            assertTrue(w.tomorrowBarTop >= w.tomorrowTitleTop + w.tomorrowHeight - .01f);
        }
        assertFalse(Logic.widgetLayout(250, 120, true).sections);
    }

    @Test
    public void aWideWidgetHasBothHalvesAndTheDayInOneRow() {
        Logic.WidgetLayout w = Logic.widgetLayout(360, 72, true);
        assertFalse(w.twoParts);
        assertTrue(Logic.widgetForm(250, 56) == Logic.W_WIDE);
        assertTrue(Logic.widgetForm(240, 80) == Logic.W_ROW);
        Logic.WideLayout l = Logic.wideLayout(360, 72);
        assertTrue(l.rowTop + l.rowHeight <= l.barTop);
        assertTrue(l.barTop + l.barHeight <= 72 - 4 + .01f);
        assertTrue(l.rowHeight >= 30);
        assertTrue(l.columnWidth >= 150);
    }

    @Test
    public void aWidgetIsRedrawnWhenItsEventLightsUp() {
        String tl = DAY_11 + repeat('1', 48);
        // 14:30 is the change: its half hour and its quarter hour both wake the refresh
        assertEquals(at(14, 15), Logic.nextRefresh(Arrays.asList(tl), OCT30, at(14, 5), at(14, 5)));
        assertTrue(Logic.exactRefresh(Arrays.asList(tl), OCT30, at(14, 5), at(14, 15)));
    }
}
