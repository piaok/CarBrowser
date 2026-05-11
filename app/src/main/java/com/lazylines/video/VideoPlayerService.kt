package com.lazylines.video

import android.content.Context
import com.google.android.exoplayer2.DefaultLoadControl
import com.google.android.exoplayer2.ExoPlayer
import com.google.android.exoplayer2.MediaItem
import com.google.android.exoplayer2.PlaybackException
import com.google.android.exoplayer2.Player
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource

/**
 * Video player service for the projector fullscreen Activity.
 * Manages an ExoPlayer instance with configurable buffer, speed, and HLS support.
 */
class VideoPlayerService(private val context: Context) {

    var player: ExoPlayer? = null
        private set

    private var onErrorListener: ((String) -> Unit)? = null

    companion object {
        private const val MIN_BUFFER_MS = 1500
        private const val MAX_BUFFER_MS = 5000
        private const val PLAYBACK_BUFFER_MS = 1000
        private const val REBUFFER_MS = 1000
        private const val USER_AGENT = "LazyLines/1.0"
    }

    /** Initialize the ExoPlayer instance. Call once before play(). */
    fun initialize() {
        if (player != null) return

        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                MIN_BUFFER_MS,
                MAX_BUFFER_MS,
                PLAYBACK_BUFFER_MS,
                REBUFFER_MS
            )
            .setTargetBufferBytes(DefaultLoadControl.DEFAULT_TARGET_BUFFER_BYTES)
            .build()

        player = ExoPlayer.Builder(context)
            .setMediaSourceFactory(
                DefaultMediaSourceFactory(
                    DefaultDataSourceFactory(context, USER_AGENT)
                )
            )
            .setLoadControl(loadControl)
            .build()

        player?.addListener(object : Player.Listener {
            override fun onPlayerError(error: PlaybackException) {
                onErrorListener?.invoke(error.message ?: "Unknown error")
            }
        })
    }

    /** Play a video URL. Supports direct media URLs and HLS streams. */
    fun play(url: String) {
        val p = player ?: return
        try {
            // Try HLS first if URL suggests it
            if (url.contains(".m3u8", ignoreCase = true)) {
                val dataSourceFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent(USER_AGENT)
                val hlsSource = HlsMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(url))
                p.setMediaSource(hlsSource)
            } else {
                val mediaItem = MediaItem.fromUri(url)
                p.setMediaItem(mediaItem)
            }
            p.prepare()
            p.playWhenReady = true
        } catch (e: Exception) {
            // Fallback: try as HLS
            try {
                val dataSourceFactory = DefaultHttpDataSource.Factory()
                    .setUserAgent(USER_AGENT)
                val hlsSource = HlsMediaSource.Factory(dataSourceFactory)
                    .createMediaSource(MediaItem.fromUri(url))
                p.setMediaSource(hlsSource)
                p.prepare()
                p.playWhenReady = true
            } catch (e2: Exception) {
                onErrorListener?.invoke(e2.message ?: "视频加载失败")
            }
        }
    }

    fun pause() {
        player?.pause()
    }

    fun resume() {
        player?.play()
    }

    fun seekTo(positionMs: Long) {
        player?.seekTo(positionMs)
    }

    /** Set playback speed. Clamped to 0.5x – 5.0x. */
    fun setSpeed(speed: Float) {
        val clamped = speed.coerceIn(0.5f, 5.0f)
        player?.setPlaybackSpeed(clamped)
    }

    fun getCurrentPosition(): Long {
        return player?.currentPosition ?: 0L
    }

    fun getDuration(): Long {
        return player?.duration ?: 0L
    }

    fun isPlaying(): Boolean {
        return player?.isPlaying == true
    }

    /** Release the player. Call when the Activity is destroyed. */
    fun release() {
        player?.release()
        player = null
    }

    fun setOnErrorListener(listener: (String) -> Unit) {
        onErrorListener = listener
    }
}
