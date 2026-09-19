package com.xstream.tv.network

import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.xstream.tv.model.LiverpoolConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request

object MatchRepository {

    private const val CONFIG_URL =
        "https://raw.githubusercontent.com/alkhan-de0s/lfcaze-tv/master/liverpool.json"

    private val jsonMapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)

    suspend fun fetchMatchConfig(): Result<LiverpoolConfig> = withContext(Dispatchers.IO) {
        try {
            val bustUrl = "$CONFIG_URL?t=${System.currentTimeMillis()}"
            val req = Request.Builder()
                .url(bustUrl)
                .header("Cache-Control", "no-cache, no-store, must-revalidate")
                .header("Pragma", "no-cache")
                .build()

            val response = StreamResolver.httpClient.newCall(req).execute()
            val bodyString = response.body?.string()
                ?: return@withContext Result.failure(Exception("Boş yanıt alındı."))

            val config = jsonMapper.readValue(bodyString, LiverpoolConfig::class.java)
            Result.success(config)
        } catch (e: Throwable) {
            Result.failure(e)
        }
    }
}
