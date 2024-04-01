package com.AsianHD

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.extractors.MixDrop
import com.lagradost.cloudstream3.extractors.StreamTape

@CloudstreamPlugin
class AsianHDProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(DramacoolProvider())
        registerExtractorAPI(embedwish())
        registerExtractorAPI(dwish())
        registerExtractorAPI(StreamTape())
        registerExtractorAPI(MixDrop())
    }
}