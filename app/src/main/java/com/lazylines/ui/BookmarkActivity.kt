package com.lazylines.ui

import android.content.Intent
import android.os.Bundle
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import com.lazylines.leanback.SafeAreaScrollView

/**
 * BookmarkActivity — LazyLines 书签页
 * D-pad可导航的书签列表
 */
class BookmarkActivity : AppCompatActivity() {

    private val bookmarks = mutableListOf<Pair<String, String>>() // title, url

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        loadBookmarks()

        val scrollView = SafeAreaScrollView(this)
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(0xFF0D0D0D.toInt())
        }

        // Title
        val title = TextView(this).apply {
            text = "← 书签"
            textSize = 22f
            setTextColor(0xFFFFFFFF.toInt())
            setPadding(54, 54, 54, 16)
        }
        layout.addView(title)

        if (bookmarks.isEmpty()) {
            val empty = TextView(this).apply {
                text = "暂无书签"
                textSize = 18f
                setTextColor(0xFF607D8B.toInt())
                setPadding(54, 32, 54, 0)
            }
            layout.addView(empty)
        } else {
            bookmarks.forEachIndexed { index, (titleStr, url) ->
                val item = LinearLayout(this).apply {
                    orientation = LinearLayout.HORIZONTAL
                    setPadding(20, 16, 20, 16)
                    setBackgroundColor(0xFF1A1A2E.toInt())
                    isFocusable = true
                    isFocusableInTouchMode = true
                    setOnFocusChangeListener { _, hasFocus ->
                        setBackgroundColor(if (hasFocus) 0xFF252540.toInt() else 0xFF1A1A2E.toInt())
                    }
                    setOnClickListener { openUrl(url) }
                    setOnLongClickListener {
                        deleteBookmark(index)
                        true
                    }

                    val titleView = TextView(context).apply {
                        text = titleStr
                        textSize = 18f
                        setTextColor(0xFFFFFFFF.toInt())
                        layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
                    }
                    addView(titleView)

                    val deleteBtn = TextView(context).apply {
                        text = "🗑"
                        textSize = 16f
                        setTextColor(0xFF607D8B.toInt())
                        setPadding(16, 0, 0, 0)
                        setOnClickListener {
                            deleteBookmark(index)
                        }
                    }
                    addView(deleteBtn)
                }

                // Divider
                val divider = View(this).apply {
                    layoutParams = LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, 1
                    )
                    setBackgroundColor(0xFF2A2A45.toInt())
                }

                layout.addView(item)
                layout.addView(divider)
            }
        }

        scrollView.addView(layout)
        setContentView(scrollView)
    }

    private fun loadBookmarks() {
        bookmarks.clear()
        val prefs = getSharedPreferences("lazylines_bookmarks", MODE_PRIVATE)
        val set = prefs.getStringSet("bookmarks", emptySet()) ?: emptySet()
        set.forEach { entry ->
            val parts = entry.split("|", limit = 2)
            if (parts.size == 2) {
                bookmarks.add(parts[0] to parts[1])
            }
        }
    }

    private fun openUrl(url: String) {
        val intent = Intent(this, BrowserActivity::class.java)
        intent.putExtra("url", url)
        startActivity(intent)
    }

    private fun deleteBookmark(index: Int) {
        AlertDialog.Builder(this)
            .setTitle("删除书签？")
            .setMessage(bookmarks[index].first)
            .setPositiveButton("删除") { _, _ ->
                val prefs = getSharedPreferences("lazylines_bookmarks", MODE_PRIVATE)
                val set = prefs.getStringSet("bookmarks", mutableSetOf())?.toMutableSet() ?: return@setPositiveButton
                val entry = "${bookmarks[index].first}|${bookmarks[index].second}"
                set.remove(entry)
                prefs.edit().putStringSet("bookmarks", set).apply()
                recreate()
            }
            .setNegativeButton("取消", null)
            .show()
    }
}
