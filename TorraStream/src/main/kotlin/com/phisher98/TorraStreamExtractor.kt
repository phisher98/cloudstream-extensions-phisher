package com.phisher98

import android.util.Log
import com.phisher98.TorraStream.Companion.AnimetoshoAPI
import com.phisher98.TorraStream.Companion.SubtitlesAPI
import com.phisher98.TorraStream.Companion.TRACKER_LIST_URL
import com.google.gson.Gson
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.newSubtitleFile
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink
import java.util.Locale

suspend fun invokeTorrentio(
    mainUrl:String,
    id: String? = null,
    season: Int? = null,
    episode: Int? = null,
    callback: (ExtractorLink) -> Unit
) {
        val torrentioAPI:String = mainUrl
        val url = if(season == null) {
            "$torrentioAPI/stream/movie/$id.json"
        }
        else {
            "$torrentioAPI/stream/series/$id:$season:$episode.json"
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

        val magnet = generateMagnetLink(TRACKER_LIST_URL, stream.infoHash)

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


suspend fun invokeTorrentioDebian(
    mainUrl: String,
    id: String? = null,
    season: Int? = null,
    episode: Int? = null,
    callback: (ExtractorLink) -> Unit
) {
    val url = if (season == null) {
        "$mainUrl/stream/movie/$id.json"
    } else {
        "$mainUrl/stream/series/$id:$season:$episode.json"
    }
    val res = app.get(url).parsedSafe<DebianRoot>()
    res?.streams?.forEach { stream ->
        val fileUrl = stream.url

        val size = Regex("""(\d+(?:[.,]\d+)?)\s*(GB|MB)""", RegexOption.IGNORE_CASE)
            .find(stream.title)
            ?.let { m -> "${m.groupValues[1].replace(',', '.')} ${m.groupValues[2].uppercase()}" }

        val seedersNum = Regex("""(\d+)$""").find(stream.title)?.groupValues?.get(1)

        val name = stream.behaviorHints.filename ?: stream.title.substringBefore("\n")
        val cache = Regex("""\[(.*?)]""").find(stream.name)?.groupValues?.get(1)
        val formattedName = name
            .substringBeforeLast('.')
            .replace('.', ' ')
            .trim()

        val parts = listOfNotNull(
            size?.let { "📦 $it" },
            seedersNum?.let { "🌱 $it" }
        )

        val suffix = if (parts.isNotEmpty()) " | ${parts.joinToString(" | ")}" else ""

        val finalTitle = "Torrentio+ | [$cache] | $formattedName$suffix"

        callback.invoke(
            newExtractorLink(
                "Torrentio+ [$cache]",
                finalTitle,
                url = fileUrl,
                INFER_TYPE
            ) {
                this.referer = ""
                this.quality = getIndexQuality(stream.name)
            }
        )
    }
}


suspend fun invokeTorrentioAnimeDebian(
    mainUrl: String,
    type: TvType,
    id: Int? = null,
    episode: Int? = null,
    callback: (ExtractorLink) -> Unit
) {
    val url = if (type == TvType.Movie) {
        "$mainUrl/stream/movie/kitsu:$id.json"
    } else {
        "$mainUrl/stream/series/kitsu:$id:$episode.json"
    }
    val res = app.get(url).parsedSafe<DebianRoot>()
    res?.streams?.forEach { stream ->
        val fileUrl = stream.url

        val size = Regex("""(\d+(?:[.,]\d+)?)\s*(GB|MB)""", RegexOption.IGNORE_CASE)
            .find(stream.title)
            ?.let { m -> "${m.groupValues[1].replace(',', '.')} ${m.groupValues[2].uppercase()}" }

        val seedersNum = Regex("""(\d+)$""").find(stream.title)?.groupValues?.get(1)

        val name = stream.behaviorHints.filename ?: stream.title.substringBefore("\n")
        val cache = Regex("""\[(.*?)]""").find(stream.name)?.groupValues?.get(1)
        val formattedName = name
            .substringBeforeLast('.')
            .replace('.', ' ')
            .trim()

        val parts = listOfNotNull(
            size?.let { "📦 $it" },
            seedersNum?.let { "🌱 $it" }
        )

        val suffix = if (parts.isNotEmpty()) " | ${parts.joinToString(" | ")}" else ""

        val finalTitle = "Torrentio+ Anime | [$cache] | $formattedName$suffix"

        callback.invoke(
            newExtractorLink(
                "Torrentio+ [$cache]",
                finalTitle,
                url = fileUrl,
                INFER_TYPE
            ) {
                this.referer = ""
                this.quality = getIndexQuality(stream.name)
            }
        )
    }
}


suspend fun invokeTorrentioAnime(
    mainUrl: String,
    type: TvType,
    id: Int? = null,
    episode: Int? = null,
    callback: (ExtractorLink) -> Unit
) {
    val url = if (type == TvType.Movie) {
        "$mainUrl/stream/movie/kitsu:$id.json"
    } else {
        "$mainUrl/stream/series/kitsu:$id:$episode.json"
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

        val magnet = generateMagnetLink(TRACKER_LIST_URL, stream.infoHash)
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


suspend fun invoke1337x(
    OnethreethreesevenxAPI: String? = null,
    title: String? = null,
    year: Int? = null,
    callback: (ExtractorLink) -> Unit
) {
    app.get("$OnethreethreesevenxAPI/category-search/${title?.replace(" ", "+")}+$year/Movies/1/")
        .documentLarge.select("tbody > tr > td a:nth-child(2)").amap {
            val iframe = OnethreethreesevenxAPI + it.attr("href")
            val doc = app.get(iframe).documentLarge

            val magnet = doc.select("#openPopup").attr("href").trim()
            val qualityRaw = doc.select("div.box-info ul.list li:contains(Type) span").text()
            val quality = getQuality(qualityRaw)

            val size = doc.select("div.box-info ul.list li:contains(Total size) span").text()
            val language = doc.select("div.box-info ul.list li:contains(Language) span").text()
            val seeders = doc.select("div.box-info ul.list li:contains(Seeders) span.seeds").text()

            val displayName = buildString {
                append("Torrent1337x $qualityRaw")
                if (size.isNotBlank()) append(" | Size: $size")
                if (language.isNotBlank()) append(" | Lang: $language")
                if (seeders.isNotBlank()) append(" | 🟢$seeders")
            }

            callback.invoke(
                newExtractorLink(
                    "Torrent1337x",
                    displayName,
                    url = magnet,
                    INFER_TYPE
                ) {
                    this.referer = ""
                    this.quality = quality
                }
            )
        }
}


suspend fun invokeMediaFusion(
    mediaFusionApi: String? = null,
    imdbId: String? =null,
    season: Int? = null,
    episode: Int? = null,
    callback: (ExtractorLink) -> Unit
) {
    try {
        val url = if(season == null) {
            "$mediaFusionApi/stream/movie/$imdbId.json"
        }
        else {
            "$mediaFusionApi/stream/series/$imdbId:$season:$episode.json"
        }
        val res = app.get(url, timeout = 10).parsedSafe<MediafusionResponse>()
        for(stream in res?.streams!!)
        {
            val magnetLink = generateMagnetLink(TRACKER_LIST_URL,stream.infoHash).trim()
            val qualityFromName = getIndexQuality(stream.name)

            callback.invoke(
                newExtractorLink(
                    "MediaFusion",
                    stream.description,
                    url = magnetLink,
                    INFER_TYPE
                ) {
                    this.referer = ""
                    this.quality = qualityFromName
                }
            )
        }
    } catch (_: Exception) { }
}

suspend fun invokeThepiratebay(
    thepiratebayApi: String? = null,
    imdbId: String? =null,
    season: Int? = null,
    episode: Int? = null,
    callback: (ExtractorLink) -> Unit
) {
    try {
        val url = if(season == null) {
            "$thepiratebayApi/stream/movie/$imdbId.json"
        }
        else {
            "$thepiratebayApi/stream/series/$imdbId:$season:$episode.json"
        }
        val res = app.get(url, timeout = 10).parsedSafe<TBPResponse>()
        for(stream in res?.streams!!)
        {
            val magnetLink = generateMagnetLink(TRACKER_LIST_URL,stream.infoHash).trim()
            callback.invoke(
                newExtractorLink(
                    "ThePirateBay",
                    "ThePirateBay [${stream.title}]",
                    url = magnetLink,
                    INFER_TYPE
                ) {
                    this.referer = ""
                    this.quality = getIndexQuality(stream.title)
                }
            )
        }
    } catch (_: Exception) { }
}

suspend fun invokePeerFlix(
    peerflixApi: String? = null,
    imdbId: String? =null,
    season: Int? = null,
    episode: Int? = null,
    callback: (ExtractorLink) -> Unit
) {
    try {
        val url = if (season == null) {
            "$peerflixApi/stream/movie/$imdbId.json"
        } else {
            "$peerflixApi/stream/series/$imdbId:$season:$episode.json"
        }
        val res = app.get(url, timeout = 10).parsedSafe<PeerflixResponse>()
        for (stream in res?.streams!!) {
            val magnetLink = generateMagnetLink(TRACKER_LIST_URL,stream.infoHash).trim()
            callback.invoke(
                newExtractorLink(
                    "Peerflix",
                    stream.name,
                    url = magnetLink,
                    INFER_TYPE
                ) {
                    this.referer = ""
                    this.quality = getIndexQuality(stream.description)
                }
            )
        }
    } catch (_: Exception) {
    }
}


suspend fun invokeComet(
    CometAPI: String? = null,
    imdbId: String? =null,
    season: Int? = null,
    episode: Int? = null,
    callback: (ExtractorLink) -> Unit
) {
    try {
        val url = if(season == null) {
            "$CometAPI/stream/movie/$imdbId.json"
        }
        else {
            "$CometAPI/stream/series/$imdbId:$season:$episode.json"
        }
        val res = app.get(url, timeout = 10).parsedSafe<MediafusionResponse>()
        for(stream in res?.streams!!)
        {
            val formattedTitleName = stream.description.let { title ->
                val tags = "\\[(.*?)]".toRegex()
                    .findAll(title)
                    .map { it.groupValues[1] }
                    .joinToString(" | ")
                    .takeIf { it.isNotBlank() }

                val quality = "💿\\s*([^\n]+)".toRegex()
                    .find(title)
                    ?.groupValues?.getOrNull(1)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() && it != "Unknown" }

                val provider = "🔎\\s*([^\n]+)".toRegex()
                    .find(title)
                    ?.groupValues?.getOrNull(1)
                    ?.trim()
                    ?.takeIf { it.isNotEmpty() && it != "Unknown" }

                buildString {
                    append("Comet")
                    if (!tags.isNullOrEmpty()) append(" | $tags")
                    if (!quality.isNullOrEmpty()) append(" | Quality: $quality")
                    if (!provider.isNullOrEmpty()) append(" | Provider: $provider")
                }
            }

            val magnetLink = generateMagnetLink(TRACKER_LIST_URL,stream.infoHash)
            callback.invoke(
                newExtractorLink(
                    "Comet",
                    formattedTitleName,
                    url = magnetLink,
                    ExtractorLinkType.MAGNET
                ) {
                    this.referer = ""
                    this.quality = getIndexQuality(stream.description)
                }
            )
        }
    } catch (_: Exception) { }
}

suspend fun invokeSubtitleAPI(
    id: String? = null,
    season: Int? = null,
    episode: Int? = null,
    subtitleCallback: (SubtitleFile) -> Unit,
) {
    val url = if (season == null) {
        "$SubtitlesAPI/subtitles/movie/$id.json"
    } else {
        "$SubtitlesAPI/subtitles/series/$id:$season:$episode.json"
    }
    val headers = mapOf(
        "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
    )
    app.get(url, headers = headers, timeout = 100L)
        .parsedSafe<SubtitlesAPI>()?.subtitles?.amap { it ->
            val lan = getLanguage(it.lang) ?:"Unknown"
            val suburl = it.url
            subtitleCallback.invoke(
                newSubtitleFile(
                    lan.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale.getDefault()) else it.toString() },  // Use label for the name
                    suburl     // Use extracted URL
                )
            )
        }
}

suspend fun invokeAnimetosho(
    id: Int? = null,
    callback: (ExtractorLink) -> Unit
) {
    val url = "$AnimetoshoAPI/json?eid=$id&qx=1&q=!(%22DTS%22%7C%22TrueHD%22)((e*%7Ca*%7Cr*%7Ci*%7Co*%7C%221080%22)%20!%22540%22%20!%22480%22)%22%0D%0A"
    val jsonResponse = app.get(url).toString()
    val parsedList = Gson().fromJson(jsonResponse, Array<AnimetoshoItem>::class.java)?.toList() ?: emptyList()
    parsedList.sortedByDescending { it.seeders }.forEach { item ->
        item.magnetUri.let { magnet ->
            val formattedTitleName = item.torrentName
                .let { title ->
                    val tags = "\\[(.*?)]".toRegex().findAll(title)
                        .map { match -> "[${match.groupValues[1]}]" }
                        .joinToString(" | ")
                    val seeder = "👤\\s*(\\d+)".toRegex().find(title)?.groupValues?.get(1) ?: ""
                    "Animetosho | $tags | Seeder: $seeder".trim()
                }
            callback.invoke(
                newExtractorLink(
                    "Animetosho",
                    formattedTitleName,
                    url = magnet,
                    INFER_TYPE
                ) {
                    this.referer = ""
                    this.quality = getIndexQuality(item.torrentName)
                }
            )
        }
    }
}

suspend fun invokeTorrentioAnime(
    mainUrl:String,
    id: String? = null,
    season: Int? = null,
    episode: Int? = null,
    callback: (ExtractorLink) -> Unit
) {
    val torrentioAPI:String = mainUrl
    val url = if(season == null) {
        "$torrentioAPI/stream/movie/$id.json"
    }
    else {
        "$torrentioAPI/stream/series/$id:$season:$episode.json"
    }
    val headers = mapOf(
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
        "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
    )
    val res = app.get(url, headers = headers, timeout = 100L).parsedSafe<TorrentioResponse>()
    res?.streams?.forEach { stream ->
        val magnet = generateMagnetLink(TRACKER_LIST_URL, stream.infoHash)
        val formattedTitleName = stream.title
            ?.let { title ->
                val tags = "\\[(.*?)]".toRegex().findAll(title)
                    .map { match -> "[${match.groupValues[1]}]" }
                    .joinToString(" | ")
                val seeder = "👤\\s*(\\d+)".toRegex().find(title)?.groupValues?.get(1) ?: "0"
                val provider = "⚙️\\s*([^\\\\]+)".toRegex().find(title)?.groupValues?.get(1)?.trim() ?: "Unknown"
                "Torrentio | $tags | Seeder: $seeder | Provider: $provider".trim()
            }

        callback.invoke(
            newExtractorLink(
                "Torrentio ",
                formattedTitleName ?: "Torrentio",
                url = magnet,
                INFER_TYPE
            ) {
                this.referer = ""
                this.quality = getIndexQuality(stream.name)
            }
        )
    }
}

suspend fun invokeAIOStreamsDebian(
    mainUrl:String,
    id: String? = null,
    season: Int? = null,
    episode: Int? = null,
    callback: (ExtractorLink) -> Unit
) {
    val mainurl = if (season == null) {
        "${mainUrl.substringBeforeLast("/manifest.json")}/stream/movie/$id.json"
    } else {
        "${mainUrl.substringBeforeLast("/manifest.json")}/stream/series/$id:$season:$episode.json"
    }
    app.get(mainurl).parsedSafe<AIODebian>()?.streams?.map {
        val qualityRegex = Regex("""\b(4K|2160p|1080p|720p|WEB[-\s]?DL|BluRay|HDRip|DVDRip)\b""", RegexOption.IGNORE_CASE)
        val qualityMatch = qualityRegex.find(it.name)?.value ?: "Unknown"
        callback.invoke(
            newExtractorLink(
                "Torrentio AIO Debian ${getIndexQuality(qualityMatch)}",
                it.behaviorHints.filename,
                it.url,
                INFER_TYPE
            ) {
                this.referer = ""
                this.quality = getIndexQuality(qualityMatch)
            }
        )
    }
}

suspend fun invokeAIOStreams(
    mainUrl:String,
    id: String? = null,
    season: Int? = null,
    episode: Int? = null,
    callback: (ExtractorLink) -> Unit
) {
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
}

suspend fun invokeDebianTorbox(
    torBoxAPI: String,
    key: String,
    id: String? =null,
    season: Int? = null,
    episode: Int? = null,
    callback: (ExtractorLink) -> Unit
) {
    val url = if (season == null) {
        "$torBoxAPI/$key/stream/movie/$id.json"
    } else {
        "$torBoxAPI/$key/stream/series/$id:$season:$episode.json"
    }

    val response = app.get(url, timeout = 10_000).parsedSafe<TorBoxDebian>() ?: return

    response.streams.forEach { stream ->

        val resolution = extractResolutionFromDescription(stream.description)

        val sourceName = stream.name
            .substringBeforeLast("(")
            .trim()
            .ifBlank { "TorBox" }

        val cache = Regex("""\((.*?)\)""").find(stream.name)
            ?.groupValues?.get(1)
            ?.takeIf { it == "Instant" }
            ?: "TorBox Download"

        val displayName = buildString {
            append("TorBox+ | [$cache] | ")
            val rawName = stream.behaviorHints.filename
            val baseName = rawName
                .substringBeforeLast(".")
                .replace(".", " ")
                .trim()

            if (baseName.isNotBlank())
                append(baseName)

            // --- filesize ---
            val fileSize = Regex("Size:\\s*([^|\\n]+)")
                .find(stream.description)
                ?.groupValues?.get(1)
                ?.trim()
            if (!fileSize.isNullOrBlank())
                append(" | 📦 $fileSize")

            // --- seeders ---
            val seeders = Regex("Seeders:\\s*(\\d+)")
                .find(stream.description)
                ?.groupValues?.get(1)
                ?.trim()
            if (!seeders.isNullOrBlank())
                append(" | 🌱 $seeders")

        }.trim()


        callback(
            newExtractorLink(
                "$sourceName [$cache]",
                displayName,
                url = stream.url,
                INFER_TYPE
            ).apply {
                referer = ""
                this.quality = getQualityFromName(resolution)
            }
        )
    }

}


suspend fun invokeUindex(
    uindex: String,
    title: String? = null,
    year: Int? = null,
    season: Int? = null,
    episode: Int? = null,
    callback: (ExtractorLink) -> Unit
) {
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
    knaben: String,
    isAnime: Boolean,
    title: String? = null,
    year: Int? = null,
    season: Int? = null,
    episode: Int? = null,
    callback: (ExtractorLink) -> Unit
) {
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
        isAnime -> "6000000"
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



suspend fun invokeTorboxAnimeDebian(
    mainUrl: String,
    key: String,
    type: TvType,
    id: Int? = null,
    episode: Int? = null,
    callback: (ExtractorLink) -> Unit
) {
    val url = if (type == TvType.Movie) {
        "$mainUrl/$key/stream/movie/kitsu:$id.json"
    } else {
        "$mainUrl/$key/stream/series/kitsu:$id:$episode.json"
    }
    val res = app.get(url, timeout = 10_000).parsedSafe<DebianRoot>()
    res?.streams?.forEach { stream ->
        val fileUrl = stream.url

        val size = Regex("""(\d+(?:[.,]\d+)?)\s*(GB|MB)""", RegexOption.IGNORE_CASE)
            .find(stream.title)
            ?.let { m -> "${m.groupValues[1].replace(',', '.')} ${m.groupValues[2].uppercase()}" }

        val seedersNum = Regex("""(\d+)$""").find(stream.title)?.groupValues?.get(1)

        val name = stream.behaviorHints.filename ?: stream.title.substringBefore("\n")

        val formattedName = name
            .substringBeforeLast('.')
            .replace('.', ' ')
            .trim()
        val cache = Regex("""\((.*?)\)""").find(stream.name)
            ?.groupValues?.get(1)
            ?.takeIf { it == "Instant" }
            ?: "TorBox Download"

        val parts = listOfNotNull(
            size?.let { "📦 $it" },
            seedersNum?.let { "🌱 $it" }
        )

        val suffix = if (parts.isNotEmpty()) " | ${parts.joinToString(" | ")}" else ""

        val finalTitle = "TorBox+ Anime | [$cache] | $formattedName$suffix"

        callback.invoke(
            newExtractorLink(
                "TorBox+ [$cache]",
                finalTitle,
                url = fileUrl,
                INFER_TYPE
            ) {
                this.referer = ""
                this.quality = getIndexQuality(stream.name)
            }
        )
    }
}
