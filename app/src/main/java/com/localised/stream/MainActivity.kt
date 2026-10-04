package com.localised.stream

import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.Gravity
import android.view.SurfaceView
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.TextView
import androidx.annotation.OptIn
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.DataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.ProgressiveMediaSource
import androidx.media3.extractor.DefaultExtractorsFactory
import androidx.media3.extractor.ts.DefaultTsPayloadReaderFactory
import java.net.Inet4Address
import java.net.NetworkInterface

@OptIn(UnstableApi::class)
class MainActivity : AppCompatActivity() {
    private val port = 4201
    private val main = Handler(Looper.getMainLooper())
    private lateinit var root: FrameLayout
    private lateinit var surface: SurfaceView
    private lateinit var info: TextView
    private var player: ExoPlayer? = null
    private var receiver: SrtReceiver? = null
    private var uiVisible = true
    private var videoW = 0
    private var videoH = 0
    private var statusText = "Starting"

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        WindowCompat.setDecorFitsSystemWindows(window, false)

        root = FrameLayout(this)
        root.setBackgroundColor(0xFF000000.toInt())
        surface = SurfaceView(this)
        root.addView(surface, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER))
        info = TextView(this).apply {
            setTextColor(0xFFFFFFFF.toInt())
            textSize = 20f
            gravity = Gravity.CENTER
            setPadding(48, 48, 48, 48)
            setBackgroundColor(0x88000000.toInt())
        }
        root.addView(info, FrameLayout.LayoutParams(-2, -2, Gravity.CENTER))
        root.setOnClickListener { setUiVisible(!uiVisible) }
        root.addOnLayoutChangeListener { _, _, _, _, _, _, _, _, _ -> fit() }
        setContentView(root)
        hideBars()

        setStatus("Starting")
        receiver = SrtReceiver(port, { s -> main.post { startPlayer(s) } }, { t -> main.post { setStatus(t) } })
        receiver?.start()
    }

    private fun localIp(): String {
        return try {
            NetworkInterface.getNetworkInterfaces().toList()
                .flatMap { it.inetAddresses.toList() }
                .filter { !it.isLoopbackAddress && it is Inet4Address && it.isSiteLocalAddress }
                .map { it.hostAddress }
                .firstOrNull() ?: "unknown"
        } catch (e: Exception) {
            "unknown"
        }
    }

    private fun setStatus(text: String) {
        statusText = text
        info.text = text + "\n\nSend SRT to  srt://" + localIp() + ":" + port + "\n\nTap anywhere to hide this text"
    }

    private fun setUiVisible(visible: Boolean) {
        uiVisible = visible
        info.visibility = if (visible) View.VISIBLE else View.GONE
    }

    private fun hideBars() {
        val c = WindowInsetsControllerCompat(window, root)
        c.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        c.hide(WindowInsetsCompat.Type.systemBars())
    }

    private fun fit() {
        val cw = root.width
        val ch = root.height
        if (cw == 0 || ch == 0 || videoW == 0 || videoH == 0) return
        val scale = minOf(cw.toFloat() / videoW, ch.toFloat() / videoH)
        val w = (videoW * scale).toInt()
        val h = (videoH * scale).toInt()
        val lp = surface.layoutParams as FrameLayout.LayoutParams
        if (lp.width != w || lp.height != h) {
            lp.width = w
            lp.height = h
            surface.layoutParams = lp
        }
    }

    private fun releasePlayer() {
        player?.release()
        player = null
    }

    private fun startPlayer(session: Session) {
        releasePlayer()
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(500, 1000, 100, 200)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()
        val p = ExoPlayer.Builder(this).setLoadControl(loadControl).build()
        p.setVideoSurfaceView(surface)
        val factory = DataSource.Factory { SrtDataSource(session) }
        val extractors = DefaultExtractorsFactory().setTsExtractorFlags(
            DefaultTsPayloadReaderFactory.FLAG_ALLOW_NON_IDR_KEYFRAMES or
                DefaultTsPayloadReaderFactory.FLAG_DETECT_ACCESS_UNITS
        )
        val source = ProgressiveMediaSource.Factory(factory, extractors)
            .createMediaSource(MediaItem.fromUri("srt://live"))
        p.addListener(object : Player.Listener {
            override fun onVideoSizeChanged(videoSize: VideoSize) {
                videoW = videoSize.width
                videoH = videoSize.height
                fit()
            }

            override fun onPlaybackStateChanged(state: Int) {
                when (state) {
                    Player.STATE_BUFFERING -> setStatus("Buffering")
                    Player.STATE_READY -> setStatus("Playing")
                    Player.STATE_ENDED -> setStatus("Stream ended")
                    else -> {}
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                setStatus("Player error: " + error.errorCodeName)
            }
        })
        p.setMediaSource(source)
        p.playWhenReady = true
        p.prepare()
        player = p
    }

    override fun onDestroy() {
        receiver?.stop()
        releasePlayer()
        super.onDestroy()
    }
}
