package com.luncher.launcher.settings

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import android.net.Uri
import com.luncher.domain.pictures.FakePictureImages
import com.luncher.domain.pictures.FakePictures
import com.luncher.domain.pictures.FolderPicture

/**
 * The ports a test gives the picture library's screens: [TestSettingsGraph]'s, with an empty
 * library unless the test gives one, and pictures drawn as [sample]s, in the background when the
 * test runs [jobs].
 */
open class TestPicturesGraph(library: FakePictures<Uri> = FakePictures()) : TestSettingsGraph(library) {
    override val pictureImages = FakePictureImages { picture, width, height, _ -> sample(picture.value, width, height) }
}

/** A picture to show in place of [name]'s: a gradient in colors of its own, so each looks different. */
fun sample(name: String, width: Int, height: Int): Bitmap {
    val hue = (name.hashCode() and 0xFFFF) % 360f
    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val paint = Paint().apply {
        shader = LinearGradient(
            0f, 0f, width.toFloat(), height.toFloat(),
            Color.HSVToColor(floatArrayOf(hue, 0.7f, 0.5f)), Color.HSVToColor(floatArrayOf((hue + 120) % 360, 0.6f, 0.9f)),
            Shader.TileMode.CLAMP,
        )
    }
    Canvas(bitmap).drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
    return bitmap
}

/** A picture in a drop folder, named [name], on a USB drive if [removable]; [unsupported] in a format this TV can't show. */
fun folderPicture(name: String, removable: Boolean = false, unsupported: com.luncher.domain.pictures.PictureFormat? = null) =
    FolderPicture(
        name, removable, 2_400_000,
        if (unsupported == null) 4000 else 0, if (unsupported == null) 3000 else 0,
        unsupported, Uri.parse("file:///storage/emulated/0/Android/media/com.luncher.launcher/$name"),
    )
