package com.lfcaze.tv.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import android.view.WindowManager
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.List
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
import com.lfcaze.tv.model.LiverpoolChannel
import com.lfcaze.tv.network.StreamResolver
import com.lfcaze.tv.ui.theme.LfcRed
import com.lfcaze.tv.ui.theme.SurfaceDark
import com.lfcaze.tv.ui.theme.TextPrimary
import com.lfcaze.tv.ui.theme.TextSecondary
import kotlinx.coroutines.launch

@UnstableApi
class PlayerActivity : ComponentActivity() {

    private var exoPlayer: ExoPlayer? = null
    private var currentChannelId: String = ""
    private var currentChannelName: String = ""
    private var allChannels: ArrayList<LiverpoolChannel> = arrayListOf()

    companion object {
        const val EXTRA_STREAM_URL = "extra_stream_url"
        const val EXTRA_REFERER = "extra_referer"
        const val EXTRA_ORIGIN = "extra_origin"
        const val EXTRA_USER_AGENT = "extra_user_agent"
        const val EXTRA_CHANNEL_ID = "extra_channel_id"
        const val EXTRA_CHANNEL_NAME = "extra_channel_name"
        const val EXTRA_MATCH_NAME = "extra_match_name"

        fun start(
            context: Context,
            streamUrl: String,
            referer: String,
            origin: String,
            userAgent: String,
            channelId: String,
            channelName: String,
            matchName: String
        ) {
            val intent = Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_STREAM_URL, streamUrl)
                putExtra(EXTRA_REFERER, referer)
                putExtra(EXTRA_ORIGIN, origin)
                putExtra(EXTRA_USER_AGENT, userAgent)
                putExtra(EXTRA_CHANNEL_ID, channelId)
                putExtra(EXTRA_CHANNEL_NAME, channelName)
                putExtra(EXTRA_MATCH_NAME, matchName)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        hideSystemUI()

        val streamUrl = intent.getStringExtra(EXTRA_STREAM_URL) ?: ""
        val referer = intent.getStringExtra(EXTRA_REFERER) ?: "https://tiestep.top/"
        val origin = intent.getStringExtra(EXTRA_ORIGIN) ?: "https://tiestep.top"
        val userAgent = intent.getStringExtra(EXTRA_USER_AGENT) ?: StreamResolver.USER_AGENT
        currentChannelId = intent.getStringExtra(EXTRA_CHANNEL_ID) ?: ""
        currentChannelName = intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: "Canlı Yayım"
        val matchName = intent.getStringExtra(EXTRA_MATCH_NAME) ?: "Liverpool FC"

        initPlayer(streamUrl, referer, origin, userAgent)

        setContent {
            var isBuffering by remember { mutableStateOf(true) }
            var errorMessage by remember { mutableStateOf<String?>(null) }
            var channelTitle by remember { mutableStateOf(currentChannelName) }

            DisposableEffect(exoPlayer) {
                val listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        isBuffering = state == Player.STATE_BUFFERING
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        errorMessage = "Yayım başladıla bilmədi (${error.errorCodeName}). Yenidən cəhd edilir..."
                        exoPlayer?.prepare()
                        exoPlayer?.play()
                    }
                }
                exoPlayer?.addListener(listener)
                onDispose {
                    exoPlayer?.removeListener(listener)
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black)
            ) {
                // ExoPlayer Surface View
                AndroidView(
                    factory = { ctx ->
                        PlayerView(ctx).apply {
                            player = exoPlayer
                            useController = true
                            setShowNextButton(false)
                            setShowPreviousButton(false)
                            controllerShowTimeoutMs = 3500
                        }
                    },
                    modifier = Modifier.fillMaxSize()
                )

                // Top Header Overlay
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0x88000000))
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { finish() }) {
                        Icon(
                            imageVector = Icons.Default.ArrowBack,
                            contentDescription = "Geri",
                            tint = TextPrimary
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    Column {
                        Text(
                            text = matchName,
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = channelTitle,
                            color = LfcRed,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Buffering Spinner
                if (isBuffering) {
                    CircularProgressIndicator(
                        color = LfcRed,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                // Error Banner
                errorMessage?.let { msg ->
                    Surface(
                        color = Color(0xDD9E0B22),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(16.dp)
                    ) {
                        Text(
                            text = msg,
                            color = Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }
            }
        }
    }

    private fun initPlayer(streamUrl: String, referer: String, origin: String, userAgent: String) {
        // Optimized buffer for zero-lag live sports streaming
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(
                2000,   // Min buffer: 2 seconds
                15000,  // Max buffer: 15 seconds
                800,    // Buffer for playback: 0.8s
                1200    // Buffer for rebuffer: 1.2s
            )
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        exoPlayer = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .build()

        val headers = mapOf(
            "Referer" to referer,
            "Origin" to origin,
            "User-Agent" to userAgent
        )

        val dataSourceFactory = OkHttpDataSource.Factory(StreamResolver.httpClient)
            .setDefaultRequestProperties(headers)

        val mediaSource = HlsMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(streamUrl))

        exoPlayer?.apply {
            setMediaSource(mediaSource)
            prepare()
            playWhenReady = true
        }
    }

    private fun hideSystemUI() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            window.insetsController?.let { controller ->
                controller.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                controller.systemBarsBehavior =
                    WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        } else {
            @Suppress("DEPRECATION")
            window.decorView.systemUiVisibility = (
                View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                    or View.SYSTEM_UI_FLAG_FULLSCREEN
                )
        }
    }

    override fun onPause() {
        super.onPause()
        exoPlayer?.pause()
    }

    override fun onResume() {
        super.onResume()
        exoPlayer?.play()
    }

    override fun onDestroy() {
        super.onDestroy()
        exoPlayer?.release()
        exoPlayer = null
    }
}
