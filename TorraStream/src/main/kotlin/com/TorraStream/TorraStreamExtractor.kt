package com.TorraStream

import com.TorraStream.TorraStream.Companion.OnethreethreesevenxAPI
import com.TorraStream.TorraStream.Companion.TRACKER_LIST_URL
import com.TorraStream.TorraStream.Companion.TorrentgalaxyAPI
import com.TorraStream.TorraStream.Companion.TorrentioAPI
import com.TorraStream.TorraStream.Companion.TorrentmovieAPI
import com.lagradost.api.Log
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.getQualityFromName

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


suspend fun invokeTorrentmovie(
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
            Log.d("Phisher",magnet)
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