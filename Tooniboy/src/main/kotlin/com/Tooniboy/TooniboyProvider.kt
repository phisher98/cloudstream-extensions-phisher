package com.Tooniboy

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.extractors.Vidmolyme

@CloudstreamPlugin
class TooniboyProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Tooniboy())
        registerExtractorAPI(StreamSB8())
        registerExtractorAPI(Vidmolyme())
        registerExtractorAPI(StreamRuby())
        registerExtractorAPI(Vidstreamxyz())
        registerExtractorAPI(D000d())
        registerExtractorAPI(vidhidevip())
        registerExtractorAPI(Cdnwish())
        registerExtractorAPI(FileMoonnl())
        registerExtractorAPI(GDMirrorbot())
    }
}