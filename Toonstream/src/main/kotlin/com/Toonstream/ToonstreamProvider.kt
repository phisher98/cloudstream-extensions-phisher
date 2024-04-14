package com.Toonstream

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class ToonstreamProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Toonstream())
        registerExtractorAPI(StreamSB8())
        registerExtractorAPI(Vidstreaming())
    }
}