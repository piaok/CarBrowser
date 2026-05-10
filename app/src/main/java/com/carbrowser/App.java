package com.carbrowser;

import android.app.Application;
import com.carbrowser.data.DatabaseHelper;
import com.carbrowser.adblock.AdBlocker;

public class App extends Application {

    private static App instance;
    private DatabaseHelper databaseHelper;
    private AdBlocker adBlocker;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        // Initialize database
        databaseHelper = new DatabaseHelper(this);

        // Initialize ad blocker (load rules from assets)
        adBlocker = new AdBlocker(this);
        adBlocker.loadRules();
    }

    public static App getInstance() {
        return instance;
    }

    public DatabaseHelper getDatabaseHelper() {
        return databaseHelper;
    }

    public AdBlocker getAdBlocker() {
        return adBlocker;
    }
}
