package com.phisher98

import com.lagradost.api.Log
import com.lagradost.cloudstream3.ErrorLoadingException
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mainPageOf
import com.lagradost.cloudstream3.utils.AppUtils.parseJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.loadExtractor

class HindiSubAnime : AnimeDekhoProvider() {
    override var mainUrl = "https://hindisubanime.co"
    override var name = "HindiSubAnime"
    override val hasMainPage = true
    override var lang = "hi"

    override val mainPage =
        mainPageOf(
            "/category/shounen/" to "Shounen",
            "/category/action/" to "Action",
            "/category/fantasy/" to "Fantasy",
            "/serie/" to "Series",
        )

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit,
    ): Boolean {
        val media = parseJson<Media>(data)
        val body = app.get(media.url).documentLarge.selectFirst("body")?.attr("class") ?: return false
        val term = Regex("""(?:term|postid)-(\d+)""").find(body)?.groupValues?.get(1)
            ?: throw ErrorLoadingException("no id found")
        for (i in 0..4) {
            val link = app.get("$mainUrl/?trdekho=$i&trid=$term&trtype=${media.mediaType}")
                .documentLarge.selectFirst("iframe")?.attr("src")
                ?: throw ErrorLoadingException("no iframe found")
            Log.d("Phisher", link)
            loadExtractor(link, subtitleCallback, callback)
        }
        return true
    }
}