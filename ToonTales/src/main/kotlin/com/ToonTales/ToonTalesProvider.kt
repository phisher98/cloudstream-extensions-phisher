package com.ToonTales

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class ToonTalesProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(ToonTales())
    }
}