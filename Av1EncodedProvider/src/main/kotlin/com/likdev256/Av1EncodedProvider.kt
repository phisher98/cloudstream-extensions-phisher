package com.likdev256

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.*
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import org.jsoup.nodes.Element

class Av1EncodedProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://www.av1encoded.in"
    override var name = "Av1Encoded"
    override val hasMainPage = true
    override var lang = "ta"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie
    )

    override val mainPage = mainPageOf(
        "$mainUrl/?order=years-desc" to "Recent Movies",
        "$mainUrl/?order=rating" to "TopRated Movies",
        "$mainUrl/?order=title-asc" to "Movies"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = if (page == 1) {
            app.get(request.data).document
        } else {
            app.get(request.data.removeSuffix(request.data.format(page).removePrefix(mainUrl))
                    + "/page/" + page + request.data.format(page).removePrefix(mainUrl) ).document
        }
        //Log.d("request", request.data.removeSuffix(request.data.format(page).removePrefix(mainUrl))
        //        + "/page/" + page + request.data.format(page).removePrefix(mainUrl))
        //Log.d("CSS element", document.select("div.item-container > div").toString())
        val home = document.select("div.item-container > div").mapNotNull {
            it.toSearchResult()
        }
        return HomePageResponse(arrayListOf(HomePageList(request.name, home)), hasNext = true)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("h2.movie-title")?.toString()?.removePrefix("<h2 class=\"movie-title\">")?.removeSuffix("</h2>")?.trim() ?: return null
        //Log.d("title", titleS)
        val href = fixUrl(this.selectFirst("a")?.attr("href").toString())
        //Log.d("href", href)
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("data-src"))
        //Log.d("posterUrl", posterUrl.toString())
        //Log.d("QualityN", qualityN)
        val quality = SearchQuality.HD
        //Log.d("Quality", quality.toString())
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.quality = quality
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        //Log.d("document", document.toString())

        return document.select("div.item-container > div").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        //Log.d("Doc", doc.toString())
        val title = doc.selectFirst("h1.entry-title")?.text()?.toString()?.trim() ?: return null
        //val titleRegex = Regex("(^.*\\)\\d*)")
        //val title = titleRegex.find(titleL)?.groups?.get(1)?.value.toString()
        //Log.d("titleL", titleL)
        //Log.d("title", title)
        val poster = fixUrlNull(doc.selectFirst("img")?.attr("data-lazy-src"))
        //Log.d("poster", poster.toString())
        val tags = doc.select("div.details > span[itemprop*=\"genre\"] > a").map { it.text() }
        val year = doc.selectFirst("div.details > span > a")?.text()?.toString()?.toInt()
        //Log.d("year", year.toString())
        val description = doc.selectFirst("p.movie-description span.trama")?.text()?.trim()
        //Log.d("desc", description.toString())
        val trailer = fixUrlNull(doc.select("#trailer > a").attr("href"))
        //Log.d("trailer", trailer.toString())
        val rating = doc.select("span.progress-value").text().toRatingInt()
        //Log.d("rating", rating.toString())
        val duration = doc.selectFirst("div.details > span[itemprop*=\"duration\"]")?.text()?.toString()?.removeSuffix("min")?.trim()?.toInt()
        //Log.d("dur", duration.toString())
        val actors = doc.select("div.person > div.data > div.name > a").map { it.text() }
        val recommendations = doc.select("div.item-container > div").mapNotNull {
            it.toSearchResult()
        }
        val link = doc.select("#videoplayer").attr("href")
        Log.d("mygodcheck", link)
        return newMovieLoadResponse(title, url, TvType.Movie, link) {
                this.posterUrl = poster
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                this.duration = duration
                addActors(actors)
                this.recommendations = recommendations
                addTrailer(trailer)
            }
       // }
    }

    data class getMp4 (
        @JsonProperty("playerId"                  ) var playerId                  : String?,
        @JsonProperty("width"                     ) var width                     : String?,
        @JsonProperty("height"                    ) var height                    : String?,
        @JsonProperty("notifyEnabled"             ) var notifyEnabled             : Boolean?,
        @JsonProperty("url"                       ) var url                       : String,
        @JsonProperty("url11"                     ) var url11                     : String?,
        @JsonProperty("html5url"                  ) var html5url                  : String?,
        @JsonProperty("minFlashVersionNewPlayer"  ) var minFlashVersionNewPlayer  : String?,
        @JsonProperty("wmode"                     ) var wmode                     : String?,
        @JsonProperty("asa"                       ) var asa                       : Boolean?,
        @JsonProperty("provider"                  ) var provider                  : String?,
        @JsonProperty("flashvars"                 ) var flashvars                 : String?,
        @JsonProperty("liveRertyTimeout"          ) var liveRertyTimeout          : Int?   ,
        @JsonProperty("poster"                    ) var poster                    : String?,
        @JsonProperty("isExternalPlayer"          ) var isExternalPlayer          : Boolean?,
        @JsonProperty("isIframePlayer"            ) var isIframePlayer            : Boolean?,
        @JsonProperty("isHtml5Player"             ) var isHtml5Player             : Boolean?,
        @JsonProperty("timestamp"                 ) var timestamp                 : String?,
        @JsonProperty("stubEnabled"               ) var stubEnabled               : Boolean?,
        @JsonProperty("verifyInline"              ) var verifyInline              : Boolean?,
        @JsonProperty("webrtcBrokenH264"          ) var webrtcBrokenH264          : Boolean?,
        @JsonProperty("playerLocalizationEnabled" ) var playerLocalizationEnabled : Boolean?,

    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        Log.d("mygoddata", data)
        val data1 = "https://www.ok.ru/videoembed/5333288946406"
        val linkJson = app.get(data1.trim(), referer = "$mainUrl/").document.select("div.vid-card_cnt").attr("data-options")
        val link = tryParseJson<getMp4>(linkJson.toString())?.url
        Log.d("mygodjson", linkJson.toString())
        Log.d("mygodlink", link.toString())

        safeApiCall {
            callback.invoke(
                ExtractorLink(
                    "Av1Encoded",
                    "Av1Encoded",
                    link.toString(),
                    "https://ok.ru/",
                    Qualities.Unknown.value,
                    false
                )
            )
        }

        return true
    }
}
