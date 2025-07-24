@file:Suppress("UnstableApiUsage")

version = 3

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
}

cloudstream {
    language = "en"
    // All of these properties are optional, you can safely remove them
     description = "Jellyfin is a Free Software Media System that puts you in control of managing and streaming your media"
     authors = listOf("Phisher98")

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

    iconUrl = "https://raw.githubusercontent.com/phisher98/TVVVV/refs/heads/main/Icons/jellyfin.png"

    requiresResources = true
    isCrossPlatform = false

}

dependencies {
    // FIXME remove this when crossplatform is fully supported
    val cloudstream by configurations
    implementation("com.google.android.material:material:1.12.0")
    cloudstream("com.lagradost:cloudstream3:pre-release")
}
