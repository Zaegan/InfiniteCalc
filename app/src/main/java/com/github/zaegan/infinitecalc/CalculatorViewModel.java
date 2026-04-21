package com.github.zaegan.infinitecalc;

import android.app.Application;
import android.content.SharedPreferences;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Android-facing wrapper around {@link CalculatorState}.
 *
 * History is stored persistently in a Room database and survives app restarts.
 * Each group is saved immediately after being committed (= or AC).
 * Date-separator items are injected between groups when the date changes.
 */
public class CalculatorViewModel extends AndroidViewModel {

    public enum VarMode { NONE, STO, REC }

    // ── LiveData ────────────────────────────────────────────────────────────
    private final MutableLiveData<String> expressionText = new MutableLiveData<>("");
    private final MutableLiveData<Integer> cursorPos = new MutableLiveData<>(0);
    private final MutableLiveData<String> previewText = new MutableLiveData<>("");
    /** Adapter-ready list: HistoryGroup items interleaved with DateSeparator items. */
    private final MutableLiveData<List<HistoryListItem>> history =
            new MutableLiveData<>(new ArrayList<>());
    private final MutableLiveData<String> errorMessage = new MutableLiveData<>();
    private final MutableLiveData<Boolean> varPanelVisible = new MutableLiveData<>(false);
    private final MutableLiveData<VarMode> varMode = new MutableLiveData<>(VarMode.NONE);
    private final MutableLiveData<Boolean> radianMode = new MutableLiveData<>(false);
    private boolean useRadians = false;

    // ── In-memory ordered list of raw groups (oldest first) ─────────────────
    private final List<HistoryGroup> rawGroups = new ArrayList<>();

    // ── Pure state ──────────────────────────────────────────────────────────
    private final CalculatorState state = new CalculatorState();

    private final List<HistoryItem> currentDraftSteps = new ArrayList<>();

    // ── Iteration state ──────────────────────────────────────────────────────
    /**
     * Suffix of the last evaluated expression after its leading number
     * (e.g. "+5" from "3+5"). Non-null only when lastActionWasEquals=true
     * and the expression started with a digit.
     */
    private String iterationTemplate = null;
    private boolean lastActionWasEquals = false;
    /** The group that successive = presses append steps to. */
    private HistoryGroup iterationGroup = null;

    // ── Persistence ─────────────────────────────────────────────────────────
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    private static final String PREF_NAME = "calculator_vars";
    private static final String[] VAR_NAMES = {
        "A", "B", "C", "D", "E", "F", "G", "H",
        "I", "J", "K", "L", "M", "N", "O", "P",
        "Q", "R", "S", "T", "U", "V", "W", "X",
        "Y", "Z", "\u03B1", "\u03B2"
    };
    private final SharedPreferences prefs;

    public CalculatorViewModel(Application application) {
        super(application);
        prefs = application.getSharedPreferences(PREF_NAME, 0);
        loadHistoryFromDb();
    }

    // ── Getters ─────────────────────────────────────────────────────────────
    public LiveData<String> getExpressionText() { return expressionText; }
    public LiveData<Integer> getCursorPos() { return cursorPos; }
    public LiveData<String> getPreviewText() { return previewText; }
    public LiveData<List<HistoryListItem>> getHistory() { return history; }
    public LiveData<String> getErrorMessage() { return errorMessage; }
    public LiveData<Boolean> getVarPanelVisible() { return varPanelVisible; }
    public LiveData<VarMode> getVarMode() { return varMode; }
    public LiveData<Boolean> getRadianMode() { return radianMode; }

    public void toggleAngleMode() {
        useRadians = !useRadians;
        radianMode.setValue(useRadians);
        updatePreview(); // refresh live preview with new angle unit
    }

    public void clearError() { errorMessage.setValue(null); }

    // ── Cursor sync ─────────────────────────────────────────────────────────
    public void syncCursor(int pos) { state.syncCursor(pos); }

    // ── Delegated input ─────────────────────────────────────────────────────
    public void insert(String text) {
        exitVarMode();
        resetIterationState();
        state.insert(text);
        updateDisplay();
    }

    /** Insert a result string at the cursor (used when tapping a result in history). */
    public void insertResult(String result) {
        exitVarMode();
        resetIterationState();
        state.insert(result);
        updateDisplay();
    }

    public void backspace() {
        exitVarMode();
        resetIterationState();
        state.backspace();
        updateDisplay();
    }

    public void smartParen() {
        exitVarMode();
        resetIterationState();
        state.smartParen();
        updateDisplay();
    }

    public void smartNegate() {
        exitVarMode();
        resetIterationState();
        state.smartNegate();
        updateDisplay();
    }

    // ── Evaluation ──────────────────────────────────────────────────────────

    /**
     * Evaluate the current expression.
     *
     * First press: evaluates, commits a HistoryGroup, clears the expression to
     * just the result. Stores an iteration template if the expression started
     * with a digit (e.g. "3+5" → template="+5").
     *
     * Consecutive press (no intervening edits): if a template exists, prepends
     * the current result to the template ("8+5"), evaluates, appends the new
     * step to the same group (accordion entry), updates history in-place.
     */
    public void onEquals() {
        String expression = state.getExpression().trim();
        if (expression.isEmpty()) return;

        if (lastActionWasEquals && iterationTemplate != null) {
            // ── Iteration: reconstruct expression from current result + template ──
            String currentResult = expression; // state holds the result from last =
            String newExpr = currentResult + iterationTemplate;
            try {
                Map<String, Double> vars = loadVariables();
                double result = MxEvaluator.evaluate(newExpr, vars, useRadians);
                String resultStr = CalculatorState.formatResult(result);

                // Build a NEW HistoryGroup (same timestamp) with the added step so that
                // DiffUtil sees two distinct objects and triggers a rebind. Mutating the
                // existing group in-place causes DiffUtil to compare the same object
                // against itself and conclude nothing changed.
                List<HistoryItem> newSteps = new ArrayList<>(iterationGroup.getSteps());
                newSteps.add(new HistoryItem(newExpr, resultStr));
                HistoryGroup newGroup = new HistoryGroup(newSteps, iterationGroup.getTimestamp());
                newGroup.setExpanded(iterationGroup.isExpanded());
                int groupIndex = rawGroups.indexOf(iterationGroup);
                rawGroups.set(groupIndex, newGroup);
                iterationGroup = newGroup;

                saveStepToDb(newGroup.getTimestamp(), newSteps.size() - 1, newExpr, resultStr);
                history.setValue(buildListItems(rawGroups));

                state.setExpression(resultStr);
                previewText.setValue("");
                updateDisplay();
                // lastActionWasEquals stays true, iterationTemplate unchanged
            } catch (Exception e) {
                errorMessage.setValue(e.getMessage() != null ? e.getMessage() : "Error");
            }
        } else {
            // ── First = press (or after an edit) ──
            try {
                Map<String, Double> vars = loadVariables();
                double result = MxEvaluator.evaluate(expression, vars, useRadians);
                String resultStr = CalculatorState.formatResult(result);

                addDraftStep(expression, resultStr);
                // Ensure plain numbers and bare variables always produce a history entry.
                if (currentDraftSteps.isEmpty()) {
                    currentDraftSteps.add(new HistoryItem(expression, resultStr));
                }
                commitGroup();

                iterationTemplate = extractIterationTemplate(expression);
                iterationGroup = iterationTemplate != null
                        ? rawGroups.get(rawGroups.size() - 1) : null;
                lastActionWasEquals = true;

                state.setExpression(resultStr);
                previewText.setValue("");
                updateDisplay();
            } catch (Exception e) {
                errorMessage.setValue(e.getMessage() != null ? e.getMessage() : "Error");
            }
        }
    }

    /** AC: commit any draft steps as a history entry, then clear. */
    public void onClear() {
        resetIterationState();
        if (!currentDraftSteps.isEmpty()) {
            commitGroup();
        }
        currentDraftSteps.clear();
        state.clear();
        previewText.setValue("");
        updateDisplay();
        exitVarMode();
    }

    /** Load a previous expression into the input field. */
    public void restoreExpression(String expression) {
        exitVarMode();
        resetIterationState();
        currentDraftSteps.clear();
        state.setExpression(expression);
        updateDisplay();
    }

    // ── Variable modes ──────────────────────────────────────────────────────

    public void enterStoMode() {
        if (varMode.getValue() == VarMode.STO) {
            exitVarMode();
        } else {
            varMode.setValue(VarMode.STO);
            varPanelVisible.setValue(true);
        }
    }

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
                double val = MxEvaluator.evaluatePartial(state.getExpression(), vars, useRadians);
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

    private void resetIterationState() {
        lastActionWasEquals = false;
        iterationTemplate = null;
        iterationGroup = null;
    }

    /**
     * Commit currentDraftSteps as a new HistoryGroup (oldest-last position = end of list).
     * Also persists to the database.
     */
    private void commitGroup() {
        HistoryGroup group = new HistoryGroup(
                new ArrayList<>(currentDraftSteps),
                System.currentTimeMillis());
        currentDraftSteps.clear();
        rawGroups.add(group);            // append (oldest first → newest last)
        history.setValue(buildListItems(rawGroups));
        saveGroupToDb(group);
    }

    /**
     * Extract the iteration template from an expression.
     * Returns the substring after the leading number, or null if the expression
     * doesn't start with a digit or consists only of a number.
     * E.g. "3+5" → "+5", "3" → null, "sin(3)" → null.
     */
    private static String extractIterationTemplate(String expression) {
        if (expression.isEmpty() || !Character.isDigit(expression.charAt(0))) return null;
        int i = 0;
        while (i < expression.length()
                && (Character.isDigit(expression.charAt(i)) || expression.charAt(i) == '.')) {
            i++;
        }
        if (i >= expression.length()) return null;
        return expression.substring(i);
    }

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
            double partialResult = MxEvaluator.evaluatePartial(expression, vars, useRadians);
            String resultStr = CalculatorState.formatResult(partialResult);
            previewText.setValue("= " + resultStr);

            try {
                MxEvaluator.evaluate(expression, vars, useRadians);
                addDraftStep(expression, resultStr);
            } catch (Exception ignored) {}

        } catch (Exception e) {
            previewText.setValue("");
        }
    }

    private Map<String, Double> loadVariables() {
        Map<String, Double> vars = new HashMap<>();
        for (String name : VAR_NAMES) {
            vars.put(name, (double) prefs.getFloat(name, 0f));
        }
        return vars;
    }

    // ── Persistence ─────────────────────────────────────────────────────────

    private void loadHistoryFromDb() {
        dbExecutor.execute(() -> {
            List<HistoryGroup> groups =
                    HistoryDatabase.getInstance(getApplication()).loadAll();
            rawGroups.clear();
            rawGroups.addAll(groups);
            history.postValue(buildListItems(rawGroups));
        });
    }

    private void saveGroupToDb(HistoryGroup group) {
        dbExecutor.execute(() ->
                HistoryDatabase.getInstance(getApplication()).saveGroup(group));
    }

    private void saveStepToDb(long groupId, int stepIndex, String expression, String result) {
        dbExecutor.execute(() ->
                HistoryDatabase.getInstance(getApplication())
                        .saveStep(groupId, stepIndex, expression, result));
    }

    // ── Date-separator injection ─────────────────────────────────────────────

    /** Convert raw groups (oldest-first) to adapter items, inserting date separators. */
    private static List<HistoryListItem> buildListItems(List<HistoryGroup> groups) {
        List<HistoryListItem> items = new ArrayList<>();
        SimpleDateFormat keyFmt   = new SimpleDateFormat("yyyyMMdd", Locale.getDefault());
        SimpleDateFormat labelFmt = new SimpleDateFormat("MMMM d, yyyy", Locale.getDefault());
        String lastDayKey = null;
        for (HistoryGroup group : groups) {
            Date date = new Date(group.getTimestamp());
            String dayKey = keyFmt.format(date);
            if (!dayKey.equals(lastDayKey)) {
                items.add(new HistoryListItem.DateSeparator(labelFmt.format(date)));
                lastDayKey = dayKey;
            }
            items.add(new HistoryListItem.GroupItem(group));
        }
        return items;
    }

    @Override
    protected void onCleared() {
        super.onCleared();
        dbExecutor.shutdown();
    }
}
