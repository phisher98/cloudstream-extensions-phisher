package com.Pinoymoviepedia

import com.lagradost.cloudstream3.mainPageOf
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixUrlNull
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class Bluray : Pinoymoviepedia() {

    override var mainUrl = "https://bluray7.com"
    override var name = "Bluray7"
    override val mainPage = mainPageOf(
        "trending" to "Trending",
        "movies" to "Movies",
        "genre/action" to "Action",
        "genre/comedy" to "Comedy",
        "genre/drama" to "Drama",
        "genre/romance" to "Romance",
        "genre/thriller" to "Thriller",
        "genre/adventure" to "Adventure",
        "genre/horror" to "Horror",
        "genre/war" to "War",
        "genre/science-fiction" to "Science Fiction"
    )
    override suspend fun load(url: String): LoadResponse {
        val request = app.get(url)
        val document = request.documentLarge
        //val directUrl = getBaseUrl(request.url)
        val title =
            document.selectFirst("div.data > h1")?.text()?.trim().toString()
        var posterUrl = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content"))
        if (posterUrl.isNullOrEmpty()) {
            posterUrl = fixUrlNull(document.select("div.poster img").attr("src"))
        }
        val description = document.select("div.wp-content > p").text().trim()
        val episodes =
            document.select("ul#playeroptionsul > li").map {
                val name = it.selectFirst("span.title")?.text()
                val type = it.attr("data-type")
                val post = it.attr("data-post")
                val nume = it.attr("data-nume")
                newEpisode(LinkData(type, post,nume).toJson())
                {
                    this.name=name
                }
            }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
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
        val loadData = tryParseJson<LinkData>(data)
        Log.d("Phisher", loadData.toString())
        val source = app.post(
            url = "$mainUrl/wp-admin/admin-ajax.php", data = mapOf(
                "action" to "doo_player_ajax", "post" to "${loadData?.post}", "nume" to "${loadData?.nume}", "type" to "movie"
            ), headers = mapOf("Accept" to "*/*", "X-Requested-With" to "XMLHttpRequest"
            )).parsed<ResponseHash>().embed_url
        if (!source.contains("youtube")) loadExtractor(
            source,
            "",
            subtitleCallback,
            callback
        )
        return true
    }

    data class LinkData(
        val type: String? = null,
        val post: String? = null,
        val nume: String? = null,
    )

    data class ResponseHash(
        @JsonProperty("embed_url") val embed_url: String,
        @JsonProperty("type") val type: String?,
    )
}