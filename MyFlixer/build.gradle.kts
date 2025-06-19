import org.jetbrains.kotlin.konan.properties.Properties

// use an integer for version numbers
version = 1


cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "Watch movies and series from Flixerz"
    authors = listOf("Phisher98")

    /**
    * Status int as the following:
    * 0: Down
    * 1: Ok
    * 2: Slow
    * 3: Beta only
    * */
    status = 1

    tvTypes = listOf("Movies", "TV Series")

    language = "en"

    // random cc logo i found
    iconUrl = "https://myflixerz.to/images/group_1/theme_7/logo.png?v=0.1"

    // Because we use android.graphics.BitmapFactory
    isCrossPlatform = true
}

