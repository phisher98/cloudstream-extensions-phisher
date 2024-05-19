package com.Wapking

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
@CloudstreamPlugin
class WapkingProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(Wapking())
    }
}