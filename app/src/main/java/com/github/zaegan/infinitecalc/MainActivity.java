package com.github.zaegan.infinitecalc;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.ActionMode;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

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

        TextView previewView       = findViewById(R.id.preview);
        RecyclerView historyList   = findViewById(R.id.history_list);
        View keypadContent         = findViewById(R.id.keypad_content);
        View varGrid               = findViewById(R.id.var_grid);
        View varExtRows            = findViewById(R.id.var_ext_rows);
        View extendedPanel         = findViewById(R.id.extended_panel);
        TextView btnExt            = findViewById(R.id.btn_ext);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setReverseLayout(true);
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
            expressionDisplay.setText(text);
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
        viewModel.getHistory().observe(this, adapter::submitList);
        viewModel.getErrorMessage().observe(this, msg -> {
            if (msg != null) {
                android.widget.Toast.makeText(this, msg, android.widget.Toast.LENGTH_SHORT).show();
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
        findViewById(R.id.btn_pow).setOnClickListener(v ->    { sync(); viewModel.insert("^"); });
        findViewById(R.id.btn_sqrt).setOnClickListener(v ->   { sync(); viewModel.insert("sqrt("); });
        findViewById(R.id.btn_paren).setOnClickListener(v ->  { sync(); viewModel.smartParen(); });
        findViewById(R.id.btn_pi).setOnClickListener(v ->     { sync(); viewModel.insert("π"); });

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

        // ── Variable panel: A–H (basic) + I–P (EXT) ──────────────────────────
        int[] varBasicIds = {R.id.btn_var_a, R.id.btn_var_b, R.id.btn_var_c, R.id.btn_var_d,
                             R.id.btn_var_e, R.id.btn_var_f, R.id.btn_var_g, R.id.btn_var_h};
        String[] varBasicNames = {"A", "B", "C", "D", "E", "F", "G", "H"};
        for (int i = 0; i < varBasicIds.length; i++) {
            final String varName = varBasicNames[i];
            findViewById(varBasicIds[i]).setOnClickListener(v -> {
                sync();
                viewModel.onVariableTapped(varName);
            });
        }

        int[] varExtIds = {R.id.btn_var_i, R.id.btn_var_j, R.id.btn_var_k, R.id.btn_var_l,
                           R.id.btn_var_m, R.id.btn_var_n, R.id.btn_var_o, R.id.btn_var_p};
        String[] varExtNames = {"I", "J", "K", "L", "M", "N", "O", "P"};
        for (int i = 0; i < varExtIds.length; i++) {
            final String varName = varExtNames[i];
            findViewById(varExtIds[i]).setOnClickListener(v -> {
                sync();
                viewModel.onVariableTapped(varName);
            });
        }
    }

    /**
     * Configure the EditText so the OS cursor works (tap to position) but the
     * software keyboard never appears.
     */
    private void setupExpressionDisplay() {
        expressionDisplay.setShowSoftInputOnFocus(false);

        expressionDisplay.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) hideKeyboard(v);
        });

        // Disable the text-selection action bar (Cut / Copy / Paste)
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

    /** Read the current EditText cursor position and sync it to the ViewModel. */
    private void sync() {
        viewModel.syncCursor(expressionDisplay.getSelectionStart());
    }
}
