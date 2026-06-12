package com.phisher98

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import com.phisher98.settings.SettingsFragment

@CloudstreamPlugin
class TorraStreamProvider: Plugin() {
    override fun load(context: Context) {
        val sharedPref = context.getSharedPreferences("TorraStream", Context.MODE_PRIVATE)
        val savedMainApisString = sharedPref.getString("main_apis", "Trakt,Anime")
        val savedMainApis = savedMainApisString?.split(",") ?: listOf("Trakt", "Anime")

        if (savedMainApis.contains("TMDB")) {
            registerMainAPI(TorraStream(sharedPref))
        }
        if (savedMainApis.contains("Anime")) {
            registerMainAPI(TorraStreamAnime(sharedPref))
        }
        if (savedMainApis.contains("Trakt")) {
            registerMainAPI(TorraStreamTrakt(sharedPref))
        }

        openSettings = { ctx ->
            val activity = ctx as AppCompatActivity
            val frag = SettingsFragment(this, sharedPref)
            frag.show(activity.supportFragmentManager, "Frag")
        }
    }
}
