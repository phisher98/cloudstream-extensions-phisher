package com.Desicinemas


import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import okhttp3.Interceptor
import okhttp3.Response
import org.jsoup.nodes.Element

open class DesicinemasProvider : MainAPI() {
    override val supportedTypes = setOf(
        TvType.Movie
    )
    override var lang = "hi"

    override var mainUrl = "https://desicinemas.to"
    override var name = "Desicinemas"

    override val hasMainPage = true

    override val mainPage = mainPageOf(
        "https://desicinemas.to/" to "Home",
        "https://desicinemas.to/category/punjabi/" to "Punjabi",
        "https://desicinemas.to/category/bollywood/" to "Bollywood",
        "https://desicinemas.to/category/hindi-dubbed/" to "Hindi Dubbed"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse? {
        val url = if (page == 1 || request.name == "Home") {
            request.data
        } else {
            "${request.data}page/$page/"
        }

        val doc = app.get(url, referer = "$mainUrl/").document

        val pages1 = if (request.name == "Home") {
            doc.selectFirst(".MovieListTop")
                ?.toHomePageList("Most popular")
        } else null

        val pages2 = if (request.name == "Home") {
            doc.selectFirst("#home-movies-post")
                ?.toHomePageList("Latest Movies")
        } else null

        val pages3 = if (request.name != "Home") {
            doc.selectFirst(".MovieList")
                ?.toHomePageList(request.name)
        } else null

        val hasNext = request.name != "Home" && pages3?.list?.isNotEmpty() == true
        return newHomePageResponse(arrayListOf(pages1, pages2, pages3).filterNotNull(), hasNext)
    }

    private fun Element.toHomePageList(name: String): HomePageList {
        val items = select("li, .TPostMv")
            .mapNotNull {
                it.toHomePageResult()
            }
        return HomePageList(name, items)
    }

    private fun Element.toHomePageResult(): SearchResponse? {
        val title = selectFirst("h2")?.text()?.trim() ?: return null
        val href = fixUrlNull(selectFirst("a")?.attr("href")) ?: return null
        val img = selectFirst("img")
        val posterUrl = fixUrlNull(img?.attr("data-src"))

        return newAnimeSearchResponse(title, href) {
            this.posterUrl = posterUrl
        }
    }


    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val doc = app.get(url, referer = "$mainUrl/").document

        val items = doc.select(".MovieList li").mapNotNull {
            it.toHomePageResult()
        }
        return items
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url, referer = "$mainUrl/").document

        val title = doc.selectFirst("h1")?.text()?.trim() ?: return null
        val posterUrl = doc.select(".Image img").attr("src")

        val episodes = arrayListOf(newEpisode(url) {
            name = title
        })

        return newTvSeriesLoadResponse(title, url, TvType.Movie, episodes) {
            this.posterUrl = fixUrlNull(posterUrl)
            plot = doc.selectFirst(".Description p")?.text()
            tags = doc.select(".Genre a").map { it.text() }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val doc = app.get(data, referer = "$mainUrl/").document
        doc.select(".MovieList .OptionBx").amap {
            val name=it.select("p.AAIco-dns").text()
            val link = it.select("a").attr("href")
            val doc2 = app.get(link, referer = data).document
            val src = doc2.select("iframe").attr("src")
            loadExtractor(src, subtitleCallback, callback,name)
        }
        return true
    }

    override fun getVideoInterceptor(extractorLink: ExtractorLink): Interceptor? {
        return object : Interceptor {
            override fun intercept(chain: Interceptor.Chain): Response {
                return chain.proceed(chain.request())
            }
        }
    }

}
