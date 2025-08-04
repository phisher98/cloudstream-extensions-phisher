import org.jetbrains.kotlin.konan.properties.Properties

// use an integer for version numbers
version = 28


android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    defaultConfig {
        val properties = Properties()
        properties.load(project.rootProject.file("local.properties").inputStream())
        android.buildFeatures.buildConfig=true
        buildConfigField("String", "KAISVA", "\"${properties.getProperty("KAISVA")}\"")
    }
}

cloudstream {
    language = "en"
    // All of these properties are optional, you can safely remove them
    description = "Animes & Animes Movie"
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
        "Anime",
        "OVA",
        "AnimeMovie"
    )

    iconUrl = "https://www.google.com/s2/favicons?domain=animekai.to&sz=%size%"

    requiresResources = true
    isCrossPlatform = false
}