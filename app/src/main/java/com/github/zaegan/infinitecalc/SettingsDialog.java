package com.github.zaegan.infinitecalc;

import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.core.content.FileProvider;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.switchmaterial.SwitchMaterial;

import org.json.JSONObject;

import java.io.File;
import java.io.FileOutputStream;
import java.util.Map;

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
                .getSharedPreferences("remap_prefs", Context.MODE_PRIVATE);
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

        // ── Export / Import ───────────────────────────────────────────────────
        view.findViewById(R.id.btn_export_history).setOnClickListener(v -> exportHistory());
        view.findViewById(R.id.btn_export_config).setOnClickListener(v -> exportConfig());

        view.findViewById(R.id.btn_import_history).setOnClickListener(v -> {
            dismiss();
            ((MainActivity) requireActivity()).launchHistoryImport();
        });
        view.findViewById(R.id.btn_import_config).setOnClickListener(v -> {
            dismiss();
            ((MainActivity) requireActivity()).launchConfigImport();
        });

        view.findViewById(R.id.btn_settings_close).setOnClickListener(v -> dismiss());

        return new AlertDialog.Builder(requireContext())
                .setView(view)
                .create();
    }

    // ── Export history ────────────────────────────────────────────────────────

    private void exportHistory() {
        Context ctx = requireContext();
        new Thread(() -> {
            try {
                // Flush WAL so the main db file is complete
                HistoryDatabase.getInstance(ctx)
                        .getReadableDatabase()
                        .rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null)
                        .close();

                File src = ctx.getDatabasePath("calc_history.db");
                File dst = new File(ctx.getCacheDir(), "infinitecalc_history.db");
                copyFile(src, dst);

                Uri uri = FileProvider.getUriForFile(
                        ctx, ctx.getPackageName() + ".fileprovider", dst);

                Intent intent = new Intent(Intent.ACTION_SEND);
                intent.setType("application/octet-stream");
                intent.putExtra(Intent.EXTRA_STREAM, uri);
                intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);

                if (isAdded()) {
                    requireActivity().runOnUiThread(() ->
                            startActivity(Intent.createChooser(intent, "Export History")));
                }
            } catch (Exception e) {
                if (isAdded()) {
                    requireActivity().runOnUiThread(() ->
                            Toast.makeText(ctx, "Export failed: " + e.getMessage(),
                                    Toast.LENGTH_SHORT).show());
                }
            }
        }).start();
    }

    // ── Export config ─────────────────────────────────────────────────────────

    private void exportConfig() {
        Context ctx = requireContext();
        try {
            JSONObject root = new JSONObject();
            root.put("version", 1);

            SharedPreferences remapPrefs =
                    ctx.getSharedPreferences("remap_prefs", Context.MODE_PRIVATE);
            RemapConfig cfg = RemapConfig.load(remapPrefs);
            root.put("remap", cfg.toJson());
            root.put("theme", remapPrefs.getString("theme_mode", "system"));

            JSONObject varsJson = new JSONObject();
            for (Map.Entry<String, ?> e :
                    ctx.getSharedPreferences("calculator_vars", Context.MODE_PRIVATE)
                       .getAll().entrySet()) {
                if (e.getValue() instanceof Float)
                    varsJson.put(e.getKey(), ((Float) e.getValue()).doubleValue());
            }
            root.put("vars", varsJson);

            JSONObject customVarsJson = new JSONObject();
            for (Map.Entry<String, ?> e :
                    ctx.getSharedPreferences("ic_custom_vars", Context.MODE_PRIVATE)
                       .getAll().entrySet()) {
                if (e.getValue() instanceof Float)
                    customVarsJson.put(e.getKey(), ((Float) e.getValue()).doubleValue());
            }
            root.put("customVars", customVarsJson);

            File outFile = new File(ctx.getCacheDir(), "infinitecalc_config.json");
            try (FileOutputStream fos = new FileOutputStream(outFile)) {
                fos.write(root.toString(2).getBytes("UTF-8"));
            }

            Uri uri = FileProvider.getUriForFile(
                    ctx, ctx.getPackageName() + ".fileprovider", outFile);

            Intent intent = new Intent(Intent.ACTION_SEND);
            intent.setType("application/json");
            intent.putExtra(Intent.EXTRA_STREAM, uri);
            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            startActivity(Intent.createChooser(intent, "Export Config"));

        } catch (Exception e) {
            Toast.makeText(ctx, "Export failed: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static void copyFile(File src, File dst) throws java.io.IOException {
        try (java.io.FileInputStream in  = new java.io.FileInputStream(src);
             java.io.FileOutputStream out = new java.io.FileOutputStream(dst)) {
            byte[] buf = new byte[8192];
            int n;
            while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
        }
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
        btn.setBackgroundTintList(
                android.content.res.ColorStateList.valueOf(bgColor));
    }
}
