package com.HindiProvider


import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context

@CloudstreamPlugin
class IPLProvider: Plugin() {
    override fun load(context: Context) {
        registerMainAPI(IPL())
    }
}