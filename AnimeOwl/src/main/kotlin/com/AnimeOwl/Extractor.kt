package com.Animeowl

import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.module.kotlin.jacksonObjectMapper
import com.fasterxml.jackson.module.kotlin.readValue
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

    override suspend fun getUrl(url: String, referer: String?, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit) {
        val response = app.get(url).document
        val datasrc=response.select("button#hot-anime-tab").attr("data-source")
        val id=datasrc.substringAfterLast("/")
        val epJS= app.get("$referer/players/$id.v2.js").text.let {
            Deobfuscator.deobfuscateScript(it)
        }
        val jwt=findFirstJwt(epJS?: throw Exception("Unable to get jwt")) ?:return
        val jsonString=app.get("$referer$datasrc").toString()
        val mapper = jacksonObjectMapper()
        val servers: Map<String, List<VideoData>> = mapper.readValue(jsonString)
        val sources = mutableListOf<Pair<String, String>>()
        Log.d("Phisher","I' here")
        servers["kaido"]?.firstOrNull()?.url?.let {
            val finalUrl = "$it$jwt"
            sources += "Kaido" to finalUrl
        }

        servers["luffy"]?.forEach { video ->
            val finalUrl = "${video.url}$jwt"
            val m3u8 = getRedirectedUrl(finalUrl)
            Log.d("Phisher", "Luffy ${video.resolution} M3U8 Added: $m3u8")
            sources += "Luffy-${video.resolution}" to m3u8
        }
        servers["zoro"]?.firstOrNull()?.url?.let {
            val finalUrl = "$it$jwt"
            val jsonResponse = getZoroJson(finalUrl)
            val (m3u8, vtt) = fetchZoroUrl(jsonResponse) ?: return
            sources += "Zoro" to m3u8
            sources += "Zoro" to vtt
        }


        sources.amap { (key, url) ->
            if (url.endsWith(".vvt")) {
                subtitleCallback.invoke(SubtitleFile("English", url))
            } else {
                callback.invoke(
                    newExtractorLink(
                        "AnimeOwl $key",
                        "AnimeOwl $key",
                        url = url,
                        INFER_TYPE
                    ) {
                        this.referer = mainUrl
                        this.quality = when {
                            key.contains("480") -> Qualities.P480.value
                            key.contains("720") -> Qualities.P720.value
                            key.contains("1080") -> Qualities.P1080.value
                            key.contains("1440") -> Qualities.P1440.value
                            key.contains("2160") -> Qualities.P2160.value
                            else -> Qualities.P1080.value
                        }
                    }
                )
            }
        }
        return
    }
}

private fun findFirstJwt(text: String): String? {
    val jwtPattern = Regex("['\"]([A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+\\.[A-Za-z0-9-_]+)['\"]")
    return jwtPattern.find(text)?.groupValues?.get(1)
}


fun getRedirectedUrl(url: String): String {
    return url
}

data class ZoroResponse(val url: String, val subtitle: String)

suspend fun getZoroJson(url: String): String {
    return app.get(url).text
}

fun fetchZoroUrl(jsonResponse: String): Pair<String, String>? {
    return try {
        val response = jacksonObjectMapper().readValue<ZoroResponse>(jsonResponse)
        response.url to response.subtitle
    } catch (e: Exception) {
        Log.e("Error:", "Error parsing Zoro JSON: ${e.message}")
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

