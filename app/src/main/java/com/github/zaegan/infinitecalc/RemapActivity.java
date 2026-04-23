package com.github.zaegan.infinitecalc;

import android.content.ClipData;
import android.content.res.ColorStateList;
import android.graphics.Color;
import android.graphics.drawable.GradientDrawable;
import android.os.Build;
import android.os.Bundle;
import android.view.DragEvent;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import java.util.ArrayList;
import java.util.List;

public class RemapActivity extends AppCompatActivity {

    // ── Drag source encoding: "basic:rowIdx:slotIdx" or "ext:pageIdx:row:slotIdx"
    private static final String DRAG_BASIC = "basic";
    private static final String DRAG_EXT   = "ext";

    // ── Mutable working copy ─────────────────────────────────────────────────
    private ArrayList<ArrayList<ButtonDef>> basicRows;
    private ArrayList<ExtPageWork> extPages;

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

        scrollView    = findViewById(R.id.remap_scroll);
        rowsContainer = findViewById(R.id.remap_rows_container);

        // Deep-copy from prefs
        RemapConfig cfg = RemapConfig.load(
                getSharedPreferences("remap_prefs", MODE_PRIVATE));
        basicRows = new ArrayList<>();
        for (List<ButtonDef> row : cfg.basicRows) basicRows.add(new ArrayList<>(row));
        extPages = new ArrayList<>();
        for (RemapConfig.ExtPage p : cfg.extPages) extPages.add(new ExtPageWork(p.row1, p.row2Middle));

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
     * type: 0=basic, 1=ext
     */
    private int[] resolveSource(String tag) {
        String[] parts = tag.split(":");
        try {
            if (DRAG_BASIC.equals(parts[0])) {
                return new int[]{0, Integer.parseInt(parts[1]), -1, Integer.parseInt(parts[2])};
            } else {
                return new int[]{1, Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]), Integer.parseInt(parts[3])};
            }
        } catch (Exception e) { return null; }
    }

    /** Remove the button at the source location and return it. */
    private ButtonDef removeFromSource(int[] src) {
        int type = src[0], idx = src[1], row = src[2], slot = src[3];
        if (type == 0) {
            // basic
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
        int[] src  = resolveSource(srcTag);
        int[] dest = resolveSource(destRowKey + ":" + destSlot);
        if (src == null || dest == null) return;

        List<ButtonDef> srcList  = getSlotList(src);
        List<ButtonDef> destList = getSlotList(dest);
        if (srcList == null || destList == null) return;

        int srcSlot  = src[3];
        int dstSlot  = dest[3];
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

        RemapConfig cfg = new RemapConfig(basic, ext);
        cfg.save(getSharedPreferences("remap_prefs", MODE_PRIVATE));
    }

    // ── Utils ─────────────────────────────────────────────────────────────────

    private int dp(int val) {
        return Math.round(val * getResources().getDisplayMetrics().density);
    }
}
