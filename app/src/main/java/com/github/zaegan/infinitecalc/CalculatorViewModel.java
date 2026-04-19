package com.github.zaegan.infinitecalc;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Holds all calculator state. Extends AndroidViewModel for SharedPreferences access.
 *
 * Expression is stored as a StringBuilder; cursor position is tracked explicitly.
 * Button handlers in MainActivity must call syncCursor() before each action so
 * that the ViewModel's cursor stays in sync with the EditText's selection.
 */
public class CalculatorViewModel extends AndroidViewModel {

    public enum VarMode { NONE, STO, REC }

    // ── LiveData ────────────────────────────────────────────────────────────
    private final MutableLiveData<String> expressionText = new MutableLiveData<>("");
    private final MutableLiveData<Integer> cursorPos = new MutableLiveData<>(0);
    private final MutableLiveData<String> previewText = new MutableLiveData<>("");
    private final MutableLiveData<List<HistoryGroup>> history =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> varPanelVisible = new MutableLiveData<>(false);
    private final MutableLiveData<VarMode> varMode = new MutableLiveData<>(VarMode.NONE);

    // ── Mutable state ───────────────────────────────────────────────────────
    private final StringBuilder expr = new StringBuilder();
    private int cursor = 0;
    private final List<HistoryItem> currentGroupSteps = new ArrayList<>();
    private long groupStartTime = 0;

    private static final String PREF_NAME = "calculator_vars";
    private static final String[] VAR_NAMES = {"A", "B", "C", "D", "E", "F", "G", "H"};
    private final SharedPreferences prefs;

    public CalculatorViewModel(Application application) {
        super(application);
        prefs = application.getSharedPreferences(PREF_NAME, 0);
    }

    // ── Getters ─────────────────────────────────────────────────────────────
    public LiveData<String> getExpressionText() { return expressionText; }
    public LiveData<Integer> getCursorPos() { return cursorPos; }
    public LiveData<String> getPreviewText() { return previewText; }
    public LiveData<List<HistoryGroup>> getHistory() { return history; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<Boolean> getVarPanelVisible() { return varPanelVisible; }
    public LiveData<VarMode> getVarMode() { return varMode; }

    public void clearError() { errorMessage.setValue(null); }

    // ── Cursor sync ─────────────────────────────────────────────────────────
    /** Called by MainActivity before every button action to keep cursor in sync. */
    public void syncCursor(int pos) {
        cursor = Math.max(0, Math.min(pos, expr.length()));
    }

    // ── Core input ──────────────────────────────────────────────────────────

    /**
     * Insert text at the current cursor position.
     * Automatically prepends × when inserting a function call, constant, or variable
     * immediately after a digit, closing paren, or one of the named constants.
     */
    public void insert(String text) {
        exitVarMode();
        if (cursor > 0) {
            char prev = expr.charAt(cursor - 1);
            boolean prevIsValue = Character.isDigit(prev) || prev == ')'
                    || prev == 'π' || (prev >= 'A' && prev <= 'H');
            boolean textOpensGroup = text.startsWith("sin(") || text.startsWith("cos(")
                    || text.startsWith("tan(") || text.startsWith("ln(")
                    || text.startsWith("log(") || text.startsWith("sqrt(")
                    || text.startsWith("√(") || text.equals("π") || text.equals("e")
                    || (text.length() == 1 && text.charAt(0) >= 'A' && text.charAt(0) <= 'H');
            if (prevIsValue && textOpensGroup) {
                expr.insert(cursor, "×");
                cursor++;
            }
        }
        expr.insert(cursor, text);
        cursor += text.length();
        updateDisplay();
    }

    /**
     * Delete the token immediately before the cursor.
     * Multi-character function tokens (e.g. "sin(") are deleted as a unit.
     */
    public void backspace() {
        exitVarMode();
        if (cursor == 0) return;
        String[] multiTokens = {"sin(", "cos(", "tan(", "log(", "ln(", "sqrt(", "√("};
        String before = expr.substring(0, cursor);
        for (String token : multiTokens) {
            if (before.endsWith(token)) {
                expr.delete(cursor - token.length(), cursor);
                cursor -= token.length();
                updateDisplay();
                return;
            }
        }
        expr.deleteCharAt(cursor - 1);
        cursor--;
        updateDisplay();
    }

    /**
     * Smart parenthesis:
     *   - Insert ) if there is an unmatched ( before cursor and the preceding char is not (.
     *   - Otherwise insert ( — with an automatic × prefix if the preceding char is a digit or ).
     */
    public void smartParen() {
        exitVarMode();
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
            updateDisplay();
        } else {
            // Open paren — auto-multiply if preceded by a value
            if (prev != 0 && (Character.isDigit(prev) || prev == ')')) {
                expr.insert(cursor, "×(");
                cursor += 2;
            } else {
                expr.insert(cursor, "(");
                cursor++;
            }
            updateDisplay();
        }
    }

    // ── Evaluation ──────────────────────────────────────────────────────────

    /** Evaluate the current expression; add step to history group; put result back. */
    public void onEquals() {
        String expression = expr.toString().trim();
        if (expression.isEmpty()) return;
        try {
            Map<String, Double> vars = loadVariables();
            double result = ExpressionEvaluator.evaluate(expression, vars);
            String resultStr = formatResult(result);
            if (groupStartTime == 0) groupStartTime = System.currentTimeMillis();
            currentGroupSteps.add(new HistoryItem(expression, resultStr));
            expr.setLength(0);
            expr.append(resultStr);
            cursor = expr.length();
            previewText.setValue("");
            updateDisplay();
        } catch (Exception e) {
            errorMessage.setValue(e.getMessage() != null ? e.getMessage() : "Error");
        }
    }

    /** Finalize current history group and clear the expression. */
    public void onClear() {
        finalizeGroup();
        expr.setLength(0);
        cursor = 0;
        previewText.setValue("");
        updateDisplay();
        exitVarMode();
    }

    /** Restore a previous expression without adding it to history yet. */
    public void restoreExpression(String expression) {
        finalizeGroup();
        expr.setLength(0);
        expr.append(expression);
        cursor = expr.length();
        updateDisplay();
    }

    // ── Variable modes ──────────────────────────────────────────────────────

    public void enterStoMode() {
        varMode.setValue(VarMode.STO);
        varPanelVisible.setValue(true);
    }

    public void enterRecMode() {
        varMode.setValue(VarMode.REC);
        varPanelVisible.setValue(true);
    }

    public void onVariableTapped(String varName) {
        VarMode mode = varMode.getValue();
        if (mode == VarMode.STO) {
            try {
                Map<String, Double> vars = loadVariables();
                double val = ExpressionEvaluator.evaluatePartial(expr.toString(), vars);
                prefs.edit().putFloat(varName, (float) val).apply();
            } catch (Exception ignored) {}
            exitVarMode();
        } else if (mode == VarMode.REC) {
            exitVarMode();
            insert(varName);
        }
    }

    // ── Internals ───────────────────────────────────────────────────────────

    private void exitVarMode() {
        if (varMode.getValue() != VarMode.NONE) {
            varMode.setValue(VarMode.NONE);
            varPanelVisible.setValue(false);
        }
    }

    private void finalizeGroup() {
        if (!currentGroupSteps.isEmpty()) {
            List<HistoryGroup> updated = new ArrayList<>(history.getValue());
            updated.add(0, new HistoryGroup(new ArrayList<>(currentGroupSteps),
                    groupStartTime == 0 ? System.currentTimeMillis() : groupStartTime));
            history.setValue(updated);
            currentGroupSteps.clear();
            groupStartTime = 0;
        }
    }

    private void updateDisplay() {
        expressionText.setValue(expr.toString());
        cursorPos.setValue(cursor);
        updatePreview();
    }

    private void updatePreview() {
        String expression = expr.toString().trim();
        if (expression.isEmpty()) {
            previewText.setValue("");
            return;
        }
        try {
            Map<String, Double> vars = loadVariables();
            double result = ExpressionEvaluator.evaluatePartial(expression, vars);
            previewText.setValue("= " + formatResult(result));
        } catch (Exception e) {
            previewText.setValue("");
        }
    }

    private Map<String, Double> loadVariables() {
        Map<String, Double> vars = new HashMap<>();
        for (String name : VAR_NAMES) {
            if (prefs.contains(name)) {
                vars.put(name, (double) prefs.getFloat(name, 0f));
            }
        }
        return vars;
    }

    String formatResult(double result) {
        if (!Double.isInfinite(result) && !Double.isNaN(result)
                && result == Math.floor(result) && Math.abs(result) < 1e15) {
            return String.valueOf((long) result);
        }
        return String.valueOf(result);
    }
}
