package com.likdev256

import android.content.Context
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class Bolly2TollyPlugin : Plugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(Bolly2TollyProvider())
        registerExtractorAPI(NeoHD())
        registerExtractorAPI(NinjaHD())
    }
}
