package com.likdev256

//import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import org.jsoup.nodes.Element
import com.lagradost.nicehttp.NiceResponse
import okhttp3.FormBody

class KatMovieProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://katmoviehd.tf"
    override var name = "KatMovie"
    override val hasMainPage = true
    override var lang = "ta"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie
    )

    override val mainPage = mainPageOf(
        "$mainUrl/category/hollywood-eng/" to "Hollywood Movies",
        "$mainUrl/category/netflix/" to "Netflix",
        "$mainUrl/category/disney/" to "Disney",
        "$mainUrl/category/animated/" to "Animated",
        "$mainUrl/category/dubbed-movie/" to "Dubbed Movies"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = if (page == 1) {
            app.get(request.data).document
        } else {
            app.get(request.data + "/page/$page/").document
        }

        //Log.d("Document", request.data)
        val home = document.select("ul.recent-posts > li").mapNotNull {
                if (it.selectFirst("div.post-content > h2 > a")?.text()?.trim().contains(Regex("(?i)EP\\s?[0-9]+|Episode\\s?[0-9]+|Season./d"))) {
                    it.toSearchResult()
                }
            }

        return HomePageResponse(arrayListOf(HomePageList(request.name, home)), hasNext = true)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        //Log.d("Got","got here")
        val title = this.selectFirst("div.post-content > h2 > a")?.text()?.trim() ?: return null
        //Log.d("title", title)
        val href = fixUrl(this.selectFirst("div.post-content > h2 > a")?.attr("href").toString())
        //Log.d("href", href)
        val posterUrl = fixUrlNull(this.selectFirst("div.post-thumb > a > img")?.attr("src"))
        //Log.d("posterUrl", posterUrl.toString())
        //Log.d("QualityN", qualityN)
        val quality = getQualityFromString(this.select("div.poster > div.mepo > span").text().toString())
        //Log.d("Quality", quality.toString())
        return newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                this.quality = quality
            }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        //Log.d("document", document.toString())

        return document.select("ul.recent-posts > li").mapNotNull {
            if (it.selectFirst("div.post-content > h2 > a")?.text()?.trim().contains(Regex("(?i)EP\\s?[0-9]+|Episode\\s?[0-9]+|Season./d"))) {
                it.toSearchResult()
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        //Log.d("Doc", doc.toString())
        val title = doc.select("div > ul:nth-child(1) > li:nth-child(1)")?.text()?.toString()?.trim()
            ?: return null
        //Log.d("title", title)
        val poster = fixUrlNull(doc.selectFirst("img.aligncenter")?.attr("src"))
        val bgposter = doc.selectFirst("div.entry-content > p > a > img").map {
            it.attr("src")
        }.random()
        //Log.d("bgposter", bgposter.toString())
        //Log.d("poster", poster.toString())
        val tags = doc.select("div > ul:nth-child(1) > li:nth-child(1) > ul:nth-child(1) > li:nth-child(7) > a").map { it.text() }
        val year =
            doc.selectFirst("span.date")?.text()?.toString()?.substringAfter(",")?.trim()?.toInt()
        //Log.d("year", year.toString())
        val description = doc.selectFirst("div > ul:nth-child(1) > li:nth-child(1) > ul:nth-child(1) > li:nth-child(4)")?.text()?.trim()
        //Log.d("desc", description.toString())
        val rating = doc.select("div > ul:nth-child(1) > li:nth-child(2) > a:nth-child(2)").text().substringBefore("/").toRatingInt()
        //Log.d("rating", rating.toString())
        val duration =
            doc.selectFirst("span.runtime")?.text()?.toString()?.removeSuffix(" Min.")?.trim()
                ?.toInt()
        //Log.d("dur", duration.toString())
        val actors =
            doc.select("div > ul:nth-child(1) > li:nth-child(1) > ul:nth-child(1) > li:nth-child(6)").split(", ").map {
                ActorData(
                    Actor(
                        it
                    )
                )
            }
        val recommendations = doc.select("#dtw_content_related-2 article").mapNotNull {
            it.toSearchResult()
        }
        val embedLink = doc.select("h4 > iframe").attr("src")

        return newMovieLoadResponse(title, url, TvType.Movie, "$url,$embedLink") {
                this.posterUrl = poster?.trim()
                this.backgroundPosterUrl = bgposter?.trim()
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                this.duration = duration
                this.actors = actors
                this.recommendations = recommendations
            }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val link = data.substringAfter(",")
        //Log.d("embedlink", link)

        val doc = app.get(link).document
        Log.d("mygoddoc", doc.toString())

        return true
    }
}
