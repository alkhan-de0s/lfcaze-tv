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
        .connectTimeout(10, TimeUnit.SECONDS)
        .readTimeout(10, TimeUnit.SECONDS)
        .followRedirects(true)
        .build()

    suspend fun resolve(channelId: String, channelName: String): Result<ResolvedStream> =
        withContext(Dispatchers.IO) {
            try {
                val cleanId = channelId.trim().removePrefix("stream-")
                val watchUrl = "$MAIN_URL/watch.php?id=$cleanId"

                // Step 1: Fetch watch page to find player stream iframe
                val playerFolders = listOf("stream", "cast", "watch", "player", "plus", "casting")
                var embedUrl: String? = null
                var streamPageReferer: String = "$MAIN_URL/stream/stream-$cleanId.php"

                for (folder in playerFolders) {
                    val streamPageUrl = "$MAIN_URL/$folder/stream-$cleanId.php"
                    try {
                        val req = Request.Builder()
                            .url(streamPageUrl)
                            .header("User-Agent", USER_AGENT)
                            .header("Referer", watchUrl)
                            .build()

                        val res = httpClient.newCall(req).execute()
                        val html = res.body?.string() ?: ""
                        val doc = Jsoup.parse(html)

                        val frameSrc = doc.select("iframe#thatframe, iframe[src*='/e/'], iframe").attr("src").trim()
                        if (frameSrc.isNotBlank() && !frameSrc.contains("about:blank")) {
                            embedUrl = when {
                                frameSrc.startsWith("//") -> "https:$frameSrc"
                                frameSrc.startsWith("http") -> frameSrc
                                else -> "$MAIN_URL/$frameSrc"
                            }
                            streamPageReferer = streamPageUrl
                            break
                        }
                    } catch (e: Throwable) {
                        continue
                    }
                }

                if (embedUrl.isNullOrBlank()) {
                    return@withContext Result.failure(Exception("Yayın oynatıcı adresi bulunamadı."))
                }

                // Step 2: Fetch embed page to find window._econfig
                val embedUri = URI(embedUrl)
                val embedHost = "${embedUri.scheme}://${embedUri.host}"

                val embedReq = Request.Builder()
                    .url(embedUrl)
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", streamPageReferer)
                    .build()

                val embedRes = httpClient.newCall(embedReq).execute()
                val embedHtml = embedRes.body?.string() ?: ""

                val econfigRegex = """window\._econfig\s*=\s*'([^']+)'""".toRegex()
                val econfig = econfigRegex.find(embedHtml)?.groupValues?.get(1)
                    ?: return@withContext Result.failure(Exception("Yayın şifresi (econfig) okunamadı."))

                // Step 3: Decode econfig & extract stream URL
                val decodedJson = DaddyLiveDecoder.decodeEConfig(econfig)
                    ?: return@withContext Result.failure(Exception("Yayın şifresi çözülemedi."))

                val streamUrl = DaddyLiveDecoder.extractStreamUrl(decodedJson)
                    ?: return@withContext Result.failure(Exception("Canlı yayın m3u8 linki bulunamadı."))

                val resolved = ResolvedStream(
                    streamUrl = streamUrl,
                    referer = "$embedHost/",
                    origin = embedHost,
                    userAgent = USER_AGENT,
                    channelName = channelName
                )

                Result.success(resolved)
            } catch (e: Throwable) {
                Result.failure(e)
            }
        }
}
