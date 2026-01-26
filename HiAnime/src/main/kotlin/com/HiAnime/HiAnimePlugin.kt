package com.HiAnime

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.lagradost.cloudstream3.AcraApplication.Companion.getKey
import com.lagradost.cloudstream3.AcraApplication.Companion.setKey
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin

enum class ServerList(val link: Pair<String, Boolean>) {
    HIANIMEZ_IS("https://hianimez.is" to true),
    BEST("https://hianime.to" to true),
    HIANIME_NZ("https://hianime.nz" to true),
    HIANIME_BZ("https://hianime.bz" to true),
    HIANIME_PE("https://hianime.pe" to true),
    HIANIME_CX("https://hianime.cx" to true),
    HIANIME_DO("https://hianime.do" to true),
}

@CloudstreamPlugin
class HiAnimeProviderPlugin : Plugin() {

    override fun load(context: Context) {
        registerMainAPI(HiAnime())
        registerExtractorAPI(Megacloud())
        this.openSettings = openSettings@{ ctx ->
            val activity = ctx as AppCompatActivity
            val frag = BottomFragment(this)
            frag.show(activity.supportFragmentManager, "")
        }
    }

    companion object {
        var currentHiAnimeServer: String
            get() = getKey("HIANIME_CURRENT_SERVER") ?: ServerList.BEST.link.first
            set(value) {
                setKey("HIANIME_CURRENT_SERVER", value)
            }
    }
}
