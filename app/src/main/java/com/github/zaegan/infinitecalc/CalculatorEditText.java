package com.github.zaegan.infinitecalc;

import android.content.ClipboardManager;
import android.content.Context;
import android.util.AttributeSet;

import androidx.appcompat.widget.AppCompatEditText;

/**
 * Calculator-aware EditText.
 *
 * <ul>
 *   <li>Intercepts paste so clipboard text is sanitised and routed through the
 *       ViewModel (maintaining it as the single source of truth).</li>
 *   <li>Intercepts Cut and demotes it to Copy (deletion would bypass the
 *       ViewModel and desync state).</li>
 *   <li>Provides a static {@link #sanitize} helper used for both paste and
 *       any other external text entering the expression field.</li>
 * </ul>
 */
public class CalculatorEditText extends AppCompatEditText {

    public interface PasteListener {
        void onPaste(String sanitizedText);
    }

    private PasteListener pasteListener;

    public CalculatorEditText(Context context) {
        super(context);
    }

    public CalculatorEditText(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public void setPasteListener(PasteListener listener) {
        this.pasteListener = listener;
    }

    @Override
    public boolean onTextContextMenuItem(int id) {
        if (id == android.R.id.paste || id == android.R.id.pasteAsPlainText) {
            ClipboardManager cb = (ClipboardManager) getContext()
                    .getSystemService(Context.CLIPBOARD_SERVICE);
            if (cb != null && cb.hasPrimaryClip()
                    && cb.getPrimaryClip().getItemCount() > 0) {
                CharSequence raw = cb.getPrimaryClip().getItemAt(0)
                        .coerceToText(getContext());
                if (raw != null && pasteListener != null) {
                    String sanitized = sanitize(raw.toString());
                    if (!sanitized.isEmpty()) pasteListener.onPaste(sanitized);
                }
            }
            return true; // consumed — insertion happens via ViewModel
        }
        if (id == android.R.id.cut) {
            // Copy the selection but do NOT delete — deletion would bypass the
            // ViewModel and leave expression state desynced.
            return super.onTextContextMenuItem(android.R.id.copy);
        }
        return super.onTextContextMenuItem(id);
    }

    /**
     * Strip or normalise characters so only valid calculator input survives.
     *
     * <ul>
     *   <li>ASCII {@code -}, {@code *}, {@code /} are normalised to their Unicode
     *       equivalents (−, ×, ÷) so pasted expressions work transparently.</li>
     *   <li>Whitespace is removed (the parser ignores spaces; newlines are noise).</li>
     *   <li>Control characters, emoji, RTL marks and anything else not in the
     *       calculator character set are dropped.</li>
     * </ul>
     */
    public static String sanitize(String raw) {
        if (raw == null) return "";
        StringBuilder sb = new StringBuilder(raw.length());
        for (int i = 0; i < raw.length(); i++) {
            char c = raw.charAt(i);
            // Normalise ASCII operators to unicode forms the evaluator uses
            if (c == '-') { sb.append('\u2212'); continue; } // −
            if (c == '*') { sb.append('\u00D7'); continue; } // ×
            if (c == '/') { sb.append('\u00F7'); continue; } // ÷
            // Strip whitespace
            if (c <= ' ') continue;
            // Digits and decimal point
            if (Character.isDigit(c) || c == '.') { sb.append(c); continue; }
            // ASCII operators, parentheses, and function-argument separator
            if (c == '+' || c == '^' || c == '%' || c == '!' || c == '(' || c == ')' || c == ',') {
                sb.append(c); continue;
            }
            // Unicode operators / symbols already in evaluator form
            if (c == '\u2212' || c == '\u00D7' || c == '\u00F7') { sb.append(c); continue; }
            if (c == '\u03C0' /* π */ || c == '\u221A' /* √   */ || c == '\u221B' /* ∛  */) { sb.append(c); continue; }
            if (c == '\u03B1' /* α */ || c == '\u03B2' /* β   */) { sb.append(c); continue; }
            // Subscript letters used in physical constant tokens: ₐ ₑ ₙ
            if (c == '\u2090' || c == '\u2091' || c == '\u2099') { sb.append(c); continue; }
            // Uppercase variables A–Z
            if (c >= 'A' && c <= 'Z') { sb.append(c); continue; }
            // Lowercase letters — valid in function names (sin, cos, ln, …) and
            // the constant 'e'. Illegal sequences will fail at parse time; we only
            // block unsafe/non-printable characters here, not bad syntax.
            if (c >= 'a' && c <= 'z') { sb.append(c); continue; }
            // Everything else is dropped.
        }
        return sb.toString();
    }
}
