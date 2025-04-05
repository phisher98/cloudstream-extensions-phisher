package com.Desicinemas

import com.fasterxml.jackson.core.json.JsonReadFeature
import com.fasterxml.jackson.databind.DeserializationFeature
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.USER_AGENT
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.nicehttp.Requests
import com.lagradost.nicehttp.ResponseParser
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
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
        } catch (e: Exception) {
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

fun loadExtractor(
    url: String,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit,
    name: String? = null,
): Boolean {
    return loadExtractor(
        url = url,
        subtitleCallback = subtitleCallback,
        callback = { link ->
            CoroutineScope(Dispatchers.IO).launch {
                val extractorLink = newExtractorLink(
                    link.source,
                    name ?: link.name,
                    url = link.url,
                    ExtractorLinkType.M3U8
                ) {
                    this.referer = link.referer
                    this.quality = link.quality
                    this.headers = link.headers
                    this.extractorData = link.extractorData
                }
                callback(extractorLink)
            }
        }
    )
}



