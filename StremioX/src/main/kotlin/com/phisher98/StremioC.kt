package com.phisher98

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.AcraApplication
import com.lagradost.cloudstream3.Episode
import com.lagradost.cloudstream3.HomePageList
import com.lagradost.cloudstream3.HomePageResponse
import com.lagradost.cloudstream3.LoadResponse
import com.lagradost.cloudstream3.LoadResponse.Companion.addActors
import com.lagradost.cloudstream3.LoadResponse.Companion.addImdbId
import com.lagradost.cloudstream3.LoadResponse.Companion.addTrailer
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.MainPageRequest
import com.lagradost.cloudstream3.Score
import com.lagradost.cloudstream3.SearchResponse
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.fixTitle
import com.lagradost.cloudstream3.imdbUrlToIdNullable
import com.lagradost.cloudstream3.newEpisode
import com.lagradost.cloudstream3.newHomePageResponse
import com.lagradost.cloudstream3.newMovieLoadResponse
import com.lagradost.cloudstream3.newMovieSearchResponse
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.newTvSeriesLoadResponse
import com.lagradost.cloudstream3.runAllAsync
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.SubtitleHelper
import com.lagradost.cloudstream3.utils.USER_PROVIDER_API
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.loadExtractor
import com.lagradost.cloudstream3.utils.newExtractorLink
import com.phisher98.StremioC.Companion.TRACKER_LIST_URLS
import com.phisher98.SubsExtractors.invokeOpenSubs
import com.phisher98.SubsExtractors.invokeWatchsomuch
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale


class StremioC(override var mainUrl: String, override var name: String) : MainAPI() {
    override val supportedTypes = setOf(TvType.Others)
    override val hasMainPage = true

    companion object {
        private const val cinemeta = "https://aiometadata.elfhosted.com/stremio/b7cb164b-074b-41d5-b458-b3a834e197bb"
        val TRACKER_LIST_URLS = listOf(
            "https://raw.githubusercontent.com/ngosang/trackerslist/refs/heads/master/trackers_best.txt",
            "https://raw.githubusercontent.com/ngosang/trackerslist/refs/heads/master/trackers_best_ip.txt",
        )
        private const val TRACKER_LIST_URL = "https://raw.githubusercontent.com/ngosang/trackerslist/master/trackers_best.txt"
    }

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        if (mainUrl.isEmpty()) {
            throw IllegalArgumentException("Configure in Extension Settings\n")
        }
        mainUrl = mainUrl.fixSourceUrl()

        val pageSize = 100
        val skip = (page - 1) * pageSize

        val manifest = app
            .get("$mainUrl/manifest.json")
            .parsedSafe<Manifest>()

        val lists = mutableListOf<HomePageList>()

        manifest?.catalogs?.amap { catalog ->
            catalog.toHomePageList(
                provider = this,
                skip = skip
            ).let {
                if (it.list.isNotEmpty()) {
                    lists.add(it)
                }
            }
        }

        return newHomePageResponse(
            lists,
            hasNext = true
        )
    }


    override suspend fun search(query: String): List<SearchResponse> {
        mainUrl = mainUrl.fixSourceUrl()
        val res = app.get("${mainUrl}/manifest.json").parsedSafe<Manifest>()
        val list = mutableListOf<SearchResponse>()
        res?.catalogs?.amap { catalog ->
            list.addAll(catalog.search(query, this))
        }
        return list.distinct()
    }

    override suspend fun load(url: String): LoadResponse {
        val res: CatalogEntry = if (url.startsWith("{")) {
            parseJson(url)
        } else {
            val json = app.get(url).text
            val metaJson = JSONObject(json).getJSONObject("meta").toString()
            parseJson(metaJson)
        }

        val encodedId = URLEncoder.encode(res.id, "UTF-8")

        val response = app.get("$mainUrl/meta/${res.type}/$encodedId.json")
            .parsedSafe<CatalogResponse>()
            ?: throw RuntimeException("Failed to load meta")

        val entry = response.meta
            ?: response.metas?.firstOrNull { it.id == res.id }
            ?: response.metas?.firstOrNull()
            ?: run {
                val fallback = app.get(
                    "$cinemeta/meta/${res.type}/$encodedId.json",
                    timeout = 120L
                ).parsedSafe<CatalogResponse>()

                fallback?.meta
                    ?: fallback?.metas?.firstOrNull()
                    ?: throw RuntimeException("Meta not found (primary + fallback)")
            }

        return entry.toLoadResponse(this, res.id)
    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val loadData = parseJson<LoadData>(data)
        val encodedId = URLEncoder.encode(loadData.id, "UTF-8")
        val request = app.get(
            "${mainUrl}/stream/${loadData.type}/$encodedId.json",
            timeout = 120L
        )

        val res = if (request.isSuccessful)
            request.parsedSafe<StreamsResponse>()
        else
            null

        if (!res?.streams.isNullOrEmpty()) {
            res.streams.forEach { stream ->
                stream.runCallback(subtitleCallback, callback)
            }
        } else {
            runAllAsync(
                    {
                        invokeStremioX(loadData.type, loadData.id, subtitleCallback, callback)
                    },{
                        invokeTorrentio(loadData.imdbId, loadData.season, loadData.episode, callback)
                    },
                    {
                        invokeKnaben(loadData.imdbId, loadData.year,loadData.season, loadData.episode, callback)
                    },
                    {
                        invokeUindex(loadData.imdbId, loadData.year,loadData.season, loadData.episode, callback)
                    },
                    {
                        invokeWatchsomuch(
                            loadData.imdbId,
                            loadData.season,
                            loadData.episode,
                            subtitleCallback
                        )
                    },
                    {
                        invokeOpenSubs(
                            loadData.imdbId,
                            loadData.season,
                            loadData.episode,
                            subtitleCallback
                        )
                    }
            )
        }

        return true
    }

    private suspend fun invokeStremioX(
        type: String?,
        id: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val sites = AcraApplication.getKey<Array<CustomSite>>(USER_PROVIDER_API)?.toMutableList()
            ?: mutableListOf()
        sites.filter { it.parentJavaClass == "StremioX" }.amap { site ->
            val res = app.get(
                "${site.url.fixSourceUrl()}/stream/${type}/${id}.json",
                timeout = 120L
            ).parsedSafe<StreamsResponse>()
            res?.streams?.forEach { stream ->
                stream.runCallback(subtitleCallback, callback)
            }
        }
    }

    data class LoadData(
        val type: String? = null,
        val id: String? = null,
        val season: Int? = null,
        val episode: Int? = null,
        val imdbId: String? = null,
        val year: Int? = null
    )

    data class CustomSite(
        @JsonProperty("parentJavaClass") val parentJavaClass: String,
        @JsonProperty("name") val name: String,
        @JsonProperty("url") val url: String,
        @JsonProperty("lang") val lang: String,
    )

    // check if id is imdb/tmdb cause stremio addons like torrentio works base on imdbId
    private fun isImdborTmdb(url: String?): Boolean {
        return imdbUrlToIdNullable(url) != null || url?.startsWith("tmdb:") == true
    }

    private fun isImdb(url: String?): Boolean {
        return imdbUrlToIdNullable(url) != null
    }


    private data class Manifest(val catalogs: List<Catalog>)
    private data class Catalog(
        var name: String?,
        val id: String,
        val type: String?,
        val types: MutableList<String> = mutableListOf()
    ) {
        init {
            if (type != null) types.add(type)
        }

        suspend fun search(query: String, provider: StremioC): List<SearchResponse> {
            val entries = mutableListOf<SearchResponse>()
            types.forEach { type ->
                val res = app.get(
                    "${provider.mainUrl}/catalog/${type}/${id}/search=${query}.json",
                    timeout = 120L
                ).parsedSafe<CatalogResponse>()
                res?.metas?.forEach { entry ->
                    entries.add(entry.toSearchResponse(provider))
                }
            }
            return entries
        }

        suspend fun toHomePageList(
            provider: StremioC,
            skip: Int
        ): HomePageList {
            val entries = mutableMapOf<String, SearchResponse>()

            types.forEach { type ->
                val url = if (skip > 0) {
                    "${provider.mainUrl}/catalog/$type/$id/skip=$skip.json"
                } else {
                    "${provider.mainUrl}/catalog/$type/$id.json"
                }

                val res = app.get(
                    url,
                    timeout = 120L
                ).parsedSafe<CatalogResponse>()

                res?.metas?.forEach { entry ->
                    if (!entries.containsKey(entry.id)) {
                        entries[entry.id] = entry.toSearchResponse(provider)
                    }
                }
            }

            return HomePageList(
                name ?: id,
                entries.values.toList()
            )
        }
    }

    private data class CatalogResponse(val metas: List<CatalogEntry>?, val meta: CatalogEntry?)

    private data class Trailer(
        val source: String?,
        val type: String?
    )

    private data class CatalogEntry(
        @JsonProperty("name") val name: String,
        @JsonProperty("id") val id: String,
        @JsonProperty("poster") val poster: String?,
        @JsonProperty("background") val background: String?,
        @JsonProperty("description") val description: String?,
        @JsonProperty("imdbRating") val imdbRating: String?,
        @JsonProperty("type") val type: String?,
        @JsonProperty("videos") val videos: List<Video>?,
        @JsonProperty("genre") val genre: List<String>?,
        @JsonProperty("genres") val genres: List<String> = emptyList(),
        @JsonProperty("cast") val cast: List<String> = emptyList(),
        @JsonProperty("trailers") val trailersSources: List<Trailer> = emptyList(),
        @JsonProperty("year") val yearNum: String? = null
    ) {
        fun toSearchResponse(provider: StremioC): SearchResponse {
            return provider.newMovieSearchResponse(
                name,
                this.toJson(),
                TvType.Others
            ) {
                posterUrl = poster
            }
        }

        suspend fun toLoadResponse(provider: StremioC, imdbId: String?): LoadResponse {
            if (videos.isNullOrEmpty()) {
                return provider.newMovieLoadResponse(
                    name,
                    "${provider.mainUrl}/meta/${type}/${id}.json",
                    TvType.Movie,
                    LoadData(type, id, imdbId = imdbId, year = yearNum?.toIntOrNull())
                ) {
                    posterUrl = poster
                    backgroundPosterUrl = background
                    score = Score.from10(imdbRating)
                    plot = description
                    year = yearNum?.toIntOrNull()
                    tags = genre ?: genres
                    addActors(cast)
                    addTrailer(trailersSources.map { "https://www.youtube.com/watch?v=${it.source}" })
                    addImdbId(imdbId)
                }
            } else {
                return provider.newTvSeriesLoadResponse(
                    name,
                    "${provider.mainUrl}/meta/${type}/${id}.json",
                    TvType.TvSeries,
                    videos.map {
                        it.toEpisode(provider, type, imdbId)
                    }
                ) {
                    posterUrl = poster
                    backgroundPosterUrl = background
                    score = Score.from10(imdbRating)
                    plot = description
                    year = yearNum?.toIntOrNull()
                    tags = genre ?: genres
                    addActors(cast)
                    addTrailer(trailersSources.map { "https://www.youtube.com/watch?v=${it.source}" }
                        .randomOrNull())
                    addImdbId(imdbId)
                }
            }

        }
    }

    private data class Video(
        @JsonProperty("id") val id: String? = null,
        @JsonProperty("title") val title: String? = null,
        @JsonProperty("name") val name: String? = null,
        @JsonProperty("season") val seasonNumber: Int? = null,
        @JsonProperty("number") val number: Int? = null,
        @JsonProperty("episode") val episode: Int? = null,
        @JsonProperty("thumbnail") val thumbnail: String? = null,
        @JsonProperty("overview") val overview: String? = null,
        @JsonProperty("description") val description: String? = null,
    ) {
        fun toEpisode(provider: StremioC, type: String?, imdbId: String?): Episode {
            return provider.newEpisode(
                LoadData(type, id, seasonNumber, episode ?: number, imdbId)
            ) {
                this.name = this@Video.name ?: title
                this.posterUrl = thumbnail
                this.description = overview ?:  this@Video.description
                this.season = seasonNumber
                this.episode =  this@Video.episode ?: number
            }
        }
    }

    private data class StreamsResponse(val streams: List<Stream>)
    private data class Subtitle(
        val url: String?,
        val lang: String?,
        val id: String?,
    )

    private data class ProxyHeaders(
        val request: Map<String, String>?,
    )

    private data class BehaviorHints(
        val proxyHeaders: ProxyHeaders?,
        val headers: Map<String, String>?,
    )

    private data class Stream(
        val name: String?,
        val title: String?,
        val url: String?,
        val description: String?,
        val ytId: String?,
        val externalUrl: String?,
        val behaviorHints: BehaviorHints?,
        val infoHash: String?,
        val sources: List<String> = emptyList(),
        val subtitles: List<Subtitle> = emptyList()
    ) {
        suspend fun runCallback(
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
        ) {
            if (url != null) {
                callback.invoke(
                    newExtractorLink(
                        name ?: "",
                        fixSourceName(name, title),
                        url,
                        INFER_TYPE,
                    )
                    {
                        this.quality=getQuality(listOf(description,title,name))
                        this.headers=behaviorHints?.proxyHeaders?.request ?: behaviorHints?.headers ?: mapOf()
                    }
                )
                subtitles.map { sub ->
                    subtitleCallback.invoke(
                        newSubtitleFile(
                            SubtitleHelper.fromTagToEnglishLanguageName(sub.lang ?: "") ?: sub.lang
                            ?: "",
                            sub.url ?: return@map
                        )
                    )
                }
            }
            if (ytId != null) {
                loadExtractor("https://www.youtube.com/watch?v=$ytId", subtitleCallback, callback)
            }
            if (externalUrl != null) {
                loadExtractor(externalUrl, subtitleCallback, callback)
            }
            if (infoHash != null) {
                val resp = app.get(TRACKER_LIST_URL).text
                val otherTrackers = resp
                    .split("\n")
                    .filterIndexed { i, _ -> i % 2 == 0 }
                    .filter { s -> s.isNotEmpty() }.joinToString("") { "&tr=$it" }

                val sourceTrackers = sources
                    .filter { it.startsWith("tracker:") }
                    .map { it.removePrefix("tracker:") }
                    .filter { s -> s.isNotEmpty() }.joinToString("") { "&tr=$it" }

                val magnet = "magnet:?xt=urn:btih:${infoHash}${sourceTrackers}${otherTrackers}"
                callback.invoke(
                    newExtractorLink(
                        name ?: "",
                        title ?: name ?: "",
                        magnet,
                    )
                    {
                        this.quality=Qualities.Unknown.value
                    }
                )
            }
        }
    }
}

suspend fun invokeUindex(
    title: String? = null,
    year: Int? = null,
    season: Int? = null,
    episode: Int? = null,
    callback: (ExtractorLink) -> Unit
) {
    val uindex = "https://uindex.org"
    val isTv = season != null

    val searchQuery = buildString {
        if (!title.isNullOrBlank()) append(title)
        if (year != null) {
            if (isNotEmpty()) append(' ')
            append(year)
        }
    }.replace(' ', '+')

    val url = "$uindex/search.php?search=$searchQuery&c=${if (isTv) 2 else 1}"

    val headers = mapOf(
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
        "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
    )

    val rows = app.get(url, headers = headers).documentLarge.select("tr")

    val episodePatterns: List<Regex> = if (isTv && episode != null) {
        val rawPatterns = listOf(
            String.format(Locale.US, "S%02dE%02d", season, episode),
            "S${season}E$episode",
            String.format(Locale.US, "S%02dE%d", season, episode),
            String.format(Locale.US, "S%dE%02d", season, episode),
        )

        rawPatterns.distinct().map {
            Regex("\\b$it\\b", RegexOption.IGNORE_CASE)
        }
    } else {
        emptyList()
    }

    rows.amap { row ->
        val rowTitle = row.select("td:nth-child(2) > a:nth-child(2)").text()
        val magnet = row.select("td:nth-child(2) > a:nth-child(1)").attr("href")

        if (rowTitle.isBlank() || magnet.isBlank()) return@amap

        if (isTv && episodePatterns.isNotEmpty()) {
            if (episodePatterns.none { it.containsMatchIn(rowTitle) }) return@amap
        }

        val qualityMatch = "(2160p|1080p|720p)"
            .toRegex(RegexOption.IGNORE_CASE)
            .find(rowTitle)
            ?.value

        val seeder = row
            .select("td:nth-child(4) > span")
            .text()
            .replace(",", "")
            .ifBlank { "0" }

        val fileSize = row.select("td:nth-child(3)").text()

        val formattedTitleName = run {
            val qualityTermsRegex =
                "(WEBRip|WEB-DL|x265|x264|10bit|HEVC|H264)"
                    .toRegex(RegexOption.IGNORE_CASE)

            val tags = qualityTermsRegex.findAll(rowTitle)
                .map { it.value.uppercase() }
                .distinct()
                .joinToString(" | ")

            "UIndex | $tags | Seeder: $seeder | FileSize: $fileSize".trim()
        }

        callback.invoke(
            newExtractorLink(
                "UIndex",
                formattedTitleName.ifBlank { rowTitle },
                url = magnet,
                type = INFER_TYPE
            ) {
                this.quality = getQualityFromName(qualityMatch)
            }
        )
    }
}

suspend fun invokeKnaben(
    title: String? = null,
    year: Int? = null,
    season: Int? = null,
    episode: Int? = null,
    callback: (ExtractorLink) -> Unit
) {
    val knaben = "https://knaben.org"
    val isTv = season != null
    val host = knaben.trimEnd('/')

    val baseQuery = buildString {
        val queryText = title?.takeIf { it.isNotBlank() } ?: return@buildString

        append(
            queryText
                .trim()
                .replace("\\s+".toRegex(), "+")
        )

        if (isTv && episode != null) {
            append("+S${season.toString().padStart(2, '0')}")
            append("E${episode.toString().padStart(2, '0')}")
        } else if (!isTv && year != null) {
            append("+$year")
        }
    }

    if (baseQuery.isBlank()) return

    val category = when {
        isTv -> "2000000"
        else -> "3000000"
    }

    for (page in 1..2) {
        val url = "$host/search/$baseQuery/$category/$page/seeders"

        val doc = app.get(url).document

        doc.select("tr.text-nowrap.border-start").forEach { row ->
            val infoTd = row.selectFirst("td:nth-child(2)") ?: return@forEach

            val titleElement = infoTd.selectFirst("a[title]") ?: return@forEach
            val rawTitle = titleElement.attr("title").ifBlank { titleElement.text() }

            val magnet = infoTd.selectFirst("a[href^=magnet:?]")?.attr("href") ?: return@forEach

            val source = row
                .selectFirst("td.d-sm-none.d-xl-table-cell a")
                ?.text()
                ?.trim()
                .orEmpty()

            val tds = row.select("td")
            val sizeText = tds.getOrNull(2)?.text().orEmpty()
            val seedsText = tds.getOrNull(4)?.text().orEmpty()
            val seeds = seedsText.toIntOrNull() ?: 0
            val qualityMatch = "(2160p|1080p|720p)"
                .toRegex(RegexOption.IGNORE_CASE)
                .find(rawTitle)
                ?.value
            val formattedTitleName = buildString {
                append("Knaben | ")
                append(rawTitle)

                if (seeds > 0) {
                    append(" | Seeds: ")
                    append(seeds)
                }

                if (sizeText.isNotBlank()) {
                    append(" | ")
                    append(sizeText)
                }

                if (source.isNotBlank()) {
                    append(" | ")
                    append(source)
                }
            }

            callback(
                newExtractorLink(
                    "Knaben",
                    formattedTitleName.ifBlank { rawTitle },
                    url = magnet,
                    type = INFER_TYPE
                ) {
                    this.quality = getQualityFromName(qualityMatch)
                }
            )
        }
    }
}

suspend fun invokeTorrentio(
    id: String? = null,
    season: Int? = null,
    episode: Int? = null,
    callback: (ExtractorLink) -> Unit
) {
    val url = if(season == null) {
        "https://torrentio.strem.fun/stream/movie/$id.json"
    }
    else {
        "https://torrentio.strem.fun/stream/series/$id:$season:$episode.json"
    }
    val headers = mapOf(
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
        "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
    )
    val res = app.get(url, headers = headers, timeout = 100L).parsedSafe<TorrentioResponse>()
    res?.streams?.forEach { stream ->
        val formattedTitleName = stream.title
            ?.let { title ->
                val qualityTermsRegex = "(2160p|1080p|720p|WEBRip|WEB-DL|x265|x264|10bit|HEVC|H264)".toRegex(RegexOption.IGNORE_CASE)
                val tagsList = qualityTermsRegex.findAll(title).map { it.value.uppercase() }.toList()
                val tags = tagsList.distinct().joinToString(" | ")

                val seeder = "👤\\s*(\\d+)".toRegex().find(title)?.groupValues?.get(1) ?: "0"
                val provider = "⚙️\\s*([^\\n]+)".toRegex().find(title)?.groupValues?.get(1)?.trim() ?: "Unknown"

                "Torrentio | $tags | Seeder: $seeder | Provider: $provider".trim()
            }

        val qualityMatch = "(2160p|1080p|720p)".toRegex(RegexOption.IGNORE_CASE)
            .find(stream.title ?: "")
            ?.value
            ?.lowercase()

        val magnet = generateMagnetLink(TRACKER_LIST_URLS, stream.infoHash)

        callback.invoke(
            newExtractorLink(
                "Torrentio",
                formattedTitleName ?: stream.name ?: "",
                url = magnet,
                INFER_TYPE
            ) {
                this.referer = ""
                this.quality = getQualityFromName(qualityMatch)
            }
        )
    }
}