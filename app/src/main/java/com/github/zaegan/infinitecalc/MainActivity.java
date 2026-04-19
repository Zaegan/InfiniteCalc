package com.github.zaegan.infinitecalc;

import android.os.Bundle;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.view.View;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class MainActivity extends AppCompatActivity {

    private CalculatorViewModel viewModel;
    private EditText expressionDisplay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        viewModel = new ViewModelProvider(this).get(CalculatorViewModel.class);

        expressionDisplay = findViewById(R.id.display);
        expressionDisplay.setShowSoftInputOnFocus(false);
        expressionDisplay.requestFocus();

        TextView previewView = findViewById(R.id.preview);
        RecyclerView historyList = findViewById(R.id.history_list);
        View varPanel = findViewById(R.id.var_panel);

        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setReverseLayout(true);
        layoutManager.setStackFromEnd(true);
        historyList.setLayoutManager(layoutManager);

        HistoryAdapter adapter = new HistoryAdapter();
        historyList.setAdapter(adapter);

        adapter.setOnGroupLongClickListener(group ->
                viewModel.restoreExpression(group.getSummaryExpression()));

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
        viewModel.getVarPanelVisible().observe(this, visible ->
                varPanel.setVisibility(visible ? View.VISIBLE : View.GONE));
        viewModel.getErrorMessage().observe(this, msg -> {
            if (msg != null) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                viewModel.clearError();
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
        findViewById(R.id.btn_add).setOnClickListener(v -> { sync(); viewModel.insert("+"); });
        // Unicode minus so the expression display is consistent with the tokenizer
        findViewById(R.id.btn_subtract).setOnClickListener(v -> { sync(); viewModel.insert("\u2212"); });
        // Unicode multiply / divide
        findViewById(R.id.btn_multiply).setOnClickListener(v -> { sync(); viewModel.insert("\u00D7"); });
        findViewById(R.id.btn_divide).setOnClickListener(v -> { sync(); viewModel.insert("\u00F7"); });
        findViewById(R.id.btn_pow).setOnClickListener(v -> { sync(); viewModel.insert("^"); });
        findViewById(R.id.btn_percent).setOnClickListener(v -> { sync(); viewModel.insert("%"); });

        // ── Scientific functions (insert name + open paren as a unit) ─────────
        findViewById(R.id.btn_sin).setOnClickListener(v -> { sync(); viewModel.insert("sin("); });
        findViewById(R.id.btn_cos).setOnClickListener(v -> { sync(); viewModel.insert("cos("); });
        findViewById(R.id.btn_tan).setOnClickListener(v -> { sync(); viewModel.insert("tan("); });
        findViewById(R.id.btn_ln).setOnClickListener(v -> { sync(); viewModel.insert("ln("); });

        // ── Smart parenthesis ─────────────────────────────────────────────────
        findViewById(R.id.btn_paren).setOnClickListener(v -> { sync(); viewModel.smartParen(); });

        // ── Backspace ─────────────────────────────────────────────────────────
        findViewById(R.id.btn_backspace).setOnClickListener(v -> { sync(); viewModel.backspace(); });

        // ── AC / Clear ────────────────────────────────────────────────────────
        findViewById(R.id.btn_clear).setOnClickListener(v -> viewModel.onClear());

        // ── Equals ────────────────────────────────────────────────────────────
        findViewById(R.id.btn_equal).setOnClickListener(v -> viewModel.onEquals());

        // ── STO / REC ─────────────────────────────────────────────────────────
        findViewById(R.id.btn_sto).setOnClickListener(v -> viewModel.enterStoMode());
        findViewById(R.id.btn_rec).setOnClickListener(v -> viewModel.enterRecMode());

        // ── Variable panel ────────────────────────────────────────────────────
        int[] varIds = {R.id.btn_var_a, R.id.btn_var_b, R.id.btn_var_c, R.id.btn_var_d,
                        R.id.btn_var_e, R.id.btn_var_f, R.id.btn_var_g, R.id.btn_var_h};
        String[] varNames = {"A", "B", "C", "D", "E", "F", "G", "H"};
        for (int i = 0; i < varIds.length; i++) {
            final String varName = varNames[i];
            findViewById(varIds[i]).setOnClickListener(v -> {
                sync();
                viewModel.onVariableTapped(varName);
            });
        }
    }

    /** Read the actual cursor position from the EditText before every action. */
    private void sync() {
        viewModel.syncCursor(expressionDisplay.getSelectionStart());
    }
}
