package com.MovieBox

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import androidx.appcompat.app.AppCompatActivity

@CloudstreamPlugin
class MovieBoxProviderPlugin : Plugin() {
    override fun load(context: Context) {
        val sharedPref = context.getSharedPreferences("MovieBox", Context.MODE_PRIVATE)
        registerMainAPI(MovieBoxProvider(sharedPref))
        val activity = context as AppCompatActivity
        openSettings = {
            val frag = SettingsFragment(this, sharedPref)
            frag.show(activity.supportFragmentManager, "MovieBoxSettings")
        }
    }
}
