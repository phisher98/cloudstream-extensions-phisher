package com.Toonstream

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.lagradost.cloudstream3.extractors.Vidmolyme

@CloudstreamPlugin
class ToonstreamProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Toonstream())
        registerExtractorAPI(StreamSB8())
        registerExtractorAPI(Vidmolyme())
        registerExtractorAPI(Vidstreaming())
        registerExtractorAPI(Streamruby())
        registerExtractorAPI(D000d())
        registerExtractorAPI(vidhidevip())
        registerExtractorAPI(Cdnwish())
        registerExtractorAPI(FileMoonnl())
    }
}