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

    // ── In-memory ordered list of raw groups (oldest first) ─────────────────
    private final List<HistoryGroup> rawGroups = new ArrayList<>();

    // ── Pure state ──────────────────────────────────────────────────────────
    private final CalculatorState state = new CalculatorState();

    private final List<HistoryItem> currentDraftSteps = new ArrayList<>();

    // ── Persistence ─────────────────────────────────────────────────────────
    private final ExecutorService dbExecutor = Executors.newSingleThreadExecutor();

    private static final String PREF_NAME = "calculator_vars";
    private static final String[] VAR_NAMES = {
        "A", "B", "C", "D", "E", "F", "G", "H",
        "I", "J", "K", "L", "M", "N", "O", "P"
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

    public void clearError() { errorMessage.setValue(null); }

    // ── Cursor sync ─────────────────────────────────────────────────────────
    public void syncCursor(int pos) { state.syncCursor(pos); }

    // ── Delegated input ─────────────────────────────────────────────────────
    public void insert(String text) {
        exitVarMode();
        state.insert(text);
        updateDisplay();
    }

    /** Insert a result string at the cursor (used when tapping a result in history). */
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
     * Evaluate and commit a HistoryGroup.  Auto-iterates if the expression
     * started with a digit (replaces leading number with result for next press).
     */
    public void onEquals() {
        String expression = state.getExpression().trim();
        if (expression.isEmpty()) return;
        try {
            Map<String, Double> vars = loadVariables();
            double result = ExpressionEvaluator.evaluate(expression, vars);
            String resultStr = CalculatorState.formatResult(result);

            addDraftStep(expression, resultStr);
            commitGroup();

            String iterated = buildIteratedExpression(expression, resultStr);
            state.setExpression(iterated != null ? iterated : resultStr);
            previewText.setValue("");
            updateDisplay();
        } catch (Exception e) {
            errorMessage.setValue(e.getMessage() != null ? e.getMessage() : "Error");
        }
    }

    /** AC: commit any draft steps as a history entry, then clear. */
    public void onClear() {
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

    private static String buildIteratedExpression(String expression, String resultStr) {
        if (expression.isEmpty() || !Character.isDigit(expression.charAt(0))) return null;
        int i = 0;
        while (i < expression.length()
                && (Character.isDigit(expression.charAt(i)) || expression.charAt(i) == '.')) {
            i++;
        }
        if (i >= expression.length()) return null;
        return resultStr + expression.substring(i);
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
            double partialResult = ExpressionEvaluator.evaluatePartial(expression, vars);
            String resultStr = CalculatorState.formatResult(partialResult);
            previewText.setValue("= " + resultStr);

            try {
                ExpressionEvaluator.evaluate(expression, vars);
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
            List<HistoryEntry> entries =
                    AppDatabase.getInstance(getApplication()).historyDao().loadAll();

            // Reconstruct HistoryGroups (entries sorted groupId ASC, stepIndex ASC)
            List<HistoryGroup> groups = new ArrayList<>();
            long currentId = -1;
            List<HistoryItem> currentSteps = null;
            for (HistoryEntry e : entries) {
                if (e.groupId != currentId) {
                    if (currentSteps != null && !currentSteps.isEmpty()) {
                        groups.add(new HistoryGroup(currentSteps, currentId));
                    }
                    currentId = e.groupId;
                    currentSteps = new ArrayList<>();
                }
                currentSteps.add(new HistoryItem(e.expression, e.result));
            }
            if (currentSteps != null && !currentSteps.isEmpty()) {
                groups.add(new HistoryGroup(currentSteps, currentId));
            }

            // groups is oldest-first
            rawGroups.clear();
            rawGroups.addAll(groups);
            history.postValue(buildListItems(rawGroups));
        });
    }

    private void saveGroupToDb(HistoryGroup group) {
        dbExecutor.execute(() -> {
            List<HistoryEntry> entries = new ArrayList<>();
            List<HistoryItem> steps = group.getSteps();
            for (int i = 0; i < steps.size(); i++) {
                HistoryEntry e = new HistoryEntry();
                e.groupId = group.getTimestamp();
                e.stepIndex = i;
                e.expression = steps.get(i).getExpression();
                e.result = steps.get(i).getResult();
                entries.add(e);
            }
            AppDatabase.getInstance(getApplication()).historyDao().insertAll(entries);
        });
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
