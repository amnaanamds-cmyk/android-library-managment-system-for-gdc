package com.college.library.utils

val DDC_MAP = mapOf(
    "computer" to "004", "software" to "005", "data" to "006",
    "philosophy" to "100", "psychology" to "150", "logic" to "160",
    "religion" to "200", "islam" to "297", "christianity" to "230",
    "social" to "300", "economics" to "330", "law" to "340", "education" to "370",
    "language" to "400", "english" to "420", "urdu" to "491",
    "science" to "500", "math" to "510", "physics" to "530", "chemistry" to "540",
    "biology" to "570", "botany" to "580", "zoology" to "590",
    "technology" to "600", "medicine" to "610", "engineering" to "620",
    "agriculture" to "630", "management" to "658",
    "art" to "700", "music" to "780", "sports" to "796",
    "literature" to "800", "fiction" to "823", "poetry" to "811",
    "history" to "900", "geography" to "910", "pakistan" to "954.91"
)

fun getSuggestedDdc(title: String, category: String): String {
    val searchStr = "$title $category".lowercase()
    return DDC_MAP.entries.firstOrNull { searchStr.contains(it.key) }?.value ?: "020" // default Library Science
}

fun buildAuthorCutter(author: String): String {
    if (author.isBlank()) return "A00"
    val parts = author.split(" ").filter { it.isNotBlank() }
    val lastName = parts.lastOrNull() ?: "Unknown"
    val firstChar = lastName.firstOrNull()?.uppercaseChar() ?: 'A'
    val number = lastName.length * 11 % 100
    return "$firstChar$number"
}
