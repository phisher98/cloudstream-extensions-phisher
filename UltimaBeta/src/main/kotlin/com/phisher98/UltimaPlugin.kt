package com.phisher98

import android.content.Context
import android.os.Handler
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.api.Log
import com.lagradost.cloudstream3.AcraApplication.Companion.context
import com.lagradost.cloudstream3.MainActivity.Companion.afterPluginsLoadedEvent
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.plugins.PluginManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import androidx.core.content.edit
import com.lagradost.cloudstream3.AcraApplication
import com.lagradost.cloudstream3.AcraApplication.Companion
import com.lagradost.cloudstream3.utils.DataStore.getDefaultSharedPrefs
import com.lagradost.cloudstream3.utils.DataStore.getSharedPrefs

@CloudstreamPlugin
class UltimaBetaPlugin : Plugin() {
    var activity: AppCompatActivity? = null

    companion object {
        inline fun Handler.postFunction(crossinline function: () -> Unit) {
            this.post { function() }
        }
    }

    override fun load(context: Context) {
        activity = context as AppCompatActivity
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
        triggerSync()
    }

    fun reload(context: Context?) {
        try {
            val pluginData =
                PluginManager.getPluginsOnline().find { it.internalName.contains("Ultima") }
            if (pluginData == null) {
                // Use reflection to call the internal function if it exists
                try {
                    val method = PluginManager::class.java.getDeclaredMethod(
                        "_DO_NOT_CALL_FROM_A_PLUGIN_hotReloadAllLocalPlugins",
                        AppCompatActivity::class.java
                    )
                    method.invoke(null, context as AppCompatActivity)
                } catch (e: Exception) {
                    // If the method doesn't exist or fails, just invoke the after plugins loaded event
                    afterPluginsLoadedEvent.invoke(true)
                }
            } else {
                PluginManager.unloadPlugin(pluginData.filePath)
                try {
                    val method = PluginManager::class.java.getDeclaredMethod(
                        "_DO_NOT_CALL_FROM_A_PLUGIN_loadAllOnlinePlugins",
                        Context::class.java
                    )
                    method.invoke(null, context ?: throw Exception("Unable to load plugins"))
                } catch (e: Exception) {
                    // If the method doesn't exist or fails, continue
                }
                afterPluginsLoadedEvent.invoke(true)
            }
        } catch (e: Exception) {

            afterPluginsLoadedEvent.invoke(true)
        }
    }

    private fun triggerSync() {
        UltimaStorageManager.deviceSyncCreds?.let { creds ->
            CoroutineScope(Dispatchers.IO).launch {
                val result = creds.syncThisDevice()
            }
        }
    }
}