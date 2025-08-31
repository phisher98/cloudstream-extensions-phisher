package com.phisher98

import com.phisher98.UltimaUtils.Category
import com.phisher98.UltimaUtils.LinkData
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.INFER_TYPE
import com.lagradost.cloudstream3.utils.Qualities
import com.lagradost.cloudstream3.utils.newExtractorLink
import org.mozilla.javascript.Scriptable

class MultiEmbededAPIProvider : MediaProvider() {
    override val name = "MultiEmbeded API"
    override val domain = "https://multiembed.mov"
    override val categories = listOf(Category.MEDIA)

    override suspend fun loadContent(
            url: String,
            data: LinkData,
            subtitleCallback: (SubtitleFile) -> Unit,
            callback: (ExtractorLink) -> Unit
    ) {
        val mediaUrl =
                if (data.season == null) {
                    "$url/directstream.php?video_id=${data.imdbId}"
                } else {
                    "$url/directstream.php?video_id=${data.imdbId}&s=${data.season}&e=${data.episode}"
                }
        val res = app.get(mediaUrl, referer = mediaUrl).document
        val script =
                res.selectFirst("script:containsData(function(h,u,n,t,e,r))")?.data().toString()
        if (script.isNotEmpty()) {
            val firstJS =
                    """
        var globalArgument = null;
        function Playerjs(arg) {
        globalArgument = arg;
        };
        """.trimIndent()
            val rhino = org.mozilla.javascript.Context.enter()
            rhino.setInterpretedMode(true)
            val scope: Scriptable = rhino.initSafeStandardObjects()
            rhino.evaluateString(scope, firstJS + script, "JavaScript", 1, null)
            val file =
                    (scope.get("globalArgument", scope).toJson())
                            .toString()
                            .substringAfter("file\":\"")
                            .substringBefore("\",")
            callback.invoke(
                newExtractorLink(
                    "MultiEmbeded API",
                    "MultiEmbeded API",
                    file,
                    INFER_TYPE
                )
                {
                    this.quality = Qualities.Unknown.value
                }
            )
        }
    }

    // #region - Encryption and Decryption handlers
    // #endregion - Encryption and Decryption handlers

    // #region - Data classes
    data class EMovieServer(
            @JsonProperty("value") val value: String? = null,
    )

    data class EMovieSources(
            @JsonProperty("file") val file: String? = null,
    )

    data class EMovieTraks(
            @JsonProperty("file") val file: String? = null,
            @JsonProperty("label") val label: String? = null,
    )
    // #endregion - Data classes

}
