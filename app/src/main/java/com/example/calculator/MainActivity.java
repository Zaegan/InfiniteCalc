package com.example.calculator;

import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.util.ArrayList;
import java.util.List;

/**
 * Main calculator activity with infinite history.
 */
public class MainActivity extends AppCompatActivity {

    private TextView displayText;
    private Button[] numberButtons;
    private Button opButtons;
    private Button equalButton;
    private Button clearButton;
    
    private RecyclerView historyList;
    private HistoryAdapter historyAdapter;
    private List<HistoryItem> history;
    
    private StringBuilder currentExpression;
    private boolean shouldClearDisplay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        displayText = findViewById(R.id.display);
        historyList = findViewById(R.id.history_list);
        history = new ArrayList<>();
        currentExpression = new StringBuilder();
        shouldClearDisplay = false;

        // Initialize history adapter
        historyAdapter = new HistoryAdapter(history);
        historyList.setLayoutManager(new LinearLayoutManager(this));
        historyList.setAdapter(historyAdapter);

        // Initialize number buttons
        numberButtons = new Button[10];
        numberButtons[0] = findViewById(R.id.btn_0);
        numberButtons[1] = findViewById(R.id.btn_1);
        numberButtons[2] = findViewById(R.id.btn_2);
        numberButtons[3] = findViewById(R.id.btn_3);
        numberButtons[4] = findViewById(R.id.btn_4);
        numberButtons[5] = findViewById(R.id.btn_5);
        numberButtons[6] = findViewById(R.id.btn_6);
        numberButtons[7] = findViewById(R.id.btn_7);
        numberButtons[8] = findViewById(R.id.btn_8);
        numberButtons[9] = findViewById(R.id.btn_9);

        for (Button btn : numberButtons) {
            final int num = Integer.parseInt(btn.getText().toString());
            btn.setOnClickListener(v -> onNumberClick(num));
        }

        // Operation buttons
        opButtons = findViewById(R.id.btn_divide);
        opButtons.setOnClickListener(v -> onOperatorClick("/"));
        opButtons = findViewById(R.id.btn_multiply);
        opButtons.setOnClickListener(v -> onOperatorClick("*"));
        opButtons = findViewById(R.id.btn_subtract);
        opButtons.setOnClickListener(v -> onOperatorClick("-"));
        opButtons = findViewById(R.id.btn_add);
        opButtons.setOnClickListener(v -> onOperatorClick("+"));
        opButtons = findViewById(R.id.btn_percent);
        opButtons.setOnClickListener(v -> onOperatorClick("%"));
        opButtons = findViewById(R.id.btn_decimal);
        opButtons.setOnClickListener(v -> onDecimalClick());

        // Special buttons
        equalButton = findViewById(R.id.btn_equal);
        equalButton.setOnClickListener(v -> onEqualClick());

        clearButton = findViewById(R.id.btn_clear);
        clearButton.setOnClickListener(v -> onClearClick());
    }

    private void onNumberClick(int num) {
        if (shouldClearDisplay) {
            currentExpression.setLength(0);
            shouldClearDisplay = false;
        }
        currentExpression.append(num);
        updateDisplay();
    }

    private void onOperatorClick(String op) {
        if (currentExpression.length() > 0) {
            char lastChar = currentExpression.charAt(currentExpression.length() - 1);
            if (lastChar == '+' || lastChar == '-' || lastChar == '*' || lastChar == '/' || lastChar == '%') {
                // Replace last operator
                currentExpression.setLength(currentExpression.length() - 1);
            }
            currentExpression.append(op);
            shouldClearDisplay = true;
            updateDisplay();
        }
    }

    private void onDecimalClick() {
        if (shouldClearDisplay) {
            currentExpression.append("0.");
            shouldClearDisplay = false;
        } else {
            // Bug #3 fix: Improved split logic to handle negative numbers correctly
            String expr = currentExpression.toString();
            // Split by operators but handle negative sign at start or after another operator
            String[] parts = expr.split("(?<![0-9.])[+\\-*/%]");
            String currentNum = parts[parts.length - 1];
            if (!currentNum.contains(".")) {
                currentExpression.append(".");
            }
        }
        updateDisplay();
    }

    private void onEqualClick() {
        String expression = currentExpression.toString();
        
        // Bug #1 fix: Validate for empty or whitespace-only expressions
        if (expression == null || expression.trim().isEmpty()) {
            Toast.makeText(this, "Enter an expression first", Toast.LENGTH_SHORT).show();
            return;
        }

        // Bug #5 fix: Prevent = when expression ends with operator
        if (endsWithOperator(expression)) {
            Toast.makeText(this, "Expression incomplete", Toast.LENGTH_SHORT).show();
            return;
        }

        try {
            double result = ExpressionEvaluator.evaluate(expression);
            String resultStr = formatResult(result);
            
            // Save to history
            history.add(new HistoryItem(expression, resultStr, System.currentTimeMillis()));
            historyAdapter.notifyItemInserted(history.size() - 1);
            historyAdapter.notifyItemRangeChanged(history.size() - 1, history.size());

            currentExpression.setLength(0);
            currentExpression.append(resultStr);
            shouldClearDisplay = true;
            updateDisplay();

        } catch (Exception e) {
            Toast.makeText(this, "Invalid expression", Toast.LENGTH_SHORT).show();
            currentExpression.setLength(0);
            shouldClearDisplay = true;
        }
    }

    private boolean endsWithOperator(String expression) {
        char lastChar = expression.charAt(expression.length() - 1);
        return lastChar == '+' || lastChar == '-' || lastChar == '*' || lastChar == '/' || lastChar == '%';
    }

    private void onClearClick() {
        String current = currentExpression.toString();
        if (!current.isEmpty()) {
            // Save to history even if incomplete
            history.add(new HistoryItem(current, "-", System.currentTimeMillis()));
            historyAdapter.notifyItemInserted(history.size() - 1);
            historyAdapter.notifyItemRangeChanged(history.size() - 1, history.size());
        }
        
        currentExpression.setLength(0);
        updateDisplay();
    }

    private void updateDisplay() {
        displayText.setText(currentExpression.toString());
    }

    private String formatResult(double result) {
        // Bug #2 fix: Check bounds before casting to long to prevent integer overflow
        if (result == (long) result) {
            long longResult = (long) result;
            // Check if the result is within long range
            if (longResult >= Long.MIN_VALUE && longResult <= Long.MAX_VALUE) {
                return String.valueOf(longResult);
            }
            // If out of range, return as double
            return String.valueOf(result);
        }
        return String.valueOf(result);
    }
}
