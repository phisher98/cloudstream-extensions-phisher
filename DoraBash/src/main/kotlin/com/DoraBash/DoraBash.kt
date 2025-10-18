package com.DoraBash

import com.lagradost.api.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class DoraBash : MainAPI() {
    override var mainUrl = "https://dorabash.com"
    override var name = "DoraBash"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.Anime, TvType.Cartoon)

    override val mainPage = mainPageOf(
        "anime?status=&type=&order=update" to "Latest",
        "tag/hindi-dubbed-episodes/page" to "Seasons",
        "tag/movies/page" to "Movies",
        "anime/?status=&type=tv&sub=sub&page=" to "Special",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}/$page").document
        val home = document.select("div.listupd > article").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.select("div.bsx > a").attr("title")
        val href = fixUrl(this.select("div.bsx > a").attr("href"))
        val posterUrl = fixUrlNull(this.select("div.bsx > a img").attr("src"))
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }


    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..3) {
            val document = app.get("${mainUrl}/page/$i/?s=$query").document

            val results =
                document.select("div.listupd > article").mapNotNull { it.toSearchResult() }

            if (!searchResponse.containsAll(results)) {
                searchResponse.addAll(results)
            } else {
                break
            }

            if (results.isEmpty()) break
        }

        return searchResponse
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("h1.entry-title")?.text()?.trim().toString()
        val href = document.selectFirst(".eplister li > a")?.attr("href") ?: ""
        var poster = document.select("div.ime > img").attr("src")
        val description = document.selectFirst("div.entry-content")?.text()?.trim()
        val type = document.selectFirst(".spe")?.text().toString()
        val tvtag = if (type.contains("Movie")) TvType.Movie else TvType.TvSeries
        return if (tvtag == TvType.TvSeries) {
            val Eppage = document.selectFirst(".eplister li > a")?.attr("href") ?: ""
            val doc = app.get(Eppage).document
            @Suppress("NAME_SHADOWING") val episodes =
                doc.select("div.episodelist > ul > li").map { info ->
                    val href = info.select("a").attr("href")
                    val Rawepisode=info.select("a span").text().substringAfter("Episode").substringBeforeLast("-").trim()
                    val episode ="Episode $Rawepisode"
                    val poster = info.selectFirst("a img")?.attr("src") ?: ""
                    newEpisode(href)
                    {
                        this.episode=Rawepisode.toIntOrNull()
                        this.name=episode
                        this.posterUrl=poster
                    }
                }
            if (poster.isEmpty()) {
                poster = document.selectFirst("meta[property=og:image]")?.attr("content")?.trim()
                    .toString()
            }
            newTvSeriesLoadResponse(title, url, TvType.Anime, episodes.reversed()) {
                this.posterUrl = poster
                this.plot = description
            }
        } else {
            if (poster.isEmpty()) {
                poster = document.selectFirst("meta[property=og:image]")?.attr("content")?.trim()
                    .toString()
            }
            newMovieLoadResponse(title, url, TvType.Movie, href) {
                this.posterUrl = poster
                this.plot = description
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        document.select("div.player-embed iframe").forEach {
            val href = it.attr("src")
            Log.d("Phisher",href)
            if (href.contains("gdplaydora.blogspot.com"))
            {
                val truelink=app.get(href).document.selectFirst("#container source")?.attr("src")
                if (truelink != null) {
                    callback.invoke(
                        newExtractorLink(
                            this.name,
                            this.name,
                            url = truelink,
                            INFER_TYPE
                        ) {
                            this.referer = ""
                            this.quality = Qualities.Unknown.value
                        }
                    )
                }
            }
            else {
                val truelink = gettrueurl(href)
                if (truelink != null) {
                    M3u8Helper.generateM3u8(
                        this.name,
                        truelink,
                        "",
                    ).forEach(callback)
                }
            }
        }
        return true
    }
    private suspend fun gettrueurl(href: String): String? {
        val filemoon=app.get(href).document.selectFirst("iframe")?.attr("src") ?:""
        val Filedata= app.get(filemoon, headers = mapOf("Accept-Language" to "en-US,en;q=0.5","sec-fetch-dest" to "iframe")).document.selectFirst("script:containsData(function(p,a,c,k,e,d))")?.data().toString()
        val Fileurl=JsUnpacker(Filedata).unpack()?.let { unPacked ->
            Regex("sources:\\[\\{file:\"(.*?)\"").find(unPacked)?.groupValues?.get(1)
        }
        return Fileurl
    }

}

