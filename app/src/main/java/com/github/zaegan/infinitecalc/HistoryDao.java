package com.github.zaegan.infinitecalc;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

@Dao
public interface HistoryDao {
    /** Load all entries, oldest group first, steps in order within each group. */
    @Query("SELECT * FROM history_entries ORDER BY groupId ASC, stepIndex ASC")
    List<HistoryEntry> loadAll();

    @Insert
    void insertAll(List<HistoryEntry> entries);
}
