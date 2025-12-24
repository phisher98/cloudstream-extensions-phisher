// use an integer for version numbers
version = 1

android {
    compileSdk = 35
    namespace = "com.tokusatsu.ultimate"

    defaultConfig {
        minSdk = 21
        targetSdk = 35
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_1_8
        targetCompatibility = JavaVersion.VERSION_1_8
    }
}

cloudstream {
    language = "en"
    // All of these properties are optional, you can safely remove them

    description = "Stream tokusatsu content including Kamen Rider, Super Sentai, Metal Heroes, and other Japanese special effect series with English subs"
    authors = listOf("YoureMotherHelped-Love-Mom")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "TvSeries",
        "Movie",
        "Anime"
    )

    iconUrl = "https://raw.githubusercontent.com/YoureMotherHelped-Love-Mom/StreamCloud_Plug_in/main/TokusatsuUltimateIcon.png"
}