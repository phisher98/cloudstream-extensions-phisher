package com.coxju


import com.google.gson.Gson
import org.jsoup.nodes.Element
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*



class actionviewphotography : MainAPI() {
    override var mainUrl              = "https://actionviewphotography.com"
    override var name                 = "Noodle NSFW"
    override val hasMainPage          = true
    override var lang                 = "hi"
    override val hasQuickSearch       = false
    override val hasDownloadSupport   = true
    override val hasChromecastSupport = true
    override val supportedTypes       = setOf(TvType.NSFW)
    override val vpnStatus            = VPNStatus.MightBeNeeded

    override val mainPage = mainPageOf(
            "video/milf" to "Milf",
            "video/brattysis" to "Brattysis",
            "video/web%20series" to "Web Series",
            "video/japanese" to "Japanese",
            "video/Step" to "Step category",
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}?p=$page").document
        val home     = document.select("#list_videos > div.item").mapNotNull { it.toSearchResult() }

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
        val title     = fixTitle(this.select("div.i_info > div.title").text())
        val href      = fixUrl(this.select("a").attr("href"))
        val posterUrl = fixUrlNull(this.selectFirst("a >div> img")?.attr("data-src")!!.trim())

        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val searchResponse = mutableListOf<SearchResponse>()

        for (i in 1..5) {
            val document = app.get("${mainUrl}/video/$query?p=$i").document
            val results = document.select("#list_videos > div.item").mapNotNull { it.toSearchResult() }
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
        val document = app.get(url).document

        val title       = document.selectFirst("meta[property=og:title]")?.attr("content")?.trim().toString()
        val poster      = fixUrlNull(document.selectFirst("meta[property=og:image]")?.attr("content").toString())
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")?.trim()

        return newMovieLoadResponse(title, url, TvType.NSFW, url) {
            this.posterUrl = poster
            this.plot      = description
        }
    }

    override suspend fun loadLinks(data: String, isCasting: Boolean, subtitleCallback: (SubtitleFile) -> Unit, callback: (ExtractorLink) -> Unit): Boolean {
        val document = app.get(data).document
        val embededurl=document.select("#iplayer").attr("src")
        val properurldoc = app.get(mainUrl+embededurl).document
        val properurldocactual=properurldoc.selectFirst("script:containsData(window.playlistUrl)")?.data().toString()
        // Extracting Base64 encoded string using regex
        val regex = Regex("""window\.playlistUrl='([^']+)';""")
        val matchResult = regex.find(properurldocactual)
        val playlistUrl = matchResult?.groups?.get(1)?.value
        val sourcesurl= app.get(mainUrl+playlistUrl).document
        val links=sourcesurl.body().text().toString().trim()
        val gson = Gson()
        val jsonObject = gson.fromJson(links, Map::class.java)
        val sources = (jsonObject["sources"] as? List<Map<String, Any>>) ?: emptyList()
        sources.forEach { source ->
            val file = source["file"] as? String
            val label = source["label"] as? String
            callback.invoke(
                ExtractorLink(
                    source  = this.name,
                    name    = this.name,
                    url     = file.toString(),
                    referer = data,
                    quality = getQualityFromName(label)

                )
            )
            //println("  File: $file, Label: $label")
        }
        return true
    }
}
