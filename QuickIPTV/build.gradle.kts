@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.konan.properties.Properties
// use an integer for version numbers
version = 5

android {
    defaultConfig {
        val properties = Properties()
        properties.load(project.rootProject.file("local.properties").inputStream())
        android.buildFeatures.buildConfig=true
        buildConfigField("String", "Su_sports", "\"${properties.getProperty("Su_sports")}\"")
        buildConfigField("String", "PirateIPTV", "\"${properties.getProperty("PirateIPTV")}\"")
        buildConfigField("String", "SonyIPTV", "\"${properties.getProperty("SonyIPTV")}\"")
        buildConfigField("String", "JapanIPTV", "\"${properties.getProperty("JapanIPTV")}\"")
    }
}

cloudstream {
    // All of these properties are optional, you can safely remove them
    language = "en"
    description = "Includes PirateIPTV,Sports IPTV,Japanese IPTV,Sony IPTV"
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
        "Live",
    )

    iconUrl = "https://www.google.com/s2/favicons?domain=github.com&sz=%size%"

    isCrossPlatform = true
}
