package com.AnimeKai

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.AcraApplication.Companion.getActivity
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

enum class ServerList(val link: Pair<String, Boolean>) {
    BEST("https://animekai.to" to true),
    BZ("https://animekai.bz" to true),
    CC("https://animekai.cc" to true),
    AC("https://animekai.ac" to true),
}

@CloudstreamPlugin
class AnimeKaiPlugin : Plugin() {

    override fun load(context: Context) {
        registerMainAPI(AnimeKai())
        registerExtractorAPI(MegaUp())

        this.openSettings = openSettings@{
            val manager =
                (context.getActivity() as? AppCompatActivity)?.supportFragmentManager
                    ?: return@openSettings
            BottomFragment(this).show(manager, "")
        }
    }

    companion object {
        var currentAnimeKaiServer: String
            get() = getKey("ANIMEKAI_CURRENT_SERVER") ?: ServerList.BEST.link.first
            set(value) {
                setKey("ANIMEKAI_CURRENT_SERVER", value)
            }
    }
}

