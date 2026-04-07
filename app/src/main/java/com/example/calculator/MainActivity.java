package com.example.calculator;

import android.os.Bundle;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import android.widget.TextView;

public class MainActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        CalculatorViewModel viewModel = new ViewModelProvider(this).get(CalculatorViewModel.class);

        TextView display = findViewById(R.id.display);
        RecyclerView historyList = findViewById(R.id.history_list);

        HistoryAdapter historyAdapter = new HistoryAdapter();
        historyList.setLayoutManager(new LinearLayoutManager(this));
        historyList.setAdapter(historyAdapter);

        // Observe ViewModel state
        viewModel.getDisplayText().observe(this, display::setText);
        viewModel.getHistory().observe(this, historyAdapter::submitList);
        viewModel.getErrorMessage().observe(this, msg -> {
            if (msg != null) {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show();
                viewModel.clearError();
            }
        });

        // Number buttons
        int[] numberIds = {
            R.id.btn_0, R.id.btn_1, R.id.btn_2, R.id.btn_3, R.id.btn_4,
            R.id.btn_5, R.id.btn_6, R.id.btn_7, R.id.btn_8, R.id.btn_9
        };
        for (int i = 0; i < numberIds.length; i++) {
            final int num = i;
            findViewById(numberIds[i]).setOnClickListener(v -> viewModel.onNumberClick(num));
        }

        // Operator buttons
        findViewById(R.id.btn_add).setOnClickListener(v -> viewModel.onOperatorClick("+"));
        findViewById(R.id.btn_subtract).setOnClickListener(v -> viewModel.onOperatorClick("-"));
        findViewById(R.id.btn_multiply).setOnClickListener(v -> viewModel.onOperatorClick("*"));
        findViewById(R.id.btn_divide).setOnClickListener(v -> viewModel.onOperatorClick("/"));
        findViewById(R.id.btn_percent).setOnClickListener(v -> viewModel.onOperatorClick("%"));
        findViewById(R.id.btn_decimal).setOnClickListener(v -> viewModel.onDecimalClick());
        findViewById(R.id.btn_equal).setOnClickListener(v -> viewModel.onEqualClick());
        findViewById(R.id.btn_clear).setOnClickListener(v -> viewModel.onClearClick());
    }
}
