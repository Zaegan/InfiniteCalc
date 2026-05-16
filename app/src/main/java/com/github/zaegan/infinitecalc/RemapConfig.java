package com.github.zaegan.infinitecalc;

import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

/**
 * Holds the full remappable button layout.
 *
 * <p><b>basicRows</b>: exactly 5 rows (always visible), each 1–5 buttons.</p>
 * <p><b>extPages</b>: variable number of ext pages.  Each page has:
 *   <ul>
 *     <li>row1 — 1–5 remappable buttons</li>
 *     <li>row2Middle — 0–3 remappable buttons (‹ and › are added by the UI)</li>
 *   </ul>
 * </p>
 */
public class RemapConfig {

    private static final String PREF_KEY = "remap_config_v1";

    // ── Structure ─────────────────────────────────────────────────────────────

    /** Exactly 5 entries; row count is immutable, slot contents are remappable. */
    public final List<List<ButtonDef>> basicRows;

    /**
     * User-defined custom buttons.  These live in the remap palette and can be
     * copied into any slot.  Entries here are never inserted into slots directly
     * — placed slots hold their own copy of the ButtonDef.
     */
    public final List<ButtonDef> customPalette;

    public static class ExtPage {
        public final List<ButtonDef> row1;
        /** Middle slots of ext row 2 (without ‹ and ›). */
        public final List<ButtonDef> row2Middle;

        public ExtPage(List<ButtonDef> row1, List<ButtonDef> row2Middle) {
            this.row1       = row1;
            this.row2Middle = row2Middle;
        }
    }

    public final List<ExtPage> extPages;

    public RemapConfig(List<List<ButtonDef>> basicRows, List<ExtPage> extPages,
                       List<ButtonDef> customPalette) {
        this.basicRows     = basicRows;
        this.extPages      = extPages;
        this.customPalette = customPalette;
    }

    // ── Default layout ────────────────────────────────────────────────────────

    public static RemapConfig makeDefault() {
        List<List<ButtonDef>> basic = new ArrayList<>();

        // Row 0: ^ √ () π
        basic.add(row(
            def("^",          "^"),
            def("\u221A(",     "√"),
            def("SMART_PAREN","( )"),
            def("π",          "π")));

        // Row 1: 7 8 9 ÷
        basic.add(row(
            def("7","7"), def("8","8"), def("9","9"),
            def("\u00F7","÷")));

        // Row 2: 4 5 6 ×
        basic.add(row(
            def("4","4"), def("5","5"), def("6","6"),
            def("\u00D7","×")));

        // Row 3: 1 2 3 −
        basic.add(row(
            def("1","1"), def("2","2"), def("3","3"),
            def("\u2212","−")));

        // Row 4: 0 . +
        basic.add(row(
            def("0","0"), def(".","."  ), def("+","+")));

        List<ExtPage> ext = new ArrayList<>();

        // Page 0
        ext.add(page(
            row(def("sin(","sin"),  def("cos(","cos"),  def("tan(","tan"),  def("RAD_DEG","RAD")),
            row(def("ln(","ln"),    def("log(","log"))));

        // Page 1
        ext.add(page(
            row(def("log2(","log₂"), def("logn(","logₙ"), def("e","e"), def("\u221B(","∛")),
            row(def("nthrt(","ⁿ√"))));

        // Page 2
        ext.add(page(
            row(def("^2","x²"), def("^3","x³"), def("abs(","abs"), def("%","%")),
            row(def("!","n!"),  def("ncr(","nCr"))));

        // Page 3
        ext.add(page(
            row(def("npr(","nPr"),  def("round(","rnd"),
                def("asin(","sin⁻¹"), def("acos(","cos⁻¹")),
            row(def("atan(","tan⁻¹"), def(",",","))));

        // Page 4
        ext.add(page(
            row(def("sinh(","sinh"), def("cosh(","cosh"),
                def("tanh(","tanh"), def("10^(","10^")),
            row(def("SETTINGS","Settings"), def("NEGATE","±"))));

        // Page 5
        ext.add(page(
            row(def("REMAP","Remap"),
                def("G\u2099","Gₙ"), def("k\u2091","kₑ"), def("N\u2090","Nₐ")),
            row(def(":",":"))));

        return new RemapConfig(basic, ext, new ArrayList<>());
    }

    // ── Persistence ───────────────────────────────────────────────────────────

    public void save(SharedPreferences prefs) {
        try {
            prefs.edit().putString(PREF_KEY, toJson().toString()).apply();
        } catch (JSONException ignored) {}
    }

    public static RemapConfig load(SharedPreferences prefs) {
        String json = prefs.getString(PREF_KEY, null);
        if (json == null) return makeDefault();
        try {
            return fromJson(new JSONObject(json));
        } catch (JSONException e) {
            return makeDefault();
        }
    }

    // ── JSON ──────────────────────────────────────────────────────────────────

    public JSONObject toJson() throws JSONException {
        JSONObject root = new JSONObject();

        JSONArray bArr = new JSONArray();
        for (List<ButtonDef> row : basicRows) bArr.put(rowToJson(row));
        root.put("basicRows", bArr);

        JSONArray eArr = new JSONArray();
        for (ExtPage page : extPages) {
            JSONObject p = new JSONObject();
            p.put("row1",       rowToJson(page.row1));
            p.put("row2Middle", rowToJson(page.row2Middle));
            eArr.put(p);
        }
        root.put("extPages", eArr);

        root.put("customPalette", rowToJson(customPalette));

        return root;
    }

    static RemapConfig fromJson(JSONObject root) throws JSONException {
        JSONArray bArr = root.getJSONArray("basicRows");
        List<List<ButtonDef>> basic = new ArrayList<>();
        for (int i = 0; i < bArr.length(); i++) basic.add(rowFromJson(bArr.getJSONArray(i)));

        JSONArray eArr = root.getJSONArray("extPages");
        List<ExtPage> ext = new ArrayList<>();
        for (int i = 0; i < eArr.length(); i++) {
            JSONObject p = eArr.getJSONObject(i);
            ext.add(new ExtPage(
                rowFromJson(p.getJSONArray("row1")),
                rowFromJson(p.getJSONArray("row2Middle"))));
        }
        List<ButtonDef> palette = new ArrayList<>();
        if (root.has("customPalette")) {
            palette = rowFromJson(root.getJSONArray("customPalette"));
        }
        return new RemapConfig(basic, ext, palette);
    }

    private static JSONArray rowToJson(List<ButtonDef> row) throws JSONException {
        JSONArray a = new JSONArray();
        for (ButtonDef d : row) a.put(d.toJson());
        return a;
    }

    private static List<ButtonDef> rowFromJson(JSONArray a) throws JSONException {
        List<ButtonDef> row = new ArrayList<>();
        for (int i = 0; i < a.length(); i++) row.add(ButtonDef.fromJson(a.getJSONObject(i)));
        return row;
    }

    // ── Builder helpers ───────────────────────────────────────────────────────

    private static ButtonDef def(String insert, String label) {
        return new ButtonDef(insert, label);
    }

    @SafeVarargs
    private static <T> List<T> row(T... items) {
        List<T> list = new ArrayList<>();
        for (T item : items) list.add(item);
        return list;
    }

    private static ExtPage page(List<ButtonDef> r1, List<ButtonDef> r2) {
        return new ExtPage(r1, r2);
    }
}
