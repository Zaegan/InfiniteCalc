package com.github.zaegan.infinitecalc;

/**
 * Pure-Java state machine for the calculator's expression input.
 * No Android dependencies — fully testable with plain JUnit.
 */
public class CalculatorState {

    private final StringBuilder expr = new StringBuilder();
    private int cursor = 0;

    // ── Accessors ────────────────────────────────────────────────────────────

    public String getExpression() { return expr.toString(); }
    public int getCursor() { return cursor; }

    public void syncCursor(int pos) {
        cursor = Math.max(0, Math.min(pos, expr.length()));
    }

    public void setExpression(String s) {
        expr.setLength(0);
        expr.append(s == null ? "" : s);
        cursor = expr.length();
    }

    public void clear() {
        expr.setLength(0);
        cursor = 0;
    }

    // ── Input mutations ──────────────────────────────────────────────────────

    public void insert(String text) {
        // Operator replacement: if inserting a non-minus binary operator, delete any
        // consecutive operators immediately before the cursor so e.g. "5+" → "×" → "5×".
        // The minus sign is excluded because it doubles as unary negative.
        if (text.length() == 1 && isNonMinusOperator(text.charAt(0))) {
            while (cursor > 0 && isOperatorChar(expr.charAt(cursor - 1))) {
                expr.deleteCharAt(cursor - 1);
                cursor--;
            }
        }

        if (cursor > 0) {
            char prev = expr.charAt(cursor - 1);
            boolean prevIsValue = Character.isDigit(prev) || prev == ')'
                    || prev == 'π' || (prev >= 'A' && prev <= 'P');
            boolean textOpensGroup = text.startsWith("sin(") || text.startsWith("cos(")
                    || text.startsWith("tan(") || text.startsWith("ln(")
                    || text.startsWith("log(") || text.startsWith("sqrt(")
                    || text.startsWith("√(")
                    || text.equals("π") || text.equals("e")
                    || (text.length() == 1 && text.charAt(0) >= 'A' && text.charAt(0) <= 'P');
            if (prevIsValue && textOpensGroup) {
                expr.insert(cursor, "×");
                cursor++;
            }
        }
        expr.insert(cursor, text);
        cursor += text.length();
    }

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

    // ── Operator helpers ────────────────────────────────────────────────────

    private static boolean isOperatorChar(char c) {
        return c == '+' || c == '\u2212' || c == '\u00D7' || c == '\u00F7'
                || c == '^' || c == '%';
    }

    /** True for binary operators other than minus (minus can be unary). */
    private static boolean isNonMinusOperator(char c) {
        return c == '+' || c == '\u00D7' || c == '\u00F7' || c == '^' || c == '%';
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
