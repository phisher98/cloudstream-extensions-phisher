@file:Suppress("UnstableApiUsage")

import org.jetbrains.kotlin.konan.properties.Properties

// use an integer for version numbers
version = 22


android {
    defaultConfig {
        val properties = Properties()
        properties.load(project.rootProject.file("local.properties").inputStream())
        buildConfigField("String", "INDIANTV_TATA_API", "\"${properties.getProperty("INDIANTV_TATA_API")}\"")
        buildConfigField("String", "INDIANTV_JIO_API", "\"${properties.getProperty("INDIANTV_JIO_API")}\"")
        buildConfigField("String", "INDIANTV_Discovery_API", "\"${properties.getProperty("INDIANTV_Discovery_API")}\"")
        buildConfigField("String", "INDIANTV_Airtel_API", "\"${properties.getProperty("INDIANTV_Airtel_API")}\"")
    }
}


cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "Indian TV"
    authors = listOf("HindiProviders,King,Lag,Rowdy")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1 // will be 3 if unspecified

    // List of video source types. Users are able to filter for extensions in a given category.
    // You can find a list of avaliable types here:
    // https://recloudstream.github.io/cloudstream/html/app/com.lagradost.cloudstream3/-tv-type/index.html
    tvTypes = listOf(
                "Live",
)
iconUrl = "https://www.freepnglogos.com/uploads/tv-png/tv-png-box-television-set-cable-screen-icon-31.png"
}
