package com.carbrowser

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.*
import android.widget.*
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory

/**
 * 悬浮窗视频播放服务
 * - 可拖拽、可缩放
 * - 支持倍速 0.5x ~ 5x
 * - 支持关闭
 */
class FloatingVideoService : Service() {

    private var windowManager: WindowManager? = null
    private var floatingView: View? = null
    private var player: ExoPlayer? = null
    private var playerView: PlayerView? = null

    // 悬浮窗参数
    private var params: WindowManager.LayoutParams? = null
    private var isExpanded = true  // 是否展开
    private var currentSpeed = 1.0f

    // 拖拽相关
    private var initialX = 0
    private var initialY = 0
    private var initialTouchX = 0f
    private var initialTouchY = 0f

    // 倍速选项
    private val speedOptions = floatArrayOf(0.5f, 0.75f, 1.0f, 1.25f, 1.5f, 2.0f, 3.0f, 4.0f, 5.0f)

    companion object {
        const val CHANNEL_ID = "floating_video"
        const val NOTIFICATION_ID = 1001
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(NOTIFICATION_ID, createNotification())
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val videoUrl = intent?.getStringExtra("video_url") ?: return START_NOT_STICKY

        if (floatingView == null) {
            createFloatingView(videoUrl)
        } else {
            // 已有悬浮窗，切换视频
            playVideo(videoUrl)
        }

        return START_NOT_STICKY
    }

    private fun createFloatingView(videoUrl: String) {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager

        floatingView = LayoutInflater.from(this).inflate(R.layout.floating_video, null)

        // 初始化播放器
        playerView = floatingView!!.findViewById(R.id.player_view)
        val btnClose = floatingView!!.findViewById<ImageButton>(R.id.btn_close)
        val btnMinimize = floatingView!!.findViewById<ImageButton>(R.id.btn_minimize)
        val btnSpeed = floatingView!!.findViewById<TextView>(R.id.btn_speed)
        val speedBar = floatingView!!.findViewById<HorizontalScrollView>(R.id.speed_bar)
        val speedContainer = floatingView!!.findViewById<LinearLayout>(R.id.speed_container)

        // 初始化播放器
        initPlayer()

        // 倍速按钮
        speedOptions.forEach { speed ->
            val btn = Button(this).apply {
                text = if (speed == speed.toInt().toFloat()) "${speed.toInt()}x" else "${speed}x"
                textSize = 12f
                setPadding(16, 4, 16, 4)
                setTextColor(if (speed == 1.0f) 0xFF4CAF50.toInt() else 0xFFFFFFFF.toInt())
                setOnClickListener {
                    setPlaybackSpeed(speed)
                    // 更新按钮颜色
                    for (i in 0 until speedContainer.childCount) {
                        val child = speedContainer.getChildAt(i) as Button
                        child.setTextColor(0xFFFFFFFF.toInt())
                    }
                    this.setTextColor(0xFF4CAF50.toInt())
                    btnSpeed.text = "${speed}x"
                }
            }
            speedContainer.addView(btn)
        }

        // 默认1x高亮
        if (speedContainer.childCount >= 3) {
            (speedContainer.getChildAt(2) as Button).setTextColor(0xFF4CAF50.toInt())
        }

        // 倍速切换 - 点击显示/隐藏倍速栏
        btnSpeed.setOnClickListener {
            speedBar.visibility = if (speedBar.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        // 关闭按钮
        btnClose.setOnClickListener {
            stopSelf()
        }

        // 最小化/展开
        btnMinimize.setOnClickListener {
            isExpanded = !isExpanded
            updateFloatingSize()
        }

        // 悬浮窗参数
        val type = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            @Suppress("DEPRECATION")
            WindowManager.LayoutParams.TYPE_PHONE
        }

        params = WindowManager.LayoutParams(
            640,  // 宽度 - 1024的约60%
            420,  // 高度 - 600的70%
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = 384  // 偏右
            y = 80
        }

        // 拖拽手势
        val dragHandle = floatingView!!.findViewById<View>(R.id.drag_handle)
        dragHandle.setOnTouchListener { _, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    initialX = params!!.x
                    initialY = params!!.y
                    initialTouchX = event.rawX
                    initialTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    params!!.x = initialX + (event.rawX - initialTouchX).toInt()
                    params!!.y = initialY + (event.rawY - initialTouchY).toInt()
                    windowManager?.updateViewLayout(floatingView, params)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    val dx = Math.abs(event.rawX - initialTouchX)
                    val dy = Math.abs(event.rawY - initialTouchY)
                    // 如果移动很小，视为点击
                    if (dx < 5 && dy < 5) {
                        // 切换播放/暂停
                        togglePlayPause()
                    }
                    true
                }
                else -> false
            }
        }

        // 双击拖拽区域切换倍速栏
        dragHandle.setOnClickListener {
            speedBar.visibility = if (speedBar.visibility == View.VISIBLE) View.GONE else View.VISIBLE
        }

        windowManager?.addView(floatingView, params)

        // 开始播放
        playVideo(videoUrl)
    }

    private fun initPlayer() {
        player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    DefaultDataSourceFactory(this, "CarBrowser/1.0")
                )
            )
            .setLoadControl(
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        1500,   // 最小缓冲
                        5000,   // 最大缓冲
                        1000,   // 播放缓冲
                        1000    // 重新缓冲
                    )
                    .setTargetBufferBytes(DefaultLoadControl.DEFAULT_TARGET_BUFFER_BYTES)
                    .build()
            )
            .build()

        playerView!!.player = player
        playerView!!.useController = true  // 显示原生控制栏

        // 播放状态监听
        player!!.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_ENDED -> {
                        // 播放结束
                    }
                    Player.STATE_READY -> {
                        // 准备就绪
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                // 错误处理 - 尝试降级
            }
        })
    }

    private fun playVideo(url: String) {
        try {
            val mediaItem = MediaItem.fromUri(url)
            player?.apply {
                setMediaItem(mediaItem)
                prepare()
                playWhenReady = true
            }
        } catch (e: Exception) {
            // HLS流处理
            try {
                val dataSourceFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent("CarBrowser/1.0")
                val hlsSource = HlsMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(url))
                player?.setMediaSource(hlsSource)
                player?.prepare()
                player?.playWhenReady = true
            } catch (e2: Exception) {
                Toast.makeText(this, "视频加载失败", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun setPlaybackSpeed(speed: Float) {
        currentSpeed = speed
        player?.setPlaybackSpeed(speed)
    }

    private fun togglePlayPause() {
        player?.let {
            if (it.isPlaying) it.pause() else it.play()
        }
    }

    private fun updateFloatingSize() {
        if (params == null || floatingView == null) return

        if (isExpanded) {
            params!!.width = 640
            params!!.height = 420
            playerView?.visibility = View.VISIBLE
        } else {
            params!!.width = 200
            params!!.height = 120
            playerView?.visibility = View.VISIBLE  // 迷你模式仍显示视频
        }

        windowManager?.updateViewLayout(floatingView, params)
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                "悬浮视频播放",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "悬浮窗视频播放状态"
            }
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(
            this, 0, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            Notification.Builder(this, CHANNEL_ID)
                .setContentTitle("CarBrowser 视频播放中")
                .setContentText("点击返回浏览器")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pendingIntent)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("CarBrowser 视频播放中")
                .setContentText("点击返回浏览器")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentIntent(pendingIntent)
                .build()
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        player?.release()
        player = null
        if (floatingView != null) {
            windowManager?.removeView(floatingView)
            floatingView = null
        }
    }
}
