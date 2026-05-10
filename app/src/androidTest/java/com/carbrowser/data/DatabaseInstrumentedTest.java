package com.carbrowser.data;

import android.content.Context;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;
import static org.junit.Assert.*;

/**
 * Instrumented tests for DatabaseHelper, BookmarkDao, and HistoryDao.
 * Runs on a real Android device/emulator.
 */
@RunWith(AndroidJUnit4.class)
public class DatabaseInstrumentedTest {

    private Context context;
    private DatabaseHelper dbHelper;
    private BookmarkDao bookmarkDao;
    private HistoryDao historyDao;

    @Before
    public void setUp() {
        context = InstrumentationRegistry.getInstrumentation().getTargetContext();
        // Use in-memory database for testing
        dbHelper = new DatabaseHelper(context);
        bookmarkDao = new BookmarkDao(context);
        historyDao = new HistoryDao(context);
    }

    // === BookmarkDao Tests ===

    @Test
    public void testAddBookmark() {
        long id = bookmarkDao.addBookmark("百度", "https://www.baidu.com");
        assertTrue(id > 0);
    }

    @Test
    public void testIsBookmarked() {
        bookmarkDao.addBookmark("B站", "https://www.bilibili.com");
        assertTrue(bookmarkDao.isBookmarked("https://www.bilibili.com"));
    }

    @Test
    public void testIsNotBookmarked() {
        assertFalse(bookmarkDao.isBookmarked("https://www.nonexistent.com"));
    }

    @Test
    public void testDeleteBookmark() {
        bookmarkDao.addBookmark("测试", "https://test.com");
        assertTrue(bookmarkDao.isBookmarked("https://test.com"));
        bookmarkDao.deleteBookmarkByUrl("https://test.com");
        assertFalse(bookmarkDao.isBookmarked("https://test.com"));
    }

    @Test
    public void testGetAllBookmarks() {
        bookmarkDao.addBookmark("站点1", "https://site1.com");
        bookmarkDao.addBookmark("站点2", "https://site2.com");
        assertEquals(2, bookmarkDao.getAllBookmarks().size());
    }

    @Test
    public void testDuplicateBookmarkReplaces() {
        bookmarkDao.addBookmark("百度", "https://www.baidu.com");
        bookmarkDao.addBookmark("百度搜索", "https://www.baidu.com"); // Same URL
        // Should replace, not duplicate (CONFLICT_REPLACE)
        assertEquals(1, bookmarkDao.getAllBookmarks().size());
    }

    // === HistoryDao Tests ===

    @Test
    public void testAddHistory() {
        long id = historyDao.addHistory("测试页", "https://test.com");
        assertTrue(id > 0);
    }

    @Test
    public void testGetRecentHistory() {
        historyDao.addHistory("页1", "https://page1.com");
        historyDao.addHistory("页2", "https://page2.com");
        historyDao.addHistory("页3", "https://page3.com");
        assertEquals(3, historyDao.getRecentHistory(10).size());
    }

    @Test
    public void testHistoryLimit() {
        historyDao.addHistory("页1", "https://page1.com");
        assertEquals(1, historyDao.getRecentHistory(1).size());
    }

    @Test
    public void testClearAllHistory() {
        historyDao.addHistory("页1", "https://page1.com");
        historyDao.addHistory("页2", "https://page2.com");
        historyDao.clearAllHistory();
        assertEquals(0, historyDao.getRecentHistory(10).size());
    }
}
