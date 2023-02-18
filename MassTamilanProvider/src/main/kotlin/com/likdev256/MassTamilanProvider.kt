package com.likdev256

//import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import org.jsoup.nodes.Element
import com.lagradost.nicehttp.NiceResponse
import okhttp3.FormBody

class MovieHUBProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://masstamilan.dev/"
    override var name = "MassTamilan"
    override val hasMainPage = true
    override var lang = "ta"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.TvSeries
    )

    override val mainPage = mainPageOf(
        "$mainUrlhttps://masstamilan.dev/latest-updates" to "Latest Updates",
        "$mainUrlhttps://masstamilan.dev/tamil-songs" to "Tamil Songs",
        "$mainUrlhttps://masstamilan.dev/telugu-songs" to "Telugu Songs",
        "$mainUrlhttps://masstamilan.dev/malayalam-songs" to "Malayalam Songs",
        "$mainUrlhttps://masstamilan.dev/hindi-songs" to "Hindi Songs"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val document = if (page == 1) {
            app.get(request.data).document
        } else {
            app.get(request.data + "?page=$page").document
        }

        //Log.d("Document", request.data)
        val home = document.select("div.botlist > div.a-i").mapNotNull {
                it.toSearchResult()
            }

        return HomePageResponse(arrayListOf(HomePageList(request.name, home)), hasNext = true)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        //Log.d("Got","got here")
        val title = this.selectFirst("div.info > h2")?.text()?.toString()?.trim() ?: return null
        //Log.d("title", title)
        val href = fixUrl(mainUrl + this.select("a").attr("href"))
        //Log.d("href", href)
        val posterUrl = fixUrlNull(this.selectFirst("div.ava > picture > img")?.attr("src"))
        //Log.d("posterUrl", posterUrl.toString())
        return newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                this.posterUrl = posterUrl
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

    data class MassTamilanLinks (
        @JsonProperty("sourceName") val sourceName: String,
        @JsonProperty("sourceLink") val sourceLink: String
    )

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        //Log.d("Doc", doc.toString())
        val title = doc.selectFirst("div.pad10 > header > h1")?.text()?.substringBefore("mp3")?.trim()
            ?: return null
        //Log.d("title", title)
        val poster = fixUrlNull(mainUrl + doc.selectFirst("#movie-image > figure > picture > img")?.attr("src"))
        //Log.d("poster", poster.toString())
        val info = doc.select("#movie-handle")
        Log.d("mybadinfo", info.toString())
        val tags = doc.select("div.sgeneros > a").map { it.text() }
        val year =
            doc.selectFirst("span.date")?.text()?.toString()?.substringAfter(",")?.trim()?.toInt()
        //Log.d("year", year.toString())
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

        val episodes = ArrayList<Episode>()
        doc.select("#tlist > tbody > tr[itemprop]").map { me ->
            val links = me.select("td > a").map {
                MassTamilanLinks(
                    it.text(),
                    mainUrl + it.attr("href")
                )
            }
            episodes.add(
                Episode(
                   data = links.toJson(),
                   name = me.select("td > span > h2 > span[itemprop~=name] > a"),
                   season = 1,
                   episode = me.select("td > span[itemprop~=position]").toInt()
                )
            )
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                //this.year = year
                //this.tags = tags
                //this.actors = actors
            }
    }


    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        parseJson<MassTamilanLinks>(data).map {
            //val mp3Stream = app.get(data, allowRedirects = true)
            safeApiCall {
                callback.invoke(
                    ExtractorLink(
                        it.sourceName,
                        it.sourceName,
                        it.sourceLink,
                        "$mainUrl/",
                        Qualities.Unknown.value
                    )
                )
            }
        }


        return true
    }
}
