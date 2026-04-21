package com.github.zaegan.infinitecalc;

import org.mariuszgromada.math.mxparser.Argument;
import org.mariuszgromada.math.mxparser.Expression;
import org.mariuszgromada.math.mxparser.License;
import org.mariuszgromada.math.mxparser.mXparser;

import java.util.Map;
import java.util.regex.Pattern;

/**
 * Expression evaluator backed by mXparser.
 *
 * <p>A thin preprocessing layer converts our UI notation into a form mXparser
 * can evaluate natively. No custom functions are registered — everything is
 * handled by mXparser's built-in library.
 *
 * <p>Preprocessing map (applied in order):
 * <ul>
 *   <li>Unicode operators (−×÷) → ASCII</li>
 *   <li>Casio unary: bare {@code -digits} at unary position → {@code (-digits)}
 *       (does not wrap when {@code -} follows {@code (})</li>
 *   <li>% infix operator → {@code mod(left,right)}</li>
 *   <li>∛( → root(3,</li>
 *   <li>√( → sqrt(</li>
 *   <li>π → pi</li>
 *   <li>Gₙ → 6.674e-11 (gravitational constant)</li>
 *   <li>kₑ → 8.9875517923e9 (Coulomb's constant)</li>
 *   <li>Nₐ → 6.02214076e23 (Avogadro's number)</li>
 *   <li>α/β → _ic_Alpha/_ic_Beta</li>
 *   <li>A–Z → _ic_A–_ic_Z  (user variables, kept out of mXparser's namespace entirely)</li>
 *   <li>log( → log10(  (single-arg base-10 convenience)</li>
 *   <li>logn( → log(   (mXparser's two-arg log)</li>
 *   <li>nthrt( → root( (mXparser's nth-root)</li>
 *   <li>cbrt( → root(3, (cube root)</li>
 *   <li>ncr( → C(      (mXparser combinations)</li>
 *   <li>npr( → nPk(    (mXparser permutations)</li>
 * </ul>
 */
public class MxEvaluator {

    // Single-arg log( → log10(  (\b avoids matching logn(, log2(, log10()
    private static final Pattern PAT_LOG = Pattern.compile("\\blog\\(");

    static {
        License.iConfirmNonCommercialUse("InfiniteCalc");
    }

    // ── Convenience overloads (default radians) ────────────────────────────

    public static double evaluate(String expression,
                                   Map<String, Double> variables) throws Exception {
        return evaluate(expression, variables, true);
    }

    public static double evaluatePartial(String expression,
                                          Map<String, Double> variables) throws Exception {
        return evaluatePartial(expression, variables, true);
    }

    public static boolean isValidPartial(String expression,
                                          Map<String, Double> variables) {
        return isValidPartial(expression, variables, true);
    }

    // ── Public API ─────────────────────────────────────────────────────────

    public static double evaluate(String expression,
                                   Map<String, Double> variables,
                                   boolean useRadians) throws Exception {
        if (useRadians) mXparser.setRadiansMode(); else mXparser.setDegreesMode();
        Expression e = build(preprocess(expression), variables);
        double result = e.calculate();
        if (Double.isNaN(result) || Double.isInfinite(result)) {
            if (Double.isInfinite(result)) throw new Exception("Division by zero");
            String msg = firstMeaningfulError(e.getErrorMessage());
            throw new Exception(msg.isEmpty() ? "Error" : msg);
        }
        return result;
    }

    public static double evaluatePartial(String expression,
                                          Map<String, Double> variables,
                                          boolean useRadians) throws Exception {
        return evaluate(autoCloseParen(expression), variables, useRadians);
    }

    public static boolean isValidPartial(String expression,
                                          Map<String, Double> variables,
                                          boolean useRadians) {
        if (expression == null || expression.trim().isEmpty()) return false;
        try {
            evaluatePartial(expression, variables, useRadians);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    // ── Preprocessing ──────────────────────────────────────────────────────

    static String preprocess(String raw) {
        if (raw == null) return "";
        String s = raw;
        // Unicode operators → ASCII
        s = s.replace('\u2212', '-').replace('\u00D7', '*').replace('\u00F7', '/');
        // Casio-style unary: bare -digits at unary position → (-digits)
        s = applyCasioUnary(s);
        // % infix operator → mod(leftOp,rightOp)
        s = convertModulo(s);
        // ∛( → root(3,
        s = s.replace("\u221B(", "root(3,");
        // √( → sqrt(
        s = s.replace("\u221A(", "sqrt(");
        // π → pi  (mXparser built-in constant)
        s = s.replace("\u03C0", "pi");
        // Physical constants (must precede A–Z remapping to avoid G→_ic_G etc.)
        s = s.replace("G\u2099", "6.674e-11");         // Gₙ — Newton's gravitational constant
        s = s.replace("k\u2091", "8.9875517923e9");    // kₑ — Coulomb's constant
        s = s.replace("N\u2090", "6.02214076e23");     // Nₐ — Avogadro's number
        // Greek variables → _ic_ identifiers (same namespace as A–Z)
        s = s.replace("\u03B1", "_ic_Alpha");
        s = s.replace("\u03B2", "_ic_Beta");
        // User variables A–Z (plus α/β above) → _ic_ identifiers.
        // All 28 user variables are kept entirely out of mXparser's built-in
        // namespace, which is opaque and version-dependent. The _ic_ prefix is
        // long enough to be practically immune to future mXparser additions.
        // The regex matches a standalone letter: not preceded or followed by
        // [A-Za-z0-9], and not followed by '(' which would indicate a function call.
        s = s.replaceAll("(?<![A-Za-z0-9])([A-Z])(?![A-Za-z0-9(])", "_ic_$1");
        // Single-arg log( → log10(
        s = PAT_LOG.matcher(s).replaceAll("log10(");
        // UI function names → mXparser native equivalents
        s = s.replace("logn(",  "log(");
        s = s.replace("nthrt(", "root(");
        s = s.replace("cbrt(",  "root(3,");
        s = s.replace("ncr(",   "C(");
        s = s.replace("npr(",   "nPk(");
        return s;
    }

    /**
     * Casio-style unary minus: wraps a bare {@code -digits} sequence in parens
     * when the minus is at a unary position (start of string, or after an
     * arithmetic operator), but NOT when it immediately follows {@code (}.
     *
     * <p>Examples: {@code -10^2} → {@code (-10)^2};
     *              {@code 2+-10^2} → {@code 2+(-10)^2};
     *              {@code (-10)^2} → unchanged (already grouped).
     */
    static String applyCasioUnary(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        int i = 0;
        while (i < s.length()) {
            char c = s.charAt(i);
            if (c == '-' && isCasioUnaryPos(s, i)) {
                int j = i + 1;
                while (j < s.length()
                        && (Character.isDigit(s.charAt(j)) || s.charAt(j) == '.')) {
                    j++;
                }
                if (j > i + 1) { // found at least one digit after the minus
                    sb.append("(-").append(s, i + 1, j).append(")");
                    i = j;
                    continue;
                }
            }
            sb.append(c);
            i++;
        }
        return sb.toString();
    }

    /** True when {@code pos} is a unary-minus position and not immediately after {@code (}. */
    private static boolean isCasioUnaryPos(String s, int pos) {
        if (pos == 0) return true;
        char prev = s.charAt(pos - 1);
        if (prev == '(') return false; // already inside explicit parens — don't double-wrap
        return prev == '+' || prev == '-' || prev == '*' || prev == '/' || prev == '^';
    }

    /**
     * Convert the {@code %} infix modulo operator to {@code mod(left,right)}.
     * Uses a paren-depth-aware scan to correctly capture multi-character operands.
     * Recurses to handle multiple {@code %} signs left-to-right.
     */
    static String convertModulo(String s) {
        int idx = s.indexOf('%');
        if (idx < 0) return s;

        // Scan LEFT: find start of left operand (stop at depth-0 +/- or string start)
        int depth = 0;
        int leftStart = idx;
        while (leftStart > 0) {
            char c = s.charAt(leftStart - 1);
            if (c == ')') { depth++; leftStart--; continue; }
            if (c == '(') {
                if (depth == 0) break;
                depth--; leftStart--; continue;
            }
            if (depth == 0 && (c == '+' || c == '-')) break;
            leftStart--;
        }

        // Scan RIGHT: find end of right operand (stop at depth-0 +,-,*,/,% or string end)
        depth = 0;
        int rightEnd = idx + 1;
        while (rightEnd < s.length()) {
            char c = s.charAt(rightEnd);
            if (c == '(') { depth++; rightEnd++; continue; }
            if (c == ')') {
                if (depth == 0) break;
                depth--; rightEnd++; continue;
            }
            if (depth == 0 && (c == '+' || c == '-' || c == '*' || c == '/' || c == '%')) break;
            rightEnd++;
        }

        String result = s.substring(0, leftStart)
                + "mod(" + s.substring(leftStart, idx) + "," + s.substring(idx + 1, rightEnd) + ")"
                + s.substring(rightEnd);
        return convertModulo(result);
    }

    private static String autoCloseParen(String expr) {
        int open = 0;
        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (c == '(') open++;
            else if (c == ')') open--;
        }
        if (open <= 0) return expr;
        StringBuilder sb = new StringBuilder(expr);
        for (int i = 0; i < open; i++) sb.append(')');
        return sb.toString();
    }

    // ── Expression building ────────────────────────────────────────────────

    private static Expression build(String expr, Map<String, Double> vars) {
        Expression e = new Expression(expr);
        e.setSilentMode();
        if (vars != null) {
            for (Map.Entry<String, Double> entry : vars.entrySet()) {
                String name = entry.getKey();
                if ("\u03B1".equals(name)) name = "_ic_Alpha";
                else if ("\u03B2".equals(name)) name = "_ic_Beta";
                else if (name.length() == 1
                        && name.charAt(0) >= 'A' && name.charAt(0) <= 'Z') {
                    name = "_ic_" + name;
                }
                if (usedIn(expr, name)) {
                    e.addArguments(new Argument(name, entry.getValue()));
                }
            }
        }
        return e;
    }

    /** True if {@code name} appears in {@code expr} as a standalone identifier. */
    private static boolean usedIn(String expr, String name) {
        int idx = expr.indexOf(name);
        while (idx >= 0) {
            boolean prevOk = idx == 0
                    || !Character.isLetterOrDigit(expr.charAt(idx - 1));
            boolean nextOk = idx + name.length() >= expr.length()
                    || !Character.isLetterOrDigit(expr.charAt(idx + name.length()));
            if (prevOk && nextOk) return true;
            idx = expr.indexOf(name, idx + 1);
        }
        return false;
    }

    // ── Helpers ────────────────────────────────────────────────────────────

    private static String firstMeaningfulError(String msg) {
        if (msg == null || msg.isEmpty()) return "";
        for (String line : msg.split("\n")) {
            line = line.trim();
            if (line.startsWith("[")) {
                int eq = line.indexOf('=');
                if (eq >= 0) {
                    String tail = line.substring(eq + 1).trim();
                    if (!tail.isEmpty() && !tail.equals("null")) return tail;
                }
            }
        }
        return "Expression error";
    }
}
