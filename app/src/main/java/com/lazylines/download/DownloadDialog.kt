package com.lazylines.download

import android.app.AlertDialog
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ListView
import android.widget.ProgressBar
import android.widget.TextView
import com.lazylines.R

/**
 * Download management dialog for TV (D-pad navigable).
 * Shows active downloads with progress bars.
 */
class DownloadDialog(context: Context) : AlertDialog(context), DownloadListener {

    private val downloadManager: DownloadManager = DownloadManager(context)
    private val handler = Handler(Looper.getMainLooper())
    private val adapter = DownloadAdapter(context)

    private lateinit var listView: ListView
    private var refreshRunnable: Runnable? = null

    init {
        setTitle("下载管理")

        val view = LayoutInflater.from(context).inflate(R.layout.dialog_download, null)
        setView(view)

        listView = view.findViewById(R.id.downloadList)
        listView.adapter = adapter
        listView.isFocusable = true

        // Close button
        setButton(BUTTON_NEGATIVE, "关闭") { _, _ ->
            dismiss()
        }

        downloadManager.addListener(this)
    }

    override fun show() {
        super.show()
        startRefresh()
    }

    override fun dismiss() {
        stopRefresh()
        downloadManager.removeListener(this)
        super.dismiss()
    }

    /** Start a new download and show in this dialog. */
    fun startDownload(url: String, filename: String) {
        downloadManager.download(url, filename, this)
        refreshList()
    }

    // ===== DownloadListener callbacks =====

    override fun onProgress(id: Int, percent: Int) {
        handler.post { refreshList() }
    }

    override fun onComplete(id: Int, path: String) {
        handler.post { refreshList() }
    }

    override fun onError(id: Int, msg: String) {
        handler.post { refreshList() }
    }

    // ===== Refresh =====

    private fun refreshList() {
        val downloads = downloadManager.getAllDownloads()
        adapter.updateItems(downloads)
    }

    private fun startRefresh() {
        stopRefresh()
        refreshRunnable = object : Runnable {
            override fun run() {
                refreshList()
                handler.postDelayed(this, 1000)
            }
        }
        handler.post(refreshRunnable!!)
    }

    private fun stopRefresh() {
        refreshRunnable?.let { handler.removeCallbacks(it) }
        refreshRunnable = null
    }

    // ===== Adapter =====

    private class DownloadAdapter(private val context: Context) : BaseAdapter() {

        private var items: List<DownloadTask> = emptyList()

        fun updateItems(newItems: List<DownloadTask>) {
            items = newItems
            notifyDataSetChanged()
        }

        override fun getCount() = items.size

        override fun getItem(position: Int) = items[position]

        override fun getItemId(position: Int) = items[position].id.toLong()

        override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
            val view = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.item_download, parent, false)

            val task = items[position]

            val tvFilename = view.findViewById<TextView>(R.id.tvFilename)
            val progressBar = view.findViewById<ProgressBar>(R.id.progressBar)
            val tvPercent = view.findViewById<TextView>(R.id.tvPercent)
            val tvStatus = view.findViewById<TextView>(R.id.tvStatus)

            tvFilename.text = task.filename
            progressBar.progress = task.progressPercent
            tvPercent.text = "${task.progressPercent}%"

            val statusText = when (task.status) {
                DownloadTask.Status.QUEUED -> "排队中"
                DownloadTask.Status.DOWNLOADING -> "下载中"
                DownloadTask.Status.COMPLETED -> "已完成"
                DownloadTask.Status.FAILED -> "失败"
                DownloadTask.Status.CANCELLED -> "已取消"
            }
            tvStatus.text = statusText

            // D-pad focus
            view.isFocusable = true

            return view
        }
    }
}
