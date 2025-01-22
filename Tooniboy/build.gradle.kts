@file:Suppress("UnstableApiUsage")
import org.jetbrains.kotlin.konan.properties.Properties

// use an integer for version numbers
version = 3


android.buildFeatures.buildConfig = true


android {
    defaultConfig {
        val properties = Properties()
        properties.load(project.rootProject.file("local.properties").inputStream())
        buildConfigField("String", "TooniboyCookie", "\"${properties.getProperty("TooniboyCookie")}\"")
    }
}


cloudstream {
    // All of these properties are optional, you can safely remove them
    description = "Tooniboy Multi Language"
    language    = "hi"
    authors = listOf("HindiProviders")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 0 // will be 3 if unspecified

    // List of video source types. Users are able to filter for extensions in a given category.
    // You can find a list of avaliable types here:
    // https://recloudstream.github.io/cloudstream/html/app/com.lagradost.cloudstream3/-tv-type/index.html
    tvTypes = listOf("Movie,Anime,Cartoon")
    iconUrl="https://www.google.com/s2/favicons?domain=tooniboy.com&sz=%size%"
}
