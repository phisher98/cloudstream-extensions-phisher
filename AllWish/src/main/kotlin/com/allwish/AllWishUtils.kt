package com.allwish

import android.os.Build
import androidx.annotation.RequiresApi
import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.databind.ObjectMapper
import com.lagradost.cloudstream3.ShowStatus
import java.net.URLEncoder
import java.util.Base64

@RequiresApi(Build.VERSION_CODES.O)
fun generateEpisodeVrf(episodeId: String): String {
    // Secret key from JS
    val secretKey = "ysJhV6U27FVIjjuk"

    // 1. encodeURIComponent equivalent
    // FIX 1: URLEncoder.encode is not the same as JS encodeURIComponent.
    // It encodes space as '+' (not '%20') and also encodes other reserved chars '()*!~'.
    val encodedId = URLEncoder.encode(episodeId, "UTF-8")
        .replace("+", "%20")
        .replace("%21", "!")
        .replace("%27", "'")
        .replace("%28", "(")
        .replace("%29", ")")
        .replace("%7E", "~")
        .replace("%2A", "*")

    // 2. RC4-like transform
    val keyCodes = secretKey.map { it.code }
    val dataCodes = encodedId.map { it.code } // This is correct (maps char codes of the UTF-8 string)
    val n = IntArray(256) { it }
    var a = 0
    for (o in 0..255) {
        a = (a + n[o] + keyCodes[o % keyCodes.size]) % 256
        n[o] = n[a].also { n[a] = n[o] } // swap
    }

    val out = mutableListOf<Int>()
    var o = 0
    a = 0
    for (r in dataCodes.indices) {
        o = (o + 1) % 256
        // val e = n[o] // e is not used, but that's fine
        a = (a + n[o]) % 256
        n[o] = n[a].also { n[a] = n[o] }
        val k = n[(n[o] + n[a]) % 256]
        out.add(dataCodes[r] xor k)
    }
    val step1 = out.map { (it and 0xFF).toByte() }.toByteArray()

    // 3. Base64 URL safe
    // FIX 2: Use getUrlEncoder() which correctly uses '_' and '-'
    // AND importantly, it omits padding ('='), which your manual replace version did not.
    val base1 = Base64.getUrlEncoder().encodeToString(step1)

    // 4. Position-based transform
    val step2Bytes = base1.toByteArray(Charsets.ISO_8859_1) // This is fine
    val transformedList: List<Byte> = step2Bytes.mapIndexed { index, value ->
        var s = value.toInt()
        s += when (index % 8) {
            1 -> 3
            7 -> 5
            2 -> -4
            4 -> -2
            6 -> 4
            0 -> -3
            3 -> 2
            5 -> 5
            else -> 0
        }
        (s and 0xFF).toByte()
    }
    val transformedBytes = transformedList.toByteArray()

    // 5. Base64 URL safe again
    // FIX 2 (Applied again): Use getUrlEncoder()
    val base2 = Base64.getUrlEncoder().encodeToString(transformedBytes)

    // 6. ROT13 for letters
    val final = base2.map { c ->
        when (c) {
            in 'A'..'Z' -> 'A' + (c - 'A' + 13) % 26
            in 'a'..'z' -> 'a' + (c - 'a' + 13) % 26
            else -> c
        }
    }.joinToString("")

    return final
}

fun parseAnimeData(jsonString: String): MetaAnimeData? {
    return try {
        val objectMapper = ObjectMapper()
        objectMapper.readValue(jsonString, MetaAnimeData::class.java)
    } catch (_: Exception) {
        null // Return null for invalid JSON instead of crashing
    }
}


@JsonIgnoreProperties(ignoreUnknown = true)
data class ImageData(
    @JsonProperty("coverType") val coverType: String?,
    @JsonProperty("url") val url: String?
)

@JsonIgnoreProperties(ignoreUnknown = true)
data class MetaEpisode(
    @JsonProperty("episode") val episode: String?,
    @JsonProperty("airdate") val airdate: String?,
    @JsonProperty("airDateUtc") val airDateUtc: String?,
    @JsonProperty("length") val length: Int?,
    @JsonProperty("runtime") val runtime: Int?,
    @JsonProperty("image") val image: String?,
    @JsonProperty("title") val title: Map<String, String>?,
    @JsonProperty("overview") val overview: String?,
    @JsonProperty("rating") val rating: String?,
    @JsonProperty("finaleType") val finaleType: String?
)


@JsonIgnoreProperties(ignoreUnknown = true)
data class MetaAnimeData(
    @JsonProperty("titles") val titles: Map<String, String>? = null,
    @JsonProperty("images") val images: List<ImageData>? = null,
    @JsonProperty("episodes") val episodes: Map<String, MetaEpisode>? = null,
)


fun getStatus(t: String?): ShowStatus {
    return when (t) {
        "Finished Airing" -> ShowStatus.Completed
        "Updating" -> ShowStatus.Ongoing
        else -> ShowStatus.Completed
    }
}