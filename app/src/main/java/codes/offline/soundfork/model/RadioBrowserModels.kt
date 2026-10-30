package codes.offline.soundfork.model

data class RadioBrowserQuery(
    val name: String = "",
    val country: String = "",
    val language: String = "",
    val tag: String = ""
) {
    fun normalized() = RadioBrowserQuery(name.trim(), country.trim(), language.trim(), tag.trim())
    fun isEmpty() = listOf(name, country, language, tag).all { it.isBlank() }
}

data class RadioBrowserSearchState(
    val query: RadioBrowserQuery = RadioBrowserQuery(),
    val stations: List<RadioStation> = emptyList(),
    val isSearching: Boolean = false,
    val hasSearched: Boolean = false,
    val error: String? = null,
    val nextOffset: Int = 0,
    val hasMore: Boolean = false
)
