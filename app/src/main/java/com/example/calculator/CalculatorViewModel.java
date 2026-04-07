package com.example.calculator;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds all calculator state and logic. No Android UI dependencies — fully unit-testable
 * with plain JUnit and androidx.arch.core:core-testing.
 */
public class CalculatorViewModel extends ViewModel {

    private final MutableLiveData<String> displayText = new MutableLiveData<>("");
    private final MutableLiveData<List<HistoryItem>> history = new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();

    private final StringBuilder currentExpression = new StringBuilder();
    // True after = is pressed: next digit starts a fresh expression instead of appending
    private boolean shouldClearOnNextDigit = false;

    public LiveData<String> getDisplayText() { return displayText; }
    public LiveData<List<HistoryItem>> getHistory() { return history; }
    public LiveData<String> getErrorMessage() { return errorMessage; }

    /** Call after observing an error to prevent it re-firing on config change. */
    public void clearError() {
        errorMessage.setValue(null);
    }

    public void onNumberClick(int num) {
        if (shouldClearOnNextDigit) {
            currentExpression.setLength(0);
            shouldClearOnNextDigit = false;
        }
        currentExpression.append(num);
        updateDisplay();
    }

    public void onOperatorClick(String op) {
        if (currentExpression.length() == 0) return;
        shouldClearOnNextDigit = false;
        char last = currentExpression.charAt(currentExpression.length() - 1);
        if (last == '+' || last == '-' || last == '*' || last == '/' || last == '%') {
            currentExpression.setLength(currentExpression.length() - 1);
        }
        currentExpression.append(op);
        updateDisplay();
    }

    public void onDecimalClick() {
        if (shouldClearOnNextDigit) {
            currentExpression.setLength(0);
            currentExpression.append("0.");
            shouldClearOnNextDigit = false;
            updateDisplay();
            return;
        }
        // Find where the current number token starts
        String expr = currentExpression.toString();
        int lastOpIdx = -1;
        for (int i = expr.length() - 1; i >= 0; i--) {
            char c = expr.charAt(i);
            if (c == '+' || c == '*' || c == '/' || c == '%') {
                lastOpIdx = i;
                break;
            }
            // Treat '-' as operator only if preceded by a digit (not a leading/unary minus)
            if (c == '-' && i > 0 && Character.isDigit(expr.charAt(i - 1))) {
                lastOpIdx = i;
                break;
            }
        }
        String currentNum = expr.substring(lastOpIdx + 1);
        if (!currentNum.contains(".")) {
            if (currentNum.isEmpty()) currentExpression.append("0");
            currentExpression.append(".");
        }
        updateDisplay();
    }

    public void onEqualClick() {
        String expression = currentExpression.toString().trim();
        if (expression.isEmpty()) {
            errorMessage.setValue("Enter an expression first");
            return;
        }
        if (endsWithOperator(expression)) {
            errorMessage.setValue("Expression incomplete");
            return;
        }
        try {
            double result = ExpressionEvaluator.evaluate(expression);
            String resultStr = formatResult(result);

            List<HistoryItem> updated = new ArrayList<>(history.getValue());
            updated.add(new HistoryItem(expression, resultStr, System.currentTimeMillis()));
            history.setValue(updated);

            currentExpression.setLength(0);
            currentExpression.append(resultStr);
            shouldClearOnNextDigit = true;
            updateDisplay();
        } catch (Exception e) {
            errorMessage.setValue("Invalid expression");
            currentExpression.setLength(0);
            shouldClearOnNextDigit = false;
            updateDisplay();
        }
    }

    public void onClearClick() {
        String current = currentExpression.toString();
        if (!current.isEmpty()) {
            List<HistoryItem> updated = new ArrayList<>(history.getValue());
            updated.add(new HistoryItem(current, "—", System.currentTimeMillis()));
            history.setValue(updated);
        }
        currentExpression.setLength(0);
        shouldClearOnNextDigit = false;
        updateDisplay();
    }

    private void updateDisplay() {
        displayText.setValue(currentExpression.toString());
    }

    boolean endsWithOperator(String expression) {
        char last = expression.charAt(expression.length() - 1);
        return last == '+' || last == '-' || last == '*' || last == '/' || last == '%';
    }

    String formatResult(double result) {
        if (!Double.isInfinite(result) && !Double.isNaN(result)
                && result == Math.floor(result) && Math.abs(result) < 1e15) {
            return String.valueOf((long) result);
        }
        return String.valueOf(result);
    }
}
