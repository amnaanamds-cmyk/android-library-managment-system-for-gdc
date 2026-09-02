package com.college.library.data.marc

import com.college.library.data.model.Book

/**
 * MARC 21 bibliographic record generation.
 *
 * Extracted from BookDetailScreen, where it was built inline. The MARC catalog
 * screen needs the same output, and two copies of a cataloguing format drift
 * apart — a record exported from one screen would not match the other.
 *
 * Field usage follows the same subset the desktop MARC screen edits:
 *   020 ISBN, 100 main entry (author), 245 title statement,
 *   250 edition, 260 publication, 300 physical description,
 *   490 series, 650 subject, 852/900 local holdings.
 */
object Marc21 {

    /** One MARC field: tag, two indicator positions, and subfield data. */
    data class Field(val tag: String, val indicators: String, val data: String) {
        fun render(): String = "$tag  $indicators $data".trimEnd()
    }

    /** Structured fields, for a screen that shows or edits them individually. */
    fun fields(book: Book): List<Field> = buildList {
        add(Field("000", "  ", "00000nam a2200000 a 4500"))
        add(Field("001", "  ", book.id.toString().padStart(8, '0')))
        if (book.isbn.isNotBlank()) add(Field("020", "  ", "\$a ${book.isbn}"))
        if (book.author.isNotBlank()) add(Field("100", "1 ", "\$a ${book.author}"))
        if (book.title.isNotBlank()) add(Field("245", "10", "\$a ${book.title}"))
        if (book.edition.isNotBlank()) add(Field("250", "  ", "\$a ${book.edition}"))
        add(
            Field(
                "260",
                "  ",
                "\$a ${book.publisherPlace.ifBlank { "S.l." }} : " +
                    "\$b ${book.publisher.ifBlank { "s.n." }}, " +
                    "\$c ${book.publishDate.ifBlank { "n.d." }}.",
            ),
        )
        if (book.pages > 0) add(Field("300", "  ", "\$a ${book.pages} p."))
        if (book.volume.isNotBlank()) add(Field("490", "1 ", "\$a ${book.volume}"))
        if (book.category.isNotBlank()) add(Field("650", " 0", "\$a ${book.category}"))
        if (book.callNumber.isNotBlank()) {
            add(Field("852", "  ", "\$h ${book.callNumber} \$i ${book.authorCutter}".trimEnd()))
        }
        if (book.accNo.isNotBlank()) add(Field("900", "  ", "\$a ${book.accNo}"))
    }

    /**
     * Plain-text MARC record.
     *
     * A book that already carries stored `marcData` from an import keeps it:
     * hand-catalogued data is authoritative over anything regenerated from the
     * few fields the app tracks.
     */
    fun render(book: Book): String {
        val stored = book.marcData
        if (!stored.isNullOrBlank()) return stored
        return fields(book).joinToString("\n") { it.render() }
    }

    /** True when the record was catalogued rather than generated on the fly. */
    fun hasStoredRecord(book: Book): Boolean = !book.marcData.isNullOrBlank()

    /**
     * Apply edited MARC text back onto a book.
     *
     * Only the three fields the desktop MARC editor writes back are mapped;
     * everything else is preserved verbatim in `marcData` so no catalogued
     * detail is lost by a round trip through this app.
     */
    fun applyEdits(book: Book, marcText: String): Book {
        var title = book.title
        var author = book.author
        var isbn = book.isbn

        marcText.lineSequence().forEach { line ->
            val trimmed = line.trim()
            if (trimmed.length < 3) return@forEach
            val tag = trimmed.take(3)
            val value = trimmed.drop(3).substringAfter("\$a", "").trim()
            if (value.isEmpty()) return@forEach
            when (tag) {
                "245" -> title = value.substringBefore(" \$").trim()
                "100" -> author = value.substringBefore(" \$").trim()
                "020" -> isbn = value.substringBefore(" \$").trim()
            }
        }

        return book.copy(
            title = title.ifBlank { book.title },
            author = author.ifBlank { book.author },
            isbn = isbn,
            marcData = marcText,
        )
    }
}
