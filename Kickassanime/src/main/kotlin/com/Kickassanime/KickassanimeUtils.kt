package com.kickassanime

//mark
import com.kickassanime.Kickassanime.Companion.mainUrl
import com.lagradost.cloudstream3.utils.SubtitleHelper
import java.net.URI
import java.net.URLDecoder

fun decode(input: String): String =
    URLDecoder.decode(input, "utf-8").replace(" ", "%20")

fun String.createSlug(): String {
    return this.replace(Regex("[^\\w ]+"), "").replace(" ", "-").lowercase()
}

fun String.getTrackerTitle(): String {
    val blacklist = arrayOf(
        "Dub",
        "Uncensored",
        "TV",
        "JPN DUB",
        "Uncensored"
    ).joinToString("|") { "\\($it\\)" }
    return this.replace(Regex(blacklist), "").trim()
}

fun getImageUrl(link: String?): String? {
    if (link == null) return null
    return if (link.startsWith(mainUrl)) link else "$mainUrl/image/poster/$link.webp"
}

fun getThumbnailUrl(link: String?): String? {
    if (link == null) return null
    return if (link.startsWith(mainUrl)) link else "$mainUrl/image/thumbnail/$link.webp"
}

fun getBannerUrl(link: String?): String? {
    if (link == null) return null
    return if (link.startsWith(mainUrl)) link else "$mainUrl/image/banner/$link.webp"
}

fun getBaseUrl(url: String): String {
    return URI(url).let {
        "${it.scheme}://${it.host}"
    }
}


fun getLanguage(language: String?): String? {
    return SubtitleHelper.fromTagToEnglishLanguageName(language ?: return null)
        ?: SubtitleHelper.fromTagToEnglishLanguageName(language.substringBefore("-"))
}

fun fixUrl(url: String, domain: String): String {
    if (url.startsWith("http")) {
        return url
    }
    if (url.isEmpty()) {
        return ""
    }

    val startsWithNoHttp = url.startsWith("//")
    if (startsWithNoHttp) {
        return "https:$url"
    } else {
        if (url.startsWith('/')) {
            return domain + url
        }
        return "$domain/$url"
    }
}

/*

data class MyJsonData(val data: String)

fun tryParseJson(jsonString: String): MyJsonData? {
    return try {
        // Attempt to parse the JSON string into the data class
        val json = Json { isLenient = true; ignoreUnknownKeys = true }
        json.decodeFromString<MyJsonData>(jsonString)
    } catch (e: Exception) {
        // Catch any exceptions and return null if parsing fails
        println("Error parsing JSON: ${e.message}")
        null
    }
}
 */