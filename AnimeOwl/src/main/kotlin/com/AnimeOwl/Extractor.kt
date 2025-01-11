package com.Animeowl

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import kotlin.text.Regex

open class OwlExtractor : ExtractorApi() {
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
        val servers=app.get("$referer$datasrc").parsedSafe<Response>()
        val sources= mutableListOf<String>()
        servers?.kaido?.let {
            sources+="$it$jwt"
        }
        servers?.luffy?.let {
            val m3u8= app.get("$it$jwt", allowRedirects = false).headers["location"] ?:return
            sources+=m3u8
        }
        servers?.zoro?.let {
            val m3u8= app.get("$it$jwt").parsedSafe<Zoro>()?.url ?:return
            val vtt= app.get("$it$jwt").parsedSafe<Zoro>()?.subtitle ?:return
            sources+=m3u8
            sources+=vtt
        }
        sources.amap { m3u8->
            if (m3u8.contains("vvt"))
            {
                subtitleCallback.invoke(
                    SubtitleFile(
                        "English",
                        m3u8
                    )
                )
            }
            else
            {
                callback.invoke(
                    ExtractorLink(
                        name,
                        name,
                        m3u8,
                        mainUrl,
                        Qualities.P1080.value,
                        INFER_TYPE,
                    )
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

data class Response(
    val kaido: String,
    val luffy: String,
    val zoro: String,
)

data class Zoro(
    val url: String,
    val subtitle: String,
)

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

