package com.phisher98

import android.content.SharedPreferences
import com.lagradost.api.Log
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.ConcurrentHashMap

/**
 * Intelligent caching system for StreamPlay
 * - TTL-based API endpoint caching
 * - LRU cache for anime ID mappings
 * - Provider performance tracking
 */
object StreamPlayCache {

    private const val TAG = "StreamPlayCache"

    // ==================== API Base Caching ====================

    data class ApiCacheEntry(
        val url: String,
        val timestamp: Long,
        val successCount: Int = 0,
        val failureCount: Int = 0
    )

    private var apiCacheEntry: ApiCacheEntry? = null
    private val apiCacheMutex = Mutex()
    private const val API_CACHE_TTL_MS = 10 * 60 * 1000L // 10 minutes
    private const val API_CACHE_SHORT_TTL_MS = 2 * 60 * 1000L // 2 minutes on failures

    /**
     * Get cached API base if still valid
     */
    suspend fun getCachedApiBase(): String? = apiCacheMutex.withLock {
        apiCacheEntry?.let { entry ->
            val age = System.currentTimeMillis() - entry.timestamp
            val ttl = if (entry.failureCount > 0) API_CACHE_SHORT_TTL_MS else API_CACHE_TTL_MS

            if (age < ttl) {
                Log.d(TAG, "✅ Using cached API base: ${entry.url} (age: ${age / 1000}s)")
                entry.url
            } else {
                Log.d(TAG, "⏰ API cache expired (age: ${age / 1000}s, TTL: ${ttl / 1000}s)")
                null
            }
        }
    }

    /**
     * Cache a successful API base
     */
    suspend fun cacheApiBase(url: String, success: Boolean = true) = apiCacheMutex.withLock {
        val current = apiCacheEntry
        apiCacheEntry = if (success) {
            ApiCacheEntry(
                url = url,
                timestamp = System.currentTimeMillis(),
                successCount = (current?.successCount ?: 0) + 1,
                failureCount = 0
            )
        } else {
            ApiCacheEntry(
                url = url,
                timestamp = System.currentTimeMillis(),
                successCount = current?.successCount ?: 0,
                failureCount = (current?.failureCount ?: 0) + 1
            )
        }
        Log.d(TAG, "📦 Cached API base: $url (success: $success)")
    }

    // ==================== Anime ID Caching ====================

    data class AnimeIdMapping(
        val anilistId: String? = null,
        val malId: String? = null,
        val kitsuId: String? = null,
        val zoroId: String? = null,
        val timestamp: Long = System.currentTimeMillis()
    )

    private val animeIdCache = ConcurrentHashMap<String, AnimeIdMapping>()
    private const val ANIME_ID_CACHE_TTL_MS = 24 * 60 * 60 * 1000L // 24 hours
    private const val ANIME_ID_CACHE_MAX_SIZE = 500

    /**
     * Get cached anime ID mapping
     */
    fun getCachedAnimeIds(key: String): AnimeIdMapping? {
        val mapping = animeIdCache[key]
        return if (mapping != null) {
            val age = System.currentTimeMillis() - mapping.timestamp
            if (age < ANIME_ID_CACHE_TTL_MS) {
                Log.d(TAG, "✅ Anime ID cache hit: $key")
                mapping
            } else {
                Log.d(TAG, "⏰ Anime ID cache expired: $key")
                animeIdCache.remove(key)
                null
            }
        } else {
            null
        }
    }

    /**
     * Cache anime ID mapping with LRU eviction
     */
    fun cacheAnimeIds(key: String, mapping: AnimeIdMapping) {
        // LRU eviction: remove oldest entries if cache is full
        if (animeIdCache.size >= ANIME_ID_CACHE_MAX_SIZE) {
            val oldest = animeIdCache.entries
                .minByOrNull { it.value.timestamp }
            oldest?.let {
                animeIdCache.remove(it.key)
                Log.d(TAG, "🗑️ Evicted oldest anime ID: ${it.key}")
            }
        }

        animeIdCache[key] = mapping
        Log.d(TAG, "📦 Cached anime ID: $key (cache size: ${animeIdCache.size})")
    }

    // ==================== Provider Performance Tracking ====================

    data class ProviderStats(
        val successCount: Int = 0,
        val failureCount: Int = 0,
        val totalTimeMs: Long = 0,
        val lastExecutionMs: Long = 0,
        val consecutiveFailures: Int = 0
    ) {
        val successRate: Float
            get() = if (successCount + failureCount == 0) 0f
            else successCount.toFloat() / (successCount + failureCount)

        val avgTimeMs: Long
            get() = if (successCount == 0) 0L else totalTimeMs / successCount

        val isCircuitBroken: Boolean
            get() = consecutiveFailures >= 5
    }

    private val providerStatsMap = ConcurrentHashMap<String, ProviderStats>()

    /**
     * Get provider statistics
     */
    fun getProviderStats(providerId: String): ProviderStats {
        return providerStatsMap.getOrDefault(providerId, ProviderStats())
    }

    /**
     * Record provider execution result
     */
    fun recordProviderExecution(providerId: String, success: Boolean, durationMs: Long) {
        val current = providerStatsMap.getOrDefault(providerId, ProviderStats())

        val updated = if (success) {
            current.copy(
                successCount = current.successCount + 1,
                totalTimeMs = current.totalTimeMs + durationMs,
                lastExecutionMs = durationMs,
                consecutiveFailures = 0
            )
        } else {
            current.copy(
                failureCount = current.failureCount + 1,
                lastExecutionMs = durationMs,
                consecutiveFailures = current.consecutiveFailures + 1
            )
        }

        providerStatsMap[providerId] = updated

        if (updated.isCircuitBroken && !current.isCircuitBroken) {
            Log.w(TAG, "📉 Provider moved to low priority: $providerId (${updated.consecutiveFailures} consecutive failures)")
        } else if (!updated.isCircuitBroken && current.isCircuitBroken) {
            Log.d(TAG, "✅ Provider recovered: $providerId")
        }
    }

    fun getProviderPriorityScore(providerId: String): Float {
        val stats = getProviderStats(providerId)

        if (stats.isCircuitBroken) return -1000f

        if (stats.successCount + stats.failureCount == 0) return 0f

        val timePenalty = if (stats.avgTimeMs > 0) stats.avgTimeMs / 1000f else 0f
        return (stats.successRate * 100f) - timePenalty
    }

    fun resetProviderCircuit(providerId: String) {
        val current = providerStatsMap[providerId]
        if (current != null && current.isCircuitBroken) {
            providerStatsMap[providerId] = current.copy(consecutiveFailures = 0)
            Log.d(TAG, "🔄 Circuit breaker reset: $providerId")
        }
    }

    // ==================== Metadata Caching ====================

    data class MetadataCache(
        val data: String, // JSON string
        val timestamp: Long = System.currentTimeMillis()
    )

    private val metadataCache = ConcurrentHashMap<String, MetadataCache>()
    private const val METADATA_CACHE_TTL_MS = 30 * 60 * 1000L // 30 minutes
    private const val METADATA_CACHE_MAX_SIZE = 100

    /**
     * Get cached metadata
     */
    fun getCachedMetadata(key: String): String? {
        val cache = metadataCache[key]
        return if (cache != null) {
            val age = System.currentTimeMillis() - cache.timestamp
            if (age < METADATA_CACHE_TTL_MS) {
                Log.d(TAG, "✅ Metadata cache hit: $key")
                cache.data
            } else {
                Log.d(TAG, "⏰ Metadata cache expired: $key")
                metadataCache.remove(key)
                null
            }
        } else {
            null
        }
    }

    /**
     * Cache metadata with LRU eviction
     */
    fun cacheMetadata(key: String, data: String) {
        // LRU eviction
        if (metadataCache.size >= METADATA_CACHE_MAX_SIZE) {
            val oldest = metadataCache.entries
                .minByOrNull { it.value.timestamp }
            oldest?.let {
                metadataCache.remove(it.key)
                Log.d(TAG, "🗑️ Evicted oldest metadata: ${it.key}")
            }
        }

        metadataCache[key] = MetadataCache(data)
        Log.d(TAG, "📦 Cached metadata: $key (cache size: ${metadataCache.size})")
    }

    // ==================== Persistence ====================

    fun saveProviderStats(prefs: SharedPreferences?) {
        prefs?.edit()?.apply {
            providerStatsMap.forEach { (providerId, stats) ->
                putString("provider_stats_$providerId",
                    "${stats.successCount},${stats.failureCount},${stats.totalTimeMs},${stats.consecutiveFailures}")
            }
            apply()
        }
    }

    /**
     * Load provider stats from SharedPreferences
     */
    fun loadProviderStats(prefs: SharedPreferences?) {
        prefs?.all?.forEach { (key, value) ->
            if (key.startsWith("provider_stats_") && value is String) {
                val providerId = key.removePrefix("provider_stats_")
                val parts = value.split(",")
                if (parts.size >= 4) {
                    try {
                        val stats = ProviderStats(
                            successCount = parts[0].toInt(),
                            failureCount = parts[1].toInt(),
                            totalTimeMs = parts[2].toLong(),
                            consecutiveFailures = parts[3].toInt()
                        )
                        providerStatsMap[providerId] = stats
                    } catch (e: Exception) {
                        Log.e(TAG, "Error loading stats for $providerId: ${e.message}")
                    }
                }
            }
        }
        Log.d(TAG, "📂 Loaded provider stats from SharedPreferences (${providerStatsMap.size} providers)")
    }
}
