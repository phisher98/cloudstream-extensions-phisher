package com.anineko

import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.addEpisodes
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.addDubStatus
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.amap

class Anineko : MainAPI() {
    override var mainUrl = "https://anineko.to"
    override var name = "Anineko"
    override val hasMainPage = true
    override var lang = "en"
    override val hasDownloadSupport = true
    override val supportedTypes = setOf(
        TvType.Anime,
        TvType.AnimeMovie,
        TvType.OVA
    )

    override val mainPage = mainPageOf(
        "/new-releases" to "New Releases",
        "/updates" to "Latest Updates",
        "/ongoing" to "Ongoing",
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val url = "$mainUrl${request.data}?page=$page"
        val doc = app.get(url).document
        
        val list = doc.select(".nv-anime-card").mapNotNull { element ->
            val href = element.selectFirst("a.nv-anime-thumb")?.attr("href") ?: return@mapNotNull null
            val title = element.selectFirst("h3.nv-anime-title a")?.text() 
                ?: element.selectFirst("img")?.attr("alt") 
                ?: return@mapNotNull null
            val posterUrl = element.selectFirst("img")?.attr("src")
            
            val subCount = element.selectFirst(".nv-stat-cc")?.text()?.replace(Regex("[^0-9]"), "")?.toIntOrNull()
            val dubCount = element.selectFirst(".nv-stat-dub span")?.text()?.replace(Regex("[^0-9]"), "")?.toIntOrNull()

            newAnimeSearchResponse(title, "$mainUrl$href", TvType.Anime) {
                this.posterUrl = posterUrl
                addDubStatus(
                    dubExist = dubCount != null,
                    subExist = subCount != null,
                    dubEpisodes = dubCount,
                    subEpisodes = subCount
                )
            }
        }
        
        return newHomePageResponse(request.name, list)
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val url = "$mainUrl/browser?keyword=${query}"
        val doc = app.get(url).document
        
        return doc.select(".nv-anime-card").mapNotNull { element ->
            val href = element.selectFirst("a.nv-anime-thumb")?.attr("href") ?: return@mapNotNull null
            val title = element.selectFirst("h3.nv-anime-title a")?.text() 
                ?: element.selectFirst("img")?.attr("alt") 
                ?: return@mapNotNull null
            val posterUrl = element.selectFirst("img")?.attr("src")
            
            val subCount = element.selectFirst(".nv-stat-cc")?.text()?.replace(Regex("[^0-9]"), "")?.toIntOrNull()
            val dubCount = element.selectFirst(".nv-stat-dub span")?.text()?.replace(Regex("[^0-9]"), "")?.toIntOrNull()

            newAnimeSearchResponse(title, "$mainUrl$href", TvType.Anime) {
                this.posterUrl = posterUrl
                addDubStatus(
                    dubExist = dubCount != null,
                    subExist = subCount != null,
                    dubEpisodes = dubCount,
                    subEpisodes = subCount
                )
            }
        }
    }

    override suspend fun load(url: String): LoadResponse? {
        val doc = app.get(url).document

        val title = doc.selectFirst("h1")?.text() ?: return null
        val altTitle = doc.selectFirst(".nv-info-alt-title")?.text()
        val poster = doc.selectFirst("aside.nv-info-poster img")?.attr("src")
        
        val bgStyle = doc.selectFirst(".nv-info-bg")?.attr("style")
        val background = bgStyle?.let { Regex("""url\(['"]?(.*?)['"]?\)""").find(it)?.groupValues?.get(1) }
        
        val plot = doc.selectFirst("p.nv-info-desc")?.text()

        val tags = doc.select(".nv-info-tags span").map { it.text() }
        val year = doc.selectFirst(".nv-info-stats div:contains(Release) strong")?.text()?.toIntOrNull()
        val typeText = doc.selectFirst(".nv-info-stats div:contains(Type) strong")?.text()

        val tvType = when {
            typeText?.contains("Movie", true) == true -> TvType.AnimeMovie
            typeText?.contains("OVA", true) == true -> TvType.OVA
            else -> TvType.Anime
        }

        val statusText = doc.selectFirst(".nv-info-stats div:contains(Status) strong")?.text()
        val showStatus = when {
            statusText?.contains("Currently Airing", true) == true -> ShowStatus.Ongoing
            statusText?.contains("Completed", true) == true -> ShowStatus.Completed
            else -> null
        }

        val searchTitle = altTitle ?: title
        val anilistId = getAnilistId(searchTitle)
        var animeMetaData: MetaAnimeData? = null
        if (anilistId != null) {
            val aniZipUrl = "https://api.ani.zip/mappings?anilist_id=$anilistId"
            val aniZipResponse = app.get(aniZipUrl).text
            animeMetaData = parseAnimeData(aniZipResponse)
        }

        val subEpisodes = mutableListOf<Episode>()
        val dubEpisodes = mutableListOf<Episode>()

        doc.select(".nv-info-episode-item").forEach { ep ->
            val epHref = ep.selectFirst("a.nv-info-episode-main")?.attr("href") ?: return@forEach
            val epName = ep.selectFirst("a.nv-info-episode-main strong")?.text()
            val epNum = epName?.replace(Regex("[^0-9]"), "")?.toIntOrNull()

            val metaEp = animeMetaData?.episodes?.get(epNum?.toString())
            val finalName = metaEp?.title?.get("en") 
                ?: metaEp?.title?.get("x-jat") 
                ?: metaEp?.title?.get("ja") 
                ?: animeMetaData?.titles?.get("en")
                ?: animeMetaData?.titles?.get("x-jat")
                ?: epName
            val description = metaEp?.overview ?: "No summary available"
            val thumbnail = metaEp?.image ?: animeMetaData?.images?.firstOrNull()?.url ?: ""
            val rating = metaEp?.rating
            val runtime = metaEp?.runtime
            val airDate = metaEp?.airDateUtc

            val badges = ep.select(".nv-info-episode-badges span").map { it.text().lowercase() }
            val hasSub = badges.contains("sub") || badges.contains("hsub") || badges.contains("hardsub")
            val hasDub = badges.contains("dub")

            if (hasSub || (!hasDub)) {
                subEpisodes.add(newEpisode("$mainUrl$epHref|sub") {
                    this.name = finalName
                    this.episode = epNum
                    this.description = description
                    this.posterUrl = thumbnail
                    this.score = com.lagradost.cloudstream3.Score.from10(rating)
                    this.runTime = runtime
                    addDate(airDate)
                })
            }
            if (hasDub) {
                dubEpisodes.add(newEpisode("$mainUrl$epHref|dub") {
                    this.name = finalName
                    this.episode = epNum
                    this.description = description
                    this.posterUrl = thumbnail
                    this.score = com.lagradost.cloudstream3.Score.from10(rating)
                    this.runTime = runtime
                    addDate(airDate)
                })
            }
        }

        val fanartUrl = animeMetaData?.images?.firstOrNull { it.coverType == "Fanart" }?.url ?: background

        return newAnimeLoadResponse(title, url, tvType) {
            this.posterUrl = poster
            this.backgroundPosterUrl = fanartUrl
            this.year = year
            this.plot = plot
            this.tags = tags
            this.showStatus = showStatus
            if (anilistId != null) {
                addAniListId(anilistId)
            }
            if (subEpisodes.isNotEmpty()) addEpisodes(DubStatus.Subbed, subEpisodes)
            if (dubEpisodes.isNotEmpty()) addEpisodes(DubStatus.Dubbed, dubEpisodes)
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val parts = data.split("|")
        val url = parts[0]
        val audioType = parts.getOrNull(1) ?: "sub"

        val doc = app.get(url).document

        val panels = doc.select(".nv-server-grid")
        val targetPanels = if (panels.isNotEmpty()) {
            panels.filter {
                val dataId = it.attr("data-id").lowercase()
                if (audioType == "dub") dataId.contains("dub") else !dataId.contains("dub")
            }
        } else {
            listOf(doc)
        }

        targetPanels.amap { panel ->
            panel.select(".server-video").amap { serverBtn ->
                val videoUrl = serverBtn.attr("data-video")
                val serverName = serverBtn.ownText().trim()
                val typeName = serverBtn.selectFirst("span")?.text()

                val subMatch = Regex("""(?:sub|caption_1|c1_file)=([^&]+)""").find(videoUrl)
                if (subMatch != null) {
                    val subUrl = subMatch.groupValues[1]
                    val subLang = Regex("""(?:sub_1|c1_label)=([^&]+)""").find(videoUrl)?.groupValues?.get(1) ?: "English"
                    subtitleCallback.invoke(newSubtitleFile(subLang, subUrl))
                }

            val finalUrl = if (videoUrl.startsWith("//")) "https:$videoUrl" else videoUrl
            val embedDoc = app.get(finalUrl, headers = mapOf("Referer" to "$mainUrl/")).text

            val hlsRegexes = listOf(
                Regex("""const\s+src\s*=\s*["'](https?://[^"']+\.m3u8[^"']*)["']""", RegexOption.IGNORE_CASE),
                Regex("""file\s*:\s*["'](https?://[^"']+\.m3u8[^"']*)["']""", RegexOption.IGNORE_CASE),
                Regex("""["'](https?://[^"']+/master\.m3u8[^"']*)["']""", RegexOption.IGNORE_CASE),
                Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']""", RegexOption.IGNORE_CASE)
            )

            var m3u8Url: String? = null
            for (regex in hlsRegexes) {
                val match = regex.find(embedDoc)
                if (match != null) {
                    m3u8Url = match.groupValues[1]
                    break
                }
            }

                if (m3u8Url != null) {
                    val sourceName = if (typeName != null) "$serverName - $typeName" else serverName
                    generateM3u8(
                        sourceName,
                        m3u8Url,
                        finalUrl
                    ).forEach(callback)
                } else if (serverName.contains("HD-")) {
                    val host = Regex("""https?://([^/]+)""").find(finalUrl)?.groupValues?.get(1) ?: ""
                    val extractor = object : StreamWishExtractor() {
                        override var mainUrl = "https://$host"
                        override var name = serverName
                    }
                    val links = mutableListOf<ExtractorLink>()
                    extractor.getUrl(finalUrl, "$mainUrl/", subtitleCallback) { link ->
                        links.add(link)
                    }
                    links.forEach { link ->
                        val newLink = newExtractorLink(
                            source = link.source,
                            name = link.name + if (typeName != null) " - $typeName" else "",
                            url = link.url,
                            type = if (link.isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        ) {
                            this.quality = link.quality
                            this.headers = link.headers
                            this.extractorData = link.extractorData
                        }
                        callback.invoke(newLink)
                    }
                } else {
                    val links = mutableListOf<ExtractorLink>()
                    loadExtractor(finalUrl, "$mainUrl/", subtitleCallback) { link ->
                        links.add(link)
                    }
                    links.forEach { link ->
                        val newLink = newExtractorLink(
                            source = link.source,
                            name = link.name + if (typeName != null) " - $typeName" else "",
                            url = link.url,
                            type = if (link.isM3u8) ExtractorLinkType.M3U8 else ExtractorLinkType.VIDEO
                        ) {
                            this.quality = link.quality
                            this.headers = link.headers
                            this.extractorData = link.extractorData
                        }
                        callback.invoke(newLink)
                    }
                }
            }
        }

        return true
    }
}
