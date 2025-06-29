package com.Animeowl

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.lagradost.api.Log
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlin.text.Regex

class OwlExtractor : ExtractorApi() {
    override var name = "OwlExtractor"
    override var mainUrl = "https://whguides.com"
    override val requiresReferer = false

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val document = app.get(url).document
        val dataSrc = document.selectFirst("button#hot-anime-tab")?.attr("data-source")
            ?: throw Exception("Missing data-source attribute")

        val id = dataSrc.substringAfterLast("/")
        val jsUrl = "$referer/players/$id.v2.js"

        val epJS = Deobfuscator.deobfuscateScript(app.get(jsUrl).text)
            ?: throw Exception("Failed to deobfuscate player JS")

        val jwt = findFirstJwt(epJS) ?: throw Exception("Unable to find JWT token")

        val jsonUrl = "$referer$dataSrc"
        val jsonString = app.get(jsonUrl).text

        val gson = Gson()
        val type = object : TypeToken<Map<String, List<VideoData>>>() {}.type
        val servers: Map<String, List<VideoData>> = gson.fromJson(jsonString, type)
        val sources = mutableListOf<Pair<String, String>>()


        // Kaido (single source)
        servers["kaido"]?.firstOrNull()?.url?.let {
            sources += "Kaido" to "$it$jwt"
        }

        // Luffy (multiple resolutions)
        servers["luffy"]?.forEach { video ->
            val finalUrl = "${video.url}$jwt"
            val redirectedUrl = getRedirectedUrl(finalUrl)
            Log.d("OwlExtractor", "Luffy ${video.resolution}: $redirectedUrl")
            sources += "Luffy-${video.resolution}" to redirectedUrl
        }

        // Zoro (may include subtitle)
        servers["zoro"]?.firstOrNull()?.url?.let {
            val finalUrl = "$it$jwt"
            val zoroJson = getZoroJson(finalUrl)
            fetchZoroUrl(zoroJson)?.let { (m3u8, subtitleUrl) ->
                sources += "Zoro" to m3u8
                sources += "Zoro-subtitle" to subtitleUrl
            }
        }

        // Emit results
        sources.amap { (key, url) ->
            if (url.endsWith(".vvt")) {
                subtitleCallback.invoke(SubtitleFile("English", url))
            } else {
                callback(
                    newExtractorLink(
                        "AnimeOwl",
                        "AnimeOwl $key",
                        url = url,
                        type = INFER_TYPE
                    ) {
                        this.referer = mainUrl
                        this.quality = when {
                            key.contains("480") -> Qualities.P480.value
                            key.contains("720") -> Qualities.P720.value
                            key.contains("1080") -> Qualities.P1080.value
                            key.contains("1440") -> Qualities.P1440.value
                            key.contains("2160") -> Qualities.P2160.value
                            key.contains("default") -> Qualities.P1080.value
                            key.contains("2K") -> Qualities.P1440.value
                            else -> Qualities.P720.value
                        }
                    }
                )
            }
        }
    }
}

private fun findFirstJwt(text: String): String? {
    val jwtPattern = Regex("['\"]([A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+)['\"]")
    return jwtPattern.find(text)?.groupValues?.get(1)
}

fun getRedirectedUrl(url: String): String = url

data class ZoroResponse(val url: String, val subtitle: String)

suspend fun getZoroJson(url: String): String = app.get(url).text

fun fetchZoroUrl(jsonResponse: String): Pair<String, String>? {
    return try {
        jacksonObjectMapper().readValue<ZoroResponse>(jsonResponse).let {
            it.url to it.subtitle
        }
    } catch (e: Exception) {
        Log.e("OwlExtractor", "Zoro JSON parsing failed: ${e.message}")
        null
    }
}



data class VideoData(val resolution: String, val url: String)


//Searchresponse

data class Searchresponse(
    val total: Long,
    val results: List<Result>,
) {
    data class Result(
        @JsonProperty("anime_id")
        val animeId: Long,
        @JsonProperty("anime_name")
        val animeName: String,
        @JsonProperty("mal_id")
        val malId: Long,
        @JsonProperty("updated_at")
        val updatedAt: String,
        @JsonProperty("jp_name")
        val jpName: String,
        @JsonProperty("anime_slug")
        val animeSlug: String,
        @JsonProperty("en_name")
        val enName: String,
        val thumbnail: String,
        val image: String,
        @JsonProperty("is_uncensored")
        val isUncensored: Long,
        val webp: String,
        @JsonProperty("total_episodes")
        val totalEpisodes: String,
        @JsonProperty("total_dub_episodes")
        val totalDubEpisodes: String?,
    )
}

