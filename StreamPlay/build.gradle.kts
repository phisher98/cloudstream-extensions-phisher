@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.konan.properties.Properties

// use an integer for version numbers
version = 93
android {
    defaultConfig {
        val properties = Properties()
        properties.load(project.rootProject.file("local.properties").inputStream())
        buildConfigField("String", "TMDB_API", "\"${properties.getProperty("TMDB_API")}\"")
        buildConfigField("String", "GHOSTX_API", "\"${properties.getProperty("GHOSTX_API")}\"")
        buildConfigField("String", "CINEMATV_API", "\"${properties.getProperty("CINEMATV_API")}\"")
        buildConfigField("String", "SFMOVIES_API", "\"${properties.getProperty("SFMOVIES_API")}\"")
        buildConfigField("String", "ZSHOW_API", "\"${properties.getProperty("ZSHOW_API")}\"")
        buildConfigField("String", "DUMP_API", "\"${properties.getProperty("DUMP_API")}\"")
        buildConfigField("String", "DUMP_KEY", "\"${properties.getProperty("DUMP_KEY")}\"")
        buildConfigField("String", "CRUNCHYROLL_BASIC_TOKEN", "\"${properties.getProperty("CRUNCHYROLL_BASIC_TOKEN")}\"")
        buildConfigField("String", "CRUNCHYROLL_REFRESH_TOKEN", "\"${properties.getProperty("CRUNCHYROLL_REFRESH_TOKEN")}\"")
        buildConfigField("String", "MOVIE_API", "\"${properties.getProperty("MOVIE_API")}\"")
        buildConfigField("String", "MultiMovies_API", "\"${properties.getProperty("MultiMovies_API")}\"")
        buildConfigField("String", "MovieDrive_API", "\"${properties.getProperty("MovieDrive_API")}\"")
        buildConfigField("String", "AsianDrama_API", "\"${properties.getProperty("AsianDrama_API")}\"")
        buildConfigField("String", "ANICHI_API", "\"${properties.getProperty("ANICHI_API")}\"")
        buildConfigField("String", "Whvx_API", "\"${properties.getProperty("Whvx_API")}\"")
        buildConfigField("String", "CatflixAPI", "\"${properties.getProperty("CatflixAPI")}\"")
        buildConfigField("String", "ConsumetAPI", "\"${properties.getProperty("ConsumetAPI")}\"")
        buildConfigField("String", "FlixAPI", "\"${properties.getProperty("FlixAPI")}\"")
        buildConfigField("String", "WhvxAPI", "\"${properties.getProperty("WhvxAPI")}\"")
        buildConfigField("String", "WhvxT", "\"${properties.getProperty("WhvxT")}\"")
        buildConfigField("String", "SharmaflixApikey", "\"${properties.getProperty("SharmaflixApikey")}\"")
        buildConfigField("String", "SharmaflixApi", "\"${properties.getProperty("SharmaflixApi")}\"")

    }
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
    )

    iconUrl = "https://i3.wp.com/yt3.googleusercontent.com/ytc/AIdro_nCBArSmvOc6o-k2hTYpLtQMPrKqGtAw_nC20rxm70akA=s900-c-k-c0x00ffffff-no-rj?ssl=1"
}
