package com.likdev256

import android.util.Log
import com.lagradost.cloudstream3.*
//import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
//import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import org.jsoup.nodes.Element
import com.lagradost.nicehttp.NiceResponse

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
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
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

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val linkRegex = Regex("(https?:\\/\\/(www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&\\/\\/=]*mp4))")
        val source = app.get(data).document.select("div.entry iframe").attr("src")
        val script = app.get(source, referer = "$mainUrl/").document.select("body > script").toString()
        //val links = linkRegex.find(script)?.groups?.get(1)?.value.toString()
        val links = linkRegex.findAll(script).map{it.value.trim()}.toList()
        //Log.d("links", links.toString())
            safeApiCall {
                callback.invoke(
                    ExtractorLink(
                        "TamilYogi",
                        "HD",
                        links[0],
                        "$mainUrl/",
                        Qualities.P720.value,
                        false
                    )
                )
                callback.invoke(
                    ExtractorLink(
                        "TamilYogi",
                        "SD",
                        links[1],
                        "$mainUrl/",
                        Qualities.P480.value,
                        false
                    )
                )
                callback.invoke(
                    ExtractorLink(
                        "TamilYogi",
                        "Low",
                        links[2],
                        "$mainUrl/",
                        Qualities.P360.value,
                        false
                    )
                )
            }
        return true
    }
}
