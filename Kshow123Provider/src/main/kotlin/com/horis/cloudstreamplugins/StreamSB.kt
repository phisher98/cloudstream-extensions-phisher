package com.horis.cloudstreamplugins

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.SubtitleFile
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.ExtractorApi
import com.lagradost.cloudstream3.utils.ExtractorLink
import com.lagradost.cloudstream3.utils.M3u8Helper

class Streamsss : StreamSB2() {
    override var mainUrl = "https://streamsss.net"
}


open class StreamSB2 : ExtractorApi() {
    override var name = "StreamSB"
    override var mainUrl = "https://watchsb.com"
    override val requiresReferer = false

    private val hexArray = "0123456789ABCDEF".toCharArray()

    private fun bytesToHex(bytes: ByteArray): String {
        val hexChars = CharArray(bytes.size * 2)
        for (j in bytes.indices) {
            val v = bytes[j].toInt() and 0xFF

            hexChars[j * 2] = hexArray[v ushr 4]
            hexChars[j * 2 + 1] = hexArray[v and 0x0F]
        }
        return String(hexChars)
    }

    data class Subs (
        @JsonProperty("file") val file: String? = null,
        @JsonProperty("label") val label: String? = null,
    )

    data class StreamData (
        @JsonProperty("file") val file: String,
        @JsonProperty("cdn_img") val cdnImg: String,
        @JsonProperty("hash") val hash: String,
        @JsonProperty("subs") val subs: ArrayList<Subs>? = arrayListOf(),
        @JsonProperty("length") val length: String,
        @JsonProperty("id") val id: String,
        @JsonProperty("title") val title: String,
        @JsonProperty("backup") val backup: String,
    )

    data class Main (
        @JsonProperty("stream_data") val streamData: StreamData,
        @JsonProperty("status_code") val statusCode: Int,
    )

    override suspend fun getUrl(
        url: String,
        referer: String?,
        subtitleCallback: (SubtitleFile) -> Unit,
        callback: (ExtractorLink) -> Unit
    ) {
        val regexID =
            Regex("(embed-[a-zA-Z0-9]{0,8}[a-zA-Z0-9_-]+|/e/[a-zA-Z0-9]{0,8}[a-zA-Z0-9_-]+)")
        val id = regexID.findAll(url).map {
            it.value.replace(Regex("(embed-|/e/)"), "")
        }.first()
//        val master = "$mainUrl/sources48/6d6144797752744a454267617c7c${bytesToHex.lowercase()}7c7c4e61755a56456f34385243727c7c73747265616d7362/6b4a33767968506e4e71374f7c7c343837323439333133333462353935333633373836643638376337633462333634663539343137373761333635313533333835333763376333393636363133393635366136323733343435323332376137633763373337343732363536313664373336327c7c504d754478413835306633797c7c73747265616d7362"
        val master = "$mainUrl/sources49/" + bytesToHex("||$id||||streamsb".toByteArray()) + "/"
        val headers = mapOf(
            "watchsb" to "sbstream",
        )
        val mapped = app.get(
            master.lowercase(),
            headers = headers,
            referer = url,
        ).parsedSafe<Main>()
        // val urlmain = mapped.streamData.file.substringBefore("/hls/")
        M3u8Helper.generateM3u8(
            name,
            mapped?.streamData?.file ?: return,
            url,
            headers = headers
        ).forEach(callback)

        mapped.streamData.subs?.map {sub ->
            subtitleCallback.invoke(
                SubtitleFile(
                    sub.label.toString(),
                    sub.file ?: return@map null,
                )
            )
        }
    }
}