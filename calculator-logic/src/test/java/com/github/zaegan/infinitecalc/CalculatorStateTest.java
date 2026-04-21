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

    @Test public void smartParenOpensAfterOperatorWithDepth() {
        // Bug: old code saw depth=2 and closed; new rule: operator → always open
        s.insert("((1+");
        s.smartParen();
        assertEquals("((1+(", s.getExpression());
    }

    @Test public void smartParenOpensAfterUnaryMinusWithDepth() {
        // ((1+− prev is minus (operator) → must open despite depth=2
        s.insert("((1+\u2212");
        s.smartParen();
        assertEquals("((1+\u2212(", s.getExpression());
    }

    @Test public void smartParenOpensAfterOperatorMinusSequence() {
        // +− is an operator sequence (unary minus); paren after it must open
        s.insert("((1+\u2212");
        s.smartParen();
        assertEquals("((1+\u2212(", s.getExpression());
    }

    @Test public void smartParenExhaustsDepthThenMultiplies() {
        // After all opens are closed, next press on a value-ending inserts ×(
        s.insert("((1+2");
        s.smartParen(); // → ((1+2)   depth becomes 1
        s.smartParen(); // → ((1+2))  depth becomes 0
        s.smartParen(); // prev=')' value, depth=0 → ×(
        assertEquals("((1+2))\u00D7(", s.getExpression());
    }

    @Test public void smartParenOpensAfterOpenParenWithDepth() {
        // '(' is not a value → always opens, even with depth > 0
        s.insert("(");
        s.smartParen(); // depth=1 but prev='(' → open
        s.smartParen(); // depth=2, prev='(' → open
        assertEquals("(((", s.getExpression());
    }

    @Test public void smartParenClosesPrecededByCloseParen() {
        // ) is a value; with depth remaining, should close
        s.insert("((1+2)");
        s.smartParen(); // prev=')', depth=1 → close
        assertEquals("((1+2))", s.getExpression());
    }

    // ── minus-toggle (insert − when unary − is before cursor) ───────────────

    @Test public void minusToggleRemovesUnaryMinus() {
        // "−5": cursor at end, inserting − again removes the existing one
        s.insert("\u2212");
        s.insert("5");
        s.syncCursor(1); // cursor right after the '−'
        s.insert("\u2212");
        assertEquals("5", s.getExpression());
        assertEquals(0, s.getCursor());
    }

    @Test public void minusAfterBinaryOperatorStacksNormally() {
        // "5+": inserting − should give "5+−" (binary + unary), no toggle
        s.insert("5");
        s.insert("+");
        s.insert("\u2212");
        assertEquals("5+\u2212", s.getExpression());
    }

    @Test public void minusAtStartTogglesOff() {
        s.insert("\u2212");
        s.insert("\u2212"); // second minus at position 1, prev='−' which is unary → remove
        assertEquals("", s.getExpression());
    }

    // ── smartNegate ──────────────────────────────────────────────────────────

    @Test public void smartNegateAddsMinusBeforeNumber() {
        s.insert("42");
        s.smartNegate();
        assertEquals("\u221242", s.getExpression());
        assertEquals(3, s.getCursor());
    }

    @Test public void smartNegateRemovesExistingUnaryMinus() {
        s.insert("\u2212");
        s.insert("42");
        s.smartNegate(); // cursor at end, prev digits → remove −
        assertEquals("42", s.getExpression());
        assertEquals(2, s.getCursor());
    }

    @Test public void smartNegateCursorInsideNumber() {
        s.insert("42");
        s.syncCursor(1); // cursor between '4' and '2'
        s.smartNegate();
        assertEquals("\u221242", s.getExpression());
    }

    @Test public void smartNegateCursorAtStartOfNumber() {
        s.insert("42");
        s.syncCursor(0);
        s.smartNegate(); // no digits before cursor, scan forward finds "42"
        assertEquals("\u221242", s.getExpression());
    }

    @Test public void smartNegateDoesNothingWithNoCursorDigit() {
        s.insert("2+");
        s.smartNegate(); // cursor at end, after '+', no digits ahead
        assertEquals("2+", s.getExpression());
    }

    @Test public void smartNegateInMiddleOfExpression() {
        s.insert("2+5");
        s.syncCursor(3); // cursor at end, "5" is the number
        s.smartNegate();
        assertEquals("2+\u22125", s.getExpression());
    }

    @Test public void smartNegateRemovesInMiddleOfExpression() {
        s.insert("2+\u22125");
        s.syncCursor(4); // after '5'
        s.smartNegate(); // prev chars: cursor backs over '5', numStart=3, prev='−' unary (after '+')
        assertEquals("2+5", s.getExpression());
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
