package com.phisher98

import com.phisher98.UltimaMediaProvidersUtils.ServerName
import com.phisher98.UltimaMediaProvidersUtils.commonLinkLoader
import com.phisher98.UltimaMediaProvidersUtils.createSlug
import com.phisher98.UltimaMediaProvidersUtils.getBaseUrl
import com.phisher98.UltimaUtils.Category
import com.phisher98.UltimaUtils.LinkData
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.amap
import com.lagradost.cloudstream3.apmap
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorLink

class MultiMoviesProvider : MediaProvider() {
    override val name = "MultiMovies"
    override val domain = "https://multimovies.coupons"
    override val categories = listOf(Category.MEDIA)
    private val xmlHeader = mapOf("X-Requested-With" to "XMLHttpRequest")

    override suspend fun loadContent(
            url: String,
            data: LinkData,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ) {
        val multimoviesAPI = getDomains()?.multiMovies ?: return ?: domain

        val fixTitle = data.title.createSlug()
        val mediaurl =
                if (data.season == null) {
                    "$multimoviesAPI/movies/$fixTitle"
                } else {
                    "$multimoviesAPI/episodes/$fixTitle-${data.season}x${data.episode}"
                }
        val req = app.get(mediaurl).document
        req.select("ul#playeroptionsul li").amap {
            val id = it.attr("data-post")
            val nume = it.attr("data-nume")
            val type = it.attr("data-type")
            if (nume.contains("trailer")) return@amap
            val apiUrl = "$url/wp-admin/admin-ajax.php"
            val postData =
                mapOf(
                    "action" to "doo_player_ajax",
                    "post" to id,
                    "nume" to nume,
                    "type" to type
                )
            val source =
                app.post(url = apiUrl, data = postData, referer = url, headers = xmlHeader)
                    .parsed<ResponseHash>()
                    .embed_url
            val link = source.substringAfter("\"").substringBefore("\"")
            val domain = getBaseUrl(link)
            val serverName =
                when (domain) {
                    "https://aa.clonimeziud" -> ServerName.Vidhide
                    "https://server2.shop" -> ServerName.Vidhide
                    "https://multimovies.cloud" -> ServerName.StreamWish
                    "https://allinonedownloader.fun" -> ServerName.StreamWish
                    else -> ServerName.NONE
                }
            commonLinkLoader(name, serverName, link, null, null, subtitleCallback, callback)
        }
    }

    // #region - Encryption and Decryption handlers
    // #endregion - Encryption and Decryption handlers

    // #region - Data classes

    data class ResponseHash(
            @JsonProperty("embed_url") val embed_url: String,
            @JsonProperty("key") val key: String? = null,
            @JsonProperty("type") val type: String? = null,
    )
    // #endregion - Data classes
}
