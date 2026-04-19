@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.konan.properties.Properties

version = 618

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    defaultConfig {
        val properties = Properties()
        properties.load(project.rootProject.file("local.properties").inputStream())
        android.buildFeatures.buildConfig=true
        buildConfigField("String", "TMDB_API", "\"${properties.getProperty("TMDB_API")}\"")
        buildConfigField("String", "ZSHOW_API", "\"${properties.getProperty("ZSHOW_API")}\"")
        buildConfigField("String", "ANICHI_API", "\"${properties.getProperty("ANICHI_API")}\"")
        buildConfigField("String", "KissKh", "\"${properties.getProperty("KissKh")}\"")
        buildConfigField("String", "KisskhSub", "\"${properties.getProperty("KisskhSub")}\"")
        buildConfigField("String", "SUPERSTREAM_THIRD_API", "\"${properties.getProperty("SUPERSTREAM_THIRD_API")}\"")
        buildConfigField("String", "SUPERSTREAM_FOURTH_API", "\"${properties.getProperty("SUPERSTREAM_FOURTH_API")}\"")
        buildConfigField("String", "SUPERSTREAM_FIRST_API", "\"${properties.getProperty("SUPERSTREAM_FIRST_API")}\"")
        buildConfigField("String", "PROXYAPI", "\"${properties.getProperty("PROXYAPI")}\"")
        buildConfigField("String", "KAISVA", "\"${properties.getProperty("KAISVA")}\"")
        buildConfigField("String", "MOVIEBOX_SECRET_KEY_ALT", "\"${properties.getProperty("MOVIEBOX_SECRET_KEY_ALT")}\"")
        buildConfigField("String", "MOVIEBOX_SECRET_KEY_DEFAULT", "\"${properties.getProperty("MOVIEBOX_SECRET_KEY_DEFAULT")}\"")
        buildConfigField("String", "KAIMEG", "\"${properties.getProperty("KAIMEG")}\"")
        buildConfigField("String", "KAIDEC", "\"${properties.getProperty("KAIDEC")}\"")
        buildConfigField("String", "KAIENC", "\"${properties.getProperty("KAIENC")}\"")
        buildConfigField("String", "Nuviostreams", "\"${properties.getProperty("Nuviostreams")}\"")
        buildConfigField("String", "VideasyDEC", "\"${properties.getProperty("VideasyDEC")}\"")
        buildConfigField("String", "YFXENC", "\"${properties.getProperty("YFXENC")}\"")
        buildConfigField("String", "YFXDEC", "\"${properties.getProperty("YFXDEC")}\"")
        buildConfigField("String", "NuvFeb", "\"${properties.getProperty("NuvFeb")}\"")
        buildConfigField("String", "ANICHI_APP", "\"${properties.getProperty("ANICHI_APP")}\"")
    }
}

dependencies {
    implementation("com.google.android.material:material:1.13.0")
}

cloudstream {
    language = "en"
    // All of these properties are optional, you can safely remove them

     description = "#1 best extention based on MultiAPI"
     authors = listOf("Phisher98", "Hexated")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "AsianDrama",
        "TvSeries",
        "Anime",
        "Movie",
        "Cartoon",
        "AnimeMovie"
    )

    iconUrl = "https://i3.wp.com/yt3.googleusercontent.com/ytc/AIdro_nCBArSmvOc6o-k2hTYpLtQMPrKqGtAw_nC20rxm70akA=s900-c-k-c0x00ffffff-no-rj?ssl=1"

    requiresResources = true
    isCrossPlatform = false

}

dependencies {
    // FIXME remove this when crossplatform is fully supported
    val cloudstream by configurations
    implementation("com.google.android.material:material:1.13.0")
    implementation("androidx.browser:browser:1.10.0")
    cloudstream("com.lagradost:cloudstream3:pre-release")
}
