package com.IndianTV

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.google.gson.reflect.TypeToken
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.google.gson.Gson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson

class IndianTVPlugin : MainAPI() {
    override var mainUrl = "https://gist.githubusercontent.com/mitthu786/6719418610aa81997e72d6c27a6a2e39/raw/tsjiotvnew.json"
    override var name = "Indian TV"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.Live)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val json = app.get(mainUrl).text
        val parsedData: List<Root2> = parseJsonWithGson(json)
        val pagesByGenre = parsedData.groupBy { it.genre }

        val homePageLists = pagesByGenre.map { (genre, items) ->
            val results = items.map { it.toSearchResult() }
            HomePageList(
                name = genre,
                list = results,
                isHorizontalImages = true
            )
        }

        return newHomePageResponse(
            list = homePageLists,
            hasNext = false
        )
    }

    private fun parseJsonWithGson(json: String): List<Root2> {
        return try {
            val gson = Gson()
            val type = object : TypeToken<List<Root2>>() {}.type
            gson.fromJson(json, type)
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun Root2.toSearchResult(): SearchResponse {
        val posterUrl=this.img
        return newMovieSearchResponse(name = this.name,LoadUrl(this.id, posterUrl,this.name).toJson(),type = TvType.Live)
        {
            this.posterUrl=posterUrl
        }
    }

    data class LoadUrl( val id: String, val posterUrl: String?,val name: String?)

    override suspend fun search(query: String): List<SearchResponse> {
        val json = app.get(mainUrl).text
        val parsedData: List<Root2> = parseJsonWithGson(json)
        val filteredResults = parsedData.filter {
            it.name.contains(query, ignoreCase = true) ||
                    it.genre.contains(query, ignoreCase = true)
        }
        return filteredResults.map { it.toSearchResult() }
    }

    override suspend fun load(url: String): LoadResponse {
            val data = tryParseJson<LoadUrl>(url)
            val id=data?.id
            val posterUrl=data?.posterUrl
            val title =data?.name ?: "Unknown"
            val description = "Live TV"
            val href="https://amit.allinonereborn.in/jiotv/app/ts_live_$id.m3u8"
            return newMovieLoadResponse(title, href, TvType.Live, href) {
                this.posterUrl = posterUrl
                this.plot = description
            }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val headers= mapOf("Connection" to "keep-alive","User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:134.0) Gecko/20100101 Firefox/134.0")
        callback.invoke(
            ExtractorLink(
                name,
                name,
                data,
                "",
                Qualities.P1080.value,
                true,
                headers
            )
        )
        return true
    }
}
