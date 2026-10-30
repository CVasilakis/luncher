package com.luncher.launcher.settings

import com.luncher.domain.pictures.ImportResult
import com.luncher.domain.pictures.PictureLimits
import com.luncher.domain.pictures.ReceivedPictures.Outcome
import com.luncher.domain.pictures.SizeLimit
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config

/** Picture limits use the language's unit in every message, without changing the accepted image size. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33, Config.TARGET_SDK], qualifiers = "en-rUS")
class PictureTextsTest {

    private val context = RuntimeEnvironment.getApplication()
    private val tooManyPixels = ImportResult.TooLarge(SizeLimit.PIXELS)
    private val twoTooLarge = Outcome.Several(listOf(tooManyPixels, tooManyPixels))

    @Test
    fun `Chinese uses ten thousands in single, folder and batch messages`() {
        RuntimeEnvironment.setQualifiers("zh-rCN")

        assertEquals("像素数超过 6400 万", context.whyNotAdded(tooManyPixels))
        assertEquals("像素数超过 6400 万", context.refusal(tooManyPixels))
        assertEquals(listOf("2 张图片的像素数超过 6400 万"), context.notAddedLines(twoTooLarge))
        assertEquals(PictureLimits.MAX_PIXELS, 6400L * context.resources.getInteger(R.integer.settings_pictures_pixels_per_unit))
    }

    @Test
    fun `English keeps megapixels in single, folder and batch messages`() {
        assertEquals("Too large: over 64 megapixels.", context.whyNotAdded(tooManyPixels))
        assertEquals("Too large (over 64 megapixels)", context.refusal(tooManyPixels))
        assertEquals(listOf("2 larger than 64 megapixels"), context.notAddedLines(twoTooLarge))
    }

    @Test
    fun `changing the app language changes the unit on the existing context`() {
        assertEquals("Too large: over 64 megapixels.", context.whyNotAdded(tooManyPixels))
        RuntimeEnvironment.setQualifiers("zh-rCN")
        assertEquals("像素数超过 6400 万", context.whyNotAdded(tooManyPixels))
        RuntimeEnvironment.setQualifiers("en-rUS")
        assertEquals("Too large: over 64 megapixels.", context.whyNotAdded(tooManyPixels))
    }

    @Test
    fun `other existing languages retain the million pixel unit`() {
        for (language in listOf("el", "ar")) {
            RuntimeEnvironment.setQualifiers(language)
            val resources = context.resources
            assertEquals(language, 1_000_000, resources.getInteger(R.integer.settings_pictures_pixels_per_unit))
            assertEquals(language, resources.getQuantityString(R.plurals.settings_pictures_too_large_pixels, 64, 64), context.whyNotAdded(tooManyPixels))
            assertEquals(language, resources.getQuantityString(R.plurals.settings_pictures_folder_too_large_pixels, 64, 64), context.refusal(tooManyPixels))
            assertEquals(language, listOf(resources.getQuantityString(R.plurals.settings_received_too_large_pixels_count, 2, 2, 64)), context.notAddedLines(twoTooLarge))
        }
    }
}
