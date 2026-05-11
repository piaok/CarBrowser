package com.carbrowser.download;

import android.app.AlertDialog;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;

import com.carbrowser.R;

import java.io.File;
import java.util.List;

/**
 * Dialogs for download operations:
 * 1. Confirm download (show URL + filename, let user edit)
 * 2. Download list (show all downloads with status/progress)
 */
public class DownloadDialog {

    /**
     * Show download confirmation dialog.
     */
    public static void showConfirmDialog(Context context, String url,
                                          String contentDisposition, String mimeType,
                                          DownloadManager downloadManager) {
        String fileName = DownloadManager.guessFileName(url, contentDisposition, mimeType);

        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(48, 24, 48, 16);

        TextView urlLabel = new TextView(context);
        urlLabel.setText("下载地址:");
        urlLabel.setTextSize(14);
        layout.addView(urlLabel);

        TextView urlText = new TextView(context);
        urlText.setText(url.length() > 80 ? url.substring(0, 77) + "..." : url);
        urlText.setTextSize(12);
        urlText.setPadding(0, 4, 0, 16);
        layout.addView(urlText);

        TextView nameLabel = new TextView(context);
        nameLabel.setText("文件名:");
        nameLabel.setTextSize(14);
        layout.addView(nameLabel);

        EditText nameInput = new EditText(context);
        nameInput.setText(fileName);
        nameInput.setSingleLine(true);
        nameInput.setPadding(0, 4, 0, 0);
        layout.addView(nameInput);

        new AlertDialog.Builder(context)
            .setTitle("下载文件")
            .setView(layout)
            .setPositiveButton("下载", (dialog, which) -> {
                String name = nameInput.getText().toString().trim();
                if (name.isEmpty()) name = fileName;
                downloadManager.startDownload(url, name);
            })
            .setNegativeButton("取消", null)
            .show();
    }

    /**
     * Show the download list dialog with all tasks.
     */
    public static void showDownloadList(Context context, DownloadManager downloadManager) {
        List<DownloadTask> tasks = downloadManager.getAllTasks();

        if (tasks.isEmpty()) {
            new AlertDialog.Builder(context)
                .setTitle("下载管理")
                .setMessage("暂无下载记录")
                .setPositiveButton("确定", null)
                .show();
            return;
        }

        ScrollView scrollView = new ScrollView(context);
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(24, 16, 24, 16);

        for (DownloadTask task : tasks) {
            View itemView = createTaskView(context, task, downloadManager);
            layout.addView(itemView);

            // Divider
            View divider = new View(context);
            divider.setBackgroundColor(0x1A000000);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 1);
            lp.topMargin = 8;
            lp.bottomMargin = 8;
            divider.setLayoutParams(lp);
            layout.addView(divider);
        }

        scrollView.addView(layout);

        new AlertDialog.Builder(context)
            .setTitle("下载管理 (" + tasks.size() + ")")
            .setView(scrollView)
            .setPositiveButton("关闭", null)
            .setNeutralButton("清空已完成", (dialog, which) -> {
                for (DownloadTask t : tasks) {
                    if (t.status == DownloadTask.STATUS_COMPLETE) {
                        downloadManager.deleteTask(t.id, false);
                    }
                }
            })
            .show();
    }

    private static View createTaskView(Context context, DownloadTask task,
                                        DownloadManager downloadManager) {
        LinearLayout item = new LinearLayout(context);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setPadding(0, 8, 0, 8);

        // Row 1: filename + status
        LinearLayout row1 = new LinearLayout(context);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(android.view.Gravity.CENTER_VERTICAL);

        TextView nameView = new TextView(context);
        String displayName = task.fileName.length() > 35
            ? task.fileName.substring(0, 32) + "..." : task.fileName;
        nameView.setText(displayName);
        nameView.setTextSize(14);
        nameView.setTextColor(0xFF333333);
        LinearLayout.LayoutParams nameLp = new LinearLayout.LayoutParams(
            0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        nameView.setLayoutParams(nameLp);
        row1.addView(nameView);

        TextView statusView = new TextView(context);
        statusView.setText(task.getStatusText());
        statusView.setTextSize(12);
        int statusColor;
        switch (task.status) {
            case DownloadTask.STATUS_COMPLETE: statusColor = 0xFF4CAF50; break;
            case DownloadTask.STATUS_FAILED: statusColor = 0xFFF44336; break;
            case DownloadTask.STATUS_RUNNING: statusColor = 0xFF2196F3; break;
            default: statusColor = 0xFF999999;
        }
        statusView.setTextColor(statusColor);
        row1.addView(statusView);
        item.addView(row1);

        // Row 2: progress bar (only for running tasks)
        if (task.status == DownloadTask.STATUS_RUNNING) {
            ProgressBar progressBar = new ProgressBar(context, null,
                android.R.attr.progressBarStyleHorizontal);
            progressBar.setMax(100);
            progressBar.setProgress(task.progress > 0 ? task.progress : 0);
            progressBar.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 24));
            item.addView(progressBar);
        }

        // Row 3: size info
        TextView sizeView = new TextView(context);
        sizeView.setTextSize(11);
        sizeView.setTextColor(0xFF999999);
        if (task.status == DownloadTask.STATUS_RUNNING) {
            String progressText = task.progress >= 0 ? task.progress + "%" : "";
            sizeView.setText(task.getFormattedDownloaded() + " / " + task.getFormattedSize()
                + (progressText.isEmpty() ? "" : "  " + progressText));
        } else if (task.status == DownloadTask.STATUS_COMPLETE) {
            sizeView.setText(task.getFormattedSize() + "  ✓  " + new java.io.File(task.filePath).getParent());
        } else if (task.status == DownloadTask.STATUS_FAILED) {
            sizeView.setText(task.error != null ? "错误: " + task.error : "下载失败");
        }
        item.addView(sizeView);

        // Click handler: open file if complete, or show options
        item.setOnClickListener(v -> {
            if (task.status == DownloadTask.STATUS_COMPLETE) {
                openFile(context, task.filePath);
            } else {
                showTaskOptions(context, task, downloadManager);
            }
        });

        return item;
    }

    private static void openFile(Context context, String filePath) {
        File file = new File(filePath);
        if (!file.exists()) {
            new AlertDialog.Builder(context)
                .setMessage("文件不存在")
                .setPositiveButton("确定", null)
                .show();
            return;
        }

        Intent intent = new Intent(Intent.ACTION_VIEW);
        intent.setDataAndType(Uri.fromFile(file), getMimeType(filePath));
        intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        try {
            context.startActivity(intent);
        } catch (Exception e) {
            // Fallback: open with generic type
            intent.setDataAndType(Uri.fromFile(file), "*/*");
            try {
                context.startActivity(intent);
            } catch (Exception e2) {
                new AlertDialog.Builder(context)
                    .setMessage("无法打开文件")
                    .setPositiveButton("确定", null)
                    .show();
            }
        }
    }

    private static void showTaskOptions(Context context, DownloadTask task,
                                          DownloadManager downloadManager) {
        String[] options;
        if (task.status == DownloadTask.STATUS_FAILED) {
            options = new String[]{"重新下载", "删除记录"};
        } else if (task.status == DownloadTask.STATUS_COMPLETE) {
            options = new String[]{"打开文件", "删除文件和记录"};
        } else {
            options = new String[]{"删除记录"};
        }

        new AlertDialog.Builder(context)
            .setTitle(task.fileName)
            .setItems(options, (dialog, which) -> {
                if (task.status == DownloadTask.STATUS_FAILED && which == 0) {
                    downloadManager.startDownload(task.url, task.fileName);
                    downloadManager.deleteTask(task.id, false);
                } else if (task.status == DownloadTask.STATUS_COMPLETE && which == 0) {
                    openFile(context, task.filePath);
                } else {
                    boolean deleteFile = (task.status == DownloadTask.STATUS_COMPLETE);
                    downloadManager.deleteTask(task.id, deleteFile);
                }
            })
            .show();
    }

    private static String getMimeType(String filePath) {
        String lower = filePath.toLowerCase();
        if (lower.endsWith(".apk")) return "application/vnd.android.package-archive";
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".pdf")) return "application/pdf";
        if (lower.endsWith(".zip")) return "application/zip";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".doc")) return "application/msword";
        if (lower.endsWith(".docx")) return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        return "*/*";
    }
}
