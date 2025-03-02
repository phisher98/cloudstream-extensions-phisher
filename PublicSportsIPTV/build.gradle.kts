@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.konan.properties.Properties

version = 1

android {
    defaultConfig {
        val properties = Properties()
        properties.load(project.rootProject.file("local.properties").inputStream())
        android.buildFeatures.buildConfig=true
        buildConfigField("String", "FanCode_API", "\"${properties.getProperty("FanCode_API")}\"")

    }
}

cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "Sports Live Streams"
    language    = "en"
    authors = listOf("Phisher98")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1 // will be 3 if unspecified

    // List of video source types. Users are able to filter for extensions in a given category.
    // You can find a list of available types here:
    // https://recloudstream.github.io/cloudstream/html/app/com.lagradost.cloudstream3/-tv-type/index.html
    tvTypes = listOf("Live")

    iconUrl="https://www.thestatesman.com/wp-content/uploads/2021/05/fancode.jpg"

    isCrossPlatform = true
}
