package com.HindiProvider


import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class Cricket_StreamsProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Cricket_Streams())
    }
}