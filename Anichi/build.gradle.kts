import org.jetbrains.kotlin.konan.properties.Properties

// use an integer for version numbers
version = 4

android {
    buildFeatures {
        buildConfig = true
    }
    defaultConfig {
        val properties = Properties()
        properties.load(project.rootProject.file("local.properties").inputStream())

        buildConfigField("String", "ANICHI_API", "\"${properties.getProperty("ANICHI_API")}\"")
        buildConfigField("String", "ANICHI_SERVER", "\"${properties.getProperty("ANICHI_SERVER")}\"")
        buildConfigField("String", "ANICHI_ENDPOINT", "\"${properties.getProperty("ANICHI_ENDPOINT")}\"")
        buildConfigField("String", "ANICHI_APP", "\"${properties.getProperty("ANICHI_APP")}\"")
    }
}

cloudstream {
    language = "en"
    // All of these properties are optional, you can safely remove them

    // description = "Lorem Ipsum"
     authors = listOf("Hexated,Phisher98")

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
    iconUrl = "https://raw.githubusercontent.com/phisher98/TVVVV/refs/heads/main/Icons/Allanime.png"

    isCrossPlatform = true
}