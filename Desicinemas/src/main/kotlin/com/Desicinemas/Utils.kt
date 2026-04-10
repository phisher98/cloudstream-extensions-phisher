package com.Desicinemas

import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.ResponseParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
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
suspend fun resolveIframeSrc(initialUrl: String): String? {
    return try {
        if (initialUrl.isBlank()) return null

        val initialResponse = app.get(initialUrl, allowRedirects = false)

        // Extract meta refresh (case-insensitive handling)
        val metaContent = initialResponse.document
            .selectFirst("meta[http-equiv=refresh]")
            ?.attr("content")
            ?.trim()

        if (metaContent.isNullOrBlank()) {
            println("⚠️ No refresh meta tag found")
            return null
        }

        val rawRefreshUrl = metaContent
            .substringAfter("url=", "")
            .substringAfter("URL=", "")
            .trim()
            .removeSurrounding("'")
            .removeSurrounding("\"")
            .trim()

        if (rawRefreshUrl.isBlank()) {
            println("⚠️ Refresh URL empty")
            return null
        }

        // Normalize refresh URL
        val refreshUrl = when {
            rawRefreshUrl.startsWith("http", true) -> rawRefreshUrl
            rawRefreshUrl.startsWith("//") -> "https:$rawRefreshUrl"
            rawRefreshUrl.startsWith("/") -> getBaseUrl(initialUrl) + rawRefreshUrl
            else -> getBaseUrl(initialUrl).trimEnd('/') + "/" + rawRefreshUrl
        }

        if (!refreshUrl.startsWith("http")) {
            println("⚠️ Invalid refresh URL: $refreshUrl")
            return null
        }

        val refreshResponse = app.get(refreshUrl, allowRedirects = false)

        // Merge all cookies safely
        val cookieHeader = refreshResponse.headers
            .values("set-cookie")
            .joinToString("; ") { it.substringBefore(";") }

        val redirectBaseUrl = getBaseUrl(refreshUrl)

        val finalResponse = app.get(
            redirectBaseUrl,
            headers = if (cookieHeader.isNotBlank())
                mapOf("cookie" to cookieHeader)
            else emptyMap()
        )

        val rawIframe = finalResponse.document
            .selectFirst("iframe")
            ?.attr("src")
            ?.trim()

        if (rawIframe.isNullOrBlank()) {
            println("⚠️ Iframe src not found")
            return null
        }

        // Normalize iframe URL
        val iframeSrc = when {
            rawIframe.startsWith("http", true) -> rawIframe
            rawIframe.startsWith("//") -> "https:$rawIframe"
            rawIframe.startsWith("/") -> getBaseUrl(redirectBaseUrl) + rawIframe
            else -> getBaseUrl(redirectBaseUrl).trimEnd('/') + "/" + rawIframe
        }

        if (!iframeSrc.startsWith("http")) {
            println("⚠️ Invalid iframe URL: $iframeSrc")
            return null
        }

        println("✅ Found iframe src: $iframeSrc")
        iframeSrc

    } catch (e: Exception) {
        println("❌ Error resolving iframe: ${e.message}")
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

private val extractorCallbackScope = CoroutineScope(Dispatchers.IO + SupervisorJob())

suspend fun loadSourceNameExtractor(
    source: String,
    url: String,
    referer: String? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
    quality: Int? = null,
    size: String = ""
) {
    val provider = source.trim().takeIf { it.isNotBlank() }
    val sizePart = size.trim().takeIf { it.isNotBlank() }

    loadExtractor(url, referer, subtitleCallback) { link ->
        extractorCallbackScope.launch {
            val label = buildString {
                provider?.let { append(it) }
                if (link.name.isNotEmpty()) {
                    if (isNotEmpty()) append(' ')
                    append(link.name)
                }
                sizePart?.let {
                    if (isNotEmpty()) append(' ')
                    append(it)
                }
            }

            callback(
                newExtractorLink(
                    link.source,
                    label,
                    link.url
                ) {
                    this.quality = quality ?: link.quality
                    this.type = link.type
                    this.referer = link.referer
                    this.headers = link.headers
                    this.extractorData = link.extractorData
                }
            )
        }
    }
}



