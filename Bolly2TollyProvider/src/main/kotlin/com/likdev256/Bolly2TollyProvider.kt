import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Element

class Bolly2Tolly : MainAPI() {
    override var mainUrl = "https://upmovies.to"
    override var name = "UPMovies"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasQuickSearch = true
    override val hasDownloadSupport = false
    override val hasChromecastSupport = true
    override val supportedTypes = setOf(TvType.Live)

    override val mainPage = mainPageOf(
        "${mainUrl}/movies-countries/india.html" to "Indian Movies",
    //    "${mainUrl}/category/english-movies/page/" to "English",
     //   "${mainUrl}/category/hindi-movies/page/" to "Hindi",
     //   "${mainUrl}/category/telugu-movies/page/" to "Telugu",
     //   "${mainUrl}/category/tamil-movies/page/" to "Tamil",
     //   "${mainUrl}/category/kannada-movies/page/" to "Kannada",
      //  "${mainUrl}/category/malayalam-movies/page/" to "Malayalam",
      //  "${mainUrl}/category/bengali-movies/page/" to "Bengali"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = app.get(request.data).document
        //Log.d("Mandik", "$document")
        val home = document.select("div.category > div.shortItem.listItem").mapNotNull {
           it.toSearchResult()
           }
        return newHomePageResponse(request.name, home)
        }

    private fun Element.toSearchResult(): SearchResponse? {
        val title = this.select(".itemInfo > div > div.title > a").text()
        val href = fixUrl(this.selectFirst(".itemInfo > div > div.title > a")?.attr("href").toString())
        val posterUrl = fixUrlNull(this.selectFirst(".itemBody > div > div > a > img")?.attr("src"))
        return newMovieSearchResponse(title ?: return null, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }


    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title =document.selectFirst(".film-detail-wrap > div h1")?.text()?.trim().toString()
        val poster =fixUrl(document.select("div.poster > img")?.attr("src").toString())
        val spoiler =document.selectFirst("div.textSpoiler")?.text()?.trim().toString()

        return newMovieLoadResponse(title, url, TvType.Live, url) {
            this.posterUrl = poster
            this.plot = spoiler
        }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val document = app.get(data).document
        Log.d("Link","$document")
        var link=document.select("body > div > div > div > video > source")?.attr("src").toString().trim()
        Log.d("Mandiklink",link)
        callback.invoke(
            ExtractorLink(
                source = this.name,
                name = this.name,
                url = link,
                referer = "",
                quality = Qualities.Unknown.value,
            )
        )
        return true
    }
    /*
        override suspend fun search(query: String): List<SearchResponse> {
            val document = app.get("$mainUrl/?s=$query").document

            return document.select(".result-item").mapNotNull {
                val title = it.select("SubTitle").text().trim()
                val href = fixUrl(it.selectFirst(".title a")?.attr("href").toString())
                val posterUrl = fixUrlNull(it.selectFirst(".thumbnail img")?.attr("src"))
                val quality = getQualityFromString(it.select("span.quality").text())
                val tvtype = if (href.contains("tvshows")) TvType.TvSeries else TvType.Movie
                newMovieSearchResponse(title, href, tvtype) {
                    this.posterUrl = posterUrl
                    this.quality = quality
                }
            }
        }

        override suspend fun load(url: String): LoadResponse? {
            val document = app.get(url).document

            val title = document.selectFirst(".SubTitle")?.text()?.trim() ?: return null
            val poster = fixUrlNull(document.selectFirst(".Image img")?.attr("src"))
            val tags = document.select(".InfoList li:eq(2) a").map { it.text() }
            val year = document.select("span.Date").text().trim().toIntOrNull()
            val tvType =
                if (document.select(".AA-cont").isNullOrEmpty()) TvType.Movie else TvType.TvSeries
            val description = document.selectFirst(".Description p")?.text()?.trim()
            //val rating = document.select(".post-ratings strong").last()!!.text().toRatingInt()
            val actors = document.select(".ListCast a").map { it.text().trim() }
            val recommendations = document.select(".Wdgt ul.MovieList li").mapNotNull {
                it.toSearchResult()
            }

            return if (tvType == TvType.TvSeries) {
                val episodes = document.select("tbody tr").mapNotNull {
                    val href = fixUrl(it.select(".MvTbTtl a").attr("href") ?: return null)
                    Log.d("href", href)
                    val name = it.select(".MvTbTtl a").text().trim()
                    val thumbs = "https:" + it.select("img").attr("src")
                    val season = document.select(".AA-Season").attr("data-tab").toInt()
                    val episode = it.select("span.Num").text().toInt()
                    Episode(
                        href,
                        name,
                        season,
                        episode,
                        thumbs
                    )
                }

                newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                    this.posterUrl = poster
                    this.year = year
                    this.plot = description
                    this.tags = tags
                    //this.rating = rating
                    addActors(actors)
                    this.recommendations = recommendations
                }
            } else {
                newMovieLoadResponse(title, url, TvType.Movie, url) {
                    this.posterUrl = poster
                    this.year = year
                    this.plot = description
                    this.tags = tags
                    //this.rating = rating
                    addActors(actors)
                    this.recommendations = recommendations
                }
            }
        }

        override suspend fun loadLinks(
            data: String,
            isCasting: Boolean,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
        ): Boolean {
            println(data)
            val sources = mutableListOf<String>()
            val document = app.get(data).document
            sources.add(document.select(".TPlayer iframe").attr("src"))
            val srcRegex = Regex("""(https.*?)"\s""")
            srcRegex.find(
                document.select(".TPlayer").text()
            )?.groupValues?.map { sources.add(it.replace("#038;", "")) }
            println(sources)
            sources.forEach {
                val source = app.get(it, referer = data).document.select("iframe").attr("src")
                println(source)
                loadExtractor(
                    source,
                    subtitleCallback,
                    callback
                )
            }
            return true
        }
        */
}
