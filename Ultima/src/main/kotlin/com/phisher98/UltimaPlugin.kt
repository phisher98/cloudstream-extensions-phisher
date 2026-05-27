package com.phisher98

import android.content.Context
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
import kotlinx.coroutines.*

@CloudstreamPlugin
class UltimaPlugin : Plugin() {
    var activity: AppCompatActivity? = null

    fun backupDevice(context: Context) {
        val creds = UltimaStorageManager.appSettingsSyncCreds ?: return
        if (!creds.isLoggedIn() || !creds.backupDevice) return
        CoroutineScope(Dispatchers.IO).launch {
            try {
                val data = UltimaBackupUtils.getBackup(context, getResumeWatching())?.toJson() ?: ""
                val res = UltimaSettingsSyncUtils.syncThisDevice(context, data)
                if (res.first) {
                    Log.d("UltimaSync", "Periodic auto backup success")
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
        if (creds != null && creds.isLoggedIn() && creds.restoreDevice) {
            CoroutineScope(Dispatchers.IO).launch {
                try {
                    val devices = UltimaSettingsSyncUtils.fetchDevices(context)
                    if (!devices.isNullOrEmpty()) {
                        val otherDevice = devices.firstOrNull { it.deviceId != creds.deviceId }
                        if (otherDevice != null && !otherDevice.syncedData.isNullOrBlank()) {
                            Log.d("UltimaSync", "Syncing from device: ${otherDevice.name}")
                            withContext(Dispatchers.Main) {
                                showToast("Syncing from ${otherDevice.name}...")
                            }
                            val backupFile = mapper.readValue<BackupFile>(otherDevice.syncedData!!)
                            // Restore without deleting existing data first - let selective restore handle what to overwrite
                            UltimaBackupUtils.restore(context, backupFile, true, true)
                            withContext(Dispatchers.Main) {
                                MainActivity.bookmarksUpdatedEvent(true)
                            }
                        } else {
                            Log.d("UltimaSync", "No other devices to restore from")
                        }
                    }
                } catch (e: Exception) {
                    Log.e("UltimaSync", "Startup restore failed: ${e.message}")
                }
            }
        }

        // Auto backup - only on bookmark changes to avoid rapid-fire Firebase writes
        MainActivity.bookmarksUpdatedEvent += { backupDevice(context) }

        // Periodic backup loop - runs every 60 seconds, backs up when data changes
        CoroutineScope(Dispatchers.IO).launch {
            var lastResumeWatching: List<DataStoreHelper.ResumeWatchingResult>? = null
            var ticksSinceLastBackup = 0
            while (isActive) {
                try {
                    val currentResumeWatching = getResumeWatching()
                    val dataChanged = currentResumeWatching != lastResumeWatching
                    if (dataChanged) {
                        ticksSinceLastBackup = 0
                        backupDevice(context)
                        lastResumeWatching = currentResumeWatching
                    } else {
                        ticksSinceLastBackup++
                        // Periodic heartbeat backup every ~10 minutes (60s * 10)
                        if (ticksSinceLastBackup >= 10) {
                            ticksSinceLastBackup = 0
                            backupDevice(context)
                        }
                    }
                } catch (e: Exception) {
                    Log.e("UltimaSync", "Periodic backup error: ${e.message}")
                }
                delay(60_000L)
            }
        }
    }

    fun reload() {
        val pluginData = PluginManager.getPluginsOnline().find { it.internalName.contains("Ultima") }
        if (pluginData == null) {
            afterPluginsLoadedEvent.invoke(true)
        }
    }
}