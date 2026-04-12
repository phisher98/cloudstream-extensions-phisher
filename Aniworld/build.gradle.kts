// use an integer for version numbers
version = 9

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
}

android {
    buildFeatures {
        buildConfig = true
    }
}

cloudstream {
    language = "de"
    // All of these properties are optional, you can safely remove them

    description = "Include: Serienstream"
    authors = listOf("Phisher98,Hexated")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "AnimeMovie",
        "Anime",
        "OVA",
    )
    requiresResources = true
    isCrossPlatform = false
    iconUrl = "https://www.google.com/s2/favicons?domain=aniworld.to&sz=%size%"
}

