package com.Toonstream

import com.lagradost.api.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class Toonstream : MainAPI() {
    override var mainUrl              = "https://toonstream.one"
    override var name                 = "Toonstream"
    override val hasMainPage          = true
    override var lang                 = "hi"
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.Movie,TvType.Anime,TvType.Cartoon)

    override val mainPage = mainPageOf(
        "series" to "Series",
        "movies" to "Movies",
        "category/cartoon" to "Cartoon",
        "category/anime" to "Animes"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}/page/$page/").documentLarge
        val home     = document.select("#movies-a > ul > li").mapNotNull { it.toSearchResult() }
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
        val title     = this.select("article  > header > h2").text().trim().replace("Watch Online","")
        val href      = fixUrl(this.select("article  > a").attr("href"))
        val posterUrlRaw = this.select("article  > div.post-thumbnail > figure > img").attr("src")
        val poster:String = if (posterUrlRaw.startsWith("http")) { posterUrlRaw } else "https:$posterUrlRaw"
        return newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
            }
    }

    private fun Element.toSearch(): SearchResponse {
        val title     = this.select("article  > header > h2").text().trim().replace("Watch Online","")
        val href      = fixUrl(this.select("article  > a").attr("href"))
        val posterUrlRaw = this.select("article figure img").attr("src")
        val poster:String = if (posterUrlRaw.startsWith("http")) { posterUrlRaw } else "https:$posterUrlRaw"

        return newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = poster
            }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..3) {
            val document = app.get("${mainUrl}/page/$i/?s=$query").documentLarge

            val results = document.select("#movies-a > ul > li").mapNotNull { it.toSearch() }

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
        val document = app.get(url).documentLarge
        val title       = document.selectFirst("header.entry-header > h1")?.text()?.trim().toString().replace("Watch Online","")
        val posterraw = document.select("div.bghd > img").attr("src")
        val poster:String = if (posterraw.startsWith("http")) { posterraw } else "https:$posterraw"
        val description = document.selectFirst("div.description > p")?.text()?.trim()
        val tvtag=if (url.contains("series")) TvType.TvSeries else TvType.Movie
        return if (tvtag == TvType.TvSeries) {
            val episodes = mutableListOf<Episode>()
            document.select("div.aa-drp.choose-season > ul > li > a").forEach { info->
                val data_post=info.attr("data-post")
                val data_season=info.attr("data-season")
                val season=app.post("$mainUrl/wp-admin/admin-ajax.php", data = mapOf(
                    "action" to "action_select_season",
                    "season" to data_season,
                    "post" to data_post
                ), headers = mapOf("X-Requested-With" to "XMLHttpRequest")
                ).documentLarge
                    season.select("article").forEach {
                        val href = it.selectFirst("article >a")?.attr("href") ?:""
                        val posterRaw=it.selectFirst("article > div.post-thumbnail > figure > img")?.attr("src")
                        val poster1="https:$posterRaw"
                        val episode = it.select("article > header.entry-header > h2").text()
                        val seasonnumber=season.toString().substringAfter("<span class=\"num-epi\">").substringBefore("x").toIntOrNull()
                        episodes.add(
                            newEpisode(href)
                            {
                                this.name=episode
                                this.posterUrl=poster1
                                this.season=seasonnumber
                            })
                    }
            }
            newTvSeriesLoadResponse(title, url, TvType.Anime, episodes) {
                this.posterUrl = poster
                this.plot = description
            }
        } else {
            newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
                this.plot = description
            }
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).documentLarge
        document.select("#aa-options > div > iframe").forEach {
            val serverlink=it.attr("data-src")
            val truelink= app.get(serverlink).documentLarge.selectFirst("iframe")?.attr("src") ?:""
            Log.d("Phisher",truelink)
            loadExtractor(truelink,subtitleCallback, callback)
        }
        return true
    }
}

class Zephyrflick : AWSStream() {
    override val name = "Zephyrflick"
    override val mainUrl = "https://play.zephyrflick.top"
    override val requiresReferer = true
}

open class AWSStream : ExtractorApi() {
    override val name = "AWSStream"
    override val mainUrl = "https://z.awstream.net"
    override val requiresReferer = true

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val extractedHash = url.substringAfterLast("/")
        val doc = app.get(url).documentLarge
        val m3u8Url = "$mainUrl/player/index.php?data=$extractedHash&do=getVideo"
        val header = mapOf("x-requested-with" to "XMLHttpRequest")
        val formdata = mapOf("hash" to extractedHash, "r" to mainUrl)
        val response = app.post(m3u8Url, headers = header, data = formdata).parsedSafe<Response>()
        response?.videoSource?.let { m3u8 ->
            callback(
                newExtractorLink(
                    name,
                    name,
                    url = m3u8,
                    type = ExtractorLinkType.M3U8
                ) {
                    this.referer = ""
                    this.quality = Qualities.P1080.value
                }
            )
            val extractedPack = doc.selectFirst("script:containsData(function(p,a,c,k,e,d))")?.data().orEmpty()

            JsUnpacker(extractedPack).unpack()?.let { unpacked ->
                Regex(""""kind":\s*"captions"\s*,\s*"file":\s*"(https.*?\.srt)""")
                    .find(unpacked)
                    ?.groupValues
                    ?.get(1)
                    ?.let { subtitleUrl ->
                        subtitleCallback.invoke(
                            newSubtitleFile(
                                "English",
                                subtitleUrl
                            )
                        )
                    }
            }
        }
    }

    data class Response(
        val hls: Boolean,
        val videoImage: String,
        val videoSource: String,
        val securedLink: String,
        val downloadLinks: List<Any?>,
        val attachmentLinks: List<Any?>,
        val ck: String,
    )
}
