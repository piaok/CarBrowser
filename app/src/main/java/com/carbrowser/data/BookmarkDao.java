package com.carbrowser.data;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;

import java.util.ArrayList;
import java.util.List;

public class BookmarkDao {

    public static class Bookmark {
        public int id;
        public String title;
        public String url;
        public long createdAt;

        public Bookmark(int id, String title, String url, long createdAt) {
            this.id = id;
            this.title = title;
            this.url = url;
            this.createdAt = createdAt;
        }
    }

    private final DatabaseHelper dbHelper;

    public BookmarkDao(Context context) {
        this.dbHelper = new DatabaseHelper(context);
    }

    public long addBookmark(String title, String url) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues values = new ContentValues();
        values.put("title", title);
        values.put("url", url);
        values.put("created_at", System.currentTimeMillis());
        return db.insertWithOnConflict(DatabaseHelper.TABLE_BOOKMARKS, null, values,
            SQLiteDatabase.CONFLICT_REPLACE);
    }

    public int deleteBookmark(int id) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.delete(DatabaseHelper.TABLE_BOOKMARKS, "id = ?",
            new String[]{String.valueOf(id)});
    }

    public int deleteBookmarkByUrl(String url) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        return db.delete(DatabaseHelper.TABLE_BOOKMARKS, "url = ?",
            new String[]{url});
    }

    public boolean isBookmarked(String url) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(DatabaseHelper.TABLE_BOOKMARKS,
            new String[]{"id"}, "url = ?", new String[]{url},
            null, null, null);
        boolean exists = cursor.moveToFirst();
        cursor.close();
        return exists;
    }

    public List<Bookmark> getAllBookmarks() {
        List<Bookmark> bookmarks = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor cursor = db.query(DatabaseHelper.TABLE_BOOKMARKS,
            null, null, null, null, null, "created_at DESC");

        if (cursor.moveToFirst()) {
            do {
                bookmarks.add(new Bookmark(
                    cursor.getInt(cursor.getColumnIndexOrThrow("id")),
                    cursor.getString(cursor.getColumnIndexOrThrow("title")),
                    cursor.getString(cursor.getColumnIndexOrThrow("url")),
                    cursor.getLong(cursor.getColumnIndexOrThrow("created_at"))
                ));
            } while (cursor.moveToNext());
        }
        cursor.close();
        return bookmarks;
    }
}
