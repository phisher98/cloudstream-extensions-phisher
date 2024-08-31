package com.HindiProviders

import com.fasterxml.jackson.annotation.JsonProperty

data class Root(
        val message: String? = null,
        val meta: Meta? = null,
        val next: Boolean? = null,
        val series: Series? = null,
        val players: List<Player>? = null,
        val noplayer: String? = null,
        val type: List<Any?>? = null,
        val languages: List<Language>? = null,
        val availableLanguages: AvailableLanguages? = null,
        val seasonList: List<SeasonList>? = null,
        val seasonData: SeasonData? = null,
)

data class Meta(
        val number: String? = null,
        val title: String? = null,
        val duration: String? = null,
        val released: String? = null,
        @JsonProperty("parent_id")
        val parentId: String? = null,
        @JsonProperty("parent_name")
        val parentName: String? = null,
        @JsonProperty("parent_slug")
        val parentSlug: String? = null,
        val players: Any?? = null,
        val download: List<Any?>? = null,
        val thumbnail: String? = null,
        @JsonProperty("anime_type")
        val animeType: String? = null,
        @JsonProperty("anime_season")
        val animeSeason: String? = null,
        @JsonProperty("anime_id")
        val animeId: String? = null,
        val episode: String? = null,
        val thumb: String? = null,
        @JsonProperty("post_title")
        val postTitle: String? = null,
        val uploaded: Uploaded? = null,
        @JsonProperty("total_view")
        val totalView: Long? = null,
)

data class Uploaded(
        val date: String? = null,
        @JsonProperty("timezone_type")
        val timezoneType: Long? = null,
        val timezone: String? = null,
)

data class Series(
        val title: String? = null,
        val synopsis: String? = null,
        val url: String? = null,
        val featured: String? = null,
        val metadata: Metadata? = null,
        val type: Type? = null,
        val attribute: List<Attribute>? = null,
        val episodes: List<Episode>? = null,
        val id: Long? = null,
)

data class Metadata(
        val spotlight: String? = null,
        val rate: String? = null,
        val native: String? = null,
        val synonyms: String? = null,
        val aired: String? = null,
        val premiered: String? = null,
        val duration: String? = null,
        val episodes: String? = null,
        val score: String? = null,
        val background: String? = null,
        val featured: String? = null,
        val updated: String? = null,
        val download: List<Any?>? = null,
        @JsonProperty("supported_language")
        val supportedLanguage: String? = null,
        val producer: List<Producer>? = null,
        val studio: List<Studio>? = null,
        val licensor: List<Licensor>? = null,
        val genre: List<Genre>? = null,
)

data class Producer(
        @JsonProperty("term_id")
        val termId: Long? = null,
        val name: String? = null,
        val slug: String? = null,
        @JsonProperty("term_group")
        val termGroup: Long? = null,
        @JsonProperty("term_taxonomy_id")
        val termTaxonomyId: Long? = null,
        val taxonomy: String? = null,
        val description: String? = null,
        val parent: Long? = null,
        val count: Long? = null,
        val filter: String? = null,
)

data class Studio(
        @JsonProperty("term_id")
        val termId: Long? = null,
        val name: String? = null,
        val slug: String? = null,
        @JsonProperty("term_group")
        val termGroup: Long? = null,
        @JsonProperty("term_taxonomy_id")
        val termTaxonomyId: Long? = null,
        val taxonomy: String? = null,
        val description: String? = null,
        val parent: Long? = null,
        val count: Long? = null,
        val filter: String? = null,
)

data class Licensor(
        @JsonProperty("term_id")
        val termId: Long? = null,
        val name: String? = null,
        val slug: String? = null,
        @JsonProperty("term_group")
        val termGroup: Long? = null,
        @JsonProperty("term_taxonomy_id")
        val termTaxonomyId: Long? = null,
        val taxonomy: String? = null,
        val description: String? = null,
        val parent: Long? = null,
        val count: Long? = null,
        val filter: String? = null,
)

data class Genre(
        @JsonProperty("term_id")
        val termId: Long? = null,
        val name: String? = null,
        val slug: String? = null,
        @JsonProperty("term_group")
        val termGroup: Long? = null,
        @JsonProperty("term_taxonomy_id")
        val termTaxonomyId: Long? = null,
        val taxonomy: String? = null,
        val description: String? = null,
        val parent: Long? = null,
        val count: Long? = null,
        val filter: String? = null,
)

data class Type(
        @JsonProperty("term_id")
        val termId: Long? = null,
        val name: String? = null,
        val slug: String? = null,
        @JsonProperty("term_group")
        val termGroup: Long? = null,
        @JsonProperty("term_taxonomy_id")
        val termTaxonomyId: Long? = null,
        val taxonomy: String? = null,
        val description: String? = null,
        val parent: Long? = null,
        val count: Long? = null,
        val filter: String? = null,
)

data class Attribute(
        @JsonProperty("term_id")
        val termId: Long? = null,
        val name: String? = null,
        val slug: String? = null,
        @JsonProperty("term_group")
        val termGroup: Long? = null,
        @JsonProperty("term_taxonomy_id")
        val termTaxonomyId: Long? = null,
        val taxonomy: String? = null,
        val description: String? = null,
        val parent: Long? = null,
        val count: Long? = null,
        val filter: String? = null,
)

data class Episode(
        val title: String? = null,
        val metadata: Metadata2? = null,
        val id: Long? = null,
        val url: String? = null,
        val published: String? = null,
)

data class Metadata2(
        val number: String? = null,
        val title: String? = null,
        val duration: String? = null,
        val released: String? = null,
        @JsonProperty("parent_id")
        val parentId: String? = null,
        @JsonProperty("parent_name")
        val parentName: String? = null,
        @JsonProperty("parent_slug")
        val parentSlug: String? = null,
        val download: List<Any?>? = null,
        val thumbnail: String? = null,
        @JsonProperty("anime_type")
        val animeType: String? = null,
        @JsonProperty("anime_season")
        val animeSeason: String? = null,
        @JsonProperty("anime_id")
        val animeId: String? = null,
)

data class Player(
        val type: String? = null,
        val language: String? = null,
        val quality: String? = null,
        val server: String? = null,
        val url: String? = null,
        val key: String? = null,
)

data class Language(
        val name: String? = null,
        val sort: String? = null,
        val slug: String? = null,
        @JsonProperty("is_default")
        val isDefault: Boolean?? = null,
)

data class AvailableLanguages(
        val all: List<All>? = null,
        val tamil: List<Tamil>? = null,
        val telugu: List<Telugu>? = null,
        val hindi: List<Hindi>? = null,
        val english: List<English>? = null,
)

data class All(
        val title: String? = null,
        val metadata: Metadata3? = null,
        val id: Long? = null,
        val url: String? = null,
        val published: String? = null,
)

data class Metadata3(
        val number: String? = null,
        val title: String? = null,
        val duration: String? = null,
        val released: String? = null,
        @JsonProperty("parent_id")
        val parentId: String? = null,
        @JsonProperty("parent_name")
        val parentName: String? = null,
        @JsonProperty("parent_slug")
        val parentSlug: String? = null,
        val download: List<Any?>? = null,
        val thumbnail: String? = null,
        @JsonProperty("anime_type")
        val animeType: String? = null,
        @JsonProperty("anime_season")
        val animeSeason: String? = null,
        @JsonProperty("anime_id")
        val animeId: String? = null,
)

data class Tamil(
        val title: String? = null,
        val metadata: Metadata4? = null,
        val id: Long? = null,
        val url: String? = null,
        val published: String? = null,
)

data class Metadata4(
        val number: String? = null,
        val title: String? = null,
        val duration: String? = null,
        val released: String? = null,
        @JsonProperty("parent_id")
        val parentId: String? = null,
        @JsonProperty("parent_name")
        val parentName: String? = null,
        @JsonProperty("parent_slug")
        val parentSlug: String? = null,
        val download: List<Any?>? = null,
        val thumbnail: String? = null,
        @JsonProperty("anime_type")
        val animeType: String? = null,
        @JsonProperty("anime_season")
        val animeSeason: String? = null,
        @JsonProperty("anime_id")
        val animeId: String? = null,
)

data class Telugu(
        val title: String? = null,
        val metadata: Metadata5? = null,
        val id: Long? = null,
        val url: String? = null,
        val published: String? = null,
)

data class Metadata5(
        val number: String? = null,
        val title: String? = null,
        val duration: String? = null,
        val released: String? = null,
        @JsonProperty("parent_id")
        val parentId: String? = null,
        @JsonProperty("parent_name")
        val parentName: String? = null,
        @JsonProperty("parent_slug")
        val parentSlug: String? = null,
        val download: List<Any?>? = null,
        val thumbnail: String? = null,
        @JsonProperty("anime_type")
        val animeType: String? = null,
        @JsonProperty("anime_season")
        val animeSeason: String? = null,
        @JsonProperty("anime_id")
        val animeId: String? = null,
)

data class Hindi(
        val title: String? = null,
        val metadata: Metadata6? = null,
        val id: Long? = null,
        val url: String? = null,
        val published: String? = null,
)

data class Metadata6(
        val number: String? = null,
        val title: String? = null,
        val duration: String? = null,
        val released: String? = null,
        @JsonProperty("parent_id")
        val parentId: String? = null,
        @JsonProperty("parent_name")
        val parentName: String? = null,
        @JsonProperty("parent_slug")
        val parentSlug: String? = null,
        val download: List<Any?>? = null,
        val thumbnail: String? = null,
        @JsonProperty("anime_type")
        val animeType: String? = null,
        @JsonProperty("anime_season")
        val animeSeason: String? = null,
        @JsonProperty("anime_id")
        val animeId: String? = null,
)

data class English(
        val title: String? = null,
        val metadata: Metadata7? = null,
        val id: Long? = null,
        val url: String? = null,
        val published: String? = null,
)

data class Metadata7(
        val number: String? = null,
        val title: String? = null,
        val duration: String? = null,
        val released: String? = null,
        @JsonProperty("parent_id")
        val parentId: String? = null,
        @JsonProperty("parent_name")
        val parentName: String? = null,
        @JsonProperty("parent_slug")
        val parentSlug: String? = null,
        val download: List<Any?>? = null,
        val thumbnail: String? = null,
        @JsonProperty("anime_type")
        val animeType: String? = null,
        @JsonProperty("anime_season")
        val animeSeason: String? = null,
        @JsonProperty("anime_id")
        val animeId: String? = null,
)

data class SeasonList(
        @JsonProperty("ID")
        val id: Long? = null,
        val season: String? = null,
        val url: String? = null,
        val featured: String? = null,
        val title: String? = null,
)

data class SeasonData(
        val all: List<All2>? = null,
        val tamil: List<Tamil2>? = null,
        val telugu: List<Telugu2>? = null,
        val hindi: List<Hindi2>? = null,
        val english: List<English2>? = null,
)

data class All2(
        val title: String? = null,
        val metadata: Metadata8? = null,
        val id: Long? = null,
        val url: String? = null,
        val published: String? = null,
        val image: String? = null,
)

data class Metadata8(
        val number: String? = null,
        val title: String? = null,
        val duration: String? = null,
        val released: String? = null,
        @JsonProperty("parent_id")
        val parentId: String? = null,
        @JsonProperty("parent_name")
        val parentName: String? = null,
        @JsonProperty("parent_slug")
        val parentSlug: String? = null,
        val download: List<Any?>? = null,
        val thumbnail: String? = null,
        @JsonProperty("anime_type")
        val animeType: String? = null,
        @JsonProperty("anime_season")
        val animeSeason: String? = null,
        @JsonProperty("anime_id")
        val animeId: String? = null,
)

data class Tamil2(
        val title: String? = null,
        val metadata: Metadata9? = null,
        val id: Long? = null,
        val url: String? = null,
        val published: String? = null,
        val image: String? = null,
)

data class Metadata9(
        val number: String? = null,
        val title: String? = null,
        val duration: String? = null,
        val released: String? = null,
        @JsonProperty("parent_id")
        val parentId: String? = null,
        @JsonProperty("parent_name")
        val parentName: String? = null,
        @JsonProperty("parent_slug")
        val parentSlug: String? = null,
        val download: List<Any?>? = null,
        val thumbnail: String? = null,
        @JsonProperty("anime_type")
        val animeType: String? = null,
        @JsonProperty("anime_season")
        val animeSeason: String? = null,
        @JsonProperty("anime_id")
        val animeId: String? = null,
)

data class Telugu2(
        val title: String? = null,
        val metadata: Metadata10? = null,
        val id: Long? = null,
        val url: String? = null,
        val published: String? = null,
        val image: String? = null,
)

data class Metadata10(
        val number: String? = null,
        val title: String? = null,
        val duration: String? = null,
        val released: String? = null,
        @JsonProperty("parent_id")
        val parentId: String? = null,
        @JsonProperty("parent_name")
        val parentName: String? = null,
        @JsonProperty("parent_slug")
        val parentSlug: String? = null,
        val download: List<Any?>? = null,
        val thumbnail: String? = null,
        @JsonProperty("anime_type")
        val animeType: String? = null,
        @JsonProperty("anime_season")
        val animeSeason: String? = null,
        @JsonProperty("anime_id")
        val animeId: String? = null,
)

data class Hindi2(
        val title: String? = null,
        val metadata: Metadata11? = null,
        val id: Long? = null,
        val url: String? = null,
        val published: String? = null,
        val image: String? = null,
)

data class Metadata11(
        val number: String? = null,
        val title: String? = null,
        val duration: String? = null,
        val released: String? = null,
        @JsonProperty("parent_id")
        val parentId: String? = null,
        @JsonProperty("parent_name")
        val parentName: String? = null,
        @JsonProperty("parent_slug")
        val parentSlug: String? = null,
        val download: List<Any?>? = null,
        val thumbnail: String? = null,
        @JsonProperty("anime_type")
        val animeType: String? = null,
        @JsonProperty("anime_season")
        val animeSeason: String? = null,
        @JsonProperty("anime_id")
        val animeId: String? = null,
)

data class English2(
        val title: String? = null,
        val metadata: Metadata12? = null,
        val id: Long? = null,
        val url: String? = null,
        val published: String? = null,
        val image: String? = null,
)

data class Metadata12(
        val number: String? = null,
        val title: String? = null,
        val duration: String? = null,
        val released: String? = null,
        @JsonProperty("parent_id")
        val parentId: String? = null,
        @JsonProperty("parent_name")
        val parentName: String? = null,
        @JsonProperty("parent_slug")
        val parentSlug: String? = null,
        val download: List<Any?>? = null,
        val thumbnail: String? = null,
        @JsonProperty("anime_type")
        val animeType: String? = null,
        @JsonProperty("anime_season")
        val animeSeason: String? = null,
        @JsonProperty("anime_id")
        val animeId: String? = null,
)
