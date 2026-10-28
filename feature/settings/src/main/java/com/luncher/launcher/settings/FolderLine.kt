package com.luncher.launcher.settings

import android.content.Context
import android.util.AttributeSet
import com.luncher.launcher.ui.ThemedText

/**
 * Add from folder's line below its title: where the pictures are, a sentence that ends with the
 * folder ([show]); where it doesn't fit on the line (the smallest screens), the folder alone, as
 * the rest says what the title and the list's rows say already. The folder is cut at its start
 * where even it doesn't fit.
 */
internal class FolderLine(context: Context, attrs: AttributeSet?) : ThemedText(context, attrs) {

    private var sentence: CharSequence = ""
    private var folder: CharSequence = ""

    /** Shows [sentence], or [folder] where the sentence doesn't fit. */
    fun show(sentence: CharSequence, folder: CharSequence) {
        this.sentence = sentence
        this.folder = folder
        text = sentence
    }

    override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
        if (MeasureSpec.getMode(widthMeasureSpec) != MeasureSpec.UNSPECIFIED) {
            val room = MeasureSpec.getSize(widthMeasureSpec) - compoundPaddingLeft - compoundPaddingRight
            val shown = if (paint.measureText(sentence, 0, sentence.length) <= room) sentence else folder
            if (text.toString() != shown.toString()) text = shown
        }
        super.onMeasure(widthMeasureSpec, heightMeasureSpec)
    }
}
