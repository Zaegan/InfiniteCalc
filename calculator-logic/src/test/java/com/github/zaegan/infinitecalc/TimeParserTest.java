package com.github.zaegan.infinitecalc;

import org.junit.Test;
import static org.junit.Assert.*;

/**
 * Tests for {@link TimeParser}.
 *
 * Covers:
 *  - Time literal formats (H:, H:MM, H:MM:SS, decimal components)
 *  - AM/PM input conversion
 *  - Addition and subtraction
 *  - Postfix '::' AM/PM display
 *  - Day wrapping and negative results
 *  - Syntax errors
 */
public class TimeParserTest {

    // ── isTimeExpression ───────────────────────────────────────────────────────

    @Test public void testIsTimeExpression_withColon()    { assertTrue(TimeParser.isTimeExpression("1:30")); }
    @Test public void testIsTimeExpression_withoutColon() { assertFalse(TimeParser.isTimeExpression("1+30")); }
    @Test public void testIsTimeExpression_null()         { assertFalse(TimeParser.isTimeExpression(null)); }
    @Test public void testIsTimeExpression_empty()        { assertFalse(TimeParser.isTimeExpression("")); }

    // ── Trailing-colon form (H:) ───────────────────────────────────────────────

    @Test public void testTrailingColon_oneHour()    { eq("1:00",  "1:"); }
    @Test public void testTrailingColon_zeroHours()  { eq("0:00",  "0:"); }
    @Test public void testTrailingColon_twoDigitH()  { eq("12:00", "12:"); }
    @Test public void testTrailingColon_largeHour()  { eq("100:00", "100:"); }

    // ── H:MM ──────────────────────────────────────────────────────────────────

    @Test public void testHoursMinutes_basic()        { eq("1:30",  "1:30"); }
    @Test public void testHoursMinutes_zeroMinutes()  { eq("2:00",  "2:00"); }
    @Test public void testHoursMinutes_59Minutes()    { eq("1:59",  "1:59"); }
    @Test public void testHoursMinutes_midnight()     { eq("0:00",  "0:00"); }

    // ── H:MM:SS ───────────────────────────────────────────────────────────────

    @Test public void testSeconds_basic()          { eq("1:30:45", "1:30:45"); }
    @Test public void testSeconds_zeroSeconds()    { eq("1:30",    "1:30:00"); }  // trailing :00 omitted
    @Test public void testSeconds_zeroMinSec()     { eq("1:00",    "1:00:00"); }

    // ── Decimal hours ──────────────────────────────────────────────────────────

    @Test public void testDecimalHours_half()       { eq("1:30",   "1.5:"); }    // 1.5h = 1h 30m
    @Test public void testDecimalHours_quarter()    { eq("0:15",   "0.25:"); }   // 0.25h = 15m
    @Test public void testDecimalHours_withMins()   { eq("1:36",   "1.1:30"); }  // 1.1h+30m = 66m+30m = 96m = 1h36m

    // ── Decimal minutes ────────────────────────────────────────────────────────

    @Test public void testDecimalMinutes_half()     { eq("1:30:30", "1:30.5"); }  // 30.5m = 30m 30s
    @Test public void testDecimalMinutes_ninetyPct(){ eq("1:00:54", "1:00.9"); }  // 0.9m = 54s
    @Test public void testDecimalMinutes_withSecs() { eq("1:30:30", "1:30.5"); }

    // ── Decimal seconds ────────────────────────────────────────────────────────

    @Test public void testDecimalSeconds_basic()    { eq("1:00:00.5", "1:00:00.5"); }
    @Test public void testDecimalSeconds_150ms()    { eq("1:00:00.15", "1:00:00.15"); }
    @Test public void testDecimalSeconds_twoFrac()  { eq("1:00:01.5",  "1:00:01.5"); }

    // ── Millisecond display precision ──────────────────────────────────────────

    @Test public void testMs_100ms()   { eq("0:00:00.1",  "0:00:00.1"); }
    @Test public void testMs_10ms()    { eq("0:00:00.01", "0:00:00.01"); }
    @Test public void testMs_1ms()     { eq("0:00:00.001","0:00:00.001"); }

    // ── Addition ───────────────────────────────────────────────────────────────

    @Test public void testAdd_simpleMinutes()      { eq("1:30", "1:00+0:30"); }
    @Test public void testAdd_crossHour()          { eq("2:00", "1:30+0:30"); }
    @Test public void testAdd_trailingColons()     { eq("3:00", "1:+2:"); }
    @Test public void testAdd_withSeconds()        { eq("1:01:00.15", "1:00:00.15+0:01"); }
    @Test public void testAdd_multipleOperands()   { eq("3:00", "1:+1:+1:"); }

    // ── Subtraction ───────────────────────────────────────────────────────────

    @Test public void testSub_basic()          { eq("1:15", "1:30-0:15"); }
    @Test public void testSub_trailingColons() { eq("1:00", "2:-1:"); }
    @Test public void testSub_toMidnight()     { eq("0:00", "1:00-1:"); }

    // ── Day wrapping ───────────────────────────────────────────────────────────

    @Test public void testDayWrap_plusOne()  { eq("1:00 +1d", "23:00+2:"); }
    @Test public void testDayWrap_plusTwo()  { eq("1:00 +2d", "23:00+26:"); }
    @Test public void testDayWrap_exact24()  { eq("0:00 +1d", "24:"); }
    @Test public void testDayWrap_negOne()   { eq("23:00 -1d", "0:00-1:"); }
    @Test public void testDayWrap_negResult(){ eq("23:00 -1d", "-1:"); }

    // ── AM/PM input ───────────────────────────────────────────────────────────

    @Test public void testAmPm_nineAm()     { eq("9:00",  "9:00 AM"); }
    @Test public void testAmPm_ninePm()     { eq("21:00", "9:00 PM"); }
    @Test public void testAmPm_midnightAm() { eq("0:00",  "12:00 AM"); }
    @Test public void testAmPm_noonPm()     { eq("12:00", "12:00 PM"); }
    @Test public void testAmPm_oneAm()      { eq("1:00",  "1:00 AM"); }
    @Test public void testAmPm_onePm()      { eq("13:00", "1:00 PM"); }
    @Test public void testAmPm_lowercase()  { eq("9:00",  "9:00 am"); }
    @Test public void testAmPm_addDuration(){ eq("9:30",  "9:00 AM+0:30"); }
    @Test public void testAmPm_withSecs()   { eq("9:30:15", "9:30:15 AM"); }

    // ── Postfix '::' AM/PM display ─────────────────────────────────────────────

    @Test public void testAmPmDisplay_noon()       { eq("12:00 PM", "12:::"); }   // 12: + ::
    @Test public void testAmPmDisplay_3pm()        { eq("3:30 PM",  "15:30::"); }
    @Test public void testAmPmDisplay_midnight()   { eq("12:00 AM", "0:::"); }
    @Test public void testAmPmDisplay_1am()        { eq("1:00 AM",  "1:::"); }
    @Test public void testAmPmDisplay_11pm()       { eq("11:00 PM", "23:::"); }
    @Test public void testAmPmDisplay_withSecs()   { eq("3:30:45 PM", "15:30:45::"); }
    @Test public void testAmPmDisplay_add()        { eq("2:00 AM",  "1:+1:::"); }
    @Test public void testAmPmDisplay_withDayWrap(){ eq("1:00 AM +1d", "23:+2:::"); }

    // ── Unicode minus ──────────────────────────────────────────────────────────

    @Test public void testUnicodeMinus() {
        // U+2212 should be treated the same as ASCII '-'
        eq("1:00", "2:\u22121:");
    }

    // ── Edge cases ────────────────────────────────────────────────────────────

    @Test public void testLeadingSign_positive()    { eq("1:30", "+1:30"); }
    @Test public void testLeadingSign_negative()    { eq("23:00 -1d", "-1:"); }
    @Test public void testZero_midnight()           { eq("0:00",  "0:00"); }
    @Test public void testSeconds_omittedWhenZero() {
        // Seconds component present but zero: omit from output
        eq("1:30", "1:30:00");
    }
    @Test public void testHH_trailingColon()        { eq("15:00", "15:"); }

    // ── Syntax errors ─────────────────────────────────────────────────────────

    @Test public void testError_oneDigitMinute() {
        err("1:5"); // minutes must be 2 digits
    }
    @Test public void testError_oneDigitSecond() {
        err("1:00:5"); // seconds must be 2 digits
    }
    @Test public void testError_amPmOnSecondOperand() {
        err("9:00 AM+1:00 PM");
    }
    @Test public void testError_amPmHoursTooLarge() {
        err("13:00 PM");
    }
    @Test public void testError_amPmHoursZero() {
        err("0:00 AM");
    }
    @Test public void testError_amPmDecimalHours() {
        err("1.5:00 AM");
    }
    @Test public void testError_missingColon() {
        err("130"); // no colon — should not be a valid time expression
        // (note: isTimeExpression("130") = false, so the ViewModel won't route it here;
        //  but if somehow called directly it should error)
    }
    @Test public void testError_emptyBeforeAmPm() {
        err("::"); // stripped to empty
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void eq(String expected, String expr) {
        try {
            String result = TimeParser.evaluate(expr);
            assertEquals("evaluate(\"" + expr + "\")", expected, result);
        } catch (Exception e) {
            fail("evaluate(\"" + expr + "\") threw: " + e.getMessage());
        }
    }

    private static void err(String expr) {
        try {
            String result = TimeParser.evaluate(expr);
            fail("evaluate(\"" + expr + "\") should have thrown but returned: " + result);
        } catch (Exception e) {
            // expected
        }
    }
}
