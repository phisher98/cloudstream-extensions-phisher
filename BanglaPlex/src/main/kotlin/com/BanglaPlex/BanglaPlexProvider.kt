package com.BanglaPlex

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.extractors.Chillx

@CloudstreamPlugin
class BanglaPlexProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Banglaplex())
        registerExtractorAPI(Chillx())
    }
}