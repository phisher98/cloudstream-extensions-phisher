package com.latanime

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class LatanimeProvider: Plugin() {
    override fun load(context: Context) {
        pingAnalytics("Latanime")
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(Latanime())
    }
}