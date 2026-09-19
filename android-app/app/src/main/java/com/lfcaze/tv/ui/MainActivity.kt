package com.lfcaze.tv.ui

import android.content.Context
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.animation.animateColorAsState
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
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Dns
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SportsSoccer
import androidx.compose.material.icons.filled.Tv
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
            val context = LocalContext.current
            val prefs = remember { context.getSharedPreferences("lfc_prefs", Context.MODE_PRIVATE) }
            var isDarkTheme by remember { mutableStateOf(prefs.getBoolean("is_dark_theme", true)) }

            LFCAZETVTheme(darkTheme = isDarkTheme) {
                HomeScreen(
                    isDarkTheme = isDarkTheme,
                    onToggleTheme = {
                        val newMode = !isDarkTheme
                        isDarkTheme = newMode
                        prefs.edit().putBoolean("is_dark_theme", newMode).apply()
                    }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(
    isDarkTheme: Boolean,
    onToggleTheme: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var config by remember { mutableStateOf<LiverpoolConfig?>(null) }
    var isLoading by remember { mutableStateOf(true) }
    var resolvingChannel by remember { mutableStateOf<LiverpoolChannel?>(null) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val bgColor = if (isDarkTheme) BgDark else BgLight
    val surfaceColor = if (isDarkTheme) SurfaceDark else SurfaceLight
    val cardBg = if (isDarkTheme) CardDark else CardLight
    val cardBorder = if (isDarkTheme) CardBorderDark else CardBorderLight
    val textPrimary = if (isDarkTheme) TextPrimaryDark else TextPrimaryLight
    val textSecondary = if (isDarkTheme) TextSecondaryDark else TextSecondaryLight

    fun loadData() {
        isLoading = true
        errorMessage = null
        scope.launch {
            val result = MatchRepository.fetchMatchConfig()
            isLoading = false
            result.onSuccess {
                config = it
            }.onFailure {
                errorMessage = "Oyun məlumatları alına bilmədi. Zəhmət olmasa internet bağlantınızı yoxlayın."
            }
        }
    }

    LaunchedEffect(Unit) {
        loadData()
    }

    fun playChannel(channel: LiverpoolChannel, matchTitle: String) {
        resolvingChannel = channel
        scope.launch {
            // Resolve all available servers (Player 1-6)
            val result = StreamResolver.resolveAll(channel.id, channel.name)
            resolvingChannel = null
            result.onSuccess { servers ->
                PlayerActivity.start(
                    context = context,
                    servers = servers,
                    channelId = channel.id,
                    channelName = channel.name,
                    matchName = matchTitle
                )
            }.onFailure { err ->
                Toast.makeText(
                    context,
                    "Yayım açıla bilmədi: ${err.message ?: "Server tapılmadı"}",
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
                            shape = RoundedCornerShape(10.dp),
                            color = LfcRed,
                            modifier = Modifier.size(36.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = Icons.Default.Tv,
                                    contentDescription = null,
                                    tint = Color.White,
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "LFCAZE TV",
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 19.sp,
                                    color = textPrimary,
                                    letterSpacing = 0.5.sp
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Surface(
                                    shape = RoundedCornerShape(4.dp),
                                    color = LfcRed.copy(alpha = 0.15f)
                                ) {
                                    Text(
                                        text = "LIVE",
                                        color = LfcRed,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Black,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                            Text(
                                text = "Canlı İdman Yayımları",
                                fontSize = 11.sp,
                                color = textSecondary,
                                fontWeight = FontWeight.Normal
                            )
                        }
                    }
                },
                actions = {
                    // Theme Switcher Button
                    IconButton(onClick = onToggleTheme) {
                        Icon(
                            imageVector = if (isDarkTheme) Icons.Default.LightMode else Icons.Default.DarkMode,
                            contentDescription = "Mövzunu Dəyiş",
                            tint = if (isDarkTheme) LfcGold else textPrimary
                        )
                    }

                    // Refresh Button
                    IconButton(onClick = { loadData() }) {
                        Icon(
                            imageVector = Icons.Default.Refresh,
                            contentDescription = "Yenilə",
                            tint = textPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = surfaceColor
                )
            )
        },
        containerColor = bgColor
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
                            color = textSecondary,
                            fontSize = 15.sp,
                            textAlign = TextAlign.Center
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        Button(
                            onClick = { loadData() },
                            colors = ButtonDefaults.buttonColors(containerColor = LfcRed),
                            shape = RoundedCornerShape(10.dp)
                        ) {
                            Text("Yenidən cəhd et", color = Color.White, fontWeight = FontWeight.Bold)
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
                        // Match Banner Header (Clean, No Logo Box)
                        item {
                            MatchBannerCard(
                                config = current,
                                isDarkTheme = isDarkTheme,
                                cardBg = cardBg,
                                cardBorder = cardBorder,
                                textPrimary = textPrimary,
                                textSecondary = textSecondary
                            )
                        }

                        // Channels Header
                        if (current != null && current.active && current.channels.isNotEmpty()) {
                            item {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 4.dp, bottom = 2.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.Dns,
                                        contentDescription = null,
                                        tint = LfcRed,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "Yayım Kanalları (${current.channels.size})",
                                        color = textPrimary,
                                        fontSize = 16.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Spacer(modifier = Modifier.weight(1f))
                                    Surface(
                                        shape = RoundedCornerShape(6.dp),
                                        color = LiveGreen.copy(alpha = 0.12f)
                                    ) {
                                        Text(
                                            text = "Çoxlu Server Aktiv",
                                            color = LiveGreen,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                                        )
                                    }
                                }
                            }

                            items(current.channels) { ch ->
                                ChannelItemCard(
                                    channel = ch,
                                    cardBg = cardBg,
                                    cardBorder = cardBorder,
                                    textPrimary = textPrimary,
                                    textSecondary = textSecondary,
                                    onClick = { playChannel(ch, current.match) }
                                )
                            }
                        } else {
                            item {
                                InactiveMatchCard(
                                    cardBg = cardBg,
                                    cardBorder = cardBorder,
                                    textPrimary = textPrimary,
                                    textSecondary = textSecondary
                                )
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
                    containerColor = surfaceColor,
                    title = {
                        Text(
                            text = "Serverlər Yoxlanılır...",
                            color = textPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                    },
                    text = {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(8.dp)
                        ) {
                            CircularProgressIndicator(
                                color = LfcRed,
                                modifier = Modifier.size(34.dp)
                            )
                            Spacer(modifier = Modifier.width(16.dp))
                            Column {
                                Text(
                                    text = ch.name,
                                    color = textPrimary,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp
                                )
                                Text(
                                    text = "6 müxtəlif player serveri axtarılır...",
                                    color = textSecondary,
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
fun MatchBannerCard(
    config: LiverpoolConfig?,
    isDarkTheme: Boolean,
    cardBg: Color,
    cardBorder: Color,
    textPrimary: Color,
    textSecondary: Color
) {
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
        colors = CardDefaults.cardColors(containerColor = cardBg),
        modifier = Modifier
            .fillMaxWidth()
            .border(
                1.dp,
                if (isActive) LfcRed.copy(alpha = 0.5f) else cardBorder,
                RoundedCornerShape(16.dp)
            )
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    if (isDarkTheme) {
                        Brush.verticalGradient(
                            listOf(
                                if (isActive) LfcDarkRed.copy(alpha = 0.4f) else Color(0xFF141824),
                                cardBg
                            )
                        )
                    } else {
                        Brush.verticalGradient(
                            listOf(
                                if (isActive) Color(0xFFFEE2E2) else Color(0xFFF1F5F9),
                                cardBg
                            )
                        )
                    }
                )
                .padding(20.dp)
        ) {
            Column(modifier = Modifier.fillMaxWidth()) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isActive) {
                        Surface(
                            color = LfcRed.copy(alpha = alpha),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "● CANLI YAYIM",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    } else {
                        Surface(
                            color = if (isDarkTheme) Color(0xFF2A2E3D) else Color(0xFFE2E8F0),
                            shape = RoundedCornerShape(6.dp)
                        ) {
                            Text(
                                text = "GÖZLƏMƏDƏ",
                                color = if (isDarkTheme) Color.White else Color(0xFF475569),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp)
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.SportsSoccer,
                            contentDescription = null,
                            tint = LfcRed,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Matchday Live",
                            color = textSecondary,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                Text(
                    text = config?.match ?: "Canlı İdman Yayımları",
                    color = textPrimary,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    lineHeight = 26.sp
                )

                if (!config?.info.isNullOrBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = config?.info ?: "",
                        color = textSecondary,
                        fontSize = 13.sp,
                        lineHeight = 18.sp
                    )
                }
            }
        }
    }
}

@Composable
fun ChannelItemCard(
    channel: LiverpoolChannel,
    cardBg: Color,
    cardBorder: Color,
    textPrimary: Color,
    textSecondary: Color,
    onClick: () -> Unit
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, cardBorder, RoundedCornerShape(14.dp))
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
                color = LfcRed.copy(alpha = 0.12f),
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
                    color = textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(top = 2.dp)
                ) {
                    Text(
                        text = "HD 1080p",
                        color = LfcGold,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = " • 6 Server (Avto-Keçid)",
                        color = textSecondary,
                        fontSize = 11.sp
                    )
                }
            }

            Surface(
                color = LfcRed,
                shape = RoundedCornerShape(8.dp)
            ) {
                Text(
                    text = "İzlə",
                    color = Color.White,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp)
                )
            }
        }
    }
}

@Composable
fun InactiveMatchCard(
    cardBg: Color,
    cardBorder: Color,
    textPrimary: Color,
    textSecondary: Color
) {
    Card(
        shape = RoundedCornerShape(14.dp),
        colors = CardDefaults.cardColors(containerColor = cardBg),
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, cardBorder, RoundedCornerShape(14.dp))
            .padding(top = 10.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                shape = CircleShape,
                color = LfcRed.copy(alpha = 0.08f),
                modifier = Modifier.size(56.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Tv,
                        contentDescription = null,
                        tint = LfcRed,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }
            Spacer(modifier = Modifier.height(14.dp))
            Text(
                text = "Hazırda Aktiv Oyun Yoxdur",
                color = textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.Bold
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Oyun günü Telegram botuna oyun adı və kanalları əlavə edildikdə bütün yayımlar burada dərhal görünəcəkdir.",
                color = textSecondary,
                fontSize = 13.sp,
                lineHeight = 19.sp,
                textAlign = TextAlign.Center
            )
        }
    }
}
