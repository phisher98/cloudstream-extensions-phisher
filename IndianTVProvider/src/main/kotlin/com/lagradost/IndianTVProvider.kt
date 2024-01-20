package com.likdev256

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.MainAPI
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.SearchResponse
import com.lagradost.cloudstream3.utils.newMovieSearchResponse
import com.lagradost.cloudstream3.utils.fixUrl
import com.lagradost.cloudstream3.utils.safeApiCall
import org.jsoup.nodes.Element

class LiveSportsClubProvider : MainAPI() {
    override var mainUrl = "https://livesportsclub.me"
    override var name = "LiveSportsClub"
    override val hasMainPage = false
    override var lang = "en"
    override val hasDownloadSupport = false
    override val supportedTypes = setOf(TvType.Live)

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/hls/$query/"
        val response = app.get(url).body
        val soup = response?.toJsoup()

        return soup?.select("div.box1")?.mapNotNull {
            it.toSearchResult()
        } ?: emptyList()
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.selectFirst("h2.text-center.text-sm.font-bold")?.text()?.trim()
        val linkElement = this.selectFirst("a[target=_blank]")
        val link = linkElement?.attr("href")
        val posterLink = fixUrl(this.selectFirst("img")?.attr("src"))

        return newMovieSearchResponse(title, LiveStreamLinks(title, posterLink, link).toJson(), TvType.Live) {
            this.posterUrl = posterLink
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        safeApiCall {
            // Implement link extraction logic here
        }

        return true
    }

    data class LiveStreamLinks(
        val title: String?,
        val poster: String?,
        val link: String?
    )

    // Other functions can be implemented based on your requirements
}
