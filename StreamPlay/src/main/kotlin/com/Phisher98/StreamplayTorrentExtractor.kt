package com.phisher98

import android.annotation.SuppressLint
import com.google.gson.Gson
import com.phisher98.StreamPlayTorrent.Companion.AnimetoshoAPI
import com.phisher98.StreamPlayTorrent.Companion.TRACKER_LIST_URL
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
import com.lagradost.cloudstream3.utils.newExtractorLink

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

suspend fun invokeAnimetosho(
    id: Int? = null,
    callback: (ExtractorLink) -> Unit
) {
    val url = "$AnimetoshoAPI/json?eid=$id&qx=1&q=!(%22DTS%22|%22TrueHD%22|%22[EMBER]%22)((e*|a*|r*|i*|o*|%221080%22)%20!%22720%22%20!%22540%22%20!%22480%22)"
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
                    this.quality = getIndexQuality(item.torrentName)
                }
            )

        }
    }
}


suspend fun invokeTorrentgalaxy(
    TorrentgalaxyAPI: String? = null,
    id: String? = null,
    callback: (ExtractorLink) -> Unit
) {

    val Torrentgalaxy="$TorrentgalaxyAPI/torrents.php?search=$id&lang=0&nox=2&sort=seeders&order=desc"
    app.get(Torrentgalaxy).document.select("div.tgxtablerow.txlight").take(10).map {
        val title=it.select("div.tgxtablecell.clickable-row.click.textshadow a.txlight").attr("title")
        val magnet=it.select("div:nth-child(5) > a:nth-child(2)").attr("href")
        callback.invoke(
            newExtractorLink(
                "Torrentgalaxy",
                "Torrentgalaxy $title",
                url = magnet,
                INFER_TYPE
            ) {
                this.quality = getIndexQuality(title)
            }
        )

    }
}


suspend fun invokeTorrentmovie(
    TorrentmovieAPI: String?=null,
    title: String? = null,
    callback: (ExtractorLink) -> Unit
) {
    app.get("$TorrentmovieAPI/secure/search/$title?limit=20").parsedSafe<Torrentmovie>()?.results?.map {
        val files= mutableListOf<String>()
        files+=it.screenResolution2160p
        files+=it.screenResolution1080p
        files+=it.screenResolution720p
        files.forEach { file ->
            val quality=file.substringAfter("resolution_").substringBefore("ps")
            callback.invoke(
                newExtractorLink(
                    "Torrentmovie",
                    "Torrentmovie",
                    url = "$TorrentmovieAPI/${file}",
                    INFER_TYPE
                ) {
                    this.quality = getQualityFromName(quality)
                }
            )

        }
    }
}


suspend fun invoke1337x(
    OnethreethreesevenxAPI: String? = null,
    title: String? = null,
    year: Int? = null,
    callback: (ExtractorLink) -> Unit
) {
    app.get("$OnethreethreesevenxAPI/category-search/${title?.replace(" ", "+")}+$year/Movies/1/")
        .document.select("tbody > tr > td a:nth-child(2)").amap {
            val iframe = OnethreethreesevenxAPI + it.attr("href")
            val doc = app.get(iframe).document

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
                this.quality = getIndexQuality(stream.name)
            }
        )
    }
}
