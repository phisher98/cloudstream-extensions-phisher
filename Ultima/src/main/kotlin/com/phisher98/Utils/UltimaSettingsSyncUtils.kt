package com.phisher98

import android.annotation.SuppressLint
import android.content.Context
import android.os.Build
import android.provider.Settings
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.api.Log
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.Dispatchers
import java.security.MessageDigest
import java.util.UUID
import android.util.Base64
import java.io.ByteArrayOutputStream
import java.io.ByteArrayInputStream
import java.util.zip.GZIPOutputStream
import java.util.zip.GZIPInputStream

data class AppSettingsSyncCreds(
    @param:JsonProperty("useCustomDatabase") var useCustomDatabase: Boolean = false,
    @param:JsonProperty("firebaseUrl") var firebaseUrl: String? = null,
    @param:JsonProperty("syncKey") var syncKey: String? = null,
    @param:JsonProperty("deviceName") var deviceName: String? = null,
    @param:JsonProperty("deviceId") var deviceId: String? = null,
    @param:JsonProperty("backupDevice") var backupDevice: Boolean = false,
    @param:JsonProperty("restoreDevice") var restoreDevice: Boolean = false,

    // Unified category sync toggles (v2)
    @param:JsonProperty("syncExtensions") var syncExtensions: Boolean = true,
    @param:JsonProperty("syncBookmarks") var syncBookmarks: Boolean = true,
    @param:JsonProperty("syncResumeWatching") var syncResumeWatching: Boolean = true,
    @param:JsonProperty("syncSearchHistory") var syncSearchHistory: Boolean = true,
    @param:JsonProperty("syncSettings") var syncSettings: Boolean = true,

    // Legacy per-category toggles (kept for backward compat deserialization)
    @param:JsonProperty("backupBookmarks") var backupBookmarks: Boolean = true,
    @param:JsonProperty("backupResumeWatching") var backupResumeWatching: Boolean = true,
    @param:JsonProperty("backupSearchHistory") var backupSearchHistory: Boolean = true,
    @param:JsonProperty("backupExtensions") var backupExtensions: Boolean = true,
    @param:JsonProperty("backupPlayer") var backupPlayer: Boolean = true,
    @param:JsonProperty("backupSubtitles") var backupSubtitles: Boolean = true,
    @param:JsonProperty("backupTheme") var backupTheme: Boolean = true,
    @param:JsonProperty("backupLayout") var backupLayout: Boolean = true,
    @param:JsonProperty("backupDownloads") var backupDownloads: Boolean = true,
    @param:JsonProperty("backupGeneral") var backupGeneral: Boolean = true,

    @param:JsonProperty("restoreBookmarks") var restoreBookmarks: Boolean = true,
    @param:JsonProperty("restoreResumeWatching") var restoreResumeWatching: Boolean = true,
    @param:JsonProperty("restoreSearchHistory") var restoreSearchHistory: Boolean = true,
    @param:JsonProperty("restoreExtensions") var restoreExtensions: Boolean = true,
    @param:JsonProperty("restorePlayer") var restorePlayer: Boolean = true,
    @param:JsonProperty("restoreSubtitles") var restoreSubtitles: Boolean = true,
    @param:JsonProperty("restoreTheme") var restoreTheme: Boolean = true,
    @param:JsonProperty("restoreLayout") var restoreLayout: Boolean = true,
    @param:JsonProperty("restoreDownloads") var restoreDownloads: Boolean = true,
    @param:JsonProperty("restoreGeneral") var restoreGeneral: Boolean = true
) {
    val defaultUrl = "https://cloudstream-ultima-sync-default-rtdb.firebaseio.com/"

    val activeUrl: String
        get() {
            val url = if (useCustomDatabase) firebaseUrl else defaultUrl
            if (url.isNullOrEmpty()) return defaultUrl
            return if (url.endsWith("/")) url else "$url/"
        }

    fun isLoggedIn(): Boolean {
        return !syncKey.isNullOrEmpty()
    }

    fun isBackupEnabled(category: SyncCategory): Boolean {
        return when (category) {
            SyncCategory.EXTENSIONS -> backupExtensions
            SyncCategory.BOOKMARKS -> backupBookmarks
            SyncCategory.RESUME_WATCHING -> backupResumeWatching
            SyncCategory.SEARCH_HISTORY -> backupSearchHistory
            SyncCategory.SETTINGS -> backupPlayer || backupSubtitles || backupTheme || backupLayout || backupDownloads || backupGeneral
        }
    }

    fun isRestoreEnabled(category: SyncCategory): Boolean {
        return when (category) {
            SyncCategory.EXTENSIONS -> restoreExtensions
            SyncCategory.BOOKMARKS -> restoreBookmarks
            SyncCategory.RESUME_WATCHING -> restoreResumeWatching
            SyncCategory.SEARCH_HISTORY -> restoreSearchHistory
            SyncCategory.SETTINGS -> restorePlayer || restoreSubtitles || restoreTheme || restoreLayout || restoreDownloads || restoreGeneral
        }
    }

    fun isSettingsBackupEnabled(sub: SettingsSubCategory): Boolean {
        return when (sub) {
            SettingsSubCategory.PLAYER -> backupPlayer
            SettingsSubCategory.SUBTITLES -> backupSubtitles
            SettingsSubCategory.THEME -> backupTheme
            SettingsSubCategory.LAYOUT -> backupLayout
            SettingsSubCategory.DOWNLOADS -> backupDownloads
            SettingsSubCategory.GENERAL -> backupGeneral
        }
    }

    fun isSettingsRestoreEnabled(sub: SettingsSubCategory): Boolean {
        return when (sub) {
            SettingsSubCategory.PLAYER -> restorePlayer
            SettingsSubCategory.SUBTITLES -> restoreSubtitles
            SettingsSubCategory.THEME -> restoreTheme
            SettingsSubCategory.LAYOUT -> restoreLayout
            SettingsSubCategory.DOWNLOADS -> restoreDownloads
            SettingsSubCategory.GENERAL -> restoreGeneral
        }
    }
}

enum class SettingsSubCategory {
    PLAYER,
    SUBTITLES,
    THEME,
    LAYOUT,
    DOWNLOADS,
    GENERAL
}

// --- Sync v2 Category System ---

enum class SyncCategory(val key: String) {
    EXTENSIONS("extensions"),
    SETTINGS("settings"),
    BOOKMARKS("bookmarks"),
    RESUME_WATCHING("resume_watching"),
    SEARCH_HISTORY("search_history");
}

data class SyncCategoryMeta(
    @JsonProperty("ts") val ts: Long = 0L,
    @JsonProperty("hash") val hash: String = "",
    @JsonProperty("device") val device: String = ""
)

data class SyncManifest(
    @JsonProperty("extensions") val extensions: SyncCategoryMeta? = null,
    @JsonProperty("settings") val settings: SyncCategoryMeta? = null,
    @JsonProperty("bookmarks") val bookmarks: SyncCategoryMeta? = null,
    @JsonProperty("resume_watching") val resumeWatching: SyncCategoryMeta? = null,
    @JsonProperty("search_history") val searchHistory: SyncCategoryMeta? = null,
    @JsonProperty("version") val version: Int = 2
) {
    fun getMeta(category: SyncCategory): SyncCategoryMeta? {
        return when (category) {
            SyncCategory.EXTENSIONS -> extensions
            SyncCategory.SETTINGS -> settings
            SyncCategory.BOOKMARKS -> bookmarks
            SyncCategory.RESUME_WATCHING -> resumeWatching
            SyncCategory.SEARCH_HISTORY -> searchHistory
        }
    }

    fun withUpdated(category: SyncCategory, meta: SyncCategoryMeta): SyncManifest {
        return when (category) {
            SyncCategory.EXTENSIONS -> copy(extensions = meta)
            SyncCategory.SETTINGS -> copy(settings = meta)
            SyncCategory.BOOKMARKS -> copy(bookmarks = meta)
            SyncCategory.RESUME_WATCHING -> copy(resumeWatching = meta)
            SyncCategory.SEARCH_HISTORY -> copy(searchHistory = meta)
        }
    }
}

data class SyncCategoryPayload(
    @JsonProperty("data") val data: String = "",
    @JsonProperty("ts") val ts: Long = 0L,
    @JsonProperty("device") val device: String = ""
)

// --- Legacy v1 types (kept for migration) ---

data class FirebaseDevice(
    @JsonProperty("name") var name: String = "",
    @JsonProperty("deviceId") var deviceId: String = "",
    @JsonProperty("lastActive") var lastActive: Long = 0L
)

data class FirebaseSharedData(
    @JsonProperty("lastUpdated") var lastUpdated: Long = 0L,
    @JsonProperty("syncedData") var syncedData: String? = null,
    @JsonProperty("writerDevice") var writerDevice: String? = null
)

object UltimaSettingsSyncUtils {
    private const val TAG = "UltimaSync"
    private const val COMPRESSED_PREFIX = "gz:"

    private fun compressData(data: String): String {
        val bos = ByteArrayOutputStream()
        GZIPOutputStream(bos).use { gz ->
            gz.write(data.toByteArray(Charsets.UTF_8))
        }
        return COMPRESSED_PREFIX + Base64.encodeToString(bos.toByteArray(), Base64.NO_WRAP)
    }

    private fun decompressData(data: String): String {
        if (!data.startsWith(COMPRESSED_PREFIX)) return data
        val compressed = Base64.decode(data.removePrefix(COMPRESSED_PREFIX), Base64.NO_WRAP)
        val bis = ByteArrayInputStream(compressed)
        return GZIPInputStream(bis).bufferedReader(Charsets.UTF_8).readText()
    }

    @SuppressLint("HardwareIds")
    fun getDeviceId(packageName: String, context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        if (!androidId.isNullOrEmpty()) {
            return md5(packageName + androidId)
        }

        // ANDROID_ID is null (very rare). Fallback to device info hash.
        val deviceInfo = "${Build.BRAND}_${Build.MODEL}_${Build.DEVICE}"
        return md5(packageName + UUID.nameUUIDFromBytes(deviceInfo.toByteArray()).toString())
    }

    fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(input.toByteArray())
        val sb = StringBuilder()
        for (byte in bytes) {
            sb.append(String.format("%02x", byte))
        }
        return sb.toString()
    }

    // --- v2 Manifest/Category API ---

    suspend fun fetchManifest(): SyncManifest? {
        val creds = UltimaStorageManager.appSettingsSyncCreds ?: return null
        if (!creds.isLoggedIn()) return null

        return try {
            val url = "${creds.activeUrl}sync/${creds.syncKey}/manifest.json"
            val res = app.get(url)
            if (res.code == 200) {
                val body = res.text
                if (body.isEmpty() || body == "null") return null
                mapper.readValue<SyncManifest>(body)
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "fetchManifest failed: ${e.message}")
            null
        }
    }

    suspend fun pushManifest(manifest: SyncManifest): Boolean {
        val creds = UltimaStorageManager.appSettingsSyncCreds ?: return false
        if (!creds.isLoggedIn()) return false

        return try {
            val url = "${creds.activeUrl}sync/${creds.syncKey}/manifest.json"
            val res = app.put(url, json = manifest)
            res.code in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "pushManifest failed: ${e.message}")
            false
        }
    }

    suspend fun fetchCategory(category: SyncCategory): SyncCategoryPayload? {
        val creds = UltimaStorageManager.appSettingsSyncCreds ?: return null
        if (!creds.isLoggedIn()) return null

        return try {
            val url = "${creds.activeUrl}sync/${creds.syncKey}/categories/${category.key}.json"
            val res = app.get(url)
            if (res.code == 200) {
                val body = res.text
                if (body.isEmpty() || body == "null") return null
                val payload = mapper.readValue<SyncCategoryPayload>(body)
                return payload.copy(data = decompressData(payload.data))
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "fetchCategory(${category.key}) failed: ${e.message}")
            null
        }
    }

    suspend fun registerDevice(): Boolean {
        val creds = UltimaStorageManager.appSettingsSyncCreds ?: return false
        if (!creds.isLoggedIn()) return false
        return try {
            val deviceUrl = "${creds.activeUrl}sync/${creds.syncKey}/devices/${creds.deviceId}.json"
            val device = FirebaseDevice(creds.deviceName ?: "Unknown", creds.deviceId ?: "", System.currentTimeMillis())
            val res = app.put(deviceUrl, json = device)
            res.code in 200..299
        } catch (e: Exception) {
            Log.e(TAG, "registerDevice failed: ${e.message}")
            false
        }
    }

    /**
     * Batch push multiple categories in parallel.
     * Uploads category payloads concurrently, then does a single manifest read-modify-write.
     * @param categoryData map of SyncCategory to Pair(jsonData, hash)
     * @return set of categories that were successfully pushed
     */
    suspend fun pushCategories(
        categoryData: Map<SyncCategory, Pair<String, String>>
    ): Set<SyncCategory> {
        val creds = UltimaStorageManager.appSettingsSyncCreds ?: return emptySet()
        if (!creds.isLoggedIn()) return emptySet()
        if (categoryData.isEmpty()) return emptySet()

        val now = System.currentTimeMillis()
        val deviceName = creds.deviceName ?: "Unknown"
        val successfulCategories = mutableSetOf<SyncCategory>()

        try {
            // 1. Push all category payloads in parallel
            coroutineScope {
                val jobs = categoryData.map { (category, dataPair) ->
                    async(Dispatchers.IO) {
                        try {
                            val catUrl = "${creds.activeUrl}sync/${creds.syncKey}/categories/${category.key}.json"
                            val compressedData = compressData(dataPair.first)
                            val payload = SyncCategoryPayload(compressedData, now, deviceName)
                            Log.d(TAG, "Push ${category.key}: raw=${dataPair.first.length} chars, compressed=${compressedData.length} chars")
                            val catRes = app.put(catUrl, json = payload)
                            if (catRes.code in 200..299) {
                                category to true
                            } else {
                                Log.e(TAG, "pushCategories: ${category.key} failed HTTP ${catRes.code}: ${catRes.text.take(200)}")
                                category to false
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "pushCategories: ${category.key} error: ${e.message}")
                            category to false
                        }
                    }
                }
                val results = jobs.awaitAll()
                results.filter { it.second }.forEach { successfulCategories.add(it.first) }
            }

            if (successfulCategories.isEmpty()) return emptySet()

            // 2. Single manifest read-modify-write for all successful categories
            val currentManifest = fetchManifest() ?: SyncManifest()
            var updatedManifest = currentManifest
            for (category in successfulCategories) {
                val hash = categoryData[category]?.second ?: ""
                val meta = SyncCategoryMeta(now, hash, deviceName)
                updatedManifest = updatedManifest.withUpdated(category, meta)
            }
            pushManifest(updatedManifest)

            // 4. Save local timestamps and hashes for successful categories
            for (category in successfulCategories) {
                val hash = categoryData[category]?.second ?: ""
                val data = categoryData[category]?.first ?: ""
                UltimaStorageManager.setCategoryTimestamp(category, now)
                UltimaStorageManager.setCategoryHash(category, hash)
                
                try {
                    val backupFile = mapper.readValue<BackupFile>(data)
                    val keys = UltimaBackupUtils.getBackupFileKeys(backupFile)
                    UltimaStorageManager.setCategorySyncedKeys(category, keys)
                } catch (e: Exception) {
                    Log.e(TAG, "Failed to update local synced keys for ${category.key}: ${e.message}")
                }
            }

            Log.d(TAG, "Batch pushed ${successfulCategories.size}/${categoryData.size} categories")
        } catch (e: Exception) {
            Log.e(TAG, "pushCategories batch error: ${e.message}")
        }

        return successfulCategories
    }

    // --- Legacy v1 API (kept for migration + device list) ---

    suspend fun fetchDevices(): List<FirebaseDevice>? {
        val creds = UltimaStorageManager.appSettingsSyncCreds ?: return null
        if (!creds.isLoggedIn()) return null

        return try {
            val url = "${creds.activeUrl}sync/${creds.syncKey}/devices.json"
            val res = app.get(url)
            if (res.code == 200) {
                val body = res.text
                if (body.isEmpty() || body == "null") return emptyList()
                val map = mapper.readValue<Map<String, FirebaseDevice>>(body)
                map.values.toList()
            } else null
        } catch (_: Exception) { null }
    }

    suspend fun fetchSharedData(): FirebaseSharedData? {
        val creds = UltimaStorageManager.appSettingsSyncCreds ?: return null
        if (!creds.isLoggedIn()) return null

        return try {
            val url = "${creds.activeUrl}sync/${creds.syncKey}/shared_data.json"
            val res = app.get(url)
            if (res.code == 200) {
                val body = res.text
                if (body.isEmpty() || body == "null") return null
                mapper.readValue<FirebaseSharedData>(body)
            } else null
        } catch (_: Exception) { null }
    }

    suspend fun deleteSharedData(): Boolean {
        val creds = UltimaStorageManager.appSettingsSyncCreds ?: return false
        if (!creds.isLoggedIn()) return false

        return try {
            val url = "${creds.activeUrl}sync/${creds.syncKey}/shared_data.json"
            val res = app.delete(url)
            res.code in 200..299
        } catch (_: Exception) { false }
    }

    suspend fun deregisterThisDevice(): Pair<Boolean, String?> {
        val creds = UltimaStorageManager.appSettingsSyncCreds ?: return false to "Credentials not found"
        if (!creds.isLoggedIn()) return false to "Not logged in"

        return try {
            val url = "${creds.activeUrl}sync/${creds.syncKey}/devices/${creds.deviceId}.json"
            val res = app.delete(url)
            if (res.code in 200..299) {
                true to "Device removed"
            } else {
                false to "Failed to remove device with code ${res.code}"
            }
        } catch (e: Exception) {
            false to e.message
        }
    }

    suspend fun removeDevice(deviceId: String): Pair<Boolean, String?> {
        val creds = UltimaStorageManager.appSettingsSyncCreds ?: return false to "Credentials not found"
        if (!creds.isLoggedIn()) return false to "Not logged in"

        return try {
            val url = "${creds.activeUrl}sync/${creds.syncKey}/devices/$deviceId.json"
            val res = app.delete(url)
            if (res.code in 200..299) {
                true to "Device removed"
            } else {
                false to "Failed with code ${res.code}"
            }
        } catch (e: Exception) {
            false to e.message
        }
    }
}
