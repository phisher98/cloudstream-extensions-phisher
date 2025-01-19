// use an integer for version numbers
import org.jetbrains.kotlin.konan.properties.Properties

version = 1


android {
    defaultConfig {
        val properties = Properties()
        properties.load(project.rootProject.file("local.properties").inputStream())
        android.buildFeatures.buildConfig=true
        buildConfigField("String", "TimefourTv", "\"${properties.getProperty("TimefourTv")}\"")
    }
}

cloudstream {
    language = "en"
    // All of these properties are optional, you can safely remove them

    description = "Sports Live Stream (Multi Region)"
    authors = listOf("Phisher")

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

    iconUrl = "https://daddylivehd1.click/wp-content/uploads/2024/10/daddylive.webp"
}
