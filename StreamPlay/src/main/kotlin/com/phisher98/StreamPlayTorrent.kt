package com.Phisher98

import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.argamap
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink

class StreamPlayTorrent() : StreamPlay() {
    override var name = "StreamPlay-Torrent"
    override var supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.AsianDrama,TvType.Torrent)
    override var lang = "en"
    override val hasMainPage = true
    override val hasQuickSearch = false

    companion object
    {
        const val TorrentioAPI="https://torrentio.strem.fun"
        const val TorrentgalaxyAPI="https://torrentgalaxy.to"
        const val TorrentmovieAPI="https://torrentmovie.net"
        const val OnethreethreesevenxAPI="https://1337x.to"
        const val TorBoxAPI="https://stremio.torbox.app"
        const val Animetosho="https://feed.animetosho.org/json?eid"
        const val TRACKER_LIST_URL="https://raw.githubusercontent.com/ngosang/trackerslist/refs/heads/master/trackers_all.txt"

    }


override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    val data= AppUtils.parseJson<LinkData>(data)
    val epid=data.epid
    val title=data.title
    val season =data.season
    val episode =data.episode
    val id =data.imdbId
    val year=data.year

    argamap(
        {
            invokeTorrastream(
                TorrentioAPI,
                id,
                season,
                episode,
                callback
            )
        },
        {
            invokeTorrentgalaxy(
                TorrentgalaxyAPI,
                id,
                callback
            )
        },
        {
            invokeTorrentmovie(
                TorrentmovieAPI,
                title,
                callback
            )
        },
        {
            invoke1337x(
                OnethreethreesevenxAPI,
                title,
                year,
                callback
            )
        },
        {
            invokeTorbox(
                TorBoxAPI,
                id,
                season,
                episode,
                callback
            )

        },
        {
            invokeAnimetosho(
                Animetosho,
                epid,
                callback
            )
        },
    )
    val SubAPI="https://opensubtitles-v3.strem.io"
    val url = if(season == null) {
        "$SubAPI/subtitles/movie/$id.json"
    }
    else {
        "$SubAPI/subtitles/series/$id:$season:$episode.json"
    }
    val headers = mapOf(
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8",
        "User-Agent" to "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/126.0.0.0 Safari/537.36",
    )
    app.get(url, headers = headers, timeout = 100L).parsedSafe<Subtitles>()?.subtitles?.amap {
        val lan=getLanguage(it.lang)
        val suburl=it.url
        subtitleCallback.invoke(
            SubtitleFile(
                lan,  // Use label for the name
                suburl     // Use extracted URL
            )
        )
    }
    return true
}
}

suspend fun generateMagnetLink(url: String, hash: String?): String {
    // Fetch the content of the file from the provided URL
    val response = app.get(url)
    val trackerList = response.text.trim().split("\n") // Assuming each tracker is on a new line

    // Build the magnet link
    return buildString {
        append("magnet:?xt=urn:btih:$hash")
        trackerList.forEach { tracker ->
            if (tracker.isNotBlank()) {
                append("&tr=").append(tracker.trim())
            }
        }
    }
}
