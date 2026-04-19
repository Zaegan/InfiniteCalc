package com.github.zaegan.infinitecalc;

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
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.button.MaterialButton;

public class MainActivity extends AppCompatActivity {

    private CalculatorViewModel viewModel;
    private EditText expressionDisplay;
    private boolean extendedMode = false;

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

        // ── Permanent row: ^ | √ | () | π ────────────────────────────────────
        findViewById(R.id.btn_pow).setOnClickListener(v ->   { sync(); viewModel.insert("^"); });
        findViewById(R.id.btn_sqrt).setOnClickListener(v ->  { sync(); viewModel.insert("sqrt("); });
        findViewById(R.id.btn_paren).setOnClickListener(v -> { sync(); viewModel.smartParen(); });
        findViewById(R.id.btn_pi).setOnClickListener(v ->    { sync(); viewModel.insert("π"); });

        // ── Extended operators ────────────────────────────────────────────────
        findViewById(R.id.btn_percent).setOnClickListener(v -> { sync(); viewModel.insert("%"); });
        findViewById(R.id.btn_10pow).setOnClickListener(v ->   { sync(); viewModel.insert("10^"); });

        // ── Scientific functions ──────────────────────────────────────────────
        findViewById(R.id.btn_sin).setOnClickListener(v -> { sync(); viewModel.insert("sin("); });
        findViewById(R.id.btn_cos).setOnClickListener(v -> { sync(); viewModel.insert("cos("); });
        findViewById(R.id.btn_tan).setOnClickListener(v -> { sync(); viewModel.insert("tan("); });
        findViewById(R.id.btn_ln).setOnClickListener(v ->  { sync(); viewModel.insert("ln("); });
        findViewById(R.id.btn_log).setOnClickListener(v -> { sync(); viewModel.insert("log("); });

        // ── Backspace / AC / = ────────────────────────────────────────────────
        findViewById(R.id.btn_backspace).setOnClickListener(v -> { sync(); viewModel.backspace(); });
        findViewById(R.id.btn_clear).setOnClickListener(v -> viewModel.onClear());
        findViewById(R.id.btn_equal).setOnClickListener(v -> viewModel.onEquals());

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

    // ── EditText setup ────────────────────────────────────────────────────────

    private void setupExpressionDisplay() {
        expressionDisplay.setShowSoftInputOnFocus(false);

        expressionDisplay.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) hideKeyboard(v);
        });

        ActionMode.Callback noOpCallback = new ActionMode.Callback() {
            @Override public boolean onCreateActionMode(ActionMode m, Menu menu) { return false; }
            @Override public boolean onPrepareActionMode(ActionMode m, Menu menu) { return false; }
            @Override public boolean onActionItemClicked(ActionMode m, MenuItem item) { return false; }
            @Override public void onDestroyActionMode(ActionMode m) {}
        };
        expressionDisplay.setCustomSelectionActionModeCallback(noOpCallback);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            expressionDisplay.setCustomInsertionActionModeCallback(noOpCallback);
        }
    }

    private void hideKeyboard(View v) {
        InputMethodManager imm = (InputMethodManager) getSystemService(INPUT_METHOD_SERVICE);
        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
    }

    private void sync() {
        viewModel.syncCursor(expressionDisplay.getSelectionStart());
    }
}
