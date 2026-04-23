package com.github.zaegan.infinitecalc;

import org.json.JSONException;
import org.json.JSONObject;

/**
 * Descriptor for a single remappable calculator button.
 *
 * {@code insertText} drives the action taken when the button is pressed.
 * Special sentinel values (handled by MainActivity's dispatcher):
 *   "SMART_PAREN"  → smartParen()
 *   "\u2212"       → insertMinus()
 *   "NEGATE"       → smartNegate()
 *   "RAD_DEG"      → toggleAngleMode()
 *   "SETTINGS"     → open settings dialog
 *   "REMAP"        → open remap screen
 * Two-arg functions (logn(, nthrt(, ncr(, npr(, round(, mod()
 *   are handled by insert + comma-mode activation.
 * Everything else → viewModel.insert(insertText).
 */
public class ButtonDef {
    public final String insertText;
    public final String labelText;

    public ButtonDef(String insertText, String labelText) {
        this.insertText = insertText;
        this.labelText  = labelText;
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
        switch (insertText) {
            case "SETTINGS": case "REMAP": return 0xFF0A2A0A;
            case "SMART_PAREN":            return 0xFF003A38;
            case "RAD_DEG": case "NEGATE": return 0xFF003A38;
            case "π": case "e":
            case "G\u2099": case "k\u2091": case "N\u2090":
                                           return 0xFF2A1E00;
            default:
                if (isDigitOrDot()) return 0xFF2C2C2C;
                if (isOperator())   return 0xFF0E2840;
                                   return 0xFF22103A;  // function / other
        }
    }

    /** Text colour for this button. */
    public int textColor() {
        switch (insertText) {
            case "SETTINGS": case "REMAP": return 0xFFAAFFAA;
            case "SMART_PAREN":            return 0xFF60FFEE;
            case "RAD_DEG": case "NEGATE": return 0xFF60FFEE;
            case "π": case "e":
            case "G\u2099": case "k\u2091": case "N\u2090":
                                           return 0xFFFFD080;
            default:
                if (isDigitOrDot()) return 0xFFFFFFFF;
                if (isOperator())   return 0xFF80C8FF;
                                   return 0xFFD0A0FF;  // function / other
        }
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
