@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.konan.properties.Properties

version = 267
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
        buildConfigField("String", "CINEMATV_API", "\"${properties.getProperty("CINEMATV_API")}\"")
        buildConfigField("String", "SFMOVIES_API", "\"${properties.getProperty("SFMOVIES_API")}\"")
        buildConfigField("String", "ZSHOW_API", "\"${properties.getProperty("ZSHOW_API")}\"")
        buildConfigField("String", "DUMP_API", "\"${properties.getProperty("DUMP_API")}\"")
        buildConfigField("String", "DUMP_KEY", "\"${properties.getProperty("DUMP_KEY")}\"")
        buildConfigField("String", "CRUNCHYROLL_BASIC_TOKEN", "\"${properties.getProperty("CRUNCHYROLL_BASIC_TOKEN")}\"")
        buildConfigField("String", "CRUNCHYROLL_REFRESH_TOKEN", "\"${properties.getProperty("CRUNCHYROLL_REFRESH_TOKEN")}\"")
        buildConfigField("String", "MOVIE_API", "\"${properties.getProperty("MOVIE_API")}\"")
        buildConfigField("String", "ANICHI_API", "\"${properties.getProperty("ANICHI_API")}\"")
        buildConfigField("String", "Whvx_API", "\"${properties.getProperty("Whvx_API")}\"")
        buildConfigField("String", "CatflixAPI", "\"${properties.getProperty("CatflixAPI")}\"")
        buildConfigField("String", "ConsumetAPI", "\"${properties.getProperty("ConsumetAPI")}\"")
        buildConfigField("String", "FlixHQAPI", "\"${properties.getProperty("FlixHQAPI")}\"")
        buildConfigField("String", "WhvxAPI", "\"${properties.getProperty("WhvxAPI")}\"")
        buildConfigField("String", "WhvxT", "\"${properties.getProperty("WhvxT")}\"")
        buildConfigField("String", "SharmaflixApikey", "\"${properties.getProperty("SharmaflixApikey")}\"")
        buildConfigField("String", "SharmaflixApi", "\"${properties.getProperty("SharmaflixApi")}\"")
        buildConfigField("String", "Theyallsayflix", "\"${properties.getProperty("Theyallsayflix")}\"")
        buildConfigField("String", "GojoAPI", "\"${properties.getProperty("GojoAPI")}\"")
        buildConfigField("String", "HianimeAPI", "\"${properties.getProperty("HianimeAPI")}\"")
        buildConfigField("String", "Vidsrccc", "\"${properties.getProperty("Vidsrccc")}\"")
        buildConfigField("String", "WASMAPI", "\"${properties.getProperty("WASMAPI")}\"")
        buildConfigField("String", "KissKh", "\"${properties.getProperty("KissKh")}\"")
        buildConfigField("String", "KisskhSub", "\"${properties.getProperty("KisskhSub")}\"")
        buildConfigField("String", "SUPERSTREAM_THIRD_API", "\"${properties.getProperty("SUPERSTREAM_THIRD_API")}\"")
        buildConfigField("String", "SUPERSTREAM_FOURTH_API", "\"${properties.getProperty("SUPERSTREAM_FOURTH_API")}\"")
        buildConfigField("String", "SUPERSTREAM_FIRST_API", "\"${properties.getProperty("SUPERSTREAM_FIRST_API")}\"")
        buildConfigField("String", "StreamPlayAPI", "\"${properties.getProperty("StreamPlayAPI")}\"")
        buildConfigField("String", "PROXYAPI", "\"${properties.getProperty("PROXYAPI")}\"")
    }
}

cloudstream {
    language = "en"
    // All of these properties are optional, you can safely remove them

     description = "#1 best extention based on MultiAPI"
     authors = listOf("Phisher98", "Hexated","salman731")

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
    implementation("com.google.android.material:material:1.12.0")
    implementation("androidx.browser:browser:1.8.0")
    cloudstream("com.lagradost:cloudstream3:pre-release")
}
