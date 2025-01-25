package com.BanglaPlex

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.extractors.StreamTape

@CloudstreamPlugin
class BanglaPlexProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Banglaplex())
        registerExtractorAPI(Vectorx())
        registerExtractorAPI(Boosterx())
        registerExtractorAPI(Iplayerhls())
        registerExtractorAPI(StreamTape()) }
}