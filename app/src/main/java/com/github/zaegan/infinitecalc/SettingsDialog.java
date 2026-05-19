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
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.DialogFragment;
import androidx.lifecycle.ViewModelProvider;

import com.google.android.material.switchmaterial.SwitchMaterial;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
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

        view.findViewById(R.id.btn_privacy_policy).setOnClickListener(v ->
                startActivity(new Intent(Intent.ACTION_VIEW,
                        Uri.parse(getString(R.string.privacy_policy_url)))));

        view.findViewById(R.id.btn_settings_close).setOnClickListener(v -> dismiss());

        return new MaterialAlertDialogBuilder(requireContext())
                .setView(view)
                .create();
    }

    // ── Export history ────────────────────────────────────────────────────────

    private void exportHistory() {
        Context ctx = requireContext();
        new Thread(() -> {
            try {
                HistoryDatabase.getInstance(ctx)
                        .getReadableDatabase()
                        .rawQuery("PRAGMA wal_checkpoint(TRUNCATE)", null)
                        .close();

                File src = ctx.getDatabasePath("calc_history.db");
                byte[] data;
                try (FileInputStream in = new FileInputStream(src);
                     ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
                    byte[] buf = new byte[8192];
                    int n;
                    while ((n = in.read(buf)) != -1) baos.write(buf, 0, n);
                    data = baos.toByteArray();
                }

                writeToDownloads(ctx, "infinitecalc_history.db", data, "application/octet-stream");

                if (isAdded()) {
                    requireActivity().runOnUiThread(() ->
                            Toast.makeText(ctx,
                                    "Saved to Downloads as \"infinitecalc_history.db\"",
                                    Toast.LENGTH_LONG).show());
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

            byte[] data = root.toString(2).getBytes("UTF-8");
            writeToDownloads(ctx, "infinitecalc_config.json", data, "application/json");

            Toast.makeText(ctx,
                    "Saved to Downloads as \"infinitecalc_config.json\"",
                    Toast.LENGTH_LONG).show();

        } catch (Exception e) {
            Toast.makeText(ctx, "Export failed: " + e.getMessage(),
                    Toast.LENGTH_SHORT).show();
        }
    }

    // ── Downloads helper ──────────────────────────────────────────────────────

    private static void writeToDownloads(Context ctx, String filename, byte[] data,
                                         String mimeType) throws Exception {
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            android.content.ContentValues values = new android.content.ContentValues();
            values.put(android.provider.MediaStore.Downloads.DISPLAY_NAME, filename);
            values.put(android.provider.MediaStore.Downloads.MIME_TYPE, mimeType);
            values.put(android.provider.MediaStore.Downloads.IS_PENDING, 1);
            android.net.Uri collection = android.provider.MediaStore.Downloads.getContentUri(
                    android.provider.MediaStore.VOLUME_EXTERNAL_PRIMARY);
            android.net.Uri itemUri = ctx.getContentResolver().insert(collection, values);
            if (itemUri == null) throw new Exception("Could not create Downloads file.");
            try (java.io.OutputStream os = ctx.getContentResolver().openOutputStream(itemUri)) {
                if (os == null) throw new Exception("Could not open Downloads file for writing.");
                os.write(data);
            }
            values.clear();
            values.put(android.provider.MediaStore.Downloads.IS_PENDING, 0);
            ctx.getContentResolver().update(itemUri, values, null, null);
        } else {
            File downloadsDir = android.os.Environment.getExternalStoragePublicDirectory(
                    android.os.Environment.DIRECTORY_DOWNLOADS);
            File dest = new File(downloadsDir, filename);
            if (dest.exists()) {
                String base = filename.contains(".") ? filename.substring(0, filename.lastIndexOf('.')) : filename;
                String ext  = filename.contains(".") ? filename.substring(filename.lastIndexOf('.')) : "";
                for (int n = 1; n < 10000; n++) {
                    dest = new File(downloadsDir, base + " (" + n + ")" + ext);
                    if (!dest.exists()) break;
                }
            }
            try (java.io.FileOutputStream fos = new java.io.FileOutputStream(dest)) {
                fos.write(data);
            }
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

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
