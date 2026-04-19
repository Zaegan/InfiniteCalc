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
     * or variable name immediately after a digit, closing paren, π, or A–H.
     */
    public void insert(String text) {
        if (cursor > 0) {
            char prev = expr.charAt(cursor - 1);
            boolean prevIsValue = Character.isDigit(prev) || prev == ')'
                    || prev == 'π' || (prev >= 'A' && prev <= 'H');
            boolean textOpensGroup = text.startsWith("sin(") || text.startsWith("cos(")
                    || text.startsWith("tan(") || text.startsWith("ln(")
                    || text.startsWith("log(") || text.startsWith("sqrt(")
                    || text.startsWith("√(")
                    || text.equals("π") || text.equals("e")
                    || (text.length() == 1 && text.charAt(0) >= 'A' && text.charAt(0) <= 'H');
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
        String[] multiTokens = {"sin(", "cos(", "tan(", "log(", "ln(", "sqrt(", "√("};
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
        if (depth > 0 && prev != '(') {
            expr.insert(cursor, ")");
            cursor++;
        } else if (prev != 0 && (Character.isDigit(prev) || prev == ')')) {
            expr.insert(cursor, "×(");
            cursor += 2;
        } else {
            expr.insert(cursor, "(");
            cursor++;
        }
    }

    // ── Formatting ───────────────────────────────────────────────────────────

    /**
     * Format a double result for display.
     * Whole numbers up to 1e15 are shown without a decimal point.
     */
    public static String formatResult(double result) {
        if (!Double.isInfinite(result) && !Double.isNaN(result)
                && result == Math.floor(result) && Math.abs(result) < 1e15) {
            return String.valueOf((long) result);
        }
        return String.valueOf(result);
    }
}
