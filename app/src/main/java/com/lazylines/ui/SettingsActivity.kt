package com.lazylines.ui

import android.os.Bundle
import android.widget.ArrayAdapter
import android.widget.ListView
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import android.widget.Toast
import com.lazylines.leanback.SafeAreaScrollView

/**
 * SettingsActivity — LazyLines 设置页
 * D-pad可导航的设置列表
 */
class SettingsActivity : AppCompatActivity() {

    private val settingsItems = listOf(
        "搜索引擎" to "bing",
        "广告拦截" to "开",
        "色温预设" to "标准",
        "清除浏览历史" to "",
        "清除缓存" to "",
        "关于 LazyLines" to "v1.0"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val scrollView = SafeAreaScrollView(this)
        val listView = ListView(this).apply {
            adapter = ArrayAdapter(this@SettingsActivity, android.R.layout.simple_list_item_2, android.R.id.text1, settingsItems.map { it.first }).also { adapter ->
                // Custom view per item
            }
            dividerHeight = 1
            setOnItemClickListener { _, _, position, _ ->
                handleSettingClick(position)
            }
        }

        val title = TextView(this).apply {
            text = "← 设置"
            textSize = 22f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(54, 54, 54, 16)
        }

        val layout = android.widget.LinearLayout(this).apply {
            orientation = android.widget.LinearLayout.VERTICAL
            setBackgroundColor(0xFF0D0D0D.toInt())
            addView(title)
            addView(listView)
        }

        scrollView.addView(layout)
        setContentView(scrollView)
    }

    private fun handleSettingClick(position: Int) {
        val prefs = getSharedPreferences("lazylines_settings", MODE_PRIVATE)
        when (position) {
            0 -> showSearchEngineDialog(prefs)
            1 -> toggleAdBlock(prefs)
            2 -> showColorTempDialog(prefs)
            3 -> clearHistory()
            4 -> clearCache()
            5 -> showAbout()
        }
    }

    private fun showSearchEngineDialog(prefs: android.content.SharedPreferences) {
        val engines = arrayOf("Bing", "Google", "Baidu", "DuckDuckGo")
        val current = prefs.getString("search_engine", "bing") ?: "bing"
        val checked = engines.indexOf(current.replaceFirstChar { it.uppercase() }).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("搜索引擎")
            .setSingleChoiceItems(engines, checked) { dialog, which ->
                val engine = engines[which].lowercase()
                prefs.edit().putString("search_engine", engine).apply()
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun toggleAdBlock(prefs: android.content.SharedPreferences) {
        val current = prefs.getBoolean("adblock_enabled", true)
        prefs.edit().putBoolean("adblock_enabled", !current).apply()
        Toast.makeText(this, if (!current) "广告拦截已开启" else "广告拦截已关闭", Toast.LENGTH_SHORT).show()
    }

    private fun showColorTempDialog(prefs: android.content.SharedPreferences) {
        val presets = arrayOf("标准 (D65)", "冷色 (D75)", "暖色 (D55)")
        val values = arrayOf("standard", "cool", "warm")
        val current = prefs.getString("color_temperature", "standard") ?: "standard"
        val checked = values.indexOf(current).coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("色温预设")
            .setSingleChoiceItems(presets, checked) { dialog, which ->
                prefs.edit().putString("color_temperature", values[which]).apply()
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun clearHistory() {
        AlertDialog.Builder(this)
            .setTitle("清除浏览历史？")
            .setPositiveButton("清除") { _, _ ->
                getSharedPreferences("lazylines_history", MODE_PRIVATE).edit().clear().apply()
                Toast.makeText(this, "已清除", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun clearCache() {
        AlertDialog.Builder(this)
            .setTitle("清除缓存？")
            .setPositiveButton("清除") { _, _ ->
                // Clear WebView cache
                android.webkit.WebView(this).clearCache(true)
                Toast.makeText(this, "已清除缓存", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("取消", null)
            .show()
    }

    private fun showAbout() {
        AlertDialog.Builder(this)
            .setTitle("关于 LazyLines")
            .setMessage("LazyLines Browser for Projector\nv1.0\n\n适配 NVIDIA Shield TV 2017 Pro + BenQ W1070+\n基于 CarBrowser (piaok/CarBrowser)")
            .setPositiveButton("确定", null)
            .show()
    }
}
