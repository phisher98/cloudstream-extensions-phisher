package com.TorraStream

import android.annotation.SuppressLint
import com.TorraStream.TorraStream.Companion.AnimetoshoAPI
import com.TorraStream.TorraStream.Companion.SubtitlesAPI
import com.TorraStream.TorraStream.Companion.TRACKER_LIST_URL
import com.google.gson.Gson
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.ExtractorLinkType
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.getQualityFromName
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
                    val tags = "\\[(.*?)]".toRegex().findAll(title)
                        .map { match -> "[${match.groupValues[1]}]" }
                        .joinToString(" | ")
                    val seeder = "👤\\s*(\\d+)".toRegex().find(title)?.groupValues?.get(1) ?: "0"
                    val provider = "⚙️\\s*([^\\\\]+)".toRegex().find(title)?.groupValues?.get(1)?.trim() ?: "Unknown"
                    "Torrentio | $tags | Seeder: $seeder | Provider: $provider".trim()
                }
            val magnet = generateMagnetLink(TRACKER_LIST_URL, stream.infoHash)
            callback.invoke(
                ExtractorLink(
                    "Torrentio",
                    formattedTitleName ?: stream.name ?: "",
                    magnet,
                    "",
                    getIndexQuality(stream.name),
                    INFER_TYPE,
                )
            )
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
            ExtractorLink(
                "Torrentgalaxy",
                "Torrentgalaxy $title",
                magnet,
                "",
                getIndexQuality(title),
                INFER_TYPE,
            )
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
                    ExtractorLink(
                        "Torrentmovie",
                        "Torrentmovie",
                        "$TorrentmovieAPI/${file}",
                        "",
                        getQualityFromName(quality),
                        INFER_TYPE,
                    )
                )
            }
    }
}


suspend fun invoke1337x(
    OnethreethreesevenxAPI:String? = null,
    title: String? = null,
    year:Int?= null,
    callback: (ExtractorLink) -> Unit
) {

        app.get("$OnethreethreesevenxAPI/category-search/${title?.replace(" ","+")}+$year/Movies/1/").document.select("tbody > tr > td a:nth-child(2)").amap {
            val iframe=OnethreethreesevenxAPI+it.attr("href")
            val doc= app.get(iframe).document
            val magnet=doc.select("#openPopup").attr("href").trim()
            val qualityraw=doc.select("div.box-info ul.list li:contains(Type) span").text()
            val quality=getQuality(qualityraw)
            callback.invoke(
                ExtractorLink(
                    "Torrent1337x $qualityraw",
                    "Torrent1337x $qualityraw",
                    magnet,
                    "",
                    quality,
                    INFER_TYPE,
                )
            )
        }
}

suspend fun invokeTorbox(
    torBoxAPI: String? = null,
    id: String? =null,
    season: Int? = null,
    episode: Int? = null,
    callback: (ExtractorLink) -> Unit
) {
    val url = if(season == null) {
        "$torBoxAPI/38162763-0628-47e2-9f10-db9fa302527e/stream/movie/$id.json"
    }
    else {
        "$torBoxAPI/38162763-0628-47e2-9f10-db9fa302527e/stream/series/$id:$season:$episode.json"
    }
    app.get(url).parsedSafe<TorBox>()?.streams?.amap {
        val magnet=it.magnet ?: return@amap
        val quality=it.resolution?.toIntOrNull()
        val providername=it.behaviorHints?.filename?.substringAfterLast("-")
        callback.invoke(
            ExtractorLink(
                "TorBox $providername",
                "TorBox $providername",
                magnet,
                "",
                quality ?: Qualities.Unknown.value,
                INFER_TYPE,
            )
        )
    }
}

@SuppressLint("DefaultLocale")
suspend fun invokeBitsearch(
    bitSearchApi: String? = null,
    title: String? =null,
    season: Int? = null,
    episode: Int? = null,
    callback: (ExtractorLink) -> Unit
) {
    val url = if(season == null) {
        val fixQuery = title?.replace(" ","+")
        "$bitSearchApi/search?q=$fixQuery"
    }
    else {
        val fixQuery = "$title S${String.format("%02d", season)}E${String.format("%02d", episode)}".replace(" ","+")
        "$bitSearchApi/search?q=$fixQuery"
    }
    val doc = app.get(url, timeout = 10).document
    val searchList = doc.select(".card.search-result.my-2")
    searchList.forEach{item ->
        @Suppress("NAME_SHADOWING") val title = item.select(".title.w-100.truncate a").text()
        val statusElement = item.select(".stats div")
        val downloadSize = statusElement[1].text()
        val seeders = statusElement[2].select("font").text()
        val leechers = statusElement[3].select("font").text()
        val magnetLink = item.select(".dl-magnet").attr("href")
        callback.invoke(
            ExtractorLink(
                "Bitsearch [${title} $downloadSize \uD83D\uDD3C $seeders \uD83D\uDD3D $leechers]",
                "Bitsearch [${title} $downloadSize \uD83D\uDD3C $seeders \uD83D\uDD3D $leechers]",
                magnetLink,
                "",
                getQuality(title),
                ExtractorLinkType.MAGNET,
            )
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
            val magnetLink = generateMagnetLink(TRACKER_LIST_URL,stream.infoHash)
            callback.invoke(
                ExtractorLink(
                    "MediaFusion",
                    stream.description,
                    magnetLink,
                    "",
                    getIndexQuality(stream.description),
                    INFER_TYPE,
                )
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
            val magnetLink = generateMagnetLink(TRACKER_LIST_URL,stream.infoHash)
            callback.invoke(
                ExtractorLink(
                    "ThePirateBay",
                    "ThePirateBay [${stream.title}]",
                    magnetLink,
                    "",
                    getIndexQuality(stream.title),
                    INFER_TYPE,
                )
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
            val magnetLink = generateMagnetLink(TRACKER_LIST_URL,stream.infoHash)
            callback.invoke(
                ExtractorLink(
                    "Peerflix",
                    stream.description,
                    magnetLink,
                    "",
                    getIndexQuality(stream.description),
                    INFER_TYPE,
                )
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
            val formattedTitleName = stream.description
                .let { title ->
                    val tags = "\\[(.*?)]".toRegex().findAll(title)
                        .map { match -> match.groupValues[1] }
                        .joinToString(" | ")

                    val quality = "💿\\s*([^\n]+)".toRegex().find(title)?.groupValues?.get(1)?.trim() ?: "Unknown"
                    val provider = "🔎\\s*([^\n]+)".toRegex().find(title)?.groupValues?.get(1)?.trim() ?: "Unknown"

                    "Comet | $tags | Quality: $quality | Provider: $provider"
                }
                .trim()
            val magnetLink = generateMagnetLink(TRACKER_LIST_URL,stream.infoHash)
            callback.invoke(
                ExtractorLink(
                    "Comet",
                    formattedTitleName,
                    magnetLink,
                    "",
                    getIndexQuality(stream.description),
                    ExtractorLinkType.MAGNET,
                )
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
        .parsedSafe<SubtitlesAPI>()?.subtitles?.amap {
            val lan = getLanguage(it.lang) ?:"Unknown"
            val suburl = it.url
            subtitleCallback.invoke(
                SubtitleFile(
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
                ExtractorLink(
                    "Animetosho",
                    formattedTitleName,
                    magnet,
                    "",
                    getIndexQuality(item.torrentName),
                    INFER_TYPE
                )
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
            ExtractorLink(
                "Torrentio ",
                formattedTitleName ?: "Torrentio",
                magnet,
                "",
                getIndexQuality(stream.name),
                INFER_TYPE,
            )
        )
    }
}
