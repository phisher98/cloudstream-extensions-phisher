package com.javdoe

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class JavdoeProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Javdoe())
        registerMainAPI(Jable())
        registerExtractorAPI(DoodJav())
        registerExtractorAPI(Streamwish())
        registerExtractorAPI(Vidhidepro())
    }
}