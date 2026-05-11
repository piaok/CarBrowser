package com.lazylines.download

/**
 * Listener for download progress and completion events.
 */
interface DownloadListener {
    /** Called when download progress updates. percent: 0–100 */
    fun onProgress(id: Int, percent: Int)

    /** Called when download completes successfully. */
    fun onComplete(id: Int, path: String)

    /** Called when download fails. */
    fun onError(id: Int, msg: String)
}
