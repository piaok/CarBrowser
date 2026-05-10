package com.carbrowser.ui;

import android.os.Bundle;
import android.widget.Switch;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.carbrowser.App;
import com.carbrowser.R;
import com.carbrowser.adblock.AdBlocker;

/**
 * Settings activity with basic toggles.
 */
public class SettingsActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);

        AdBlocker adBlocker = App.getInstance().getAdBlocker();

        Switch switchAdBlock = findViewById(R.id.switch_adblock);
        switchAdBlock.setChecked(adBlocker.isEnabled());
        switchAdBlock.setOnCheckedChangeListener((buttonView, isChecked) -> {
            adBlocker.setEnabled(isChecked);
        });

        // Search engine selection
        TextView tvSearchEngine = findViewById(R.id.tv_search_engine);
        String engine = getSharedPreferences("carbrowser_prefs", MODE_PRIVATE)
            .getString("search_engine", "baidu");
        tvSearchEngine.setText("搜索引擎: " + engine);

        tvSearchEngine.setOnClickListener(v -> {
            String[] engines = {"baidu", "bing", "sogou", "google"};
            new android.app.AlertDialog.Builder(this)
                .setTitle("选择搜索引擎")
                .setItems(engines, (dialog, which) -> {
                    getSharedPreferences("carbrowser_prefs", MODE_PRIVATE)
                        .edit().putString("search_engine", engines[which]).apply();
                    tvSearchEngine.setText("搜索引擎: " + engines[which]);
                })
                .show();
        });
    }
}
