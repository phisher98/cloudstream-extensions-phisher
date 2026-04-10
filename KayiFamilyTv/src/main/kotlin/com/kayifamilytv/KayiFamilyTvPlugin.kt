package com.kayifamilytv

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class KayiFamilyTvPlugin: Plugin() {
    override fun load(context: Context) {
        // Registers the main API for this plugin
        registerMainAPI(KayiFamilyTv())
        registerExtractorAPI(Videa())
        registerExtractorAPI(FirePlayerX())
    }
}