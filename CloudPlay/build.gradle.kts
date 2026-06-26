@file:Suppress("UnstableApiUsage")

version = 4

android {
    defaultConfig {
        android.buildFeatures.buildConfig = true
    }
}

cloudstream {
    language = "en"
    requiresResources = false
    description = "CloudPlay Live TV Extension"
    authors = listOf("Phisher98")

    status = 1
    tvTypes = listOf(
        "Live",
    )

    iconUrl = "https://raw.githubusercontent.com/phisher98/TVVVV/refs/heads/main/Icons/cloudplay.jpg"

    isCrossPlatform = false
}
