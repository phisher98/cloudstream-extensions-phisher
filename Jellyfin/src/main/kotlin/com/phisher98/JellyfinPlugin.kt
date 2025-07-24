package com.phisher98

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.phisher98.settings.SettingsFragment
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.extractors.DoodYtExtractor
import com.lagradost.cloudstream3.extractors.FileMoon
import com.lagradost.cloudstream3.extractors.Gofile
import com.lagradost.cloudstream3.extractors.MixDrop
import com.lagradost.cloudstream3.extractors.Mp4Upload
import com.lagradost.cloudstream3.extractors.OkRuHTTP
import com.lagradost.cloudstream3.extractors.OkRuSSL
import com.lagradost.cloudstream3.extractors.StreamSB
import com.lagradost.cloudstream3.extractors.StreamSB8
import com.lagradost.cloudstream3.extractors.StreamTape
import com.lagradost.cloudstream3.extractors.StreamWishExtractor
import com.lagradost.cloudstream3.extractors.Streamlare
import com.lagradost.cloudstream3.extractors.VidHidePro6
import com.lagradost.cloudstream3.extractors.VidSrcExtractor
import com.lagradost.cloudstream3.extractors.Vidmolyme
import com.lagradost.cloudstream3.extractors.Vidplay
import com.lagradost.cloudstream3.extractors.Voe
import com.lagradost.cloudstream3.plugins.Plugin

@CloudstreamPlugin
class JellyfinPlugin: Plugin() {
    override fun load(context: Context) {
        val sharedPref = context.getSharedPreferences("SuperStream", Context.MODE_PRIVATE)
        registerMainAPI(Jellyfin(sharedPref))
        val activity = context as AppCompatActivity
        openSettings = {
            val frag = SettingsFragment(this, sharedPref)
            frag.show(activity.supportFragmentManager, "SettingsFragment")
        }
    }
}