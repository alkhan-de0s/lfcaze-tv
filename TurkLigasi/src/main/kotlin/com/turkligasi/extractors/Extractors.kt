package com.turkligasi.extractors

import android.util.Base64
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.turkligasi.BiatDomainResponse
import com.turkligasi.TrGoalsAuthResponse

object Extractors {
    private val jsonMapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    private const val DEFAULT_UA =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/133.0.0.0 Safari/537.36"

    /**
     * Unpacks Dean Edwards packer JavaScript: eval(function(p,a,c,k,e,d)...)
     */
    fun unpackPacker(script: String): String {
        return try {
            val pattern = Regex(
                """eval\(function\(p,a,c,k,e,[rd]\)\{[\s\S]*?\}\('([\s\S]*?)',\s*(\d+),\s*(\d+),\s*'([\s\S]*?)'\.split\('\|'\)"""
            )
            val match = pattern.find(script) ?: return script
            var p = match.groupValues[1]
            val a = match.groupValues[2].toIntOrNull() ?: 62
            var c = match.groupValues[3].toIntOrNull() ?: 0
            val k = match.groupValues[4].split('|')

            fun baseN(num: Int, base: Int): String {
                val chars = "0123456789abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ"
                if (num == 0) return "0"
                var n = num
                val sb = StringBuilder()
                while (n > 0) {
                    sb.append(chars[n % base])
                    n /= base
                }
                return sb.reverse().toString()
            }

            while (c > 0) {
                c--
                val word = k.getOrNull(c)
                if (!word.isNullOrEmpty()) {
                    val key = baseN(c, a)
                    p = p.replace(Regex("""\b$key\b"""), word)
                }
            }
            p
        } catch (_: Exception) {
            script
        }
    }

    /**
     * TRGoals Extractor: POST to /auth.php -> returns CDN URL + TOKEN -> playable M3U8
     */
    suspend fun extractTrgoals(
        trgoalsDomain: String,
        sourceId: String,
        isChannel: Boolean = true,
        sourceName: String = "TRGoals CDN"
    ): List<ExtractorLink> {
        val links = mutableListOf<ExtractorLink>()
        try {
            val authUrl = "$trgoalsDomain/auth.php"

            val res = app.post(
                authUrl,
                headers = mapOf(
                    "User-Agent" to DEFAULT_UA,
                    "Referer" to "$trgoalsDomain/",
                    "Origin" to trgoalsDomain,
                    "X-Requested-With" to "XMLHttpRequest"
                ),
                data = mapOf(if (isChannel) "channel" to sourceId else "id" to sourceId)
            ).text

            val authData = jsonMapper.readValue<TrGoalsAuthResponse>(res)
            val streamUrl = authData.url
            val token = authData.token

            if (!streamUrl.isNullOrEmpty()) {
                val playUrl = if (streamUrl.contains(".m3u8")) streamUrl else "$streamUrl#.m3u8"
                val headers = mutableMapOf(
                    "User-Agent" to DEFAULT_UA,
                    "Referer" to "$trgoalsDomain/",
                    "Origin" to trgoalsDomain
                )
                if (!token.isNullOrEmpty()) {
                    headers["usertoken"] = token
                    headers["pl"] = "TrGoals"
                }

                links.add(
                    ExtractorLink(
                        source = sourceName,
                        name = "$sourceName (FHD/HD)",
                        url = playUrl,
                        referer = "$trgoalsDomain/",
                        quality = Qualities.P1080.value,
                        type = ExtractorLinkType.M3U8,
                        headers = headers
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return links
    }

    /**
     * Aga CDN Extractor (Maçkeyfi & İzlemaç): GET /t?id=<matchId> -> AGA domain + verify token
     */
    suspend fun extractAga(
        domain: String,
        matchId: String,
        sourceName: String = "Maçkeyfi AGA"
    ): List<ExtractorLink> {
        val links = mutableListOf<ExtractorLink>()
        try {
            val cleanMatchId = Regex("""\d+""").find(matchId)?.value ?: matchId
            val tUrl = "$domain/t?id=$cleanMatchId"
            val tRes = app.get(
                tUrl,
                headers = mapOf(
                    "User-Agent" to DEFAULT_UA,
                    "Referer" to "$domain/wp-content/themes/ikisifirbirdokuz/match-center.php?id=$cleanMatchId"
                )
            ).text

            // tRes format: ["atob(\"...\")", "", "token", "{...}", ["domain"], "?verify=..."]
            val b64Match = Regex("""atob\("([^"]+)"\)""").find(tRes) ?: return links
            val b64 = b64Match.groupValues[1]
            val decodedDom = String(Base64.decode(b64, Base64.DEFAULT)).trim()
            val cleanDom = if (decodedDom.startsWith(".")) decodedDom.substring(1) else decodedDom

            val verifyMatch = Regex("""\?verify=[^"'\],]+""").find(tRes)
            val verify = verifyMatch?.value ?: ""

            val streamUrl = "https://$cleanDom/bc2b05d321cb80050c5d035a9daeb26d/-/$cleanMatchId/playlist.m3u8$verify"

            links.add(
                ExtractorLink(
                    source = sourceName,
                    name = "$sourceName (720p HD)",
                    url = streamUrl,
                    referer = "$domain/",
                    quality = Qualities.P720.value,
                    type = ExtractorLinkType.M3U8,
                    headers = mapOf(
                        "User-Agent" to DEFAULT_UA,
                        "Referer" to "$domain/"
                    )
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return links
    }

    /**
     * Taraftarium Extractor: watch iframe -> loadstream.php -> unpacked M3U8
     */
    suspend fun extractTaraftarium(
        taraftariumDomain: String,
        watchSlugOrUrl: String,
        sourceName: String = "Taraftarium VoleStream"
    ): List<ExtractorLink> {
        val links = mutableListOf<ExtractorLink>()
        try {
            val watchUrl = if (watchSlugOrUrl.startsWith("http")) {
                watchSlugOrUrl
            } else {
                "$taraftariumDomain/channel/watch/$watchSlugOrUrl"
            }

            val watchHtml = app.get(
                watchUrl,
                headers = mapOf("User-Agent" to DEFAULT_UA, "Referer" to "$taraftariumDomain/")
            ).text

            // Look for loadstream.php iframe
            val iframeSrcMatch = Regex("""src=["']([^"']*loadstream\.php[^"']*)["']""", RegexOption.IGNORE_CASE).find(watchHtml)
            val iframeSrc = iframeSrcMatch?.groupValues?.get(1) ?: return links

            val fullIframeUrl = if (iframeSrc.startsWith("http")) iframeSrc else "$taraftariumDomain$iframeSrc"

            val iframeHtml = app.get(
                fullIframeUrl,
                headers = mapOf("User-Agent" to DEFAULT_UA, "Referer" to watchUrl)
            ).text

            // Unpack Dean Edwards packer
            val unpacked = unpackPacker(iframeHtml)

            // Look for base64 in unpacked: atob("d2luZG93LmNvbmZpZz17bWF0Y2g6e3NvdXJjZToiaHR0cHM6Ly9...
            var m3u8Url: String? = null
            val atobMatch = Regex("""atob\("([A-Za-z0-9+/=]{20,})"\)""").find(unpacked)
            if (atobMatch != null) {
                val decoded = String(Base64.decode(atobMatch.groupValues[1], Base64.DEFAULT))
                val m3u8Find = Regex("""https?://[^"'\s<>]+\.m3u8""").find(decoded)
                if (m3u8Find != null) {
                    m3u8Url = m3u8Find.value
                }
            }

            if (m3u8Url == null) {
                m3u8Url = Regex("""https?://[^"'\s<>]+\.m3u8""").find(unpacked)?.value
            }

            if (!m3u8Url.isNullOrEmpty()) {
                links.add(
                    ExtractorLink(
                        source = sourceName,
                        name = "$sourceName (HD)",
                        url = m3u8Url,
                        referer = "$taraftariumDomain/",
                        quality = Qualities.P1080.value,
                        type = ExtractorLinkType.M3U8,
                        headers = mapOf(
                            "User-Agent" to DEFAULT_UA,
                            "Referer" to "$taraftariumDomain/"
                        )
                    )
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return links
    }

    /**
     * BiatSports Extractor: data-reality.com/domain.php -> CDN base URL + id/mono.m3u8
     */
    suspend fun extractBiatSports(
        biatDomain: String,
        channelId: String,
        sourceName: String = "BiatSports"
    ): List<ExtractorLink> {
        val links = mutableListOf<ExtractorLink>()
        try {
            val domainRes = app.get(
                "https://data-reality.com/domain.php",
                headers = mapOf("User-Agent" to DEFAULT_UA, "Referer" to "$biatDomain/")
            ).text

            val data = jsonMapper.readValue<BiatDomainResponse>(domainRes)
            val baseurl = data.baseurl ?: "https://5ln.zirvedesin244.cfd/"
            val streamUrl = "${baseurl}${channelId}/mono.m3u8"

            links.add(
                ExtractorLink(
                    source = sourceName,
                    name = "$sourceName (HD)",
                    url = streamUrl,
                    referer = "$biatDomain/",
                    quality = Qualities.P720.value,
                    type = ExtractorLinkType.M3U8,
                    headers = mapOf(
                        "User-Agent" to DEFAULT_UA,
                        "Referer" to "$biatDomain/",
                        "Origin" to biatDomain
                    )
                )
            )
        } catch (e: Exception) {
            e.printStackTrace()
        }
        return links
    }
}
