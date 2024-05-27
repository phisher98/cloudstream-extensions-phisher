package com.KimCartoon

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.extractors.Vidmoly

@CloudstreamPlugin
class KimCartoonPlugin: Plugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner
        registerMainAPI(KimCartoon())
        registerExtractorAPI(Bembed())
        registerExtractorAPI(Vidmoly())
        registerExtractorAPI(Listeamed())
        registerExtractorAPI(streamwish())
    }
}