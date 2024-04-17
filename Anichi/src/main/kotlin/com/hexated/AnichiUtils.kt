package com.hexated

import com.hexated.Anichi.Companion.anilistApi
import com.hexated.Anichi.Companion.apiEndPoint
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.base64Decode
import com.lagradost.cloudstream3.fixTitle
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.nicehttp.RequestBodyTypes
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI
import java.net.URLDecoder

suspend fun getTracker(name: String?, altName: String?, year: Int?, season: String?, type: String?): AniMedia? {
    return fetchId(name, year, season, type).takeIf { it?.id != null } ?: fetchId(
        altName,
        year,
        season,
        type
    )
}

suspend fun fetchId(title: String?, year: Int?, season: String?, type: String?): AniMedia? {
    val query = """
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
    """.trimIndent().trim()

    val variables = mapOf(
        "search" to title,
        "sort" to "SEARCH_MATCH",
        "type" to "ANIME",
        "season" to if(type.equals("ona", true)) "" else season?.uppercase(),
        "year" to "$year%",
        "format" to listOf(type?.uppercase())
    ).filterValues { value -> value != null && value.toString().isNotEmpty() }

    val data = mapOf(
        "query" to query,
        "variables" to variables
    ).toJson().toRequestBody(RequestBodyTypes.JSON.toMediaTypeOrNull())

    return try {
        app.post(anilistApi, requestBody = data)
            .parsedSafe<AniSearch>()?.data?.Page?.media?.firstOrNull()
    } catch (t: Throwable) {
        logError(t)
        null
    }

}

suspend fun aniToMal(id: String): String? {
    return app.post(
        anilistApi, data = mapOf(
            "query" to "{Media(id:$id,type:ANIME){idMal}}",
        )
    ).parsedSafe<DataAni>()?.data?.media?.idMal
}

fun decode(input: String): String = URLDecoder.decode(input, "utf-8")

private val embedBlackList = listOf(
    "https://mp4upload.com/",
    "https://streamsb.net/",
    "https://dood.to/",
    "https://videobin.co/",
    "https://ok.ru",
    "https://streamlare.com",
    "https://filemoon",
    "streaming.php",
)

fun embedIsBlacklisted(url: String): Boolean {
    return embedBlackList.any { url.contains(it) }
}

fun AvailableEpisodesDetail.getEpisode(
    lang: String,
    id: String,
    malId: Int?,
): List<Episode> {
    val meta = if (lang == "sub") this.sub else this.dub
    return meta.map { eps ->
        Episode(
            AnichiLoadData(id, lang, eps, malId).toJson(),
            episode = eps.toIntOrNull()
        )
    }.reversed()
}

suspend fun getM3u8Qualities(
    m3u8Link: String,
    referer: String,
    qualityName: String,
): List<ExtractorLink> {
    return M3u8Helper.generateM3u8(
        qualityName,
        m3u8Link,
        referer,
    )
}

fun String.getHost(): String {
    return fixTitle(URI(this).host.substringBeforeLast(".").substringAfterLast("."))
}

fun String.fixUrlPath() : String {
    return if(this.contains(".json?")) apiEndPoint + this else apiEndPoint + URI(this).path + ".json?" + URI(this).query
}

fun fixSourceUrls(url: String, source: String?) : String? {
    return if(source == "Ak" || url.contains("/player/vitemb")) {
        AppUtils.tryParseJson<AkIframe>(base64Decode(url.substringAfter("=")))?.idUrl
    } else {
        url.replace(" ", "%20")
    }
}