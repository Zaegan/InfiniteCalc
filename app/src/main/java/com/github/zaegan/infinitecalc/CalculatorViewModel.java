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
 * Android-facing wrapper around {@link CalculatorState}.
 *
 * History design:
 *   - Each = press immediately creates one HistoryGroup and adds it to history.
 *   - AC also commits any accumulated draft steps as a history entry.
 *   - While the user types, any syntactically complete evaluatable expression is
 *     automatically recorded as a "draft step" in the pending group.
 *   - Auto-iterate: after =, if the expression started with a digit, the leading
 *     number is replaced with the result so = can be pressed repeatedly.
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

    // ── Pure state ──────────────────────────────────────────────────────────
    private final CalculatorState state = new CalculatorState();

    /**
     * Draft steps accumulated for the current in-progress group.
     * Each entry is a fully evaluatable expression the user paused on.
     */
    private final List<HistoryItem> currentDraftSteps = new ArrayList<>();

    private static final String PREF_NAME = "calculator_vars";
    private static final String[] VAR_NAMES = {
        "A", "B", "C", "D", "E", "F", "G", "H",
        "I", "J", "K", "L", "M", "N", "O", "P"
    };
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
    public void syncCursor(int pos) { state.syncCursor(pos); }

    // ── Delegated input ─────────────────────────────────────────────────────
    public void insert(String text) {
        exitVarMode();
        state.insert(text);
        updateDisplay();
    }

    public void insertResult(String result) {
        exitVarMode();
        state.insert(result);
        updateDisplay();
    }

    public void backspace() {
        exitVarMode();
        state.backspace();
        updateDisplay();
    }

    public void smartParen() {
        exitVarMode();
        state.smartParen();
        updateDisplay();
    }

    // ── Evaluation ──────────────────────────────────────────────────────────

    /**
     * Evaluate the current expression.  Immediately creates and commits a
     * HistoryGroup from the accumulated draft steps, then resets.
     *
     * Auto-iterate: if the expression begins with a digit, the leading number
     * token is replaced with the result, so pressing = repeatedly iterates.
     */
    public void onEquals() {
        String expression = state.getExpression().trim();
        if (expression.isEmpty()) return;
        try {
            Map<String, Double> vars = loadVariables();
            double result = ExpressionEvaluator.evaluate(expression, vars);
            String resultStr = CalculatorState.formatResult(result);

            // Ensure the final expression is in the draft list
            addDraftStep(expression, resultStr);

            // Commit the group immediately
            List<HistoryGroup> updated = new ArrayList<>(history.getValue());
            updated.add(0, new HistoryGroup(
                    new ArrayList<>(currentDraftSteps),
                    System.currentTimeMillis()));
            history.setValue(updated);
            currentDraftSteps.clear();

            // Auto-iterate if expression starts with a digit
            String iterated = buildIteratedExpression(expression, resultStr);
            state.setExpression(iterated != null ? iterated : resultStr);
            previewText.setValue("");
            updateDisplay();
        } catch (Exception e) {
            errorMessage.setValue(e.getMessage() != null ? e.getMessage() : "Error");
        }
    }

    /**
     * AC: commit any accumulated draft steps as a history entry, then clear.
     */
    public void onClear() {
        if (!currentDraftSteps.isEmpty()) {
            List<HistoryGroup> updated = new ArrayList<>(history.getValue());
            updated.add(0, new HistoryGroup(
                    new ArrayList<>(currentDraftSteps),
                    System.currentTimeMillis()));
            history.setValue(updated);
        }
        currentDraftSteps.clear();
        state.clear();
        previewText.setValue("");
        updateDisplay();
        exitVarMode();
    }

    /** Load a previous expression into the input field, discarding current drafts. */
    public void restoreExpression(String expression) {
        exitVarMode();
        currentDraftSteps.clear();
        state.setExpression(expression);
        updateDisplay();
    }

    // ── Variable modes ──────────────────────────────────────────────────────

    /** Toggle STO mode: enter if not in STO mode, exit if already in STO mode. */
    public void enterStoMode() {
        if (varMode.getValue() == VarMode.STO) {
            exitVarMode();
        } else {
            varMode.setValue(VarMode.STO);
            varPanelVisible.setValue(true);
        }
    }

    /** Toggle REC mode: enter/switch if not in REC mode, exit if already in REC mode. */
    public void enterRecMode() {
        if (varMode.getValue() == VarMode.REC) {
            exitVarMode();
        } else {
            varMode.setValue(VarMode.REC);
            varPanelVisible.setValue(true);
        }
    }

    public void onVariableTapped(String varName) {
        VarMode mode = varMode.getValue();
        if (mode == VarMode.STO) {
            try {
                Map<String, Double> vars = loadVariables();
                double val = ExpressionEvaluator.evaluatePartial(state.getExpression(), vars);
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

    /**
     * If the expression begins with a digit, replace the leading number token
     * with resultStr so that pressing = again iterates the computation.
     * Returns null if the expression does not start with a digit, or is a bare number.
     */
    private static String buildIteratedExpression(String expression, String resultStr) {
        if (expression.isEmpty() || !Character.isDigit(expression.charAt(0))) return null;
        int i = 0;
        while (i < expression.length()
                && (Character.isDigit(expression.charAt(i)) || expression.charAt(i) == '.')) {
            i++;
        }
        if (i >= expression.length()) return null; // expression is just a bare number
        return resultStr + expression.substring(i);
    }

    /**
     * Add a draft step if the expression is syntactically complete (full evaluate
     * succeeds), contains at least one operator (suppresses bare-digit noise),
     * and hasn't already been recorded.
     */
    private void addDraftStep(String expression, String result) {
        if (!hasOperatorOrFunction(expression)) return;
        if (!currentDraftSteps.isEmpty()
                && currentDraftSteps.get(currentDraftSteps.size() - 1)
                        .getExpression().equals(expression)) return;
        currentDraftSteps.add(new HistoryItem(expression, result));
    }

    private static boolean hasOperatorOrFunction(String expr) {
        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (c == '+' || c == '\u2212' || c == '\u00D7' || c == '\u00F7'
                    || c == '^' || c == '%' || c == '(') return true;
        }
        return false;
    }

    private void updateDisplay() {
        expressionText.setValue(state.getExpression());
        cursorPos.setValue(state.getCursor());
        updatePreview();
    }

    private void updatePreview() {
        String expression = state.getExpression().trim();
        if (expression.isEmpty()) {
            previewText.setValue("");
            return;
        }
        try {
            Map<String, Double> vars = loadVariables();
            double partialResult = ExpressionEvaluator.evaluatePartial(expression, vars);
            String resultStr = CalculatorState.formatResult(partialResult);
            previewText.setValue("= " + resultStr);

            // Auto-record as a draft step only when the expression is fully valid
            try {
                ExpressionEvaluator.evaluate(expression, vars);
                addDraftStep(expression, resultStr);
            } catch (Exception ignored) { /* incomplete expression — no draft */ }

        } catch (Exception e) {
            previewText.setValue("");
        }
    }

    /** All variables default to 0 if not yet stored. */
    private Map<String, Double> loadVariables() {
        Map<String, Double> vars = new HashMap<>();
        for (String name : VAR_NAMES) {
            vars.put(name, (double) prefs.getFloat(name, 0f));
        }
        return vars;
    }
}
