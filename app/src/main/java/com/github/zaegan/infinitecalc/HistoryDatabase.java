package com.github.zaegan.infinitecalc;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

/**
 * Plain SQLite persistence for calculation history.
 * No external dependencies — uses the Android-builtin SQLiteOpenHelper.
 */
public class HistoryDatabase extends SQLiteOpenHelper {

    private static final String DB_NAME    = "calc_history.db";
    private static final int    DB_VERSION = 1;

    private static final String TABLE  = "history_entries";
    private static final String C_GID  = "group_id";
    private static final String C_IDX  = "step_index";
    private static final String C_EXPR = "expression";
    private static final String C_RES  = "result";

    private static volatile HistoryDatabase INSTANCE;

    public static HistoryDatabase getInstance(Context context) {
        if (INSTANCE == null) {
            synchronized (HistoryDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = new HistoryDatabase(context.getApplicationContext());
                }
            }
        }
        return INSTANCE;
    }

    private HistoryDatabase(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE + " (" +
                C_GID  + " INTEGER NOT NULL," +
                C_IDX  + " INTEGER NOT NULL," +
                C_EXPR + " TEXT NOT NULL," +
                C_RES  + " TEXT NOT NULL," +
                "PRIMARY KEY (" + C_GID + ", " + C_IDX + "))");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE);
        onCreate(db);
    }

    // ── Queries ──────────────────────────────────────────────────────────────

    /**
     * Load all history groups, oldest first.
     * Must be called from a background thread.
     */
    public List<HistoryGroup> loadAll() {
        List<HistoryGroup> groups = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.query(TABLE, null, null, null, null, null,
                C_GID + " ASC, " + C_IDX + " ASC");
        try {
            int colGid  = cursor.getColumnIndexOrThrow(C_GID);
            int colExpr = cursor.getColumnIndexOrThrow(C_EXPR);
            int colRes  = cursor.getColumnIndexOrThrow(C_RES);

            long currentGroupId = -1;
            List<HistoryItem> currentSteps = null;

            while (cursor.moveToNext()) {
                long gid = cursor.getLong(colGid);
                if (gid != currentGroupId) {
                    if (currentSteps != null && !currentSteps.isEmpty()) {
                        groups.add(new HistoryGroup(currentSteps, currentGroupId));
                    }
                    currentGroupId = gid;
                    currentSteps = new ArrayList<>();
                }
                currentSteps.add(new HistoryItem(
                        cursor.getString(colExpr),
                        cursor.getString(colRes)));
            }
            if (currentSteps != null && !currentSteps.isEmpty()) {
                groups.add(new HistoryGroup(currentSteps, currentGroupId));
            }
        } finally {
            cursor.close();
        }
        return groups;
    }

    /**
     * Persist a single new step to an existing group.
     * Must be called from a background thread.
     */
    public void saveStep(long groupId, int stepIndex, String expression, String result) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues(4);
        cv.put(C_GID, groupId);
        cv.put(C_IDX, stepIndex);
        cv.put(C_EXPR, expression);
        cv.put(C_RES, result);
        db.insertOrThrow(TABLE, null, cv);
    }

    /**
     * Persist a single HistoryGroup.
     * Must be called from a background thread.
     */
    public void saveGroup(HistoryGroup group) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            List<HistoryItem> steps = group.getSteps();
            for (int i = 0; i < steps.size(); i++) {
                ContentValues cv = new ContentValues(4);
                cv.put(C_GID,  group.getTimestamp());
                cv.put(C_IDX,  i);
                cv.put(C_EXPR, steps.get(i).getExpression());
                cv.put(C_RES,  steps.get(i).getResult());
                db.insert(TABLE, null, cv);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }
}
