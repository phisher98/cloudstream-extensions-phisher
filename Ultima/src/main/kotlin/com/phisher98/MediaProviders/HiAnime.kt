package com.phisher98

import android.os.Build
import androidx.annotation.RequiresApi
import com.phisher98.UltimaMediaProvidersUtils.ServerName
import com.phisher98.UltimaMediaProvidersUtils.commonLinkLoader
import com.phisher98.UltimaUtils.Category
import com.phisher98.UltimaUtils.LinkData
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

class HiAnimeMediaProvider : MediaProvider() {
    override val name = "HiAnime"
    override val domain = "https://hianime.bz"
    override val categories = listOf(Category.ANIME)

    @RequiresApi(Build.VERSION_CODES.O)
    override suspend fun loadContent(
            url: String,
            data: LinkData,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ) {
        data.year ?: return
        val filterUrl = "$url/search?keyword=${fixName(data.title)}&sy=${data.year}"
        val filterRes = app.get(filterUrl).document
        val result = filterRes.selectFirst("div.film-poster > a")?.attr("href") ?: return
        val seasonId = result.substringAfterLast("-")
        val epListResUrl = "$url/ajax/v2/episode/list/$seasonId"
        val epListRes = app.get(epListResUrl).parsed<ApiResponseHTML>()
        if (!epListRes.status) return
        val epId =
                epListRes
                        .html()
                        .selectFirst("div.ss-list > a[data-number=\"${data.episode}\"]")
                        ?.attr("data-id")
                        ?: return
        val serversListResUrl = "$url/ajax/v2/episode/servers?episodeId=$epId"
        val serversListRes = app.get(serversListResUrl).parsed<ApiResponseHTML>()
        if (!serversListRes.status) return
        val servers =
                serversListRes.html().select("div.server-item").map {
                    it.attr("data-type").replaceFirstChar(Char::titlecase) to it.attr("data-id")
                }
        servers.amap { server ->
            val serverResUrl = "$url/ajax/v2/episode/sources?id=${server.second}"
            val serverRes = app.get(serverResUrl).parsed<ServerData>()
            if (serverRes.type == "error") return@amap
            val serverName =
                    when (serverRes.server) {
                        1 -> ServerName.Megacloud
                        4 -> ServerName.Megacloud
                        else -> return@amap
                    }
            commonLinkLoader(
                name,
                serverName,
                serverRes.link,
                null,
                server.first,
                subtitleCallback,
                callback
            )
        }
    }

    // #region - Encryption and Decryption handlers
    // #endregion - Encryption and Decryption handlers

    // #region - Data classes
    data class ApiResponseHTML(
            @JsonProperty("status") val status: Boolean,
            @JsonProperty("html") val result: String
    ) {
        fun html(): Document {
            return Jsoup.parse(result)
        }
    }

    data class ServerData(
            @JsonProperty("type") val type: String,
            @JsonProperty("link") val link: String,
            @JsonProperty("server") val server: Int
    )
    // #endregion - Data classes

    private fun fixName(name: String?):String? {
        return when(name) {
            "DAN DA DAN" -> "Dandadan"
            else -> name
        }
    }
}
