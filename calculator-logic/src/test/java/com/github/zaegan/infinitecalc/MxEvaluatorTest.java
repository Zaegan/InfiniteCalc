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

    // ── Preprocessing helpers (package-visible, tested directly) ─────────────

    @Test public void casioUnaryWrapsDigits() {
        assertEquals("(-10)^2", MxEvaluator.applyCasioUnary("-10^2"));
    }

    @Test public void casioUnaryAfterOperator() {
        assertEquals("2+(-10)^2", MxEvaluator.applyCasioUnary("2+-10^2"));
    }

    @Test public void casioUnaryAlreadyGroupedUnchanged() {
        assertEquals("(-10)^2", MxEvaluator.applyCasioUnary("(-10)^2"));
    }

    @Test public void casioUnaryDoesNotWrapInsideParens() {
        // -5 inside abs( — prev is '(' so no wrapping
        assertEquals("abs(-5)", MxEvaluator.applyCasioUnary("abs(-5)"));
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
        // Casio mode: -10^2 → (-10)^2 = 100, not -(10^2) = -100
        assertEquals(100.0, eval("-10^2"), 1e-10);
    }

    @Test public void casioUnaryInExpression() throws Exception {
        assertEquals(102.0, eval("2+-10^2"), 1e-10); // 2 + (-10)^2 = 2+100
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
