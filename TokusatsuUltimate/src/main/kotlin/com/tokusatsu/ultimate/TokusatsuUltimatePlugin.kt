package com.tokusatsu.ultimate

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class TokusatsuUltimatePlugin: Plugin() {
    override fun load(context: Context) {
        // Registers the main API for this plugin
        registerMainAPI(TokusatsuUltimate())
        registerExtractorAPI(TokusatsuUltimate.P2pplay())
    }
}