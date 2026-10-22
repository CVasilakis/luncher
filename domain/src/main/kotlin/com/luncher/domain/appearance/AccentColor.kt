package com.luncher.domain.appearance

/**
 * The accent colors to choose from: each one a [vivid] color, the home screen's focus frame, and
 * its tones from dark ([tone10]) to light ([tone90]), which the menus and the settings take their
 * colors from ([colorRoles]), as Material's tonal palettes do. The tones are worked out once, with
 * Material's color utilities, and written here: nothing is computed on the TV. No red: it's the
 * color of deleting.
 */
enum class AccentColor(
    val vivid: Int,
    val tone10: Int,
    val tone20: Int,
    val tone30: Int,
    val tone40: Int,
    val tone80: Int,
    val tone90: Int,
) {
    ORANGE(0xFFFF7043.argb, 0xFF3A0B00.argb, 0xFF5E1700.argb, 0xFF852400.argb, 0xFFAB3500.argb, 0xFFFFB59D.argb, 0xFFFFDBD0.argb),
    PINK(0xFFE91E63.argb, 0xFF3F0019.argb, 0xFF65002D.argb, 0xFF8E0042.argb, 0xFFB80F58.argb, 0xFFFFB1C5.argb, 0xFFFFD9E1.argb),
    PURPLE(0xFF9575CD.argb, 0xFF24005A.argb, 0xFF3B1A87.argb, 0xFF53379E.argb, 0xFF6B51B8.argb, 0xFFCFBDFF.argb, 0xFFE9DDFF.argb),
    BLUE(0xFF4C8DF6.argb, 0xFF001A41.argb, 0xFF002E69.argb, 0xFF004494.argb, 0xFF005AC1.argb, 0xFFADC6FF.argb, 0xFFD8E2FF.argb),
    TEAL(0xFF00A896.argb, 0xFF00201C.argb, 0xFF003731.argb, 0xFF005048.argb, 0xFF006A60.argb, 0xFF53DBC9.argb, 0xFF74F8E5.argb),
    GREEN(0xFF43A047.argb, 0xFF002106.argb, 0xFF00390F.argb, 0xFF00531A.argb, 0xFF006E25.argb, 0xFF78DC77.argb, 0xFF94F990.argb),
    AMBER(0xFFFFB300.argb, 0xFF281900.argb, 0xFF432C00.argb, 0xFF604100.argb, 0xFF7F5700.argb, 0xFFF8BD2A.argb, 0xFFFFDEA6.argb),
    GRAY(0xFF9AA0A8.argb, 0xFF191C20.argb, 0xFF2E3135.argb, 0xFF44474C.argb, 0xFF5C5F64.argb, 0xFFC4C6CC.argb, 0xFFE0E2E8.argb),
}

private val Long.argb: Int get() = toInt()
