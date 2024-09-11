package com.hexated

import com.lagradost.api.Log
import com.lagradost.cloudstream3.*
import com.lagradost.cloudstream3.utils.AppUtils
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.Qualities
import org.jsoup.nodes.Element
import java.net.URI

class TimefourTv : MainAPI() {
    override var mainUrl = "https://dlhd.so"
    override var name = "Time4tv"
    override val hasDownloadSupport = false
    override val hasMainPage = true
    override val supportedTypes = setOf(
        TvType.Live
    )

    private val homePoster = "https://img.freepik.com/premium-photo/international-sports-day-6-april_10221-18992.jpg"
    private val detailPoster =
        "https://img.freepik.com/premium-photo/international-sports-day-6-april_10221-18992.jpg"

    override val mainPage = mainPageOf(
        "$mainUrl/24-7-channels.php" to "24/7 Channels",
        "$mainUrl/schedule/schedule-generated.json" to "Schedule Channels"
    )

    override suspend fun getMainPage(
        page: Int,
        request: MainPageRequest
    ): HomePageResponse {
        val items = mutableListOf<HomePageList>()
        if (request.name == "24/7 Channels") {
            val req = app.get(request.data)
            mainUrl = getBaseUrl(req.url)
            val res = req.document
            val channels = res.select("div.grid-container div.grid-item").mapNotNull {
                it.toSearchResponse()
            }
            if (channels.isNotEmpty()) items.add(HomePageList(request.name, channels, true))
        } else {
            val res = app.get(request.data).parsedSafe<Map<String, Map<String, ArrayList<Items>>>>()
            res?.forEach { tag ->
                val header = tag.key
                val channels = tag.value.mapNotNull {
                    LiveSearchResponse(
                        it.key,
                        Item(it.key, items = it.value.toJson()).toJson(),
                        this@TimefourTv.name,
                        TvType.Live,
                        posterUrl = homePoster,
                    )
                }
                if (channels.isNotEmpty()) items.add(HomePageList(header, channels, true))
            }
        }

        return newHomePageResponse(items, false)
    }

    private fun Element.toSearchResponse(): LiveSearchResponse {
        val title = this.select("strong").text()
        val href = fixUrl(this.select("a").attr("href"))
        return LiveSearchResponse(
            title,
            Item(title, href).toJson(),
            this@TimefourTv.name,
            TvType.Live,
            posterUrl = homePoster,
        )
    }

    override suspend fun search(query: String): List<SearchResponse> {
        val document = app.get("$mainUrl/24-7-channels.php").document
        return document.select("div.grid-container div.grid-item:contains($query)").mapNotNull {
            it.toSearchResponse()
        }
    }


    override suspend fun load(url: String): LoadResponse {
        val data = AppUtils.parseJson<Item>(url)
        val episodes = if (data.items.isNullOrEmpty()) {
            listOf(Episode(arrayListOf(Channels(data.title, data.url)).toJson()))
        } else {
            val items = AppUtils.parseJson<ArrayList<Items>>(data.items)
            items.mapNotNull { eps ->
                Episode(
                    data = eps.channels?.toJson() ?: return@mapNotNull null,
                    name = "${eps.event} • ${eps.time}",
                    description = eps.channels.map { it.channel_name }.joinToString(" • "),
                    posterUrl = detailPoster,
                )
            }
        }
        return newTvSeriesLoadResponse(
            data.title ?: "",
            url,
            TvType.TvSeries,
            episodes = episodes
        ) {
            posterUrl = homePoster
        }

    }

    override suspend fun loadLinks(
        data: String,
        isCasting: Boolean,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ): Boolean {

        val json = AppUtils.parseJson<ArrayList<Channels>>(data)
        json.apmap {
            val iframe = app.get(
                fixChannelUrl(
                    it.channel_id ?: return@apmap
                )
            ).document.selectFirst("iframe#thatframe")?.attr("src")
                ?: throw ErrorLoadingException("No Iframe Found")
            val host = getBaseUrl(iframe)
            val video = extractVideo(iframe)

            callback.invoke(
                ExtractorLink(
                    this.name,
                    it.channel_name ?: return@apmap,
                    video ?: return@apmap,
                    "$host/",
                    Qualities.Unknown.value,
                    isM3u8 = true,
                )
            )
        }

        return true
    }

    private suspend fun extractVideo(url: String): String? {
        val res = app.get(url, referer = mainUrl)
        return Regex("""source:.*?'(.*)'""").find(res.text)?.groupValues?.getOrNull(
            1
        )?.replace("index","playlist") ?: run {
            val scriptData =
                res.document.selectFirst("div#player")?.nextElementSibling()?.data()
                    ?.substringAfterLast("return(")?.substringBefore(".join")
            scriptData?.removeSurrounding("[", "]")?.replace("\"", "")?.split(",")
                ?.joinToString("")
        }
    }

    private fun fixChannelUrl(url: String): String {
        return if (url.startsWith(mainUrl)) {
            url
        } else {
            "$mainUrl/stream/stream-$url.php"
        }
    }

    private fun getBaseUrl(url: String): String {
        return URI(url).let {
            "${it.scheme}://${it.host}"
        }
    }

    data class Item(
        val title: String? = null,
        val url: String? = null,
        val items: String? = null,
    )

    data class Items(
        val time: String? = null,
        val event: String? = null,
        val channels: ArrayList<Channels>? = arrayListOf(),
    )

    data class Channels(
        val channel_name: String? = null,
        val channel_id: String? = null,
    )

}