import com.lagradost.cloudstream3.plugins.CloudstreamPlugin
import com.lagradost.cloudstream3.plugins.Plugin
import android.content.Context
import com.IndianTV.IndianTVPlugin

@CloudstreamPlugin
class IndianTVProvider: Plugin() {
    override fun load(context: Context) {
        // All providers should be added in this manner. Please don't edit the providers list directly.
        registerMainAPI(IndianTVPlugin())
    }
}



