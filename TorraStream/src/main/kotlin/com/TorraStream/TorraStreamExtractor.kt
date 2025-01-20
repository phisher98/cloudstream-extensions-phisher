package com.TorraStream

import com.TorraStream.TorraStream.Companion.TRACKER_LIST_URL
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
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
        torrentioAPI="$mainUrl/$service=$key"
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
        torrentioAPI= mainUrl
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