package com.KimCartoon

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.extractors.Vidguardto

class KimCartoon : MainAPI() {
    override var mainUrl = "https://kimcartoon.li"
    override var name = "KimCartoon"
    override val supportedTypes = setOf(
        TvType.Cartoon
    )

    override var lang = "en"

    // enable this when your provider has a main page
    override val hasMainPage = true

    override val mainPage = mainPageOf(
        Pair(mainUrl, "Latest update"),
        Pair("tab-top-day", "Top day"),
        Pair("tab-top-week", "Top week"),
        Pair("tab-top-month", "Top month"),
        Pair("tab-newest-series", "New cartoons")
    )

    private data class NewSearchItem (
        val name: String,
        val url: String,
        val type: TvType,
        val posterurl: String?
    )

    private fun checkSelector(selector: Set<String>): String? {
        selector.forEach { check ->
            if (check.isNotEmpty()) {
                return check
            }
        }

        return null
    }

    private fun Element.toSearchResponse(name: String) : SearchResponse {
        val items = mutableListOf<NewSearchItem>()
        val lname = name.lowercase()
        if (lname.contains("latest update")) {
            val url = fixUrlNull(this.attr("href")) ?: ""
            val poster = checkSelector(
                setOf(
                    this.select("> img").attr("src"),
                    this.select("> img").attr("srctemp")
                )
            )
            val posterUrl = fixUrlNull(poster)
            val title = if (lname.contains("search")) this.selectFirst("span.title")?.text() ?: ""
            else this.selectFirst("div.item-title")?.ownText() ?: ""

            items.add(NewSearchItem(title, url, TvType.Cartoon, posterUrl))

        }
        else {
            val url = fixUrlNull(this.select("a").attr("href")) ?: ""
            val poster = checkSelector(
                setOf(
                    this.select("a img").attr("src"),
                    this.select("a img").attr("srctemp")
                )
            )
            val posterUrl = fixUrlNull(poster)
            val title = this.select("a:nth-child(2) span.title").text()

            items.add(NewSearchItem(title, url, TvType.Cartoon, posterUrl))
        }

        return newAnimeSearchResponse(
            name = items[0].name,
            url = items[0].url,
            type = items[0].type
        ) {
            this.posterUrl = items[0].posterurl
        }
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val list = mutableListOf<HomePageList>()
        if (page <= 1) {
            try {
                if (request.name == "Latest update") {
                    val html = app.get(request.data).document
                    val soup = html.select("div#container")
                    val items = soup.select("div.bigBarContainer div.items > div > a").mapNotNull { item ->
                        item.toSearchResponse("Latest update")
                    }

                    list.add(HomePageList(request.name, items))
                } else {
                    val html = app.get(mainUrl).document
                    val divName = request.data
                    val soup = html.select("div#container")
                    val items = soup.select("div#subcontent div#$divName > div").mapNotNull { item ->
                        item.toSearchResponse(divName)
                    }

                    list.add(HomePageList(request.name, items))
                }

            } catch (e: Exception) {
                throw Error("$e")
            }
        }

        return newHomePageResponse(
            list = list,
            hasNext = false
        )
    }

    override suspend fun load(url: String): LoadResponse {
        try {
            val html = app.get(url).document
            val soup = html.select("div#container")
            val title = soup.select("div.barContent > div:nth-child(2) > a").text()
            val tags = soup.select("div.barContent > div:nth-child(2) > p:nth-child(8) > a")
                .mapNotNull { it.text() }
            val episodes = soup.select("div.barContent table.listing > tbody td a").reversed().mapNotNull {
                Episode(
                    data = fixUrl(it.attr("href")),
                    name = it.text()
                )
            }
            val plot = checkSelector(
                setOf(
                    soup.select("div.barContent > div:nth-child(2) > p:nth-child(6)").text()
                )
            )
            val poster = checkSelector(
                setOf(
                    soup.select("div.barContent > div > img").attr("src")
                )
            )
            val posterUrl = fixUrlNull(poster)

            return newTvSeriesLoadResponse(
                name = title,
                url = url,
                type = TvType.Cartoon,
                episodes
            ) {
                this.plot = plot.toString()
                this.posterUrl = posterUrl
                this.tags = tags
            }

        } catch (e: Exception) {
            throw Error("$e")
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val html = app.get(data).document
        val soup = html.select("div#container")
        val servers = soup.select("div.barContent select#selectServer option").mapNotNull {
            fixUrlNull(it.attr("value"))
        }
        servers.amap {
                val link = app.get(it).document.select("div#container div.barContent iframe#my_video_1")
                    .attr("src")
                loadExtractor(link,subtitleCallback, callback)
        }
        return true
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val html = app.post("$mainUrl/Search/Cartoon", data = mapOf("keyword" to query)).document
        return html.select("div#container div.barContent div.list-cartoon > div.item > a")
            .mapNotNull { item ->
            item.toSearchResponse("latest update|search")
        }
    }
}

class Bembed : Vidguardto() {
    override var name = "Bebed"
    override var mainUrl = "https://bembed.net"
}


class Listeamed : Vidguardto() {
    override var name = "Listeamed"
    override var mainUrl = "https://listeamed.net"
}

class streamwish : StreamWishExtractor() {
    override var name = "StreamWish"
    override var mainUrl = "https://streamwish.to"
}
