package com.github.zaegan.infinitecalc;

import android.content.ClipData;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.text.InputType;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class RemapActivity extends AppCompatActivity {

    // ── Drag source encoding:
    //   "basic:rowIdx:slotIdx"       — basic row slot (move)
    //   "ext:pageIdx:row:slotIdx"    — ext page slot  (move)
    //   "palette:idx"                — custom palette  (copy, original stays)
    private static final String DRAG_BASIC   = "basic";
    private static final String DRAG_EXT     = "ext";
    private static final String DRAG_PALETTE = "palette";

    // ── Mutable working copy ─────────────────────────────────────────────────
    private ArrayList<ArrayList<ButtonDef>> basicRows;
    private ArrayList<ExtPageWork> extPages;
    private ArrayList<ButtonDef> customPalette;

    private static class ExtPageWork {
        ArrayList<ButtonDef> row1;
        ArrayList<ButtonDef> row2Middle;
        ExtPageWork(List<ButtonDef> r1, List<ButtonDef> r2) {
            row1       = new ArrayList<>(r1);
            row2Middle = new ArrayList<>(r2);
        }
    }

    private LinearLayout rowsContainer;
    private ScrollView scrollView;
    private TutorialManager tutorialManager;

    // Highlights all drop insert-zones during a drag
    private final List<View> activeInsertZones = new ArrayList<>();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            getWindow().setStatusBarColor(Color.parseColor("#121212"));
            getWindow().setNavigationBarColor(Color.parseColor("#121212"));
        }

        setContentView(R.layout.activity_remap);
        tutorialManager = new TutorialManager(this);

        scrollView    = findViewById(R.id.remap_scroll);
        rowsContainer = findViewById(R.id.remap_rows_container);

        // Deep-copy from prefs
        RemapConfig cfg = RemapConfig.load(
                getSharedPreferences("remap_prefs", MODE_PRIVATE));
        basicRows = new ArrayList<>();
        for (List<ButtonDef> row : cfg.basicRows) basicRows.add(new ArrayList<>(row));
        extPages = new ArrayList<>();
        for (RemapConfig.ExtPage p : cfg.extPages) extPages.add(new ExtPageWork(p.row1, p.row2Middle));
        customPalette = new ArrayList<>(cfg.customPalette);

        buildUI();

        findViewById(R.id.btn_remap_lock).setOnClickListener(v -> {
            saveConfig();
            setResult(RESULT_OK);
            finish();
        });
        findViewById(R.id.btn_remap_discard).setOnClickListener(v -> finish());
    }

    @Override public void onBackPressed() { finish(); }

    // ── Build the full scrollable UI ─────────────────────────────────────────

    private void buildUI() {
        rowsContainer.removeAllViews();

        // Control row (permanent)
        addSectionLabel("Controls");
        addPermanentRow(new String[]{"STO","REC","EXT","⌫","AC"},
                new int[]{0xFFFFB060,0xFF60B0FF,0xFFAAFFAA,0xFFFFAAAA,0xFFFF8080},
                new int[]{0xFF3A2000,0xFF001E3A,0xFF0A2A0A,0xFF2A1010,0xFF3A0000});

        // Ext pages
        addSectionLabel("Extended Buttons");
        for (int p = 0; p < extPages.size(); p++) {
            addExtPageRows(p);
        }
        // Phantom page drop zone
        addPhantomPage();

        // Basic rows
        addSectionLabel("Basic Buttons");
        for (int r = 0; r < basicRows.size(); r++) {
            addRemapRow("basic", r);
        }

        // Enter row (permanent)
        addSectionLabel("Enter");
        addPermanentRow(new String[]{"="},
                new int[]{0xFF80FF90}, new int[]{0xFF0A3A0A});

        // Custom palette
        addSectionLabel("Custom Buttons");
        addCustomPalette();
    }

    // ── Section helpers ───────────────────────────────────────────────────────

    private void addSectionLabel(String text) {
        TextView tv = new TextView(this);
        tv.setText(text);
        tv.setTextColor(0xFF888888);
        tv.setTextSize(11f);
        tv.setPadding(dp(10), dp(6), dp(10), dp(2));
        rowsContainer.addView(tv);
    }

    /** A non-draggable row of permanent buttons. */
    private void addPermanentRow(String[] labels, int[] textColors, int[] bgColors) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int n = labels.length;
        for (int i = 0; i < n; i++) {
            Button btn = makeButton(labels[i], textColors[i], bgColors[i], 1f, 52);
            btn.setAlpha(0.4f);
            btn.setClickable(false);
            row.addView(btn);
        }
        rowsContainer.addView(row);
    }

    private void addExtPageRows(int pageIdx) {
        ExtPageWork page = extPages.get(pageIdx);

        // Row 1
        addRemapRowExt(pageIdx, 0, page.row1, false);

        // Row 2: ‹ [remappable middle] ›
        LinearLayout row2 = new LinearLayout(this);
        row2.setOrientation(LinearLayout.HORIZONTAL);

        Button prev = makeButton("‹", 0xFF80AABB, 0xFF1A2A30, 1f, 52);
        prev.setAlpha(0.5f);
        prev.setClickable(false);
        row2.addView(prev);

        // Middle slots container
        LinearLayout mid = buildRemapRowLayout(
                page.row2Middle, DRAG_EXT + ":" + pageIdx + ":1", 3);
        LinearLayout.LayoutParams midLp = new LinearLayout.LayoutParams(
                0, dp(52), Math.max(page.row2Middle.size(), 1));
        row2.addView(mid, midLp);

        Button next = makeButton("›", 0xFF80AABB, 0xFF1A2A30, 1f, 52);
        next.setAlpha(0.5f);
        next.setClickable(false);
        row2.addView(next);

        rowsContainer.addView(row2);
    }

    // ── Custom palette ────────────────────────────────────────────────────────

    /**
     * Renders the custom palette: a wrapping row of existing custom buttons
     * (each draggable as a copy-source) plus a "＋ New" button.
     *
     * Palette drags are COPY operations — the palette entry is never removed.
     * Deleting a palette entry uses the small "×" button on each item.
     */
    private void addCustomPalette() {
        // Palette buttons are laid out in a wrapping horizontal row.
        // Use a LinearLayout that wraps content; for simplicity we allow one
        // long horizontal scroll row per custom button + the Create button.
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams rowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        row.setLayoutParams(rowLp);

        for (int i = 0; i < customPalette.size(); i++) {
            final int idx = i;
            ButtonDef def = customPalette.get(i);
            row.addView(makePaletteItem(def, idx));
        }

        // "＋ New" button
        Button addBtn = new Button(this);
        addBtn.setAllCaps(false);
        addBtn.setText("＋ New");
        addBtn.setTextColor(0xFFAAAAFF);
        addBtn.setBackgroundTintList(ColorStateList.valueOf(0xFF1A1A3A));
        LinearLayout.LayoutParams addLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(52));
        addLp.setMargins(dp(2), dp(2), dp(2), dp(2));
        addBtn.setLayoutParams(addLp);
        addBtn.setOnClickListener(v -> {
            if (!tutorialManager.isSeen(TutorialManager.ADD_BUTTON)) {
                tutorialManager.markSeen(TutorialManager.ADD_BUTTON);
                TutorialContent.Entry entry = TutorialContent.get(TutorialManager.ADD_BUTTON);
                new AlertDialog.Builder(this)
                        .setTitle(entry.title)
                        .setMessage(entry.body)
                        .setPositiveButton("Got it", (d, w) -> showCreateButtonDialog())
                        .setCancelable(false)
                        .show();
            } else {
                showCreateButtonDialog();
            }
        });
        row.addView(addBtn);

        rowsContainer.addView(row);

        // Hint if palette is empty
        if (customPalette.isEmpty()) {
            TextView hint = new TextView(this);
            hint.setText("Tap ＋ New to create a custom button, then drag it into any slot above.");
            hint.setTextColor(0xFF555555);
            hint.setTextSize(11f);
            hint.setPadding(dp(10), dp(2), dp(10), dp(8));
            rowsContainer.addView(hint);
        }
    }

    /**
     * A palette item: the draggable copy-source button with a small × delete badge.
     */
    private LinearLayout makePaletteItem(ButtonDef def, int paletteIdx) {
        LinearLayout wrapper = new LinearLayout(this);
        wrapper.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams wLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        wLp.setMargins(dp(2), dp(2), dp(2), dp(2));
        wrapper.setLayoutParams(wLp);

        Button btn = new Button(this);
        btn.setAllCaps(false);
        btn.setTextSize(13f);
        btn.setText(def.labelText);
        btn.setTextColor(def.textColor());
        btn.setBackgroundTintList(ColorStateList.valueOf(def.bgColor()));
        LinearLayout.LayoutParams btnLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(52));
        btn.setLayoutParams(btnLp);

        String palTag = DRAG_PALETTE + ":" + paletteIdx;
        btn.setOnLongClickListener(v -> {
            ClipData cd = ClipData.newPlainText("drag", palTag);
            View.DragShadowBuilder shadow = new View.DragShadowBuilder(v);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                v.startDragAndDrop(cd, shadow, palTag, 0);
            } else {
                //noinspection deprecation
                v.startDrag(cd, shadow, palTag, 0);
            }
            return true;
        });

        // Delete button
        Button del = new Button(this);
        del.setAllCaps(false);
        del.setText("×");
        del.setTextColor(0xFFFF6666);
        del.setTextSize(11f);
        del.setBackgroundTintList(ColorStateList.valueOf(0xFF2A0000));
        LinearLayout.LayoutParams delLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(22));
        del.setLayoutParams(delLp);
        del.setPadding(0, 0, 0, 0);
        del.setOnClickListener(v -> {
            customPalette.remove(paletteIdx);
            buildUI();
        });

        wrapper.addView(btn);
        wrapper.addView(del);
        return wrapper;
    }

    private void showCreateButtonDialog() {
        int padding = dp(16);
        int fieldMarginBottom = dp(12);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(padding, padding, padding, padding);

        // ── Mode toggle ───────────────────────────────────────────────────────
        LinearLayout modeRow = new LinearLayout(this);
        modeRow.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams modeRowLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        modeRowLp.bottomMargin = fieldMarginBottom;
        modeRow.setLayoutParams(modeRowLp);

        Button btnModeInsert = new Button(this);
        btnModeInsert.setAllCaps(false);
        btnModeInsert.setText("Insert Text");
        Button btnModeSet = new Button(this);
        btnModeSet.setAllCaps(false);
        btnModeSet.setText("Set Variable");

        LinearLayout.LayoutParams modeBtnLp = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        btnModeInsert.setLayoutParams(modeBtnLp);
        btnModeSet.setLayoutParams(new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f));
        modeRow.addView(btnModeInsert);
        modeRow.addView(btnModeSet);
        layout.addView(modeRow);

        // ── Insert section ────────────────────────────────────────────────────
        LinearLayout insertSection = new LinearLayout(this);
        insertSection.setOrientation(LinearLayout.VERTICAL);

        TextView insertLabelHint = new TextView(this);
        insertLabelHint.setText("Label (shown on button)");
        insertLabelHint.setTextColor(0xFF888888);
        insertLabelHint.setTextSize(12f);
        insertSection.addView(insertLabelHint);

        EditText insertLabelEdit = new EditText(this);
        insertLabelEdit.setInputType(InputType.TYPE_CLASS_TEXT);
        insertLabelEdit.setHint("e.g.  x²");
        insertLabelEdit.setHintTextColor(0xFF555555);
        insertLabelEdit.setTextColor(0xFFFFFFFF);
        insertLabelEdit.setBackgroundTintList(ColorStateList.valueOf(0xFF444444));
        LinearLayout.LayoutParams iLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        iLp.bottomMargin = fieldMarginBottom;
        insertLabelEdit.setLayoutParams(iLp);
        insertSection.addView(insertLabelEdit);

        TextView insertTextHint = new TextView(this);
        insertTextHint.setText("Insert text (typed into expression)");
        insertTextHint.setTextColor(0xFF888888);
        insertTextHint.setTextSize(12f);
        insertSection.addView(insertTextHint);

        EditText insertTextEdit = new EditText(this);
        insertTextEdit.setInputType(InputType.TYPE_CLASS_TEXT);
        insertTextEdit.setHint("e.g.  ^2");
        insertTextEdit.setHintTextColor(0xFF555555);
        insertTextEdit.setTextColor(0xFFFFFFFF);
        insertTextEdit.setBackgroundTintList(ColorStateList.valueOf(0xFF444444));
        insertSection.addView(insertTextEdit);

        layout.addView(insertSection);

        // ── Set Variable section ──────────────────────────────────────────────
        LinearLayout setSection = new LinearLayout(this);
        setSection.setOrientation(LinearLayout.VERTICAL);
        setSection.setVisibility(View.GONE);

        TextView setNameHint = new TextView(this);
        setNameHint.setText("Variable name (stored as _ic_<name>)");
        setNameHint.setTextColor(0xFF888888);
        setNameHint.setTextSize(12f);
        setSection.addView(setNameHint);

        EditText setNameEdit = new EditText(this);
        setNameEdit.setInputType(InputType.TYPE_CLASS_TEXT);
        setNameEdit.setHint("e.g.  result");
        setNameEdit.setHintTextColor(0xFF555555);
        setNameEdit.setTextColor(0xFFFFFFFF);
        setNameEdit.setBackgroundTintList(ColorStateList.valueOf(0xFF444444));
        LinearLayout.LayoutParams snLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        snLp.bottomMargin = fieldMarginBottom;
        setNameEdit.setLayoutParams(snLp);
        setSection.addView(setNameEdit);

        TextView setFromHint = new TextView(this);
        setFromHint.setText("FROM expression (use _ic_current for display value)");
        setFromHint.setTextColor(0xFF888888);
        setFromHint.setTextSize(12f);
        setSection.addView(setFromHint);

        EditText setFromEdit = new EditText(this);
        setFromEdit.setInputType(InputType.TYPE_CLASS_TEXT);
        setFromEdit.setText("_ic_current");
        setFromEdit.setTextColor(0xFFFFFFFF);
        setFromEdit.setBackgroundTintList(ColorStateList.valueOf(0xFF444444));
        LinearLayout.LayoutParams sfLp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        sfLp.bottomMargin = fieldMarginBottom;
        setFromEdit.setLayoutParams(sfLp);
        setSection.addView(setFromEdit);

        TextView setLabelHint = new TextView(this);
        setLabelHint.setText("Label (shown on button, optional)");
        setLabelHint.setTextColor(0xFF888888);
        setLabelHint.setTextSize(12f);
        setSection.addView(setLabelHint);

        EditText setLabelEdit = new EditText(this);
        setLabelEdit.setInputType(InputType.TYPE_CLASS_TEXT);
        setLabelEdit.setHint("e.g.  →result");
        setLabelEdit.setHintTextColor(0xFF555555);
        setLabelEdit.setTextColor(0xFFFFFFFF);
        setLabelEdit.setBackgroundTintList(ColorStateList.valueOf(0xFF444444));
        setSection.addView(setLabelEdit);

        layout.addView(setSection);

        // ── Mode toggle wiring ────────────────────────────────────────────────
        final boolean[] setMode = {false};

        btnModeInsert.setBackgroundTintList(ColorStateList.valueOf(0xFF1A3A1A));
        btnModeInsert.setTextColor(0xFFAAFFAA);
        btnModeSet.setBackgroundTintList(ColorStateList.valueOf(0xFF1A1A1A));
        btnModeSet.setTextColor(0xFF666666);

        btnModeInsert.setOnClickListener(v -> {
            setMode[0] = false;
            insertSection.setVisibility(View.VISIBLE);
            setSection.setVisibility(View.GONE);
            btnModeInsert.setBackgroundTintList(ColorStateList.valueOf(0xFF1A3A1A));
            btnModeInsert.setTextColor(0xFFAAFFAA);
            btnModeSet.setBackgroundTintList(ColorStateList.valueOf(0xFF1A1A1A));
            btnModeSet.setTextColor(0xFF666666);
        });
        btnModeSet.setOnClickListener(v -> {
            setMode[0] = true;
            insertSection.setVisibility(View.GONE);
            setSection.setVisibility(View.VISIBLE);
            btnModeSet.setBackgroundTintList(ColorStateList.valueOf(0xFF2A1A00));
            btnModeSet.setTextColor(0xFFFFAA40);
            btnModeInsert.setBackgroundTintList(ColorStateList.valueOf(0xFF1A1A1A));
            btnModeInsert.setTextColor(0xFF666666);
        });

        new AlertDialog.Builder(this)
                .setTitle("New Custom Button")
                .setView(layout)
                .setPositiveButton("Create", (dialog, which) -> {
                    if (setMode[0]) {
                        // Set Variable mode
                        String rawName = setNameEdit.getText().toString().trim()
                                .replaceAll("[^A-Za-z0-9_]", "");
                        if (rawName.isEmpty()) return;
                        String targetVar = "_ic_" + rawName;
                        String fromExpr = setFromEdit.getText().toString().trim();
                        if (fromExpr.isEmpty()) fromExpr = "_ic_current";
                        String label = setLabelEdit.getText().toString().trim();
                        if (label.isEmpty()) label = "\u2192" + rawName;
                        String insertText = "FROM " + fromExpr + " SET " + targetVar;
                        customPalette.add(new ButtonDef(insertText, label));
                    } else {
                        // Insert Text mode
                        String label  = insertLabelEdit.getText().toString().trim();
                        String insert = CalculatorEditText.sanitize(
                                insertTextEdit.getText().toString());
                        if (label.isEmpty()) label = insert;
                        if (insert.isEmpty()) return;
                        customPalette.add(new ButtonDef(insert, label));
                    }
                    buildUI();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void addRemapRow(String section, int rowIdx) {
        List<ButtonDef> slots = basicRows.get(rowIdx);
        LinearLayout row = buildRemapRowLayout(slots,
                section + ":" + rowIdx, 5);
        rowsContainer.addView(row);
    }

    private void addRemapRowExt(int pageIdx, int rowIn, List<ButtonDef> slots, boolean isMid) {
        int maxSlots = isMid ? 3 : 5;
        LinearLayout row = buildRemapRowLayout(slots,
                DRAG_EXT + ":" + pageIdx + ":" + rowIn, maxSlots);
        rowsContainer.addView(row);
    }

    /**
     * Build a horizontal row with interleaved insert-zones and draggable button views.
     *
     * @param slots    current button definitions
     * @param sourceKey encoded source prefix used to identify this row
     * @param maxSlots maximum buttons this row accepts (5 for most, 3 for ext row2 middle)
     */
    private LinearLayout buildRemapRowLayout(List<ButtonDef> slots,
                                             String sourceKey, int maxSlots) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        int n = slots.size();

        // Insert zone before slot 0
        row.addView(makeInsertZone(sourceKey, 0, n, maxSlots));

        for (int i = 0; i < n; i++) {
            ButtonDef def = slots.get(i);
            float weight = (n == 3 && i == 0) ? 2f : 1f;
            Button btn = makeDraggableButton(def, sourceKey, i, weight);
            row.addView(btn);

            // Insert zone after each slot
            row.addView(makeInsertZone(sourceKey, i + 1, n, maxSlots));
        }
        return row;
    }

    // ── Phantom page ──────────────────────────────────────────────────────────

    private void addPhantomPage() {
        LinearLayout phantom = new LinearLayout(this);
        phantom.setOrientation(LinearLayout.VERTICAL);
        phantom.setGravity(Gravity.CENTER);
        phantom.setPadding(dp(8), dp(4), dp(8), dp(4));

        GradientDrawable border = new GradientDrawable();
        border.setColor(0xFF1A1A1A);
        border.setStroke(dp(1), 0xFF444444);
        border.setCornerRadius(dp(4));
        phantom.setBackground(border);

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(56));
        lp.setMargins(dp(6), dp(4), dp(6), dp(4));
        phantom.setLayoutParams(lp);

        TextView label = new TextView(this);
        label.setText("Drop here to add a new page");
        label.setTextColor(0xFF555555);
        label.setTextSize(12f);
        label.setGravity(Gravity.CENTER);
        phantom.addView(label);

        phantom.setOnDragListener((v, event) -> {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return true;
                case DragEvent.ACTION_DRAG_ENTERED:
                    border.setStroke(dp(2), 0xFF80FF90);
                    label.setTextColor(0xFF80FF90);
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    border.setStroke(dp(1), 0xFF444444);
                    label.setTextColor(0xFF555555);
                    return true;
                case DragEvent.ACTION_DROP:
                    border.setStroke(dp(1), 0xFF444444);
                    label.setTextColor(0xFF555555);
                    String tag = (String) event.getClipData().getItemAt(0).getText();
                    dropOntoPhantomPage(tag);
                    return true;
                default:
                    return false;
            }
        });

        rowsContainer.addView(phantom);
    }

    private void dropOntoPhantomPage(String sourceTag) {
        int[] src = resolveSource(sourceTag);
        if (src == null) return;
        ButtonDef def = removeFromSource(src);
        if (def == null) return;

        // Create new ext page with this button in row1
        ArrayList<ButtonDef> newRow1 = new ArrayList<>();
        newRow1.add(def);
        extPages.add(new ExtPageWork(newRow1, new ArrayList<>()));
        buildUI();
    }

    // ── Drop resolution helpers ───────────────────────────────────────────────

    /**
     * Parse source tag into int[] {type, pageOrRow, rowInPage, slotIdx}.
     * type: 0=basic, 1=ext, 2=palette (copy — never removed from source)
     */
    private int[] resolveSource(String tag) {
        String[] parts = tag.split(":");
        try {
            if (DRAG_BASIC.equals(parts[0])) {
                return new int[]{0, Integer.parseInt(parts[1]), -1, Integer.parseInt(parts[2])};
            } else if (DRAG_PALETTE.equals(parts[0])) {
                return new int[]{2, Integer.parseInt(parts[1]), -1, -1};
            } else {
                return new int[]{1, Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]), Integer.parseInt(parts[3])};
            }
        } catch (Exception e) { return null; }
    }

    /**
     * Remove the button at the source location and return it.
     * For palette sources (type=2) the entry is NOT removed — it is copied.
     */
    private ButtonDef removeFromSource(int[] src) {
        int type = src[0], idx = src[1], row = src[2], slot = src[3];
        if (type == 2) {
            // Palette: copy only, never remove
            return idx < customPalette.size() ? customPalette.get(idx) : null;
        }
        if (type == 0) {
            if (idx < basicRows.size() && slot < basicRows.get(idx).size()) {
                return basicRows.get(idx).remove(slot);
            }
        } else {
            if (idx < extPages.size()) {
                ExtPageWork p = extPages.get(idx);
                List<ButtonDef> rowList = (row == 0) ? p.row1 : p.row2Middle;
                if (slot < rowList.size()) {
                    ButtonDef def = rowList.remove(slot);
                    // Remove ext page if both rows are now empty
                    if (p.row1.isEmpty() && p.row2Middle.isEmpty()) {
                        extPages.remove(idx);
                    }
                    return def;
                }
            }
        }
        return null;
    }

    // ── Draggable button factory ──────────────────────────────────────────────

    private Button makeDraggableButton(ButtonDef def, String rowKey, int slotIdx, float weight) {
        Button btn = makeButton(def.labelText, def.textColor(), def.bgColor(), weight, 52);
        // Drag tag encodes full source location: rowKey + ":" + slotIdx
        String dragTag = rowKey + ":" + slotIdx;

        btn.setOnLongClickListener(v -> {
            ClipData cd = ClipData.newPlainText("drag", dragTag);
            View.DragShadowBuilder shadow = new View.DragShadowBuilder(v);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                v.startDragAndDrop(cd, shadow, dragTag, 0);
            } else {
                //noinspection deprecation
                v.startDrag(cd, shadow, dragTag, 0);
            }
            return true;
        });

        // SWAP drop target: dropping onto this button's center swaps the two buttons
        btn.setOnDragListener((v, event) -> {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    return true;
                case DragEvent.ACTION_DRAG_ENTERED:
                    v.setAlpha(0.5f);
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    v.setAlpha(1f);
                    return true;
                case DragEvent.ACTION_DROP: {
                    v.setAlpha(1f);
                    String srcTag = (String) event.getLocalState();
                    if (srcTag == null) srcTag = (String) event.getClipData().getItemAt(0).getText();
                    if (dragTag.equals(srcTag)) return false; // dropped onto itself
                    performSwap(srcTag, rowKey, slotIdx);
                    return true;
                }
                case DragEvent.ACTION_DRAG_ENDED:
                    v.setAlpha(1f);
                    return true;
                default:
                    return false;
            }
        });
        return btn;
    }

    /** Swap the button at srcTag with the button at (destRowKey, destSlot). */
    private void performSwap(String srcTag, String destRowKey, int destSlot) {
        int[] src = resolveSource(srcTag);
        if (src == null) return;

        // Palette drag onto a button: insert the copy before the target button.
        if (src[0] == 2) {
            performInsert(srcTag, destRowKey, destSlot);
            return;
        }

        int[] dest = resolveSource(destRowKey + ":" + destSlot);
        if (dest == null) return;

        List<ButtonDef> srcList  = getSlotList(src);
        List<ButtonDef> destList = getSlotList(dest);
        if (srcList == null || destList == null) return;

        int srcSlot = src[3];
        int dstSlot = dest[3];
        if (srcSlot >= srcList.size() || dstSlot >= destList.size()) return;

        ButtonDef tmp = srcList.get(srcSlot);
        srcList.set(srcSlot, destList.get(dstSlot));
        destList.set(dstSlot, tmp);
        buildUI();
    }

    private List<ButtonDef> getSlotList(int[] loc) {
        if (loc[0] == 0) {
            return loc[1] < basicRows.size() ? basicRows.get(loc[1]) : null;
        } else {
            if (loc[1] >= extPages.size()) return null;
            ExtPageWork p = extPages.get(loc[1]);
            return loc[2] == 0 ? p.row1 : p.row2Middle;
        }
    }

    // ── Insert zone factory ───────────────────────────────────────────────────

    /**
     * A thin vertical strip that highlights on drag-enter and inserts the dragged
     * button at {@code insertIndex} when dropped.
     */
    private View makeInsertZone(String rowKey, int insertIndex, int currentCount, int maxSlots) {
        View zone = new View(this);
        boolean canInsert = currentCount < maxSlots;

        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(dp(canInsert ? 10 : 4), dp(52));
        lp.setMargins(0, dp(2), 0, dp(2));
        zone.setLayoutParams(lp);
        zone.setBackgroundColor(Color.TRANSPARENT);

        if (!canInsert) {
            // Row is full — insert zones are just spacers, not targets
            return zone;
        }

        activeInsertZones.add(zone);

        zone.setOnDragListener((v, event) -> {
            switch (event.getAction()) {
                case DragEvent.ACTION_DRAG_STARTED:
                    v.setBackgroundColor(0x2280FF90);
                    return true;
                case DragEvent.ACTION_DRAG_ENTERED:
                    v.setBackgroundColor(0xAA80FF90);
                    return true;
                case DragEvent.ACTION_DRAG_EXITED:
                    v.setBackgroundColor(0x2280FF90);
                    return true;
                case DragEvent.ACTION_DROP: {
                    v.setBackgroundColor(Color.TRANSPARENT);
                    String srcTag = (String) event.getLocalState();
                    if (srcTag == null) srcTag = (String) event.getClipData().getItemAt(0).getText();
                    performInsert(srcTag, rowKey, insertIndex);
                    return true;
                }
                case DragEvent.ACTION_DRAG_ENDED:
                    v.setBackgroundColor(Color.TRANSPARENT);
                    return true;
                default:
                    return false;
            }
        });
        return zone;
    }

    /**
     * Remove button from source, insert it at {@code insertIndex} in the destination row.
     * The destination row is identified by {@code destRowKey} using the same encoding
     * as drag tags (without the slot suffix).
     */
    private void performInsert(String srcTag, String destRowKey, int insertIndex) {
        int[] src = resolveSource(srcTag);
        if (src == null) return;

        // Resolve dest row list from destRowKey
        List<ButtonDef> destList = resolveRowList(destRowKey);
        if (destList == null) return;

        // Adjust insertIndex if source and dest are the same row and src slot < insertIndex
        boolean sameRow = isSameRow(src, destRowKey);
        int srcSlot = src[3];

        ButtonDef def = removeFromSource(src);
        if (def == null) return;

        // If same row and we removed a slot before insertIndex, shift target index down
        int adjustedIndex = insertIndex;
        if (sameRow && srcSlot < insertIndex) adjustedIndex--;
        adjustedIndex = Math.max(0, Math.min(adjustedIndex, destList.size()));

        destList.add(adjustedIndex, def);
        buildUI();
    }

    private List<ButtonDef> resolveRowList(String rowKey) {
        String[] parts = rowKey.split(":");
        try {
            if (DRAG_BASIC.equals(parts[0])) {
                int r = Integer.parseInt(parts[1]);
                return r < basicRows.size() ? basicRows.get(r) : null;
            } else {
                int p = Integer.parseInt(parts[1]);
                int r = Integer.parseInt(parts[2]);
                if (p >= extPages.size()) return null;
                return r == 0 ? extPages.get(p).row1 : extPages.get(p).row2Middle;
            }
        } catch (Exception e) { return null; }
    }

    private boolean isSameRow(int[] src, String destRowKey) {
        if (src[0] == 2) return false; // palette is never "same row" as a layout row
        String[] parts = destRowKey.split(":");
        try {
            if (src[0] == 0 && DRAG_BASIC.equals(parts[0])) {
                return src[1] == Integer.parseInt(parts[1]);
            } else if (src[0] == 1 && DRAG_EXT.equals(parts[0])) {
                return src[1] == Integer.parseInt(parts[1])
                        && src[2] == Integer.parseInt(parts[2]);
            }
        } catch (Exception ignored) {}
        return false;
    }

    // ── Button factory ────────────────────────────────────────────────────────

    private Button makeButton(String label, int textColor, int bgColor, float weight, int heightDp) {
        Button btn = new Button(this);
        btn.setAllCaps(false);
        btn.setTextSize(13f);
        btn.setText(label);
        btn.setTextColor(textColor);
        btn.setBackgroundTintList(ColorStateList.valueOf(bgColor));
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, dp(heightDp), weight);
        lp.setMargins(dp(2), dp(2), dp(2), dp(2));
        btn.setLayoutParams(lp);
        return btn;
    }

    // ── Persist ───────────────────────────────────────────────────────────────

    private void saveConfig() {
        ArrayList<List<ButtonDef>> basic = new ArrayList<>();
        for (ArrayList<ButtonDef> row : basicRows) basic.add(row);

        ArrayList<RemapConfig.ExtPage> ext = new ArrayList<>();
        for (ExtPageWork p : extPages) ext.add(new RemapConfig.ExtPage(p.row1, p.row2Middle));

        RemapConfig cfg = new RemapConfig(basic, ext, new ArrayList<>(customPalette));
        cfg.save(getSharedPreferences("remap_prefs", MODE_PRIVATE));
    }

    // ── Utils ─────────────────────────────────────────────────────────────────

    private int dp(int val) {
        return Math.round(val * getResources().getDisplayMetrics().density);
    }
}
