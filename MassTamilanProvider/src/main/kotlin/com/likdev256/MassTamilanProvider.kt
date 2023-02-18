package com.likdev256

//import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import org.jsoup.nodes.Element

class MovieHUBProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://masstamilan.dev"
    override var name = "MassTamilan"
    override val hasMainPage = true
    override var lang = "ta"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.TvSeries
    )

    override val mainPage = mainPageOf(
        "$mainUrl/latest-updates" to "Latest Updates",
        "$mainUrl/tamil-songs" to "Tamil Songs",
        "$mainUrl/telugu-songs" to "Telugu Songs",
        "$mainUrl/malayalam-songs" to "Malayalam Songs",
        "$mainUrl/hindi-songs" to "Hindi Songs"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = if (page == 1) {
            app.get(request.data).document
        } else {
            app.get(request.data + "?page=$page").document
        }

        //Log.d("Document", request.data)
        val home = document.select("div.botlist > div.a-i").mapNotNull {
                it.toSearchResult()
            }

        return HomePageResponse(arrayListOf(HomePageList(request.name, home)), hasNext = true)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        //Log.d("Got","got here")
        val title = this.selectFirst("div.info > h2")?.text()?.toString()?.trim() ?: return null
        //Log.d("title", title)
        val href = fixUrl(mainUrl + this.select("a").attr("href"))
        //Log.d("href", href)
        val posterUrl = fixUrlNull(this.selectFirst("div.ava > picture > img")?.attr("src"))
        //Log.d("posterUrl", posterUrl.toString())
        return newTvSeriesSearchResponse(title, href+",,"+title, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
    }

    // Search is disabled bcz the provider doesn't support native search the current search is powered by google
    // which is garbaja and im too lazy to work on that PR if you can

    /*override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        //Log.d("document", document.toString())

        return document.select("div.result-item").mapNotNull {
            val title = it.selectFirst("article > div.details > div.title > a")?.text().toString().trim()
            //Log.d("title", titleS)
            val href = fixUrl(it.selectFirst("article > div.details > div.title > a")?.attr("href").toString())
            //Log.d("href", href)
            val posterUrl = fixUrlNull(it.selectFirst("article > div.image > div.thumbnail > a > img")?.attr("src"))
            //Log.d("posterUrl", posterUrl.toString())
            //Log.d("QualityN", qualityN)
            val quality = getQualityFromString(it.select("div.poster > div.mepo > span").text().toString())
            //Log.d("Quality", quality.toString())
            val type = it.select("article > div.image > div.thumbnail > a > span").text().toString()
            if (type.contains("Movie")) {
                newMovieSearchResponse(title, href, TvType.Movie) {
                    this.posterUrl = posterUrl
                    this.quality = quality
                }
            } else {
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                    this.posterUrl = posterUrl
                    this.quality = quality
                }
            }
        }
    }*/

    data class MassTamilanLinks (
        @JsonProperty("sourceName") val sourceName: String,
        @JsonProperty("sourceLink") val sourceLink: String
    )

    override suspend fun load(url: String): LoadResponse? {
        val docLink = url.substringBefore(",,")
        val doc = app.get(docLink).document
        //Log.d("Doc", doc.toString())
        val title = url.substringAfter(",,")
        //Log.d("title", title)
        val poster = fixUrlNull(mainUrl + doc.selectFirst("#movie-image > figure > picture > img")?.attr("src"))
        //Log.d("poster", poster.toString())
        val description = doc.select("#movie-handle").text()
        var tags = listOf<String>()
        var year: Int? = 0
        var actors = listOf<ActorData>()
        doc.select("#movie-handle b + a").map { me ->
            tags = me.select("a[href~=-songs]").map { it.text() }
            year = me.select("a[href~=year]").text().trim().toIntOrNull()
            //Log.d("mybadyear1", me.select("b, a").toString())
            //Log.d("mybadyear2", me.select("b + a").toString())
            //Log.d("mybadyear3", me.text())
            if (!me.select("a[href~=artist]").isNullOrEmpty()) {
                actors = me.select("a[href~=artist]").map {
                    ActorData(
                        Actor(
                            it.text()
                        ),
                        roleString = "Artist",
                    )
                }
            }
            if (!me.select("a[href~=music]").isNullOrEmpty()) {
                actors = me.select("a[href~=music]").map {
                    ActorData(
                        Actor(
                            it.text()
                        ),
                        roleString = "Music",
                    )
                }
            }
        }
        //Log.d("mybadinfo", info.toString())

        val episodes = ArrayList<Episode>()
        doc.select("#tlist > tbody > tr[itemprop]").map { me ->
            val links = me.select("td > a").map {
                MassTamilanLinks(
                    it.text(),
                    mainUrl + it.attr("href")
                )
            }
            val epPlot = "Singers: ${me.select("td > span[itemprop~=item] > span[itemprop~=byArtist]").text()} && \n" +
                    "Duration: ${me.select("td > span[itemprop~=item] > span[itemprop~=duration]").text()} && \n" +
                    "Downloads: ${me.select("td > span[itemprop~=item] > span[class~=dl-count]").text()}\n"

            episodes.add(
                Episode(
                    data = links.toJson(),
                    name = me.select("td > span > h2 > span[itemprop~=name] > a").text(),
                    season = 1,
                    episode = me.select("td > span[itemprop~=position]").text().toInt(),
                    posterUrl = poster,
                    description = epPlot
                )
            )
        }

        return newTvSeriesLoadResponse(title, docLink, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                this.tags = tags
                this.actors = actors
                this.plot = description
            }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        parseJson<ArrayList<MassTamilanLinks>>(data).map {
            //val mp3Stream = app.get(data, allowRedirects = true)
            safeApiCall {
                callback.invoke(
                    ExtractorLink(
                        it.sourceName,
                        it.sourceName,
                        it.sourceLink,
                        "$mainUrl/",
                        Qualities.Unknown.value
                    )
                )
            }
        }


        return true
    }
}
