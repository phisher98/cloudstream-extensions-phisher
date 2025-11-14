package com.Desicinemas

import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.ResponseParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URI
import kotlin.reflect.KClass

val JSONParser = object : ResponseParser {
    val mapper: ObjectMapper = jacksonObjectMapper()
        .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
        .configure(JsonReadFeature.ALLOW_UNESCAPED_CONTROL_CHARS.mappedFeature(), true)
        .configure(JsonReadFeature.ALLOW_UNQUOTED_FIELD_NAMES.mappedFeature(), true)
        .configure(JsonReadFeature.ALLOW_TRAILING_COMMA.mappedFeature(), true)

    override fun <T : Any> parse(text: String, kClass: KClass<T>): T {
        return mapper.readValue(text, kClass.java)
    }

    override fun <T : Any> parseSafe(text: String, kClass: KClass<T>): T? {
        return try {
            mapper.readValue(text, kClass.java)
        } catch (_: Exception) {
            null
        }
    }

    override fun writeValueAsString(obj: Any): String {
        return mapper.writeValueAsString(obj)
    }
}

val app = Requests(responseParser = JSONParser).apply {
    defaultHeaders = mapOf("User-Agent" to USER_AGENT)
}


inline fun <reified T : Any> tryParseJson(text: String): T? {
    return try {
        return JSONParser.parse(text, T::class)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}

suspend fun loadCustomExtractor(
    name: String? = null,
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
    quality: Int? = null,
) {
    loadExtractor(url, referer, subtitleCallback) { link ->
        CoroutineScope(Dispatchers.IO).launch {
            callback.invoke(
                newExtractorLink(
                    name ?: link.source,
                    name ?: link.name,
                    link.url,
                ) {
                    this.quality = when {
                        link.name == "VidSrc" -> Qualities.P1080.value
                        link.type == ExtractorLinkType.M3U8 -> link.quality
                        else -> quality ?: link.quality
                    }
                    this.type = link.type
                    this.referer = link.referer
                    this.headers = link.headers
                    this.extractorData = link.extractorData
                }
            )
        }
    }
}


suspend fun resolveIframeSrc(initialUrl: String): String? {
    return try {
        val initialResponse = app.get(initialUrl, allowRedirects = false)

        val refreshUrl = initialResponse.documentLarge
            .selectFirst("meta[http-equiv=refresh]")
            ?.attr("content")
            ?.substringAfter("url=")
            ?.removeSurrounding("'", "'")
            ?.trim()
            .takeIf { !it.isNullOrEmpty() }
            ?: run {
                println("⚠️ No refresh meta tag found")
                return null
            }

        val refreshResponse = app.get(refreshUrl, allowRedirects = false)
        val cookieHeader = refreshResponse.headers["set-cookie"].orEmpty()
        val redirectBaseUrl = getBaseUrl(refreshUrl)
        val finalResponse = app.get(redirectBaseUrl, headers = mapOf("cookie" to cookieHeader))
        val iframeSrc = finalResponse.documentLarge.selectFirst("iframe")?.attr("src")
        println("✅ Found iframe src: $iframeSrc")
        iframeSrc
    } catch (e: Exception) {
        println("❌ Error resolving iframe: ${e.message}")
        e.printStackTrace()
        null
    }
}
private fun getBaseUrl(url: String): String {
    return try {
        URI(url).let { "${it.scheme}://${it.host}" }
    } catch (_: Exception) {
        ""
    }
}



