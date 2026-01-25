import org.jetbrains.kotlin.konan.properties.Properties

// use an integer for version numbers
version = 64

android {
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    defaultConfig {
        val properties = Properties()
        properties.load(project.rootProject.file("local.properties").inputStream())
        android.buildFeatures.buildConfig=true
    }
}

cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "#1 Best Extension – MultiAPI-Based with 4K Torrent Support (Debian) Use Extension Settings"
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
    tvTypes = listOf("Movie","Torrent","AsianDrama","TvSeries","Anime")

    iconUrl = "https://torrentio.strem.fun/images/logo_v1.png"
    requiresResources = true
    isCrossPlatform = false
}

dependencies {
    implementation("androidx.appcompat:appcompat:1.7.1")
    implementation("com.google.android.material:material:1.13.0")
    // FIXME remove this when crossplatform is fully supported
    val cloudstream by configurations
    cloudstream("com.lagradost:cloudstream3:pre-release")
}
