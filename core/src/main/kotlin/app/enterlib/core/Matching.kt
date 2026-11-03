package app.enterlib.core

import java.text.Normalizer

object Matching {
    private val marks = Regex("\\p{Mn}+")
    private val nonAlnum = Regex("[^a-z0-9\\p{L}]+")
    private val article = Regex("^(the|a|an) ")
    private val titleYear = Regex("^(.*?)\\s*[\\(\\[]((?:19|20)\\d{2})[\\)\\]]\\s*$")

    fun normalizeTitle(title: String): String {
        val n = Normalizer.normalize(title, Normalizer.Form.NFD).replace(marks, "")
        return n.lowercase().replace("&", " and ").replace(nonAlnum, " ").trim().replace(article, "")
    }

    fun matchKey(kind: MediaKind, title: String, year: Int?): String =
        "${kind.name.lowercase()}:${normalizeTitle(title)}:${year ?: 0}"

    fun splitTitleYear(raw: String): Pair<String, Int?> {
        val m = titleYear.matchEntire(raw.trim()) ?: return raw.trim() to null
        return m.groupValues[1].trim() to m.groupValues[2].toInt()
    }
}
