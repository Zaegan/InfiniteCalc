package com.github.zaegan.infinitecalc;

import org.junit.Test;

import java.util.HashMap;
import java.util.Map;

import static org.junit.Assert.*;

public class ExpressionEvaluatorTest {

    // ── Helpers ──────────────────────────────────────────────────────────────

    private double eval(String expr) throws Exception {
        return ExpressionEvaluator.evaluate(expr, null);
    }

    private double evalVars(String expr, Map<String, Double> vars) throws Exception {
        return ExpressionEvaluator.evaluate(expr, vars);
    }

    private double partial(String expr) throws Exception {
        return ExpressionEvaluator.evaluatePartial(expr, null);
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
        assertEquals(1.0, eval("10%3"), 1e-10);
    }

    // ── Precedence and associativity ─────────────────────────────────────────

    @Test public void addThenMultiply() throws Exception {
        // 2 + 3 * 4 = 14, not 20
        assertEquals(14.0, eval("2+3*4"), 1e-10);
    }

    @Test public void multiplyThenAdd() throws Exception {
        assertEquals(10.0, eval("2*3+4"), 1e-10);
    }

    @Test public void powerRightAssociative() throws Exception {
        // 2^3^2 should be 2^(3^2) = 2^9 = 512
        assertEquals(512.0, eval("2^3^2"), 1e-10);
    }

    @Test public void powerBeforeMultiply() throws Exception {
        assertEquals(18.0, eval("2*3^2"), 1e-10);
    }

    // ── Unary operators ──────────────────────────────────────────────────────

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
        for (char c = 'A'; c <= 'H'; c++) vars.put(String.valueOf(c), (double)(c - 'A' + 1));
        // A=1, B=2, ..., H=8
        assertEquals(1.0, evalVars("A", vars), 1e-10);
        assertEquals(8.0, evalVars("H", vars), 1e-10);
    }

    // ── Partial evaluation ───────────────────────────────────────────────────

    @Test public void partialAutoClosesOneParen() throws Exception {
        // "sin(1" → auto-closes to "sin(1)" = sin(1)
        assertEquals(Math.sin(1), partial("sin(1"), 1e-10);
    }

    @Test public void partialAutoClosesNestedParens() throws Exception {
        // "(1+2" → "(1+2)" = 3
        assertEquals(3.0, partial("(1+2"), 1e-10);
    }

    @Test public void partialCompleteExpressionUnchanged() throws Exception {
        assertEquals(6.0, partial("2*3"), 1e-10);
    }

    @Test public void isValidPartialRejectsUnmatchedClose() {
        assertFalse(ExpressionEvaluator.isValidPartial("2+3)", null));
    }

    @Test public void isValidPartialAcceptsGoodExpr() {
        assertTrue(ExpressionEvaluator.isValidPartial("2+3", null));
    }

    @Test public void isValidPartialAcceptsOpenParen() {
        assertTrue(ExpressionEvaluator.isValidPartial("sin(1", null));
    }

    // ── Error cases ──────────────────────────────────────────────────────────

    @Test(expected = Exception.class) public void divisionByZero() throws Exception {
        eval("5/0");
    }

    @Test(expected = Exception.class) public void moduloByZero() throws Exception {
        eval("5%0");
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
        eval("5 3");
    }

    @Test(expected = Exception.class) public void emptyExpression() throws Exception {
        eval("");
    }

    @Test(expected = Exception.class) public void unmatchedCloseParen() throws Exception {
        eval("5+3)");
    }
}
