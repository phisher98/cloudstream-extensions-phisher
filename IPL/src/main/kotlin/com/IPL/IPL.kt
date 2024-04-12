package com.HindiProvider

import android.util.Log
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*

class IPL : MainAPI() {
    override var mainUrl              = "https://ipl-2024-live.vercel.app"
    override var name                 = "IPL"
    override val hasMainPage          = true
    override var lang                 = "hi"
    override val hasQuickSearch       = false
    override val hasDownloadSupport   = true
    override val supportedTypes       = setOf(TvType.Live)

    override val mainPage = mainPageOf(
        "/" to "IPL",
    )

    override suspend fun getMainPage(page: Int,request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}").document
        val home     = document.select("#lifestyle-channels > a").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list    = HomePageList(
                name               = request.name,
                list               = home,
                isHorizontalImages = true
            ),
            hasNext = false
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title     = this.select("div > h2").text().trim()
        val href      = fixUrl(this.attr("href"))
        val posterUrl = fixUrlNull(this.select("img").attr("src"))
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }


    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document

        val title= document.selectFirst("center > h4")?.text().toString()
        val poster = "https://pbs.twimg.com/media/GBsB-L-aMAAX4gN.jpg"
        val description = "The Indian Premier League, also known as the TATA IPL for sponsorship reasons, is a men's Twenty20 cricket league held annually in India. Founded by the BCCI in 2007, the league features ten city-based franchise teams. The IPL usually takes place during the summer, between March and May each year."

        return newMovieLoadResponse(title, url, TvType.Live, url) {
            this.posterUrl = poster
            this.plot      = description
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).document
       if (data.contains("m3u"))
       {
           Log.d("Test m3U",data)
           callback.invoke(
               ExtractorLink(
                   source = this.name,
                   name = this.name,
                   url = data,
                   referer = "",
                   quality = Qualities.Unknown.value,
                   isM3u8 = true
               )
           )
       }
        else {
           val streamlink=document.selectFirst("script:containsData(file)")?.data().toString().substringAfter("\"file\": \"").substringBefore("\",")
           Log.d("Test stream",streamlink)
           callback.invoke(
                   ExtractorLink(
                       source = this.name,
                       name = this.name,
                       url = streamlink,
                       referer = "",
                       quality = Qualities.Unknown.value,
                       isM3u8 = true
                   )
               )
       }
        return true
    }
}
