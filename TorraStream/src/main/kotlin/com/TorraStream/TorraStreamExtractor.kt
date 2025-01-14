package com.TorraStream

import com.TorraStream.TorraStream.Companion.TRACKER_LIST_URL
import com.TorraStream.TorraStream.Companion.TorrentgalaxyAPI
import com.TorraStream.TorraStream.Companion.TorrentioAPI
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE

suspend fun invokeTorrastream(
    mainUrl:String,
    id: String? = null,
    season: Int? = null,
    episode: Int? = null,
    callback: (ExtractorLink) -> Unit
) {
    val torrentioAPI:String
    if (mainUrl.contains(","))
    {
        val splitdata = mainUrl.split(",")
        val service = splitdata[0]
        val key= splitdata[1]
        torrentioAPI="$TorrentioAPI/$service=$key"
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
        val res = app.get(url, headers = headers, timeout = 100L).parsedSafe<DebianRoot>()
        res?.streams?.forEach { stream ->
            callback.invoke(
                ExtractorLink(
                    "Torrentio",
                    stream.title,
                    stream.url,
                    "",
                    getIndexQuality(stream.name),
                    INFER_TYPE,
                )
            )
        }
    }
    else
    {
        torrentioAPI= TorrentioAPI
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
            callback.invoke(
                ExtractorLink(
                    "Torrentio",
                    stream.title ?: stream.name ?: "",
                    magnet,
                    "",
                    getIndexQuality(stream.name),
                    INFER_TYPE,
                )
            )
        }
    }
}

suspend fun invokeTorrentgalaxy(
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