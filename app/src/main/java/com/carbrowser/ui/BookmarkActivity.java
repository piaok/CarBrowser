package com.carbrowser.ui;

import android.os.Bundle;
import android.widget.ArrayAdapter;
import android.widget.ListView;

import androidx.appcompat.app.AppCompatActivity;

import com.carbrowser.R;
import com.carbrowser.data.BookmarkDao;

import java.util.List;

/**
 * Bookmark management activity.
 */
public class BookmarkActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_bookmark);

        ListView listView = findViewById(R.id.bookmark_list);
        BookmarkDao dao = new BookmarkDao(this);
        List<BookmarkDao.Bookmark> bookmarks = dao.getAllBookmarks();

        String[] items = new String[bookmarks.size()];
        for (int i = 0; i < bookmarks.size(); i++) {
            items[i] = bookmarks.get(i).title + "\n" + bookmarks.get(i).url;
        }

        ArrayAdapter<String> adapter = new ArrayAdapter<>(this,
            android.R.layout.simple_list_item_2, android.R.id.text1, items);
        listView.setAdapter(adapter);

        listView.setOnItemClickListener((parent, view, position, id) -> {
            String url = bookmarks.get(position).url;
            android.content.Intent intent = new android.content.Intent(this, BrowserActivity.class);
            intent.setData(android.net.Uri.parse(url));
            startActivity(intent);
        });

        listView.setOnItemLongClickListener((parent, view, position, id) -> {
            dao.deleteBookmark(bookmarks.get(position).id);
            adapter.remove(adapter.getItem(position));
            adapter.notifyDataSetChanged();
            return true;
        });
    }
}
