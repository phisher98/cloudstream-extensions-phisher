package com.phisher98

import com.lagradost.cloudstream3.APIHolder.unixTimeMS
import com.lagradost.cloudstream3.mvvm.logError
import com.lagradost.cloudstream3.utils.getQualityFromName
import okhttp3.Interceptor
import okhttp3.Response
import java.text.SimpleDateFormat
import java.util.Locale
import java.util.concurrent.TimeUnit

fun String.fixSourceUrl(): String {
    return this.replace("/manifest.json", "").replace("stremio://", "https://")
}

fun fixSourceName(name: String?, title: String?, description: String?): String {
    val pName = name?.replace("\n", " ")
    val pTitle = title?.replace("\n", " ")

    return when {
        !pName.isNullOrEmpty() && !pTitle.isNullOrEmpty() -> "$pName\n$pTitle"
        !pName.isNullOrEmpty() && !description.isNullOrEmpty() -> "$pName\n$description"
        else -> pTitle ?: description ?: pName ?: ""
    }
}

fun getQuality(qualities: List<String?>): Int {
    fun String.getQuality(): String? {
        val has = Regex("(\\d{3,4}[pP])").find(this)?.groupValues?.getOrNull(1)
        if (has != null) return has
        if (contains("4k", ignoreCase = true)) return "2160p"
        return null
    }
    val quality = qualities.firstNotNullOfOrNull { it?.getQuality() }
    return getQualityFromName(quality)
}

fun getEpisodeSlug(
    season: Int? = null,
    episode: Int? = null,
): Pair<String, String> {
    return if (season == null && episode == null) {
        "" to ""
    } else {
        (if (season!! < 10) "0$season" else "$season") to (if (episode!! < 10) "0$episode" else "$episode")
    }
}

fun isUpcoming(dateString: String?): Boolean {
    return try {
        val format = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateTime = dateString?.let { format.parse(it)?.time } ?: return false
        unixTimeMS < dateTime
    } catch (t: Throwable) {
        logError(t)
        false
    }

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
