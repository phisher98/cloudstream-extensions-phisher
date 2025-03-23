package com.KdramaHoodProvider

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.Jsoup

class KdramaHoodProvider : MainAPI() {
    override var mainUrl = "https://kdramahood.com"
    override var name = "KDramaHood"
    override val hasQuickSearch = false
    override val hasMainPage = true
    override val hasChromecastSupport = false
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(TvType.AsianDrama)

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val doc = app.get("$mainUrl/home2").document
        val home = ArrayList<HomePageList>()

        // Hardcoded homepage cause of site implementation
        // Recently added
        val recentlyInner = doc.selectFirst("div.peliculas")
        val recentlyAddedTitle = recentlyInner!!.selectFirst("h1")?.text() ?: "Recently Added"
        val recentlyAdded = recentlyInner.select("div.item_2.items > div.fit.item").mapNotNull {
            val innerA = it.select("div.image > a")
            val link = fixUrlNull(innerA.attr("href")) ?: return@mapNotNull null
            val image = fixUrlNull(innerA.select("img").attr("src"))

            val innerData = it.selectFirst("div.data")
            val title = innerData!!.selectFirst("h1")?.text() ?: return@mapNotNull null
            val year = try {
                val yearText = innerData.selectFirst("span.titulo_o")
                    ?.text()?.takeLast(11)?.trim()?.take(4) ?: ""
                val rex = Regex("\\((\\d+)")
                rex.find(yearText)?.value?.toIntOrNull()
            } catch (e: Exception) {
                null
            }
            newMovieSearchResponse(title, link, TvType.Movie)
            {
                this.year = year
                this.posterUrl = image
            }
        }.distinctBy { it.url }
        home.add(HomePageList(recentlyAddedTitle, recentlyAdded))
        return newHomePageResponse(home.filter { it.list.isNotEmpty() })
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/?s=$query"
        val html = app.get(url).document
        val document = html.getElementsByTag("body")
            .select("div.item_1.items > div.item")

        return document.mapNotNull {
            val innerA = it.selectFirst("div.boxinfo > a") ?: return@mapNotNull null
            val link = fixUrlNull(innerA.attr("href")) ?: return@mapNotNull null
            val title = innerA.select("span.tt").text()

            val year = it.selectFirst("span.year")?.text()?.toIntOrNull()
            val image = fixUrlNull(it.selectFirst("div.image > img")?.attr("src"))
            newMovieSearchResponse(title, link, TvType.Movie)
            {
                this.year = year
                this.posterUrl = image
            }
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val doc = app.get(url).document
        val inner = doc.selectFirst("div.central")

        val title = inner?.selectFirst("h1")?.text() ?: ""
        val poster = fixUrlNull(doc.selectFirst("meta[property=og:image]")?.attr("content")) ?: ""
        val info = inner!!.selectFirst("div#info")
        val descript = inner.selectFirst("div.contenidotv > div > p")?.text()
        val year = try {
            val startLink = "https://kdramahood.com/drama-release-year/"
            var res: Int? = null
            info?.select("div.metadatac")?.forEach {
                if (res != null) {
                    return@forEach
                }
                val yearLink = it.select("a").attr("href")
                if (yearLink.startsWith(startLink)) {
                    res = yearLink.substring(startLink.length).replace("/", "").toIntOrNull()
                }
            }
            res
        } catch (e: Exception) {
            null
        }

        val recs = doc.select("div.sidebartv > div.tvitemrel").mapNotNull {
            val a = it.select("a")
            val aUrl = fixUrlNull(a.attr("href")) ?: return@mapNotNull null
            val aImg = a.select("img")
            val aCover = fixUrlNull(aImg.attr("src")) ?: fixUrlNull(aImg.attr("data-src"))
            val aNameYear = a.select("div.datatvrel")
            val aName = aNameYear.select("h4").text()
            val aYear = aName.trim().takeLast(5).removeSuffix(")").toIntOrNull()
            newMovieSearchResponse(title, aUrl, TvType.Movie)
            {
                this.year = aYear
                this.posterUrl = aCover
            }
        }

        // Episodes Links
        val episodeList = inner.select("ul.episodios > li").mapNotNull { ep ->
            val listOfLinks = mutableListOf<String>()
            val count = ep.select("div.numerando").text().toIntOrNull() ?: 0
            val innerA = ep.select("div.episodiotitle > a")
            val epLink = fixUrlNull(innerA.attr("href")) ?: return@mapNotNull null
            if (epLink.isNotBlank()) {
                // Fetch video links
                val epVidLinkEl = app.get(epLink, referer = mainUrl).document
                val epLinksContent = epVidLinkEl.selectFirst("div.player_nav > script")?.html()
                    ?.replace("ifr_target.src =", "<div>")
                    ?.replace("';", "</div>")
                if (!epLinksContent.isNullOrEmpty()) {
                    Jsoup.parse(epLinksContent).select("div").forEach { em ->
                        val href = em.html().trim().removePrefix("'")
                        val href1 = epVidLinkEl.selectFirst("div.linkstv li a")?.attr("href")
                            ?: return@forEach
                        val sub =
                            epVidLinkEl.selectFirst("div.linkstv li:nth-child(8) a")?.attr("href")
                                ?: return@forEach
                        if (href.isNotBlank()) {
                            listOfLinks.add(fixUrl(href))
                            listOfLinks.add(fixUrl(href1))
                            listOfLinks.add(fixUrl(sub))
                        }
                    }
                }
                //Fetch default source and subtitles
                epVidLinkEl.select("div.embed2").forEach { defsrc ->
                    val scriptstring = defsrc.toString()
                    if (scriptstring.contains("sources: [{")) {
                        "(?<=playerInstance2.setup\\()([\\s\\S]*?)(?=\\);)".toRegex()
                            .find(scriptstring)?.value?.let { itemjs ->
                                listOfLinks.add("$mainUrl$itemjs")
                            }
                    }
                }
            }
            newEpisode(listOfLinks.distinct().toJson())
            {
                this.episode = count
                this.posterUrl = poster
            }
        }

        //If there's only 1 episode, consider it a movie.
        if (episodeList.size == 1) {
            return newMovieLoadResponse(title, url, TvType.Movie, episodeList[0].data)
            {
                this.year = year
                this.plot = descript
                this.posterUrl = poster
                this.recommendations = recs
            }
        }

        return newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodeList.reversed())
        {
            this.year = year
            this.posterUrl = poster
            this.plot = descript
            this.recommendations = recs
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        parseJson<List<String>>(data).amap { item ->
            if (item.contains(".srt")) {
                subtitleCallback.invoke(
                    SubtitleFile(
                        "Subtitle",  // Use label for the name
                        item     // Use extracted URL
                    )
                )
            } else
                if (item.contains(".mp4")) {
                    callback.invoke(
                        ExtractorLink(
                            name,
                            name,
                            item,
                            "",
                            getQualityFromName(""),
                            INFER_TYPE
                        )
                    )
                } else
                    loadExtractor(item, subtitleCallback, callback)
        }
        return true
    }
}