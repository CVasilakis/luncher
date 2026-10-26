package com.luncher.launcher.home

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Shader
import com.luncher.domain.pictures.PictureId
import org.robolectric.RuntimeEnvironment
import java.io.File

/**
 * A picture in the library's folder, as one added there: a made-up landscape, a sky and two
 * ranges of hills, its sharp edges showing the blur.
 */
fun landscape(): PictureId {
    val id = PictureId("landscape")
    val picture = Bitmap.createBitmap(1600, 1200, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(picture)
    canvas.drawPaint(Paint().apply { shader = LinearGradient(0f, 0f, 0f, 1200f, Color.rgb(40, 50, 110), Color.rgb(220, 110, 120), Shader.TileMode.CLAMP) })
    canvas.drawCircle(1150f, 640f, 110f, Paint().apply { color = Color.rgb(250, 200, 120) })
    for ((top, shade) in listOf(560f to Color.rgb(70, 50, 100), 760f to Color.rgb(40, 30, 60))) {
        val hills = Path().apply {
            moveTo(0f, top + 120)
            for (x in 0..1600 step 200) lineTo(x.toFloat(), top + if (x / 200 % 2 == 0) 120f else 0f)
            lineTo(1600f, 1200f)
            lineTo(0f, 1200f)
            close()
        }
        canvas.drawPath(hills, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = shade })
    }
    return added(id, picture)
}

/**
 * A see-through picture in the library's folder: a made-up logo, a red rounded square with a white
 * triangle in it, and beside it a white bar over a dark one, as a name in two colors would be;
 * nothing around them, so the tile's backdrop and focus show.
 */
fun seeThroughLogo(): PictureId {
    val picture = Bitmap.createBitmap(800, 450, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(picture)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    canvas.drawRoundRect(RectF(100f, 125f, 300f, 325f), 36f, 36f, paint.apply { color = Color.rgb(225, 20, 30) })
    val triangle = Path().apply {
        moveTo(170f, 175f)
        lineTo(250f, 225f)
        lineTo(170f, 275f)
        close()
    }
    canvas.drawPath(triangle, paint.apply { color = Color.WHITE })
    canvas.drawRoundRect(RectF(340f, 170f, 700f, 215f), 12f, 12f, paint.apply { color = Color.WHITE })
    canvas.drawRoundRect(RectF(340f, 240f, 620f, 280f), 12f, 12f, paint.apply { color = Color.rgb(30, 30, 50) })
    return added(PictureId("logo"), picture)
}

private fun added(id: PictureId, picture: Bitmap): PictureId {
    val folder = File(RuntimeEnvironment.getApplication().filesDir, "pictures").apply { mkdirs() }
    File(folder, id.value).outputStream().use { picture.compress(Bitmap.CompressFormat.PNG, 100, it) }
    return id
}
