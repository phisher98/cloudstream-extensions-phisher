package com.likdev256

import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import org.jsoup.nodes.Element

class AllMovieLandProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://allmovieland.com"
    override var name = "AllMovieLand"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie
    )

    override val mainPage = mainPageOf(
        "$mainUrl/films/" to "Movies",
        "$mainUrl/bollywood/" to "Bollywood Movies",
        "$mainUrl/hollywood/" to "Hollywood Movies",
        "$mainUrl/series/" to "TV Shows",
        "$mainUrl/cartoons/" to "Cartoons"
    )

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
        val home = document.select("article.short-mid").mapNotNull {
                    it.toSearchResult()
                }

        return HomePageResponse(arrayListOf(HomePageList(request.name, home)), hasNext = true)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        //Log.d("Got","got here")
        val title = this.selectFirst("a > h3")?.text()?.trim() ?: return null
        //Log.d("title", title)
        val href = fixUrl(this.select("a").attr("href"))
        //Log.d("href", href)
        val posterUrl = fixUrlNull(mainUrl + this.select("div.new-short > a.new-short > img.new-short").attr("src"))
        //Log.d("posterUrl", posterUrl.toString())
        return newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/?s=$query").document
        //Log.d("document", document.toString())

        return document.select("ul.recent-posts > li").mapNotNull {
                it.toSearchResult()
        }.filter { document.select("div.post-content > h2 > a")
            .text().trim()
            .contains(Regex("(?i)EP\\\\s?[0-9]+|Episode\\\\s?[0-9]+|Season..|season")).not() }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document
        //Log.d("Doc", doc.toString())
        val title = doc.selectFirst("h1.fs__title")?.text()?.toString()?.trim()
            ?: return null
        //Log.d("titleL", titleL)
        //Log.d("title", title)
        val poster = fixUrlNull(doc.selectFirst("div.poster > img")?.attr("src"))
        //Log.d("poster", poster.toString())
        val tags = doc.select("div.xfs__item--value[itemprop=genre] > a").map { it.text() }
        val year =
            doc.selectFirst("span.date")?.text()?.toString()?.substringAfter(",")?.trim()?.toInt()
        //Log.d("year", year.toString())
        val description = doc.selectFirst("div.wp-content > p > span")?.text()?.trim()
        val type = if (doc.select("li.fs__info_top--li:nth-child(3)").text().contains("pg", true)) TvType.Movie else TvType.TvSeries
        //Log.d("desc", description.toString())
        val trailerLink = doc.select("#player > div.tabs").map {
            it.select("iframe").attr("src")
        }.filter { it.contains("youtube") }
        val trailer = fixUrlNull(
                getEmbed(trailerLink)
            )
        //Log.d("trailer", trailer.toString())
        val rating = doc.select("b.imdb__value").text().replace(",", ".").toRatingInt()
        //Log.d("rating", rating.toString())
        val duration =
            doc.select("li.xfs__item_op:nth-child(3) > b")?.text()?.toString()?.removeSuffix(" min.")?.trim()
                ?.toInt()
        //Log.d("dur", duration.toString())
        val actors =
            doc.select("div.person").map {
                ActorData(
                    Actor(
                        it.select("div.xfs__item_op > b[itemprop=actors]").text().toString()
                    )
                )
            }
        val recommendations = doc.select("li.short-mid").mapNotNull {
            it.toSearchResult()
        }

        /*val episodes = ArrayList<Episode>()
        doc.select("section.container > div.border-b").forEach { me ->
            val seasonNum = me.select("button > span").text()
            me.select("div.season-list > a").forEach {
                episodes.add(
                    Episode(
                        data = mainUrl + it.attr("href").toString(),
                        name = it.ownText().toString().removePrefix("Episode ").substring(2),//.replaceFirst(epName.first().toString(), ""),
                        season = titRegex.find(seasonNum)?.value?.toInt(),
                        episode = titRegex.find(it.select("span.flex").text().toString())?.value?.toInt()
                    )
                )
            }
        }*/

        return if (type == TvType.Movie) {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
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
                addTrailer(trailer?.toString())
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

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val url  = data.substringBefore(",")
        val link = data.substringAfter(",")
        Log.d("mygodembedlink", link)

        val doc = app.get(link, referer = url).document
        val jsonString = Regex("""\{.*\}""").find(doc.select("body > script:last-child").toString())?.value.toString()
        Log.d("mygoddoc", doc.toString())
        Log.d("mygoddoc", jsonString)
        val json = tryParseJson<Getfile>(jsonString)
        //Log.d("mygodjson1", json.toString())
        Log.d("mygodjson2", "https://${json?.href}/playlist/${json?.file}.txt")
        val m3u8Link = app.post(
            "https://${json?.href}/playlist/${json?.file}.txt",
            referer = link,
            headers = mapOf("Content-Type" to "application/x-www-form-urlencoded",),
            data = mapOf(
                "id" to json?.id.toString(),
                "cuid" to json?.cuid.toString(),
                "key" to json?.key.toString(),
                "movie" to json?.movie.toString(),
                "host" to json?.host.toString(),
                "masterId" to json?.masterId.toString(),
                "masterHash" to json?.masterHash.toString(),
            )
        ).toString()
        Log.d("mygodjson1", m3u8Link)

        safeApiCall {
            callback.invoke(
                ExtractorLink(
                    "KatMovies",
                    "KatMovies-HD",
                    m3u8Link,
                    "https://${json?.href}",
                    Qualities.Unknown.value,
                    true
                )
            )
        }

        return true
    }
}
