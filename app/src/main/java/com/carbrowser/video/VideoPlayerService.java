package com.carbrowser.video;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.provider.Settings;
import android.view.Gravity;
import android.view.WindowManager;
import android.widget.FrameLayout;

import com.carbrowser.ui.VideoActivity;
import com.google.android.exoplayer2.DefaultLoadControl;
import com.google.android.exoplayer2.DefaultRenderersFactory;
import com.google.android.exoplayer2.ExoPlayer;
import com.google.android.exoplayer2.MediaItem;
import com.google.android.exoplayer2.PlaybackParameters;
import com.google.android.exoplayer2.Player;
import com.google.android.exoplayer2.ui.PlayerView;

/**
 * Manages ExoPlayer instances for video playback.
 * Supports: normal playback, float window, fullscreen, speed control.
 */
public class VideoPlayerService {

    private static final float[] SPEED_OPTIONS = {0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f, 5.0f};

    private ExoPlayer player;
    private Context context;
    private WindowManager floatWindowManager;
    private PlayerView floatPlayerView;
    private int currentSpeedIndex = 2; // Default 1.0x

    public VideoPlayerService(Context context) {
        this.context = context.getApplicationContext();
    }

    /**
     * Start playback in VideoActivity (fullscreen or normal).
     */
    public void playInActivity(String videoUrl, boolean fullscreen) {
        Intent intent = new Intent(context, VideoActivity.class);
        intent.putExtra("video_url", videoUrl);
        intent.putExtra("fullscreen", fullscreen);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        context.startActivity(intent);
    }

    /**
     * Create and configure an ExoPlayer instance.
     */
    public ExoPlayer createPlayer() {
        if (player != null) {
            releasePlayer();
        }

        DefaultLoadControl loadControl = new DefaultLoadControl.Builder()
            .setBufferDurationsMs(2000, 5000, 1000, 1000) // 2s-5s buffer for low-end
            .setTargetBufferBytes(-1)
            .setPrioritizeTimeOverSizeThresholds(true)
            .createDefaultLoadControl();

        player = new ExoPlayer.Builder(context)
            .setLoadControl(loadControl)
            .setRenderersFactory(new DefaultRenderersFactory(context)
                .setExtensionRendererMode(DefaultRenderersFactory.EXTENSION_RENDERER_MODE_OFF))
            .build();

        player.setPlayWhenReady(true);
        return player;
    }

    /**
     * Load a video URL into the player.
     */
    public void playUrl(String url) {
        if (player == null) {
            createPlayer();
        }
        MediaItem mediaItem = MediaItem.fromUri(Uri.parse(url));
        player.setMediaItem(mediaItem);
        player.prepare();
    }

    /**
     * Cycle through speed options: 0.5x → 0.75x → 1.0x → 1.25x → ... → 5.0x → 0.5x
     */
    public float cycleSpeed() {
        currentSpeedIndex = (currentSpeedIndex + 1) % SPEED_OPTIONS.length;
        float speed = SPEED_OPTIONS[currentSpeedIndex];
        if (player != null) {
            player.setPlaybackParameters(new PlaybackParameters(speed));
        }
        return speed;
    }

    public float getCurrentSpeed() {
        return SPEED_OPTIONS[currentSpeedIndex];
    }

    /**
     * Check if overlay permission is granted (required for API 23+).
     */
    public boolean canDrawOverlays() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(context);
        }
        return true; // Below API 23, permission is granted at install time
    }

    /**
     * Get Intent to request overlay permission.
     */
    public Intent getOverlayPermissionIntent() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                Uri.parse("package:" + context.getPackageName()));
        }
        return null;
    }

    /**
     * Show floating video window using WindowManager.
     * Requires SYSTEM_ALERT_WINDOW permission.
     */
    public void showFloatWindow(String videoUrl) {
        if (floatPlayerView != null) {
            dismissFloatWindow();
        }

        // Check overlay permission on API 23+
        if (!canDrawOverlays()) {
            return; // Caller should check canDrawOverlays() first and request permission
        }

        floatWindowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);

        // Create PlayerView for float window
        floatPlayerView = new PlayerView(context);
        if (player == null) {
            createPlayer();
        }
        floatPlayerView.setPlayer(player);

        // Float window params: 400x225 at top-right
        // Use TYPE_SYSTEM_ALERT for API < 26, TYPE_APPLICATION_OVERLAY for API 26+
        int windowType;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            windowType = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            windowType = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        }

        WindowManager.LayoutParams params = new WindowManager.LayoutParams(
            400, 225,
            windowType,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            android.graphics.PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.END;
        params.x = 20;
        params.y = 20;

        floatWindowManager.addView(floatPlayerView, params);
        playUrl(videoUrl);
    }

    public void dismissFloatWindow() {
        if (floatPlayerView != null) {
            floatPlayerView.setPlayer(null); // Detach player before removing view
            if (floatWindowManager != null) {
                try {
                    floatWindowManager.removeView(floatPlayerView);
                } catch (Exception e) {
                    // View may already be removed
                }
            }
            floatPlayerView = null;
        }
    }

    public void releasePlayer() {
        if (player != null) {
            player.stop();
            player.release();
            player = null;
        }
        dismissFloatWindow();
    }

    public ExoPlayer getPlayer() {
        return player;
    }

    public static float[] getSpeedOptions() {
        return SPEED_OPTIONS;
    }
}
