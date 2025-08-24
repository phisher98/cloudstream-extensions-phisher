package com.phisher98

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class StremioXPlugin: Plugin() {
    override fun load(context: Context) {
        val sharedPref = context.getSharedPreferences("StremioX", Context.MODE_PRIVATE)
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(StremioX())
        registerMainAPI(StremioC())
        /*
        val activity = context as AppCompatActivity
        openSettings = {
            val frag = BottomSheet(this, sharedPref)
            frag.show(activity.supportFragmentManager, "Frag")
        }
        */
    }
}