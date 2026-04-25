package com.github.zaegan.infinitecalc;

import android.app.Dialog;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
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

        // ── Negation-First Mode ───────────────────────────────────────────────
        SwitchMaterial switchNeg = view.findViewById(R.id.switch_negation_mode);
        Boolean current = viewModel.getNegationFirstMode().getValue();
        switchNeg.setChecked(current != null && current);
        switchNeg.setOnCheckedChangeListener((btn, isChecked) -> {
            Boolean live = viewModel.getNegationFirstMode().getValue();
            boolean liveVal = live != null && live;
            if (isChecked != liveVal) viewModel.toggleNegationFirstMode();
        });

        // ── Theme selector ────────────────────────────────────────────────────
        SharedPreferences prefs = requireActivity()
                .getSharedPreferences("remap_prefs", android.content.Context.MODE_PRIVATE);
        String currentTheme = prefs.getString("theme_mode", "system");

        Button btnDark   = view.findViewById(R.id.btn_theme_dark);
        Button btnLight  = view.findViewById(R.id.btn_theme_light);
        Button btnSystem = view.findViewById(R.id.btn_theme_system);

        updateThemeButtons(btnDark, btnLight, btnSystem, currentTheme);

        View.OnClickListener themeClick = v -> {
            String mode;
            if (v == btnDark)        mode = "dark";
            else if (v == btnLight)  mode = "light";
            else                     mode = "system";

            prefs.edit().putString("theme_mode", mode).apply();
            MainActivity.applyThemeMode(mode);
            dismiss();
            requireActivity().recreate();
        };
        btnDark.setOnClickListener(themeClick);
        btnLight.setOnClickListener(themeClick);
        btnSystem.setOnClickListener(themeClick);

        view.findViewById(R.id.btn_settings_close).setOnClickListener(v -> dismiss());

        return new AlertDialog.Builder(requireContext())
                .setView(view)
                .create();
    }

    private void updateThemeButtons(Button dark, Button light, Button system, String active) {
        styleThemeBtn(dark,   "dark".equals(active));
        styleThemeBtn(light,  "light".equals(active));
        styleThemeBtn(system, "system".equals(active));
    }

    private void styleThemeBtn(Button btn, boolean selected) {
        int textColor = ContextCompat.getColor(requireContext(),
                selected ? R.color.btn_equal_text : R.color.text_muted);
        int bgColor = ContextCompat.getColor(requireContext(),
                selected ? R.color.btn_equal_bg : R.color.remap_mode_off_bg);
        btn.setTextColor(textColor);
        btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(bgColor));
    }
}
