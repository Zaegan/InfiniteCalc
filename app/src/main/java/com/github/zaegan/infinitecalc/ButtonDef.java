package com.github.zaegan.infinitecalc;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Descriptor for a single remappable calculator button.
 *
 * {@code insertText} drives the action taken when the button is pressed.
 * Use {@link #getActionType()} to classify; handle each case in the dispatcher.
 *
 * <p>Sentinel insertText values and their {@link ActionType}:
 * <ul>
 *   <li>{@code "SMART_PAREN"} → {@link ActionType#SMART_PAREN}</li>
 *   <li>{@code "\u2212"}      → {@link ActionType#MINUS}  (routes to insertMinus)</li>
 *   <li>{@code "NEGATE"}      → {@link ActionType#NEGATE}</li>
 *   <li>{@code "RAD_DEG"}     → {@link ActionType#RAD_DEG}</li>
 *   <li>{@code "SETTINGS"}    → {@link ActionType#SETTINGS}</li>
 *   <li>{@code "REMAP"}       → {@link ActionType#REMAP}</li>
 * </ul>
 * Everything else is {@link ActionType#CUSTOM}: either plain insert text, or a
 * SET-variable command of the form {@code FROM <expr> SET _ic_<name>}.
 */
public class ButtonDef {

    public enum ActionType {
        SMART_PAREN,
        MINUS,
        NEGATE,
        RAD_DEG,
        SETTINGS,
        REMAP,
        /** User-created: plain insert text, or {@code FROM <expr> SET _ic_<name>}. */
        CUSTOM
    }

    public final String insertText;
    public final String labelText;

    public ButtonDef(String insertText, String labelText) {
        this.insertText = insertText;
        this.labelText  = labelText;
    }

    /** Classify this button's action. All user-created buttons return {@link ActionType#CUSTOM}. */
    public ActionType getActionType() {
        switch (insertText) {
            case "SMART_PAREN": return ActionType.SMART_PAREN;
            case "\u2212":      return ActionType.MINUS;
            case "NEGATE":      return ActionType.NEGATE;
            case "RAD_DEG":     return ActionType.RAD_DEG;
            case "SETTINGS":    return ActionType.SETTINGS;
            case "REMAP":       return ActionType.REMAP;
            default:            return ActionType.CUSTOM;
        }
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("i", insertText);
        o.put("l", labelText);
        return o;
    }

    public static ButtonDef fromJson(JSONObject o) throws JSONException {
        return new ButtonDef(o.getString("i"), o.getString("l"));
    }

    // ── Styling helpers ───────────────────────────────────────────────────────

    /** Background tint colour for this button, determined by content category. */
    public int bgColor() {
        switch (getActionType()) {
            case SETTINGS:
            case REMAP:        return 0xFF0A2A0A;
            case SMART_PAREN:
            case RAD_DEG:
            case NEGATE:       return 0xFF003A38;
            default: break;
        }
        // MINUS and CUSTOM
        if (isSetCommand())    return 0xFF2A1500;  // SET variable — amber
        switch (insertText) {
            case "π": case "e":
            case "G\u2099": case "k\u2091": case "N\u2090":
                               return 0xFF2A1E00;
        }
        if (isDigitOrDot())    return 0xFF2C2C2C;
        if (isOperator())      return 0xFF0E2840;
                               return 0xFF22103A;  // function / other
    }

    /** Text colour for this button. */
    public int textColor() {
        switch (getActionType()) {
            case SETTINGS:
            case REMAP:        return 0xFFAAFFAA;
            case SMART_PAREN:
            case RAD_DEG:
            case NEGATE:       return 0xFF60FFEE;
            default: break;
        }
        // MINUS and CUSTOM
        if (isSetCommand())    return 0xFFFFAA40;  // SET variable — amber
        switch (insertText) {
            case "π": case "e":
            case "G\u2099": case "k\u2091": case "N\u2090":
                               return 0xFFFFD080;
        }
        if (isDigitOrDot())    return 0xFFFFFFFF;
        if (isOperator())      return 0xFF80C8FF;
                               return 0xFFD0A0FF;  // function / other
    }

    /** True if this button encodes a SET-variable command. */
    public boolean isSetCommand() {
        return insertText.startsWith("FROM ") && insertText.contains(" SET _ic_");
    }

    private boolean isDigitOrDot() {
        return insertText.length() == 1
                && (Character.isDigit(insertText.charAt(0)) || insertText.charAt(0) == '.');
    }

    private boolean isOperator() {
        if (insertText.length() != 1) return false;
        char c = insertText.charAt(0);
        return c == '+' || c == '\u2212' || c == '\u00D7' || c == '\u00F7'
                || c == '^' || c == '%' || c == '!';
    }
}
