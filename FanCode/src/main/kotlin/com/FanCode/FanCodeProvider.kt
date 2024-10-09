package com.FanCode

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class FanCodeProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(FanCode())
    }
}
