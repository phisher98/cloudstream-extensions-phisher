package com.phisher98

import com.phisher98.StreamPlayExtractor.invokeSubtitleAPI
import com.phisher98.StreamPlayExtractor.invokeWyZIESUBAPI
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.argamap
import com.lagradost.cloudstream3.runAllAsync
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink

@Suppress("NAME_SHADOWING")
class StreamPlayTorrent() : StreamPlay() {
    override var name = "StreamPlay-Torrent"
    override var supportedTypes = setOf(TvType.Movie, TvType.TvSeries, TvType.Anime, TvType.AsianDrama,TvType.Torrent)
    override var lang = "en"
    override val hasMainPage = true
    override val hasQuickSearch = false

    companion object
    {
        const val TorrentioAPI="https://torrentio.strem.fun/providers=yts,eztv,rarbg,1337x,thepiratebay,kickasstorrents,torrentgalaxy,magnetdl%7Csort=seeders"
        const val TorrentgalaxyAPI="https://torrentgalaxy.to"
        const val TorrentmovieAPI="https://torrentmovie.net"
        const val OnethreethreesevenxAPI="https://1337x.to"
        const val TorBoxAPI="https://stremio.torbox.app"
        const val BitsearchApi="https://bitsearch.to"
        const val MediafusionApi="https://mediafusion.elfhosted.com"
        const val CometAPI = "https://comet.elfhosted.com"
        const val ThePirateBayApi="https://thepiratebay-plus.strem.fun"
        const val PeerflixApi="https://peerflix.mov"
        const val AnimetoshoAPI="https://feed.animetosho.org"
        const val TorrentioAnimeAPI="https://torrentio.strem.fun/providers=nyaasi,tokyotosho,anidex%7Csort=seeders"
        const val TRACKER_LIST_URL="https://newtrackon.com/api/stable"

    }


override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    val data= AppUtils.parseJson<LinkData>(data)
    val title=data.title
    val season =data.season
    val episode =data.episode
    val id =data.imdbId
    val year=data.year
    val anijson=app.get("https://api.ani.zip/mappings?imdb_id=$id").toString()
    val anidbEid = getAnidbEid(anijson, episode) ?: 0
    runAllAsync(
        {
            invokeTorrentio(
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
            invokeBitsearch(
                BitsearchApi,
                title,
                season,
                episode,
                callback
            )

        },
        {
            invokeMediaFusion(
                MediafusionApi,
                id,
                season,
                episode,
                callback
            )

        },
        {
            invokeThepiratebay(
                ThePirateBayApi,
                id,
                season,
                episode,
                callback
            )

        },
        {
            invokePeerFlix(
                PeerflixApi,
                id,
                season,
                episode,
                callback
            )
        },
        {
            invokeComet(
                CometAPI,
                id,
                season,
                episode,
                callback
            )
        },
        {
            if (data.isAnime) invokeAnimetosho(
                anidbEid,
                callback
            )
        },
        {
            if (data.isAnime) invokeTorrentioAnime(
                TorrentioAnimeAPI,
                id,
                season,
                episode,
                callback
            )
        },

        //Source till here
        //Subtitles Invokes
        {
            invokeWyZIESUBAPI(
                id,
                season,
                episode,
                subtitleCallback,
            )
        },
        {
            invokeSubtitleAPI(
                id,
                season,
                episode,
                subtitleCallback,
                callback
            )
        }
    )
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

/*
fun generateMagnetLinkFromSource(trackersList: List<String>, hash: String?): String {
    // Fetch the content of the file from the provided URL

    // Build the magnet link
    return buildString {
        append("magnet:?xt=urn:btih:$hash")
        for (index in 0 until trackersList.size - 1) {
            if (trackersList[index].isNotBlank()) {
                append("&tr=").append(trackersList[index].trim())
            }
        }
    }
}
 */
