import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*

class IndianTVPlugin : MainAPI() {
    override var mainUrl              = "https://madplay.live/hls/tata/"
    override var name                 = "TATA Sky"
    override val hasMainPage          = true
    override var lang                 = "hi"
    override val hasQuickSearch       = true
    override val hasDownloadSupport   = true
    override val hasChromecastSupport = true
    override val supportedTypes       = setOf(TvType.Live)

      override val mainPage = mainPageOf(
        "${mainUrl}/" to "TATA",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get(request.data).document
        val home     = document.select("div#listContainer > div.box1").mapNotNull { it.toSearchResult() }
        val groupedHome = home.groupBy { it.category } // Grouping by category

        val homePageLists = groupedHome.map { (category, items) ->
            HomePageList(
                name = category ?: request.name, // Using category as name if available
                list = items,
                isHorizontalImages = true
            )
        }

        return newHomePageResponse(
            list    = homePageLists,
            hasNext = false
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title     = this.select("h2.text-center").text()
        val href      = fixUrl(this.select("a").attr("href"))
        val posterUrl = fixUrlNull(this.select("img").attr("src"))
        val category  = this.select("p").text()

        return newMovieSearchResponse(title, href, TvType.Live) {
            this.posterUrl = posterUrl
            this.category = category // Storing category
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()
        /* 
        for (i in 1..10) {
            val document = app.get("${mainUrl}/page/$i?s=$query").document

            val results = document.select("div.box1").mapNotNull { it.toSearchResult() }

            if (!searchResponse.containsAll(results)) {
                searchResponse.addAll(results)
            } else {
                break
            }

            if (results.isEmpty()) break
        }
        */
        return searchResponse
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title       = document.selectFirst("div.program-info > span.channel-name")?.text()?.trim().toString()
        val poster      = fixUrl("https://raw.githubusercontent.com/phisher98/HindiProviders/master/TATATVProvider/src/main/kotlin/com/lagradost/0-compressed-daf4.jpg")
        val showname    = document.selectFirst("div.program-info > div.program-name")?.text()?.trim().toString()
        val description = document.selectFirst("div.program-info > div.program-description")?.text()?.trim().toString()

        return newMovieLoadResponse(title, url, TvType.Live, url) {
            this.posterUrl = poster
            this.plot      = showname
        }
    }
    
    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).document

        document.select("video.vjs-tech").map { res ->
            callback.invoke(
                    ExtractorLink(
                        source  = this.name,
                        name    = this.name,
                        url     = fixUrl(res.attr("src")?.trim().toString()),
                        referer = data,
                        quality = Qualities.Unknown.value
                    )
            )
        }

        return true
    }
}
