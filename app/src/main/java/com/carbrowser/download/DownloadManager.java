package com.carbrowser.download;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Download engine for CarBrowser.
 * Uses HttpURLConnection + thread pool for concurrent downloads.
 * Tracks progress in SQLite database for persistence across app restarts.
 */
public class DownloadManager {

    private static final String TAG = "DownloadManager";
    private static final int BUFFER_SIZE = 8192;
    private static final int MAX_CONCURRENT = 3;

    private final Context context;
    private final ExecutorService executor;
    private final Handler mainHandler;
    private final DownloadDb dbHelper;
    private final List<DownloadListener> listeners = new ArrayList<>();

    public interface DownloadListener {
        void onDownloadAdded(DownloadTask task);
        void onDownloadProgress(DownloadTask task, int percent);
        void onDownloadComplete(DownloadTask task);
        void onDownloadFailed(DownloadTask task, String error);
    }

    public DownloadManager(Context context) {
        this.context = context.getApplicationContext();
        this.executor = Executors.newFixedThreadPool(MAX_CONCURRENT);
        this.mainHandler = new Handler(Looper.getMainLooper());
        this.dbHelper = new DownloadDb(context);
    }

    public void addListener(DownloadListener listener) {
        if (!listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(DownloadListener listener) {
        listeners.remove(listener);
    }

    /**
     * Get the default download directory.
     */
    public File getDownloadDir() {
        File dir = new File(android.os.Environment.getExternalStoragePublicDirectory(
            android.os.Environment.DIRECTORY_DOWNLOADS), "CarBrowser");
        if (!dir.exists()) dir.mkdirs();
        return dir;
    }

    /**
     * Start a new download.
     */
    public DownloadTask startDownload(String url, String fileName) {
        if (fileName == null || fileName.isEmpty()) {
            fileName = extractFileName(url);
        }

        File destFile = new File(getDownloadDir(), fileName);
        // Handle duplicate filenames
        if (destFile.exists()) {
            String base = fileName;
            String ext = "";
            int dot = fileName.lastIndexOf('.');
            if (dot > 0) {
                base = fileName.substring(0, dot);
                ext = fileName.substring(dot);
            }
            int count = 1;
            while (destFile.exists()) {
                destFile = new File(getDownloadDir(), base + " (" + count + ")" + ext);
                count++;
            }
            fileName = destFile.getName();
        }

        DownloadTask task = new DownloadTask();
        task.id = System.currentTimeMillis();
        task.url = url;
        task.fileName = fileName;
        task.filePath = destFile.getAbsolutePath();
        task.status = DownloadTask.STATUS_PENDING;
        task.downloadedBytes = 0;
        task.totalBytes = 0;

        // Save to database
        saveTask(task);

        // Notify listeners
        for (DownloadListener l : listeners) {
            l.onDownloadAdded(task);
        }

        // Start download
        executor.execute(() -> doDownload(task));

        return task;
    }

    private void doDownload(DownloadTask task) {
        HttpURLConnection conn = null;
        InputStream is = null;
        FileOutputStream fos = null;

        try {
            URL url = new URL(task.url);
            conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(30000);
            conn.setRequestProperty("User-Agent", "CarBrowser/1.0");
            conn.setRequestMethod("GET");

            // Handle redirects
            conn.setInstanceFollowRedirects(true);

            int responseCode = conn.getResponseCode();
            if (responseCode != HttpURLConnection.HTTP_OK && responseCode != HttpURLConnection.HTTP_PARTIAL) {
                throw new Exception("HTTP " + responseCode);
            }

            int contentLength = conn.getContentLength();
            task.totalBytes = contentLength > 0 ? contentLength : -1;
            task.status = DownloadTask.STATUS_RUNNING;
            updateTask(task);

            is = conn.getInputStream();
            fos = new FileOutputStream(task.filePath);

            byte[] buffer = new byte[BUFFER_SIZE];
            long lastNotifyTime = 0;
            int bytesRead;
            long totalRead = 0;

            while ((bytesRead = is.read(buffer)) != -1) {
                fos.write(buffer, 0, bytesRead);
                totalRead += bytesRead;
                task.downloadedBytes = totalRead;

                // Notify progress at most once per 500ms
                long now = System.currentTimeMillis();
                if (now - lastNotifyTime > 500 || task.totalBytes > 0 && totalRead == task.totalBytes) {
                    lastNotifyTime = now;
                    int percent = task.totalBytes > 0 ? (int) (totalRead * 100 / task.totalBytes) : -1;
                    task.progress = percent;
                    updateTask(task);
                    for (DownloadListener l : listeners) {
                        mainHandler.post(() -> l.onDownloadProgress(task, percent));
                    }
                }
            }

            fos.flush();
            task.status = DownloadTask.STATUS_COMPLETE;
            task.progress = 100;
            updateTask(task);

            for (DownloadListener l : listeners) {
                mainHandler.post(() -> l.onDownloadComplete(task));
            }

        } catch (Exception e) {
            Log.e(TAG, "Download failed: " + e.getMessage());
            task.status = DownloadTask.STATUS_FAILED;
            task.error = e.getMessage();
            updateTask(task);

            // Delete partial file
            File f = new File(task.filePath);
            if (f.exists()) f.delete();

            for (DownloadListener l : listeners) {
                mainHandler.post(() -> l.onDownloadFailed(task, e.getMessage()));
            }
        } finally {
            try { if (is != null) is.close(); } catch (Exception ignored) {}
            try { if (fos != null) fos.close(); } catch (Exception ignored) {}
            if (conn != null) conn.disconnect();
        }
    }

    /**
     * Get all download tasks from database.
     */
    public List<DownloadTask> getAllTasks() {
        List<DownloadTask> tasks = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = null;
        try {
            c = db.query("downloads", null, null, null, null, null, "id DESC");
            while (c.moveToNext()) {
                tasks.add(taskFromCursor(c));
            }
        } finally {
            if (c != null) c.close();
        }
        return tasks;
    }

    /**
     * Delete a download task and its file.
     */
    public void deleteTask(long id, boolean deleteFile) {
        DownloadTask task = getTask(id);
        if (task != null && deleteFile) {
            File f = new File(task.filePath);
            if (f.exists()) f.delete();
        }
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        db.delete("downloads", "id = ?", new String[]{String.valueOf(id)});
    }

    private DownloadTask getTask(long id) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = null;
        try {
            c = db.query("downloads", null, "id = ?", new String[]{String.valueOf(id)}, null, null, null);
            if (c.moveToFirst()) return taskFromCursor(c);
        } finally {
            if (c != null) c.close();
        }
        return null;
    }

    private void saveTask(DownloadTask task) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = taskToContentValues(task);
        db.insert("downloads", null, cv);
    }

    private void updateTask(DownloadTask task) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = taskToContentValues(task);
        db.update("downloads", cv, "id = ?", new String[]{String.valueOf(task.id)});
    }

    private ContentValues taskToContentValues(DownloadTask task) {
        ContentValues cv = new ContentValues();
        cv.put("id", task.id);
        cv.put("url", task.url);
        cv.put("file_name", task.fileName);
        cv.put("file_path", task.filePath);
        cv.put("status", task.status);
        cv.put("total_bytes", task.totalBytes);
        cv.put("downloaded_bytes", task.downloadedBytes);
        cv.put("progress", task.progress);
        cv.put("error", task.error != null ? task.error : "");
        return cv;
    }

    private DownloadTask taskFromCursor(Cursor c) {
        DownloadTask task = new DownloadTask();
        task.id = c.getLong(c.getColumnIndexOrThrow("id"));
        task.url = c.getString(c.getColumnIndexOrThrow("url"));
        task.fileName = c.getString(c.getColumnIndexOrThrow("file_name"));
        task.filePath = c.getString(c.getColumnIndexOrThrow("file_path"));
        task.status = c.getInt(c.getColumnIndexOrThrow("status"));
        task.totalBytes = c.getLong(c.getColumnIndexOrThrow("total_bytes"));
        task.downloadedBytes = c.getLong(c.getColumnIndexOrThrow("downloaded_bytes"));
        task.progress = c.getInt(c.getColumnIndexOrThrow("progress"));
        task.error = c.getString(c.getColumnIndexOrThrow("error"));
        return task;
    }

    private String extractFileName(String url) {
        try {
            String path = new URL(url).getPath();
            String name = path.substring(path.lastIndexOf('/') + 1);
            if (name.isEmpty() || name.length() > 100) {
                name = "download_" + System.currentTimeMillis();
            }
            return name;
        } catch (Exception e) {
            return "download_" + System.currentTimeMillis();
        }
    }

    /**
     * Guess filename from URL and Content-Disposition header.
     */
    public static String guessFileName(String url, String contentDisposition, String mimeType) {
        // Use Android's built-in guesser
        return android.webkit.URLUtil.guessFileName(url, contentDisposition, mimeType);
    }

    public void shutdown() {
        executor.shutdownNow();
        dbHelper.close();
    }

    // --- SQLite Helper ---

    private static class DownloadDb extends SQLiteOpenHelper {
        private static final String DB_NAME = "carbrowser_downloads.db";
        private static final int DB_VERSION = 1;

        DownloadDb(Context context) {
            super(context, DB_NAME, null, DB_VERSION);
        }

        @Override
        public void onCreate(SQLiteDatabase db) {
            db.execSQL(
                "CREATE TABLE downloads (" +
                "id INTEGER PRIMARY KEY," +
                "url TEXT NOT NULL," +
                "file_name TEXT NOT NULL," +
                "file_path TEXT NOT NULL," +
                "status INTEGER DEFAULT 0," +
                "total_bytes INTEGER DEFAULT 0," +
                "downloaded_bytes INTEGER DEFAULT 0," +
                "progress INTEGER DEFAULT 0," +
                "error TEXT DEFAULT '')"
            );
        }

        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            db.execSQL("DROP TABLE IF EXISTS downloads");
            onCreate(db);
        }
    }
}
