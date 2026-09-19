package com.lfcaze.tv.ui

import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.lfcaze.tv.model.LiverpoolChannel
import com.lfcaze.tv.model.LiverpoolConfig
import com.lfcaze.tv.network.MatchRepository
import com.lfcaze.tv.network.StreamResolver
import com.lfcaze.tv.ui.theme.*
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            LFCAZETVTheme {
                HomeScreen()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var config by remember { mutableStateOf<LiverpoolConfig?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var resolvingChannel by remember { mutableStateOf<LiverpoolChannel?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    fun loadData() {
        isLoading = true
        errorMessage = null
        scope.launch {
            val result = MatchRepository.fetchMatchConfig()
            isLoading = false
            result.onSuccess {
                config = it
            }.onFailure {
                errorMessage = "Maç verisi alınamadı. Lütfen internet bağlantınızı kontrol edin."
            }
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    fun playChannel(channel: LiverpoolChannel, matchTitle: String) {
        resolvingChannel = channel
        scope.launch {
            val result = StreamResolver.resolve(channel.id, channel.name)
            resolvingChannel = null
            result.onSuccess { resolved ->
                PlayerActivity.start(
                    context = context,
                    streamUrl = resolved.streamUrl,
                    referer = resolved.referer,
                    origin = resolved.origin,
                    userAgent = resolved.userAgent,
                    channelId = channel.id,
                    channelName = channel.name,
                    matchName = matchTitle
                )
            }.onFailure { err ->
                Toast.makeText(
                    context,
                    "Yayın açılamadı: ${err.message ?: "Bilinmeyen hata"}",
                    Toast.LENGTH_LONG
                ).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Surface(
                            shape = CircleShape,
                            color = LfcRed,
                            modifier = Modifier.size(32.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = "L",
                                    color = Color.White,
                                    fontWeight = FontWeight.Black,
                                    fontSize = 18.sp
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = "LFCAZE TV",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = TextPrimary
                        )
                    }
                },
                actions = {
                    IconButton(onClick = { loadData() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Yenile",
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = SurfaceDark
                )
            )
        },
        containerColor = BgDark
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            when {
                isLoading && config == null -> {
                    CircularProgressIndicator(
                        color = LfcRed,
                        modifier = Modifier.align(Alignment.Center)
                    )
                }

                errorMessage != null && config == null -> {
                    Column(
                        modifier = Modifier
                            .align(Alignment.Center)
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = errorMessage ?: "",
                            color = TextSecondary,
                            fontSize = 15.sp
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { loadData() },
                            colors = ButtonDefaults.buttonColors(containerColor = LfcRed)
                        ) {
                            Text("Tekrar Dene")
                        }
                    }
                }

                else -> {
                    val current = config
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Match Banner Header
                        item {
                            MatchBannerCard(config = current)
                        }

                        // Channels Header
                        if (current != null && current.active && current.channels.isNotEmpty()) {
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 8.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Tv,
                                        contentDescription = null,
                                        tint = LfcRed,
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Canlı Yayın Kanalları (${current.channels.size})",
                                        color = TextPrimary,
                                        fontSize = 17.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }

                            items(current.channels) { ch ->
                                ChannelItemCard(
                                    channel = ch,
                                    onClick = { playChannel(ch, current.match) }
                                )
                            }
                        } else {
                            item {
                                InactiveMatchCard()
                            }
                        }
                    }
                }
            }

            // Stream Resolving Dialog
            resolvingChannel?.let { ch ->
                AlertDialog(
                    onDismissRequest = {},
                    confirmButton = {},
                    containerColor = SurfaceDark,
                    title = {
                        Text(
                            text = "Yayın Başlatılıyor",
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        )
                    },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            CircularProgressIndicator(
                                color = LfcRed,
                                modifier = Modifier.size(36.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = ch.name,
                                    color = TextPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 15.sp
                                )
                                Text(
                                    text = "0.5 saniyede bağlanıyor...",
                                    color = TextSecondary,
                                    fontSize = 12.sp
                                )
                            }
                        }
                    }
                )
            }
        }
    }
}

@Composable
fun MatchBannerCard(config: LiverpoolConfig?) {
    val isActive = config?.active == true && config.channels.isNotEmpty()

    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.4f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )

    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, if (isActive) LfcRed.copy(alpha = 0.5f) else CardBorder, RoundedCornerShape(16.dp))
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(
                            if (isActive) LfcDarkRed.copy(alpha = 0.5f) else Color(0xFF1E1E28),
                            CardDark
                        )
                    )
                )
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Team / Match Logo
                AsyncImage(
                    model = config?.poster?.takeIf { it.isNotBlank() }
                        ?: "https://upload.wikimedia.org/wikipedia/en/thumb/0/0c/Liverpool_FC.svg/800px-Liverpool_FC.svg.png",
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(72.dp)
                        .clip(RoundedCornerShape(8.dp))
                )

                Spacer(modifier = Modifier.width(16.dp))

                Column(modifier = Modifier.weight(1f)) {
                    if (isActive) {
                        Surface(
                            color = LfcRed.copy(alpha = alpha),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Text(
                                text = "● CANLI YAYIN",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    } else {
                        Surface(
                            color = Color(0xFF444455),
                            shape = RoundedCornerShape(4.dp),
                            modifier = Modifier.padding(bottom = 6.dp)
                        ) {
                            Text(
                                text = "BEKLEMEDE",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }

                    Text(
                        text = config?.match ?: "Liverpool FC",
                        color = TextPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                        lineHeight = 24.sp
                    )

                    if (!config?.info.isNullOrBlank()) {
                        Text(
                            text = config?.info ?: "",
                            color = TextSecondary,
                            fontSize = 13.sp,
                            modifier = Modifier.padding(top = 4.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ChannelItemCard(
    channel: LiverpoolChannel,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, CardBorder, RoundedCornerShape(12.dp))
            .clickable { onClick() }
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(
                shape = CircleShape,
                color = LfcRed.copy(alpha = 0.15f),
                modifier = Modifier.size(42.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.PlayArrow,
                        contentDescription = "Oynat",
                        tint = LfcRed,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.width(14.dp))

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = channel.name,
                    color = TextPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "HD 1080p • DaddyLive Stream",
                    color = TextSecondary,
                    fontSize = 12.sp
                )
            }

            Surface(
                color = Color(0xFF282838),
                shape = RoundedCornerShape(6.dp)
            ) {
                Text(
                    text = "İzle",
                    color = LfcGold,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
                )
            }
        }
    }
}

@Composable
fun InactiveMatchCard() {
    Card(
        shape = RoundedCornerShape(12.dp),
        colors = CardDefaults.cardColors(containerColor = CardDark),
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Default.Tv,
                contentDescription = null,
                tint = TextSecondary,
                modifier = Modifier.size(48.dp)
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = "Şu Anda Aktif Maç Yok",
                color = TextPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Maç günü Telegram botuna maç adı ve kanalları eklediğinizde yayınlar burada anında listelenecektir.",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center
            )
        }
    }
}
