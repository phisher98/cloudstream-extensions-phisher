import org.jetbrains.kotlin.konan.properties.Properties

version = 9

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
    }
}

cloudstream {
    language = "en"

     description = "[!] Requires Setup \n- Allows you to use any Stremio addon by pasting their manifest.json url"
     authors = listOf("Hexated,phisher98,erynith")

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
        "Torrent"
    )
    requiresResources = true
    iconUrl = "https://files.catbox.moe/ol63rm.png"
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    val cloudstream by configurations
    cloudstream("com.lagradost:cloudstream3:pre-release")
}
