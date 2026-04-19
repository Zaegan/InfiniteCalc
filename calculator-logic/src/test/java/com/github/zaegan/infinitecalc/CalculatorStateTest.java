package com.github.zaegan.infinitecalc;

import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

public class CalculatorStateTest {

    private CalculatorState s;

    @Before
    public void setUp() {
        s = new CalculatorState();
    }

    // ── Initial state ────────────────────────────────────────────────────────

    @Test public void initiallyEmpty() {
        assertEquals("", s.getExpression());
        assertEquals(0, s.getCursor());
    }

    // ── insert ───────────────────────────────────────────────────────────────

    @Test public void insertDigit() {
        s.insert("3");
        assertEquals("3", s.getExpression());
        assertEquals(1, s.getCursor());
    }

    @Test public void insertMultipleChars() {
        s.insert("42");
        assertEquals("42", s.getExpression());
        assertEquals(2, s.getCursor());
    }

    @Test public void insertAtCursorMiddle() {
        s.insert("13");
        s.syncCursor(1);
        s.insert("2");
        assertEquals("123", s.getExpression());
        assertEquals(2, s.getCursor());
    }

    @Test public void insertOperator() {
        s.insert("5");
        s.insert("+");
        assertEquals("5+", s.getExpression());
    }

    @Test public void insertFunctionToken() {
        s.insert("sin(");
        assertEquals("sin(", s.getExpression());
        assertEquals(4, s.getCursor());
    }

    @Test public void implicitMultiplyBeforeFunction() {
        s.insert("2");
        s.insert("sin(");
        assertEquals("2×sin(", s.getExpression());
        assertEquals(6, s.getCursor());
    }

    @Test public void implicitMultiplyBeforePi() {
        s.insert("3");
        s.insert("π");
        assertEquals("3×π", s.getExpression());
    }

    @Test public void implicitMultiplyBeforeVariable() {
        s.insert("4");
        s.insert("A");
        assertEquals("4×A", s.getExpression());
    }

    @Test public void implicitMultiplyAfterCloseParen() {
        s.insert("(2)");
        s.insert("sin(");
        assertEquals("(2)×sin(", s.getExpression());
    }

    @Test public void noImplicitMultiplyAfterOperator() {
        s.insert("2+");
        s.insert("sin(");
        assertEquals("2+sin(", s.getExpression());
    }

    @Test public void noImplicitMultiplyAtStart() {
        s.insert("sin(");
        assertEquals("sin(", s.getExpression());
    }

    @Test public void noImplicitMultiplyEAfterOperator() {
        s.insert("1+");
        s.insert("e");
        assertEquals("1+e", s.getExpression());
    }

    // ── backspace ────────────────────────────────────────────────────────────

    @Test public void backspaceSingleChar() {
        s.insert("42");
        s.backspace();
        assertEquals("4", s.getExpression());
        assertEquals(1, s.getCursor());
    }

    @Test public void backspaceAtStartIsNoop() {
        s.insert("5");
        s.syncCursor(0);
        s.backspace();
        assertEquals("5", s.getExpression());
        assertEquals(0, s.getCursor());
    }

    @Test public void backspaceOnEmptyIsNoop() {
        s.backspace();
        assertEquals("", s.getExpression());
        assertEquals(0, s.getCursor());
    }

    @Test public void backspaceDeletesSinToken() {
        s.insert("sin(");
        s.backspace();
        assertEquals("", s.getExpression());
        assertEquals(0, s.getCursor());
    }

    @Test public void backspaceDeletesCosToken() {
        s.insert("cos(");
        s.backspace();
        assertEquals("", s.getExpression());
    }

    @Test public void backspaceDeletesTanToken() {
        s.insert("tan(");
        s.backspace();
        assertEquals("", s.getExpression());
    }

    @Test public void backspaceDeletesLnToken() {
        s.insert("ln(");
        s.backspace();
        assertEquals("", s.getExpression());
    }

    @Test public void backspaceDeletesLogToken() {
        s.insert("log(");
        s.backspace();
        assertEquals("", s.getExpression());
    }

    @Test public void backspaceDeletesSqrtToken() {
        s.insert("sqrt(");
        s.backspace();
        assertEquals("", s.getExpression());
    }

    @Test public void backspaceAfterFunctionTokenLeavesPrefix() {
        s.insert("1+sin(");
        s.backspace();
        assertEquals("1+", s.getExpression());
        assertEquals(2, s.getCursor());
    }

    @Test public void backspaceAtMiddleOfExpression() {
        s.insert("123");
        s.syncCursor(2);
        s.backspace();
        assertEquals("13", s.getExpression());
        assertEquals(1, s.getCursor());
    }

    // ── smartParen ───────────────────────────────────────────────────────────

    @Test public void smartParenOpensAtStart() {
        s.smartParen();
        assertEquals("(", s.getExpression());
        assertEquals(1, s.getCursor());
    }

    @Test public void smartParenOpensAfterOperator() {
        s.insert("2+");
        s.smartParen();
        assertEquals("2+(", s.getExpression());
    }

    @Test public void smartParenClosesWhenUnmatched() {
        s.insert("(2+3");
        s.smartParen();
        assertEquals("(2+3)", s.getExpression());
        assertEquals(5, s.getCursor());
    }

    @Test public void smartParenInsertsMultiplyBeforeOpenAfterDigit() {
        s.insert("2");
        s.smartParen();
        assertEquals("2×(", s.getExpression());
        assertEquals(3, s.getCursor());
    }

    @Test public void smartParenInsertsMultiplyBeforeOpenAfterCloseParen() {
        s.insert("(2)");
        s.smartParen();
        assertEquals("(2)×(", s.getExpression());
    }

    @Test public void smartParenDoesNotCloseWhenPrevIsOpen() {
        // "(" then smartParen: depth=1 but prev='(' → should open another (
        s.insert("(");
        s.smartParen();
        assertEquals("((", s.getExpression());
    }

    @Test public void smartParenMultipleCloses() {
        s.insert("((1+2");
        s.smartParen(); // closes first open: ((1+2)
        assertEquals("((1+2)", s.getExpression());
        s.smartParen(); // closes second open: ((1+2))
        assertEquals("((1+2))", s.getExpression());
    }

    // ── clear / setExpression ────────────────────────────────────────────────

    @Test public void clearResetsState() {
        s.insert("42+7");
        s.clear();
        assertEquals("", s.getExpression());
        assertEquals(0, s.getCursor());
    }

    @Test public void setExpressionReplacesContent() {
        s.insert("old");
        s.setExpression("2+3");
        assertEquals("2+3", s.getExpression());
        assertEquals(3, s.getCursor());
    }

    @Test public void setExpressionNull() {
        s.setExpression(null);
        assertEquals("", s.getExpression());
        assertEquals(0, s.getCursor());
    }

    // ── syncCursor ───────────────────────────────────────────────────────────

    @Test public void syncCursorClampsToLength() {
        s.insert("abc");
        s.syncCursor(100);
        assertEquals(3, s.getCursor());
    }

    @Test public void syncCursorClampsToZero() {
        s.insert("abc");
        s.syncCursor(-5);
        assertEquals(0, s.getCursor());
    }

    // ── formatResult ─────────────────────────────────────────────────────────

    @Test public void formatWholeNumber() {
        assertEquals("7", CalculatorState.formatResult(7.0));
    }

    @Test public void formatNegativeWholeNumber() {
        assertEquals("-3", CalculatorState.formatResult(-3.0));
    }

    @Test public void formatDecimal() {
        assertEquals("2.5", CalculatorState.formatResult(2.5));
    }

    @Test public void formatZero() {
        assertEquals("0", CalculatorState.formatResult(0.0));
    }

    @Test public void formatLargeWholeNumber() {
        // 1e14 is below the 1e15 threshold → shown as long
        assertEquals("100000000000000", CalculatorState.formatResult(1e14));
    }

    @Test public void formatVeryLargeNumberAsDouble() {
        // 1e15 and above → shown in scientific notation (normalised)
        String result = CalculatorState.formatResult(1e15);
        // Verify it round-trips correctly and is in sci notation
        assertTrue(result.contains("e") || result.contains("E"));
        assertEquals(1e15, Double.parseDouble(result), 0.0);
    }

    @Test public void formatInfinity() {
        assertEquals("Infinity", CalculatorState.formatResult(Double.POSITIVE_INFINITY));
    }

    @Test public void formatNaN() {
        assertEquals("NaN", CalculatorState.formatResult(Double.NaN));
    }
}
