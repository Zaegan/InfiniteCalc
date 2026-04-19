package com.github.zaegan.infinitecalc;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.text.SpannableStringBuilder;
import android.text.Spanned;
import android.text.style.ForegroundColorSpan;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

public class MainActivity extends AppCompatActivity {

    // Cursor glyph rendered in the expression display (app-managed, not OS keyboard cursor)
    private static final String CURSOR_GLYPH = "|";
    private static final int CURSOR_COLOR = 0xFF00E5FF; // bright cyan

    private CalculatorViewModel viewModel;
    private boolean extendedMode = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Match status-bar and nav-bar chrome to the app background
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.parseColor("#121212"));
            getWindow().setNavigationBarColor(Color.parseColor("#121212"));
        }

        setContentView(R.layout.activity_main);

        viewModel = new ViewModelProvider(this).get(CalculatorViewModel.class);

        TextView expressionDisplay = findViewById(R.id.display);
        TextView previewView = findViewById(R.id.preview);
        RecyclerView historyList = findViewById(R.id.history_list);
        View varPanel = findViewById(R.id.var_panel);
        View sciRow = findViewById(R.id.sci_row);
        TextView btnExt = findViewById(R.id.btn_ext);

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
            int pos = viewModel.getCursorPos().getValue() != null
                    ? viewModel.getCursorPos().getValue() : text.length();
            pos = Math.max(0, Math.min(pos, text.length()));

            SpannableStringBuilder sb = new SpannableStringBuilder();
            sb.append(text.substring(0, pos));
            int cursorStart = sb.length();
            sb.append(CURSOR_GLYPH);
            sb.setSpan(new ForegroundColorSpan(CURSOR_COLOR),
                    cursorStart, cursorStart + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            sb.append(text.substring(pos));
            expressionDisplay.setText(sb);
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

        // ── EXT toggle ───────────────────────────────────────────────────────
        btnExt.setOnClickListener(v -> {
            extendedMode = !extendedMode;
            sciRow.setVisibility(extendedMode ? View.VISIBLE : View.GONE);
            btnExt.setText(extendedMode ? "BASIC" : "EXT");
        });

        // ── Digits ───────────────────────────────────────────────────────────
        int[] numIds = {R.id.btn_0, R.id.btn_1, R.id.btn_2, R.id.btn_3, R.id.btn_4,
                        R.id.btn_5, R.id.btn_6, R.id.btn_7, R.id.btn_8, R.id.btn_9};
        for (int i = 0; i < numIds.length; i++) {
            final String digit = String.valueOf(i);
            findViewById(numIds[i]).setOnClickListener(v -> viewModel.insert(digit));
        }

        // ── Decimal ──────────────────────────────────────────────────────────
        findViewById(R.id.btn_decimal).setOnClickListener(v -> viewModel.insert("."));

        // ── Arithmetic operators ──────────────────────────────────────────────
        findViewById(R.id.btn_add).setOnClickListener(v -> viewModel.insert("+"));
        // Unicode minus U+2212 matches what ExpressionEvaluator tokenizes
        findViewById(R.id.btn_subtract).setOnClickListener(v -> viewModel.insert("\u2212"));
        // Unicode × U+00D7 and ÷ U+00F7
        findViewById(R.id.btn_multiply).setOnClickListener(v -> viewModel.insert("\u00D7"));
        findViewById(R.id.btn_divide).setOnClickListener(v -> viewModel.insert("\u00F7"));
        findViewById(R.id.btn_pow).setOnClickListener(v -> viewModel.insert("^"));
        findViewById(R.id.btn_percent).setOnClickListener(v -> viewModel.insert("%"));

        // ── Scientific functions ──────────────────────────────────────────────
        findViewById(R.id.btn_sin).setOnClickListener(v -> viewModel.insert("sin("));
        findViewById(R.id.btn_cos).setOnClickListener(v -> viewModel.insert("cos("));
        findViewById(R.id.btn_tan).setOnClickListener(v -> viewModel.insert("tan("));
        findViewById(R.id.btn_ln).setOnClickListener(v -> viewModel.insert("ln("));

        // ── Smart paren / backspace / AC / = ─────────────────────────────────
        findViewById(R.id.btn_paren).setOnClickListener(v -> viewModel.smartParen());
        findViewById(R.id.btn_backspace).setOnClickListener(v -> viewModel.backspace());
        findViewById(R.id.btn_clear).setOnClickListener(v -> viewModel.onClear());
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
            findViewById(varIds[i]).setOnClickListener(v ->
                    viewModel.onVariableTapped(varName));
        }
    }
}
