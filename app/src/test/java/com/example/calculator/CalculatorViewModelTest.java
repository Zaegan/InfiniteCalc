package com.example.calculator;

import androidx.arch.core.executor.testing.InstantTaskExecutorRule;

import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class CalculatorViewModelTest {

    @Rule
    public InstantTaskExecutorRule rule = new InstantTaskExecutorRule();

    private CalculatorViewModel vm;

    @Before
    public void setup() {
        vm = new CalculatorViewModel();
    }

    @Test
    public void addition() {
        vm.onNumberClick(5);
        vm.onOperatorClick("+");
        vm.onNumberClick(3);
        vm.onEqualClick();
        assertEquals("8", vm.getDisplayText().getValue());
        assertEquals(1, vm.getHistory().getValue().size());
    }

    @Test
    public void subtraction() {
        vm.onNumberClick(9);
        vm.onOperatorClick("-");
        vm.onNumberClick(4);
        vm.onEqualClick();
        assertEquals("5", vm.getDisplayText().getValue());
    }

    @Test
    public void multiplication() {
        vm.onNumberClick(6);
        vm.onOperatorClick("*");
        vm.onNumberClick(7);
        vm.onEqualClick();
        assertEquals("42", vm.getDisplayText().getValue());
    }

    @Test
    public void division() {
        vm.onNumberClick(1);
        vm.onNumberClick(0);
        vm.onOperatorClick("/");
        vm.onNumberClick(4);
        vm.onEqualClick();
        assertEquals("2.5", vm.getDisplayText().getValue());
    }

    @Test
    public void operatorPrecedence() {
        // 2 + 3 * 4 should be 14, not 20
        vm.onNumberClick(2);
        vm.onOperatorClick("+");
        vm.onNumberClick(3);
        vm.onOperatorClick("*");
        vm.onNumberClick(4);
        vm.onEqualClick();
        assertEquals("14", vm.getDisplayText().getValue());
    }

    @Test
    public void divisionByZeroShowsError() {
        vm.onNumberClick(5);
        vm.onOperatorClick("/");
        vm.onNumberClick(0);
        vm.onEqualClick();
        assertNotNull(vm.getErrorMessage().getValue());
    }

    @Test
    public void emptyExpressionShowsError() {
        vm.onEqualClick();
        assertNotNull(vm.getErrorMessage().getValue());
    }

    @Test
    public void incompleteExpressionShowsError() {
        vm.onNumberClick(5);
        vm.onOperatorClick("+");
        vm.onEqualClick();
        assertNotNull(vm.getErrorMessage().getValue());
    }

    @Test
    public void clearSavesToHistory() {
        vm.onNumberClick(3);
        vm.onNumberClick(7);
        vm.onClearClick();
        List<HistoryItem> history = vm.getHistory().getValue();
        assertEquals(1, history.size());
        assertEquals("37", history.get(0).getExpression());
        assertEquals("—", history.get(0).getResult());
        assertEquals("", vm.getDisplayText().getValue());
    }

    @Test
    public void clearOnEmptyDoesNotSaveToHistory() {
        vm.onClearClick();
        assertEquals(0, vm.getHistory().getValue().size());
    }

    @Test
    public void nextDigitAfterEqualsStartsFresh() {
        vm.onNumberClick(5);
        vm.onEqualClick();
        vm.onNumberClick(3);
        assertEquals("3", vm.getDisplayText().getValue());
    }

    @Test
    public void chainedOperationsAfterEquals() {
        // 5 = 5, then + 3 = 8
        vm.onNumberClick(5);
        vm.onEqualClick();
        vm.onOperatorClick("+");
        vm.onNumberClick(3);
        vm.onEqualClick();
        assertEquals("8", vm.getDisplayText().getValue());
    }

    @Test
    public void consecutiveOperatorReplacesLast() {
        vm.onNumberClick(5);
        vm.onOperatorClick("+");
        vm.onOperatorClick("-");
        assertEquals("5-", vm.getDisplayText().getValue());
    }

    @Test
    public void decimalInput() {
        vm.onNumberClick(1);
        vm.onDecimalClick();
        vm.onNumberClick(5);
        vm.onEqualClick();
        // 1.5 with no operator just evaluates to 1.5
        assertEquals("1.5", vm.getDisplayText().getValue());
    }

    @Test
    public void doubleDecimalIgnored() {
        vm.onNumberClick(1);
        vm.onDecimalClick();
        vm.onDecimalClick(); // second decimal should be ignored
        vm.onNumberClick(5);
        assertEquals("1.5", vm.getDisplayText().getValue());
    }

    @Test
    public void historyEntryContainsExpressionAndResult() {
        vm.onNumberClick(3);
        vm.onOperatorClick("+");
        vm.onNumberClick(4);
        vm.onEqualClick();
        HistoryItem entry = vm.getHistory().getValue().get(0);
        assertEquals("3+4", entry.getExpression());
        assertEquals("7", entry.getResult());
    }

    @Test
    public void clearErrorResetsErrorMessage() {
        vm.onEqualClick(); // triggers error
        assertNotNull(vm.getErrorMessage().getValue());
        vm.clearError();
        assertNull(vm.getErrorMessage().getValue());
    }
}
