package com.github.zaegan.infinitecalc;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.style.RelativeSizeSpan;
import android.text.style.SuperscriptSpan;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

public class MainActivity extends AppCompatActivity {

    private CalculatorViewModel viewModel;
    private CalculatorEditText expressionDisplay;
    private boolean extendedMode = false;

    // ── Extended panel paging ─────────────────────────────────────────────────

    /**
     * Insert text for each of the 6 remappable slots per extended page.
     * Slots 0–3 fill row 1; slots 4–5 fill the middle of row 2 (between ‹ and ›).
     *
     * Sentinels (handled specially in bindExtPage):
     *   "RAD_DEG" → toggleAngleMode(), text = current mode label
     *   "NEGATE"  → smartNegate()
     *   "SETTINGS"→ placeholder (future settings screen)
     *   "REMAP"   → placeholder (future remapping screen)
     */
    private static final java.util.ArrayList<String[]> EXT_INSERT =
            new java.util.ArrayList<>(java.util.Arrays.asList(
        // Page 0 — trig & basic functions
        new String[]{"sin(", "cos(", "tan(", "RAD_DEG",       "ln(", "log("},
        // Page 1 — logs & roots (5 items; sqrt is on the basic panel)
        new String[]{"log2(", "logn(", "e", "\u221B(",         "nthrt("},
        // Page 2 — powers & combinatorics
        new String[]{"^2", "^3", "abs(", "%",                  "!", "ncr("},
        // Page 3 — misc, inverse trig & comma
        new String[]{"npr(", "round(", "asin(", "acos(",       "atan(", ","},
        // Page 4 — hyperbolic & negate
        new String[]{"sinh(", "cosh(", "tanh(", "10^(",        "SETTINGS", "NEGATE"},
        // Page 5 — remap & physical constants (4 items)
        new String[]{"REMAP", "G\u2099", "k\u2091", "N\u2090"}
    ));

    private static final java.util.ArrayList<String[]> EXT_LABELS =
            new java.util.ArrayList<>(java.util.Arrays.asList(
        new String[]{"sin", "cos", "tan", "RAD",               "ln", "log"},
        new String[]{"log₂", "logₙ", "e", "∛",                "ⁿ√"},
        new String[]{"x²", "x³", "abs", "%",                  "n!", "nCr"},
        new String[]{"nPr", "rnd", "sin⁻¹", "cos⁻¹",         "tan⁻¹", ","},
        new String[]{"sinh", "cosh", "tanh", "10^",            "Settings", "±"},
        new String[]{"Remap", "Gₙ", "kₑ", "Nₐ"}
    ));

    private int currentExtPage = 0;
    private android.widget.Button[] extButtons;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.parseColor("#121212"));
            getWindow().setNavigationBarColor(Color.parseColor("#121212"));
        }

        setContentView(R.layout.activity_main);

        viewModel = new ViewModelProvider(this).get(CalculatorViewModel.class);

        expressionDisplay = findViewById(R.id.display);
        setupExpressionDisplay();

        TextView previewView    = findViewById(R.id.preview);
        RecyclerView historyList = findViewById(R.id.history_list);
        View keypadContent      = findViewById(R.id.keypad_content);
        View varGrid            = findViewById(R.id.var_grid);
        View varExtRows         = findViewById(R.id.var_ext_rows);
        View extendedPanel      = findViewById(R.id.extended_panel);
        TextView btnExt         = findViewById(R.id.btn_ext);

        // List is oldest-first; stackFromEnd keeps newest visible at bottom.
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

        viewModel.getRadianMode().observe(this, isRad -> {
            // If the current page contains the RAD/DEG slot, update its label live
            String[] inserts = EXT_INSERT.get(currentExtPage);
            for (int i = 0; i < extButtons.length && i < inserts.length; i++) {
                if ("RAD_DEG".equals(inserts[i])) {
                    extButtons[i].setText(isRad ? "DEG" : "RAD");
                    break;
                }
            }
        });

        viewModel.getErrorMessage().observe(this, msg -> {
            if (msg != null) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                viewModel.clearError();
            }
        });

        // ── Var mode: toggle between keypad and var grid ──────────────────────
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

        // ── Digits ───────────────────────────────────────────────────────────
        int[] numIds = {R.id.btn_0, R.id.btn_1, R.id.btn_2, R.id.btn_3, R.id.btn_4,
                        R.id.btn_5, R.id.btn_6, R.id.btn_7, R.id.btn_8, R.id.btn_9};
        for (int i = 0; i < numIds.length; i++) {
            final String digit = String.valueOf(i);
            findViewById(numIds[i]).setOnClickListener(v -> { sync(); viewModel.insert(digit); });
        }

        // ── Decimal ──────────────────────────────────────────────────────────
        findViewById(R.id.btn_decimal).setOnClickListener(v -> { sync(); viewModel.insert("."); });

        // ── Arithmetic operators ──────────────────────────────────────────────
        findViewById(R.id.btn_add).setOnClickListener(v ->      { sync(); viewModel.insert("+"); });
        findViewById(R.id.btn_subtract).setOnClickListener(v -> { sync(); viewModel.insert("\u2212"); });
        findViewById(R.id.btn_multiply).setOnClickListener(v -> { sync(); viewModel.insert("\u00D7"); });
        findViewById(R.id.btn_divide).setOnClickListener(v ->   { sync(); viewModel.insert("\u00F7"); });

        // ── Basic row 1: ^ | √ | () | π ─────────────────────────────────────
        findViewById(R.id.btn_pow).setOnClickListener(v ->   { sync(); viewModel.insert("^"); });
        findViewById(R.id.btn_sqrt).setOnClickListener(v ->  { sync(); viewModel.insert("\u221A("); });
        findViewById(R.id.btn_paren).setOnClickListener(v -> { sync(); viewModel.smartParen(); });
        findViewById(R.id.btn_pi).setOnClickListener(v ->    { sync(); viewModel.insert("π"); });

        // ── Extended panel: paged function buttons ────────────────────────────
        // Slots 0-3 → row 1; slots 4-5 → row 2 middle (between permanent ‹/›)
        extButtons = new android.widget.Button[]{
            findViewById(R.id.btn_ext_0), findViewById(R.id.btn_ext_1),
            findViewById(R.id.btn_ext_2), findViewById(R.id.btn_ext_3),
            findViewById(R.id.btn_ext_4), findViewById(R.id.btn_ext_5)
        };
        bindExtPage(0);
        // ‹ and › are permanent — wired once here, never overwritten by bindExtPage
        findViewById(R.id.btn_ext_prev).setOnClickListener(v ->
            bindExtPage((currentExtPage - 1 + EXT_INSERT.size()) % EXT_INSERT.size()));
        findViewById(R.id.btn_ext_next).setOnClickListener(v ->
            bindExtPage((currentExtPage + 1) % EXT_INSERT.size()));

        // ── Backspace / AC / = ────────────────────────────────────────────────
        findViewById(R.id.btn_backspace).setOnClickListener(v -> { sync(); viewModel.backspace(); });
        findViewById(R.id.btn_clear).setOnClickListener(v -> viewModel.onClear());
        findViewById(R.id.btn_equal).setOnClickListener(v -> viewModel.onEquals());
        findViewById(R.id.btn_equal_var).setOnClickListener(v -> viewModel.onEquals());

        // ── STO / REC ─────────────────────────────────────────────────────────
        findViewById(R.id.btn_sto).setOnClickListener(v -> viewModel.enterStoMode());
        findViewById(R.id.btn_rec).setOnClickListener(v -> viewModel.enterRecMode());

        // ── Variable panel: A–T (basic, 20 vars) + U–Z/α/β (EXT, 8 vars) ────
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
                sync();
                viewModel.onVariableTapped(varName);
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
                sync();
                viewModel.onVariableTapped(varName);
            });
        }
    }

    // ── Extended panel paging ─────────────────────────────────────────────────

    /** Two-arg function insert strings — pressing these triggers comma-mode. */
    private static final java.util.Set<String> TWO_ARG_INSERTS = new java.util.HashSet<>(
            java.util.Arrays.asList("logn(", "nthrt(", "ncr(", "npr(", "round("));

    private void bindExtPage(int page) {
        currentExtPage = page;
        Boolean isRad = viewModel.getRadianMode().getValue();
        String[] inserts = EXT_INSERT.get(page);
        String[] labels  = EXT_LABELS.get(page);
        for (int i = 0; i < extButtons.length; i++) {
            if (i >= inserts.length) {
                extButtons[i].setVisibility(View.GONE);
                continue;
            }
            extButtons[i].setVisibility(View.VISIBLE);
            final String insert = inserts[i];
            final String label  = labels[i];
            final android.widget.Button btn = extButtons[i];
            switch (insert) {
                case "RAD_DEG":
                    btn.setText(isRad != null && isRad ? "DEG" : "RAD");
                    btn.setOnClickListener(v -> viewModel.toggleAngleMode());
                    break;
                case "NEGATE":
                    btn.setText(label);
                    btn.setOnClickListener(v -> { sync(); viewModel.smartNegate(); });
                    break;
                case "SETTINGS":
                    btn.setText(label);
                    btn.setOnClickListener(v ->
                        Toast.makeText(this, "Settings — coming soon", Toast.LENGTH_SHORT).show());
                    break;
                case "REMAP":
                    btn.setText(label);
                    btn.setOnClickListener(v ->
                        Toast.makeText(this, "Remap — coming soon", Toast.LENGTH_SHORT).show());
                    break;
                case ",":
                    btn.setText(",");
                    btn.setOnClickListener(v -> { sync(); viewModel.insert(","); });
                    break;
                default:
                    btn.setText(label);
                    if (TWO_ARG_INSERTS.contains(insert)) {
                        btn.setOnClickListener(v -> {
                            sync();
                            viewModel.insert(insert);
                            activateCommaMode(btn);
                        });
                    } else {
                        btn.setOnClickListener(v -> { sync(); viewModel.insert(insert); });
                    }
                    break;
            }
        }
    }

    /**
     * Transform a button into a persistent comma key.
     * Stays as ',' until {@link #bindExtPage} is called (i.e. the user changes pages).
     */
    private void activateCommaMode(android.widget.Button btn) {
        btn.setText(",");
        btn.setOnClickListener(v -> { sync(); viewModel.insert(","); });
    }

    // ── STO / REC active-mode highlight ──────────────────────────────────────

    /**
     * Visually distinguish the active STO or REC button with a bright fill and
     * a semi-white stroke so it's obvious which mode is on and how to exit it.
     */
    private void updateVarModeHighlight(CalculatorViewModel.VarMode mode) {
        MaterialButton btnSto = (MaterialButton) findViewById(R.id.btn_sto);
        MaterialButton btnRec = (MaterialButton) findViewById(R.id.btn_rec);
        int strokePx = Math.round(2.5f * getResources().getDisplayMetrics().density);
        int haloColor = Color.parseColor("#ccffffff"); // semi-transparent white glow

        boolean stoActive = mode == CalculatorViewModel.VarMode.STO;
        boolean recActive = mode == CalculatorViewModel.VarMode.REC;

        btnSto.setBackgroundTintList(ColorStateList.valueOf(
                Color.parseColor(stoActive ? "#d45000" : "#3a2000")));
        btnSto.setTextColor(stoActive ? Color.WHITE : Color.parseColor("#ffb060"));
        btnSto.setStrokeColor(ColorStateList.valueOf(stoActive ? haloColor : Color.TRANSPARENT));
        btnSto.setStrokeWidth(stoActive ? strokePx : 0);

        btnRec.setBackgroundTintList(ColorStateList.valueOf(
                Color.parseColor(recActive ? "#0055cc" : "#001e3a")));
        btnRec.setTextColor(recActive ? Color.WHITE : Color.parseColor("#60b0ff"));
        btnRec.setStrokeColor(ColorStateList.valueOf(recActive ? haloColor : Color.TRANSPARENT));
        btnRec.setStrokeWidth(recActive ? strokePx : 0);
    }

    // ── Superscript unary minus ───────────────────────────────────────────────

    /**
     * Build a SpannableString for display.  Any '−' that appears at the start
     * of the expression, or immediately after a binary operator or '(', is
     * rendered in superscript to clarify it is a unary negative sign.
     */
    private static SpannableString buildDisplayText(String expr) {
        SpannableString ss = new SpannableString(expr);
        for (int i = 0; i < expr.length(); i++) {
            if (expr.charAt(i) == '\u2212' && isUnaryPosition(expr, i)) {
                ss.setSpan(new SuperscriptSpan(), i, i + 1,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE);
                ss.setSpan(new RelativeSizeSpan(0.65f), i, i + 1,
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

    // ── EditText / CalculatorEditText setup ───────────────────────────────────

    private void setupExpressionDisplay() {
        expressionDisplay.setShowSoftInputOnFocus(false);
        expressionDisplay.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) hideKeyboard(v);
        });

        // Paste: sanitize clipboard text and route through ViewModel so it
        // remains the single source of truth for the expression.
        expressionDisplay.setPasteListener(text -> {
            viewModel.syncCursor(expressionDisplay.getSelectionStart());
            viewModel.insert(text); // text is already sanitized by CalculatorEditText
        });

        // Selection action mode (long-press with selection handles):
        // allow Copy, Paste, Select All — remove Cut (deletion bypasses ViewModel).
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

        // Insertion action mode (tap to place cursor): show Paste only.
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
        // Extra safety: hide the keyboard whenever the window regains focus
        // (e.g. returning from standby), even if setShowSoftInputOnFocus already
        // suppresses it — some devices re-show it during the focus transition.
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
}
