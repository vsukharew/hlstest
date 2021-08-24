package com.example.testproject

import android.app.Activity
import android.graphics.Point
import android.net.Uri
import android.os.Bundle
import android.util.DisplayMetrics
import android.view.View
import android.view.ViewGroup
import android.widget.ProgressBar
import androidx.appcompat.app.AppCompatActivity
import androidx.constraintlayout.widget.ConstraintLayout
import com.google.android.exoplayer2.*
import com.google.android.exoplayer2.analytics.AnalyticsCollector
import com.google.android.exoplayer2.source.DefaultMediaSourceFactory
import com.google.android.exoplayer2.source.MediaSource
import com.google.android.exoplayer2.source.hls.DefaultHlsDataSourceFactory
import com.google.android.exoplayer2.source.hls.HlsMediaSource
import com.google.android.exoplayer2.trackselection.AdaptiveTrackSelection
import com.google.android.exoplayer2.trackselection.DefaultTrackSelector
import com.google.android.exoplayer2.ui.PlayerView
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter
import com.google.android.exoplayer2.upstream.DefaultHttpDataSource
import com.google.android.exoplayer2.util.Clock
import com.google.android.exoplayer2.util.EventLogger
import java.net.UnknownHostException

class MainActivity : AppCompatActivity() {

    private var hlsView: PlayerView? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        val root = findViewById<ConstraintLayout>(R.id.root)
        hlsView = PlayerView(this).apply {
            ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT
            )
            root.addView(this)
        }
        PlayerHelper(
            this,
            hlsView!!,
            null,
            Uri.parse("https://live-edge-3.webinar.ru/mgw/9196652/em0a7e0b833dcf10f00027fd0dc6c71e88/playlist.m3u8")
        ).preparePlayer(true, 0)
    }
}

class PlayerHelper(
    var activity: Activity,
    private var playerView: PlayerView,
    val progressBar: ProgressBar?,
    val contentUri: Uri
) : Player.Listener {

    private val remoteLog = RemoteLog("""${javaClass.simpleName} $contentUri""")
    var player: SimpleExoPlayer? = null
    private var playerNeedsPrepare: Boolean = false
    private var trackSelector: DefaultTrackSelector? = null

    private var playerPosition: Long = 0
    private val metrics: DisplayMetrics = DisplayMetrics()
    private val videoSize = Point(480, 320)
    private var mediaSource: MediaSource? = null
    var isPlaying: Boolean = false
    var isReady: Boolean = false
    var disabledVideo: Boolean = false

    init {
        activity.windowManager.defaultDisplay.getMetrics(metrics)
    }

    fun preparePlayer(playWhenReady: Boolean, playerPosition: Long) {
        this.playerPosition = playerPosition
        remoteLog.send("Preparing hlsPlayer...")
        remoteLog.send("playWhenReady = $playWhenReady; playerPosition = $playerPosition")
        if (player == null) {
            trackSelector = DefaultTrackSelector(activity.applicationContext, AdaptiveTrackSelection.Factory())

            mediaSource = HlsMediaSource.Factory(
                DefaultHlsDataSourceFactory(
                    DefaultHttpDataSource.Factory().setUserAgent(ClientBuilder.getUserAgent(activity))
                )
            )
                .createMediaSource(MediaItem.fromUri(contentUri))

            val renderersFactory = DefaultRenderersFactory(activity.applicationContext)
            val mediaSourceFactory = DefaultMediaSourceFactory(activity.applicationContext)
            val loadControl = DefaultLoadControl()
            val bandwidthMeter = DefaultBandwidthMeter.Builder(activity.applicationContext).build()
            val analyticsCollector = AnalyticsCollector(Clock.DEFAULT)
            val selector = trackSelector
            player = SimpleExoPlayer.Builder(
                activity.applicationContext,
                renderersFactory,
                trackSelector!!,
                mediaSourceFactory,
                loadControl,
                bandwidthMeter,
                analyticsCollector
            ).build().apply {
                this.playWhenReady = playWhenReady
                this.setMediaSource(mediaSource!!)
                addListener(this@PlayerHelper)
                addAnalyticsListener(EventLogger(selector))
                prepare()
            }
        }
        if (playerNeedsPrepare) {
            playerNeedsPrepare = false
        }
        if (playWhenReady)
            play()
    }

    fun releasePlayer() {
        remoteLog.send("Releasing rtmpPlayer...")
        if (player != null) {
            pause()
            playerPosition = player!!.currentPosition
            player!!.release()
            player = null
        }
    }

    fun play() {
        if (!isPlaying) {
            remoteLog.send("Start playing")
            player?.playWhenReady = true
            player?.playbackState
            playerView.onResume()
        }
    }

    fun pause() {
        remoteLog.send("Pause")
        player?.playWhenReady = false
        player?.playbackState
        playerView.onPause()
    }

    override fun onPlayerStateChanged(playWhenReady: Boolean, playbackState: Int) {
        remoteLog.send("onPlayerStateChanged playWhenReady=$playWhenReady, playbackState=$playbackState, url = $contentUri")
        isPlaying = playWhenReady && playbackState == Player.STATE_READY
        isReady = playbackState == Player.STATE_READY
        when (playbackState) {
            Player.STATE_BUFFERING -> {
                progressBar?.visibility = View.GONE
                disableVideo(disabledVideo)
            }
            Player.STATE_ENDED -> progressBar?.visibility = View.GONE
            Player.STATE_IDLE -> progressBar?.visibility = View.VISIBLE
            Player.STATE_READY -> {
                progressBar?.visibility = View.GONE
            }
            else -> progressBar?.visibility = View.GONE
        }
    }

    override fun onLoadingChanged(isLoading: Boolean) {
        progressBar?.visibility = if (isLoading) View.VISIBLE else View.GONE
    }

    override fun onVideoSizeChanged(
        width: Int, height: Int, unappliedRotationDegrees: Int,
        pixelWidthAspectRatio: Float
    ) {
        videoSize.x = width
        videoSize.y = height
        val lp = playerView.layoutParams
        if (lp.width < 0)
            setSurfaceSize(metrics.widthPixels)
        else
            setSurfaceSize(lp.width)
    }

    override fun onPlayerError(error: PlaybackException) {
        remoteLog.send("ERROR ${error.localizedMessage} / $contentUri ${player?.currentPosition}")
        when (error.cause?.cause) {
            is UnknownHostException -> {
                player?.prepare()
            }
        }
        super.onPlayerError(error)
    }


    override fun onSurfaceSizeChanged(width: Int, height: Int) {
        videoSize.x = width
        videoSize.y = height
        val lp = playerView.layoutParams
        if (lp.width < 0)
            setSurfaceSize(metrics.widthPixels)
        else
            setSurfaceSize(lp.width)
    }

    fun setSurfaceSize(_width: Int) {
        var width = _width
        if (width < 0) {
            width = metrics.widthPixels
        }
        try {
            remoteLog.send("Setting surface size for width = $width")
            val lp = playerView.layoutParams
            lp.width = width
            lp.height = (width * (videoSize.y.toDouble() / videoSize.x.toDouble())).toInt()
            remoteLog.send(" lp.width = ${lp.width} , lp.height = ${lp.height}")
            playerView.layoutParams = lp
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    //todo переделать на свойство
    fun disableVideo(disable: Boolean) {
        disabledVideo = disable
        switchTrack(!disable, C.TRACK_TYPE_VIDEO)
    }

    private fun switchTrack(isEnabled: Boolean, trackType: Int) {
        player?.let {
            for (i in 0 until it.rendererCount) {
                if (it.getRendererType(i) == trackType) {
                    trackSelector?.apply {
                        parameters = buildUponParameters()
                            .setRendererDisabled(i, !isEnabled)
                            .build()
                    }
                }
            }
        }
    }
}