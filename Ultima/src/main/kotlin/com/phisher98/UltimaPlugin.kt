package com.phisher98

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.api.Log
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.MainActivity.Companion.afterPluginsLoadedEvent
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.plugins.PluginManager
import com.lagradost.cloudstream3.ui.home.HomeViewModel.Companion.getResumeWatching
import com.lagradost.cloudstream3.utils.AppUtils.toJson
import com.lagradost.cloudstream3.utils.DataStoreHelper
import com.fasterxml.jackson.module.kotlin.readValue
import com.lagradost.cloudstream3.mapper
import com.lagradost.cloudstream3.CommonActivity.showToast
import com.lagradost.cloudstream3.utils.DataStore.getDefaultSharedPrefs
import com.lagradost.cloudstream3.utils.DataStore.getSharedPrefs
import kotlinx.coroutines.*

@CloudstreamPlugin
class UltimaPlugin : Plugin() {
    var activity: AppCompatActivity? = null
    var lastOtherDeviceData: String? = null
    var lastBackupData: String? = null

    private var dataPrefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var defaultPrefsListener: SharedPreferences.OnSharedPreferenceChangeListener? = null
    private var backupJob: Job? = null

    fun backupDeviceDebounced(context: Context) {
        val creds = UltimaStorageManager.appSettingsSyncCreds ?: return
        if (!creds.isLoggedIn() || !creds.backupDevice) return

        backupJob?.cancel()
        backupJob = CoroutineScope(Dispatchers.IO).launch {
            delay(2000) // Debounce for 2 seconds to batch preference updates
            backupDevice(context)
        }
    }

    fun backupDevice(context: Context) {
        val creds = UltimaStorageManager.appSettingsSyncCreds ?: return
        if (!creds.isLoggedIn() || !creds.backupDevice) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val data = UltimaBackupUtils.getBackup(context, getResumeWatching())?.toJson() ?: ""
                if (data.isBlank()) return@launch
                
                // Avoid backup loop if the data hasn't changed since the last restore/backup
                if (data == lastOtherDeviceData || data == lastBackupData) {
                    Log.d("UltimaSync", "Data is identical to last sync state. Skipping backup.")
                    return@launch
                }

                val now = System.currentTimeMillis()
                val res = UltimaSettingsSyncUtils.pushSharedData(context, data, now)
                if (res.first) {
                    UltimaStorageManager.lastLocalSyncTime = now
                    lastOtherDeviceData = data
                    lastBackupData = data
                    Log.d("UltimaSync", "Periodic auto backup success at $now")
                } else {
                    Log.e("UltimaSync", "Periodic auto backup failed: ${res.second}")
                }
            } catch (e: Exception) {
                Log.e("UltimaSync", "Auto backup error: ${e.message}")
            }
        }
    }

    override fun load(context: Context) {
        activity = context as AppCompatActivity
        // All providers should be added in this manner
        registerMainAPI(Ultima(this))

        UltimaStorageManager.currentMetaProviders.forEach { metaProvider ->
            when (metaProvider.first) {
                //"Simkl" -> if (metaProvider.second) registerMainAPI(Simkl(this))
                //"AniList" -> if (metaProvider.second) registerMainAPI(AniList(this))
                //"MyAnimeList" -> if (metaProvider.second) registerMainAPI(MyAnimeList(this))
                //"TMDB" -> if (metaProvider.second) registerMainAPI(Tmdb(this))
                //"Trakt" -> if (metaProvider.second) registerMainAPI(Trakt(this))
                else -> {}
            }
        }

        openSettings = { ctx ->
            val act = ctx as? AppCompatActivity
            if (act != null && !act.isFinishing && !act.isDestroyed) {
                val frag = UltimaSettings(this)
                frag.show(act.supportFragmentManager, "UltimaSettingsDialog")
            } else {
                Log.e("Plugin", "Activity is not valid anymore, cannot show settings dialog")
            }
        }

        // Initialize App Settings & Data Sync hooks
        val creds = UltimaStorageManager.appSettingsSyncCreds
        if (creds != null && creds.isLoggedIn()) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val sharedData = UltimaSettingsSyncUtils.fetchSharedData(context)
                    if (sharedData != null && !sharedData.syncedData.isNullOrBlank()) {
                        val cloudTime = sharedData.lastUpdated
                        val localTime = UltimaStorageManager.lastLocalSyncTime
                        if (cloudTime > localTime && creds.restoreDevice) {
                            Log.d("UltimaSync", "Startup restore from cloud. Cloud: $cloudTime, Local: $localTime")
                            withContext(Dispatchers.Main) {
                                showToast("Syncing from cloud (${sharedData.writerDevice})...")
                            }
                            val backupFile = mapper.readValue<BackupFile>(sharedData.syncedData!!)
                            lastOtherDeviceData = sharedData.syncedData
                            lastBackupData = sharedData.syncedData
                            UltimaBackupUtils.restore(context, backupFile, true, true)
                            UltimaStorageManager.lastLocalSyncTime = cloudTime
                            reload()
                            withContext(Dispatchers.Main) {
                                MainActivity.bookmarksUpdatedEvent(true)
                            }
                        } else if (localTime == 0L && creds.backupDevice) {
                            // Baseline push
                            backupDevice(context)
                        }
                    } else if (creds.backupDevice) {
                        // Baseline push
                        backupDevice(context)
                    }
                } catch (e: Exception) {
                    Log.e("UltimaSync", "Startup restore failed: ${e.message}")
                }
            }
        }

        // Safeguard: Check if any online plugins are missing locally, and redownload them on startup
        if (creds != null && creds.isLoggedIn() && creds.restoreDevice) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    delay(3000) // Delay slightly to let standard initialization settle
                    val onlinePlugins = PluginManager.getPluginsOnline()
                    val missingPlugins = onlinePlugins.filter { !java.io.File(it.filePath).exists() || java.io.File(it.filePath).length() == 0L }
                    if (missingPlugins.isNotEmpty()) {
                        Log.d("UltimaSync", "Detected ${missingPlugins.size} missing/0 KB plugins on startup. Triggering restore to repair them.")
                        val sharedData = UltimaSettingsSyncUtils.fetchSharedData(context)
                        if (sharedData != null && !sharedData.syncedData.isNullOrBlank()) {
                            val backupFile = mapper.readValue<BackupFile>(sharedData.syncedData!!)
                            UltimaBackupUtils.restore(context, backupFile, false, true) // Restore datastore/extensions only
                            reload()
                        }
                    }
                } catch (e: Exception) {
                    Log.e("UltimaSync", "Startup plugins validation failed: ${e.message}")
                }
            }
        }

        // Register preference change listeners for real-time instant backups on changes
        val listener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
            if (key != null && !UltimaBackupUtils.nonTransferableKeys.any { key.contains(it) }) {
                Log.d("UltimaSync", "Preference changed: $key. Triggering debounced auto-backup.")
                backupDeviceDebounced(context)
            }
        }
        dataPrefsListener = listener
        defaultPrefsListener = listener
        try {
            context.getSharedPrefs().registerOnSharedPreferenceChangeListener(listener)
            context.getDefaultSharedPrefs().registerOnSharedPreferenceChangeListener(listener)
            Log.d("UltimaSync", "Registered real-time preference change listeners for instant sync.")
        } catch (e: Exception) {
            Log.e("UltimaSync", "Failed to register preference change listeners: ${e.message}")
        }

        // Auto backup on any relevant event to ensure settings are always in sync
        val backupListener = { _: Boolean -> backupDeviceDebounced(context) }
        MainActivity.bookmarksUpdatedEvent += backupListener
        MainActivity.afterPluginsLoadedEvent += backupListener
        MainActivity.mainPluginsLoadedEvent += backupListener
        MainActivity.reloadHomeEvent += backupListener
        MainActivity.reloadAccountEvent += backupListener

        // Periodic sync loop - runs every 60 seconds, backs up when data changes and checks for updates
        CoroutineScope(Dispatchers.IO).launch {
            var lastResumeWatching: List<DataStoreHelper.ResumeWatchingResult>? = null
            var ticksSinceLastBackup = 0
            while (isActive) {
                try {
                    val currentCreds = UltimaStorageManager.appSettingsSyncCreds
                    if (currentCreds != null && currentCreds.isLoggedIn()) {
                        // Check for updates from cloud
                        if (currentCreds.restoreDevice) {
                            val sharedData = UltimaSettingsSyncUtils.fetchSharedData(context)
                            if (sharedData != null && !sharedData.syncedData.isNullOrBlank()) {
                                val cloudTime = sharedData.lastUpdated
                                val localTime = UltimaStorageManager.lastLocalSyncTime
                                if (cloudTime > localTime) {
                                    Log.d("UltimaSync", "Periodic restore. Cloud: $cloudTime, Local: $localTime")
                                    withContext(Dispatchers.Main) {
                                        showToast("Syncing from cloud (${sharedData.writerDevice})...")
                                    }
                                    val backupFile = mapper.readValue<BackupFile>(sharedData.syncedData!!)
                                    lastOtherDeviceData = sharedData.syncedData
                                    lastBackupData = sharedData.syncedData
                                    UltimaBackupUtils.restore(context, backupFile, true, true)
                                    UltimaStorageManager.lastLocalSyncTime = cloudTime
                                    reload()
                                    withContext(Dispatchers.Main) {
                                        MainActivity.bookmarksUpdatedEvent(true)
                                    }
                                }
                            }
                        }

                        // Backup our changes
                        if (currentCreds.backupDevice) {
                            val currentResumeWatching = getResumeWatching()
                            val currentBackupData = UltimaBackupUtils.getBackup(context, currentResumeWatching)?.toJson() ?: ""
                            val dataChanged = currentBackupData != lastBackupData
                            if (dataChanged && currentBackupData.isNotBlank()) {
                                ticksSinceLastBackup = 0
                                backupDevice(context)
                                lastBackupData = currentBackupData
                            } else {
                                ticksSinceLastBackup++
                                // Periodic heartbeat backup every ~10 minutes (60s * 10)
                                if (ticksSinceLastBackup >= 10) {
                                    ticksSinceLastBackup = 0
                                    backupDevice(context)
                                }
                            }
                        }
                    }
                } catch (e: Exception) {
                    Log.e("UltimaSync", "Periodic loop error: ${e.message}")
                }
                delay(60_000L)
            }
        }
    }

    fun reload() {
        CoroutineScope(Dispatchers.Main).launch {
            try {
                afterPluginsLoadedEvent.invoke(true)
            } catch (e: Throwable) {
                Log.e("UltimaSync", "afterPluginsLoadedEvent invoke failed: ${e.message}")
            }
        }
    }
}