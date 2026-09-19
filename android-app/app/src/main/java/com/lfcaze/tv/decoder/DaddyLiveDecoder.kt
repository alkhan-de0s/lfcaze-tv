package com.lfcaze.tv.decoder

import android.util.Base64
import org.json.JSONObject
import java.nio.charset.StandardCharsets

object DaddyLiveDecoder {

    /**
     * Reconstructs the original stream payload from the obfuscated window._econfig string.
     */
    fun decodeEConfig(obfuscated: String): String? {
        return try {
            // Stage 1: Initial Base64 decode
            val rawBytes = Base64.decode(obfuscated, Base64.DEFAULT)
            val s = String(rawBytes, StandardCharsets.UTF_8)

            // Stage 2: Divide into 4 equal segments
            val partLen = s.length / 4
            val chunks = mutableListOf<String>()
            for (i in 0 until 4) {
                val start = i * partLen
                val end = if (i == 3) s.length else (i + 1) * partLen
                chunks.add(s.substring(start, end))
            }

            // Stage 3: Drop the character at index 3 in each segment
            val slicedChunks = chunks.map { chunk ->
                if (chunk.length > 3) {
                    chunk.substring(0, 3) + chunk.substring(4)
                } else {
                    chunk
                }
            }

            // Stage 4: Base64 decode each sliced chunk
            val decodedSegments = slicedChunks.map { chunk ->
                Base64.decode(chunk, Base64.DEFAULT)
            }

            // Stage 5: Reassemble with permutation [2, 0, 3, 1]
            val permutation = intArrayOf(2, 0, 3, 1)
            var totalLength = 0
            for (p in permutation) {
                totalLength += decodedSegments[p].size
            }

            val combined = ByteArray(totalLength)
            var offset = 0
            for (p in permutation) {
                val seg = decodedSegments[p]
                System.arraycopy(seg, 0, combined, offset, seg.size)
                offset += seg.size
            }

            // Stage 6: Final Base64 decode yielding the clear JSON payload
            val finalJsonBytes = Base64.decode(combined, Base64.DEFAULT)
            String(finalJsonBytes, StandardCharsets.UTF_8)
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Extracts the direct .m3u8 stream URL from the decoded JSON configuration.
     */
    fun extractStreamUrl(jsonString: String): String? {
        return try {
            val json = JSONObject(jsonString)

            if (json.has("stream_url")) {
                val url = json.getString("stream_url")
                if (url.isNotBlank()) return url
            }

            if (json.has("source")) {
                val source = json.get("source")
                if (source is JSONObject && source.has("file")) {
                    return source.getString("file")
                } else if (source is String && source.isNotBlank()) {
                    return source
                }
            }

            // Fallback regex search for .m3u8 inside the json string
            val m3u8Regex = """https?://[^\s"'<>]+\.m3u8[^\s"'<>]*""".toRegex()
            m3u8Regex.find(jsonString)?.value
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }
}
