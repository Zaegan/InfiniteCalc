package com.github.zaegan.infinitecalc;

import android.content.Context;
import android.content.SharedPreferences;

/**
 * Tracks which one-shot tutorials have been shown.
 * Backed by a dedicated SharedPreferences file so tutorial state is
 * independent of remap/variable prefs and survives those being cleared.
 */
public class TutorialManager {

    private static final String PREFS_NAME = "ic_tutorial_prefs";

    // ── Tutorial IDs ─────────────────────────────────────────────────────────

    /** Shown proactively at first launch — accordion expand/collapse. */
    public static final String INTRO_ACCORDION    = "intro_accordion";
    /** Shown proactively at first launch — long-press to copy from history. */
    public static final String INTRO_HISTORY_COPY = "intro_history_copy";
    /** Shown proactively at first launch — EXT panel overview. */
    public static final String INTRO_EXT          = "intro_ext";

    /** Triggered on first STO press. */
    public static final String STO         = "btn_sto";
    /** Triggered on first REC press. */
    public static final String REC         = "btn_rec";
    /** Triggered on first RAD/DEG toggle. */
    public static final String RAD_DEG     = "btn_rad_deg";
    /** Triggered on first REMAP press. */
    public static final String REMAP       = "btn_remap";
    /** Triggered on first + (add custom button) press in RemapActivity. */
    public static final String ADD_BUTTON  = "btn_add_custom";

    /** Triggered on first use of each two-argument function. */
    public static final String FUNC_LOGN  = "func_logn";
    public static final String FUNC_NTHRT = "func_nthrt";
    public static final String FUNC_NCR   = "func_ncr";
    public static final String FUNC_NPR   = "func_npr";
    public static final String FUNC_ROUND = "func_round";
    public static final String FUNC_MOD   = "func_mod";

    // ── Implementation ────────────────────────────────────────────────────────

    private final SharedPreferences prefs;

    public TutorialManager(Context context) {
        prefs = context.getApplicationContext()
                       .getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE);
    }

    public boolean isSeen(String id) {
        return prefs.getBoolean(id, false);
    }

    public void markSeen(String id) {
        prefs.edit().putBoolean(id, true).apply();
    }

    public void markAllSeen(String... ids) {
        SharedPreferences.Editor ed = prefs.edit();
        for (String id : ids) ed.putBoolean(id, true);
        ed.apply();
    }
}
