package com.TorraStream

import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.metaproviders.TraktProvider
import com.lagradost.cloudstream3.syncproviders.SyncIdName
import com.lagradost.cloudstream3.utils.*
import org.json.JSONObject
import java.net.URLEncoder

class TorraStreamAIO() : TraktProvider() {
    override var name = "TorraStream AIO"
    override var mainUrl = "https://aiostreams.elfhosted.com/E2-xLzptGhmwLnA9L%2FOUHyZJg%3D%3D-Io2cJBStOrbqlmGwGz2ZwBbMGBj5enyJFqgN5XcslkuiUS5KSjJrv90yd4HHLj1fyq6hJm7QpnCxDiPqbeOwdGA2yySllUQh2T%2B5qPqgtPt2sWBN5zdeetbiFFLHvVqq0PZOhKGM7pv2LzCoMLAk%2BSo86mcrzWIeszmvHuRMoKX3zBO6hUDvH6oqK2hFfbUF7ZONMdm9jE7lHp0LuXKPzHSwKUvDZroJ9iRgBkvHIGjJL65oBv2PxfQK%2Fu4gYEuLVhH3dQ7Xu6i1AshdxycCPRQOO2LcDDZkBC84zLXoy3DDPkvDkWBv2icVZIs2dnQlwvtfu7fFiXaGxWJxtYvbBALIhey8SaaeCKts8xMEyuJvSZiKBbkiTblb0NbqfRyGoJz5rJkiCPzlnX6S%2BpNHKNXVYRj2QZmmvN47fdteAZfhvCuNRW1XBP%2FhTr5ufzCQ9tC8ao%2F4ZhoVXPje45mgPpeJy%2FqYGkX36%2BDgjUMGM1SIvm416pHFL1fVG9MQlIdTn2T4VaUHA0dZHXxznaSQDB%2F1GIkDCHOp2iWUl8zceINOE08AI%2BUwmWCnVXsvsXYaTbFnsE%2F0n1zQwN19ULRCnO4AN2KKLfWKHCz9q5YwQG6y9r%2BXTkjtAXoju764x1f2UlFZT8aavjX1oAcPiTC5vA%3D%3D"
    override var supportedTypes =
        setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama, TvType.Torrent)
    override var lang = "en"
    override val supportedSyncNames = setOf(SyncIdName.Trakt)
    override val hasMainPage = true
    override val hasQuickSearch = false


    private val traktApiUrl = base64Decode("aHR0cHM6Ly9hcGl6LnRyYWt0LnR2")

    override val mainPage =
        mainPageOf(
            "$traktApiUrl/movies/trending?extended=cloud9,full&limit=25" to
                    "Trending Movies",
            "$traktApiUrl/movies/popular?extended=cloud9,full&limit=25" to "Popular Movies",
            "$traktApiUrl/shows/trending?extended=cloud9,full&limit=25" to "Trending Shows",
            "$traktApiUrl/shows/popular?extended=cloud9,full&limit=25" to "Popular Shows",
            "$traktApiUrl/shows/trending?extended=cloud9,full&limit=25&network_ids=53,1465" to
                    "Netflix",
            "$traktApiUrl/shows/trending?extended=cloud9,full&limit=25&network_ids=47,2385" to
                    "Amazon Prime Video",
            "$traktApiUrl/shows/trending?extended=cloud9,full&limit=25&network_ids=256" to
                    "Apple TV+",
            "$traktApiUrl/shows/trending?extended=cloud9,full&limit=25&network_ids=41,2018,2566,2567,2597" to
                    "Disney+",
            "$traktApiUrl/shows/trending?extended=cloud9,full&limit=25&network_ids=87" to
                    "Hulu",
            "$traktApiUrl/shows/trending?extended=cloud9,full&limit=25&network_ids=1623" to
                    "Paramount+",
            "$traktApiUrl/shows/trending?extended=cloud9,full&limit=25&network_ids=550,3027" to
                    "Peacock",
        )

    @Suppress("NAME_SHADOWING")
    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {
        val data = AppUtils.parseJson<LinkData>(data)
        val season = data.season
        val episode = data.episode
        val id = data.imdbId
        val mainurl = if (season == null) {
            "$mainUrl/stream/movie/$id.json"
        } else {
            "$mainUrl/stream/series/$id:$season:$episode.json"
        }
        val json= app.get(mainurl).toString()
        val magnetLink = parseStreamsToMagnetLinks(json)
        magnetLink.forEach {
            callback.invoke(
                newExtractorLink(
                    "Torrentio AIO ${it.title}",
                    it.title,
                    it.magnet,
                    INFER_TYPE
                ) {
                    this.referer = ""
                    this.quality = getIndexQuality(it.quality)
                }
            )
        }
        val SubAPI = "https://opensubtitles-v3.strem.io"
        val url = if (season == null) {
            "$SubAPI/subtitles/movie/$id.json"
        } else {
            "$SubAPI/subtitles/series/$id:$season:$episode.json"
        }
        val headers = mapOf(
            "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
            "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
        )
        app.get(url, headers = headers, timeout = 100L).parsedSafe<Subtitles>()?.subtitles?.amap {
            val lan = getLanguage(it.lang) ?: it.lang
            val suburl = it.url
            subtitleCallback.invoke(
                SubtitleFile(
                    lan,  // Use label for the name
                    suburl     // Use extracted URL
                )
            )
        }
        return true
    }

    data class MagnetStream(
        val title: String,
        val quality: String,
        val magnet: String
    )

    private fun parseStreamsToMagnetLinks(jsonString: String): List<MagnetStream> {
        val json = JSONObject(jsonString)
        val streams = json.getJSONArray("streams")

        return (0 until streams.length()).mapNotNull { i ->
            val item = streams.getJSONObject(i)

            val infoHash = item.optString("infoHash")
            if (infoHash.isBlank()) return@mapNotNull null

            val originalName = item.optString("name", "Unnamed")
            val sources = item.optJSONArray("sources") ?: return@mapNotNull null

            val behaviorHints = item.optJSONObject("behaviorHints")
            val bingeGroup = behaviorHints?.optString("bingeGroup").orEmpty()
            bingeGroup.split("|").filter { it.isNotBlank() && it != "Unknown" }

            // Extract quality (simple regex to match "720p", "1080p", "WEB-DL", etc.)
            val qualityRegex = Regex("""\b(4K|2160p|1080p|720p|WEB[-\s]?DL|BluRay|HDRip|DVDRip)\b""", RegexOption.IGNORE_CASE)
            val qualityMatch = qualityRegex.find(originalName)?.value ?: "Unknown"

            // Build magnet link
            val encodedName = URLEncoder.encode(originalName, "UTF-8")
            val trackers = (0 until sources.length()).joinToString("&") {
                val tracker = sources.optString(it)
                "tr=${URLEncoder.encode(tracker, "UTF-8")}"
            }

            val magnet = "magnet:?xt=urn:btih:$infoHash&dn=$encodedName&$trackers"

            MagnetStream(
                title = originalName,
                quality = qualityMatch,
                magnet = magnet
            )
        }
    }
}
