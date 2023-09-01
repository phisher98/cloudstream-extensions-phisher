package com.likdev256

//import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.nicehttp.NiceResponse
import okhttp3.FormBody
import org.jsoup.nodes.Element

class AllMovieLandProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://allmovieland.fun"
    override var name = "AllMovieLand"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries,
        TvType.Cartoon
    )

    private var playerDomain: String? = ""
    private var cookiesSSID: String? = ""
    private var cookies = mapOf<String, String>()
    private var tokenKey: String? = ""

    override val mainPage = mainPageOf(
        "$mainUrl/films/" to "Movies",
        "$mainUrl/bollywood/" to "Bollywood Movies",
        "$mainUrl/hollywood/" to "Hollywood Movies",
        "$mainUrl/series/" to "TV Shows",
        "$mainUrl/cartoon/" to "Cartoons"
    )

    private suspend fun querySearchApi(query: String): NiceResponse {
        val body = FormBody.Builder()
            .addEncoded("do", "search")
            .addEncoded("subaction", "search")
            .addEncoded("search_start", "0")
            .addEncoded("full_search", "0")
            .addEncoded("result_from", "1")
            .addEncoded("story", query)
            .build()

        return app.post(
            "$mainUrl/index.php?do=opensearch", //$mainUrl/engine/ajax/controller.php?mod=search
            requestBody = body,
            referer = "$mainUrl/",
            cookies = cookies
        )
    }

    private suspend fun getDlJson(link: String, url: String): String {
        val doc = app.get(link, referer = url).document
        val jsonString = Regex("""\{.*\}""").find(doc.select("body > script:last-child").toString())?.value.toString()
        //Log.d("mybaddoc", doc.toString())
        //Log.d("mybaddoc", jsonString)
        val json = parseJson<Getfile>(jsonString)
        //Log.d("mygodjson1", json.toString())
        //Log.d("mybadjson2", "https://${json.href}${json.file}")
        tokenKey = json.key
        val m3u8Langs = app.post(
            "https://${json.href}${json.file}",
            referer = link,
            headers = mapOf(
                "X-CSRF-TOKEN" to "${json.key}",
            ),
        ).toString()
        //Log.d("mybadjson3", m3u8Langs)
        return m3u8Langs.replace(Regex("(\\,)\\s*\\[\\]"), "")
    }

    private suspend fun getM3u8(file: String?): String {
        return app.post(
            "$playerDomain/playlist/$file.txt",
            headers = mapOf(
                "X-CSRF-TOKEN" to "$tokenKey",
            ),
            referer = "$mainUrl/"
        ).toString()
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        cookiesSSID = app.get("$mainUrl/").cookies["PHPSESSID"]
        cookies = mapOf(
            "PHPSESSID" to "$cookiesSSID"
        )
        val document = if (page == 1) {
            app.get(request.data, cookies = cookies).document
        } else {
            app.get(request.data + "/page/$page/", cookies = cookies).document
        }

        //Log.d("Document", request.data)
        val home = document.select("article.short-mid").mapNotNull {
                    it.toHomeSearchResult()
                }

        return HomePageResponse(arrayListOf(HomePageList(request.name, home)), hasNext = true)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        //Log.d("mygodcookie",cookies.toString())
        val title = this.selectFirst("a > h3")?.text()?.trim() ?: return null
        //Log.d("mybatitle", title)
        val href = fixUrl(this.select("a").attr("href"))
        //Log.d("href", href)
        val posterUrl = fixUrlNull(mainUrl + this.select("div.new-short__poster > a.new-short__poster--link > img").attr("data-src"))
        //Log.d("mygodposterUrl", posterUrl.toString())
        val checkType = this.select("span.new-short__cats").text()
        //Log.d("mybacheck", checkType)
        val type = if (checkType.contains("films", true)) TvType.Movie
        else if (checkType.contains("series", true)) TvType.TvSeries
        else TvType.Cartoon
        return when (type) {
            TvType.Movie -> {
                newMovieSearchResponse(title, href, TvType.Movie) {
                    this.posterUrl = posterUrl
                    posterHeaders = cookies
                }
            }
            TvType.TvSeries -> {
                newTvSeriesSearchResponse(title, href, TvType.TvSeries) {
                    this.posterUrl = posterUrl
                    posterHeaders = cookies
                }
            }
            else -> {
                newMovieSearchResponse(title, href, TvType.Cartoon) {
                    this.posterUrl = posterUrl
                    posterHeaders = cookies
                }
            }
        }
    }

    private fun Element.toHomeSearchResult(): SearchResponse? {
        //Log.d("mygodcookie",cookies.toString())
        val title = this.selectFirst("a > h3")?.text()?.trim() ?: return null
        //Log.d("title", title)
        val href = fixUrl(this.select("a").attr("href"))
        //Log.d("href", href)
        val posterUrl = fixUrlNull(mainUrl + this.select("div.new-short__poster > a.new-short__poster--link > img").attr("data-src"))
        //Log.d("mygodposterUrl", posterUrl.toString())
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
            posterHeaders = cookies
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchList = querySearchApi(
            query
        ).document

        return searchList.select("article.short-mid").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        //Log.d("Doc", doc.toString())
        val title = doc.selectFirst("h1.fs__title")?.text()?.toString()?.trim()
            ?: return null
        //Log.d("title", title)
        val poster = fixUrlNull(mainUrl + doc.selectFirst("img.fs__poster-img")?.attr("src"))
        //Log.d("poster", poster.toString())
        val tags = doc.select("div.xfs__item--value[itemprop=genre] > a").map { it.text() }
        val yearRegex = Regex("(?<=\\()[\\d(\\]]+(?!=\\))")
        val year = yearRegex.find(title)?.value
            ?.toIntOrNull()
        //Log.d("year", year.toString())
        val description = doc.select("div.fs__descr--text > p").joinToString {
            it.text().trim()
        }
        val isMovie    = tags.filter { it.contains("films", true) }.joinToString()
        val isTvSeries = tags.filter { it.contains("series", true) }.joinToString()
        //val isCartoon  = tags.filter { it.contains("cartoon", true) }.joinToString()
        //Log.d("mybaddesc", mycheckType.toString())
        val type = if (isMovie.contains("films", true)) TvType.Movie
        else if (isTvSeries.contains("series", true)) TvType.TvSeries
        else TvType.Cartoon
        val trailerLink = doc.select("#player > div").map {
            it.select("iframe").attr("src")
        }.filter { it.contains("youtube") }.joinToString()
        val trailer = fixUrlNull(trailerLink)
        //Log.d("mygodtrailer", trailerLink)
        val rating = doc.select("b.imdb__value").text().replace(",", ".").toRatingInt()
        //Log.d("rating", rating.toString())
        val duration =
            doc.select("li.xfs__item_op:nth-child(3) > b").text().removeSuffix(" min.").trim()
                .toIntOrNull()
        //Log.d("dur", duration.toString())
        val actors =
            doc.select("div.xfs__item_op > b[itemprop=actors]").text().split(", ").map {
                ActorData(
                    Actor(it)
                )
            }
        val recommendations = doc.select("li.short-mid").mapNotNull {
            it.toSearchResult()
        }
        val idRegex = Regex("(src:.')+(\\D.*\\d)")
        val id = idRegex.find(doc.select("div.tabs__content script").toString())?.groups?.get(2)?.value
        // Automating awful player domain changes
        val playerScript = "https:" + doc.select("div.tabs__content > script:nth-child(3)").attr("src")
        val domainRegex = Regex("const AwsIndStreamDomain.*'(.*)';")
        playerDomain = domainRegex.find(app.get(playerScript).toString())?.groups?.get(1)?.value
        val embedLink = "$playerDomain/play/$id"
        val jsonReceive = getDlJson(embedLink, url)

        var episodes: List<Episode> = listOf()
        var data = ""
        if (type == TvType.TvSeries) {
            if (jsonReceive.contains("folder", true)) {
                    //Log.d("mybadEmbed", jsonReceive)
                    episodes = parseJson<ArrayList<Seasons>>(jsonReceive).map { Seasons ->
                        val sNum = Seasons.id.toIntOrNull()
                        //Log.d("mybadSnum", Snum.toString())
                        Seasons.folder.map { ep ->
                            val eNum = ep.episode.toIntOrNull()
                            Episode(
                                ep.folder.toJson(),
                                ep.title,
                                sNum,
                                eNum,
                                poster
                            )
                        }
                    }.flatten()
                } else {
                    //Log.d("mybadepisode", jsonReceive)
                    episodes = parseJson<Array<Extract>>(jsonReceive).map {
                        Episode(
                            jsonReceive.toJson(),
                            "1 episode",
                            1,
                            1,
                            poster
                        )
                    }
            }
        } else {
            data = jsonReceive.toJson()
        }

        return when (type) {
            TvType.Movie -> {
                newMovieLoadResponse(title, url, TvType.Movie, data) {
                    this.posterUrl = poster?.trim()
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
            TvType.TvSeries -> {
                newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                    this.posterUrl = poster?.trim()
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
            else -> {
                newMovieLoadResponse(title, url, TvType.Movie, data) {
                    this.posterUrl = poster?.trim()
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
    }

    data class Getfile (
        @JsonProperty("file"       ) var file       : String,
        @JsonProperty("hls"        ) var hls        : Int?   ,
        @JsonProperty("id"         ) var id         : String?,
        @JsonProperty("cuid"       ) var cuid       : String?,
        @JsonProperty("key"        ) var key        : String?,
        @JsonProperty("movie"      ) var movie      : String?,
        @JsonProperty("host"       ) var host       : String?,
        @JsonProperty("masterId"   ) var masterId   : String?,
        @JsonProperty("masterHash" ) var masterHash : String?,
        @JsonProperty("userIp"     ) var userIp     : String?,
        @JsonProperty("poster"     ) var poster     : String?,
        @JsonProperty("href"       ) var href       : String,
        @JsonProperty("p2p"        ) var p2p        : Boolean?,
        @JsonProperty("rek"        ) var rek        : Any?,
        @JsonProperty("autoplay"   ) var autoplay   : Int?   ,
        @JsonProperty("domain"     ) var domain     : Any?,
        @JsonProperty("kp"         ) var kp         : String?,
    )

    data class Extract (
        @JsonProperty("title" ) var title : String?,
        @JsonProperty("id"    ) var id    : String?,
        @JsonProperty("file"  ) var file  : String?
    )

    data class Seasons (
        @JsonProperty("title"  ) var title  : String?,
        @JsonProperty("id"     ) var id     : String,
        @JsonProperty("folder" ) var folder : List<Episodes> = listOf()
    )

    data class Episodes (
        @JsonProperty("episode" ) var episode : String,
        @JsonProperty("title"   ) var title   : String?,
        @JsonProperty("id"      ) var id      : String?,
        @JsonProperty("folder"  ) var folder  : List<Files> = listOf()
    )

    data class Files (
        @JsonProperty("file"    ) var file   : String,
        @JsonProperty("end_tag" ) var endTag : String?,
        @JsonProperty("title"   ) var title  : String?,
        @JsonProperty("id"      ) var id     : String?
    )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        //Log.d("mybadembedlink", data)
        val m3u8Links = parseJson<List<Extract>>(data.replace(Regex("""\[],"""), ""))

        m3u8Links.forEach {
            safeApiCall {
                callback.invoke(
                    ExtractorLink(
                        "AllMovieLand-${it.title}",
                        "AllMovieLand-${it.title}",
                        getM3u8(it.file),
                        playerDomain.toString(),
                        Qualities.Unknown.value,
                        true
                    )
                )
            }
        }

        return true
    }
}
