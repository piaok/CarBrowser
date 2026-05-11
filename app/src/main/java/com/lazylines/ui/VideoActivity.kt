package com.lazylines.ui

import android.app.AlertDialog
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.KeyEvent
import android.view.View
import android.view.WindowManager
import android.widget.ImageButton
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.google.android.exoplayer2.ui.PlayerView
import com.lazylines.video.VideoPlayerService

/**
 * Full-screen video Activity for the projector browser.
 * D-pad controls: CENTER=play/pause, LEFT=seek-10s, RIGHT=seek+10s,
 * UP/DOWN=show/hide controls, BACK=finish, long-press CENTER=speed panel.
 */
class VideoActivity : AppCompatActivity() {

    companion object {
        const val EXTRA_VIDEO_URL = "video_url"
        private const val SEEK_STEP_MS = 10_000L
        private const val AUTO_HIDE_DELAY_MS = 3000L
        private val SPEED_OPTIONS = floatArrayOf(0.5f, 0.75f, 1.0f, 1.5f, 2.0f, 3.0f, 5.0f)
    }

    private lateinit var playerView: PlayerView
    private lateinit var controlBar: LinearLayout
    private lateinit var btnBack10s: ImageButton
    private lateinit var btnPlayPause: ImageButton
    private lateinit var btnForward10s: ImageButton
    private lateinit var btnSpeed: TextView
    private lateinit var btnExit: ImageButton

    private val videoService = VideoPlayerService(this)
    private val autoHideHandler = Handler(Looper.getMainLooper())
    private var controlsVisible = false

    private val autoHideRunnable = Runnable {
        hideControls()
    }

    // Long-press detection for speed panel
    private var isLongPress = false
    private val longPressHandler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        isLongPress = true
        showSpeedPanel()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Full screen
        window.addFlags(
            WindowManager.LayoutParams.FLAG_FULLSCREEN or
            WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON
        )
        window.decorView.systemUiVisibility = (
            View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
        )

        setContentView(R.layout.activity_video)

        initViews()
        initPlayer()

        val videoUrl = intent.getStringExtra(EXTRA_VIDEO_URL) ?: run {
            finish()
            return
        }
        videoService.play(videoUrl)
    }

    private fun initViews() {
        playerView = findViewById(R.id.playerView)
        controlBar = findViewById(R.id.controlBar)
        btnBack10s = findViewById(R.id.btnBack10s)
        btnPlayPause = findViewById(R.id.btnPlayPause)
        btnForward10s = findViewById(R.id.btnForward10s)
        btnSpeed = findViewById(R.id.btnSpeed)
        btnExit = findViewById(R.id.btnExit)

        btnBack10s.setOnClickListener {
            videoService.seekTo(videoService.getCurrentPosition() - SEEK_STEP_MS)
            resetAutoHide()
        }

        btnPlayPause.setOnClickListener {
            togglePlayPause()
            resetAutoHide()
        }

        btnForward10s.setOnClickListener {
            videoService.seekTo(videoService.getCurrentPosition() + SEEK_STEP_MS)
            resetAutoHide()
        }

        btnSpeed.setOnClickListener {
            showSpeedPanel()
            resetAutoHide()
        }

        btnExit.setOnClickListener {
            finish()
        }
    }

    private fun initPlayer() {
        videoService.initialize()
        playerView.player = videoService.player
        playerView.useController = false  // We use our own control bar

        videoService.setOnErrorListener { msg ->
            runOnUiThread {
                AlertDialog.Builder(this)
                    .setTitle("播放错误")
                    .setMessage(msg)
                    .setPositiveButton("关闭") { _, _ -> finish() }
                    .setCancelable(false)
                    .show()
            }
        }
    }

    // ===== D-pad Key Handling =====

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        when (keyCode) {
            KeyEvent.KEYCODE_DPAD_CENTER -> {
                // Start long-press timer
                isLongPress = false
                longPressHandler.postDelayed(longPressRunnable, 500L)
                return true
            }
            KeyEvent.KEYCODE_DPAD_LEFT -> {
                videoService.seekTo(videoService.getCurrentPosition() - SEEK_STEP_MS)
                showControls()
                return true
            }
            KeyEvent.KEYCODE_DPAD_RIGHT -> {
                videoService.seekTo(videoService.getCurrentPosition() + SEEK_STEP_MS)
                showControls()
                return true
            }
            KeyEvent.KEYCODE_DPAD_UP -> {
                showControls()
                return true
            }
            KeyEvent.KEYCODE_DPAD_DOWN -> {
                hideControls()
                return true
            }
            KeyEvent.KEYCODE_BACK -> {
                finish()
                return true
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    override fun onKeyUp(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_DPAD_CENTER) {
            longPressHandler.removeCallbacks(longPressRunnable)
            if (!isLongPress) {
                // Short press: toggle play/pause (show controls if hidden)
                if (!controlsVisible) {
                    showControls()
                } else {
                    togglePlayPause()
                }
            }
            return true
        }
        return super.onKeyUp(keyCode, event)
    }

    // ===== Play/Pause =====

    private fun togglePlayPause() {
        if (videoService.isPlaying()) {
            videoService.pause()
            btnPlayPause.setImageResource(android.R.drawable.ic_media_play)
        } else {
            videoService.resume()
            btnPlayPause.setImageResource(android.R.drawable.ic_media_pause)
        }
    }

    // ===== Control Bar Show/Hide =====

    private fun showControls() {
        controlsVisible = true
        controlBar.visibility = View.VISIBLE
        resetAutoHide()
    }

    private fun hideControls() {
        controlsVisible = false
        controlBar.visibility = View.GONE
        autoHideHandler.removeCallbacks(autoHideRunnable)
    }

    private fun resetAutoHide() {
        autoHideHandler.removeCallbacks(autoHideRunnable)
        autoHideHandler.postDelayed(autoHideRunnable, AUTO_HIDE_DELAY_MS)
    }

    // ===== Speed Panel =====

    private fun showSpeedPanel() {
        val currentSpeed = videoService.player?.playbackParameters?.speed ?: 1.0f

        val buttons = SPEED_OPTIONS.mapIndexed { index, speed ->
            val label = if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x"
            label to speed
        }

        val labels = buttons.map { it.first }.toTypedArray()
        val checkedItem = SPEED_OPTIONS.indexOfFirst { it == currentSpeed }.coerceAtLeast(0)

        AlertDialog.Builder(this)
            .setTitle("播放速度")
            .setSingleChoiceItems(labels, checkedItem) { dialog, which ->
                val speed = SPEED_OPTIONS[which]
                videoService.setSpeed(speed)
                btnSpeed.text = if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x"
                dialog.dismiss()
            }
            .setNegativeButton("取消", null)
            .setCancelable(true)
            .show()
    }

    // ===== Lifecycle =====

    override fun onPause() {
        super.onPause()
        videoService.pause()
    }

    override fun onResume() {
        super.onResume()
        // Don't auto-resume; let user control
    }

    override fun onDestroy() {
        autoHideHandler.removeCallbacks(autoHideRunnable)
        longPressHandler.removeCallbacks(longPressRunnable)
        videoService.release()
        super.onDestroy()
    }
}
