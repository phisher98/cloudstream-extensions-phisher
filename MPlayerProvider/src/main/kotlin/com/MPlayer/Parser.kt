package com.MPlayer


import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.fasterxml.jackson.annotation.JsonProperty

@JsonIgnoreProperties(ignoreUnknown = true)
data class MXPlayer(
    val style: String,
    val totalCount: Long,
    val next: String,
    val previous: String,
    val items: List<Item>,
)


data class Item(
    val description: String,
    val title: String,
    val releaseDate: String,
    val stream: Stream?,
    val type: String,
    val tvodPackImageInfo: Any?,
    val tvodDetail: Any?,
    val watchAt: Long,
    val lastWatched: Boolean,
    val lastWatchedEpisodeId: Any?,
    val subtitleLanguageCode: Any?,
    val audioTrackLanguageCode: Any?,
    val statusCode: Long,
    val lastTvShowEpisode: Boolean,
    val rating: Long,
    val descriptor: Any?,
    val id: String,
    val languages: List<String>,
    val languagesDetails: List<LanguagesDetail>,
    val duration: Long,
    val genres: List<String>,
    val genresDetails: List<GenresDetail>,
    val secondaryGenres: List<String>,
    val publishTime: Any?,
    val shareUrl: String,
    val image: Image,
    val imageInfo: List<ImageInfo>,
    val titleContentImageInfo: List<TitleContentImageInfo>,
    val trailerPreview: Any?,
    val trailer: List<Trailer>?,
    val container: Any?,
    val contributors: List<Any?>,
    val sequence: Long,
    val subType: String,
    val gifVideoUrl: Any?,
    val gifVideoUrlInfo: Any?,
    @JsonProperty("canPreviewGIFVideo")
    val canPreviewGifvideo: Boolean,
    val webUrl: String,
    val isOptimizedDescription: Boolean,
    val childCount: Long,
    val videoCount: Long,
    val detailKey: Any?,
    val inlineData: Any?,
    val statistics: Any?,
    val viewCount: Long,
    val overlayImages: Any?,
    val tags: Any?,
    val tabs: Any?,
    val goldBadgeImageInfo: Any?,
    @JsonProperty("existInCW")
    val existInCw: Boolean,
)

data class LanguagesDetail(
    val id: String,
    val name: String,
    val webUrl: String?,
    @JsonProperty("three_char_language_id")
    val threeCharLanguageId: String,
)

data class GenresDetail(
    val id: String,
    val name: String,
    val webUrl: String?,
)

data class Image(
    @JsonProperty("16x9")
    val n16x9: String,
    @JsonProperty("2x3")
    val n2x3: String,
    @JsonProperty("1x1")
    val n1x1: String?,
    @JsonProperty("18x14")
    val n18x14: Any?,
    @JsonProperty("40x13")
    val n40x13: Any?,
    @JsonProperty("9x16")
    val n9x16: Any?,
    @JsonProperty("13x15")
    val n13x15: Any?,
    @JsonProperty("2x1")
    val n2x1: String?,
    @JsonProperty("9x19")
    val n9x19: String?,
)

data class ImageInfo(
    val density: String,
    val width: Long,
    val type: String,
    val url: String,
    val height: Long,
    @JsonProperty("genre_ids")
    val genreIds: List<String>?,
)

data class TitleContentImageInfo(
    val density: String,
    val width: Long,
    val type: String,
    val url: String,
    val height: Long,
)

data class Trailer(
    val description: String,
    val title: String,
    val releaseDate: String,
    val stream: Stream,
    val type: String,
    val tvodPackImageInfo: Any?,
    val tvodDetail: Any?,
    val watchAt: Long,
    val lastWatched: Boolean,
    val lastWatchedEpisodeId: Any?,
    val subtitleLanguageCode: Any?,
    val audioTrackLanguageCode: Any?,
    val statusCode: Long,
    val lastTvShowEpisode: Boolean,
    val rating: Long,
    val descriptor: Any?,
    val id: String,
    val languages: List<String>,
    val languagesDetails: List<LanguagesDetail2>,
    val duration: Long,
    val genres: List<String>,
    val genresDetails: List<GenresDetail2>,
    val secondaryGenres: Any?,
    val publishTime: Any?,
    val shareUrl: String,
    val image: Image2,
    val imageInfo: List<ImageInfo2>,
    val titleContentImageInfo: Any?,
    val trailer: Any?,
    val firstVideo: Any?,
    val container: Any?,
    val contributors: List<Any?>,
    val sequence: Long,
    val subType: String,
    @JsonProperty("canPreviewGIFVideo")
    val canPreviewGifvideo: Boolean,
    val webUrl: String,
    val isOptimizedDescription: Boolean,
    val childCount: Long,
    val videoCount: Long,
    val detailKey: Any?,
    val inlineData: Any?,
    val statistics: Any?,
    val viewCount: Long,
    val overlayImages: Any?,
    val tabs: Any?,
    val goldBadgeImageInfo: Any?,
    @JsonProperty("existInCW")
    val existInCw: Boolean,
)

data class Stream(
    val provider: String,
    val dash: Dash,
    val hls: Hls,
    val drmProtect: Boolean,
    val mxplay: Mxplay,
    val youtube: Any?,
    val sony: Any?,
    val altBalaji: Any?,
    val thirdParty: ThirdParty?,
    val videoHash: String,
    val adTagProvider: String,
    val download: Download,
    val watermark: Any?,
    val aspectRatio: String,
)

data class ThirdParty(
    val dashUrl: String?,
    val hlsUrl: String?,
    val contentId: String?,
    val hlsId: String?,
    val dashId: String?,
    val webHlsUrl: String?,
    val validUntil: String?,
    val name: String
)

data class Dash(
    val high: String,
    val base: Any?,
    val main: Any?,
)

data class Hls(
    val high: String,
    val base: Any?,
    val main: Any?,
)

data class Mxplay(
    val dash: Dash2,
    val hls: Hls2,
    val contentId: String,
    val validUntil: Any?,
    val offsetTime: Long,
    val dvr: Boolean,
)

data class Dash2(
    val high: String,
    val base: Any?,
    val main: Any?,
)

data class Hls2(
    val high: String,
    val base: Any?,
    val main: Any?,
)

data class Download(
    val expiryDate: String,
    val requireLoginToDownload: Boolean,
    val requiredPack: String,
    val requireSubscriptionToDownload: Boolean,
    val downloadCriteria: String,
    val isEligibleForDownload: Boolean,
)

data class LanguagesDetail2(
    val id: String,
    val name: String,
    val webUrl: String,
    @JsonProperty("three_char_language_id")
    val threeCharLanguageId: String,
)

data class GenresDetail2(
    val id: String,
    val name: String,
)

data class Image2(
    @JsonProperty("16x9")
    val n16x9: String,
    @JsonProperty("2x3")
    val n2x3: Any?,
    @JsonProperty("1x1")
    val n1x1: Any?,
    @JsonProperty("18x14")
    val n18x14: Any?,
    @JsonProperty("40x13")
    val n40x13: Any?,
    @JsonProperty("9x16")
    val n9x16: String?,
    @JsonProperty("13x15")
    val n13x15: Any?,
    @JsonProperty("2x1")
    val n2x1: Any?,
    @JsonProperty("9x19")
    val n9x19: Any?,
)

data class ImageInfo2(
    val density: String,
    val width: Long,
    val type: String,
    val url: String,
    val height: Long,
)

//Loadtest




//Entity


//EpisodesParser
@JsonIgnoreProperties(ignoreUnknown = true)
data class EpisodesParser(
    val id: String,
    val style: String,
    val items: List<EpisodesItem>,
    val next: String?,
    val previous: String,
    val name: String,
    val webUrl: Any?,
    val channelDetails: Any?,
    val features: Any?,
    val tournament: Any?,
    val ascend: Boolean,
)


data class EpisodesItem(
    val description: String,
    val title: String?,
    val releaseDate: String,
    val stream: EpisodesStream,
    val type: String,
    val tvodPackImageInfo: Any?,
    val tvodDetail: Any?,
    val watchAt: Long,
    val lastWatched: Boolean,
    val lastWatchedEpisodeId: Any?,
    val subtitleLanguageCode: Any?,
    val audioTrackLanguageCode: Any?,
    val statusCode: Long,
    val lastTvShowEpisode: Boolean,
    val rating: Long,
    val descriptor: Any?,
    val id: String,
    val languages: List<String>,
    val languagesDetails: List<EpisodesLanguagesDetail>,
    val duration: Long,
    val genres: List<String>,
    val genresDetails: List<GenresDetail>,
    val secondaryGenres: List<String>,
    val publishTime: String,
    val shareUrl: String,
    val image: EpisodesImage,
    val imageInfo: List<EpisodesImageInfo>,
    val titleContentImageInfo: Any?,
    val trailerPreview: Any?,
    val trailer: Any?,
    val firstVideo: Any?,
    val container: Container,
    val sequence: Long,
    val subType: String,
    @JsonProperty("canPreviewGIFVideo")
    val canPreviewGifvideo: Boolean,
    val webUrl: String?,
    val isOptimizedDescription: Boolean,
    val childCount: Long,
    val videoCount: Long,
    val detailKey: Any?,
    val inlineData: Any?,
    val statistics: Any?,
    val viewCount: Long,
    val overlayImages: Any?,
    val tabs: Any?,
    val goldBadgeImageInfo: Any?,
    @JsonProperty("existInCW")
    val existInCw: Boolean,
)

data class EpisodesStream(
    val provider: String,
    val dash: EpisodesDash,
    val hls: EpisodesHls,
    val drmProtect: Boolean,
    val mxplay: EpisodesMxplay,
    val youtube: Any?,
    val sony: Any?,
    val altBalaji: Any?,
    val thirdParty: Any?,
    val videoHash: String,
    val adTagProvider: String,
    val download: EpisodesDownload,
    val watermark: Any?,
    val aspectRatio: String,
)

data class EpisodesDash(
    val high: String,
    val base: Any?,
    val main: Any?,
)

data class EpisodesHls(
    val high: String,
    val base: Any?,
    val main: Any?,
)

data class EpisodesMxplay(
    val dash: EpisodesDash2,
    val hls: EpisodesHls2,
    val contentId: String,
    val validUntil: Any?,
    val offsetTime: Long,
    val dvr: Boolean,
)

data class EpisodesDash2(
    val high: String,
    val base: Any?,
    val main: Any?,
)

data class EpisodesHls2(
    val high: String,
    val base: Any?,
    val main: Any?,
)

data class EpisodesDownload(
    val expiryDate: String,
    val requireLoginToDownload: Boolean,
    val requiredPack: String,
    val requireSubscriptionToDownload: Boolean,
    val downloadCriteria: String,
    val isEligibleForDownload: Boolean,
)

data class EpisodesLanguagesDetail(
    val id: String,
    val name: String,
    val webUrl: String,
    @JsonProperty("three_char_language_id")
    val threeCharLanguageId: String,
)

data class EpisodesImage(
    @JsonProperty("16x9")
    val n16x9: String,
    @JsonProperty("2x3")
    val n2x3: Any?,
    @JsonProperty("1x1")
    val n1x1: Any?,
    @JsonProperty("18x14")
    val n18x14: String,
    @JsonProperty("40x13")
    val n40x13: Any?,
    @JsonProperty("9x16")
    val n9x16: Any?,
    @JsonProperty("13x15")
    val n13x15: Any?,
    @JsonProperty("2x1")
    val n2x1: String,
    @JsonProperty("9x19")
    val n9x19: Any?,
)

data class EpisodesImageInfo(
    val density: String,
    val width: Long,
    val type: String,
    val url: String,
    val height: Long,
)

data class Container(
    val title: String,
    val type: String,
    val sequence: Long,
    val imageInfo: List<ImageInfo2>,
    val aroundApi: Any?,
    val episodesCount: Long,
    val lastWatched: Boolean,
    val lastWatchedEpisodeId: Any?,
    val id: String,
)


//MovieHomapege

data class MovieRoot(
    val style: String,
    val totalCount: Long,
    val next: String,
    val previous: String,
    val items: List<MovieItem>,
)

data class MovieItem(
    val description: String,
    val title: String,
    val releaseDate: String,
    val stream: MovieStream,
    val type: String,
    val tvodPackImageInfo: Any?,
    val tvodDetail: Any?,
    val watchAt: Long,
    val lastWatched: Boolean,
    val lastWatchedEpisodeId: Any?,
    val subtitleLanguageCode: Any?,
    val audioTrackLanguageCode: Any?,
    val statusCode: Long,
    val lastTvShowEpisode: Boolean,
    val rating: Long,
    val descriptor: Any?,
    val id: String,
    val languages: List<String>,
    val languagesDetails: List<MovieLanguagesDetail>,
    val duration: Long,
    val genres: List<String>,
    val genresDetails: List<MovieGenresDetail>,
    val secondaryGenres: List<String>?,
    val publishTime: String,
    val shareUrl: String,
    val image: MovieImage,
    val imageInfo: List<MovieImageInfo>,
    val titleContentImageInfo: List<MovieTitleContentImageInfo>?,
    val trailerPreview: Any?,
    val trailer: List<MovieTrailer>?,
    val firstVideo: Any?,
    val container: Any?,
    val sequence: Long,
    val subType: String,
    @JsonProperty("canPreviewGIFVideo")
    val canPreviewGifvideo: Boolean,
    val webUrl: String,
    val isOptimizedDescription: Boolean,
    val publisher: MoviePublisher2,
    val childCount: Long,
    val videoCount: Long,
    val detailKey: Any?,
    val inlineData: Any?,
    val statistics: Any?,
    val viewCount: Long,
    val overlayImages: Any?,
    val tabs: Any?,
    val goldBadgeImageInfo: Any?,
    @JsonProperty("existInCW")
    val existInCw: Boolean,
)

data class MovieStream(
    val provider: String,
    val dash: MovieDash?,
    val hls: MovieHls?,
    val drmProtect: Boolean,
    val mxplay: MovieMxplay?,
    val youtube: Any?,
    val sony: Any?,
    val altBalaji: Any?,
    val thirdParty: MovieThirdParty?,
    val videoHash: String,
    val adTagProvider: String,
    val download: MovieDownload,
    val watermark: Any?,
    val aspectRatio: String,
)

data class MovieDash(
    val high: String?,
    val base: String?,
    val main: Any?,
)

data class MovieHls(
    val high: String?,
    val base: String?,
    val main: Any?,
)

data class MovieMxplay(
    val dash: MovieDash2,
    val hls: MovieHls2,
    val contentId: String,
    val validUntil: Any?,
    val offsetTime: Long,
    val dvr: Boolean,
)

data class MovieDash2(
    val high: String?,
    val base: String?,
    val main: Any?,
)

data class MovieHls2(
    val high: String?,
    val base: String?,
    val main: Any?,
)

data class MovieThirdParty(
    val dashUrl: String,
    val hlsUrl: String,
    val contentId: Any?,
    val hlsId: Any?,
    val dashId: Any?,
    val webHlsUrl: Any?,
    val validUntil: Any?,
    val name: String,
)

data class MovieDownload(
    val expiryDate: String?,
    val requireLoginToDownload: Boolean,
    val requiredPack: String,
    val requireSubscriptionToDownload: Boolean,
    val downloadCriteria: String,
    val isEligibleForDownload: Boolean,
)

data class MovieLanguagesDetail(
    val id: String,
    val name: String,
    val webUrl: String,
    @JsonProperty("three_char_language_id")
    val threeCharLanguageId: String,
)

data class MovieGenresDetail(
    val id: String,
    val name: String,
    val webUrl: String,
)

data class MovieImage(
    @JsonProperty("16x9")
    val n16x9: String,
    @JsonProperty("2x3")
    val n2x3: String,
    @JsonProperty("1x1")
    val n1x1: Any?,
    @JsonProperty("18x14")
    val n18x14: Any?,
    @JsonProperty("40x13")
    val n40x13: Any?,
    @JsonProperty("9x16")
    val n9x16: Any?,
    @JsonProperty("13x15")
    val n13x15: Any?,
    @JsonProperty("2x1")
    val n2x1: Any?,
    @JsonProperty("9x19")
    val n9x19: Any?,
)

data class MovieImageInfo(
    val density: String,
    val width: Long,
    val type: String,
    val url: String,
    val height: Long,
)

data class MovieTitleContentImageInfo(
    val density: String,
    val width: Long,
    val type: String,
    val url: String,
    val height: Long,
)

data class MovieTrailer(
    val description: String,
    val title: String,
    val releaseDate: String,
    val stream: MovieStream2,
    val type: String,
    val tvodPackImageInfo: Any?,
    val tvodDetail: Any?,
    val watchAt: Long,
    val lastWatched: Boolean,
    val lastWatchedEpisodeId: Any?,
    val subtitleLanguageCode: Any?,
    val audioTrackLanguageCode: Any?,
    val statusCode: Long,
    val lastTvShowEpisode: Boolean,
    val rating: Long,
    val descriptor: Any?,
    val id: String,
    val languages: List<String>,
    val languagesDetails: List<MovieLanguagesDetail2>,
    val duration: Long,
    val genres: List<String>,
    val genresDetails: List<MovieGenresDetail2>,
    val secondaryGenres: Any?,
    val publishTime: Any?,
    val shareUrl: String,
    val image: MovieImage2,
    val titleContentImageInfo: Any?,
    val trailerPreview: MovieTrailerPreview,
    val trailer: Any?,
    val firstVideo: Any?,
    val container: Any?,
    val contributors: List<Any?>,
    val sequence: Long,
    val subType: String,
    val gifVideoUrl: MovieGifVideoUrl,
    val gifVideoUrlInfo: List<MovieGifVideoUrlInfo>,
    @JsonProperty("canPreviewGIFVideo")
    val canPreviewGifvideo: Boolean,
    val webUrl: String,
    val isOptimizedDescription: Boolean,
    val publisher: MoviePublisher,
    val childCount: Long,
    val videoCount: Long,
    val detailKey: Any?,
    val inlineData: Any?,
    val statistics: Any?,
    val viewCount: Long,
    val overlayImages: Any?,
    val tags: List<MovieTag>,
    val tabs: Any?,
    val goldBadgeImageInfo: Any?,
    @JsonProperty("existInCW")
    val existInCw: Boolean,
)

data class MovieStream2(
    val provider: String,
    val dash: MovieDash3,
    val hls: MovieHls3,
    val drmProtect: Boolean,
    val mxplay: MovieMxplay2,
    val youtube: Any?,
    val sony: Any?,
    val altBalaji: Any?,
    val thirdParty: Any?,
    val videoHash: String,
    val adTagProvider: String,
    val download: MovieDownload2,
    val watermark: Any?,
    val aspectRatio: String,
)

data class MovieDash3(
    val high: String,
    val base: Any?,
    val main: Any?,
)

data class MovieHls3(
    val high: String,
    val base: Any?,
    val main: Any?,
)

data class MovieMxplay2(
    val dash: MovieDash4,
    val hls: MovieHls4,
    val contentId: String,
    val validUntil: Any?,
    val offsetTime: Long,
    val dvr: Boolean,
)

data class MovieDash4(
    val high: String,
    val base: Any?,
    val main: Any?,
)

data class MovieHls4(
    val high: String,
    val base: Any?,
    val main: Any?,
)

data class MovieDownload2(
    val expiryDate: String?,
    val requireLoginToDownload: Boolean,
    val requiredPack: String,
    val requireSubscriptionToDownload: Boolean,
    val downloadCriteria: String,
    val isEligibleForDownload: Boolean,
)

data class MovieLanguagesDetail2(
    val id: String,
    val name: String,
    val webUrl: String,
    @JsonProperty("three_char_language_id")
    val threeCharLanguageId: String,
)

data class MovieGenresDetail2(
    val id: String,
    val name: String,
)

data class MovieImage2(
    @JsonProperty("16x9")
    val n16x9: String,
    @JsonProperty("2x3")
    val n2x3: String,
    @JsonProperty("1x1")
    val n1x1: Any?,
)

data class MovieTrailerPreview(
    val url: String,
    val previewImage: String,
)

data class MovieGifVideoUrl(
    val high: String,
    val base: String,
)

data class MovieGifVideoUrlInfo(
    val height: String,
    val width: String,
    val type: String,
    val url: String,
)

data class MoviePublisher(
    val id: String,
    val name: String,
)

data class MovieTag(
    val id: String,
    val name: String,
)

data class MoviePublisher2(
    val id: String,
    val name: String,
)
