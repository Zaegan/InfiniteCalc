package com.github.zaegan.infinitecalc;

/**
 * Pure-Java state machine for the calculator's expression input.
 * No Android dependencies — fully testable with plain JUnit.
 *
 * Holds the current expression string and the cursor position within it.
 * All mutation methods update both atomically.
 */
public class CalculatorState {

    private final StringBuilder expr = new StringBuilder();
    private int cursor = 0;

    // ── Accessors ────────────────────────────────────────────────────────────

    public String getExpression() { return expr.toString(); }
    public int getCursor() { return cursor; }

    /** Clamp and store a cursor position received from the UI. */
    public void syncCursor(int pos) {
        cursor = Math.max(0, Math.min(pos, expr.length()));
    }

    /** Replace the entire expression (e.g. when restoring a history entry). */
    public void setExpression(String s) {
        expr.setLength(0);
        expr.append(s == null ? "" : s);
        cursor = expr.length();
    }

    /** Clear the expression and reset the cursor to 0. */
    public void clear() {
        expr.setLength(0);
        cursor = 0;
    }

    // ── Input mutations ──────────────────────────────────────────────────────

    /**
     * Insert text at the current cursor position.
     *
     * Automatically prepends × when inserting a function call, constant,
     * or variable name immediately after a digit, closing paren, π, or A–Z.
     */
    public void insert(String text) {
        // Minus-toggle: if inserting '−' and there is already a unary '−' directly
        // before the cursor, remove it instead of stacking another minus sign.
        if (text.equals("\u2212") && cursor > 0
                && expr.charAt(cursor - 1) == '\u2212'
                && isUnaryAt(expr.toString(), cursor - 1)) {
            expr.deleteCharAt(cursor - 1);
            cursor--;
            return;
        }

        if (text.length() == 1 && isNonMinusOperator(text.charAt(0))) {
            while (cursor > 0 && isOperatorChar(expr.charAt(cursor - 1))) {
                expr.deleteCharAt(cursor - 1);
                cursor--;
            }
        }

        if (cursor > 0) {
            char prev = expr.charAt(cursor - 1);
            boolean prevIsValue = Character.isDigit(prev) || prev == ')'
                    || prev == 'π' || prev == 'e'
                    || (prev >= 'A' && prev <= 'Z')
                    || prev == '\u03B1' || prev == '\u03B2'
                    || prev == '\u2099' || prev == '\u2091' || prev == '\u2090'; // subscript constant endings
            boolean textOpensGroup = text.startsWith("sin(") || text.startsWith("cos(")
                    || text.startsWith("tan(") || text.startsWith("ln(")
                    || text.startsWith("log(") || text.startsWith("sqrt(")
                    || text.startsWith("√(") || text.startsWith("\u221B(")
                    || text.startsWith("asin(") || text.startsWith("acos(") || text.startsWith("atan(")
                    || text.startsWith("sinh(") || text.startsWith("cosh(") || text.startsWith("tanh(")
                    || text.startsWith("exp(") || text.startsWith("cbrt(") || text.startsWith("nthrt(")
                    || text.startsWith("abs(") || text.startsWith("round(")
                    || text.startsWith("floor(") || text.startsWith("ceil(")
                    || text.startsWith("log2(") || text.startsWith("logn(")
                    || text.startsWith("mod(") || text.startsWith("ncr(") || text.startsWith("npr(")
                    || text.startsWith("10^(")
                    || text.equals("π") || text.equals("e")
                    || text.equals("G\u2099") || text.equals("k\u2091") || text.equals("N\u2090")
                    || (text.length() == 1 && (
                            (text.charAt(0) >= 'A' && text.charAt(0) <= 'Z')
                            || text.charAt(0) == '\u03B1' || text.charAt(0) == '\u03B2'));
            if (prevIsValue && textOpensGroup) {
                expr.insert(cursor, "×");
                cursor++;
            }
        }
        expr.insert(cursor, text);
        cursor += text.length();
    }

    /**
     * Delete the token immediately before the cursor.
     * Multi-character function tokens (e.g. "sin(") are deleted as a single unit.
     */
    public void backspace() {
        if (cursor == 0) return;
        // Canonical built-in tokens deleted as a single unit.
        // Keep in sync with the multi-char insert texts in RemapConfig.makeDefault().
        // User-defined custom button text is intentionally NOT listed here — it
        // deletes character by character, which is the naive/expected behaviour.
        String[] multiTokens = {
            "asin(", "acos(", "atan(",
            "sinh(", "cosh(", "tanh(",
            "nthrt(", "round(", "floor(", "ceil(",
            "sqrt(", "cbrt(", "logn(", "log2(", "log(", "ln(",
            "ncr(", "npr(", "mod(",
            "exp(", "abs(", "sin(", "cos(", "tan(", "√(", "\u221B(",
            "10^(", "^2", "^3",
            "G\u2099", "k\u2091", "N\u2090"
        };
        String before = expr.substring(0, cursor);
        for (String token : multiTokens) {
            if (before.endsWith(token)) {
                expr.delete(cursor - token.length(), cursor);
                cursor -= token.length();
                return;
            }
        }
        expr.deleteCharAt(cursor - 1);
        cursor--;
    }

    /**
     * Smart parenthesis logic:
     * <ul>
     *   <li>Insert ) if there is an unmatched ( before the cursor
     *       AND the character immediately before the cursor is not (.</li>
     *   <li>Otherwise insert ( — with an automatic × prefix when the
     *       preceding character is a digit or ).</li>
     * </ul>
     */
    public void smartParen() {
        String before = expr.substring(0, cursor);
        int depth = 0;
        for (char c : before.toCharArray()) {
            if (c == '(') depth++;
            else if (c == ')') depth--;
        }
        char prev = before.isEmpty() ? 0 : before.charAt(before.length() - 1);

        boolean prevIsValue = prev != 0
                && (Character.isDigit(prev) || prev == ')'
                    || prev == 'π' || prev == 'e'
                    || (prev >= 'A' && prev <= 'Z')
                    || prev == '\u03B1' || prev == '\u03B2'
                    || prev == '\u2099' || prev == '\u2091' || prev == '\u2090');

        if (prevIsValue) {
            if (depth > 0) {
                expr.insert(cursor, ")");
                cursor++;
            } else {
                expr.insert(cursor, "×(");
                cursor += 2;
            }
        } else {
            expr.insert(cursor, "(");
            cursor++;
        }
    }

    /**
     * Smart negation (standard mode).
     * Delegates to {@link #smartNegate(boolean)} with {@code false}.
     */
    public void smartNegate() {
        smartNegate(false);
    }

    /**
     * Smart negation — behavior depends on mode.
     *
     * <p>Both modes find a "token start": the start of any adjacent digit run,
     * the position of an adjacent {@code (} or letter ahead, or the cursor itself.
     *
     * <p><b>Negation-first mode:</b> toggles a {@code −} (U+2212) prefix immediately
     * before the token — same handling for digits, paren groups, and function names.
     *
     * <p><b>Standard mode:</b> toggles a {@code (−} (open-paren + U+2212) prefix
     * immediately before the token.  No auto-close; the user is responsible for the
     * closing {@code )}.
     */
    public void smartNegate(boolean negationFirstMode) {
        String s = expr.toString();
        int tokenStart = findNegationTokenStart(s, cursor);

        if (negationFirstMode) {
            // Toggle U+2212 before the token
            if (tokenStart > 0 && s.charAt(tokenStart - 1) == '\u2212'
                    && isUnaryAt(s, tokenStart - 1)) {
                expr.deleteCharAt(tokenStart - 1);
                if (cursor > tokenStart - 1) cursor--;
            } else {
                expr.insert(tokenStart, '\u2212');
                if (cursor >= tokenStart) cursor++;
            }
        } else {
            // Standard mode: toggle (U+2212 before the token
            if (tokenStart >= 2 && s.charAt(tokenStart - 2) == '('
                    && s.charAt(tokenStart - 1) == '\u2212') {
                expr.delete(tokenStart - 2, tokenStart);
                if (cursor >= tokenStart) cursor -= 2;
                else if (cursor > tokenStart - 2) cursor = tokenStart - 2;
            } else {
                expr.insert(tokenStart, "(\u2212");
                if (cursor >= tokenStart) cursor += 2;
            }
        }
    }

    /**
     * Insert {@code (−} at the cursor (standard-mode negation from the minus button).
     * Toggles: if {@code (−} is already immediately before the cursor, removes it.
     */
    public void insertStandardNegation() {
        String s = expr.toString();
        if (cursor >= 2 && s.charAt(cursor - 2) == '('
                && s.charAt(cursor - 1) == '\u2212') {
            expr.delete(cursor - 2, cursor);
            cursor -= 2;
        } else {
            expr.insert(cursor, "(\u2212");
            cursor += 2;
        }
    }

    /**
     * Find the position where a negation prefix should be inserted.
     * <ul>
     *   <li>If a digit run ends at (or starts at) the cursor: returns its start.</li>
     *   <li>If the character at the cursor is {@code (} or a letter: returns the cursor.</li>
     *   <li>Otherwise: returns the cursor.</li>
     * </ul>
     */
    private static int findNegationTokenStart(String s, int cursor) {
        // Scan back for digit run ending at cursor
        int numStart = cursor;
        while (numStart > 0
                && (Character.isDigit(s.charAt(numStart - 1))
                    || s.charAt(numStart - 1) == '.')) {
            numStart--;
        }
        if (numStart < cursor) return numStart;

        // Digit run starting at cursor
        int len = s.length();
        if (cursor < len
                && (Character.isDigit(s.charAt(cursor)) || s.charAt(cursor) == '.')) {
            return cursor;
        }

        // '(' or letter directly ahead
        if (cursor < len
                && (s.charAt(cursor) == '(' || Character.isLetter(s.charAt(cursor)))) {
            return cursor;
        }

        return cursor;
    }

    // ── Operator helpers ────────────────────────────────────────────────────

    private static boolean isOperatorChar(char c) {
        return c == '+' || c == '\u2212' || c == '\u00D7' || c == '\u00F7'
                || c == '^' || c == '%';
    }

    private static boolean isNonMinusOperator(char c) {
        return c == '+' || c == '\u00D7' || c == '\u00F7' || c == '^' || c == '%';
    }

    private static boolean isUnaryAt(String s, int pos) {
        if (pos == 0) return true;
        char prev = s.charAt(pos - 1);
        return prev == '+' || prev == '\u2212' || prev == '\u00D7' || prev == '\u00F7'
                || prev == '^' || prev == '%' || prev == '(';
    }

    // ── Formatting ───────────────────────────────────────────────────────────

    /**
     * Format a result for display, suppressing floating-point noise by rounding
     * to 10 significant figures (e.g. 0.1+0.2 = 0.30000000000000004 → "0.3").
     */
    public static String formatResult(double result) {
        if (Double.isNaN(result)) return "NaN";
        if (Double.isInfinite(result)) return result > 0 ? "Infinity" : "-Infinity";

        // Round to 10 significant figures
        String formatted = String.format("%.10g", result);
        double rounded = Double.parseDouble(formatted);

        // Show as a plain integer if the rounded value is whole and in safe range
        if (rounded == Math.floor(rounded) && Math.abs(rounded) < 1e15) {
            return String.valueOf((long) rounded);
        }

        // Strip trailing zeros from mantissa (handles both decimal and sci notation)
        int eIdx = formatted.indexOf('e');
        if (eIdx < 0) eIdx = formatted.indexOf('E');
        if (eIdx >= 0) {
            String mantissa = formatted.substring(0, eIdx).replaceAll("\\.?0+$", "");
            // Normalise exponent: "e+15" → "e15", "e-05" → "e-5"
            String exp = formatted.substring(eIdx)
                    .replaceAll("[eE]\\+0*", "e")
                    .replaceAll("[eE]-0*", "e-");
            return mantissa + exp;
        }
        return formatted.replaceAll("\\.?0+$", "");
    }
}
