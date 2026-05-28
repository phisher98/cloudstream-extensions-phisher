package com.phisher98

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mapper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.api.Log
import java.security.MessageDigest
import java.util.UUID

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

    fun isCategoryEnabled(category: SyncCategory): Boolean {
        return when (category) {
            SyncCategory.EXTENSIONS -> syncExtensions
            SyncCategory.BOOKMARKS -> syncBookmarks
            SyncCategory.RESUME_WATCHING -> syncResumeWatching
            SyncCategory.SEARCH_HISTORY -> syncSearchHistory
            SyncCategory.SETTINGS -> syncSettings
        }
    }
}

// --- Sync v2 Category System ---

enum class SyncCategory(val key: String) {
    EXTENSIONS("extensions"),
    SETTINGS("settings"),
    BOOKMARKS("bookmarks"),
    RESUME_WATCHING("resume_watching"),
    SEARCH_HISTORY("search_history");

    companion object {
        fun fromKey(key: String): SyncCategory? = entries.find { it.key == key }
    }
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

    fun getDeviceId(packageName: String, context: Context): String {
        val androidId = Settings.Secure.getString(context.contentResolver, Settings.Secure.ANDROID_ID)
        if (!androidId.isNullOrEmpty()) {
            return md5(packageName + androidId)
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                @Suppress("MissingPermission")
                val serialNumber = Build.getSerial()
                if (!serialNumber.isNullOrEmpty()) {
                    return md5(packageName + serialNumber)
                }
            } catch (_: SecurityException) {
            }
        } else {
            @Suppress("DEPRECATION")
            val serialNumber = Build.SERIAL
            if (!serialNumber.isNullOrEmpty() && serialNumber != "unknown") {
                return md5(packageName + serialNumber)
            }
        }

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

    suspend fun fetchManifest(context: Context): SyncManifest? {
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

    suspend fun pushManifest(context: Context, manifest: SyncManifest): Boolean {
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

    suspend fun fetchCategory(context: Context, category: SyncCategory): SyncCategoryPayload? {
        val creds = UltimaStorageManager.appSettingsSyncCreds ?: return null
        if (!creds.isLoggedIn()) return null

        return try {
            val url = "${creds.activeUrl}sync/${creds.syncKey}/categories/${category.key}.json"
            val res = app.get(url)
            if (res.code == 200) {
                val body = res.text
                if (body.isEmpty() || body == "null") return null
                mapper.readValue<SyncCategoryPayload>(body)
            } else null
        } catch (e: Exception) {
            Log.e(TAG, "fetchCategory(${category.key}) failed: ${e.message}")
            null
        }
    }

    suspend fun pushCategory(context: Context, category: SyncCategory, data: String, hash: String): Boolean {
        val creds = UltimaStorageManager.appSettingsSyncCreds ?: return false
        if (!creds.isLoggedIn()) return false

        val now = System.currentTimeMillis()
        val deviceName = creds.deviceName ?: "Unknown"

        return try {
            // 1. Push the category data
            val catUrl = "${creds.activeUrl}sync/${creds.syncKey}/categories/${category.key}.json"
            val payload = SyncCategoryPayload(data, now, deviceName)
            val catRes = app.put(catUrl, json = payload)

            if (catRes.code !in 200..299) {
                Log.e(TAG, "pushCategory(${category.key}) failed: HTTP ${catRes.code}")
                return false
            }

            // 2. Update the manifest for this category
            val currentManifest = fetchManifest(context) ?: SyncManifest()
            val meta = SyncCategoryMeta(now, hash, deviceName)
            val updatedManifest = currentManifest.withUpdated(category, meta)
            pushManifest(context, updatedManifest)

            // 3. Update device status
            val deviceUrl = "${creds.activeUrl}sync/${creds.syncKey}/devices/${creds.deviceId}.json"
            val device = FirebaseDevice(deviceName, creds.deviceId ?: "", now)
            app.put(deviceUrl, json = device)

            // 4. Save local timestamp and hash
            UltimaStorageManager.setCategoryTimestamp(category, now)
            UltimaStorageManager.setCategoryHash(category, hash)

            Log.d(TAG, "Pushed category ${category.key} successfully (hash=$hash)")
            true
        } catch (e: Exception) {
            Log.e(TAG, "pushCategory(${category.key}) error: ${e.message}")
            false
        }
    }

    // --- Legacy v1 API (kept for migration + device list) ---

    suspend fun fetchDevices(context: Context): List<FirebaseDevice>? {
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
        } catch (e: Exception) { null }
    }

    suspend fun fetchSharedData(context: Context): FirebaseSharedData? {
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
        } catch (e: Exception) { null }
    }

    suspend fun deleteSharedData(context: Context): Boolean {
        val creds = UltimaStorageManager.appSettingsSyncCreds ?: return false
        if (!creds.isLoggedIn()) return false

        return try {
            val url = "${creds.activeUrl}sync/${creds.syncKey}/shared_data.json"
            val res = app.delete(url)
            res.code in 200..299
        } catch (e: Exception) { false }
    }

    suspend fun pushSharedData(context: Context, backupData: String, timestamp: Long): Pair<Boolean, String?> {
        val creds = UltimaStorageManager.appSettingsSyncCreds ?: return false to "Credentials not found"
        if (!creds.isLoggedIn()) return false to "Not logged in"

        return try {
            val sharedUrl = "${creds.activeUrl}sync/${creds.syncKey}/shared_data.json"
            val shared = FirebaseSharedData(timestamp, backupData, creds.deviceName ?: "Unknown Device")
            val sharedRes = app.put(sharedUrl, json = shared)

            val deviceUrl = "${creds.activeUrl}sync/${creds.syncKey}/devices/${creds.deviceId}.json"
            val device = FirebaseDevice(creds.deviceName ?: "Unknown Device", creds.deviceId ?: "", timestamp)
            app.put(deviceUrl, json = device)

            if (sharedRes.code in 200..299) {
                true to "Sync success"
            } else {
                false to "Sync failed with code ${sharedRes.code}"
            }
        } catch (e: Exception) {
            false to e.message
        }
    }

    suspend fun syncThisDevice(context: Context, backupData: String): Pair<Boolean, String?> {
        val now = System.currentTimeMillis()
        return pushSharedData(context, backupData, now)
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
