package com.lfcaze.tv.network

import com.lfcaze.tv.decoder.DaddyLiveDecoder
import com.lfcaze.tv.model.ResolvedStream
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.net.URI
import java.util.concurrent.TimeUnit

object StreamResolver {

    private const val MAIN_URL = "https://dlive.sx"
    const val USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"

    val httpClient: OkHttpClient = OkHttpClient.Builder()
        .connectTimeout(8, TimeUnit.SECONDS)
        .readTimeout(8, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    val PLAYER_SERVERS = listOf(
        "stream" to "Player 1 (Əsas)",
        "cast" to "Player 2 (Cast)",
        "watch" to "Player 3 (Watch)",
        "plus" to "Player 4 (Plus)",
        "casting" to "Player 5 (Casting)",
        "player" to "Player 6 (Player)"
    )

    suspend fun resolveAll(channelId: String, channelName: String): Result<List<ResolvedStream>> =
        withContext(Dispatchers.IO) {
            val cleanId = channelId.trim().removePrefix("stream-")
            val watchUrl = "$MAIN_URL/watch.php?id=$cleanId"
            val results = mutableListOf<ResolvedStream>()

            for ((folder, serverLabel) in PLAYER_SERVERS) {
                val streamPageUrl = "$MAIN_URL/$folder/stream-$cleanId.php"
                try {
                    val req = Request.Builder()
                        .url(streamPageUrl)
                        .header("User-Agent", USER_AGENT)
                        .header("Referer", watchUrl)
                        .build()

                    val res = httpClient.newCall(req).execute()
                    val html = res.body?.string() ?: continue
                    val doc = Jsoup.parse(html)

                    val frameSrc = doc.select("iframe#thatframe, iframe[src*='/e/'], iframe").attr("src").trim()
                    if (frameSrc.isBlank() || frameSrc.contains("about:blank")) continue

                    val embedUrl = when {
                        frameSrc.startsWith("//") -> "https:$frameSrc"
                        frameSrc.startsWith("http") -> frameSrc
                        else -> "$MAIN_URL/$frameSrc"
                    }

                    val embedUri = URI(embedUrl)
                    val embedHost = "${embedUri.scheme}://${embedUri.host}"

                    val embedReq = Request.Builder()
                        .url(embedUrl)
                        .header("User-Agent", USER_AGENT)
                        .header("Referer", streamPageUrl)
                        .build()

                    val embedRes = httpClient.newCall(embedReq).execute()
                    val embedHtml = embedRes.body?.string() ?: continue

                    val econfigRegex = """window\._econfig\s*=\s*'([^']+)'""".toRegex()
                    val econfig = econfigRegex.find(embedHtml)?.groupValues?.get(1) ?: continue

                    val decodedJson = DaddyLiveDecoder.decodeEConfig(econfig) ?: continue
                    val streamUrl = DaddyLiveDecoder.extractStreamUrl(decodedJson) ?: continue

                    results.add(
                        ResolvedStream(
                            streamUrl = streamUrl,
                            referer = "$embedHost/",
                            origin = embedHost,
                            userAgent = USER_AGENT,
                            channelName = channelName,
                            serverName = serverLabel
                        )
                    )
                } catch (e: Throwable) {
                    continue
                }
            }

            if (results.isNotEmpty()) {
                Result.success(results)
            } else {
                Result.failure(Exception("Heç bir yayım serveri ilə əlaqə qurula bilmədi."))
            }
        }

    suspend fun resolve(channelId: String, channelName: String): Result<ResolvedStream> =
        withContext(Dispatchers.IO) {
            val all = resolveAll(channelId, channelName)
            all.mapCatching { list ->
                list.firstOrNull() ?: throw Exception("Yayım serveri tapılmadı.")
            }
        }
}
