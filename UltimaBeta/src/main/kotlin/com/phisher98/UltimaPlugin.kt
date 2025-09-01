package com.phisher98

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
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
        triggerSync()
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
        openSettings = {
            val frag = UltimaSettings(this)
            frag.show(
                activity?.supportFragmentManager ?: throw Exception("Unable to open settings"),
                ""
            )
        }
    }

    fun reload(context: Context?) {
        val pluginData = PluginManager.getPluginsOnline().find { it.internalName.contains("Ultima Beta") }
        if (pluginData == null) {
            afterPluginsLoadedEvent.invoke(true)
        }
    }

    private fun triggerSync() {
        UltimaStorageManager.deviceSyncCreds?.let { creds ->
            CoroutineScope(Dispatchers.IO).launch {
                creds.syncThisDevice()
                creds.fetchDevices()
            }
        }
    }
}