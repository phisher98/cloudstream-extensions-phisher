package com.Tennistream


import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import java.net.URI

class Tennistream : MainAPI() {
    override var mainUrl              = "https://tennistream.com"
    override var name                 = "Tennistream"
    override val hasMainPage          = true
    override var lang                 = "en"
    override val supportedTypes       = setOf(TvType.Live)

    override val mainPage = mainPageOf(
        "live-all-channels" to "All Channels",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}").documentLarge
        val home     = document.select("div.entry-content.cf p a").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list    = HomePageList(
                name               = request.name,
                list               = home,
                isHorizontalImages = true
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title     = this.text()
        val href      = fixUrl(this.attr("href"))
        val posterUrl = "https://img.freepik.com/premium-photo/international-sports-day-6-april_10221-18992.jpg"
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()
        return searchResponse
    }

    override suspend fun load(url: String): LoadResponse {
        val title= "Tennistream"
        val poster = "https://tennistream.com/wp-content/uploads/2024/01/cropped-AO_Nine_Partnership-1-778x438.png"
        val description="Tennis Live Stream THURSDAY, SEPTEMBER 5 >> WATCH ALL STREAMS<<  WTA – US OPEN 02:00 Navarro E. vs Sabalenka A. SuperSport  ESPN  Sky Sports 03:30 Pegula J. vs Muchova K. SuperSport  ESPN  Sky Sports ATP DOUBLES – US OPEN 23:00 Purcell M./Thompson J. vs Lammons N./Withrow J. WATCH 00:30 Arevalo M./Pavic M. vs Krawietz K./Puetz T. WATCH […]"
        return newMovieLoadResponse(title, url, TvType.Movie, url) {
                this.posterUrl = poster
            this.plot=description
            }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).documentLarge
       document.select("p a").amap {
           it.attr("href").let { href ->
               val link=app.get(href).documentLarge.selectFirst("iframe")?.attr("src") ?:""
               var trueurl=app.get(link).documentLarge.selectFirst("iframe")?.attr("src") ?:""
               if (trueurl.isEmpty())
               {
                   val fid=app.get(link).text.substringAfter("fid=\"").substringBefore("\"")
                   val url="https://freshwaterdell.com/wiki.php?player=desktop&live=$fid"
                   trueurl= httpsify(app.get(url, referer = "https://wikisport.best/").text.substringAfter("return([").substringBefore("].join").replace("\"","").replace(",",""))
               }
               if (trueurl.contains("quest4play"))
               {
                   loadExtractor(trueurl,referer = getBaseUrl(link),subtitleCallback, callback)
               }
               else if (trueurl.contains("m3u8"))
               {
                   callback.invoke(
                       newExtractorLink(
                           "Server 2",
                           "Server 2",
                           url = trueurl,
                           ExtractorLinkType.M3U8
                       ) {
                           this.referer = "https://freshwaterdell.com"
                           this.quality = Qualities.P1080.value
                       }
                   )
               }
               else
               {
                   loadExtractor(trueurl,subtitleCallback, callback)
               }
           }
       }
        return true
    }

    private fun getBaseUrl(url: String): String {
        return URI(url).let {
            "${it.scheme}://${it.host}"
        }
    }
}
