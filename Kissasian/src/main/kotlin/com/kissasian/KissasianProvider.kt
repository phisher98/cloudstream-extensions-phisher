package com.kissasian

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.extractors.MixDrop
import com.lagradost.cloudstream3.extractors.StreamTape

@CloudstreamPlugin
class MydesiProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Kissasian())
        registerMainAPI(Dramacool())
        registerExtractorAPI(embedwish())
        registerExtractorAPI(dwish())
        registerExtractorAPI(StreamTape())
        registerExtractorAPI(MixDrop())
    }
}