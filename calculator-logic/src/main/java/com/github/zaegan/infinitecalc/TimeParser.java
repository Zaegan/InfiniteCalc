package com.github.zaegan.infinitecalc;

import java.util.Locale;

/**
 * Parallel parser for clock/time expressions.
 *
 * <p>Triggered when an expression contains ':'. Evaluates addition and subtraction
 * of time values using 24-hour (military) arithmetic. Results are normalised to
 * the current day; overflow is expressed as a day offset.
 *
 * <h3>Time literal formats</h3>
 * <pre>
 *   H:              H hours (trailing colon marks a time, no minutes)
 *   H.hh:           Decimal hours, trailing colon
 *   H:MM            Hours and minutes  (MM = exactly 2 digits)
 *   H.hh:MM         Decimal hours and integer minutes
 *   H:MM.mm         Hours and decimal minutes
 *   H.hh:MM.mm      Decimal hours and decimal minutes
 *   H:MM:SS         Hours, minutes, integer seconds  (SS = exactly 2 digits)
 *   H.hh:MM:SS      Decimal hours, minutes, seconds
 *   H:MM:SS.ss      Hours, minutes, decimal seconds
 *   H.hh:MM:SS.ss   All components with decimal seconds
 * </pre>
 *
 * <p>Decimal fractions: each component's decimal is a fraction of that unit.
 * <ul>
 *   <li>0.5 hours = 30 minutes</li>
 *   <li>0.5 minutes = 30 seconds</li>
 *   <li>0.5 seconds = 500 ms</li>
 * </ul>
 *
 * <h3>AM/PM input</h3>
 * <p>AM or PM suffix is accepted on the first operand only (case-insensitive).
 * Integer hours must be 1–12; decimal hours are not permitted with AM/PM.
 * <ul>
 *   <li>12 AM = midnight (0:00 military)</li>
 *   <li>12 PM = noon (12:00 military)</li>
 *   <li>13 AM/PM = syntax error</li>
 * </ul>
 *
 * <h3>Postfix {@code ::} operator</h3>
 * <p>Appended to the whole expression to request AM/PM output.
 * Examples:
 * <ul>
 *   <li>{@code 15:30::} → 3:30 PM</li>
 *   <li>{@code 12:::} = {@code 12:} + {@code ::} → 12:00 PM</li>
 *   <li>{@code 0:::} → 12:00 AM</li>
 * </ul>
 *
 * <h3>Day wrapping</h3>
 * <p>Results outside [0, 24h) are normalised and the day offset is appended.
 * Example: {@code 23:00+2:00} → {@code 1:00 +1d}
 */
public class TimeParser {

    // ── Public API ─────────────────────────────────────────────────────────────

    /** Returns true if the expression should be routed to the time parser. */
    public static boolean isTimeExpression(String expr) {
        return expr != null && expr.contains(":");
    }

    /**
     * Evaluate a time expression and return a formatted result string.
     *
     * @param raw the expression to evaluate (may contain Unicode minus U+2212)
     * @return formatted time string, e.g. "1:30", "3:30 PM", "1:00 +1d"
     * @throws Exception with a descriptive message on any syntax or range error
     */
    public static String evaluate(String raw) throws Exception {
        if (raw == null || raw.trim().isEmpty()) throw new Exception("Empty time expression");

        // Normalise Unicode minus to ASCII so the inner parser stays simple
        String expr = raw.trim().replace('\u2212', '-');

        // Detect and strip trailing '::' (AM/PM display postfix)
        boolean ampmDisplay = false;
        if (expr.endsWith("::")) {
            ampmDisplay = true;
            expr = expr.substring(0, expr.length() - 2).trim();
            if (expr.isEmpty()) throw new Exception("Empty expression before '::'");
        }

        ExprParser p = new ExprParser(expr);
        double totalSeconds = p.parseExpr();
        if (p.pos != expr.length()) {
            throw new Exception("Unexpected characters at position " + p.pos + ": \""
                    + expr.substring(p.pos) + "\"");
        }

        return formatResult(totalSeconds, ampmDisplay);
    }

    // ── Expression parser ──────────────────────────────────────────────────────

    private static class ExprParser {
        final String s;
        int pos = 0;
        boolean firstOperand = true;

        ExprParser(String s) { this.s = s; }

        double parseExpr() throws Exception {
            skipWs();

            // Leading sign applies to the first operand
            double sign = 1.0;
            if (pos < s.length() && s.charAt(pos) == '-') { sign = -1.0; pos++; skipWs(); }
            else if (pos < s.length() && s.charAt(pos) == '+') { pos++; skipWs(); }

            double total = sign * parseTimeLiteral();
            firstOperand = false;

            while (true) {
                skipWs();
                if (pos >= s.length()) break;
                char op = s.charAt(pos);
                if (op == '+')      { pos++; skipWs(); total += parseTimeLiteral(); }
                else if (op == '-') { pos++; skipWs(); total -= parseTimeLiteral(); }
                else break;
            }
            return total;
        }

        void skipWs() {
            while (pos < s.length() && s.charAt(pos) == ' ') pos++;
        }

        /**
         * Parse a single time literal: H[.hh]:[[MM[.mm]][:SS[.ss]]] [AM|PM]
         * Returns the literal's value in seconds.
         */
        double parseTimeLiteral() throws Exception {
            // ── Hours integer part ─────────────────────────────────────────
            if (pos >= s.length() || !Character.isDigit(s.charAt(pos))) {
                throw new Exception("Expected number at position " + pos);
            }
            int hStart = pos;
            while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
            double hours = Double.parseDouble(s.substring(hStart, pos));

            // ── Optional decimal fraction of hours ─────────────────────────
            if (pos < s.length() && s.charAt(pos) == '.'
                    && pos + 1 < s.length() && Character.isDigit(s.charAt(pos + 1))) {
                pos++; // skip '.'
                int fracStart = pos;
                while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
                hours += Double.parseDouble("0." + s.substring(fracStart, pos));
            }

            // ── Colon separator (required) ─────────────────────────────────
            if (pos >= s.length() || s.charAt(pos) != ':') {
                throw new Exception("Expected ':' after hours at position " + pos);
            }
            pos++; // consume ':'

            // ── Optional minutes (exactly 2 integer digits) ────────────────
            double minutes = 0.0;
            double seconds = 0.0;

            if (pos < s.length() && Character.isDigit(s.charAt(pos))) {
                int mStart = pos;
                while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
                int mLen = pos - mStart;
                if (mLen != 2) {
                    throw new Exception("Minutes must be exactly 2 digits, got " + mLen
                            + " at position " + mStart);
                }
                minutes = Double.parseDouble(s.substring(mStart, pos));

                // Optional decimal fraction of minutes
                if (pos < s.length() && s.charAt(pos) == '.'
                        && pos + 1 < s.length() && Character.isDigit(s.charAt(pos + 1))) {
                    pos++;
                    int fracStart = pos;
                    while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
                    minutes += Double.parseDouble("0." + s.substring(fracStart, pos));
                }

                // ── Optional ':SS' ─────────────────────────────────────────
                if (pos < s.length() && s.charAt(pos) == ':') {
                    int savedPos = pos;
                    pos++; // tentatively consume ':'
                    if (pos < s.length() && Character.isDigit(s.charAt(pos))) {
                        int sStart = pos;
                        while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
                        int sLen = pos - sStart;
                        if (sLen != 2) {
                            throw new Exception("Seconds must be exactly 2 digits, got " + sLen
                                    + " at position " + sStart);
                        }
                        seconds = Double.parseDouble(s.substring(sStart, pos));

                        // Optional decimal fraction of seconds
                        if (pos < s.length() && s.charAt(pos) == '.'
                                && pos + 1 < s.length() && Character.isDigit(s.charAt(pos + 1))) {
                            pos++;
                            int fracStart = pos;
                            while (pos < s.length() && Character.isDigit(s.charAt(pos))) pos++;
                            seconds += Double.parseDouble("0." + s.substring(fracStart, pos));
                        }
                    } else {
                        // ':' not followed by a digit — back up (leave ':' unconsumed)
                        pos = savedPos;
                    }
                }
            }
            // else: trailing-colon form (H: or H.hh:) — minutes and seconds stay 0

            // ── Optional AM/PM suffix ──────────────────────────────────────
            skipWs();
            if (pos + 1 < s.length()) {
                String suffix = s.substring(pos, pos + 2).toUpperCase(Locale.US);
                if ("AM".equals(suffix) || "PM".equals(suffix)) {
                    if (!firstOperand) {
                        throw new Exception("AM/PM suffix is only allowed on the first operand");
                    }
                    boolean isPm = "PM".equals(suffix);
                    pos += 2;

                    // Validate: integer hours only, 1–12
                    if (hours != Math.floor(hours)) {
                        throw new Exception("Decimal hours are not allowed with AM/PM suffix");
                    }
                    int h = (int) hours;
                    if (h < 1 || h > 12) {
                        throw new Exception("Hours must be 1–12 with AM/PM, got " + h);
                    }
                    // Validate integer minutes 0–59
                    if (Math.floor(minutes) > 59) {
                        throw new Exception("Minutes must be 0–59 with AM/PM");
                    }
                    // Validate integer seconds 0–59
                    if (Math.floor(seconds) > 59) {
                        throw new Exception("Seconds must be 0–59 with AM/PM");
                    }

                    // Convert to military hours
                    if (isPm) {
                        hours = (h == 12) ? 12.0 : h + 12.0;
                    } else { // AM
                        hours = (h == 12) ? 0.0 : h;
                    }
                }
            }

            return hours * 3600.0 + minutes * 60.0 + seconds;
        }
    }

    // ── Result formatting ──────────────────────────────────────────────────────

    /**
     * Format a total-seconds value as a clock string.
     *
     * <p>Working internally in milliseconds avoids floating-point noise in
     * component extraction. Precision is capped at 1 ms (3 decimal places).
     */
    private static String formatResult(double totalSeconds, boolean ampmDisplay) {
        // Round to millisecond precision
        long totalMs = Math.round(totalSeconds * 1000.0);

        final long MS_PER_DAY = 86_400_000L;
        long days   = Math.floorDiv(totalMs, MS_PER_DAY);
        long msOfDay = Math.floorMod(totalMs, MS_PER_DAY);

        long h  = msOfDay / 3_600_000L;  msOfDay %= 3_600_000L;
        long m  = msOfDay /    60_000L;  msOfDay %=    60_000L;
        long s  = msOfDay /     1_000L;
        long ms = msOfDay %     1_000L;

        // Seconds string (omitted when zero)
        String secondsPart = "";
        if (s > 0 || ms > 0) {
            if (ms == 0) {
                secondsPart = String.format(Locale.US, ":%02d", s);
            } else {
                // Strip trailing zeros from milliseconds
                String msStr;
                if (ms % 100 == 0)      msStr = String.valueOf(ms / 100);
                else if (ms % 10 == 0)  msStr = String.format(Locale.US, "%02d", ms / 10);
                else                    msStr = String.format(Locale.US, "%03d", ms);
                secondsPart = String.format(Locale.US, ":%02d.%s", s, msStr);
            }
        }

        // Main time string
        String timeStr;
        if (ampmDisplay) {
            long displayH = h % 12;
            if (displayH == 0) displayH = 12;
            String ampm = (h < 12) ? " AM" : " PM";
            timeStr = displayH + ":" + String.format(Locale.US, "%02d", m) + secondsPart + ampm;
        } else {
            timeStr = h + ":" + String.format(Locale.US, "%02d", m) + secondsPart;
        }

        // Day suffix
        if (days > 0) timeStr += " +" + days + "d";
        else if (days < 0) timeStr += " " + days + "d";

        return timeStr;
    }
}
