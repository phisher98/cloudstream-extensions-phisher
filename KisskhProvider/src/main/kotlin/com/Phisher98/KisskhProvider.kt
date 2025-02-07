package com.Phisher98

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.BuildConfig
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.M3u8Helper
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import java.util.ArrayList

class KisskhProvider : MainAPI() {
    override var mainUrl = "https://kisskh.co"
    override var name = "Kisskh"
    override val hasMainPage = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.AsianDrama,
        TvType.Anime
    )
    override val mainPage = mainPageOf(
        "&type=2&sub=0&country=2&status=0&order=1" to "Movie Popular",
        "&type=2&sub=0&country=2&status=0&order=2" to "Movie Last Update",
        "&type=1&sub=0&country=2&status=0&order=1" to "TVSeries Popular",
        "&type=1&sub=0&country=2&status=0&order=2" to "TVSeries Last Update",
        "&type=3&sub=0&country=0&status=0&order=1" to "Anime Popular",
        "&type=3&sub=0&country=0&status=0&order=2" to "Anime Last Update",
        "&type=4&sub=0&country=0&status=0&order=1" to "Hollywood Popular",
        "&type=4&sub=0&country=0&status=0&order=2" to "Hollywood Last Update",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val home = app.get("$mainUrl/api/DramaList/List?page=$page${request.data}")
            .parsedSafe<Responses>()?.data
            ?.mapNotNull { media ->
                media.toSearchResponse()
            } ?: throw ErrorLoadingException("Invalid Json reponse")
        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = true
            ),
            hasNext = true
        )
    }

    private fun Media.toSearchResponse(): SearchResponse? {

        return newAnimeSearchResponse(
            title ?: return null,
            "$title/$id",
            TvType.TvSeries,
        ) {
            this.posterUrl = thumbnail
            addSub(episodesCount)
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse =
            app.get("$mainUrl/api/DramaList/Search?q=$query&type=0", referer = "$mainUrl/").text
        return tryParseJson<ArrayList<Media>>(searchResponse)?.mapNotNull { media ->
            media.toSearchResponse()
        } ?: throw ErrorLoadingException("Invalid Json reponse")
    }

    private fun getTitle(str: String): String {
        return str.replace(Regex("[^a-zA-Z0-9]"), "-")
    }

    override suspend fun load(url: String): LoadResponse? {
        val id = url.split("/")
        val res = app.get(
            "$mainUrl/api/DramaList/Drama/${id.last()}?isq=false",
            referer = "$mainUrl/Drama/${
                getTitle(id.first())
            }?id=${id.last()}"
        ).parsedSafe<MediaDetail>()
            ?: throw ErrorLoadingException("Invalid Json reponse")

        val episodes = res.episodes?.map { eps ->
            Episode(
                data = Data(res.title, eps.number, res.id, eps.id).toJson(),
                episode = eps.number
            )
        } ?: throw ErrorLoadingException("No Episode")

        return newTvSeriesLoadResponse(
            res.title ?: return null,
            url,
            if (res.type == "Movie" || episodes.size == 1) TvType.Movie else TvType.TvSeries,
            episodes
        ) {
            this.posterUrl = res.thumbnail
            this.year = res.releaseDate?.split("-")?.first()?.toIntOrNull()
            this.plot = res.description
            this.tags = listOf("${res.country}", "${res.status}", "${res.type}")
            this.showStatus = when (res.status) {
                "Completed" -> ShowStatus.Completed
                "Ongoing" -> ShowStatus.Ongoing
                else -> null
            }
        }

    }

    private fun getLanguage(str: String): String {
        return when (str) {
            "Indonesia" -> "Indonesian"
            else -> str
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val KisskhAPI=com.Phisher98.BuildConfig.KissKh
        val KisskhSub=com.Phisher98.BuildConfig.KisskhSub
        val loadData = parseJson<Data>(data)
        val kkey=app.get("$KisskhAPI${loadData.epsId}&version=2.8.10", timeout = 10000).parsedSafe<Key>()?.key ?:""
        app.get(
            "$mainUrl/api/DramaList/Episode/${loadData.epsId}.png?err=false&ts=&time=&kkey=$kkey",
            referer = "$mainUrl/Drama/${getTitle("${loadData.title}")}/Episode-${loadData.eps}?id=${loadData.id}&ep=${loadData.epsId}&page=0&pageSize=100"
        ).parsedSafe<Sources>()?.let { source ->
            listOf(source.video, source.thirdParty).apmap { link ->
                safeApiCall {
                    if (link?.contains(".m3u8") == true) {
                        M3u8Helper.generateM3u8(
                            this.name,
                            link,
                            referer = "$mainUrl/",
                            headers = mapOf("Origin" to mainUrl)
                        ).forEach(callback)
                    } else if (link?.contains("mp4") == true) {
                        callback.invoke(
                            ExtractorLink(
                                this.name,
                                this.name,
                                fixUrl( link),
                                "",
                                Qualities.P720.value,
                                INFER_TYPE
                            )
                        )

                } else {
                        loadExtractor(
                            link?.substringBefore("=http") ?: return@safeApiCall,
                            "$mainUrl/",
                            subtitleCallback,
                            callback
                        )
                    }
                }
            }
        }
        val kkey1=app.get("$KisskhSub${loadData.epsId}&version=2.8.10", timeout = 10000).parsedSafe<Key>()?.key ?:""
        app.get("$mainUrl/api/Sub/${loadData.epsId}?kkey=$kkey1").text.let { res ->
            tryParseJson<List<Subtitle>>(res)?.map { sub ->
                if (sub.src!!.endsWith("txt")) {
                    subtitleCallback.invoke(
                        SubtitleFile(
                            getLanguage(sub.label ?: return@map),
                            sub.src
                        )
                    )
                }
                else
                subtitleCallback.invoke(
                    SubtitleFile(
                        getLanguage(sub.label ?: return@map),
                        sub.src
                    )
                )
            }
        }

        return true

    }
// SubDecryptor Code from Thanks to https://github.com/Kohi-den/extensions-source/blob/515590ecfec6af2b915d23508266536f7f5a3ab8/src/en/kisskh/src/eu/kanade/tachiyomi/animeextension/en/kisskh/SubDecryptor.kt

    private val CHUNK_REGEX1 by lazy { Regex("^\\d+$", RegexOption.MULTILINE) }
    override fun getVideoInterceptor(extractorLink: ExtractorLink): Interceptor {
        return object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): Response {
                val request = chain.request()
                    .newBuilder()
                    .build()
                val response = chain.proceed(request)
                if (response.request.url.toString().endsWith(".txt")) {
                    val responseBody = response.body.string()
                    val chunks = responseBody.split(CHUNK_REGEX1)
                        .filter(String::isNotBlank)
                        .map(String::trim)
                    val decrypted = chunks.mapIndexed { index, chunk ->
                        val parts = chunk.split("\n")
                        val text = parts.slice(1 until parts.size)
                        val d = text.map { decrypt(it) }.joinToString("\n")
                        arrayOf(index + 1, parts.first(), d).joinToString("\n")
                    }.joinToString("\n\n")
                    val newBody = decrypted.toResponseBody(response.body.contentType())
                    return response.newBuilder()
                        .body(newBody)
                        .build()
                }
                return response
            }
        }
    }


    data class Data(
        val title: String?,
        val eps: Int?,
        val id: Int?,
        val epsId: Int?,
    )

    data class Sources(
        @JsonProperty("Video") val video: String?,
        @JsonProperty("ThirdParty") val thirdParty: String?,
    )

    data class Subtitle(
        @JsonProperty("src") val src: String?,
        @JsonProperty("label") val label: String?,
    )

    data class Responses(
        @JsonProperty("data") val data: ArrayList<Media>? = arrayListOf(),
    )

    data class Media(
        @JsonProperty("episodesCount") val episodesCount: Int?,
        @JsonProperty("thumbnail") val thumbnail: String?,
        @JsonProperty("id") val id: Int?,
        @JsonProperty("title") val title: String?,
    )

    data class Episodes(
        @JsonProperty("id") val id: Int?,
        @JsonProperty("number") val number: Int?,
        @JsonProperty("sub") val sub: Int?,
    )

    data class MediaDetail(
        @JsonProperty("description") val description: String?,
        @JsonProperty("releaseDate") val releaseDate: String?,
        @JsonProperty("status") val status: String?,
        @JsonProperty("type") val type: String?,
        @JsonProperty("country") val country: String?,
        @JsonProperty("episodes") val episodes: ArrayList<Episodes>? = arrayListOf(),
        @JsonProperty("thumbnail") val thumbnail: String?,
        @JsonProperty("id") val id: Int?,
        @JsonProperty("title") val title: String?,
    )

    data class Key(
        val id: String,
        val version: String,
        val key: String,
    )

}
