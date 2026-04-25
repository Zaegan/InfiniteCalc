package com.github.zaegan.infinitecalc;

import java.util.HashMap;
import java.util.Map;

/**
 * Static title + body text for every tutorial.
 * Content is keyed by the IDs defined in {@link TutorialManager}.
 */
public class TutorialContent {

    public static class Entry {
        public final String title;
        public final String body;
        Entry(String title, String body) { this.title = title; this.body = body; }
    }

    private static final Map<String, Entry> MAP = new HashMap<>();

    static {
        // ── Intro sequence ────────────────────────────────────────────────────

        put(TutorialManager.INTRO_ACCORDION,
            "History steps",
            "Tap ▶ next to any history entry to expand it and see each individual step.\n\n" +
            "Tap ▼ to collapse it again.");

        put(TutorialManager.INTRO_HISTORY_COPY,
            "Copy from history",
            "Long-press any expression or result in history to copy it to the clipboard.\n\n" +
            "Tap it to load it directly into the expression bar.");

        put(TutorialManager.INTRO_EXT,
            "Extended panel",
            "Tap EXT to open the scientific functions panel.\n\n" +
            "Use ‹ and › to browse pages. Tap BASIC to return to the main keypad.");

        // ── STO / REC ─────────────────────────────────────────────────────────

        put(TutorialManager.STO,
            "Store to variable",
            "STO stores the current result into a variable slot.\n\n" +
            "Tap any variable (A–Z, α, β) to save the value there. " +
            "The STO button highlights orange while active.");

        put(TutorialManager.REC,
            "Recall a variable",
            "REC recalls a stored variable.\n\n" +
            "Tap any variable slot to insert its stored value into the expression. " +
            "The REC button highlights blue while active.");

        // ── RAD/DEG ───────────────────────────────────────────────────────────

        put(TutorialManager.RAD_DEG,
            "Angle mode",
            "Switches between Radians and Degrees for all trigonometric functions " +
            "(sin, cos, tan, and their inverses).\n\n" +
            "The label shows the mode you will switch TO — not the current mode.");

        // ── REMAP ─────────────────────────────────────────────────────────────

        put(TutorialManager.REMAP,
            "Custom buttons",
            "Long-press any button slot to replace it with a custom button.\n\n" +
            "Tap + to create a new button and add it to the custom palette.\n\n" +
            "Tap Lock to save your changes.");

        // ── Add button (in RemapActivity) ─────────────────────────────────────

        put(TutorialManager.ADD_BUTTON,
            "Two button modes",
            "Insert Text — types an expression fragment when pressed. " +
            "Use this for operators, constants, or opening functions like x².\n\n" +
            "Set Variable — evaluates an expression and stores the result to a named variable. " +
            "Use _ic_current in the expression to reference whatever is currently displayed.");

        // ── Two-argument functions ────────────────────────────────────────────

        put(TutorialManager.FUNC_LOGN,
            "logn(  —  logarithm, custom base",
            "logn(base, value)\n\n" +
            "Enter the base first, then tap any operator to insert the comma automatically, " +
            "then enter the value, then close with ).\n\n" +
            "Example:  logn(3, 27)  =  3");

        put(TutorialManager.FUNC_NTHRT,
            "nthrt(  —  nth root",
            "nthrt(n, value)\n\n" +
            "Enter the degree of the root first, then tap any operator for the comma, " +
            "then enter the value, then close with ).\n\n" +
            "Example:  nthrt(3, 8)  =  2  (cube root of 8)");

        put(TutorialManager.FUNC_NCR,
            "ncr(  —  combinations",
            "ncr(n, r)\n\n" +
            "How many ways to choose r items from a set of n, where order does not matter.\n\n" +
            "Enter n first, then tap any operator for the comma, then r, then close with ).\n\n" +
            "Example:  ncr(5, 2)  =  10");

        put(TutorialManager.FUNC_NPR,
            "npr(  —  permutations",
            "npr(n, r)\n\n" +
            "How many ordered arrangements of r items chosen from a set of n.\n\n" +
            "Enter n first, then tap any operator for the comma, then r, then close with ).\n\n" +
            "Example:  npr(5, 2)  =  20");

        put(TutorialManager.FUNC_ROUND,
            "round(  —  round to decimal places",
            "round(value, decimals)\n\n" +
            "Enter the value first, then tap any operator for the comma, " +
            "then the number of decimal places, then close with ).\n\n" +
            "Example:  round(3.14159, 2)  =  3.14");

        put(TutorialManager.FUNC_MOD,
            "mod(  —  remainder",
            "mod(value, divisor)\n\n" +
            "Returns the remainder after dividing value by divisor.\n\n" +
            "Enter the value first, then tap any operator for the comma, " +
            "then the divisor, then close with ).\n\n" +
            "Example:  mod(10, 3)  =  1");
    }

    private static void put(String id, String title, String body) {
        MAP.put(id, new Entry(title, body));
    }

    /** Returns the Entry for {@code id}, or null if not found. */
    public static Entry get(String id) {
        return MAP.get(id);
    }
}
