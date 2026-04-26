package com.github.zaegan.infinitecalc;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class MxEvaluatorTest {

    // ── Helpers ──────────────────────────────────────────────────────────────

    private double eval(String expr) throws Exception {
        return MxEvaluator.evaluate(expr, null);
    }

    private double evalVars(String expr, Map<String, Double> vars) throws Exception {
        return MxEvaluator.evaluate(expr, vars);
    }

    private double partial(String expr) throws Exception {
        return MxEvaluator.evaluatePartial(expr, null);
    }

    /** Evaluate in negation-first mode, restoring the flag afterward. */
    private double evalNegFirst(String expr) throws Exception {
        MxEvaluator.negationFirstMode = true;
        try {
            return MxEvaluator.evaluate(expr, null);
        } finally {
            MxEvaluator.negationFirstMode = false;
        }
    }

    // ── Preprocessing helpers (package-visible, tested directly) ─────────────

    @Test public void casioUnaryWrapsDigits() {
        assertEquals("(-10)^2", MxEvaluator.applyCasioUnary("-10^2"));
    }

    @Test public void casioUnaryAfterOperator() {
        assertEquals("2+(-10)^2", MxEvaluator.applyCasioUnary("2+-10^2"));
    }

    @Test public void casioUnaryDoubleGroupHarmless() {
        // Already-grouped (-10) gets wrapped again: ((-10))^2 = 100, still correct
        assertEquals("((-10))^2", MxEvaluator.applyCasioUnary("(-10)^2"));
    }

    @Test public void casioUnaryInsideParens() {
        // (-5^2+2)/2: Casio wraps the -5 inside the group → ((-5)^2+2)/2
        assertEquals("((-5)^2+2)/2", MxEvaluator.applyCasioUnary("(-5^2+2)/2"));
    }

    // Pass 2: -(expr)^X wrapping

    @Test public void casioUnaryParenGroupWrapped() {
        // -(5+3)^2 → (-(5+3))^2
        assertEquals("(-(5+3))^2", MxEvaluator.applyCasioUnary("-(5+3)^2"));
    }

    @Test public void casioUnaryParenGroupNoCaretUnchanged() {
        // No ^ after group — not a negation-first pattern
        assertEquals("-(5+3)+1", MxEvaluator.applyCasioUnary("-(5+3)+1"));
    }

    @Test public void casioUnaryParenGroupAfterOperator() {
        assertEquals("2*(-(5+3))^2", MxEvaluator.applyCasioUnary("2*-(5+3)^2"));
    }

    @Test public void casioUnaryParenGroupNested() {
        // Inner -3 is already wrapped by pass 1; outer group then wrapped by pass 2
        assertEquals("(-(5+(-3)))^2", MxEvaluator.applyCasioUnary("-(5+-3)^2"));
    }

    @Test public void casioUnaryBothDigitAndParenGroup() {
        assertEquals("(-9)^2+(-(5+3))^2", MxEvaluator.applyCasioUnary("-9^2+-(5+3)^2"));
    }

    // Pass 3: -funcname(expr)^X wrapping

    @Test public void casioUnaryFuncWrapped() {
        assertEquals("(-sin(x))^2", MxEvaluator.applyCasioUnary("-sin(x)^2"));
    }

    @Test public void casioUnaryFuncNoCaretUnchanged() {
        assertEquals("-sin(x)+1", MxEvaluator.applyCasioUnary("-sin(x)+1"));
    }

    @Test public void casioUnaryFuncAfterOperator() {
        assertEquals("2*(-sin(x))^2", MxEvaluator.applyCasioUnary("2*-sin(x)^2"));
    }

    @Test public void casioUnaryAllThreePatterns() {
        assertEquals("(-9)^2+(-(5+3))^2+(-sin(x))^2",
                MxEvaluator.applyCasioUnary("-9^2+-(5+3)^2+-sin(x)^2"));
    }

    @Test public void convertModuloSimple() {
        assertEquals("mod(10,3)", MxEvaluator.convertModulo("10%3"));
    }

    @Test public void convertModuloInExpression() {
        assertEquals("2+mod(10,3)", MxEvaluator.convertModulo("2+10%3"));
    }

    @Test public void convertModuloLeftOperandIsProduct() {
        assertEquals("mod(2*5,3)", MxEvaluator.convertModulo("2*5%3"));
    }

    @Test public void convertModuloRightStopsAtMultiply() {
        assertEquals("mod(10,3)*2", MxEvaluator.convertModulo("10%3*2"));
    }

    // ── Basic arithmetic ─────────────────────────────────────────────────────

    @Test public void addition() throws Exception {
        assertEquals(8.0, eval("5+3"), 1e-10);
    }

    @Test public void subtraction() throws Exception {
        assertEquals(5.0, eval("9-4"), 1e-10);
    }

    @Test public void multiplicationAscii() throws Exception {
        assertEquals(42.0, eval("6*7"), 1e-10);
    }

    @Test public void multiplicationUnicode() throws Exception {
        assertEquals(42.0, eval("6\u00D77"), 1e-10);
    }

    @Test public void divisionAscii() throws Exception {
        assertEquals(2.5, eval("10/4"), 1e-10);
    }

    @Test public void divisionUnicode() throws Exception {
        assertEquals(2.5, eval("10\u00F74"), 1e-10);
    }

    @Test public void modulo() throws Exception {
        assertEquals(1.0, eval("mod(10,3)"), 1e-10);
    }

    @Test public void moduloInfixOperator() throws Exception {
        assertEquals(1.0, eval("10%3"), 1e-10);
    }

    @Test public void moduloInfixInExpression() throws Exception {
        assertEquals(3.0, eval("2+10%3"), 1e-10); // 2 + mod(10,3) = 2+1 = 3
    }

    // ── Precedence and associativity ─────────────────────────────────────────

    @Test public void addThenMultiply() throws Exception {
        assertEquals(14.0, eval("2+3*4"), 1e-10);
    }

    @Test public void multiplyThenAdd() throws Exception {
        assertEquals(10.0, eval("2*3+4"), 1e-10);
    }

    @Test public void powerRightAssociative() throws Exception {
        assertEquals(512.0, eval("2^3^2"), 1e-10);
    }

    @Test public void powerBeforeMultiply() throws Exception {
        assertEquals(18.0, eval("2*3^2"), 1e-10);
    }

    // ── Unary operators ──────────────────────────────────────────────────────

    @Test public void casioUnaryNegation() throws Exception {
        // Negation-first mode: -10^2 → (-10)^2 = 100
        assertEquals(100.0, evalNegFirst("-10^2"), 1e-10);
    }

    @Test public void casioUnaryInExpression() throws Exception {
        // Negation-first: 2 + (-10)^2 = 2 + 100 = 102
        assertEquals(102.0, evalNegFirst("2+-10^2"), 1e-10);
    }

    @Test public void casioUnaryInsideGroup() throws Exception {
        // Negation-first wraps -5 inside parens → ((-5)^2+2)/2 = 27/2 = 13.5
        assertEquals(13.5, evalNegFirst("(-5^2+2)/2"), 1e-10);
    }

    @Test public void unaryMinus() throws Exception {
        assertEquals(-2.0, eval("-5+3"), 1e-10);
    }

    @Test public void unaryMinusUnicode() throws Exception {
        assertEquals(-2.0, eval("\u22125+3"), 1e-10);
    }

    @Test public void unaryPlus() throws Exception {
        assertEquals(5.0, eval("+5"), 1e-10);
    }

    @Test public void doubleUnaryMinus() throws Exception {
        assertEquals(5.0, eval("--5"), 1e-10);
    }

    // ── Parentheses ──────────────────────────────────────────────────────────

    @Test public void parenthesesOverridePrecedence() throws Exception {
        assertEquals(20.0, eval("(2+3)*4"), 1e-10);
    }

    @Test public void nestedParens() throws Exception {
        assertEquals(10.0, eval("((2+3)*2)"), 1e-10);
    }

    // ── Decimal numbers ──────────────────────────────────────────────────────

    @Test public void decimalAddition() throws Exception {
        assertEquals(3.0, eval("1.5+1.5"), 1e-10);
    }

    // ── Constants ────────────────────────────────────────────────────────────

    @Test public void piConstant() throws Exception {
        assertEquals(Math.PI, eval("π"), 1e-10);
    }

    @Test public void piKeyword() throws Exception {
        assertEquals(Math.PI, eval("pi"), 1e-10);
    }

    @Test public void eConstant() throws Exception {
        assertEquals(Math.E, eval("e"), 1e-10);
    }

    @Test public void piInExpression() throws Exception {
        assertEquals(2 * Math.PI, eval("2*π"), 1e-10);
    }

    // ── Functions ────────────────────────────────────────────────────────────

    @Test public void sinZero() throws Exception {
        assertEquals(0.0, eval("sin(0)"), 1e-10);
    }

    @Test public void cosZero() throws Exception {
        assertEquals(1.0, eval("cos(0)"), 1e-10);
    }

    @Test public void tanZero() throws Exception {
        assertEquals(0.0, eval("tan(0)"), 1e-10);
    }

    @Test public void lnE() throws Exception {
        assertEquals(1.0, eval("ln(e)"), 1e-10);
    }

    @Test public void logHundred() throws Exception {
        assertEquals(2.0, eval("log(100)"), 1e-10);
    }

    @Test public void sqrtFour() throws Exception {
        assertEquals(2.0, eval("sqrt(4)"), 1e-10);
    }

    @Test public void sqrtUnicode() throws Exception {
        assertEquals(3.0, eval("√(9)"), 1e-10);
    }

    @Test public void cubeRootUnicode() throws Exception {
        assertEquals(2.0, eval("\u221B(8)"), 1e-10);
    }

    @Test public void gravitationalConstant() throws Exception {
        assertEquals(6.674e-11, eval("G\u2099"), 1e-6);
    }

    @Test public void coulombConstant() throws Exception {
        assertEquals(8.9875517923e9, eval("k\u2091"), 1e3);
    }

    @Test public void avogadroNumber() throws Exception {
        assertEquals(6.02214076e23, eval("N\u2090"), 1e13);
    }

    @Test public void functionInExpression() throws Exception {
        assertEquals(1.0 + Math.sin(Math.PI / 2), eval("1+sin(π/2)"), 1e-10);
    }

    // ── Variables ────────────────────────────────────────────────────────────

    @Test public void variableLookup() throws Exception {
        Map<String, Double> vars = new HashMap<>();
        vars.put("A", 7.0);
        assertEquals(7.0, evalVars("A", vars), 1e-10);
    }

    @Test public void variableInExpression() throws Exception {
        Map<String, Double> vars = new HashMap<>();
        vars.put("B", 3.0);
        assertEquals(9.0, evalVars("B*B", vars), 1e-10);
    }

    @Test public void allVariableNames() throws Exception {
        Map<String, Double> vars = new HashMap<>();
        for (char c = 'A'; c <= 'Z'; c++) vars.put(String.valueOf(c), (double)(c - 'A' + 1));
        assertEquals(1.0,  evalVars("A", vars), 1e-10);
        assertEquals(3.0,  evalVars("C", vars), 1e-10); // C would conflict with mXparser C(n,k)
        assertEquals(26.0, evalVars("Z", vars), 1e-10);
    }

    @Test public void variableDoesNotConflictWithBuiltins() throws Exception {
        // All 26 variables in the map; ncr/npr must still work as built-in functions
        Map<String, Double> vars = new HashMap<>();
        for (char c = 'A'; c <= 'Z'; c++) vars.put(String.valueOf(c), (double)(c - 'A' + 1));
        assertEquals(10.0, evalVars("ncr(5,2)", vars), 1e-10);
        assertEquals(20.0, evalVars("npr(5,2)", vars), 1e-10);
    }

    // ── Partial evaluation ───────────────────────────────────────────────────

    @Test public void partialAutoClosesOneParen() throws Exception {
        assertEquals(Math.sin(1), partial("sin(1"), 1e-10);
    }

    @Test public void partialAutoClosesNestedParens() throws Exception {
        assertEquals(3.0, partial("(1+2"), 1e-10);
    }

    @Test public void partialCompleteExpressionUnchanged() throws Exception {
        assertEquals(6.0, partial("2*3"), 1e-10);
    }

    @Test public void isValidPartialRejectsUnmatchedClose() {
        assertFalse(MxEvaluator.isValidPartial("2+3)", null));
    }

    @Test public void isValidPartialAcceptsGoodExpr() {
        assertTrue(MxEvaluator.isValidPartial("2+3", null));
    }

    @Test public void isValidPartialAcceptsOpenParen() {
        assertTrue(MxEvaluator.isValidPartial("sin(1", null));
    }

    // ── Trig rounding ────────────────────────────────────────────────────────

    @Test public void sinPiSnapsToZero() throws Exception {
        assertEquals(0.0, eval("sin(π)"), 0.0);
    }

    @Test public void cosPiSnapsToMinusOne() throws Exception {
        assertEquals(-1.0, eval("cos(π)"), 0.0);
    }

    // ── Inverse trig (radians) ───────────────────────────────────────────────

    @Test public void asinHalf() throws Exception {
        assertEquals(Math.PI / 6, eval("asin(0.5)"), 1e-10);
    }

    @Test public void acosOne() throws Exception {
        assertEquals(0.0, eval("acos(1)"), 1e-10);
    }

    @Test public void atanOne() throws Exception {
        assertEquals(Math.PI / 4, eval("atan(1)"), 1e-10);
    }

    // ── Inverse trig (degrees) ───────────────────────────────────────────────

    @Test public void asinHalfDeg() throws Exception {
        assertEquals(30.0, MxEvaluator.evaluate("asin(0.5)", null, false), 1e-10);
    }

    @Test public void acosHalfDeg() throws Exception {
        assertEquals(60.0, MxEvaluator.evaluate("acos(0.5)", null, false), 1e-10);
    }

    @Test public void atan1Deg() throws Exception {
        assertEquals(45.0, MxEvaluator.evaluate("atan(1)", null, false), 1e-10);
    }

    // ── Hyperbolic functions ─────────────────────────────────────────────────

    @Test public void sinhZero() throws Exception {
        assertEquals(0.0, eval("sinh(0)"), 1e-10);
    }

    @Test public void coshZero() throws Exception {
        assertEquals(1.0, eval("cosh(0)"), 1e-10);
    }

    @Test public void tanhZero() throws Exception {
        assertEquals(0.0, eval("tanh(0)"), 1e-10);
    }

    // ── exp / cbrt ───────────────────────────────────────────────────────────

    @Test public void expOne() throws Exception {
        assertEquals(Math.E, eval("exp(1)"), 1e-10);
    }

    @Test public void cbrtEight() throws Exception {
        assertEquals(2.0, eval("cbrt(8)"), 1e-10);
    }

    @Test public void cbrtNegativeEight() throws Exception {
        assertEquals(-2.0, eval("cbrt(-8)"), 1e-10);
    }

    // ── abs / round / floor / ceil ───────────────────────────────────────────

    @Test public void absNegative() throws Exception {
        assertEquals(5.0, eval("abs(-5)"), 1e-10);
    }

    @Test public void roundHalf() throws Exception {
        assertEquals(3.0, eval("round(2.5,0)"), 1e-10);
    }

    @Test public void floorPositive() throws Exception {
        assertEquals(2.0, eval("floor(2.9)"), 1e-10);
    }

    @Test public void ceilPositive() throws Exception {
        assertEquals(3.0, eval("ceil(2.1)"), 1e-10);
    }

    // ── log2 ─────────────────────────────────────────────────────────────────

    @Test public void log2ofEight() throws Exception {
        assertEquals(3.0, eval("log2(8)"), 1e-10);
    }

    // ── Two-arg: logn ────────────────────────────────────────────────────────

    @Test public void lognBase3Of27() throws Exception {
        assertEquals(3.0, eval("logn(3,27)"), 1e-10);
    }

    @Test public void lognBase10Of100() throws Exception {
        assertEquals(2.0, eval("logn(10,100)"), 1e-10);
    }

    // ── Two-arg: nthrt ───────────────────────────────────────────────────────

    @Test public void nthrtCubeRoot() throws Exception {
        assertEquals(2.0, eval("nthrt(3,8)"), 1e-10);
    }

    @Test public void nthrtNegativeOddRoot() throws Exception {
        assertEquals(-2.0, eval("nthrt(3,-8)"), 1e-10);
    }

    // ── Factorial ────────────────────────────────────────────────────────────

    @Test public void factZero() throws Exception {
        assertEquals(1.0, eval("0!"), 1e-10);
    }

    @Test public void factFive() throws Exception {
        assertEquals(120.0, eval("5!"), 1e-10);
    }

    // ── Two-arg: ncr / npr ───────────────────────────────────────────────────

    @Test public void ncrFiveChooseTwo() throws Exception {
        assertEquals(10.0, eval("ncr(5,2)"), 1e-10);
    }

    @Test public void ncrNChoose0() throws Exception {
        assertEquals(1.0, eval("ncr(7,0)"), 1e-10);
    }

    @Test public void nprFivePermTwo() throws Exception {
        assertEquals(20.0, eval("npr(5,2)"), 1e-10);
    }

    // ── Mode-comparison: same expression, different modes ─────────────────────

    @Test public void standardModeNegDigitBeforePow() throws Exception {
        // Standard: −9^2 = -(9^2) = -81  (U+2212, the UI minus)
        assertEquals(-81.0, eval("\u22129^2"), 1e-10);
    }

    @Test public void standardModeNegTwoSquared() throws Exception {
        // The originally reported bug: −2^2 must equal -4, not 4
        assertEquals(-4.0, eval("\u22122^2"), 1e-10);
    }

    @Test public void negFirstModeNegDigitBeforePow() throws Exception {
        // Negation-first: -9^2 → (-9)^2 = 81
        assertEquals(81.0, evalNegFirst("-9^2"), 1e-10);
    }

    @Test public void standardModeNegParenBeforePow() throws Exception {
        // Standard: −(5+3)^2 = -(64) = -64  (U+2212, the UI minus)
        assertEquals(-64.0, eval("\u2212(5+3)^2"), 1e-10);
    }

    @Test public void negFirstModeNegParenBeforePow() throws Exception {
        // Negation-first: -(5+3)^2 → (-(5+3))^2 = (-8)^2 = 64
        assertEquals(64.0, evalNegFirst("-(5+3)^2"), 1e-10);
    }

    @Test public void negDigitInsideExprBothModes() throws Exception {
        // −9^2 inside a larger expression — UI minus (U+2212) in standard mode
        assertEquals(-81.0 + 1.0, eval("\u22129^2+1"), 1e-10);      // standard: -80
        assertEquals(81.0 + 1.0,  evalNegFirst("-9^2+1"), 1e-10);   // negFirst (ASCII ok): 82
    }

    @Test public void negParenInsideExprBothModes() throws Exception {
        assertEquals(-64.0 + 1.0, eval("\u2212(5+3)^2+1"), 1e-10);      // standard: -63
        assertEquals(64.0 + 1.0,  evalNegFirst("-(5+3)^2+1"), 1e-10);   // negFirst (ASCII ok): 65
    }

    @Test public void standardModeFuncNegBeforePow() throws Exception {
        // Standard: −sin(π/2)^2 = -(sin(π/2)^2) = -(1) = -1  (U+2212, the UI minus)
        assertEquals(-1.0, eval("\u2212sin(pi/2)^2"), 1e-10);
    }

    @Test public void negFirstModeFuncNegBeforePow() throws Exception {
        // Negation-first: -sin(π/2)^2 → (-sin(π/2))^2 = (-1)^2 = 1
        assertEquals(1.0, evalNegFirst("-sin(pi/2)^2"), 1e-10);
    }

    // ── normalizeToNegFirst ───────────────────────────────────────────────────

    @Test public void normalizeToNegFirstDigit() {
        assertEquals("\u2212(9^2)", MxEvaluator.normalizeToNegFirst("\u22129^2"));
    }

    @Test public void normalizeToNegFirstDigitInExpr() {
        assertEquals("\u2212(9^2)+1", MxEvaluator.normalizeToNegFirst("\u22129^2+1"));
    }

    @Test public void normalizeToNegFirstChainedExponent() {
        // −9^3^2 → −(9^3^2): entire right-associative chain is wrapped
        assertEquals("\u2212(9^3^2)", MxEvaluator.normalizeToNegFirst("\u22129^3^2"));
    }

    @Test public void normalizeToNegFirstParen() {
        assertEquals("\u2212((5+3)^2)", MxEvaluator.normalizeToNegFirst("\u2212(5+3)^2"));
    }

    @Test public void normalizeToNegFirstParenInExpr() {
        assertEquals("1+\u2212((5+3)^2)", MxEvaluator.normalizeToNegFirst("1+\u2212(5+3)^2"));
    }

    @Test public void normalizeToNegFirstNoPatternUnchanged() {
        // No ^ after digit/group — nothing to rewrite
        assertEquals("\u22129+2", MxEvaluator.normalizeToNegFirst("\u22129+2"));
    }

    @Test public void normalizeToNegFirstAlreadyWrapped() {
        // −(9^2): no ^ after the outer ')' — left as-is
        assertEquals("\u2212(9^2)", MxEvaluator.normalizeToNegFirst("\u2212(9^2)"));
    }

    @Test public void normalizeToNegFirstMultiplePatterns() {
        assertEquals("\u2212(9^2)\u00D7\u2212(4^3)",
                MxEvaluator.normalizeToNegFirst("\u22129^2\u00D7\u22124^3"));
    }

    @Test public void normalizeToNegFirstFunction() {
        // −sin(x)^2 in standard = -(sin(x))^2 = -(sin(x)^2)
        // After normalizeToNegFirst: −(sin(x)^2) so negFirst still gives -(sin(x)^2)
        assertEquals("\u2212(sin(x)^2)", MxEvaluator.normalizeToNegFirst("\u2212sin(x)^2"));
    }

    @Test public void normalizeToNegFirstFunctionNoCaretUnchanged() {
        assertEquals("\u2212sin(x)+1", MxEvaluator.normalizeToNegFirst("\u2212sin(x)+1"));
    }

    // ── normalizeToStandard ───────────────────────────────────────────────────

    @Test public void normalizeToStandardDigit() {
        assertEquals("(\u22129)^2", MxEvaluator.normalizeToStandard("\u22129^2"));
    }

    @Test public void normalizeToStandardDigitInExpr() {
        assertEquals("(\u22129)^2+1", MxEvaluator.normalizeToStandard("\u22129^2+1"));
    }

    @Test public void normalizeToStandardParen() {
        assertEquals("(\u2212(5+3))^2", MxEvaluator.normalizeToStandard("\u2212(5+3)^2"));
    }

    @Test public void normalizeToStandardNoPatternUnchanged() {
        assertEquals("\u22129+2", MxEvaluator.normalizeToStandard("\u22129+2"));
    }

    @Test public void normalizeToStandardAlreadyExplicit() {
        // (−9)^2: the − is after '(' (unary) but ')' is NOT followed by '^' at this −
        // so it stays untouched
        assertEquals("(\u22129)^2", MxEvaluator.normalizeToStandard("(\u22129)^2"));
    }

    @Test public void normalizeToStandardMultiplePatterns() {
        assertEquals("(\u22129)^2\u00D7(\u22124)^3",
                MxEvaluator.normalizeToStandard("\u22129^2\u00D7\u22124^3"));
    }

    @Test public void normalizeToStandardFunction() {
        assertEquals("(\u2212sin(x))^2", MxEvaluator.normalizeToStandard("\u2212sin(x)^2"));
    }

    @Test public void normalizeToStandardFunctionNoCaretUnchanged() {
        assertEquals("\u2212sin(x)+1", MxEvaluator.normalizeToStandard("\u2212sin(x)+1"));
    }

    // ── Normalization round-trips (semantic preservation) ────────────────────

    @Test public void normalizeNegFirstPreservesDigitEval() throws Exception {
        // −9^2 in standard = -81; after normalizeToNegFirst, negFirst mode also gives -81
        String normalized = MxEvaluator.normalizeToNegFirst("\u22129^2");
        assertEquals(-81.0, evalNegFirst(normalized), 1e-10);
    }

    @Test public void normalizeToStdPreservesDigitEval() throws Exception {
        // −9^2 in negFirst = 81 (preprocessing: (-9)^2); after normalizeToStandard → (−9)^2
        // standard eval of (−9)^2 = 81
        String normalized = MxEvaluator.normalizeToStandard("\u22129^2");
        assertEquals(81.0, eval(normalized), 1e-10);
    }

    @Test public void normalizeNegFirstPreservesParenEval() throws Exception {
        // −(5+3)^2 in standard = -64; after normalizeToNegFirst, negFirst also gives -64
        String normalized = MxEvaluator.normalizeToNegFirst("\u2212(5+3)^2");
        assertEquals(-64.0, evalNegFirst(normalized), 1e-10);
    }

    @Test public void normalizeToStdPreservesParenEval() throws Exception {
        // −(5+3)^2 in negFirst = 64; after normalizeToStandard → (−(5+3))^2, standard gives 64
        String normalized = MxEvaluator.normalizeToStandard("\u2212(5+3)^2");
        assertEquals(64.0, eval(normalized), 1e-10);
    }

    @Test public void normalizeNegFirstPreservesInsideExprDigit() throws Exception {
        // 1+−9^2+1 in standard: 1 + -(9^2) + 1 = -79
        String expr = "1+\u22129^2+1";
        String normalized = MxEvaluator.normalizeToNegFirst(expr);
        assertEquals(eval(expr), evalNegFirst(normalized), 1e-10);
    }

    @Test public void normalizeNegFirstPreservesInsideExprParen() throws Exception {
        // 2+−(5+3)^2+1 in standard: 2 + -(64) + 1 = -61
        String expr = "2+\u2212(5+3)^2+1";
        String normalized = MxEvaluator.normalizeToNegFirst(expr);
        assertEquals(eval(expr), evalNegFirst(normalized), 1e-10);
    }

    @Test public void normalizeNegFirstPreservesFunctionEval() throws Exception {
        // −sin(π/2)^2 in standard = -1; after normalizeToNegFirst, negFirst also gives -1
        String expr = "\u2212sin(pi/2)^2";
        String normalized = MxEvaluator.normalizeToNegFirst(expr);
        assertEquals(eval(expr), evalNegFirst(normalized), 1e-10);
    }

    @Test public void normalizeToStdPreservesFunctionEval() throws Exception {
        // −sin(π/2)^2 in negFirst = 1; after normalizeToStandard, standard also gives 1
        String expr = "\u2212sin(pi/2)^2";
        String normalized = MxEvaluator.normalizeToStandard(expr);
        assertEquals(evalNegFirst(expr), eval(normalized), 1e-10);
    }

    // ── Error cases ──────────────────────────────────────────────────────────

    @Test(expected = Exception.class) public void divisionByZero() throws Exception {
        eval("5/0");
    }

    @Test(expected = Exception.class) public void moduloByZero() throws Exception {
        eval("mod(5,0)");
    }

    @Test(expected = Exception.class) public void sqrtOfNegative() throws Exception {
        eval("sqrt(-1)");
    }

    @Test(expected = Exception.class) public void lnOfZero() throws Exception {
        eval("ln(0)");
    }

    @Test(expected = Exception.class) public void lnOfNegative() throws Exception {
        eval("ln(-1)");
    }

    @Test(expected = Exception.class) public void logOfZero() throws Exception {
        eval("log(0)");
    }

    @Test(expected = Exception.class) public void undefinedVariable() throws Exception {
        eval("A");
    }

    @Test(expected = Exception.class) public void unknownIdentifier() throws Exception {
        eval("foo");
    }

    @Test(expected = Exception.class) public void invalidCharacter() throws Exception {
        eval("5$3");
    }

    @Test(expected = Exception.class) public void unexpectedToken() throws Exception {
        eval("5+(");
    }

    @Test(expected = Exception.class) public void emptyExpression() throws Exception {
        eval("");
    }

    @Test(expected = Exception.class) public void unmatchedCloseParen() throws Exception {
        eval("5+3)");
    }

    @Test(expected = Exception.class) public void asinOutOfDomain() throws Exception {
        eval("asin(2)");
    }

    @Test(expected = Exception.class) public void log2OfZero() throws Exception {
        eval("log2(0)");
    }

    @Test(expected = Exception.class) public void nthrtEvenRootOfNegative() throws Exception {
        eval("nthrt(2,-4)");
    }
}
