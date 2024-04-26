package com.Anplay

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.extractors.Vidmolyme

@CloudstreamPlugin
class AnplayProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Anplay())
        registerExtractorAPI(AnimesagaStream())
    }
}