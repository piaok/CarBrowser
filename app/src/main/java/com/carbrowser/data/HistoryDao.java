package com.carbrowser.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

public class HistoryDao {

    public static class HistoryEntry {
        public int id;
        public String title;
        public String url;
        public long visitedAt;

        public HistoryEntry(int id, String title, String url, long visitedAt) {
            this.id = id;
            this.title = title;
            this.url = url;
            this.visitedAt = visitedAt;
        }
    }

    private static final int MAX_HISTORY = 500; // Limit history size

    private final DatabaseHelper dbHelper;

    public HistoryDao(Context context) {
        this.dbHelper = new DatabaseHelper(context);
    }

    public long addHistory(String title, String url) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("title", title);
        values.put("url", url);
        values.put("visited_at", System.currentTimeMillis());
        long id = db.insert(DatabaseHelper.TABLE_HISTORY, null, values);

        // Prune old entries
        pruneHistory(db);
        return id;
    }

    public int clearAllHistory() {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.delete(DatabaseHelper.TABLE_HISTORY, null, null);
    }

    public List<HistoryEntry> getRecentHistory(int limit) {
        List<HistoryEntry> entries = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(DatabaseHelper.TABLE_HISTORY,
            null, null, null, null, null,
            "visited_at DESC", String.valueOf(limit));

        if (cursor.moveToFirst()) {
            do {
                entries.add(new HistoryEntry(
                    cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                    cursor.getString(cursor.getColumnIndexOrThrow("title")),
                    cursor.getString(cursor.getColumnIndexOrThrow("url")),
                    cursor.getLong(cursor.getColumnIndexOrThrow("visited_at"))
                ));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return entries;
    }

    private void pruneHistory(SQLiteDatabase db) {
        Cursor cursor = db.rawQuery("SELECT COUNT(*) FROM " + DatabaseHelper.TABLE_HISTORY, null);
        if (cursor.moveToFirst() && cursor.getInt(0) > MAX_HISTORY) {
            db.execSQL("DELETE FROM " + DatabaseHelper.TABLE_HISTORY +
                " WHERE id NOT IN (SELECT id FROM " + DatabaseHelper.TABLE_HISTORY +
                " ORDER BY visited_at DESC LIMIT " + MAX_HISTORY + ")");
        }
        cursor.close();
    }
}
