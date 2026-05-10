package com.carbrowser.data;

import android.content.Context;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DB_NAME = "carbrowser.db";
    private static final int DB_VERSION = 1;

    public static final String TABLE_BOOKMARKS = "bookmarks";
    public static final String TABLE_HISTORY = "history";
    public static final String TABLE_AD_RULES = "ad_rules";

    public DatabaseHelper(Context context) {
        super(context, DB_NAME, null, DB_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE " + TABLE_BOOKMARKS + " (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "title TEXT NOT NULL, " +
            "url TEXT NOT NULL UNIQUE, " +
            "favicon BLOB, " +
            "created_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE " + TABLE_HISTORY + " (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "title TEXT NOT NULL, " +
            "url TEXT NOT NULL, " +
            "visited_at INTEGER NOT NULL)");

        db.execSQL("CREATE TABLE " + TABLE_AD_RULES + " (" +
            "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
            "pattern TEXT NOT NULL, " +
            "rule_type TEXT NOT NULL DEFAULT 'block', " +
            "is_regex INTEGER DEFAULT 0)");

        // Indexes
        db.execSQL("CREATE INDEX idx_bookmark_url ON " + TABLE_BOOKMARKS + "(url)");
        db.execSQL("CREATE INDEX idx_history_visited ON " + TABLE_HISTORY + "(visited_at DESC)");
        db.execSQL("CREATE INDEX idx_ad_rule_type ON " + TABLE_AD_RULES + "(rule_type)");
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        // Future migrations
    }
}
