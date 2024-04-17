import org.jetbrains.kotlin.konan.properties.Properties

// use an integer for version numbers
version = 10

android {
    defaultConfig {
        val properties = Properties()
        properties.load(project.rootProject.file("local.properties").inputStream())

        buildConfigField("String", "ANICHI_API", "\"${properties.getProperty("ANICHI_API")}\"")
        buildConfigField("String", "ANICHI_SERVER", "\"${properties.getProperty("ANICHI_SERVER")}\"")
        buildConfigField("String", "ANICHI_ENDPOINT", "\"${properties.getProperty("ANICHI_ENDPOINT")}\"")
        buildConfigField("String", "ANICHI_APP", "\"${properties.getProperty("ANICHI_APP")}\"")


    }
}

cloudstream {
    language = "en"
    // All of these properties are optional, you can safely remove them

    // description = "Lorem Ipsum"
     authors = listOf("Hexated")

    /**
     * Status int as the following:
     * 0: Down
     * 1: Ok
     * 2: Slow
     * 3: Beta only
     * */
    status = 1 // will be 3 if unspecified
    tvTypes = listOf(
        "AnimeMovie",
        "Anime",
        "OVA",
    )

    iconUrl = "https://cdn.discordapp.com/attachments/1109266606292488297/1200425504432472176/Anichi.png?ex=65eb0c5f&is=65d8975f&hm=974898b22b08774a5caa835b40546a6419280446b68255147dd1febb3abe9119&"
}