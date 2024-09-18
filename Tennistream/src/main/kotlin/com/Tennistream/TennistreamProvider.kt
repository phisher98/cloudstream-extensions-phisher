package com.Tennistream

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.extractors.Vidmolyme

@CloudstreamPlugin
class CloudyProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Tennistream())
        registerExtractorAPI(Quest4play())
        registerExtractorAPI(Vaguedinosaurs())
    }
}
