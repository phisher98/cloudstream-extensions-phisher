// use an integer for version numbers
version = 12


cloudstream {
    // All of these properties are optional, you can safely remove them

    description = "Contains SeaTV (Chinese)"
    language    = "zh"
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
    tvTypes = listOf("Anime")
    iconUrl="https://raw.githubusercontent.com/Kohi-den/extensions-source/927f9c68fd64ce52f6212ae633d0f0585eca3545/src/en/donghuastream/res/mipmap-xxxhdpi/ic_launcher.png"

    isCrossPlatform = true
}
