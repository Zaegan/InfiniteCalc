package com.github.zaegan.infinitecalc;

import androidx.room.Entity;

/** Room entity: one step within a HistoryGroup. */
@Entity(tableName = "history_entries", primaryKeys = {"groupId", "stepIndex"})
public class HistoryEntry {
    public long groupId;     // == HistoryGroup.timestamp
    public int stepIndex;    // 0-based position within the group
    public String expression;
    public String result;
}
