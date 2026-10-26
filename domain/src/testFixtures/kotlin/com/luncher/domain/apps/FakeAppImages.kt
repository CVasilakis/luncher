package com.luncher.domain.apps

/** [AppImages] that draw with [draw], counting what they draw: [rendered] says which app, which image, at which size. */
class FakeAppImages<Image>(private val draw: (HomeApp, Banner.Own, Int, Int) -> Image) : AppImages<Image> {

    /** A call of [render]. */
    data class Rendered(val app: LaunchableApp, val banner: Banner.Own, val width: Int, val height: Int)

    /** Every image [render] drew, in order. */
    val rendered = mutableListOf<Rendered>()

    override fun render(app: HomeApp, banner: Banner.Own, width: Int, height: Int): Image {
        rendered += Rendered(app.launchable, banner, width, height)
        return draw(app, banner, width, height)
    }
}
