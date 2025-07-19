package com.phisher98

import android.content.Context
import androidx.appcompat.app.AppCompatActivity
import com.Phisher98.settings.MainSettingsFragment
import com.lagradost.cloudstream3.MainAPI
import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.extractors.DoodYtExtractor
import com.lagradost.cloudstream3.extractors.FileMoon
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
import com.lagradost.cloudstream3.extractors.VidStack
import com.lagradost.cloudstream3.extractors.Vidmolyme
import com.lagradost.cloudstream3.extractors.Vidplay
import com.lagradost.cloudstream3.extractors.Voe
import com.lagradost.cloudstream3.plugins.Plugin
import androidx.core.content.edit
import com.lagradost.api.Log

@CloudstreamPlugin
class StreamPlayPlugin: Plugin() {
    private val registeredMainApis = mutableListOf<MainAPI>()

    override fun load(context: Context) {

        Log.d("StreamPlay", "Plugin loading with context: $context")
        val sharedPref = context.getSharedPreferences("StreamPlay", Context.MODE_PRIVATE)
        val mainApis = listOf(
            StreamPlay(sharedPref), StreamPlayLite(),
            StreamPlayTorrent(), StreamPlayAnime(), StreamplayTorrentAnime()
        )
        val savedSet = sharedPref.getStringSet("enabled_plugins_saved", null)
        val defaultEnabled = mainApis.map { it.name }.toSet()
        val enabledSet = savedSet ?: defaultEnabled

        Log.d("StreamPlay", "SavedSet: $savedSet, DefaultEnabled: $defaultEnabled")
        Log.d("StreamPlay", "Final enabled set: $enabledSet")

        for (api in mainApis) {
            if (enabledSet.contains(api.name)) {
                registerMainAPI(api)
                registeredMainApis.add(api)
                Log.d("StreamPlay", "Registered plugin: ${api.name}")
            } else {
                Log.d("StreamPlay", "Not enabled: ${api.name}")
            }
        }

        sharedPref.edit { remove("enabled_plugins_set") }

        //=====================Settings============================//
/*
        val sharedPref = context.getSharedPreferences("StreamPlay", Context.MODE_PRIVATE)
        val mainApis = listOf(
            StreamPlay(sharedPref),
            StreamPlayLite(),
            StreamPlayTorrent(),
            StreamPlayAnime(),
            StreamplayTorrentAnime()
        )
        val savedSet = sharedPref.getStringSet("enabled_plugins_saved", null)
        val defaultEnabled = mainApis.map { it.name }.toSet()
        val enabledSet = savedSet ?: defaultEnabled

        for (api in mainApis) {
            if (enabledSet.contains(api.name)) {
                registerMainAPI(api)
                registeredMainApis.add(api)
            }
        }
        sharedPref.edit { remove("enabled_plugins_set") }
 */

        //=====================MainAPI============================//

        //registerMainAPI(StreamPlayTest(sharedPref))

        //=====================Extractors=========================//

        registerExtractorAPI(Animefever())
        registerExtractorAPI(Multimovies())
        registerExtractorAPI(MultimoviesSB())
        registerExtractorAPI(Yipsu())
        registerExtractorAPI(Mwish())
        registerExtractorAPI(TravelR())
        registerExtractorAPI(Playm4u())
        registerExtractorAPI(Vidplay())
        registerExtractorAPI(FileMoon())
        registerExtractorAPI(VCloud())
        registerExtractorAPI(Kwik())
        registerExtractorAPI(VCloudGDirect())
        registerExtractorAPI(Filelions())
        registerExtractorAPI(Snolaxstream())
        registerExtractorAPI(Pixeldra())
        registerExtractorAPI(Mp4Upload())
        registerExtractorAPI(Graceaddresscommunity())
        registerExtractorAPI(M4ufree())
        registerExtractorAPI(Streamruby())
        registerExtractorAPI(StreamWishExtractor())
        registerExtractorAPI(Filelion())
        registerExtractorAPI(DoodYtExtractor())
        registerExtractorAPI(dlions())
        registerExtractorAPI(MixDrop())
        registerExtractorAPI(dwish())
        registerExtractorAPI(Embedwish())
        registerExtractorAPI(UqloadsXyz())
        registerExtractorAPI(Uploadever())
        registerExtractorAPI(Netembed())
        registerExtractorAPI(Flaswish())
        registerExtractorAPI(Comedyshow())
        registerExtractorAPI(Ridoo())
        registerExtractorAPI(Streamvid())
        registerExtractorAPI(StreamTape())
        registerExtractorAPI(do0od())
        registerExtractorAPI(doodre())
        registerExtractorAPI(Embedrise())
        registerExtractorAPI(GDMirrorbot())
        registerExtractorAPI(FilemoonNl())
        registerExtractorAPI(Alions())
        registerExtractorAPI(Vidmolyme())
        registerExtractorAPI(AllinoneDownloader())
        registerExtractorAPI(Tellygossips())
        registerExtractorAPI(Tvlogy())
        registerExtractorAPI(Voe())
        registerExtractorAPI(Gofile())
        registerExtractorAPI(Animezia())
        registerExtractorAPI(PixelDrain())
        registerExtractorAPI(Modflix())
        registerExtractorAPI(Sethniceletter())
        registerExtractorAPI(GDFlix())
        registerExtractorAPI(fastdlserver())
        registerExtractorAPI(GDFlix1())
        registerExtractorAPI(GDFlix2())
        registerExtractorAPI(furher())
        registerExtractorAPI(VidSrcExtractor())
        registerExtractorAPI(Servertwo())
        registerExtractorAPI(MultimoviesAIO())
        registerExtractorAPI(HubCloud())
        registerExtractorAPI(Driveseed())
        registerExtractorAPI(Driveleech())
        registerExtractorAPI(VidHidePro6())
        registerExtractorAPI(MixDropSi())
        registerExtractorAPI(MixDropPs())
        registerExtractorAPI(Mp4Upload())
        registerExtractorAPI(Streamlare())
        registerExtractorAPI(StreamSB8())
        registerExtractorAPI(StreamSB())
        registerExtractorAPI(OkRuSSL())
        registerExtractorAPI(OkRuHTTP())
        registerExtractorAPI(Embtaku())
        registerExtractorAPI(bulbasaur())
        registerExtractorAPI(GDMirrorbot())
        registerExtractorAPI(Megacloud())
        registerExtractorAPI(Cdnstreame())
        registerExtractorAPI(OwlExtractor())
        registerExtractorAPI(Rapidplayers())
        registerExtractorAPI(Maxfinishseveral())
        registerExtractorAPI(Pahe())
        registerExtractorAPI(MegaUp())
        registerExtractorAPI(oxxxfile())
        registerExtractorAPI(Hblinks())
        registerExtractorAPI(VidStack())
        registerExtractorAPI(Videostr())
        registerExtractorAPI(DriveleechPro())
        registerExtractorAPI(DriveleechNet())
        registerExtractorAPI(Molop())
        registerExtractorAPI(showflixupnshare())
        registerExtractorAPI(Embedwish())
        registerExtractorAPI(Rubyvidhub())
        registerExtractorAPI(smoothpre())
        registerExtractorAPI(Akirabox())
        registerExtractorAPI(BuzzServer())
        val activity = context as AppCompatActivity
        openSettings = {
            val frag = MainSettingsFragment(this, sharedPref)
            frag.show(activity.supportFragmentManager, "Frag")
        }
    }
}