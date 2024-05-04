package com.BanglaPlex

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class BanglaPlexProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Banglaplex())
        registerExtractorAPI(Vectorx())
        registerExtractorAPI(Chillx())
    }
}