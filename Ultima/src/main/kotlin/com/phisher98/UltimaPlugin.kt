package com.phisher98

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.api.Log
import com.lagradost.cloudstream3.MainActivity.Companion.afterPluginsLoadedEvent
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.plugins.PluginManager

@CloudstreamPlugin
class UltimaPlugin : Plugin() {
    var activity: AppCompatActivity? = null
    override fun load(context: Context) {
        activity = context as AppCompatActivity
        // All providers should be added in this manner
        registerMainAPI(Ultima(this))

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
                Log.e("Plugin", "Activity is not valid anymore, cannot show settings dialog")
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