package com.xstream.tv.ui

import android.content.Context
import android.content.Intent
import android.os.Bundle
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.datasource.okhttp.OkHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.hls.HlsMediaSource
import androidx.media3.ui.PlayerView
import com.xstream.tv.model.ResolvedStream
import com.xstream.tv.network.StreamResolver
import com.xstream.tv.ui.theme.StreamGold
import com.xstream.tv.ui.theme.StreamRed
import com.xstream.tv.ui.theme.SurfaceDark
import com.xstream.tv.ui.theme.TextPrimary
import com.xstream.tv.ui.theme.TextSecondary
import kotlinx.coroutines.delay

@UnstableApi
class PlayerActivity : ComponentActivity() {

    private var exoPlayer: ExoPlayer? = null
    private var servers: ArrayList<ResolvedStream> = arrayListOf()
    private var currentChannelId: String = ""
    private var currentChannelName: String = ""
    private var matchName: String = ""

    companion object {
        const val EXTRA_SERVERS = "extra_servers"
        const val EXTRA_CHANNEL_ID = "extra_channel_id"
        const val EXTRA_CHANNEL_NAME = "extra_channel_name"
        const val EXTRA_MATCH_NAME = "extra_match_name"

        fun start(
            context: Context,
            servers: List<ResolvedStream>,
            channelId: String,
            channelName: String,
            matchName: String
        ) {
            val intent = Intent(context, PlayerActivity::class.java).apply {
                putExtra(EXTRA_SERVERS, ArrayList(servers))
                putExtra(EXTRA_CHANNEL_ID, channelId)
                putExtra(EXTRA_CHANNEL_NAME, channelName)
                putExtra(EXTRA_MATCH_NAME, matchName)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } catch (_: Exception) {}
        hideSystemUI()

        @Suppress("DEPRECATION", "UNCHECKED_CAST")
        servers = (intent.getSerializableExtra(EXTRA_SERVERS) as? ArrayList<ResolvedStream>) ?: arrayListOf()
        currentChannelId = intent.getStringExtra(EXTRA_CHANNEL_ID) ?: ""
        currentChannelName = intent.getStringExtra(EXTRA_CHANNEL_NAME) ?: "Canlı Yayım"
        matchName = intent.getStringExtra(EXTRA_MATCH_NAME) ?: "Canlı Oyun"

        val initialStream = servers.firstOrNull() ?: ResolvedStream(
            streamUrl = "",
            referer = "https://tiestep.top/",
            origin = "https://tiestep.top",
            userAgent = StreamResolver.USER_AGENT,
            channelName = currentChannelName,
            serverName = "Player 1"
        )

        initPlayer(initialStream)

        setContent {
            var currentServerIdx by remember { mutableIntStateOf(0) }
            var isBuffering by remember { mutableStateOf(true) }
            var errorMessage by remember { mutableStateOf<String?>(null) }
            var showControls by remember { mutableStateOf(true) }
            var showServerDialog by remember { mutableStateOf(false) }

            val currentServer = servers.getOrNull(currentServerIdx) ?: initialStream

            // Auto-hide controls after 3.5 seconds
            LaunchedEffect(showControls) {
                if (showControls) {
                    delay(3500)
                    showControls = false
                }
            }

            DisposableEffect(exoPlayer) {
                val listener = object : Player.Listener {
                    override fun onPlaybackStateChanged(state: Int) {
                        isBuffering = state == Player.STATE_BUFFERING
                        if (state == Player.STATE_READY) {
                            errorMessage = null
                        }
                    }

                    override fun onPlayerError(error: PlaybackException) {
                        // Auto-fallback to next server if available!
                        if (currentServerIdx < servers.size - 1) {
                            val nextIdx = currentServerIdx + 1
                            val nextServer = servers[nextIdx]
                            errorMessage = "${currentServer.serverName} xətası. Növbəti serverə (${nextServer.serverName}) keçilir..."
                            currentServerIdx = nextIdx
                            switchServer(nextServer)
                        } else {
                            errorMessage = "Yayım xətası (${error.errorCodeName}). Zəhmət olmasa digər serveri seçin."
                        }
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
                    .clickable {
                        // Toggle header and controls visibility on tap!
                        showControls = !showControls
                    }
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

                // Top Header Overlay (AUTO-HIDES AFTER 3.5 SECONDS)
                AnimatedVisibility(
                    visible = showControls,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.align(Alignment.TopCenter)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                Brush.verticalGradient(
                                    listOf(Color(0xEE000000), Color(0x66000000), Color.Transparent)
                                )
                            )
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = { finish() }) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                                contentDescription = "Geri",
                                tint = Color.White
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = matchName,
                                color = Color.White,
                                fontSize = 16.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Surface(
                                    color = StreamRed,
                                    shape = CircleShape,
                                    modifier = Modifier.size(7.dp)
                                ) {}
                                Spacer(modifier = Modifier.width(6.dp))
                                Text(
                                    text = "$currentChannelName • ${currentServer.serverName}",
                                    color = StreamGold,
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Server Switcher Button
                        if (servers.size > 1) {
                            Surface(
                                shape = RoundedCornerShape(8.dp),
                                color = Color(0x55FFFFFF),
                                modifier = Modifier.clickable {
                                    showServerDialog = true
                                    showControls = true
                                }
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Dns,
                                        contentDescription = "Serverlər",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = "Serverlər (${servers.size})",
                                        color = Color.White,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                        }
                    }
                }

                // Buffering Spinner
                if (isBuffering) {
                    CircularProgressIndicator(
                        color = StreamRed,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                // Error Banner
                errorMessage?.let { msg ->
                    Surface(
                        color = Color(0xEE8B0000),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 60.dp, start = 16.dp, end = 16.dp)
                    ) {
                        Text(
                            text = msg,
                            color = Color.White,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }
                }

                // Server Selection Modal Dialog
                if (showServerDialog) {
                    AlertDialog(
                        onDismissRequest = { showServerDialog = false },
                        containerColor = SurfaceDark,
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Dns,
                                    contentDescription = null,
                                    tint = StreamRed,
                                    modifier = Modifier.size(22.dp)
                                )
                                Spacer(modifier = Modifier.width(10.dp))
                                Text(
                                    text = "Yayım Serverini Seçin",
                                    color = TextPrimary,
                                    fontSize = 18.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        },
                        text = {
                            LazyColumn(
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                itemsIndexed(servers) { idx, srv ->
                                    val isSelected = idx == currentServerIdx
                                    Card(
                                        shape = RoundedCornerShape(10.dp),
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) StreamRed.copy(alpha = 0.25f) else Color(0xFF222634)
                                        ),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                currentServerIdx = idx
                                                switchServer(srv)
                                                showServerDialog = false
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(horizontal = 14.dp, vertical = 12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Tv,
                                                contentDescription = null,
                                                tint = if (isSelected) StreamRed else TextSecondary,
                                                modifier = Modifier.size(20.dp)
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    text = srv.serverName,
                                                    color = if (isSelected) Color.White else TextPrimary,
                                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                                    fontSize = 15.sp
                                                )
                                                Text(
                                                    text = if (idx == 0) "Əsas Server • Tövsiyə olunur" else "Alternativ Ehtiyat Server",
                                                    color = if (isSelected) StreamGold else TextSecondary,
                                                    fontSize = 11.sp
                                                )
                                            }
                                            if (isSelected) {
                                                Icon(
                                                    imageVector = Icons.Default.Check,
                                                    contentDescription = "Seçilib",
                                                    tint = StreamGold,
                                                    modifier = Modifier.size(20.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            TextButton(onClick = { showServerDialog = false }) {
                                Text("Bağla", color = Color.White)
                            }
                        }
                    )
                }
            }
        }
    }

    private fun switchServer(resolved: ResolvedStream) {
        val headers = mapOf(
            "Referer" to resolved.referer,
            "Origin" to resolved.origin,
            "User-Agent" to resolved.userAgent
        )
        val dataSourceFactory = OkHttpDataSource.Factory(StreamResolver.httpClient)
            .setDefaultRequestProperties(headers)

        val mediaSource = HlsMediaSource.Factory(dataSourceFactory)
            .createMediaSource(MediaItem.fromUri(resolved.streamUrl))

        exoPlayer?.apply {
            stop()
            setMediaSource(mediaSource)
            prepare()
            playWhenReady = true
        }
    }

    private fun initPlayer(resolved: ResolvedStream) {
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(2000, 15000, 800, 1200)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        exoPlayer = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .build()

        switchServer(resolved)
    }

    private fun hideSystemUI() {
        try {
            WindowCompat.setDecorFitsSystemWindows(window, false)
            val controller = WindowCompat.getInsetsController(window, window.decorView)
            controller.hide(WindowInsetsCompat.Type.systemBars())
            controller.systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        } catch (_: Exception) {}
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
