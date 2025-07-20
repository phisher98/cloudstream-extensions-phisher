package com.PagalWorld

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class PagalWorld : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://pagalnew.com"
    override var name = "PagalWorld"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Music,TvType.Movie
    )

    override val mainPage = mainPageOf(
        "$mainUrl/recentsongs" to "Latest Songs",
        "$mainUrl/category/bollywood-tracks" to "Bollywood Songs",
        "$mainUrl/category/punjabi-mp3-tracks" to "Punjabi Songs",
        "$mainUrl/category/haryanvi-mp3-tracks" to "Haryanvi Songs",
        "$mainUrl/category/bhojpuri-mp3-tracks" to "Bhojpuri Songs",
        "$mainUrl/category/english-mp3-tracks" to "English Songs",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get("${request.data}/$page").document
        val home = document.select("div.main_page_category_music").mapNotNull {
                it.toSearchResult()
            }

        return newHomePageResponse(arrayListOf(HomePageList(request.name, home)), hasNext = true)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.selectFirst("div.main_page_category_music_txt div")?.text()?.trim() ?: return null
        val href = fixUrl(this.select("a").attr("href"))
        var posterUrl = this.selectFirst("a img")?.attr("src")?.replace("covericons","coverimages")?.replace("80","500")?.replace("png","jpg")
        if (posterUrl!!.startsWith("/images/") || posterUrl.startsWith("../"))
        {
            posterUrl=mainUrl+posterUrl.substringAfter("..")
        }
        return newTvSeriesSearchResponse(title, href+",,"+title, TvType.TvSeries) {
                this.posterUrl = posterUrl
            }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/search.php?find=$query", timeout = 50000).document
        return document.select("a").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val docLink = url.substringBefore(",,")
        val doc = app.get(docLink).document
        val title = url.substringAfter(",,")
        var poster = doc.selectFirst("#main_page_middle img.b-lazy")?.attr("data-src")
        if (poster!!.startsWith("../"))
        {
            poster=mainUrl+poster.substringAfter("..")
        }
        val description = doc.select("#movie-handle").text()
        var tags = listOf<String>()
        var actors = listOf<ActorData>()
        doc.select("#movie-handle b + a").map { me ->
            tags = me.select("a[href~=-songs]").map { it.text() }
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
        var index = 1
        val episodes = mutableListOf<Episode>()
        if (url.contains("album"))
        {
            doc.select("div.main_page_category_music").map { me ->
                val name = me.select("a").text().substringBefore("-")
                val Description=me.select("div:nth-child(2)").text().substringAfter("-").substringAfter("-")
                val links = me.select("a").attr("href")
                var songposter= me.select("img").attr("data-src")
                if (songposter.startsWith("/"))
                {
                    songposter= poster.toString()
                }
                episodes.add(
                    newEpisode(links)
                    {
                        this.name=name
                        this.season=1
                        this.episode=index
                        this.posterUrl=songposter
                        this.description=Description
                    }
                )
                index++
            }
        }
        else
        {
            episodes.add(
                newEpisode(url.substringBefore(",,"))
                {
                    this.name=title
                    this.season=1
                    this.episode=1
                    this.posterUrl=poster
                }
            )
        }


        return newTvSeriesLoadResponse(title, docLink, TvType.TvSeries, episodes) {
                this.posterUrl = poster.toString()
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
        app.get(data).document.select("div.downloaddiv a").map {
            var href=it.attr("href")
            val quality=it.text().substringBefore("Song")
            if (href.startsWith("/"))
            {
                href=fixUrl(href)
            }
            callback.invoke(
                newExtractorLink(
                    "$name $quality",
                    "$name $quality",
                    url = "https://goodproxy.goodproxy.workers.dev/fetch?url=$href",
                    INFER_TYPE
                ) {
                    this.referer = "$mainUrl/"
                    this.quality = Qualities.Unknown.value
                }
            )
        }
        return true
    }
}
