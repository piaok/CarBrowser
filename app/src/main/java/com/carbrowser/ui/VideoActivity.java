package com.carbrowser.ui;

import android.net.Uri;
import android.os.Bundle;
import android.view.KeyEvent;
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
 * Video playback activity using ExoPlayer with D-pad remote control support.
 * 
 * Key mapping:
 *   DPAD_CENTER = play/pause
 *   DPAD_LEFT = rewind 10s
 *   DPAD_RIGHT = fast-forward 10s
 *   DPAD_LEFT long-press = speed rewind (2x→4x→8x)
 *   DPAD_RIGHT long-press = speed forward (2x→4x→8x)
 *   BACK = exit fullscreen
 */
public class VideoActivity extends AppCompatActivity {

    private static final long SEEK_STEP_MS = 10_000; // 10 seconds
    private static final float[] SPEED_STEPS = {1f, 1.5f, 2f, 3f, 4f, 8f};

    private PlayerView playerView;
    private ExoPlayer player;
    private VideoPlayerService videoService;
    private ImageButton btnSpeed;
    private ImageButton btnBack;
    private TextView speedLabel;

    // Long-press tracking for speed seek
    private int speedIndex = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        // Fullscreen on TV (always true for projector, but read intent extra for consistency)
        boolean isFullscreen = getIntent().getBooleanExtra("fullscreen", true);
        getWindow().getDecorView().setSystemUiVisibility(
            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            | View.SYSTEM_UI_FLAG_FULLSCREEN
            | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
        );

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

        btnSpeed.setOnClickListener(v -> cycleSpeed());
        btnBack.setOnClickListener(v -> finish());

        // Make buttons focusable for D-pad
        btnSpeed.setFocusable(true);
        btnSpeed.setFocusableInTouchMode(true);
        btnBack.setFocusable(true);
        btnBack.setFocusableInTouchMode(true);

        updateSpeedLabel();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        // Ignore key repeat (long press generates repeated onKeyDown calls)
        if (event.getRepeatCount() > 0) {
            // On repeat: handle speed change
            if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                if (player != null) {
                    float currentSpeed = SPEED_STEPS[speedIndex];
                    // Decrease speed on left repeat
                    if (speedIndex > 0) {
                        speedIndex--;
                    } else {
                        speedIndex = 0; // Stay at 1x minimum
                    }
                    setSpeed(speedIndex);
                }
                return true;
            }
            if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                cycleSpeed();
                return true;
            }
            return true; // Consume repeated center/back events
        }

        switch (keyCode) {
            case KeyEvent.KEYCODE_DPAD_CENTER:
                if (player != null) {
                    player.setPlayWhenReady(!player.getPlayWhenReady());
                }
                return true;

            case KeyEvent.KEYCODE_DPAD_LEFT:
                // Short press: seek back 10s
                if (player != null) {
                    long pos = player.getCurrentPosition() - SEEK_STEP_MS;
                    player.seekTo(Math.max(0, pos));
                }
                return true;

            case KeyEvent.KEYCODE_DPAD_RIGHT:
                // Short press: seek forward 10s
                if (player != null) {
                    long pos = player.getCurrentPosition() + SEEK_STEP_MS;
                    long dur = player.getDuration();
                    player.seekTo(dur > 0 ? Math.min(pos, dur) : pos);
                }
                return true;
        }
        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        // When key is released after speed change, reset to 1x
        if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT || keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
            if (speedIndex != 0) {
                speedIndex = 0;
                setSpeed(0); // Reset to 1x
            }
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

    private void cycleSpeed() {
        speedIndex = (speedIndex + 1) % SPEED_STEPS.length;
        setSpeed(speedIndex);
    }

    private void setSpeed(int index) {
        speedIndex = index;
        float speed = SPEED_STEPS[speedIndex];
        videoService.setSpeed(speed);
        updateSpeedLabel();
    }

    private void updateSpeedLabel() {
        speedLabel.setText(SPEED_STEPS[speedIndex] + "x");
    }

    @Override
    protected void onPause() {
        super.onPause();
        if (player != null) {
            wasUserPaused = !player.getPlayWhenReady();
            player.setPlayWhenReady(false);
        }
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (player != null && !wasUserPaused) {
            player.setPlayWhenReady(true);
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        videoService.releasePlayer();
    }

    @Override
    public void onBackPressed() {
        finish();
    }
}
