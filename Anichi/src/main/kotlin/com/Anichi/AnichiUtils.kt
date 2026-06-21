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
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec
import com.lagradost.cloudstream3.base64DecodeArray


object AnichiUtils {

    suspend fun getTracker(
        name: String?,
        altName: String?,
        year: Int?,
        season: String?,
        type: String?
    ): AniMedia? {

        val primary = fetchId(name, year, season, type)
        if (primary?.id != null) return primary

        val secondary = fetchId(altName, year, season, type)
        if (secondary?.id != null) return secondary

        return null
    }

    suspend fun fetchId(title: String?, year: Int?, season: String?, type: String?): AniMedia? {

        if (title.isNullOrBlank()) return null

        val query = """
        query (${'$'}search: String, ${'$'}type: MediaType) {
          Page(perPage: 10) {
            media(search: ${'$'}search, type: ${'$'}type) {
              id
              idMal
              seasonYear
              format
              title { romaji english native }
              synonyms
              coverImage { extraLarge large }
              bannerImage
            }
          }
        }
    """.trimIndent()

        val variables = mapOf(
            "search" to title,
            "type" to "ANIME"
        )

        val body = mapOf("query" to query, "variables" to variables)
            .toJson()
            .toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())

        val results = try {
            app.post(anilistApi, requestBody = body)
                .parsedSafe<AniSearch>()
                ?.data?.Page?.media
        } catch (_: Throwable) {
            null
        } ?: return null

        return results.maxByOrNull { media ->

            var score = 0

            // Year match
            if (year != null && media.seasonYear == year) score += 3

            // Format match
            if (!type.isNullOrBlank() && media.format?.equals(type, true) == true) score += 2

            // Collect all titles safely
            val titles = buildList {
                media.title?.romaji?.let { add(it) }
                media.title?.english?.let { add(it) }
                media.title?.native?.let { add(it) }
                media.synonyms?.let { addAll(it) }
            }

            // Exact match
            if (titles.any { it.equals(title, ignoreCase = true) }) score += 5

            // Partial match
            if (titles.any { it.contains(title, ignoreCase = true) }) score += 2

            score
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
    @param:JsonProperty("data") val data: AnilistData,
) {
    data class AnilistData(
        @param:JsonProperty("Page") val page: AnilistPage?,
        @param:JsonProperty("Media") val media: anilistMedia?,
    ) {
        data class AnilistPage(
            @param:JsonProperty("pageInfo") val pageInfo: LikePageInfo,
            @param:JsonProperty("media") val media: List<Media>,
        )
    }

    data class anilistMedia(
        @param:JsonProperty("id") val id: Int,
        @param:JsonProperty("startDate") val startDate: StartDate,
        @param:JsonProperty("episodes") val episodes: Int?,
        @param:JsonProperty("title") val title: Title,
        @param:JsonProperty("season") val season: String?,
        @param:JsonProperty("genres") val genres: List<String>,
        @param:JsonProperty("averageScore") val averageScore: Int,
        @param:JsonProperty("status") val status: String,
        @param:JsonProperty("description") val description: String?,
        @param:JsonProperty("coverImage") val coverImage: CoverImage,
        @param:JsonProperty("bannerImage") val bannerImage: String?,
        @param:JsonProperty("nextAiringEpisode") val nextAiringEpisode: SeasonNextAiringEpisode?,
        @param:JsonProperty("airingSchedule") val airingSchedule: AiringScheduleNodes?,
        @param:JsonProperty("recommendations") val recommendations: RecommendationConnection?,
        @param:JsonProperty("format") val format: String?,
    ) {
        data class StartDate(@param:JsonProperty("year") val year: Int)

        data class AiringScheduleNodes(
            @param:JsonProperty("nodes") val nodes: List<SeasonNextAiringEpisode>?
        )
    }

    data class Media(
        @param:JsonProperty("id") val id: Int,
        @param:JsonProperty("idMal") val idMal: Int?,
        @param:JsonProperty("season") val season: String?,
        @param:JsonProperty("seasonYear") val seasonYear: Int,
        @param:JsonProperty("format") val format: String?,
        @param:JsonProperty("averageScore") val averageScore: Int,
        @param:JsonProperty("episodes") val episodes: Int,
        @param:JsonProperty("title") val title: Title,
        @param:JsonProperty("description") val description: String?,
        @param:JsonProperty("coverImage") val coverImage: CoverImage,
        @param:JsonProperty("synonyms") val synonyms: List<String>,
        @param:JsonProperty("nextAiringEpisode") val nextAiringEpisode: SeasonNextAiringEpisode?,
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

fun decodeToBeParsed(encoded: String): String? {
    return try {
        val raw = base64DecodeArray(encoded)

        if (raw.size < 29) return null

        val iv = raw.copyOfRange(1, 13)

        val ctr = ByteArray(16)
        System.arraycopy(iv, 0, ctr, 0, iv.size)
        ctr[15] = 0x02

        val ciphertext = raw.copyOfRange(13, raw.size - 16)

        val key = MessageDigest
            .getInstance("SHA-256")
            .digest("Xot36i3lK3:v1".toByteArray())

        val cipher = Cipher.getInstance("AES/CTR/NoPadding")
        cipher.init(
            Cipher.DECRYPT_MODE,
            SecretKeySpec(key, "AES"),
            IvParameterSpec(ctr)
        )

        cipher.doFinal(ciphertext).toString(Charsets.UTF_8)
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}