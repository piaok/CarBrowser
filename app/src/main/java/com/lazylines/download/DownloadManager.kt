package com.lazylines.download

import android.content.Context
import android.webkit.CookieManager
import java.io.File
import java.io.FileOutputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicInteger

/**
 * Simple download manager for LazyLines browser.
 * Uses HttpURLConnection with a thread pool.
 * Saves to Downloads/LazyLines/ via scoped storage.
 */
class DownloadManager(private val context: Context) {

    private val executor = Executors.newFixedThreadPool(3)
    private val idGenerator = AtomicInteger(0)
    private val tasks = ConcurrentHashMap<Int, DownloadTask>()
    private val listeners = CopyOnWriteArrayList<DownloadListener>()

    companion object {
        private const val USER_AGENT = "LazyLines/1.0 (Linux; Android) AppleWebKit/537.36 Chrome/120.0.0.0 Mobile Safari/537.36"
        private const val DOWNLOAD_DIR = "LazyLines"
    }

    /**
     * Start a download.
     * @return the download ID
     */
    fun download(url: String, filename: String, listener: DownloadListener? = null): Int {
        val id = idGenerator.incrementAndGet()
        val saveDir = File(context.getExternalFilesDir(android.os.Environment.DIRECTORY_DOWNLOADS), DOWNLOAD_DIR)
        val savePath = File(saveDir, filename)

        val task = DownloadTask(
            id = id,
            url = url,
            filename = filename,
            savePath = savePath.absolutePath,
            totalBytes = 0L,
            downloadedBytes = 0L,
            status = DownloadTask.Status.QUEUED
        )
        tasks[id] = task

        listener?.let { addListener(it) }

        executor.submit {
            executeDownload(task)
        }

        return id
    }

    /** Cancel an active download. */
    fun cancel(downloadId: Int) {
        tasks[downloadId]?.let { task ->
            task.status = DownloadTask.Status.CANCELLED
            tasks.remove(downloadId)
        }
    }

    /** Get all active (queued or downloading) tasks. */
    fun getActiveDownloads(): List<DownloadTask> {
        return tasks.values.filter {
            it.status == DownloadTask.Status.QUEUED || it.status == DownloadTask.Status.DOWNLOADING
        }
    }

    /** Get all tasks (including completed/failed). */
    fun getAllDownloads(): List<DownloadTask> {
        return tasks.values.toList()
    }

    fun addListener(listener: DownloadListener) {
        listeners.add(listener)
    }

    fun removeListener(listener: DownloadListener) {
        listeners.remove(listener)
    }

    private fun executeDownload(task: DownloadTask) {
        var connection: HttpURLConnection? = null
        try {
            task.status = DownloadTask.Status.DOWNLOADING

            val url = URL(task.url)
            connection = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "GET"
                connectTimeout = 15000
                readTimeout = 30000
                setRequestProperty("User-Agent", USER_AGENT)
                setRequestProperty("Referer", task.url)
                setRequestProperty("Accept-Encoding", "identity")  // Prevent gzip on binary

                // Forward cookies from WebView CookieManager
                val cookies = CookieManager.getInstance().getCookie(task.url)
                if (!cookies.isNullOrEmpty()) {
                    setRequestProperty("Cookie", cookies)
                }
            }

            val responseCode = connection.responseCode
            if (responseCode != HttpURLConnection.HTTP_OK) {
                task.status = DownloadTask.Status.FAILED
                notifyError(task.id, "HTTP $responseCode")
                return
            }

            val contentLength = connection.contentLengthLong
            task.totalBytes = if (contentLength > 0) contentLength else 0L

            // Ensure save directory exists
            val saveFile = File(task.savePath)
            saveFile.parentFile?.mkdirs()

            connection.inputStream.use { input ->
                FileOutputStream(saveFile).use { output ->
                    val buffer = ByteArray(8192)
                    var bytesRead: Int
                    var lastProgress = -1

                    while (input.read(buffer).also { bytesRead = it } != -1) {
                        if (task.status == DownloadTask.Status.CANCELLED) {
                            saveFile.delete()
                            return
                        }

                        output.write(buffer, 0, bytesRead)
                        task.downloadedBytes += bytesRead

                        // Calculate progress
                        if (task.totalBytes > 0) {
                            val percent = ((task.downloadedBytes * 100) / task.totalBytes).toInt()
                            if (percent != lastProgress) {
                                lastProgress = percent
                                notifyProgress(task.id, percent)
                            }
                        }
                    }
                    output.flush()
                }
            }

            task.status = DownloadTask.Status.COMPLETED
            notifyComplete(task.id, task.savePath)

        } catch (e: Exception) {
            task.status = DownloadTask.Status.FAILED
            notifyError(task.id, e.message ?: "下载失败")
        } finally {
            connection?.disconnect()
            tasks.remove(task.id)
        }
    }

    private fun notifyProgress(id: Int, percent: Int) {
        listeners.forEach { it.onProgress(id, percent) }
    }

    private fun notifyComplete(id: Int, path: String) {
        listeners.forEach { it.onComplete(id, path) }
    }

    private fun notifyError(id: Int, msg: String) {
        listeners.forEach { it.onError(id, msg) }
    }

    /** Shutdown the download executor. */
    fun shutdown() {
        executor.shutdownNow()
    }
}
