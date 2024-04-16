package com.javdoe

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class JavdoeProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Javdoe())
        //registerMainAPI(Jable())
        registerMainAPI(Javguru())
        registerExtractorAPI(DoodJav())
        registerExtractorAPI(javclan())
        registerExtractorAPI(Maxstream())
        registerExtractorAPI(Ds2Play())
        registerExtractorAPI(Streamwish())
        registerExtractorAPI(Vidhidepro())
    }
}