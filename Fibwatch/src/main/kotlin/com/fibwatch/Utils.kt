package com.Fibwatch

fun cleanTitle(raw: String?): String {
    val regex = Regex("""S(\d+)[Ee](\d+)(?:-(\d+))?""")
    val match = regex.find(raw ?: "") ?: return raw!!.trim()

    val season = match.groupValues[1].toInt()
    val epStart = match.groupValues[2].toInt()
    val epEnd = match.groupValues.getOrNull(3)?.takeIf { it.isNotEmpty() }?.toInt()

    val showName = raw?.substringBefore(match.value)!!.trim()
    val year = Regex("""\((\d{4})\)""").find(raw)?.groupValues?.get(1)

    val titleBase = if (year != null) showName else showName
    val episodes = if (epEnd != null) "Episodes $epStart–$epEnd" else "Episode $epStart"

    return "$titleBase Season $season | $episodes"
}


data class Links(
    val status: String?,
    val current: List<Current> = emptyList(),
    val popup: List<Popup> = emptyList(),
)

data class Current(
    val res: String?,
    val url: String?,
    val selected: Boolean = false,
)

data class Popup(
    val res: String?,
    val url: String?,
    val selected: Boolean = false,
)

data class LoadItem(
    val quality: String,
    val url: String,
    val selected: Boolean = false
)

data class LoadlinksOut(
    val status: String,
    val current: List<LoadItem>,
    val popup: List<LoadItem>
)

data class EpisodesResponse(
    val status: String?,
    val episodes: List<EpisodeItem>?
)

data class EpisodeItem(
    val ep_key: String?,
    val display: String?,
    val title: String?,
    val url: String?,
    val is_current: Boolean?
)

data class EpisodeInfo(
    val season: Int?,
    val episodeStart: Int?,
    val episodeEnd: Int?
)

fun parseSeasonEpisode(title: String): EpisodeInfo {
    val t = title.lowercase()

    // Case: S01E05 or S1E5 or S01E05-08
    val full = Regex("""s(\d{1,2})e(\d{1,3})(?:-(\d{1,3}))?""").find(t)
    if (full != null) {
        val season = full.groupValues[1].toInt()
        val epStart = full.groupValues[2].toInt()
        val epEnd = full.groupValues[3].takeIf { it.isNotBlank() }?.toInt()
        return EpisodeInfo(season, epStart, epEnd)
    }

    // Case: S01
    val seasonOnly = Regex("""s(\d{1,2})\b""").find(t)
    if (seasonOnly != null) {
        val season = seasonOnly.groupValues[1].toInt()
        return EpisodeInfo(season, null, null)
    }

    // Case: E05
    val epOnly = Regex("""e(\d{1,3})\b""").find(t)
    if (epOnly != null) {
        val episode = epOnly.groupValues[1].toInt()
        return EpisodeInfo(null, episode, null)
    }

    return EpisodeInfo(null, null, null)
}

