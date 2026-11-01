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
        return "{\"regionId\":\"kyiv-region\",\"fact\":{\"update\":\"29.10.2026 20:25\",\"today\":" + D30
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
        assertEquals("графік оновлено вчора о 20:25", Logic.updated(1793298300L, at(9, 0)));
        assertEquals("графік оновлено сьогодні о 07:10", Logic.updated(at(7, 10), at(9, 0)));
        assertEquals("графік оновлено 27.10 о 07:10", Logic.updated(at(7, 10) - 3 * 86400, at(9, 0)));
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
        assertEquals("Відключень за графіком не заплановано", n.hint);
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
        Logic.Notice s = Logic.eventText(e, t, OCT30, at(14, 2));
        // the place opens the second line: the title stays short enough for one line
        assertEquals("\uD83D\uDD34 Скоро вимкнуть світло", s.title);
        assertEquals("Дім · о 14:30, через 28 хв", s.text);
        assertFalse(s.on);
        assertEquals(at(18, 30), s.until);  // gone by itself once the power is back
        Logic.Event on = Logic.events(home(), t, OCT30, at(15, 0), new Logic.Alerts()).get(0);
        Logic.Notice n = Logic.eventText(on, t, OCT30, at(18, 15));
        assertEquals("\uD83D\uDFE2 Скоро увімкнуть світло", n.title);
        assertEquals("Дім · о 18:30, через 15 хв", n.text);
        assertTrue(n.on);
        assertEquals(at(19, 30), n.until);
        String maybe = repeat('1', 20) + repeat('3', 8) + repeat('1', 20) + repeat('2', 48);
        Logic.Event m = Logic.events(home(), maybe, OCT30, at(9, 0), new Logic.Alerts()).get(0);
        assertEquals("\uD83D\uDFE0 Можливе вимкнення", Logic.eventText(m, maybe, OCT30, at(9, 45)).title);
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
        Logic.Notice off = Logic.eventText(ev.get(0), t, OCT30, at(14, 30));
        assertEquals("\uD83D\uDD34 Вимкнення за графіком", off.title);
        assertEquals("Дім · до 18:30", off.text);
        assertEquals(Logic.Event.AT_ON, ev.get(1).kind);
        Logic.Notice on = Logic.eventText(ev.get(1), t, OCT30, at(18, 30));
        assertEquals("\uD83D\uDFE2 Світло має з'явитися", on.title);
        assertEquals("Дім · за графіком з 18:30", on.text);
    }

    @Test
    public void anOutageWithoutAKnownEndIsDroppedAfterFourHours() throws Exception {
        String open = repeat('1', 12) + repeat('0', 36) + repeat('2', 48);
        Logic.Alerts a = new Logic.Alerts();
        a.leadOff = 0;
        Logic.Event e = Logic.events(home(), open, OCT30, at(3, 0), a).get(0);
        Logic.Notice s = Logic.eventText(e, open, OCT30, at(6, 0));
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
        assertEquals("Завантажити застосунок «Світлячок»: https://github.com/ABovsh/svitlyachok", Logic.SHARE_TEXT);
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
            assertEquals("графік оновлено 27.10 о 07:10", Logic.updated(at(7, 10) - 3 * 86400, at(9, 0)));
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
        assertEquals(Logic.Event.SOON_ON,
                Logic.events(home(), t, OCT30, at(14, 26), new Logic.Alerts()).get(0).kind);
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
        assertEquals("Дім · Відключень не заплановано", Logic.notes(home(), data(TODAY_ONLY), after, at(19, 0)).get(0).text);
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
        assertEquals(Logic.W_ROW, Logic.widgetForm(300, 70));    // 4 x 1
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
        Logic.Notice n = Logic.eventText(e, t, OCT30, at(14, 2));
        assertEquals("\uD83D\uDD34 Скоро вимкнуть світло", n.title);
        assertEquals("о 14:30, через 28 хв", n.text);
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
    public void theCurrentLineHasAFullAndAShortForm() throws Exception {
        Logic.Now on = Logic.now(tl(TODAY_ONLY), OCT30, at(10, 0), false, null);
        assertEquals("Світло є з 08:00", Logic.nowLine(on));
        assertEquals("(уже 2 год 00 хв)", Logic.nowLineIn(on));
        assertEquals("Світло є вже 2 год 00 хв", Logic.nowLineShort(on));
        Logic.Now allDay = Logic.now(tl(TODAY_ONLY), OCT30, at(3, 0), false, repeat('1', 48));
        assertEquals("Світло є з 00:00 учора", Logic.nowLine(allDay));
        assertEquals("(уже 1 день 3 год)", Logic.nowLineIn(allDay));
        Logic.Now unknown = Logic.now(tl(TODAY_ONLY), OCT30, at(3, 0), false, null);
        assertEquals("Світло є з 00:00", Logic.nowLine(unknown));
        assertEquals("(уже 3 год 00 хв)", Logic.nowLineIn(unknown));
        assertEquals("Світло є вже 3 год 00 хв", Logic.nowLineShort(unknown));
    }

    @Test
    public void hoursInsideTheBarToTenths() {
        assertEquals("3,5г", Logic.barHours(7));
        assertEquals("4г", Logic.barHours(8));
        assertEquals("0,5г", Logic.barHours(1));
        assertEquals("24г", Logic.barHours(48));
    }

    @Test
    public void theLightOfADayInShorterForms() {
        assertEquals(Arrays.asList("18 год світла · 75 %", "18 год світла", "75 %"), Logic.dayLights(DAY_11));
        assertEquals(Arrays.asList("без вимкнень"), Logic.dayLights(repeat('1', 48)));
    }

    @Test
    public void theHighlightComesWithTheReminderOfThePlace() {
        Logic.Alerts a = new Logic.Alerts();
        a.leadOff = 15;
        a.leadOn = 10;
        assertEquals(15 * 60L, Logic.soonS(a, Logic.OFF));
        assertEquals(15 * 60L, Logic.soonS(a, Logic.MAYBE));
        assertEquals(10 * 60L, Logic.soonS(a, Logic.ON));
        a.on = false;  // no reminder: the usual half an hour
        assertEquals(Logic.SOON_S, Logic.soonS(a, Logic.ON));
        a.leadOff = 0;  // a reminder at the very moment: still the usual half an hour ahead
        assertEquals(Logic.SOON_S, Logic.soonS(a, Logic.OFF));
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
        // an outage to the end of the day: power back at 24:00; one from midnight starts at 00:00
        List<Logic.Label> t = Logic.barLabels(repeat('0', 4) + repeat('1', 36) + repeat('3', 8), -1, 318, 30, 5);
        assertEquals(Logic.OFF, kind(t, 0));
        assertEquals(Logic.ON, kind(t, 4));
        assertEquals(Logic.MAYBE, kind(t, 40));
        assertEquals(Logic.ON, kind(t, 48));
        // no outages: only the grey hours
        List<Logic.Label> clean = Logic.barLabels(repeat('1', 48), 10, 318, 30, 5);
        assertEquals(Logic.UNKNOWN, kind(clean, 0));
        assertEquals(Logic.UNKNOWN, kind(clean, 48));
        assertEquals(5, clean.size());  // 00, 06, 12, 18, 24
    }

    @Test
    public void compactLinesPreserveStateAndKnownEnd() throws Exception {
        Logic.Now n = Logic.now(tl(TODAY_ONLY), OCT30, at(10, 0), false);
        assertEquals("Є до 14:30", Logic.tileLine(n));
        assertEquals("через 4 год 30 хв · до 18:30", Logic.nextDetail(n));
        assertEquals(Arrays.asList("Вимкнуть о", "14:30", "до 18:30"), Arrays.asList(Logic.widgetNext(n)));
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

    @Test
    public void compactEventsKeepTomorrowMidnightAndPossibleOutagesExplicit() {
        Logic.Now n = new Logic.Now();
        n.state = Logic.MAYBE; n.title = "Можливе вимкнення"; n.big = "до 15:00";
        assertEquals(n.title, Logic.tileLine(n));
        n.next = Logic.MAYBE; n.change = 1; n.hint = "Можливе вимкнення завтра о 06:00";
        n.then = "потім можливе вимкнення до завтра 09:00"; n.hintIn = "";
        assertEquals("до завтра 09:00", Logic.nextDetail(n));
        assertEquals(Arrays.asList("Можливе вимкнення завтра о", "06:00", "до завтра 09:00"), Arrays.asList(Logic.widgetNext(n)));
        n.next = Logic.ON; n.hint = "Увімкнуть о 24:00"; n.hintIn = "через 30 хв";
        assertEquals("через 30 хв", Logic.nextDetail(n));
        n.then = "";
        assertEquals(Arrays.asList("Увімкнуть о", "24:00", ""), Arrays.asList(Logic.widgetNext(n)));
        n.change = 0;
        assertEquals(Arrays.asList("", "", ""), Arrays.asList(Logic.widgetNext(n)));
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
            assertTrue(w.textSize >= 12 && w.textSize <= 19);
        }
        assertFalse(Logic.widgetLayout(180,80,true).twoParts);
        assertFalse(Logic.widgetLayout(360,80,true).twoParts);
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

    private static boolean has(List<Logic.Label> l, int slot) {
        for (Logic.Label x : l) if (x.slot == slot) return true;
        return false;
    }

    private static int kind(List<Logic.Label> l, int slot) {
        for (Logic.Label x : l) if (x.slot == slot) return x.kind;
        return -99;
    }
}
