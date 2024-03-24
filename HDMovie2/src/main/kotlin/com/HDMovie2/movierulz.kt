package com.HDMovie2

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class movierulz : MainAPI() {
    override var mainUrl = "https://6movierulz.cc"
    override var name = "6movierulz"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.Movie, TvType.TvSeries)
    override val mainPage = mainPageOf(
        "movies?sort=featured&ref=home-latest-movies" to "Featured Movies",
        "movies?sort=latest&ref=home-latest-movies" to "Movies",
        "category/hollywood-featured" to "Hollywood",
        "category/bollywood-featured" to "Bollywood",
        "category/telugu-featured" to "Telugu Dubbed",
        "category/tamil-featured" to "Tamil Dubbed",
        "web-series?ref=home-latest-movies" to "Web Series",
        "tv?sort=featured&ref=home-latest-tv-series" to "TV Series",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val previouspage = page - 1
        val multipliedPage = previouspage * 16
        //Log.d("Testg","$multipliedPage")
        if (multipliedPage.equals("0")) {
            val document = app.get("$mainUrl/${request.data}/page/0/").document
            val home =
                document.select(
                    "div.content.home_style > ul > li"
                )
                    .mapNotNull { it.toSearchResult() }

            return newHomePageResponse(
                list = HomePageList(name = request.name, list = home, isHorizontalImages = false),
                hasNext = true
            )
        } else {
            val document = app.get("$mainUrl/${request.data}/page/$multipliedPage/").document
            //Log.d("Mandik", "$document")
            val home =
                document.select(
                    "div.content.home_style > ul > li"
                )
                    .mapNotNull { it.toSearchResult() }

            return newHomePageResponse(
                list = HomePageList(name = request.name, list = home, isHorizontalImages = false),
                hasNext = true
            )
        }
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = this.select("div.boxed.film > a").attr("title")
        val href = this.select("div.boxed.film > a").attr("href")
        val posterUrl = this.selectFirst("div.boxed.film > a > div > img")?.attr("src")
        return newMovieSearchResponse(title, href, TvType.Movie) { this.posterUrl = "$posterUrl" }
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

        val title =
            document.selectFirst("div.entry-content > img")?.attr("alt")?.trim().toString()
        val poster =
            document.selectFirst("div.entry-content > img")?.attr("src")?.trim().toString()
        val description =
            document.selectFirst("div.entry-content > p:nth-child(6)").text().trim()
        //Log.d("Tesy12","$poster")
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
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
        val document = app.get(data).document
        //Log.d("Test1244","$document")
        val script = document.selectFirst("script:containsData(location)")?.data().toString().trimIndent()
        val urlRegex = Regex("""https?:\\/\\/(?:\\\\/)?[^",]+""")
        val matches = urlRegex.findAll(script)

        val extractedUrls = mutableListOf<String>() // List to store the matched values

        matches.forEach {
            val url = it.value.replace("\\/", "/").replace("\\", "")
            extractedUrls.add(url)
        }
        //Log.d("Test1244", "$script")
        //Log.d("Test1244", "$matches")
        //Log.d("Test12494", "$extractedUrls")
        extractedUrls.forEach { url->
            if (url.contains("dood"))
            {
                Log.d("Testdood", url)
                val links =
                    DoodReExtractor()
                        .getUrl(
                            url,""
                        ) // hardcoding the referer to test
                links?.forEach { link -> callback.invoke(link) }
            }
            else
            Log.d("Test", url)
                Log.d("Testelse", url)
                loadExtractor(url, subtitleCallback, callback)
        }
        return true
    }
}
