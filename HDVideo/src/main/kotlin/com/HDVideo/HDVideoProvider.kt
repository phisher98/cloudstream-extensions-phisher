package com.HDVideo

import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class HDVideoProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(HDVideo())
    }
}