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
 * Handles LiveData, SharedPreferences, and history grouping.
 * All pure expression logic lives in CalculatorState (no Android deps, fully unit-testable).
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
    public void syncCursor(int pos) { state.syncCursor(pos); }

    // ── Delegated input ─────────────────────────────────────────────────────
    public void insert(String text) {
        exitVarMode();
        state.insert(text);
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
    public void onEquals() {
        String expression = state.getExpression().trim();
        if (expression.isEmpty()) return;
        try {
            Map<String, Double> vars = loadVariables();
            double result = ExpressionEvaluator.evaluate(expression, vars);
            String resultStr = CalculatorState.formatResult(result);
            if (groupStartTime == 0) groupStartTime = System.currentTimeMillis();
            currentGroupSteps.add(new HistoryItem(expression, resultStr));
            state.setExpression(resultStr);
            previewText.setValue("");
            updateDisplay();
        } catch (Exception e) {
            errorMessage.setValue(e.getMessage() != null ? e.getMessage() : "Error");
        }
    }

    public void onClear() {
        finalizeGroup();
        state.clear();
        previewText.setValue("");
        updateDisplay();
        exitVarMode();
    }

    public void restoreExpression(String expression) {
        finalizeGroup();
        state.setExpression(expression);
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
            double result = ExpressionEvaluator.evaluatePartial(expression, vars);
            previewText.setValue("= " + CalculatorState.formatResult(result));
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
}
