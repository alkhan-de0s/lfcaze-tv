package com.xstream.tv.ui

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.xstream.tv.ui.theme.BgDark
import com.xstream.tv.ui.theme.CardDark
import com.xstream.tv.ui.theme.XStreamTVTheme
import com.xstream.tv.ui.theme.StreamRed
import com.xstream.tv.ui.theme.SurfaceDark
import com.xstream.tv.ui.theme.TextPrimary
import com.xstream.tv.ui.theme.TextSecondary

class CrashActivity : ComponentActivity() {

    companion object {
        const val EXTRA_ERROR_MESSAGE = "extra_error_message"
        const val EXTRA_STACK_TRACE = "extra_stack_trace"

        fun start(context: Context, error: Throwable) {
            val stackTrace = error.stackTraceToString()
            val intent = Intent(context, CrashActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                putExtra(EXTRA_ERROR_MESSAGE, error.localizedMessage ?: error.javaClass.name)
                putExtra(EXTRA_STACK_TRACE, stackTrace)
            }
            context.startActivity(intent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val errorMessage = intent.getStringExtra(EXTRA_ERROR_MESSAGE) ?: "Naməlum xəta"
        val stackTrace = intent.getStringExtra(EXTRA_STACK_TRACE) ?: ""

        val deviceInfo = buildString {
            appendLine("Cihaz: ${Build.MANUFACTURER} ${Build.MODEL} (${Build.PRODUCT})")
            appendLine("Android: ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT})")
            appendLine("Brand: ${Build.BRAND} / Hardware: ${Build.HARDWARE}")
        }

        val fullReport = buildString {
            appendLine("=== XStream TV QƏZA HESABATI ===")
            appendLine(deviceInfo)
            appendLine("Xəta: $errorMessage")
            appendLine("--- Stack Trace ---")
            appendLine(stackTrace)
        }

        setContent {
            XStreamTVTheme {
                CrashScreen(
                    deviceInfo = deviceInfo,
                    errorMessage = errorMessage,
                    stackTrace = stackTrace,
                    fullReport = fullReport,
                    onRestart = {
                        val restartIntent = Intent(this, MainActivity::class.java).apply {
                            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TASK
                        }
                        startActivity(restartIntent)
                        finish()
                    }
                )
            }
        }
    }
}

@Composable
fun CrashScreen(
    deviceInfo: String,
    errorMessage: String,
    stackTrace: String,
    fullReport: String,
    onRestart: () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        containerColor = BgDark,
        bottomBar = {
            Surface(
                color = SurfaceDark,
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            val clip = ClipData.newPlainText("XStream TV Crash", fullReport)
                            clipboard.setPrimaryClip(clip)
                            Toast.makeText(context, "Xəta loqu kopyalandı!", Toast.LENGTH_SHORT).show()
                        },
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.ContentCopy, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kopyala", fontSize = 14.sp)
                    }

                    Button(
                        onClick = onRestart,
                        colors = ButtonDefaults.buttonColors(containerColor = StreamRed),
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Icon(imageVector = Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Yenidən Başlat", fontSize = 14.sp)
                    }
                }
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(16.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Surface(
                color = StreamRed.copy(alpha = 0.15f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.size(64.dp)
            ) {
                Box(contentAlignment = Alignment.Center) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = StreamRed,
                        modifier = Modifier.size(36.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Text(
                text = "Tətbiqdə Xəta Baş Verdi",
                color = TextPrimary,
                fontSize = 20.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            Text(
                text = "Bu məlumat tətbiqin yeni Android versiyalarında (Honor, Tecno və s.) stabil işləməsi üçün loqlandı.",
                color = TextSecondary,
                fontSize = 13.sp,
                lineHeight = 18.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF333344), RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Cihaz Məlumatı",
                        color = Color(0xFFF6EB61),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = deviceInfo,
                        color = TextPrimary,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Card(
                shape = RoundedCornerShape(12.dp),
                colors = CardDefaults.cardColors(containerColor = CardDark),
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF333344), RoundedCornerShape(12.dp))
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Xəta Detalı",
                        color = StreamRed,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = errorMessage,
                        color = TextPrimary,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = stackTrace,
                        color = TextSecondary,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                        lineHeight = 15.sp
                    )
                }
            }
        }
    }
}
