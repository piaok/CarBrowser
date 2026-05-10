package com.carbrowser;

import android.app.Application;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.util.Log;

import com.carbrowser.data.DatabaseHelper;
import com.carbrowser.adblock.AdBlocker;

import java.io.File;
import java.io.FileWriter;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

public class App extends Application {

    private static App instance;
    private DatabaseHelper databaseHelper;
    private AdBlocker adBlocker;

    @Override
    public void onCreate() {
        super.onCreate();
        instance = this;

        // Install global crash handler FIRST
        Thread.setDefaultUncaughtExceptionHandler(new CrashHandler(
            this, Thread.getDefaultUncaughtExceptionHandler()));

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

    /**
     * Get crash log file path for external access.
     */
    public static File getCrashLogFile(Context context) {
        return new File(context.getExternalFilesDir(null), "crash.log");
    }

    /**
     * Global uncaught exception handler — writes full stack trace to file
     * so we can diagnose crashes on the car head unit.
     */
    static class CrashHandler implements Thread.UncaughtExceptionHandler {
        private final Context context;
        private final Thread.UncaughtExceptionHandler defaultHandler;

        CrashHandler(Context context, Thread.UncaughtExceptionHandler defaultHandler) {
            this.context = context;
            this.defaultHandler = defaultHandler;
        }

        @Override
        public void uncaughtException(Thread thread, Throwable throwable) {
            // Write crash log to file
            try {
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);

                // Header with device info
                SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
                pw.println("=== CarBrowser Crash Log ===");
                pw.println("Time: " + sdf.format(new Date()));
                pw.println("Device: " + Build.MANUFACTURER + " " + Build.MODEL);
                pw.println("Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")");
                pw.println("ABI: " + Build.SUPPORTED_ABIS[0]);
                pw.println();

                // Full stack trace
                pw.println("--- Stack Trace ---");
                throwable.printStackTrace(pw);

                // Caused by chain
                Throwable cause = throwable;
                while (cause.getCause() != null) {
                    cause = cause.getCause();
                    pw.println();
                    pw.println("--- Caused by ---");
                    cause.printStackTrace(pw);
                }

                pw.flush();
                pw.close();

                // Write to app-specific external storage
                File logFile = new File(context.getExternalFilesDir(null), "crash.log");
                FileWriter fw = new FileWriter(logFile, true); // append mode
                fw.write(sw.toString());
                fw.write("\n\n");
                fw.close();

                Log.e("CarBrowser", "Crash log written to: " + logFile.getAbsolutePath());
            } catch (Exception e) {
                Log.e("CarBrowser", "Failed to write crash log", e);
            }

            // Let the default handler kill the process
            if (defaultHandler != null) {
                defaultHandler.uncaughtException(thread, throwable);
            }
        }
    }
}
