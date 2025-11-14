package com.likdev256


import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import org.jsoup.nodes.Element

class MassTamilanProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://masstamilan.dev"
    override var name = "MassTamilan"
    override val hasMainPage = true
    override var lang = "ta"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Music,TvType.Movie
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
        val document = app.get("${request.data}?page=$page").documentLarge
        Log.d("Phisher","${request.data}?page=$page")
        val home = document.select("div.a-i").mapNotNull {
                it.toSearchResult()
            }
        return newHomePageResponse(arrayListOf(HomePageList(request.name, home)), hasNext = true)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("div h2")?.text()?.trim() ?: return null
        val href = fixUrl(mainUrl + this.select("a").attr("href"))
        val posterUrl = fixUrlNull(this.selectFirst("a picture img")?.attr("src"))
        return newTvSeriesSearchResponse(title, href+",,"+title, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
    }

    // Search is disabled bcz the provider doesn't support native search the current search is powered by google
    // which is garbaja and im too lazy to work on that PR if you can

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/search?keyword=$query").documentLarge
        //Log.d("document", document.toString())

        return document.select("div.a-i").mapNotNull {
            it.toSearchResult()
        }
    }

    data class MassTamilanLinks (
        @JsonProperty("sourceName") val sourceName: String,
        @JsonProperty("sourceLink") val sourceLink: String
    )

    override suspend fun load(url: String): LoadResponse {
        val docLink = url.substringBefore(",,")
        val doc = app.get(docLink).documentLarge
        //Log.d("Doc", doc.toString())
        val title = url.substringAfter(",,")
        //Log.d("title", title)
        val poster = fixUrlNull(mainUrl + doc.selectFirst("figure.ib > picture > img")?.attr("src"))
        //Log.d("poster", poster.toString())
        val description = doc.select("#movie-handle").text()
        var tags = listOf<String>()
        var year: Int? = 0
        var actors = listOf<ActorData>()
        doc.select("#movie-handle b + a").map { me ->
            tags = me.select("a[href~=-songs]").map { it.text() }
            year = me.select("a[href~=year]").text().trim().toIntOrNull()
            if (!me.select("a[href~=artist]").isEmpty()) {
                actors = me.select("a[href~=artist]").map {
                    ActorData(
                        Actor(
                            it.text()
                        ),
                        roleString = "Artist",
                    )
                }
            }
            if (!me.select("a[href~=music]").isEmpty()) {
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
                newEpisode(links.toJson())
                {
                    this.name=me.select("td > span > h2 > span[itemprop~=name] > a").text()
                    this.season=1
                    this.episode=me.select("td > span[itemprop~=position]").text().toInt()
                    this.posterUrl=poster
                    this.description=epPlot
                }
            )
        }
        val zipLinks = doc.select("h2.ziparea > a.dlink").map {
            MassTamilanLinks(
                it.text(),
                mainUrl + it.attr("href")
            )
        }

        episodes.add(
            newEpisode(zipLinks.toJson())
            {
                this.name="Full Zip"
                this.season=1
                this.episode=episodes.count()+1
                this.posterUrl="https://miro.medium.com/v2/resize:fit:720/format:webp/1*nCwjG9N0CkYXOkznDB7kSw.png"
                this.description= "Zip/Rar links"
            }
        )

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
                    newExtractorLink(
                        it.sourceName,
                        it.sourceName,
                        url = "https://goodproxy.goodproxy.workers.dev/fetch?url=${it.sourceLink}",
                        INFER_TYPE
                    ) {
                        this.referer = "$mainUrl/"
                        this.quality = Qualities.Unknown.value
                    }
                )
            }
        }


        return true
    }
}
