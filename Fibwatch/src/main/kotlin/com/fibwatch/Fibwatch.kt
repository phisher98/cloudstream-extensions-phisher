package com.Fibwatch

import android.util.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.AppUtils.tryParseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import org.jsoup.nodes.Element
import kotlin.collections.mapNotNull
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit


open class Fibwatch : MainAPI() {
    override var mainUrl = "https://fibwatch.biz"
    override var name = "FibWatch"
    override val hasMainPage = true
    override var lang = "hi"
    override val hasDownloadSupport = true

    override val supportedTypes = setOf(
        TvType.Movie,
        TvType.TvSeries
    )

    override val mainPage = mainPageOf(
        "videos/trending" to "Trending Videos",
        "videos/top" to "Top Videos",
        "videos/latest" to "Latest Videos",
        "videos/category/1" to "Bangla–Kolkata Movies",
        "videos/category/852" to "Bangla Dubbed",
        "videos/category/3" to "Web Series",
        "videos/category/4" to "Hindi Movies",
        "videos/category/5" to "Hindi Dubbed Movies",
        "videos/category/9" to "Horror Movies",
        "videos/category/6" to "Tamil & Telugu Movies",
        "videos/category/11" to "Kannada Movies",
        "videos/category/10" to "Malayalam Movies",
        "videos/category/8" to "English Movies",
        "videos/category/12" to "Korean Movies",
        "videos/category/13" to "Marathi Movies",
        "videos/category/7" to "Cartoon Movies",
        "videos/category/853" to "Mixed Content",
        "videos/category/854" to "TV Shows",
        "videos/category/855" to "Natok",
        "videos/category/other" to "Other"
    )


    override suspend fun getMainPage(page: Int, request: MainPageRequest): HomePageResponse {
        val document = app.get("$mainUrl/${request.data}?page_id=$page").document
        val home = document.select("div.video-thumb").mapNotNull { it.toSearchResult() }

        return newHomePageResponse(
            list = HomePageList(
                name = request.name,
                list = home,
                isHorizontalImages = true
            ),
            hasNext = true
        )
    }

    private fun Element.toSearchResult(): SearchResponse {
        val title = cleanTitle(this.selectFirst("p.hptag")?.text() ?: this.selectFirst("div.video-thumb img")?.attr("alt"))
        val href = fixUrl(this.select("a").attr("href"))
        val posterUrl = fixUrlNull(this.select("img").attr("src"))
        return newMovieSearchResponse(title, href, TvType.Movie) {
            this.posterUrl = posterUrl
        }
    }


    override suspend fun search(query: String,page: Int): SearchResponseList {
        val document = app.get("$mainUrl/search?keyword=$query&page_id=$page").document
        return document.select("div.video-thumb").mapNotNull { it.toSearchResult() }.toNewSearchResponseList()
    }

    override suspend fun load(url: String): LoadResponse = withContext(Dispatchers.IO) {
        val document = app.get(url).document

        val rawTitle = document.selectFirst("meta[property=og:title]")?.attr("content") ?: "Unknown"
        val title = rawTitle.substringBefore("S0")
        val poster = document.selectFirst("""meta[property="og:image"]""")?.attr("content")
        val description = document.selectFirst("meta[property=og:description]")?.attr("content") ?: "Unknown"
        val rawTitleLower = rawTitle.lowercase()

        // precompiled/simple regex checks (local, no external helpers)
        val sxeRegex = Regex("""s\d{1,2}e\d{1,3}""")
        val seasonRegex = Regex("""\bs\d{1,2}\b""")
        val episodeRegex = Regex("""\be\d{1,3}\b""")

        val tvType = when {
            sxeRegex.containsMatchIn(rawTitleLower) -> TvType.TvSeries
            seasonRegex.containsMatchIn(rawTitleLower) -> TvType.TvSeries
            episodeRegex.containsMatchIn(rawTitleLower) -> TvType.TvSeries
            else -> TvType.Movie
        }

        val videoId = document.selectFirst("input#video-id")?.attr("value")?.takeIf { it.isNotBlank() }

        val toLoadItem: (String?, String?, Boolean) -> LoadItem = { r, u, s ->
            LoadItem(quality = r?.trim().orEmpty(), url = u?.trim().orEmpty(), selected = s)
        }

        val dedupeByUrl: (List<LoadItem>) -> List<LoadItem> = { list ->
            val seen = LinkedHashSet<String>()
            list.filter { seen.add(it.url) }
        }

        val links: Links? = runCatching {
            if (videoId != null) app.get("$mainUrl/ajax/resolution_switcher.php?video_id=$videoId").parsedSafe<Links>() else null
        }.getOrNull()

        val currentRaw = links?.current
            ?.mapNotNull { c -> c.url?.trim().takeIf { it?.isNotEmpty() == true }?.let { toLoadItem(c.res, it, c.selected) } }
            ?: emptyList()

        val popupRaw = links?.popup
            ?.mapNotNull { p -> p.url?.trim().takeIf { it?.isNotEmpty() == true }?.let { toLoadItem(p.res, it, p.selected) } }
            ?: emptyList()

        val currentList = dedupeByUrl(currentRaw)
        val popupList = dedupeByUrl(popupRaw.filter { item -> currentList.none { it.url == item.url } })

        var out = LoadlinksOut(status = links?.status ?: "error", current = currentList, popup = popupList)
        if (out.current.isEmpty() && out.popup.isEmpty()) {
            try {
                val downloadEl = document.selectFirst("a.hidden-button.buttonDownloadnew")
                val dlUrl = downloadEl?.attr("onclick")
                    ?.substringAfter("url=", "")
                    ?.substringBefore("',", "")
                    ?.takeIf { it.isNotBlank() }

                if (!dlUrl.isNullOrBlank()) {
                    val normalized = dlUrl.trim()
                    val dlItem = toLoadItem(null, normalized, false) // quality = null
                    val current = dedupeByUrl(listOf(dlItem))
                    out = LoadlinksOut(status = out.status, current = current, popup = emptyList())
                }
            } catch (_: Throwable) {
                // ignore and keep original epOut
            }
        }


        val recommendations = document
            .select("div.col-md-4.no-padding-left.mobile div.videos-list.pt_mn_wtch_rlts_prnt .video-wrapper")
            .mapNotNull { it.toSearchResult()
            }

        if (tvType == TvType.TvSeries) {
            val data: EpisodesResponse? = runCatching {
                if (videoId != null) app.get("$mainUrl/ajax/episodes.php?video_id=$videoId").parsedSafe<EpisodesResponse>() else null
            }.getOrNull()

            val episodesList = data?.episodes.orEmpty()
            if (episodesList.isEmpty()) {
                return@withContext newTvSeriesLoadResponse(title, url, TvType.TvSeries, emptyList()) {
                    this.posterUrl = poster
                    this.plot = description
                    this.tags = document.select("div.tags-list a[rel='tag']").map { it.text() }
                    this.recommendations = recommendations
                }
            }
            // bounded concurrency to avoid overload
            val semaphore = Semaphore(6)
            val episodes = coroutineScope {
                episodesList.map { ep ->
                    async {
                        semaphore.withPermit {
                            try {
                                val epUrl = ep.url?.trim().orEmpty()
                                if (epUrl.isEmpty()) return@withPermit null

                                val epTitle = ep.title?.trim().orEmpty()
                                val lower = epTitle.lowercase()

                                var season: Int? = null
                                var episodeNum: Int? = null

                                Regex("""s(\d{1,2})e(\d{1,3})(?:-(\d{1,3}))?""").find(lower)?.let { m ->
                                    season = m.groupValues[1].toIntOrNull()
                                    episodeNum = m.groupValues[2].toIntOrNull()
                                } ?: run {
                                    Regex("""\bs(\d{1,2})\b""").find(lower)?.let { m -> season = m.groupValues[1].toIntOrNull() }
                                    Regex("""\be(\d{1,3})\b""").find(lower)?.let { m -> episodeNum = m.groupValues[1].toIntOrNull() }
                                }

                                val allqualities = runCatching { app.get(fixUrl(epUrl)).document }.getOrNull() ?: return@withPermit null

                                val shouldRequestResSwitcher = allqualities.select("div.available-res:contains(Available in Other Parts:)").isNotEmpty()

                                val innerVideoId = allqualities.selectFirst("input#video-id")?.attr("value")?.takeIf { it.isNotBlank() }

                                val epLinks: Links? = runCatching {
                                    if (shouldRequestResSwitcher && innerVideoId != null) {
                                        app.get("$mainUrl/ajax/resolution_switcher.php?video_id=$innerVideoId").parsedSafe<Links>()
                                    } else null
                                }.getOrNull()

                                val epCurrentRaw = epLinks?.current
                                    ?.mapNotNull { c -> c.url?.trim().takeIf { it?.isNotEmpty() == true }?.let { toLoadItem(c.res, it, c.selected) } }
                                    ?: emptyList()

                                val epPopupRaw = epLinks?.popup
                                    ?.mapNotNull { p -> p.url?.trim().takeIf { it?.isNotEmpty() == true }?.let { toLoadItem(p.res, it, p.selected) } }
                                    ?: emptyList()

                                val epCurrentList = dedupeByUrl(epCurrentRaw)
                                val epPopupList = dedupeByUrl(epPopupRaw.filter { item -> epCurrentList.none { it.url == item.url } })

                                var epOut = LoadlinksOut(status = epLinks?.status ?: "error", current = epCurrentList, popup = epPopupList)

                                if (epOut.current.isEmpty() && epOut.popup.isEmpty()) {
                                    try {
                                        val downloadEl = allqualities.selectFirst("a.hidden-button.buttonDownloadnew")
                                        val dlUrl = downloadEl?.attr("onclick")
                                                ?.substringAfter("url=", "")
                                                ?.substringBefore("',", "")
                                                ?.takeIf { it.isNotBlank() }

                                        if (!dlUrl.isNullOrBlank()) {
                                            val normalized = dlUrl.trim()
                                            val dlItem = toLoadItem(null, normalized, false) // quality = null
                                            val current = dedupeByUrl(listOf(dlItem))
                                            epOut = LoadlinksOut(status = epLinks?.status ?: "success", current = current, popup = emptyList())
                                        }
                                    } catch (_: Throwable) {
                                        // ignore and keep original epOut
                                    }
                                }

                                newEpisode(epOut.toJson()) {
                                    this.name = epTitle
                                    season?.let { this.season = it }
                                    episodeNum?.let { this.episode = it }
                                    this.posterUrl = poster
                                }
                            } catch (_: Throwable) {
                                null
                            }
                        }
                    }
                }.awaitAll().filterNotNull()
            }


            return@withContext newTvSeriesLoadResponse(title, url, TvType.TvSeries, episodes) {
                this.posterUrl = poster
                this.plot = description
                this.tags = document.select("div.tags-list a[rel='tag']").map { it.text() }
                this.recommendations = recommendations
            }
        } else {
            return@withContext newMovieLoadResponse(title, url, TvType.Movie, out.toJson()) {
                this.posterUrl = poster
                this.plot = description
                this.tags = document.select("div.tags-list a[rel='tag']").map { it.text() }
                this.recommendations = recommendations
            }
        }
    }




    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val loadData = tryParseJson<LoadlinksOut>(data) ?: return true

        val currentUrls = loadData.current.map { it.url.trim() }.toSet()
        val combined = ArrayList<LoadItem>(loadData.current.size + loadData.popup.size)

        combined += loadData.current
        combined += loadData.popup.filter { it.url.trim() !in currentUrls }

        for (item in combined) {

            val url = item.url.trim()
            if (url.isEmpty()) continue

            val isDirectMedia = url.contains(".mkv", true) ||
                    url.contains(".mp4", true) ||
                    url.contains(".m3u8", true)

            val finalUrl = if (isDirectMedia) {
                url
            } else {
                runCatching {
                    val doc = app.get(fixUrl(url)).document
                    val onclick = doc.selectFirst("a.hidden-button.buttonDownloadnew")
                        ?.attr("onclick") ?: return@runCatching null

                    onclick.substringAfter("url=")
                        .substringBefore("',")
                        .trim()
                        .takeIf { it.isNotEmpty() }
                }.getOrNull()
            }


            if (finalUrl.isNullOrEmpty()) {
                Log.w(name, "no download url for $url")
                continue
            }

            callback.invoke(
                newExtractorLink(
                    mainUrl,
                    this.name,
                    url = finalUrl
                ) {
                    this.quality = getQualityFromName(item.quality)
                }
            )
        }

        return true
    }
}






