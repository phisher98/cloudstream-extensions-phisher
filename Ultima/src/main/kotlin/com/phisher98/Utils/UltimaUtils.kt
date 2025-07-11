package com.phisher98

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import kotlinx.coroutines.delay
import com.lagradost.cloudstream3.app

object UltimaUtils {
    data class SectionInfo(
            @JsonProperty("name") var name: String,
            @JsonProperty("url") var url: String,
            @JsonProperty("pluginName") var pluginName: String,
            @JsonProperty("enabled") var enabled: Boolean = false,
            @JsonProperty("priority") var priority: Int = 0
    )

    data class ExtensionInfo(
            @JsonProperty("name") var name: String? = null,
            @JsonProperty("sections") var sections: Array<SectionInfo>? = null
    )

    enum class Category {
        ANIME,
        MEDIA,
        NONE
    }

    data class MediaProviderState(
            @JsonProperty("name") var name: String,
            @JsonProperty("enabled") var enabled: Boolean = true,
            @JsonProperty("customDomain") var customDomain: String? = null
    ) {
        fun getProvider(): MediaProvider {
            return UltimaMediaProvidersUtils.mediaProviders.find { it.name.equals(name) }
                    ?: throw Exception("Unable to find media provider for $name")
        }

        fun getDomain(): String {
            return customDomain ?: getProvider().domain
        }
    }

    data class LinkData(
            @JsonProperty("simklId") val simklId: Int? = null,
            @JsonProperty("traktId") val traktId: Int? = null,
            @JsonProperty("imdbId") val imdbId: String? = null,
            @JsonProperty("tmdbId") val tmdbId: Int? = null,
            @JsonProperty("tvdbId") val tvdbId: Int? = null,
            @JsonProperty("type") val type: String? = null,
            @JsonProperty("season") val season: Int? = null,
            @JsonProperty("episode") val episode: Int? = null,
            @JsonProperty("aniId") val aniId: String? = null,
            @JsonProperty("malId") val malId: String? = null,
            @JsonProperty("title") val title: String? = null,
            @JsonProperty("year") val year: Int? = null,
            @JsonProperty("orgTitle") val orgTitle: String? = null,
            @JsonProperty("isAnime") val isAnime: Boolean = false,
            @JsonProperty("airedYear") val airedYear: Int? = null,
            @JsonProperty("lastSeason") val lastSeason: Int? = null,
            @JsonProperty("epsTitle") val epsTitle: String? = null,
            @JsonProperty("jpTitle") val jpTitle: String? = null,
            @JsonProperty("date") val date: String? = null,
            @JsonProperty("airedDate") val airedDate: String? = null,
            @JsonProperty("isAsian") val isAsian: Boolean = false,
            @JsonProperty("isBollywood") val isBollywood: Boolean = false,
            @JsonProperty("isCartoon") val isCartoon: Boolean = false,
    )
}

suspend fun <T> retry(
    times: Int = 3,
    delayMillis: Long = 1000,
    block: suspend () -> T
): T? {
    repeat(times - 1) {
        runCatching { return block() }.onFailure { delay(delayMillis) }
    }
    return runCatching { block() }.getOrNull()
}

data class DomainsParser(
    val moviesdrive: String,
    @JsonProperty("HDHUB4u")
    val hdhub4u: String,
    @JsonProperty("4khdhub")
    val n4khdhub: String,
    @JsonProperty("MultiMovies")
    val multiMovies: String,
    val bollyflix: String,
    @JsonProperty("UHDMovies")
    val uhdmovies: String,
    val moviesmod: String,
    val topMovies: String,
    val hdmovie2: String,
    val vegamovies: String,
    val rogmovies: String,
    val luxmovies: String,
    val xprime: String,
    val extramovies:String,
    val dramadrip:String
)

// ----------- Constants and Cache -----------
private var cachedDomains: DomainsParser? = null
private const val DOMAINS_URL =
    "https://raw.githubusercontent.com/phisher98/TVVVV/main/domains.json"

// ----------- Domain Fetch Function -----------
suspend fun getDomains(forceRefresh: Boolean = false): DomainsParser? {
    if (cachedDomains == null || forceRefresh) {
        try {
            val response = app.get(DOMAINS_URL)
            cachedDomains = response.parsedSafe<DomainsParser>()
            if (cachedDomains == null) {
                Log.e("getDomains", "Parsed domains are null. Possibly malformed JSON.")
            }
        } catch (e: Exception) {
            Log.e("getDomains", "Error fetching/parsing domains: ${e.message}")
            return null
        }
    }
    return cachedDomains
}


