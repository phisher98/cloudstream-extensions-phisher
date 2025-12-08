package com.phisher98

import com.phisher98.StreamPlayExtractor.invokeSubtitleAPI
import com.phisher98.StreamPlayExtractor.invokeWyZIESUBAPI
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.TvType
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.runAllAsync
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.json.JSONObject
import java.net.URLEncoder
import java.nio.charset.StandardCharsets

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
        private const val Uindex = "https://uindex.org"
        private const val Knaben = "https://knaben.org"
        val TRACKER_LIST_URL= listOf(
            "https://raw.githubusercontent.com/ngosang/trackerslist/refs/heads/master/trackers_best.txt",
            "https://raw.githubusercontent.com/ngosang/trackerslist/refs/heads/master/trackers_best_ip.txt",
        )

    }


override suspend fun loadLinks(
    data: String,
    isCasting: Boolean,
    subtitleCallback: (SubtitleFile) -> Unit,
    callback: (ExtractorLink) -> Unit
): Boolean {
    val data = AppUtils.parseJson<LinkData>(data)
    val title = data.title
    val season = data.season
    val episode = data.episode
    val id = data.imdbId
    val year = data.year
    val isAnime = data.isAnime
    var type = TvType.TvSeries
    var anijson: String? = null

    try {
        anijson = app.get("https://api.ani.zip/mappings?imdb_id=$id").toString()
        val mappings = JSONObject(anijson).optJSONObject("mappings")
        val rawtype = mappings?.optString("type", "")
        if (rawtype?.contains("MOVIE", ignoreCase = true) == true) {
            type = TvType.Movie
        }
    } catch (e: Exception) {
        println("Error fetching or parsing mapping: ${e.message}")
    }

    val anidbEid = getAnidbEid(anijson ?: "{}", episode) ?: 0
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
            invoke1337x(
                OnethreethreesevenxAPI,
                title,
                year,
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
        invokeUindex(
            Uindex,
            title,
            year,
            season,
            episode,
            callback)
        },
        /*
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
         */
        {
            if (data.isAnime) invokeAnimetosho(
                anidbEid,
                callback
            )
        },
        {
            if (data.isAnime) invokeTorrentioAnime(
                TorrentioAnimeAPI,
                type,
                season,
                episode,
                callback
            )
        },
        {
            invokeKnaben(Knaben,
                isAnime,
                title,
                year,
                season,
                episode,
                callback)
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
                subtitleCallback
            )
        }
    )
    return true
}
}


suspend fun generateMagnetLink(
    trackerUrls: List<String>,
    hash: String?,
): String {
    require(hash?.isNotBlank() == true)

    val trackers = mutableSetOf<String>()

    trackerUrls.forEach { url ->
        try {
            val response = app.get(url)
            response.text
                .lineSequence()
                .map { it.trim() }
                .filter { it.isNotEmpty() && !it.startsWith("#") }
                .forEach { trackers.add(it) }
        } catch (_: Exception) {
            // ignore bad sources
        }
    }

    return buildString {
        append("magnet:?xt=urn:btih:").append(hash)

        if (hash.isNotBlank()) {
            append("&dn=")
            append(URLEncoder.encode(hash, StandardCharsets.UTF_8.name()))
        }

        trackers
            .take(10) // practical limit
            .forEach { tracker ->
                append("&tr=")
                append(URLEncoder.encode(tracker, StandardCharsets.UTF_8.name()))
            }
    }
}