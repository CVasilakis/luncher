package com.luncher.domain.pictures

import java.text.Collator
import java.util.Locale

/**
 * The library as the background editor lists it: by name, as [locale]'s language sorts words; and
 * what each one is used for ([uses], from [pictureUses]), which deleting it says.
 */
class PictureLibrary(pictures: List<Picture>, locale: Locale, private val uses: Map<PictureId, List<PictureUse>> = emptyMap()) {

    /** By name; names the language sorts alike by id, so the order never changes between two reads. */
    val pictures: List<Picture> = Collator.getInstance(locale).let { collator ->
        pictures.sortedWith(Comparator<Picture> { a, b -> collator.compare(a.name, b.name) }.thenBy { it.id.value })
    }

    /** What the picture [id] is used for; nothing if it's unused. */
    fun usesOf(id: PictureId): List<PictureUse> = uses[id].orEmpty()

    /** Where the picture [id] is in [pictures]; -1 if it isn't. */
    fun indexOf(id: PictureId): Int = pictures.indexOfFirst { it.id == id }
}
