package com.likdev256

//import android.util.Log
import android.annotation.SuppressLint
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import org.jsoup.nodes.Element
import java.util.*
import kotlin.collections.ArrayList

class FDMProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://freedrivemovie.lol"
    override var name = "FreeDriveMovie"
    override val hasMainPage = true
    override var lang = "ta"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    override val mainPage = mainPageOf(
        "$mainUrl/movies/" to "Movies",
        "$mainUrl/tvshows/" to "TV Shows",
        "$mainUrl/genre/action/" to "Action",
        "$mainUrl/genre/adventure/" to "Adventure",
        "$mainUrl/genre/animation/" to "Animation",
        "$mainUrl/genre/fantasy/" to "Fantasy",
        "$mainUrl/genre/horror/" to "Horror",
        "$mainUrl/genre/romance/" to "Romance",
        "$mainUrl/genre/science-fiction/" to "Sci-Fi"
    )

    data class FDMLinks(
        @JsonProperty("sourceName") val sourceName: String,
        @JsonProperty("sourceLink") val sourceLink: String
    )

/*    data class OiyaLink (
        @JsonProperty("ads1"      ) var ads1      : String?,
        @JsonProperty("ads2"      ) var ads2      : String?,
        @JsonProperty("logo"      ) var logo      : String?,
        @JsonProperty("image1"    ) var image1    : String?,
        @JsonProperty("image2"    ) var image2    : String?,
        @JsonProperty("image3"    ) var image3    : String?,
        @JsonProperty("delaytext" ) var delaytext : String?,
        @JsonProperty("delay"     ) var delay     : String?,
        @JsonProperty("adb"       ) var adb       : String?,
        @JsonProperty("adb1"      ) var adb1      : String?,
        @JsonProperty("adb2"      ) var adb2      : String?,
        @JsonProperty("linkr"     ) var linkr     : String
    )
*/
    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = if (page == 1) {
            app.get(request.data).document
        } else {
            app.get(request.data + "/page/$page/").document
        }

        //Log.d("Document", request.data)
        val home = if (request.data.contains("genre")) {
            document.select("div.items > article").mapNotNull {
                it.toSearchResult()
            }
        } else {
            document.select("#archive-content > article").mapNotNull {
                it.toSearchResult()
            }
        }

        return HomePageResponse(arrayListOf(HomePageList(request.name, home)), hasNext = true)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        //Log.d("Got","got here")
        val title = this.selectFirst("div.animation-1 > div.title > h4")?.text()?.toString()?.trim() ?: return null
        //Log.d("title", title)
        val href = fixUrl(this.selectFirst("div.poster > a")?.attr("href").toString())
        //Log.d("href", href)
        val posterUrl = fixUrlNull(this.selectFirst("div.poster > img")?.attr("src"))
        //Log.d("posterUrl", posterUrl.toString())
        //Log.d("QualityN", qualityN)
        val quality = getQualityFromString(this.select("div.poster > div.mepo > span").text().toString())
        //Log.d("Quality", quality.toString())
        return if (href.contains("Movie", true)) {
            newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                this.quality = quality
            }
        } else {
            newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
                this.quality = quality
            }
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        //Log.d("document", document.toString())

        return document.select("div.result-item").mapNotNull {
            val title = it.selectFirst("article > div.details > div.title > a")?.text().toString().trim()
            //Log.d("title", titleS)
            val href = fixUrl(it.selectFirst("article > div.details > div.title > a")?.attr("href").toString())
            //Log.d("href", href)
            val posterUrl = fixUrlNull(it.selectFirst("article > div.image > div.thumbnail > a > img")?.attr("src"))
            //Log.d("posterUrl", posterUrl.toString())
            //Log.d("QualityN", qualityN)
            val quality = getQualityFromString(it.select("div.poster > div.mepo > span").text().toString())
            //Log.d("Quality", quality.toString())
            val type = it.select("article > div.image > div.thumbnail > a > span").text().toString()
            if (type.contains("Movie")) {
                newMovieSearchResponse(title, href, TvType.Movie) {
                    this.posterUrl = posterUrl
                    this.quality = quality
                }
            } else {
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                    this.posterUrl = posterUrl
                    this.quality = quality
                }
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        //Log.d("Doc", doc.toString())
        val title = doc.select("div.sheader > div.data > h1").text().trim()
        //Log.d("title", title)
        val poster = fixUrlNull(doc.selectFirst("div.poster > img")?.attr("src"))
        val bgposter = fixUrlNull(
            doc.select("div.g-item:nth-child(1) > a:nth-child(1)").attr("href")
        )
        //Log.d("bgposter", bgposter.toString())
        //Log.d("poster", poster.toString())
        val tags = doc.select("div.sgeneros > a").map { it.text() }
        val year =
            doc.selectFirst("span.date")?.text()?.toString()?.substringAfter(",")?.trim()?.toInt()
        //Log.d("year", year.toString())
        val description = doc.select("div.wp-content > p").text().trim()
        val type = if (url.contains("movies", true)) TvType.Movie else TvType.TvSeries
        //Log.d("desc", description.toString())
        val trailer = fixUrlNull(doc.select("iframe.rptss").attr("src"))
        //Log.d("trailer", trailer.toString())
        val rating = doc.select("span.dt_rating_vgs").text().toRatingInt()
        //Log.d("rating", rating.toString())
        val duration =
            doc.selectFirst("span.runtime")?.text()?.toString()?.removeSuffix(" Min.")?.trim()
                ?.toInt()
        //Log.d("dur", duration.toString())
        val actors =
            doc.select("div.person").map {
                ActorData(
                    Actor(
                        it.select("div.data > div.name > a").text().toString(),
                        it.select("div.img > a > img").attr("src").toString()
                    ),
                    roleString = it.select("div.data > div.caracter").text().toString(),
                )
            }
        val recommendations = doc.select("#dtw_content_related-2 article").mapNotNull {
            it.toSearchResult()
        }

        val episodes = ArrayList<Episode>()
        doc.select("#seasons div.se-c").map { me ->
            val seasonNum = me.select("div.se-q > span.se-t").text()
            me.select("div.se-a > ul > li").mapIndexed { index, it ->
                episodes.add(
                    Episode(
                        data = it.select("div.episodiotitle > a").attr("href").toString(),
                        name = it.select("div.episodiotitle > a").text(),
                        season = seasonNum.toInt(),
                        episode = index,
                        posterUrl = it.select("div.imagen > img").attr("src"),
                        description = it.select("div.episodiotitle > span.date").text()
                    )
                )
            }
        }
        val movieLinks = doc.select("div.fix-table > table > tbody > tr").map {
            FDMLinks(
                "${it.select("td > strong").text()}@" +
                        it.select("td:nth-child(3)").text() +
                        "[${it.select("td:nth-child(4)").text()}]",
                it.select("td > a").attr("href")
            )
        }.first { !(it.sourceName.contains("mega", true)) }

        return if (type == TvType.Movie) {
            newMovieLoadResponse(title, url, TvType.Movie, movieLinks.toJson()) {
                this.posterUrl = poster?.trim()
                this.backgroundPosterUrl = bgposter?.trim()
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                this.duration = duration
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        } else {
            newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster?.trim()
                this.backgroundPosterUrl = bgposter?.trim()
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                this.duration = duration
                this.actors = actors
                this.recommendations = recommendations
                addTrailer(trailer)
            }
        }
    }

    @SuppressLint("NewApi")
    private suspend fun fdmExtractor(sourceLink: String): String {
        //Log.d("mybadadlinks", sourceLink)
        val fdmLinkDoc = app.get(sourceLink).document
        val base64Link = fdmLinkDoc.select("#link")
            .attr("href").substringAfter("/go/").trim().replace("-", "")
        //Log.d("mybadbase", base64Link)
        val adLink = String(
            Base64.getDecoder().decode(base64Link)
        )
        //Log.d("mybadadlinks", adLink)
        //Log.d("mybadadlinks", app.get(adLink, allowRedirects = true).toString())
        return String(
            Base64.getDecoder().decode(
                app.get(adLink, allowRedirects = true).document
            .select("input").attr("value")
            )
        )
    }

    suspend fun extractOiya(url: String): List<FDMLinks>? {
        val doc = app.get(url).document
        return doc.selectFirst("div.is-content-justification-left")?.select("div.wp-block-button > a")?.map {
            FDMLinks(
                it.ownText(),
                it.attr("href")
            )
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        //Log.d("mybadata", data)
        if (data.contains("episode", true)) {
            //Fuckin TODOok
            //val doc = app.get(data).document
            /*val epLinks = doc.select("div.fix-table > table > tbody > tr").map {
                FDMLinks(
                    "${it.select("td > strong").text()}@" +
                            it.select("td:nth-child(3)").text() +
                            "[${it.select("td:nth-child(4)").text()}]",
                    it.select("td > a").attr("href")
                )
            }.filter { it.sourceName.contains("watch", true) }

            Log.d("mybadepLinks", epLinks.toString())
            val final = epLinks.map {
                fdmExtractor(it.sourceLink)
                //Log.d("mybadOiya", oiyaLink)
            }.filter { it.contains("oiya", true) }.distinct().joinToString() */
        } else {
            val links = parseJson<FDMLinks>(data)
            //Log.d("mybadlinks", links.toString())
            val oiyaLink = fdmExtractor(links.sourceLink).replace("-watch", "")
            //Log.d("mybadOiya", oiyaLink)

            extractOiya(oiyaLink)?.map {
                safeApiCall {
                    callback.invoke(
                        ExtractorLink(
                            it.sourceName,
                            it.sourceName,
                            it.sourceLink,
                            "",
                            Qualities.Unknown.value,
                            isM3u8 = false
                        )
                    )
                }
            }
        }

        return true
    }
}
