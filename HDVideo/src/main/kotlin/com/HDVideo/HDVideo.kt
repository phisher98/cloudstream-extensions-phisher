package com.HDVideo


import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class HDVideo : MainAPI() {
    override var mainUrl = "https://hdvideo9.com"
    override var name = "HDVideo9"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Music,TvType.Others)

    companion object
    {
        val headers= mapOf("User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64; rv:126.0) Gecko/20100101 Firefox/126.0","Cookie" to "cf_clearance=B77Qm0xSVkfxZVWLpof9Ikt_LrPrUmtUjotTp_mODmc-1716808128-1.0.1.1-X5MP6jVxgrx4yJI81UbB2sucusQhlVjMbDWOW3sLrKJ0Ot21l8pqc7OAx77_pQ5WI2WModSfn4KBwrc.D41Qag")
    }

    override val mainPage = mainPageOf(
        "category/bollywood-movie-video-songs.html" to "Bollywood Movies",
        "category/a-to-z-bollywood-movie-video-songs.html" to "A-Z Bollywood",
        "videos/punjabi-video-songs.html" to "Punjabi",
        "videos/new-haryanvi-video-songs.html" to "Haryanvi",
        "videos/new-bhojpuri-video-songs.html" to "Bhojpuri",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}", referer = mainUrl, headers = headers).document
        val home = document.select("div.catList .catRow,div.fl").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = false
            ),
            hasNext = false
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.select("a div:nth-child(2),a div").text()
        val href = fixUrl(this.select("a").attr("href"))
        var posterUrl = fixUrlNull(this.select("a img").attr("src"))
        if(posterUrl.isNullOrEmpty())
        {
            posterUrl = "https://static.vecteezy.com/system/resources/thumbnails/031/097/002/small_2x/background-of-headset-with-neon-style-available-for-intro-and-opening-or-closing-free-video.jpg"
        }
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    private fun Element.toSearch(): SearchResponse {
        val title = this.select("article  > header > h2").text().trim().replace("Watch Online", "")
        val href = fixUrl(this.select("article  > a").attr("href"))
        val posterUrlRaw =
            this.select("article  > div.post-thumbnail > figure > img").attr("src").toString()
        return if (posterUrlRaw.contains("http")) {
            val posterUrl = posterUrlRaw
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        } else {
            val posterUrl = "https:$posterUrlRaw"
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..3) {
            val document = app.get("${mainUrl}/page/$i/?s=$query",referer = mainUrl, headers = headers).document

            val results = document.select("#movies-a > ul > li").mapNotNull { it.toSearch() }

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
        val title = document.selectFirst("div.wrap h1")?.text()?.trim().toString()
            .replace("Video Songs Download", "")
        val poster = document.select("p.showimage img").attr("src")
        val description = document.selectFirst("div.fd1")?.text()?.trim()
            val episodes = mutableListOf<Episode>()
            document.select("div.fl").forEach { it ->
                    val name=it.select("a div").text()
                    val href = it.selectFirst("a")?.attr("href") ?: ""
                    episodes.add(Episode(name=name, data = href))
            }
            return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
            }
    }

        override suspend fun loadLinks(
            data: String,
            isCasting: Boolean,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
        ): Boolean {
            val doc= app.get(data,referer = mainUrl, headers = headers).document
            doc.select(".fshow div a:contains(HD)").forEach {
                val link=it.attr("href")
                val quality=link.toString().substringAfter("HD_").substringBefore("/")
                callback.invoke(
                    ExtractorLink(
                        this.name,
                        this.name,
                        url = link,
                        referer = "",
                        quality = getQualityFromName(quality),
                        type = INFER_TYPE,
                        headers = headers
                    )
                )
            }
            return true
        }
}
