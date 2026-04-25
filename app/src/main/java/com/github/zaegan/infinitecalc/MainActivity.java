package com.github.zaegan.infinitecalc;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.text.style.SuperscriptSpan;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MainActivity extends AppCompatActivity {

    private static final int REQUEST_REMAP = 1001;

    private CalculatorViewModel viewModel;
    private CalculatorEditText expressionDisplay;
    private boolean extendedMode = false;
    private boolean negationFirstMode = false;

    private RemapConfig remapConfig;
    private SharedPreferences remapPrefs;
    private TutorialManager tutorialManager;

    private static final String[] INTRO_IDS = {
        TutorialManager.INTRO_ACCORDION,
        TutorialManager.INTRO_HISTORY_COPY,
        TutorialManager.INTRO_EXT,
    };

    // Containers populated programmatically from RemapConfig
    private LinearLayout containerExtRow1;
    private LinearLayout containerExtRow2Mid;
    private final LinearLayout[] containerBasic = new LinearLayout[5];

    private int currentExtPage = 0;

    // Two-arg function inserts that trigger comma mode
    private static final Set<String> TWO_ARG_INSERTS = new HashSet<>(
            Arrays.asList("logn(", "nthrt(", "ncr(", "npr(", "round(", "mod("));

    private final ActivityResultLauncher<Intent> remapLauncher =
            registerForActivityResult(new ActivityResultContracts.StartActivityForResult(),
                    result -> {
                        // Reload config whether Lock or Discard — RemapActivity always
                        // saves on Lock; on Discard the prefs are unchanged.
                        remapConfig = RemapConfig.load(remapPrefs);
                        rebuildBasicRows();
                        rebuildExtPage(currentExtPage);
                    });

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        // Apply saved theme before super so the window gets the right background
        String themeMode = getSharedPreferences("remap_prefs", MODE_PRIVATE)
                .getString("theme_mode", "dark");
        applyThemeMode(themeMode);

        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            int bgColor = ContextCompat.getColor(this, R.color.bg);
            getWindow().setStatusBarColor(bgColor);
            getWindow().setNavigationBarColor(bgColor);
        }

        setContentView(R.layout.activity_main);

        viewModel = new ViewModelProvider(this).get(CalculatorViewModel.class);

        // Load remap config
        remapPrefs = getSharedPreferences("remap_prefs", MODE_PRIVATE);
        remapConfig = RemapConfig.load(remapPrefs);
        tutorialManager = new TutorialManager(this);

        expressionDisplay = findViewById(R.id.display);
        setupExpressionDisplay();

        TextView previewView     = findViewById(R.id.preview);
        RecyclerView historyList = findViewById(R.id.history_list);
        View keypadContent       = findViewById(R.id.keypad_content);
        View varGrid             = findViewById(R.id.var_grid);
        View varExtRows          = findViewById(R.id.var_ext_rows);
        View extendedPanel       = findViewById(R.id.extended_panel);
        TextView btnExt          = findViewById(R.id.btn_ext);

        containerExtRow1    = findViewById(R.id.container_ext_row1);
        containerExtRow2Mid = findViewById(R.id.container_ext_row2_mid);
        containerBasic[0]   = findViewById(R.id.container_basic_0);
        containerBasic[1]   = findViewById(R.id.container_basic_1);
        containerBasic[2]   = findViewById(R.id.container_basic_2);
        containerBasic[3]   = findViewById(R.id.container_basic_3);
        containerBasic[4]   = findViewById(R.id.container_basic_4);

        // Build dynamic rows from config
        rebuildBasicRows();
        rebuildExtPage(0);

        // History list
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(true);
        historyList.setLayoutManager(layoutManager);
        HistoryAdapter adapter = new HistoryAdapter();
        historyList.setAdapter(adapter);

        adapter.setOnHistoryClickListener(new HistoryAdapter.OnHistoryClickListener() {
            @Override public void onExpressionClick(String expression) {
                viewModel.restoreExpression(expression);
            }
            @Override public void onResultClick(String result) {
                viewModel.insertResult(result);
            }
            @Override public void onExpressionLongClick(String expression) {
                copyToClipboard(expression);
            }
            @Override public void onResultLongClick(String result) {
                copyToClipboard(result);
            }
        });

        // ── Observe ──────────────────────────────────────────────────────────
        viewModel.getExpressionText().observe(this, text -> {
            expressionDisplay.setText(buildDisplayText(text), TextView.BufferType.SPANNABLE);
            Integer pos = viewModel.getCursorPos().getValue();
            if (pos != null) {
                int clamped = Math.max(0, Math.min(pos, text.length()));
                expressionDisplay.setSelection(clamped);
            }
        });

        viewModel.getCursorPos().observe(this, pos -> {
            if (pos != null) {
                int len = expressionDisplay.getText().length();
                expressionDisplay.setSelection(Math.max(0, Math.min(pos, len)));
            }
        });

        viewModel.getPreviewText().observe(this, previewView::setText);

        viewModel.getHistory().observe(this, items -> {
            adapter.submitList(items, () -> {
                if (items != null && !items.isEmpty()) {
                    historyList.scrollToPosition(items.size() - 1);
                }
            });
        });

        viewModel.getRadianMode().observe(this, isRad ->
                rebuildExtPage(currentExtPage));

        viewModel.getNegationFirstMode().observe(this, isNegFirst -> {
            negationFirstMode = (isNegFirst != null && isNegFirst);
            String currentExpr = viewModel.getExpressionText().getValue();
            if (currentExpr != null) {
                expressionDisplay.setText(buildDisplayText(currentExpr), TextView.BufferType.SPANNABLE);
                Integer pos = viewModel.getCursorPos().getValue();
                if (pos != null) {
                    int clamped = Math.max(0, Math.min(pos, currentExpr.length()));
                    expressionDisplay.setSelection(clamped);
                }
            }
        });

        viewModel.getErrorMessage().observe(this, msg -> {
            if (msg != null) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                viewModel.clearError();
            }
        });

        // ── Var mode ─────────────────────────────────────────────────────────
        viewModel.getVarMode().observe(this, mode -> {
            boolean inVarMode = mode != null && mode != CalculatorViewModel.VarMode.NONE;
            keypadContent.setVisibility(inVarMode ? View.GONE : View.VISIBLE);
            varGrid.setVisibility(inVarMode ? View.VISIBLE : View.GONE);
            if (inVarMode) {
                varExtRows.setVisibility(extendedMode ? View.VISIBLE : View.GONE);
                extendedPanel.setVisibility(View.GONE);
            } else {
                varExtRows.setVisibility(View.GONE);
                extendedPanel.setVisibility(extendedMode ? View.VISIBLE : View.GONE);
            }
            updateVarModeHighlight(mode);
        });

        // ── EXT toggle ───────────────────────────────────────────────────────
        btnExt.setOnClickListener(v -> {
            extendedMode = !extendedMode;
            btnExt.setText(extendedMode ? "BASIC" : "EXT");
            CalculatorViewModel.VarMode mode = viewModel.getVarMode().getValue();
            boolean inVarMode = mode != null && mode != CalculatorViewModel.VarMode.NONE;
            if (inVarMode) {
                varExtRows.setVisibility(extendedMode ? View.VISIBLE : View.GONE);
            } else {
                extendedPanel.setVisibility(extendedMode ? View.VISIBLE : View.GONE);
            }
        });

        // ── Ext page navigation ──────────────────────────────────────────────
        findViewById(R.id.btn_ext_prev).setOnClickListener(v ->
                rebuildExtPage((currentExtPage - 1 + remapConfig.extPages.size())
                        % remapConfig.extPages.size()));
        findViewById(R.id.btn_ext_next).setOnClickListener(v ->
                rebuildExtPage((currentExtPage + 1) % remapConfig.extPages.size()));

        // ── Backspace / AC / = ───────────────────────────────────────────────
        findViewById(R.id.btn_backspace).setOnClickListener(v -> { sync(); viewModel.backspace(); });
        findViewById(R.id.btn_clear).setOnClickListener(v -> viewModel.onClear());
        findViewById(R.id.btn_equal).setOnClickListener(v -> viewModel.onEquals());
        findViewById(R.id.btn_equal_var).setOnClickListener(v -> viewModel.onEquals());

        // ── STO / REC ────────────────────────────────────────────────────────
        findViewById(R.id.btn_sto).setOnClickListener(v ->
                maybeShowTutorial(TutorialManager.STO, () -> viewModel.enterStoMode()));
        findViewById(R.id.btn_rec).setOnClickListener(v ->
                maybeShowTutorial(TutorialManager.REC, () -> viewModel.enterRecMode()));

        // ── Variable panel: A–T ──────────────────────────────────────────────
        int[] varBasicIds = {
            R.id.btn_var_a, R.id.btn_var_b, R.id.btn_var_c, R.id.btn_var_d,
            R.id.btn_var_e, R.id.btn_var_f, R.id.btn_var_g, R.id.btn_var_h,
            R.id.btn_var_i, R.id.btn_var_j, R.id.btn_var_k, R.id.btn_var_l,
            R.id.btn_var_m, R.id.btn_var_n, R.id.btn_var_o, R.id.btn_var_p,
            R.id.btn_var_q, R.id.btn_var_r, R.id.btn_var_s, R.id.btn_var_t
        };
        String[] varBasicNames = {
            "A","B","C","D","E","F","G","H","I","J","K","L","M","N","O","P","Q","R","S","T"
        };
        for (int i = 0; i < varBasicIds.length; i++) {
            final String varName = varBasicNames[i];
            findViewById(varBasicIds[i]).setOnClickListener(v -> {
                sync(); viewModel.onVariableTapped(varName);
            });
        }

        int[] varExtIds = {
            R.id.btn_var_u, R.id.btn_var_v, R.id.btn_var_w, R.id.btn_var_x,
            R.id.btn_var_y, R.id.btn_var_z, R.id.btn_var_alpha, R.id.btn_var_beta
        };
        String[] varExtNames = {"U","V","W","X","Y","Z","\u03B1","\u03B2"};
        for (int i = 0; i < varExtIds.length; i++) {
            final String varName = varExtNames[i];
            findViewById(varExtIds[i]).setOnClickListener(v -> {
                sync(); viewModel.onVariableTapped(varName);
            });
        }

        // Show intro tutorials (accordion, history copy, EXT) sequentially on first launch
        showNextIntroFrom(0);
    }

    // ── Dynamic row building ─────────────────────────────────────────────────

    private void rebuildBasicRows() {
        List<List<ButtonDef>> rows = remapConfig.basicRows;
        for (int r = 0; r < containerBasic.length && r < rows.size(); r++) {
            populateRow(containerBasic[r], rows.get(r), false);
        }
    }

    private void rebuildExtPage(int page) {
        if (remapConfig.extPages.isEmpty()) return;
        currentExtPage = ((page % remapConfig.extPages.size()) + remapConfig.extPages.size())
                % remapConfig.extPages.size();
        RemapConfig.ExtPage ep = remapConfig.extPages.get(currentExtPage);
        populateRow(containerExtRow1, ep.row1, true);
        populateRow(containerExtRow2Mid, ep.row2Middle, true);

        // Keep ext row 2 container weight proportional to middle slot count
        // (‹ and › each have weight=1; midWeight = middleSlots so each slot ≈ equal)
        int midCount = ep.row2Middle.size();
        LinearLayout.LayoutParams midParams =
                (LinearLayout.LayoutParams) containerExtRow2Mid.getLayoutParams();
        midParams.weight = Math.max(midCount, 0);
        containerExtRow2Mid.setLayoutParams(midParams);
    }

    /**
     * Remove all children from {@code container} and add one {@link Button} per
     * {@link ButtonDef} in {@code slots}, sized according to the width rule:
     * n=3 → first slot weight 2, rest weight 1; otherwise all weight 1.
     */
    private void populateRow(LinearLayout container, List<ButtonDef> slots, boolean isExt) {
        container.removeAllViews();
        int n = slots.size();
        int margin = dp(2);
        int height = isExt ? dp(52) : dp(56);

        for (int i = 0; i < n; i++) {
            ButtonDef def = slots.get(i);
            Button btn = new Button(this);
            btn.setAllCaps(false);
            btn.setTextSize(13f);
            btn.setText(displayLabelFor(def));
            btn.setTextColor(def.textColor());
            btn.setBackgroundTintList(ColorStateList.valueOf(def.bgColor()));

            float weight = (n == 3 && i == 0) ? 2f : 1f;
            LinearLayout.LayoutParams lp =
                    new LinearLayout.LayoutParams(0, height, weight);
            lp.setMargins(margin, margin, margin, margin);
            btn.setLayoutParams(lp);

            final ButtonDef captured = def;
            btn.setOnClickListener(v -> { sync(); handleButtonAction(captured, btn); });

            container.addView(btn);
        }
    }

    /**
     * Dynamic label: RAD_DEG shows current mode text; everything else uses labelText.
     */
    private String displayLabelFor(ButtonDef def) {
        if ("RAD_DEG".equals(def.insertText)) {
            Boolean isRad = viewModel.getRadianMode().getValue();
            return (isRad != null && isRad) ? "DEG" : "RAD";
        }
        return def.labelText;
    }

    // ── Button action dispatcher ─────────────────────────────────────────────

    private void handleButtonAction(ButtonDef def, Button btn) {
        String tutId = getTutorialId(def);
        if (tutId != null && !tutorialManager.isSeen(tutId)) {
            tutorialManager.markSeen(tutId);
            showTutorial(tutId, () -> executeButtonAction(def, btn));
        } else {
            executeButtonAction(def, btn);
        }
    }

    private void executeButtonAction(ButtonDef def, Button btn) {
        switch (def.getActionType()) {
            case SMART_PAREN:
                viewModel.smartParen();
                break;
            case MINUS:
                viewModel.insertMinus();
                break;
            case NEGATE:
                viewModel.smartNegate();
                break;
            case RAD_DEG:
                viewModel.toggleAngleMode();
                break;
            case SETTINGS:
                new SettingsDialog().show(getSupportFragmentManager(), "settings");
                break;
            case REMAP:
                remapLauncher.launch(new Intent(this, RemapActivity.class));
                break;
            case CUSTOM:
                if (def.isSetCommand()) {
                    executeSetCommand(def.insertText);
                } else {
                    viewModel.insert(def.insertText);
                    if (TWO_ARG_INSERTS.contains(def.insertText)) {
                        activateCommaMode(btn);
                    }
                }
                break;
        }
    }

    /** Returns the tutorial ID for this button, or null if none applies. */
    private String getTutorialId(ButtonDef def) {
        switch (def.getActionType()) {
            case RAD_DEG: return TutorialManager.RAD_DEG;
            case REMAP:   return TutorialManager.REMAP;
            case CUSTOM:
                if (TWO_ARG_INSERTS.contains(def.insertText)) {
                    return getTwoArgTutorialId(def.insertText);
                }
                return null;
            default:
                return null;
        }
    }

    private String getTwoArgTutorialId(String insertText) {
        switch (insertText) {
            case "logn(":  return TutorialManager.FUNC_LOGN;
            case "nthrt(": return TutorialManager.FUNC_NTHRT;
            case "ncr(":   return TutorialManager.FUNC_NCR;
            case "npr(":   return TutorialManager.FUNC_NPR;
            case "round(": return TutorialManager.FUNC_ROUND;
            case "mod(":   return TutorialManager.FUNC_MOD;
            default:       return null;
        }
    }

    // ── Tutorial helpers ──────────────────────────────────────────────────────

    /**
     * Shows the tutorial for {@code id} if not yet seen, then runs {@code action}.
     * If already seen, runs {@code action} immediately.
     */
    private void maybeShowTutorial(String id, Runnable action) {
        if (!tutorialManager.isSeen(id)) {
            tutorialManager.markSeen(id);
            showTutorial(id, action);
        } else {
            action.run();
        }
    }

    /** Shows a single tutorial dialog and runs {@code onDone} when dismissed. */
    private void showTutorial(String id, Runnable onDone) {
        TutorialContent.Entry entry = TutorialContent.get(id);
        if (entry == null) { if (onDone != null) onDone.run(); return; }
        new AlertDialog.Builder(this)
                .setTitle(entry.title)
                .setMessage(entry.body)
                .setPositiveButton("Got it", (d, w) -> { if (onDone != null) onDone.run(); })
                .setCancelable(false)
                .show();
    }

    /**
     * Walks the INTRO_IDS array from {@code index}, skipping already-seen entries,
     * and shows each unseen intro tutorial in sequence.
     * The last unseen intro shows "Got it"; earlier ones show "Next" and "Skip all".
     */
    private void showNextIntroFrom(int index) {
        // Advance past any already-seen entries
        while (index < INTRO_IDS.length && tutorialManager.isSeen(INTRO_IDS[index])) index++;
        if (index >= INTRO_IDS.length) return;

        // Check whether any later unseen entries remain (determines Next vs Got it)
        boolean isLast = true;
        for (int i = index + 1; i < INTRO_IDS.length; i++) {
            if (!tutorialManager.isSeen(INTRO_IDS[i])) { isLast = false; break; }
        }

        final int currentIndex = index;
        tutorialManager.markSeen(INTRO_IDS[currentIndex]);
        TutorialContent.Entry entry = TutorialContent.get(INTRO_IDS[currentIndex]);

        AlertDialog.Builder builder = new AlertDialog.Builder(this)
                .setTitle(entry.title)
                .setMessage(entry.body)
                .setCancelable(false)
                .setPositiveButton(isLast ? "Got it" : "Next",
                        (d, w) -> showNextIntroFrom(currentIndex + 1));

        if (!isLast) {
            builder.setNegativeButton("Skip all", (d, w) -> {
                for (int i = currentIndex; i < INTRO_IDS.length; i++) {
                    tutorialManager.markSeen(INTRO_IDS[i]);
                }
            });
        }

        builder.show();
    }

    private void executeSetCommand(String insertText) {
        // Syntax: FROM <expr> SET _ic_<name>
        int setIdx = insertText.lastIndexOf(" SET _ic_");
        if (setIdx < 0) return;
        String fromExpr  = insertText.substring("FROM ".length(), setIdx);
        String targetVar = insertText.substring(setIdx + " SET ".length());
        viewModel.executeCustomSet(fromExpr, targetVar);
    }

    /**
     * Transform a button into a persistent comma key until the next page change.
     */
    private void activateCommaMode(Button btn) {
        btn.setText(",");
        btn.setOnClickListener(v -> { sync(); viewModel.insert(","); });
    }

    // ── STO/REC highlight ────────────────────────────────────────────────────

    private void updateVarModeHighlight(CalculatorViewModel.VarMode mode) {
        MaterialButton btnSto = (MaterialButton) findViewById(R.id.btn_sto);
        MaterialButton btnRec = (MaterialButton) findViewById(R.id.btn_rec);
        int strokePx = Math.round(2.5f * getResources().getDisplayMetrics().density);
        int haloColor = Color.parseColor("#ccffffff");

        boolean stoActive = mode == CalculatorViewModel.VarMode.STO;
        boolean recActive = mode == CalculatorViewModel.VarMode.REC;

        btnSto.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this,
                stoActive ? R.color.btn_sto_active_bg : R.color.btn_sto_bg)));
        btnSto.setTextColor(stoActive ? Color.WHITE
                : ContextCompat.getColor(this, R.color.btn_sto_text));
        btnSto.setStrokeColor(ColorStateList.valueOf(stoActive ? haloColor : Color.TRANSPARENT));
        btnSto.setStrokeWidth(stoActive ? strokePx : 0);

        btnRec.setBackgroundTintList(ColorStateList.valueOf(ContextCompat.getColor(this,
                recActive ? R.color.btn_rec_active_bg : R.color.btn_rec_bg)));
        btnRec.setTextColor(recActive ? Color.WHITE
                : ContextCompat.getColor(this, R.color.btn_rec_text));
        btnRec.setStrokeColor(ColorStateList.valueOf(recActive ? haloColor : Color.TRANSPARENT));
        btnRec.setStrokeWidth(recActive ? strokePx : 0);
    }

    // ── Display text ─────────────────────────────────────────────────────────

    private SpannableString buildDisplayText(String expr) {
        SpannableString ss = new SpannableString(expr);
        int opColor = ContextCompat.getColor(this, R.color.syntax_op);
        for (int i = 0; i < expr.length(); i++) {
            char c = expr.charAt(i);
            if (c == '\u2212') {
                if (isUnaryPosition(expr, i)) {
                    if (negationFirstMode) {
                        ss.setSpan(new SuperscriptSpan(), i, i + 1,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                        ss.setSpan(new RelativeSizeSpan(0.65f), i, i + 1,
                                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                    }
                } else {
                    ss.setSpan(new ForegroundColorSpan(opColor), i, i + 1,
                            Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                }
            } else if (c == '+' || c == '\u00D7' || c == '\u00F7'
                    || c == '^' || c == '%' || c == '!') {
                ss.setSpan(new ForegroundColorSpan(opColor), i, i + 1,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
            }
        }
        return ss;
    }

    private static boolean isUnaryPosition(String expr, int pos) {
        if (pos == 0) return true;
        char prev = expr.charAt(pos - 1);
        return prev == '+' || prev == '\u2212' || prev == '\u00D7' || prev == '\u00F7'
                || prev == '^' || prev == '%' || prev == '(';
    }

    // ── EditText setup ───────────────────────────────────────────────────────

    private void setupExpressionDisplay() {
        expressionDisplay.setShowSoftInputOnFocus(false);
        expressionDisplay.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) hideKeyboard(v);
        });

        expressionDisplay.setPasteListener(text -> {
            viewModel.syncCursor(expressionDisplay.getSelectionStart());
            viewModel.insert(text);
        });

        ActionMode.Callback selectionCallback = new ActionMode.Callback() {
            @Override public boolean onCreateActionMode(ActionMode m, Menu menu) { return true; }
            @Override public boolean onPrepareActionMode(ActionMode m, Menu menu) {
                menu.removeItem(android.R.id.cut);
                return true;
            }
            @Override public boolean onActionItemClicked(ActionMode m, MenuItem item) { return false; }
            @Override public void onDestroyActionMode(ActionMode m) {}
        };
        expressionDisplay.setCustomSelectionActionModeCallback(selectionCallback);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            ActionMode.Callback insertionCallback = new ActionMode.Callback() {
                @Override public boolean onCreateActionMode(ActionMode m, Menu menu) { return true; }
                @Override public boolean onPrepareActionMode(ActionMode m, Menu menu) {
                    menu.removeItem(android.R.id.cut);
                    menu.removeItem(android.R.id.copy);
                    menu.removeItem(android.R.id.selectAll);
                    return true;
                }
                @Override public boolean onActionItemClicked(ActionMode m, MenuItem item) { return false; }
                @Override public void onDestroyActionMode(ActionMode m) {}
            };
            expressionDisplay.setCustomInsertionActionModeCallback(insertionCallback);
        }
    }

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) hideKeyboard(expressionDisplay);
    }

    private void hideKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    private void copyToClipboard(String text) {
        ClipboardManager cb = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (cb != null) {
            cb.setPrimaryClip(ClipData.newPlainText("calculator", text));
            Toast.makeText(this, "Copied", Toast.LENGTH_SHORT).show();
        }
    }

    private void sync() {
        viewModel.syncCursor(expressionDisplay.getSelectionStart());
    }

    static void applyThemeMode(String mode) {
        switch (mode) {
            case "dark":   AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES); break;
            case "light":  AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO); break;
            default:       AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM); break;
        }
    }

    private int dp(int dp) {
        return Math.round(dp * getResources().getDisplayMetrics().density);
    }
}
