package com.likdev256

//import android.util.Log
import com.lagradost.cloudstream3.*
//import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
//import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class TamilYogiProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://tamilyogi.city"
    override var name = "TamilYogi"
    override val hasMainPage = true
    override var lang = "ta"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    //private const val TAG = "TamilYogi"

    override val mainPage = mainPageOf(
        "$mainUrl/category/tamilyogi-full-movie-online/" to "New Movies",
        "$mainUrl/category/tamil-hd-movies/" to "HD Movies",
        "$mainUrl/category/tamilyogi-dubbed-movies-online/" to "Dubbed Movies",
        "$mainUrl/category/tamil-web-series/" to "TV Series"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        //Log.d("request", request.toString())
        //Log.d("Check", request.data)
        //Log.d("Page", page.toString())

        val document = if (page == 1) {
            app.get(request.data).document
        } else {
            app.get(request.data + "page/" + page).document
        }
        //Log.d("CSS element", document.select("ul li").toString())
        val home = document.select("ul li").mapNotNull {
            it.toSearchResult()
        }
        return HomePageResponse(arrayListOf(HomePageList(request.name, home, isHorizontalImages = true)), hasNext = true)
        //return newHomePageResponse(request.name, home)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        val titleS = this.selectFirst("div.cover a")?.attr("title")?.toString()?.trim() ?: return null
        val titleRegex = Regex("(^.*\\)\\d*)")
        val title = titleRegex.find(titleS)?.groups?.get(1)?.value.toString()
        //Log.d("title", titleS)
        val href = fixUrl(this.selectFirst("a")?.attr("href").toString())
        //Log.d("href", href)
        val posterUrl = fixUrlNull(this.selectFirst("img")?.attr("src"))
        //Log.d("posterUrl", posterUrl.toString())
        val qualityRegex = Regex("(?i)((DVDRip)|(HD)|(HQ)|(HDRip))")
        val qualityN = qualityRegex.find(titleS)?.value.toString()
        //Log.d("QualityN", qualityN)
        val quality = getQualityFromString(qualityN)
        //Log.d("Quality", quality.toString())
        val checkTvSeriesRegex = Regex("(?i)(E\\s?[0-9]+)|([-]/?[0-9]+)")
        val isTV = title.contains(checkTvSeriesRegex)

        return if (isTV) {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                this.quality = quality
            }
        } else {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                this.quality = quality
            }
        }

      /*  return if (isTV == true) {
        newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            this.quality = quality
        } */
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        //Log.d("document", document.toString())

        return document.select("ul li").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        //Log.d("Doc", doc.toString())
        val titleL = doc.selectFirst("#content h1 a")?.attr("title")?.toString()?.trim() ?: return null
        val titleRegex = Regex("(^.*\\)\\d*)")
        val rmGibRegex = Regex("(Permanent Link to )")
        val title = rmGibRegex.replace(titleRegex.find(titleL)?.groups?.get(1)?.value.toString(), "")
        //Log.d("title", title)
        //val titleRegex = Regex()
        //val title =
        val poster = fixUrlNull(doc.selectFirst("meta[content\$=\".jpg\"]")?.attr("content"))
        //Log.d("poster", poster.toString())
        //val tags = document.select("div.mvici-left p:nth-child(1) a").map { it.text() }
        val yearRegex = Regex("(?<=\\()[\\d(\\]]+(?!=\\))")
        val year = yearRegex.find(title)?.value
            ?.toIntOrNull()
        //Log.d("year", year.toString())
        val checkTvSeriesRegex = Regex("(?i)(E\\s?[0-9]+)|([-]/?[0-9]+)")
        val tvType = if (title.contains(checkTvSeriesRegex))
            TvType.TvSeries else TvType.Movie
        //val description = document.selectFirst("p.f-desc")?.text()?.trim()
        //val trailer = fixUrlNull(document.select("iframe#iframe-trailer").attr("src"))
        //val rating = document.select("div.mvici-right > div.imdb_r span").text().toRatingInt()
        //val actors = document.select("div.mvici-left p:nth-child(3) a").map { it.text() }
        val recommendations = doc.select("ul li").mapNotNull {
            it.toSearchResult()
        }

       /* return if (tvType == TvType.TvSeries) {
            val episodes = if (doc.selectFirst("div.les-title strong")?.text().toString()
                    .contains(Regex("(?i)EP\\s?[0-9]+|Episode\\s?[0-9]+"))
            ) {
                doc.select("ul.idTabs li").map {
                    val id = it.select("a").attr("href")
                    Episode(
                        data = fixUrl(doc.select("div$id iframe").attr("src")),
                        name = it.select("strong").text().replace("Server Ep", "Episode")
                    )
                }
            } else {
                doc.select("div.les-content a").map {
                    Episode(
                        data = it.attr("href"),
                        name = it.text().replace("Server Ep", "Episode").trim(),
                    )
                }
            }

            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.year = year
                //this.plot = description
                //this.tags = tags
                //this.rating = rating
                //addActors(actors)
                this.recommendations = recommendations
                //addTrailer(trailer)
            }
        } else { */
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.year = year
                //this.plot = description
                //this.tags = tags
                //this.rating = rating
                //addActors(actors)
                this.recommendations = recommendations
                //addTrailer(trailer)
            }
       // }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val linkRegex = Regex("(https?:\\/\\/(www\\.)?[-a-zA-Z0-9@:%._\\+~#=]{1,256}\\.[a-zA-Z0-9()]{1,6}\\b([-a-zA-Z0-9()@:%_\\+.~#?&\\/\\/=]*mp4))")
        val source = app.get(data).document.select("div.entry iframe").attr("src")
        val script = app.get(source, referer = "$mainUrl/").document.select("body > script").toString()
        //val links = linkRegex.find(script)?.groups?.get(1)?.value.toString()
        val links = linkRegex.findAll(script).map{it.value.trim()}.toList()
        //Log.d("links", links.toString())
            safeApiCall {
                callback.invoke(
                    ExtractorLink(
                        "TamilYogi",
                        "HD",
                        links[0],
                        "$mainUrl/",
                        Qualities.P720.value,
                        false
                    )
                )
                callback.invoke(
                    ExtractorLink(
                        "TamilYogi",
                        "SD",
                        links[1],
                        "$mainUrl/",
                        Qualities.P480.value,
                        false
                    )
                )
                callback.invoke(
                    ExtractorLink(
                        "TamilYogi",
                        "Low",
                        links[2],
                        "$mainUrl/",
                        Qualities.P360.value,
                        false
                    )
                )
            }
        return true
    }
}
