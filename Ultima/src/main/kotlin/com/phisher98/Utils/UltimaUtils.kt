package com.phisher98

import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import kotlinx.coroutines.delay
import com.lagradost.cloudstream3.app
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

object UltimaUtils {
    fun getAllProviders(): List<com.lagradost.cloudstream3.MainAPI> {
        try {
            val clazz = Class.forName("com.lagradost.cloudstream3.APIHolder")
            
            // 1. Try to find the INSTANCE field first (in case it's an object class)
            val instanceField = try {
                clazz.getDeclaredField("INSTANCE")
            } catch (e: Exception) {
                null
            }
            val instance = instanceField?.get(null)
            
            // 2. Try to search for methods/properties that return a List or Array of MainAPI
            for (method in clazz.declaredMethods) {
                if (method.name == "getAllProviders" || method.name == "getProviders" || method.name == "getApis") {
                    method.isAccessible = true
                    val result = if (instance != null) method.invoke(instance) else method.invoke(null)
                    if (result is List<*>) {
                        return result.filterIsInstance<com.lagradost.cloudstream3.MainAPI>()
                    }
                    if (result is Array<*>) {
                        return result.filterIsInstance<com.lagradost.cloudstream3.MainAPI>()
                    }
                }
            }
            
            // 3. Try to search all fields
            for (field in clazz.declaredFields) {
                if (field.name == "allProviders" || field.name == "providers" || field.name == "apis") {
                    field.isAccessible = true
                    val result = if (instance != null) field.get(instance) else field.get(null)
                    if (result is List<*>) {
                        return result.filterIsInstance<com.lagradost.cloudstream3.MainAPI>()
                    }
                    if (result is Array<*>) {
                        return result.filterIsInstance<com.lagradost.cloudstream3.MainAPI>()
                    }
                }
            }
            
            // 4. Try direct method invocation by name "allProviders"
            for (method in clazz.declaredMethods) {
                if (method.name == "allProviders") {
                    method.isAccessible = true
                    val result = if (instance != null) method.invoke(instance) else method.invoke(null)
                    if (result is List<*>) {
                        return result.filterIsInstance<com.lagradost.cloudstream3.MainAPI>()
                    }
                }
            }
        } catch (e: Exception) {
            Log.e("Ultima", "Failed to retrieve allProviders via reflection: ${e.message}")
        }
        
        // Fallback using compile-time access (wrapped in try-catch so it won't crash class loading if signature is wrong)
        return try {
            com.lagradost.cloudstream3.APIHolder.allProviders
        } catch (e: Throwable) {
            emptyList()
        }
    }

    data class SectionInfo(
            @param:JsonProperty("name") var name: String,
            @param:JsonProperty("url") var url: String,
            @param:JsonProperty("pluginName") var pluginName: String,
            @param:JsonProperty("enabled") var enabled: Boolean = false,
            @param:JsonProperty("priority") var priority: Int = 0
    )

    data class ExtensionInfo(
            @param:JsonProperty("name") var name: String? = null,
            @param:JsonProperty("sections") var sections: Array<SectionInfo>? = null
    ) {
        override fun equals(other: Any?): Boolean {
            if (this === other) return true
            if (javaClass != other?.javaClass) return false

            other as ExtensionInfo

            if (name != other.name) return false
            if (!sections.contentEquals(other.sections)) return false

            return true
        }

        override fun hashCode(): Int {
            var result = name?.hashCode() ?: 0
            result = 31 * result + (sections?.contentHashCode() ?: 0)
            return result
        }
    }

    enum class Category {
        ANIME,
        MEDIA,
        NONE
    }

    data class MediaProviderState(
            @param:JsonProperty("name") var name: String,
            @param:JsonProperty("enabled") var enabled: Boolean = true,
            @param:JsonProperty("customDomain") var customDomain: String? = null
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
        @param:JsonProperty("simklId") val simklId: Int? = null,
        @param:JsonProperty("traktId") val traktId: Int? = null,
        @param:JsonProperty("imdbId") val imdbId: String? = null,
        @param:JsonProperty("tmdbId") val tmdbId: Int? = null,
        @param:JsonProperty("tvdbId") val tvdbId: Int? = null,
        @param:JsonProperty("type") val type: String? = null,
        @param:JsonProperty("season") val season: Int? = null,
        @param:JsonProperty("episode") val episode: Int? = null,
        @param:JsonProperty("aniId") val aniId: Int? = null,
        @param:JsonProperty("malId") val malId: Int? = null,
        @param:JsonProperty("title") val title: String? = null,
        @param:JsonProperty("year") val year: Int? = null,
        @param:JsonProperty("orgTitle") val orgTitle: String? = null,
        @param:JsonProperty("isAnime") val isAnime: Boolean = false,
        @param:JsonProperty("airedYear") val airedYear: Int? = null,
        @param:JsonProperty("lastSeason") val lastSeason: Int? = null,
        @param:JsonProperty("epsTitle") val epsTitle: String? = null,
        @param:JsonProperty("jpTitle") val jpTitle: String? = null,
        @param:JsonProperty("date") val date: String? = null,
        @param:JsonProperty("airedDate") val airedDate: String? = null,
        @param:JsonProperty("isAsian") val isAsian: Boolean = false,
        @param:JsonProperty("isBollywood") val isBollywood: Boolean = false,
        @param:JsonProperty("isCartoon") val isCartoon: Boolean = false,
        @param:JsonProperty("isDub") val isDub: Boolean = false,
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
    @param:JsonProperty("HDHUB4u")
    val hdhub4u: String,
    @param:JsonProperty("4khdhub")
    val n4khdhub: String,
    @param:JsonProperty("MultiMovies")
    val multiMovies: String,
    val bollyflix: String,
    @param:JsonProperty("UHDMovies")
    val uhdmovies: String,
    val moviesmod: String,
    val topMovies: String,
    val hdmovie2: String,
    val vegamovies: String,
    val rogmovies: String,
    val luxmovies: String,
    val movierulzhd: String,
    val extramovies: String,
    val banglaplex: String,
    val toonstream: String,
    val telugumv: String,
    val filmycab: String,
    val tellyhd: String,
    val filmyfiy: String,
    val hindmoviez: String,
    val tamilblasters: String,
    val hubcloud: String,
    val movienestbd: String,
    val movies4u: String,
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

suspend fun <T> runLimitedParallel(
    limit: Int = 4,
    blockList: List<suspend () -> T>
): List<T> {
    val semaphore = Semaphore(limit)
    return coroutineScope {
        blockList.map { block ->
            async(Dispatchers.IO) {
                semaphore.withPermit { block() }
            }
        }.awaitAll()
    }
}

fun cleanTitle(title: String): String {
    val parts = title.split(".", "-", "_")

    val qualityTags = listOf(
        "WEBRip", "WEB-DL", "WEB", "BluRay", "HDRip", "DVDRip", "HDTV",
        "CAM", "TS", "R5", "DVDScr", "BRRip", "BDRip", "DVD", "PDTV",
        "HD"
    )

    val audioTags = listOf(
        "AAC", "AC3", "DTS", "MP3", "FLAC", "DD5", "EAC3", "Atmos"
    )

    val subTags = listOf(
        "ESub", "ESubs", "Subs", "MultiSub", "NoSub", "EnglishSub", "HindiSub"
    )

    val codecTags = listOf(
        "x264", "x265", "H264", "HEVC", "AVC"
    )

    val startIndex = parts.indexOfFirst { part ->
        qualityTags.any { tag -> part.contains(tag, ignoreCase = true) }
    }

    val endIndex = parts.indexOfLast { part ->
        subTags.any { tag -> part.contains(tag, ignoreCase = true) } ||
                audioTags.any { tag -> part.contains(tag, ignoreCase = true) } ||
                codecTags.any { tag -> part.contains(tag, ignoreCase = true) }
    }

    return if (startIndex != -1 && endIndex != -1 && endIndex >= startIndex) {
        parts.subList(startIndex, endIndex + 1).joinToString(".")
    } else if (startIndex != -1) {
        parts.subList(startIndex, parts.size).joinToString(".")
    } else {
        parts.takeLast(3).joinToString(".")
    }
}