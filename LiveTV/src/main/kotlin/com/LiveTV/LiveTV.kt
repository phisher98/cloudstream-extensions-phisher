package com.LiveTV

import com.fasterxml.jackson.annotation.JsonProperty
import com.google.gson.Gson
import com.lagradost.api.Log
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newLiveSearchResponse
import com.lagradost.cloudstream3.newLiveStreamLoadResponse
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.URLEncoder
import java.nio.charset.StandardCharsets


class LiveTV : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://mediafusion.elfhosted.com"
    override var name = "LiveTV"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Live
    )

    override val mainPage = mainPageOf(
        "$mainUrl/catalog/tv/live_tv/skip=" to "Live TV",
        "$mainUrl/catalog/events/live_sport_events/skip=" to "Events Live",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get("${request.data}$page.json").parsedSafe<ResponseJSON>()
        val home = document?.metas?.map {
            it.toSearchResult()
        }.orEmpty()
        return newHomePageResponse(request.name, home)
    }

    private fun Meta.toSearchResult(): SearchResponse {
        val type = if (this.type.equals("tv", ignoreCase = true)) TvType.TvSeries else TvType.Live
        return newLiveSearchResponse(
            this.name,
            LoadRES(this.id, this.poster,this.name).toJson(),
            type
        ) {
            this.posterUrl = this@toSearchResult.poster
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val encodedQuery = withContext(Dispatchers.IO) {
            URLEncoder.encode(query, StandardCharsets.UTF_8.toString())
        }
        val document = app.get("$mainUrl/catalog/tv/mediafusion_search_tv/search=$encodedQuery.json").parsedSafe<ResponseJSON>()
        return document?.metas?.map {
            it.toSearchResult()
        }.orEmpty()
    }


    override suspend fun load(url: String): LoadResponse {
        val meta = parseMetaJson(url)
        val href="https://mediafusion.elfhosted.com/stream/tv/${meta.id}.json"
        val title = meta.name
        val poster = meta.background ?: meta.poster
        val tags =meta.genres
        val description = "Live TV Channels"
        Log.d("Phisher",url)
        Log.d("Phisher",href)

        return newLiveStreamLoadResponse(title, url, href)
        {
            this.posterUrl=poster
            this.tags = tags
            this.plot = description
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        app.get(data).parsedSafe<LoadlinksJson>()?.streams?.forEach { stream ->
            val request = stream.behaviorHints?.proxyHeaders?.request
            val headers = mapOf(
                "referer" to request?.referer.orEmpty(),
                "Origin" to request?.origin.orEmpty(),
                "User-Agent" to request?.userAgent.orEmpty()
            )

            callback.invoke(
                newExtractorLink(
                    stream.description.orEmpty(),
                    stream.description.orEmpty(),
                    stream.url.orEmpty(),
                    INFER_TYPE
                ) {
                    this.quality = Qualities.Unknown.value
                    this.referer = request?.referer.orEmpty()
                    this.headers = headers
                }
            )
        }

        return true
    }


    data class ResponseJSON(
        val metas: List<Meta> = emptyList()
    )

    data class Meta(
        val id: String,
        val name: String = "",
        val type: String,
        val poster: String,
        val background: String? = null,
        val description: String? = null,
        val genres: List<String> = emptyList(),
        val country: String? = null,
        val language: String? = null,
        val logo: String? = null
    )

    data class LoadlinksJson(
        val streams: List<Stream> = emptyList()
    )

    data class Stream(
        val name: String? = null,
        val description: String? = null,
        val url: String? = null,
        val behaviorHints: BehaviorHints? = null
    )

    data class BehaviorHints(
        val notWebReady: Boolean? = null,
        val proxyHeaders: ProxyHeaders? = null
    )

    data class ProxyHeaders(
        val request: Request? = null,
        val response: Response? = null
    )

    data class Request(
        @JsonProperty("Referer")
        val referer: String? = null,
        @JsonProperty("Origin")
        val origin: String? = null,
        @JsonProperty("User-Agent")
        val userAgent: String? = null
    )

    data class Response(
        @JsonProperty("Content-Type")
        val contentType: String? = null
    )


    data class LoadRES(val id: String, val poster: String,val name:String)

    private fun parseMetaJson(jsonString: String): Meta {
        return Gson().fromJson(jsonString, Meta::class.java)
    }

}
