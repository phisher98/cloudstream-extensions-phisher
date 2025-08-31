package com.phisher98

import android.content.Context
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.api.Log
import kotlinx.coroutines.delay
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.utils.BackupUtils
import com.lagradost.cloudstream3.utils.DataStore.getDefaultSharedPrefs
import com.lagradost.cloudstream3.utils.DataStore.getSharedPrefs
import com.lagradost.cloudstream3.utils.DataStoreHelper
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

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

object BackupUtils {

    private val nonTransferableKeys = listOf(
        "anilist_token",
    )

    // Checks if a SharedPreferences key is a resume-watching key
    private fun String.isResume(): Boolean = !nonTransferableKeys.any { this.contains(it) }

    // Creates a BackupFile object containing only relevant data for syncing
    fun getBackup(context: Context?, resumeWatching: List<DataStoreHelper.ResumeWatchingResult>? = null): BackupUtils.BackupFile? {
        if (context == null) return null

        val allData = context.getSharedPrefs().all.filter { it.key.isResume() }
        val allSettings = context.getDefaultSharedPrefs().all.filter { it.key.isResume() }

        val dataStore = BackupUtils.BackupVars(
            allData.filterValues { it is Boolean } as? Map<String, Boolean>,
            allData.filterValues { it is Int } as? Map<String, Int>,
            allData.filterValues { it is String } as? Map<String, String>,
            allData.filterValues { it is Float } as? Map<String, Float>,
            allData.filterValues { it is Long } as? Map<String, Long>,
            allData.filterValues { it is Set<*> } as? Map<String, Set<String>>
        )

        val settings = BackupUtils.BackupVars(
            allSettings.filterValues { it is Boolean } as? Map<String, Boolean>,
            allSettings.filterValues { it is Int } as? Map<String, Int>,
            allSettings.filterValues { it is String } as? Map<String, String>,
            allSettings.filterValues { it is Float } as? Map<String, Float>,
            allSettings.filterValues { it is Long } as? Map<String, Long>,
            allSettings.filterValues { it is Set<*> } as? Map<String, Set<String>>
        )

        return BackupUtils.BackupFile(
            datastore = dataStore,
            settings = settings
        )
    }

    // Apply a map of data to SharedPreferences
    private fun <T> Context.restoreMap(map: Map<String, T>?, isSettings: Boolean = false) {
        val editor = if (isSettings) getDefaultSharedPrefs().edit() else getSharedPrefs().edit()
        map?.forEach { (key, value) ->
            if (key.isResume()) {
                when (value) {
                    is Boolean -> editor.putBoolean(key, value)
                    is Int -> editor.putInt(key, value)
                    is String -> editor.putString(key, value)
                    is Float -> editor.putFloat(key, value)
                    is Long -> editor.putLong(key, value)
                    is Set<*> -> editor.putStringSet(key, value.filterIsInstance<String>().toSet())
                }
            }
        }
        editor.apply()
    }

    // Restore a BackupFile into SharedPreferences
    fun restore(context: Context?, backupFile: BackupUtils.BackupFile, restoreSettings: Boolean = true, restoreDataStore: Boolean = true) {
        if (context == null) return

        if (restoreSettings) {
            context.restoreMap(backupFile.settings.bool, true)
            context.restoreMap(backupFile.settings.int, true)
            context.restoreMap(backupFile.settings.string, true)
            context.restoreMap(backupFile.settings.float, true)
            context.restoreMap(backupFile.settings.long, true)
            context.restoreMap(backupFile.settings.stringSet, true)
        }

        if (restoreDataStore) {
            context.restoreMap(backupFile.datastore.bool)
            context.restoreMap(backupFile.datastore.int)
            context.restoreMap(backupFile.datastore.string)
            context.restoreMap(backupFile.datastore.float)
            context.restoreMap(backupFile.datastore.long)
            context.restoreMap(backupFile.datastore.stringSet)
        }
    }
}
