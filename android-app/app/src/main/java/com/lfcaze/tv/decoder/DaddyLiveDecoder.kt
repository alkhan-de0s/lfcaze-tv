package com.lfcaze.tv.decoder

object DaddyLiveDecoder {

    private fun base64Decode(str: String): ByteArray {
        return try {
            android.util.Base64.decode(str, android.util.Base64.DEFAULT)
        } catch (e1: Throwable) {
            try {
                java.util.Base64.getDecoder().decode(str.trim())
            } catch (e2: Throwable) {
                java.util.Base64.getMimeDecoder().decode(str.trim())
            }
        }
    }

    /**
     * Decodes the obfuscated window._econfig string found in the DaddyLive player embed
     */
    fun decodeEConfig(rawBase64: String): String? {
        return try {
            val s = String(base64Decode(rawBase64), Charsets.UTF_8)
            val partsCount = 4
            val partLen = s.length / partsCount
            if (partLen <= 3) return null

            val parts = ArrayList<String>(partsCount)
            var offset = 0
            for (i in 0 until partsCount) {
                parts.add(s.substring(offset, offset + partLen))
                offset += partLen
            }

            val perm = intArrayOf(2, 0, 3, 1)
            val reordered = Array(4) { "" }

            for (i in perm.indices) {
                val part = parts[i]
                if (part.length <= 3) return null
                // Drop the 4th character (index 3)
                val trimmed = part.substring(0, 3) + part.substring(4)
                val decodedChunk = String(base64Decode(trimmed), Charsets.UTF_8)
                reordered[perm[i]] = decodedChunk
            }

            val joined = reordered.joinToString("")
            String(base64Decode(joined), Charsets.UTF_8)
        } catch (e: Throwable) {
            e.printStackTrace()
            null
        }
    }

    /**
     * Extracts the HLS manifest URL (m3u8) from the decoded JSON configuration
     */
    fun extractStreamUrl(jsonString: String): String? {
        val regex = """"(?:stream_url|stream_url_nop2p)"\s*:\s*"([^"]+)"""".toRegex()
        return regex.find(jsonString)?.groupValues?.get(1)?.replace("\\/", "/")
    }
}
