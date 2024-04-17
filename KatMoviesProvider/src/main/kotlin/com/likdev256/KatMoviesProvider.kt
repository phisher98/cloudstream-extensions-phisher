package com.likdev256

//import android.util.Log
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.mvvm.safeApiCall
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import org.jsoup.nodes.Element

class KatMoviesProvider : MainAPI() { // all providers must be an instance of MainAPI
    override var mainUrl = "https://katmoviehd.tf"
    override var name = "KatMovies"
    override val hasMainPage = true
    override var lang = "ta"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Movie
    )

    override val mainPage = mainPageOf(
        "$mainUrl/category/hollywood-eng/" to "Hollywood Movies",
        "$mainUrl/category/dubbed-movie/" to "Dubbed Movies"
    )

    private var tokenKey: String? = ""

    private suspend fun getMovieDl(link: String, url: String): String {
        val doc = app.get(link, referer = url).document
        val jsonString = Regex("""\{.*\}""").find(
            doc.select("body > script:last-child").toString()
        )?.value.toString()
        //Log.d("mybaddoc", doc.toString())
        //Log.d("mybaddoc", jsonString)
        val json = parseJson<Getfile>(jsonString)
        //Log.d("mybadjson1", json.toString())
        //Log.d("mybadjson2", "https://${json.href}/playlist/${json.file}.txt")
        tokenKey = json.key
        //Log.d("mybadtoken", tokenKey.toString())
        return app.post(
            "https://${json.href}/playlist/${json.file}.txt",
            referer = link,
            headers = mapOf(
                "X-CSRF-TOKEN" to "$tokenKey",
            ),
        ).toString()
    }

    private suspend fun getM3u8(file: String?): String {
        return app.post(
            "https://advise-shine-i-206.site/playlist/$file.txt",
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
        val document = if (page == 1) {
            app.get(request.data).document
        } else {
            app.get(request.data + "/page/$page/").document
        }

        //Log.d("Document", request.data)
        val home = document.select("ul.recent-posts > li").mapNotNull {
                    it.toSearchResult()
                }/*.filter { document.select("ul.recent-posts > li > div.post-content > h2 > a")
                        .text().trim()
                        .contains(Regex("(?i)EP\\\\s?[0-9]+|Episode\\\\s?[0-9]+|Season..|season")).not() }*/

        return HomePageResponse(arrayListOf(HomePageList(request.name, home)), hasNext = true)
    }

    private fun Element.toSearchResult(): SearchResponse? {
        //Log.d("Got","got here")
        val title = this.selectFirst("div.post-content > h2 > a")?.text()?.replace("watch", "", true)?.trim() ?: return null
        //Log.d("title", title)
        val href = fixUrl(this.selectFirst("div.post-content > h2 > a")?.attr("href").toString())
        //Log.d("href", href)
        val posterUrl = fixUrlNull(this.selectFirst("div.post-thumb > a > img")?.attr("src"))
        //Log.d("posterUrl", posterUrl.toString())
        //Log.d("QualityN", qualityN)
        val quality = getQualityFromString(this.select("div.poster > div.mepo > span").text().toString())
        //Log.d("Quality", quality.toString())
        return newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
                this.quality = quality
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

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        //Log.d("Doc", doc.toString())
        var title = ""
        var tags = listOf("")
        var description: String? = ""
        var rating: Int? = 0
        var actors: List<ActorData> = listOf(ActorData(Actor("")))
        doc.select("div[data-ved] ul > li").forEach {
            //Log.d("mybadit", it.toString())
            if (it.text().contains("Movie Name")) {
                title = it.ownText().trim()
            } else if (it.text().contains("Genres:")) {
                tags = it.select("a").map { me -> me.text() }
            } else if (it.text().contains("Language:")) {
                description = it.ownText().trim()
            } else if (it.text().contains("IMDb Rating:")) {
                rating = it.select("a").text().substringBefore("/").toRatingInt()
            } else if (it.text().contains("Stars:")) {
                actors = it.ownText().split(", ").map { me ->
                    ActorData(
                        Actor(me)
                    )
                }
            }
        }
        val yearRegex = Regex("(?<=\\()[\\d(\\]]+(?!=\\))")
        val year = yearRegex.find(title)?.value
                ?.toIntOrNull()
        val poster = doc.select("img.aligncenter").attr("src").trim()
        val embedLink = doc.select("h4 > iframe").attr("src")
        return newMovieLoadResponse(title, url, TvType.Movie, "$url,$embedLink") {
                this.posterUrl = poster.trim()
                this.year = year
                this.plot = description
                this.tags = tags
                this.rating = rating
                this.actors = actors
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
        //Log.d("mybadembedlink", link)
        //Log.d("mybadgetDl", getMovieDl(link, url))

        //Log.d("mybadembedM3u8", getM3u8(it.file))
        safeApiCall {
            callback.invoke(
                ExtractorLink(
                    "KatMovies",
                    "KatMovies",
                    getMovieDl(link, url),
                    "https://advise-shine-i-206.site",
                    Qualities.P1080.value,
                    true
                )
            )
        }

        return true
    }
}
