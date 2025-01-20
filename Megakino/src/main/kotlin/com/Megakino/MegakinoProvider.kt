package com.Megakino

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.extractors.Voe

@CloudstreamPlugin
class MegakinoProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Megakino())
        registerExtractorAPI(Voe())
        registerExtractorAPI(Gxplayer())
    }
}