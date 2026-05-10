package com.carbrowser.ui;

import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.view.WindowManager;
import android.widget.ImageButton;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.carbrowser.R;
import com.carbrowser.video.VideoPlayerService;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.ui.PlayerView;

/**
 * Video playback activity using ExoPlayer.
 * Supports fullscreen mode and speed control.
 */
public class VideoActivity extends AppCompatActivity {

    private PlayerView playerView;
    private ExoPlayer player;
    private VideoPlayerService videoService;
    private ImageButton btnSpeed;
    private ImageButton btnBack;
    private TextView speedLabel;
    private boolean isFullscreen = true;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Keep screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Fullscreen
        if (getIntent().getBooleanExtra("fullscreen", true)) {
            getWindow().getDecorView().setSystemUiVisibility(
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                | View.SYSTEM_UI_FLAG_FULLSCREEN
                | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            );
        }

        setContentView(R.layout.activity_video);

        playerView = findViewById(R.id.player_view);
        btnSpeed = findViewById(R.id.btn_speed);
        btnBack = findViewById(R.id.btn_back);
        speedLabel = findViewById(R.id.speed_label);

        videoService = new VideoPlayerService(this);
        player = videoService.createPlayer();
        playerView.setPlayer(player);

        String videoUrl = getIntent().getStringExtra("video_url");
        if (videoUrl != null) {
            MediaItem mediaItem = MediaItem.fromUri(Uri.parse(videoUrl));
            player.setMediaItem(mediaItem);
            player.prepare();
            player.setPlayWhenReady(true);
        }

        btnSpeed.setOnClickListener(v -> {
            float speed = videoService.cycleSpeed();
            speedLabel.setText(speed + "x");
        });

        btnBack.setOnClickListener(v -> finish());

        updateSpeedLabel();
    }

    private void updateSpeedLabel() {
        speedLabel.setText(videoService.getCurrentSpeed() + "x");
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) player.setPlayWhenReady(false);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (player != null) player.setPlayWhenReady(true);
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        videoService.releasePlayer();
    }

    @Override
    public void onBackPressed() {
        // Don't accidentally exit video
        finish();
    }
}
