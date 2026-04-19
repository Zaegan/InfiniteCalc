package com.github.zaegan.infinitecalc;

/** Union type for the history RecyclerView: either a calculation group or a date separator. */
public abstract class HistoryListItem {

    public static final int TYPE_GROUP = 0;
    public static final int TYPE_DATE  = 1;

    public abstract int getType();

    // ── Concrete types ───────────────────────────────────────────────────────

    public static final class GroupItem extends HistoryListItem {
        public final HistoryGroup group;
        public GroupItem(HistoryGroup group) { this.group = group; }
        @Override public int getType() { return TYPE_GROUP; }
    }

    public static final class DateSeparator extends HistoryListItem {
        public final String label;   // e.g. "April 19, 2026"
        public DateSeparator(String label) { this.label = label; }
        @Override public int getType() { return TYPE_DATE; }
    }
}
