package com.phisher98

import android.content.Context
import android.os.Build
import android.provider.Settings
import com.fasterxml.jackson.annotation.JsonProperty
import com.lagradost.cloudstream3.app
import com.lagradost.cloudstream3.mapper
import com.fasterxml.jackson.module.kotlin.readValue
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
}

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
            } catch (e: SecurityException) {
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

    private fun md5(input: String): String {
        val digest = MessageDigest.getInstance("MD5")
        val bytes = digest.digest(input.toByteArray())
        val sb = StringBuilder()
        for (byte in bytes) {
            sb.append(String.format("%02x", byte))
        }
        return sb.toString()
    }

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
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
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
            } else {
                null
            }
        } catch (e: Exception) {
            null
        }
    }

    suspend fun pushSharedData(context: Context, backupData: String, timestamp: Long): Pair<Boolean, String?> {
        val creds = UltimaStorageManager.appSettingsSyncCreds ?: return false to "Credentials not found"
        if (!creds.isLoggedIn()) return false to "Not logged in"
        
        return try {
            // 1. Update shared data
            val sharedUrl = "${creds.activeUrl}sync/${creds.syncKey}/shared_data.json"
            val shared = FirebaseSharedData(timestamp, backupData, creds.deviceName ?: "Unknown Device")
            val sharedRes = app.put(sharedUrl, json = shared)
            
            // 2. Update device status
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
        // Compatibility wrapper: push to shared data and register device
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
}
