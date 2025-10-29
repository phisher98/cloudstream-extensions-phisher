import org.jetbrains.kotlin.konan.properties.Properties

// use an integer for version numbers
version = 1


android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    defaultConfig {
        val properties = Properties()
        properties.load(project.rootProject.file("local.properties").inputStream())
        android.buildFeatures.buildConfig=true
        buildConfigField("String", "YFXENC", "\"${properties.getProperty("YFXENC")}\"")
        buildConfigField("String", "YFXDEC", "\"${properties.getProperty("YFXDEC")}\"")
        buildConfigField("String", "KAIMEG", "\"${properties.getProperty("KAIMEG")}\"")
    }
}


cloudstream {
    language = "en"
    // All of these properties are optional, you can safely remove them
    description = "Movies & TV Series Etc"
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

    iconUrl = "https://www.google.com/s2/favicons?domain=yflix.to/&sz=%size%"

    requiresResources = true
    isCrossPlatform = false
}