package com.kayifamilytv

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.*
import org.jsoup.nodes.Element

class KayiFamilyTv: MainAPI() {
    override var mainUrl = "https://kayifamilytv.com/v18"
    override var name = "KayiFamily"
    override val hasMainPage = true
    override var lang = "en"
    override val hasQuickSearch = true
    override val hasDownloadSupport = true
    override val supportedTypes = setOf( TvType.TvSeries, TvType.Movie, TvType.Documentary )

    override val mainPage = mainPageOf(
        "/turkish-historical-tv-shows" to "Latest Episodes",
        "/family-series" to "Family Series",
        "/documentaries" to "Documentaries",
        "/videos" to "Videos"
    )

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = if(page == 1) "$mainUrl${request.data}" else "$mainUrl${request.data}/page/$page/"

        val document = app.get(url).document

        val home = buildList {
            if (page == 1) {
                document.selectFirst("div.mvp-widget-feat2-left")
                    ?.extractSearchResult(
                        titleSelector = "h2.mvp-stand-title",
                        imageSelector = "img.wp-post-image"
                    )?.let(::add)

                addAll(
                    document.select("div.mvp-widget-feat2-right-cont")
                        .mapNotNull { it.extractSearchResult() }
                )
            }

            addAll(
                document.select("li.mvp-blog-story-col")
                    .mapNotNull { it.extractSearchResult() }
            )
        }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = true
            ),
            hasNext = true
        )
    }

    private fun Element.extractSearchResult(
        titleSelector: String = "h2",
        imageSelector: String = "img.mvp-reg-img",
    ): SearchResponse? {
        val link = selectFirst("a") ?: return null

        val href = fixUrl(link.attr("href"))
        val title = selectFirst(titleSelector)?.text()?.trim().orEmpty()

        val img = selectFirst(imageSelector)
        val poster = img?.attr("data-src")?.ifEmpty { img.attr("src") }.orEmpty()

        return newMovieSearchResponse(title, href, TvType.Movie) {
            posterUrl = poster
        }
    }

    private fun Element.toSearchResult(): SearchResponse? {
        // For search results - exclude NEWS category
        val categoryElement = this.selectFirst("span.mvp-cd-cat")
        val category = categoryElement?.text()?.trim()?.uppercase() ?: ""

        // Skip NEWS items
        if (category == "NEWS") {
            return null
        }

        val linkElement = this.selectFirst("a")
        if (linkElement != null) {
            val href = fixUrl(linkElement.attr("href"))
            val title = this.selectFirst("h2")?.text()?.trim() ?: ""
            val imgElement = this.selectFirst("img.mvp-reg-img")
            val posterUrl = imgElement?.attr("data-src")?.ifEmpty { imgElement.attr("src") } ?: ""

            return newMovieSearchResponse(title, href, TvType.Movie) {
                this.posterUrl = posterUrl
            }
        }
        return null
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl?s=$query").document
        return document.select("li.mvp-blog-story-col").mapNotNull {
            it.toSearchResult()
        }
    }

    override suspend fun load(url: String): LoadResponse {
        val document = app.get(url).document
        val title = document.selectFirst("h1")?.text() ?: document.selectFirst("title")?.text() ?: "Unknown Title"
        val posterUrl = document.selectFirst("meta[property=og:image]")?.attr("content") ?: ""
        val description = document.selectFirst("meta[property=og:description]")?.attr("content")
        val year = document.select("span.year").text().trim().toIntOrNull()

        return newMovieLoadResponse(title, url, TvType.Movie, url) {
            this.posterUrl = posterUrl
            this.plot = description
            this.year = year
        }
    }

    private val blockedIframeRegex = Regex("googletagmanager|analytics|adsbygoogle|donorbox\\.org")

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
         val document = app.get(data).document

         document.select("#mvp-content-wrap iframe").forEach { iframe ->
             val iframeUrl = sequenceOf(
                 iframe.attr("data-src"),
                 iframe.attr("data-litespeed-src"),
                 iframe.attr("src")
             ).map(String::trim).firstOrNull { url ->
                 url.isNotEmpty() &&
                 !url.startsWith("about:") &&
                 !url.startsWith("data:") &&
                 !blockedIframeRegex.containsMatchIn(url)
             }

             iframeUrl?.let { url ->
                 if( url.contains("videa.hu")) println("Videa.hu Found: $url")
                 loadExtractor(
                     if (url.startsWith("//")) "https:$url" else url,
                     data, subtitleCallback, callback
                 )
             }
         }
         return true
    }
}