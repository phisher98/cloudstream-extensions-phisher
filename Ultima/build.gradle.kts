@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.konan.properties.Properties

dependencies {
    implementation("com.google.android.material:material:1.12.0")

    // FIXME remove this when crossplatform is fully supported
    val cloudstream by configurations
    cloudstream("com.lagradost:cloudstream3:pre-release")
}
// use an integer for version numbers
version = 14

cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "The ultimate All-in-One home screen to access all of your extensions at one place (You need to select/deselect sections in Ultima's settings to load other extensions on home screen)"
    authors = listOf("RowdyRushya,Phisher98")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 2

    tvTypes = listOf("All")

    requiresResources = true
    language = "en"

    // random cc logo i found
    iconUrl = "https://raw.githubusercontent.com/phisher98/TVVVV/refs/heads/main/Icons/Ultima.jpg"

    isCrossPlatform = false
}

android {
    buildFeatures {
        buildConfig = true
    }

    defaultConfig {
        val properties = Properties()
        properties.load(project.rootProject.file("local.properties").inputStream())
        buildConfigField("String", "KAISVA", "\"${properties.getProperty("KAISVA")}\"")
        buildConfigField("String", "SIMKL_API", "\"${properties.getProperty("SIMKL_API")}\"")
        buildConfigField("String", "MAL_API", "\"${properties.getProperty("MAL_API")}\"")
    }
}