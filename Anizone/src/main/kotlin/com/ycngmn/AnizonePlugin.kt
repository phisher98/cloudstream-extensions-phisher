package com.ycngmn

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import androidx.appcompat.app.AppCompatActivity

@CloudstreamPlugin
class AnizonePlugin: Plugin() {
    override fun load(context: Context) {
        val sharedPref = context.getSharedPreferences("AnizonePref", Context.MODE_PRIVATE)
        registerMainAPI(AnizoneProvider(sharedPref))

        openSettings = { ctx ->
            val act = ctx as AppCompatActivity
            val frag = SettingsFragment(this, sharedPref)
            frag.show(act.supportFragmentManager, "AnizoneSettings")
        }
    }
}
