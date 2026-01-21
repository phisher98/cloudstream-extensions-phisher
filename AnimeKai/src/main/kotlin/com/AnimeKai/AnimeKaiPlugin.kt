package com.AnimeKai

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

enum class ServerList(val link: Pair<String, Boolean>) {
    IM("https://animekai.im" to true),
    IN("https://animekai.in" to true),
    LA("https://animekai.la" to true),
    NL("https://animekai.nl" to true),
    VC("https://animekai.vc" to true),
    BEST("https://anikai.to" to true)
}


@CloudstreamPlugin
class AnimeKaiPlugin : Plugin() {

    override fun load(context: Context) {
        registerMainAPI(AnimeKai())
        registerExtractorAPI(MegaUp())
        registerExtractorAPI(Fourspromax())
        registerExtractorAPI(MegaUpTwoTwo())

        this.openSettings = { ctx ->
            val activity = ctx as AppCompatActivity
            val frag = BottomFragment(this)
            frag.show(activity.supportFragmentManager, "")
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

