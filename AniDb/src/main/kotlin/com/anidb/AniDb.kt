package com.anidb

import com.lagradost.cloudstream3.AnimeSearchResponse
import com.lagradost.cloudstream3.DubStatus
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addAniListId
import com.lagradost.cloudstream3.LoadResponse.Companion.addMalId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponseList
import com.lagradost.cloudstream3.ShowStatus
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.addDate
import com.lagradost.cloudstream3.addEpisodes
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.newAnimeLoadResponse
import com.lagradost.cloudstream3.newAnimeSearchResponse
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.toNewSearchResponseList
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper.Companion.generateM3u8
import com.lagradost.cloudstream3.utils.loadExtractor
import org.jsoup.nodes.Document

class AniDb : MainAPI() {
    override var mainUrl = "https://anidb.app"
    override var name = "AniDB"
    override var supportedTypes = setOf(TvType.Anime, TvType.AnimeMovie, TvType.OVA)
    override var lang = "en"
    override val hasMainPage = true

    override val mainPage = mainPageOf(
        "$mainUrl/browse?q=&type=&status=&season=&year=&genres=&sort=order_top_airing&page=" to "Top Airing",
        "$mainUrl/browse?q=&type=&status=&season=&year=&genres=&sort=order_popular&page=" to "Popular",
        "$mainUrl/browse?q=&type=&status=&season=&year=&genres=&sort=order_updated&page=" to "Recently Updated",
        "$mainUrl/browse?q=&type=&status=&season=&year=&genres=&sort=aired_start&page=" to "Recently Aired",
        "$mainUrl/browse?q=&type=&status=Currently+Airing&season=&year=&genres=&sort=order_favorite&page=" to "Currently Airing",
        "$mainUrl/browse?type=TV&page=" to "TV Series",
        "$mainUrl/browse?type=Movie&page=" to "Movies",
        "$mainUrl/browse?type=ONA&page=" to "ONA",
        "$mainUrl/browse?type=OVA&page=" to "OVA",
        "$mainUrl/browse?type=Special&page=" to "Specials",
        "$mainUrl/browse?q=&type=&status=Finished+Airing&season=&year=&genres=&sort=order_favorite&page=" to "Finished Airing"
    )
    private fun searchResponseBuilder(res: Document): List<AnimeSearchResponse> {
        val results = mutableListOf<AnimeSearchResponse>()
        res.select("a.anime-card").forEach { item ->
            val title = item.attr("title")
            val url = item.attr("href")
            val posterUrl = item.selectFirst("img")?.attr("src")
            val ratingText = item.selectFirst("span.badge-gray")?.ownText()?.trim()
            val rating = ratingText?.replace(Regex("[^0-9.]"), "")?.toDoubleOrNull()
            results += newAnimeSearchResponse(title, url) {
                this.posterUrl = posterUrl
                if (rating != null) {
                    this.score = Score.from10(rating.toString())
                }
            }
        }
        return results
    }

    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val url = request.data + page.toString()
        val res = app.get(url).document
        val searchRes = searchResponseBuilder(res)
        return newHomePageResponse(request.name, searchRes)
    }

    override suspend fun search(query: String, page: Int): SearchResponseList {
        val browseRes = app.get("$mainUrl/browse?q=$query").document
        return searchResponseBuilder(browseRes).toNewSearchResponseList()
    }

    override suspend fun load(url: String): LoadResponse? {
        val slug = url.substringAfterLast("/")
        val siteId = slug.substringAfterLast("-").toIntOrNull() ?: return null

        val doc = app.get(url).document
        val title = doc.selectFirst("h1")?.text() ?: ""
        val poster = doc.selectFirst("div.flex-shrink-0 img")?.attr("src")
            ?: doc.selectFirst("meta[property=og:image]")?.attr("content")
        val description = doc.selectFirst("meta[name=description]")?.attr("content")
            ?: doc.selectFirst(".description")?.text()

        val tags = doc.select("a.filter-chip").map { it.text() }
        val year = doc.selectFirst("a[href*=&year=]")?.text()?.split(" ")?.lastOrNull()?.toIntOrNull()
        val ratingText = doc.select("span.badge-gray").firstOrNull { it.text().contains(Regex("[0-9]")) }?.ownText()?.trim()
        val rating = ratingText?.replace(Regex("[^0-9.]"), "")?.toDoubleOrNull()
        
        val episodesUrl = "$mainUrl/api/frontend/anime/$siteId/episodes"
        val epResponse = app.get(episodesUrl, headers = mapOf("X-Requested-With" to "XMLHttpRequest")).parsedSafe<EpisodesResponse>()
        val episodesList = epResponse?.episodes ?: emptyList()

        val firstEpId = episodesList.firstOrNull()?.id
        var hasSub = true
        var hasDub = false

        if (firstEpId != null) {
            val langUrl = "$mainUrl/api/frontend/episode/$firstEpId/languages"
            val langResponse = app.get(langUrl, headers = mapOf("X-Requested-With" to "XMLHttpRequest")).parsedSafe<LanguagesResponse>()
            val langs = langResponse?.languages ?: emptyList()
            hasSub = langs.isEmpty() || langs.any { it.code?.lowercase() in listOf("jpn", "ja", "japanese") || it.name?.lowercase() in listOf("jpn", "ja", "japanese") }
            hasDub = langs.any { it.code?.lowercase() in listOf("eng", "en", "english") || it.name?.lowercase() in listOf("eng", "en", "english") }
        }

        val subEpisodes = mutableListOf<Episode>()
        val dubEpisodes = mutableListOf<Episode>()

        val malId = doc.selectFirst("a[href*=myanimelist.net/anime/]")?.attr("href")?.substringAfter("anime/")?.substringBefore("/")?.toIntOrNull()
        val anilistId = doc.selectFirst("a[href*=anilist.co/anime/]")?.attr("href")?.substringAfter("anime/")?.substringBefore("/")?.toIntOrNull()

        val syncMetaData = if (anilistId != null) {
            app.get("https://api.ani.zip/mappings?anilist_id=$anilistId").text
        } else if (malId != null) {
            app.get("https://api.ani.zip/mappings?mal_id=$malId").text
        } else null

        val animeMetaData = syncMetaData?.let { parseAnimeData(it) }

        val isMovie = doc.selectFirst("a[class*=badge-orange][href*=/browse?type=Movie]") != null
        
        episodesList.forEachIndexed { index, ep ->
            val num = index + 1
            val metaEp = animeMetaData?.episodes?.get(num.toString())
            
            val epName = metaEp?.title?.get("en") ?: metaEp?.title?.get("x-jat") ?: metaEp?.title?.get("ja") ?: "Episode $num"
            val epDesc = metaEp?.overview
            val epPoster = metaEp?.image
            val epRating = metaEp?.rating?.let { Score.from10(it) }
            val epRuntime = metaEp?.runtime
            val epAirDate = metaEp?.airDateUtc

            if (isMovie) {
                subEpisodes.add(newEpisode("${ep.id}|$slug|movie") {
                    this.episode = num
                    this.name = epName
                    this.description = epDesc
                    this.posterUrl = epPoster
                    if (epRating != null) this.score = epRating
                    this.runTime = epRuntime
                    this.addDate(epAirDate)
                })
            } else {
                if (hasSub) {
                    subEpisodes.add(newEpisode("${ep.id}|$slug|sub") {
                        this.episode = num
                        this.name = epName
                        this.description = epDesc
                        this.posterUrl = epPoster
                        if (epRating != null) this.score = epRating
                        this.runTime = epRuntime
                        this.addDate(epAirDate)
                    })
                }
                if (hasDub) {
                    dubEpisodes.add(newEpisode("${ep.id}|$slug|dub") {
                        this.episode = num
                        this.name = epName
                        this.description = epDesc
                        this.posterUrl = epPoster
                        if (epRating != null) this.score = epRating
                        this.runTime = epRuntime
                        this.addDate(epAirDate)
                    })
                }
            }
        }

        val tvType = if (isMovie) TvType.AnimeMovie else TvType.Anime

        val trailerUrl = doc.selectFirst("a[href*=youtube.com/watch]")?.attr("href")
        
        val statusText = doc.selectFirst("a[class*=badge][href*=/browse?status=]")?.text()
        val showStatus = when (statusText) {
            "Finished Airing" -> ShowStatus.Completed
            "Currently Airing" -> ShowStatus.Ongoing
            else -> null
        }

        val durationText = doc.select("div.flex.flex-wrap.gap-x-6 span").firstOrNull { it.text().contains("m") || it.text().contains("h") }?.text()
        val duration = durationText?.let {
            if (it.contains("h") && it.contains("m")) {
                val h = it.substringBefore("h").toIntOrNull() ?: 0
                val m = it.substringAfter("h").substringBefore("m").trim().toIntOrNull() ?: 0
                h * 60 + m
            } else if (it.contains("h")) {
                (it.substringBefore("h").toIntOrNull() ?: 0) * 60
            } else {
                it.substringBefore("m").trim().toIntOrNull()
            }
        }

        return newAnimeLoadResponse(title, url, tvType) {
            this.posterUrl = poster
            this.plot = description
            this.year = year
            this.tags = tags
            this.showStatus = showStatus
            this.duration = duration
            if (rating != null) {
                this.score = Score.from10(rating.toString())
            }
            addMalId(malId)
            addAniListId(anilistId)
            addTrailer(trailerUrl)
            if (isMovie) {
                addEpisodes(DubStatus.Subbed, subEpisodes)
            } else {
                addEpisodes(DubStatus.Subbed, subEpisodes)
                addEpisodes(DubStatus.Dubbed, dubEpisodes)
            }
        }
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val parts = data.split("|")
        val episodeIdRaw = parts.getOrNull(0) ?: return false
        val episodeId = episodeIdRaw.substringAfterLast("/")
        val slug = parts.getOrNull(1) ?: return false
        val audio = parts.getOrNull(2) ?: "sub"

        val langUrl = "$mainUrl/api/frontend/episode/$episodeId/languages"
        val langResponse = app.get(langUrl, headers = mapOf("X-Requested-With" to "XMLHttpRequest", "Referer" to "$mainUrl/anime/$slug")).parsedSafe<LanguagesResponse>()

        val langs = langResponse?.languages ?: emptyList()
        val langsToExtract = if (audio == "movie") {
            langs
        } else {
            val preferredCodes = if (audio == "sub") listOf("jpn", "ja", "japanese") else listOf("eng", "en", "english")
            listOfNotNull(langs.find { it.code?.lowercase() in preferredCodes } ?: langs.find { it.name?.lowercase() in preferredCodes })
        }
        
        val hlsRegex = listOf(
            Regex("""file\s*:\s*["'](https?://[^"']+\.m3u8[^"']*)["']""", RegexOption.IGNORE_CASE),
            Regex("""sources\s*:\s*\[\s*\{[^}]*file\s*:\s*["'](https?://[^"']+\.m3u8[^"']*)["']""", RegexOption.IGNORE_CASE),
            Regex("""["'](https?://[^"']+/master\.m3u8[^"']*)["']""", RegexOption.IGNORE_CASE),
            Regex("""["'](https?://[^"']+\.m3u8[^"']*)["']""", RegexOption.IGNORE_CASE)
        )

        langsToExtract.amap { language ->
            val embedUrl = language.embed_url ?: return@amap
            val embedDoc = app.get(embedUrl, headers = mapOf("Referer" to "$mainUrl/")).text
            
            var hlsUrl: String? = null
            for (regex in hlsRegex) {
                val match = regex.find(embedDoc)
                if (match != null) {
                    hlsUrl = match.groupValues[1]
                    break
                }
            }
            
            if (hlsUrl != null) {
                val sourceName = if (audio == "movie") "$name - ${language.name ?: "Unknown"}" else name
                generateM3u8(
                    sourceName,
                    hlsUrl,
                    "$mainUrl/"
                ).forEach(callback)
            } else {
                loadExtractor(embedUrl, "$mainUrl/", subtitleCallback, callback)
            }
        }
        
        return true
    }

    data class EpisodeItem(
        val id: Int? = null,
        val number: Int? = null,
        val filler: Boolean? = null
    )

    data class EpisodesResponse(
        val episodes: List<EpisodeItem>? = null
    )

    data class Language(
        val id: Int? = null,
        val code: String? = null,
        val name: String? = null,
        val embed_url: String? = null
    )

    data class LanguagesResponse(
        val languages: List<Language>? = null
    )
}
