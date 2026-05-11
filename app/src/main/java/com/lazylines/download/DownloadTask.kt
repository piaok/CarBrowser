package com.lazylines.download

/**
 * Represents a single download task with its state.
 */
data class DownloadTask(
    val id: Int,
    val url: String,
    val filename: String,
    val savePath: String,
    var totalBytes: Long,
    var downloadedBytes: Long,
    var status: Status
) {
    enum class Status {
        QUEUED,
        DOWNLOADING,
        COMPLETED,
        FAILED,
        CANCELLED
    }

    /** Progress percentage (0-100), returns 0 if total unknown. */
    val progressPercent: Int
        get() = if (totalBytes > 0) ((downloadedBytes * 100) / totalBytes).toInt() else 0
}
