package com.github.zaegan.infinitecalc;

import android.app.Dialog;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.switchmaterial.SwitchMaterial;

public class SettingsDialog extends DialogFragment {

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        CalculatorViewModel viewModel =
                new ViewModelProvider(requireActivity()).get(CalculatorViewModel.class);

        View view = LayoutInflater.from(requireContext())
                .inflate(R.layout.dialog_settings, null);

        SwitchMaterial switchNeg = view.findViewById(R.id.switch_negation_mode);

        // Initialise switch state from current ViewModel value
        Boolean current = viewModel.getNegationFirstMode().getValue();
        switchNeg.setChecked(current != null && current);

        switchNeg.setOnCheckedChangeListener((btn, isChecked) -> {
            Boolean live = viewModel.getNegationFirstMode().getValue();
            boolean liveVal = live != null && live;
            if (isChecked != liveVal) {
                viewModel.toggleNegationFirstMode();
            }
        });

        view.findViewById(R.id.btn_settings_close).setOnClickListener(v -> dismiss());

        return new AlertDialog.Builder(requireContext())
                .setView(view)
                .create();
    }
}
