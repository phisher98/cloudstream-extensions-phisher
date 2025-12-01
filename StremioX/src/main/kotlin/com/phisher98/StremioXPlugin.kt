package com.phisher98

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.api.Log
import com.lagradost.cloudstream3.MainActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.lagradost.cloudstream3.plugins.PluginData
import com.lagradost.cloudstream3.plugins.PluginManager
import org.json.JSONArray

@CloudstreamPlugin
class StremioXPlugin : Plugin() {
    private val PREF_FILE = "StremioX"
    private val PREF_KEY_LINKS = "stremio_saved_links"

    override fun load(context: Context) {
        try {
            registerMainAPI(StremioX("", "StremioX"))
        } catch (_: Throwable) {}
        try {
            registerMainAPI(StremioC("", "StremioC"))
        } catch (_: Throwable) {}
        reload(context)
        val activity = context as? AppCompatActivity
        openSettings = {
            val frag = SettingsBottomFragment(this, context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE))
            activity?.supportFragmentManager?.let { fm -> frag.show(fm, "Frag") }
        }
    }

    fun reload(context: Context) {
        try {
            val prefs = context.getSharedPreferences(PREF_FILE, Context.MODE_PRIVATE)
            val json = prefs.getString(PREF_KEY_LINKS, null) ?: "[]"
            val arr = JSONArray(json)
            val links = mutableListOf<LinkEntry>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                links.add(
                    LinkEntry(
                        id = obj.optLong("id", System.currentTimeMillis()),
                        name = obj.optString("name", ""),
                        link = obj.optString("link", ""),
                        type = obj.optString("type", "StremioX")
                    )
                )
            }
            for (item in links) {
                val pluginsOnline: Array<PluginData> = PluginManager.getPluginsOnline()
                var found: PluginData? = null
                for (p in pluginsOnline) {
                    if (p.internalName.contains(item.name, ignoreCase = true)) {
                        found = p
                        break
                    }
                }
                if (found != null) {
                    try {
                        PluginManager.unloadPlugin(found.filePath)
                    } catch (e: Throwable) {
                        Log.e("StremioXPlugin", "unload failed ${e.message}")
                    }
                } else {
                    try {
                        when (item.type) {
                            "StremioX" -> {
                                try {
                                    registerMainAPI(StremioX(item.link, item.name))
                                } catch (_: Throwable) {
                                    try { registerMainAPI(StremioX("", item.name)) } catch (_: Throwable) {}
                                }
                            }
                            "StremioC" -> {
                                try {
                                    registerMainAPI(StremioC(item.link, item.name))
                                } catch (_: Throwable) {
                                    try { registerMainAPI(StremioC("", item.name)) } catch (_: Throwable) {}
                                }
                            }
                            else -> {
                                try { registerMainAPI(StremioX(item.link, item.name)) } catch (_: Throwable) {}
                            }
                        }
                    } catch (e: Throwable) {
                        Log.e("StremioXPlugin", "register failed ${e.message}")
                    }
                }
            }
            try {
                MainActivity.afterPluginsLoadedEvent.invoke(true)
            } catch (e: Throwable) {
                Log.w("StremioXPlugin", "afterPluginsLoaded invoke failed ${e.message}")
            }
        } catch (e: Throwable) {
            Log.e("StremioXPlugin", "reload error ${e.message}")
        }
    }

    data class LinkEntry(
        val id: Long,
        val name: String,
        val link: String,
        val type: String
    )
}
