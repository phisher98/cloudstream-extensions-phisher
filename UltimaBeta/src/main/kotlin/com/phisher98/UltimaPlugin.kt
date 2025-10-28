package com.phisher98

import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.api.Log
import com.lagradost.cloudstream3.MainActivity.Companion.afterPluginsLoadedEvent
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.plugins.PluginManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

@CloudstreamPlugin
class UltimaBetaPlugin : Plugin() {
    var activity: AppCompatActivity? = null

    override fun load(context: Context) {
        activity = context as AppCompatActivity
        handler.post(syncRunnable)
        // All providers should be added in this manner
        registerMainAPI(UltimaBeta(this))
        UltimaStorageManager.currentMetaProviders.forEach { metaProvider ->
            when (metaProvider.first) {
                "Simkl" -> if (metaProvider.second) registerMainAPI(Simkl(this))
                "AniList" -> if (metaProvider.second) registerMainAPI(AniList(this))
                "MyAnimeList" -> if (metaProvider.second) registerMainAPI(MyAnimeList(this))
                "TMDB" -> if (metaProvider.second) registerMainAPI(Tmdb(this))
                "Trakt" -> if (metaProvider.second) registerMainAPI(Trakt(this))
                else -> {}
            }
        }


        openSettings = { ctx ->
            val act = ctx as? AppCompatActivity
            if (act != null && !act.isFinishing && !act.isDestroyed) {
                val frag = UltimaSettings(this)
                frag.show(act.supportFragmentManager, "UltimaSettingsDialog")
            } else {
                Log.e("UltimaPlugin", "Activity is not valid anymore, cannot show settings dialog")
            }
        }
    }

    fun reload() {
        val pluginData = PluginManager.getPluginsOnline().find { it.internalName.contains("Ultima Beta") }
        if (pluginData == null) {
            afterPluginsLoadedEvent.invoke(true)
        }
    }

    private val handler = Handler(Looper.getMainLooper())

    private val syncRunnable = object : Runnable {
        override fun run() {
            triggerSync()
            handler.postDelayed(this, 5000)
        }
    }
    private fun triggerSync() {
        UltimaStorageManager.deviceSyncCreds?.let { creds ->
            CoroutineScope(Dispatchers.IO).launch {
                runCatching {
                    creds.syncThisDevice()
                    Log.i("Sync", "Local backup synced")
                    val devices = creds.fetchDevices()
                    devices?.forEach { device ->
                        Log.i("Sync", "Device: ${device.name}, payload: ${device.payload}")
                    }
                }.onFailure {
                    Log.e("Sync", "Sync/Backup/Fetch failed: ${it.message}")
                }
            }
        }
    }




}