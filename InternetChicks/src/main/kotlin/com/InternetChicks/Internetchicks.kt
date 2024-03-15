package com.coxju

import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class internetchicks : MainAPI() {
    override var mainUrl              = "https://internetchicks.com"
    override var name                 = "Internetchicks"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasQuickSearch       = false
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.NSFW)
    override val vpnStatus            = VPNStatus.MightBeNeeded

    override val mainPage = mainPageOf(
        "category/onlyfans" to "Onlyfans",
        "category/patreon" to "Patreon",
        "category/manyvids" to "Random",
        "category/tiktok" to "Tiktok",
        "category/webcam" to "Webcam",
        "category/snapchat" to "Snapchat",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}/page/$page/").document
        val home     = document.select("article").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list    = HomePageList(
                name               = request.name,
                list               = home,
                isHorizontalImages = true
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title     = this.select("header > h2 > a").text().trim()
        val href      = fixUrl(this.select("header > h2 > a").attr("href"))
        val posterUrl = fixUrlNull(this.select("header > a > img").attr("data-src"))
        println(posterUrl)
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..5) {
            val document = app.get("${mainUrl}/page/$i/?s=$query&id=5036").document

            val results = document.select("article").mapNotNull { it.toSearchResult() }

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

        val title       = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim().toString()
        val poster      = fixUrlNull(document.selectFirst("[property='og:image']")?.attr("content"))
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()


        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot      = description
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).document
        //val sources = mutableListOf<String>()
        document.select("article > div > div > button").forEach { button ->
            val onclickAttr = button.attr("onclick")
            val regex = Regex("""playEmbed\('([^']+)'\)""")
            val matchResult = regex.find(onclickAttr)
            val playlistUrl = matchResult?.groups?.get(1)?.value
            playlistUrl?.let {
                //println(it) // Print the extracted URL
                //sources.add(it) // Add the URL to the sources list
                loadExtractor(it, subtitleCallback, callback)
            }
        }
        return true
    }
}
