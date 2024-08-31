package com.Primewire

//import android.util.Log
import com.lagradost.api.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.utils.*

class Primewire : MainAPI() {
    override var mainUrl              = "https://www.primewire.tf"
    override var name                 = "Primewire"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val hasQuickSearch       = true
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.Movie,TvType.TvSeries)

    override val mainPage = mainPageOf(
        "filter?sort=Trending+Today&type=movie" to "Trending Movies",
        "filter?sort=Trending+Today&type=tv" to "Trending TvSeries",
        "filter?sort=Just+Added&free_links=true&&type=movie" to "Recently Added Movies",
        "filter?sort=Featured&e=v" to "Movies",
        "filter?sort=Just+Added&free_links=true&type=tv" to "Recently Added TvSeries",
        "filter?type=tv&sort=Latest%20Episode" to "TvSeries",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}&page=$page").document
        val home     = document.select("div.index_container > div.index_item").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list    = HomePageList(
                name               = request.name,
                list               = home,
                isHorizontalImages = false
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title     = this.select("a").attr("title")
        val href      = fixUrl(this.select("a").attr("href"))
        val posterUrl = fixUrlNull(this.select("a > img").attr("src"))
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..3) {
            val document = app.get("${mainUrl}/page/$i/?s=$query").document

            val results = document.select("div.blog-items > article").mapNotNull { it.toSearchResult() }

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
        val title       = document.selectFirst("h1.titles > span > a")?.text() ?:""
        var poster = document.select("div.movie_thumb > a").attr("href")
        if (poster.isEmpty())
        {
            poster="https://img.freepik.com/free-photo/assortment-cinema-elements-red-background-with-copy-space_23-2148457848.jpg?size=626&ext=jpg&ga=GA1.1.2082370165.1716422400&semt=ais_user"
        }
        val description = document.selectFirst("div.movie_info td > p")?.text()?.trim()
        val trailer = document.selectFirst("div.tmdb-trailer > iframe")?.attr("src")
        val actors = document.select("div.movie_info > table > tbody > tr:nth-child(8)").map {
            Actor(
                it.select(" td  span a").text(),
            )
        }
        val tvType=if (url.contains("tv")) TvType.TvSeries else TvType.Movie
        return if (tvType == TvType.TvSeries) {
            val episodes =
                document.select("div.tv_episode_item.released").map {
                    val href = it.select("a").attr("href")
                    val trueurl="$mainUrl$href"
                    val episode = it.selectFirst("a span.tv_episode_name")?.ownText()?.substringAfter("-")
                        ?.trim()
                        ?:""
                    val name=it.select("a").text()
                    val regex = Regex("E(\\d+)")
                    val matchResult = regex.find(name)
                    var number=0
                    if (matchResult != null) {
                         number= matchResult.groupValues[1].toIntOrNull() ?:0
                    } else {
                        Log.d("Error","Error")
                    }
                    val season= it.select("input").attr("data-season").toIntOrNull()
                    Episode(data=trueurl,name=episode,episode=number,season=season)
                }
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes = episodes) {
                this.posterUrl = poster
                this.plot = description
            }
        }
        else {
            return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster.ifEmpty {
                    ({
                        posterUrl =
                            "https://www.streamblasters.link/wp-content/uploads/2022/05/cropped-png12.png"
                    }).toString()
                }
                this.plot = description
                addTrailer(trailer)
                addActors(actors)
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).document
        Log.d("Phisher test",data)
        document.select("span.movie_version_link a").map {
            val partialhref=it.attr("data-wp-menu")
            if (partialhref.isEmpty())
            {
                Log.d("Not Found","Not Found")
            }
            else
            {
                    val href="$mainUrl/links/go/$partialhref"
                    val link=app.get(href, allowRedirects = false).headers["location"] ?:""
                    Log.d("Phisher test links",link)
                    loadExtractor(link,subtitleCallback, callback)
            }
        }
        return true
    }
}
