package com.Anichi

import com.Anichi.Anichi.Companion.anilistApi
import com.Anichi.Anichi.Companion.apiEndPoint
import com.Anichi.AnichiParser.AkIframe
import com.Anichi.AnichiParser.AniMedia
import com.Anichi.AnichiParser.AniSearch
import com.Anichi.AnichiParser.DataAni
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.fixTitle
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi.CoverImage
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi.LikePageInfo
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi.RecommendationConnection
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi.SeasonNextAiringEpisode
import com.lagradost.cloudstream3.syncproviders.providers.AniListApi.Title
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.nicehttp.RequestBodyTypes
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.net.URI
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONObject

object AnichiUtils {

    suspend fun getTracker(
            name: String?,
            altName: String?,
            year: Int?,
            season: String?,
            type: String?
    ): AniMedia? {
        return fetchId(name, year, season, type).takeIf { it?.id != null }
                ?: fetchId(altName, year, season, type)
    }

    suspend fun fetchId(title: String?, year: Int?, season: String?, type: String?): AniMedia? {
        val query =
                """
        query (
          ${'$'}page: Int = 1
          ${'$'}search: String
          ${'$'}sort: [MediaSort] = [POPULARITY_DESC, SCORE_DESC]
          ${'$'}type: MediaType
          ${'$'}season: MediaSeason
          ${'$'}year: String
          ${'$'}format: [MediaFormat]
        ) {
          Page(page: ${'$'}page, perPage: 20) {
            media(
              search: ${'$'}search
              sort: ${'$'}sort
              type: ${'$'}type
              season: ${'$'}season
              startDate_like: ${'$'}year
              format_in: ${'$'}format
            ) {
              id
              idMal
              coverImage { extraLarge large }
              bannerImage
            }
          }
        }
    """
                        .trimIndent()
                        .trim()

        val variables =
                mapOf(
                                "search" to title,
                                "sort" to "SEARCH_MATCH",
                                "type" to "ANIME",
                                "season" to
                                        if (type.equals("ona", true)) "" else season?.uppercase(),
                                "year" to "$year%",
                                "format" to listOf(type?.uppercase())
                        )
                        .filterValues { value -> value != null && value.toString().isNotEmpty() }

        val data =
                mapOf("query" to query, "variables" to variables)
                        .toJson()
                        .toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())

        return try {
            app.post(anilistApi, requestBody = data)
                    .parsedSafe<AniSearch>()
                    ?.data
                    ?.Page
                    ?.media
                    ?.firstOrNull()
        } catch (t: Throwable) {
            logError(t)
            null
        }
    }

    suspend fun aniToMal(id: String): String? {
        return app.post(
                        anilistApi,
                        data =
                                mapOf(
                                        "query" to "{Media(id:$id,type:ANIME){idMal}}",
                                )
                )
                .parsedSafe<DataAni>()
                ?.data
                ?.media
                ?.idMal
    }

    private val embedBlackList =
            listOf(
                    "https://mp4upload.com/",
                    "https://streamsb.net/",
                    "https://dood.to/",
                    "https://videobin.co/",
                    "https://ok.ru",
                    "https://streamlare.com",
                    "https://filemoon",
                    "streaming.php",
            )

    suspend fun getM3u8Qualities(
            m3u8Link: String,
            referer: String,
            qualityName: String,
    ): List<ExtractorLink> {
        return M3u8Helper.generateM3u8(
                qualityName,
                m3u8Link,
                referer
        )
    }

    fun String.getHost(): String {
        return fixTitle(URI(this).host.substringBeforeLast(".").substringAfterLast("."))
    }

    fun String.fixUrlPath(): String {
        return if (this.contains(".json?")) apiEndPoint + this
        else apiEndPoint + URI(this).path + ".json?" + URI(this).query
    }

    fun fixSourceUrls(url: String, source: String?): String? {
        return if (source == "Ak" || url.contains("/player/vitemb")) {
            AppUtils.tryParseJson<AkIframe>(base64Decode(url.substringAfter("=")))?.idUrl
        } else {
            url.replace(" ", "%20")
        }
    }
}


fun parseAnimeData(jsonString: String): MetaAnimeData? {
    return try {
        val objectMapper = ObjectMapper()
        objectMapper.readValue(jsonString, MetaAnimeData::class.java)
    } catch (_: Exception) {
        null // Return null for invalid JSON instead of crashing
    }
}

suspend fun fetchTmdbLogoUrl(
    tmdbAPI: String,
    apiKey: String,
    type: TvType,
    tmdbId: Int?,
    appLangCode: String?
): String? {

    if (tmdbId == null) return null

    val appLang = appLangCode
        ?.substringBefore("-")
        ?.lowercase()

    val url = if (type == TvType.Movie) {
        "$tmdbAPI/movie/$tmdbId/images?api_key=$apiKey"
    } else {
        "$tmdbAPI/tv/$tmdbId/images?api_key=$apiKey"
    }

    val json = runCatching { JSONObject(app.get(url).text) }.getOrNull()
        ?: return null

    val logos = json.optJSONArray("logos") ?: return null
    if (logos.length() == 0) return null

    fun logoUrlAt(i: Int): String = "https://image.tmdb.org/t/p/w500${logos.getJSONObject(i).optString("file_path")}"

    if (!appLang.isNullOrBlank()) {
        for (i in 0 until logos.length()) {
            val logo = logos.optJSONObject(i) ?: continue
            if (logo.optString("iso_639_1") == appLang) {
                return logoUrlAt(i)
            }
        }
    }

    for (i in 0 until logos.length()) {
        val logo = logos.optJSONObject(i) ?: continue
        if (logo.optString("iso_639_1") == "en") {
            return logoUrlAt(i)
        }
    }

    return logoUrlAt(0)
}

private val apiUrl = "https://graphql.anilist.co"

private val headerJSON =
    mapOf("Accept" to "application/json", "Content-Type" to "application/json")

suspend fun anilistAPICall(query: String): AnilistAPIResponse {
    val data = mapOf("query" to query)
    val test = app.post(apiUrl, headers = headerJSON, data = data)
    val res =
        test.parsedSafe<AnilistAPIResponse>()
            ?: throw Exception("Unable to fetch or parse Anilist api response")
    return res
}

data class AnilistAPIResponse(
    @JsonProperty("data") val data: AnilistData,
) {
    data class AnilistData(
        @JsonProperty("Page") val page: AnilistPage?,
        @JsonProperty("Media") val media: anilistMedia?,
    ) {
        data class AnilistPage(
            @JsonProperty("pageInfo") val pageInfo: LikePageInfo,
            @JsonProperty("media") val media: List<Media>,
        )
    }

    data class anilistMedia(
        @JsonProperty("id") val id: Int,
        @JsonProperty("startDate") val startDate: StartDate,
        @JsonProperty("episodes") val episodes: Int?,
        @JsonProperty("title") val title: Title,
        @JsonProperty("season") val season: String?,
        @JsonProperty("genres") val genres: List<String>,
        @JsonProperty("averageScore") val averageScore: Int,
        @JsonProperty("status") val status: String,
        @JsonProperty("description") val description: String?,
        @JsonProperty("coverImage") val coverImage: CoverImage,
        @JsonProperty("bannerImage") val bannerImage: String?,
        @JsonProperty("nextAiringEpisode") val nextAiringEpisode: SeasonNextAiringEpisode?,
        @JsonProperty("airingSchedule") val airingSchedule: AiringScheduleNodes?,
        @JsonProperty("recommendations") val recommendations: RecommendationConnection?,
        @JsonProperty("format") val format: String?,
    ) {
        data class StartDate(@JsonProperty("year") val year: Int)

        data class AiringScheduleNodes(
            @JsonProperty("nodes") val nodes: List<SeasonNextAiringEpisode>?
        )

        fun totalEpisodes(): Int {
            return nextAiringEpisode?.episode?.minus(1)
                ?: episodes
                ?: airingSchedule?.nodes?.getOrNull(0)?.episode
                ?: 0
        }

        fun getTitle(): String {
            return title.english
                ?: title.romaji ?: throw Exception("Unable to calculate total episodes")
        }

        fun getCoverImage(): String? {
            return coverImage.extraLarge ?: coverImage.large ?: coverImage.medium
        }
    }

    data class Media(
        @JsonProperty("id") val id: Int,
        @JsonProperty("idMal") val idMal: Int?,
        @JsonProperty("season") val season: String?,
        @JsonProperty("seasonYear") val seasonYear: Int,
        @JsonProperty("format") val format: String?,
        @JsonProperty("averageScore") val averageScore: Int,
        @JsonProperty("episodes") val episodes: Int,
        @JsonProperty("title") val title: Title,
        @JsonProperty("description") val description: String?,
        @JsonProperty("coverImage") val coverImage: CoverImage,
        @JsonProperty("synonyms") val synonyms: List<String>,
        @JsonProperty("nextAiringEpisode") val nextAiringEpisode: SeasonNextAiringEpisode?,
    )
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
