package com.carbrowser.download;

/**
 * Represents a single download task.
 */
public class DownloadTask {

    public static final int STATUS_PENDING = 0;
    public static final int STATUS_RUNNING = 1;
    public static final int STATUS_COMPLETE = 2;
    public static final int STATUS_FAILED = 3;

    public long id;
    public String url;
    public String fileName;
    public String filePath;
    public int status;
    public long totalBytes;
    public long downloadedBytes;
    public int progress;  // 0-100, or -1 if unknown
    public String error;

    public String getStatusText() {
        switch (status) {
            case STATUS_PENDING: return "等待中";
            case STATUS_RUNNING: return "下载中";
            case STATUS_COMPLETE: return "已完成";
            case STATUS_FAILED: return "失败";
            default: return "未知";
        }
    }

    public String getFormattedSize() {
        return formatSize(totalBytes > 0 ? totalBytes : downloadedBytes);
    }

    public String getFormattedDownloaded() {
        return formatSize(downloadedBytes);
    }

    private String formatSize(long bytes) {
        if (bytes <= 0) return "未知";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.1f KB", bytes / 1024.0);
        if (bytes < 1024 * 1024 * 1024) return String.format("%.1f MB", bytes / (1024.0 * 1024));
        return String.format("%.2f GB", bytes / (1024.0 * 1024 * 1024));
    }
}
