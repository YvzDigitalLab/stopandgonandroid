package fr.yvz.stopandgo.ui.theme

import androidx.compose.ui.graphics.Color

data class ThemeColors(
    val menu: Color,
    val mode: Color,
    val previous: Color,
    val play: Color,
    val next: Color
)

object ColorThemes {
    val Red = ThemeColors(
        menu = Color(0xFFFFAD00),
        mode = Color(0xFFFB7700),
        previous = Color(0xFFDE3A08),
        play = Color(0xFFBC0000),
        next = Color(0xFFFB7700)
    )

    val Blue = ThemeColors(
        menu = Color(0xFF00CEFF),
        mode = Color(0xFF0099EA),
        previous = Color(0xFF0072C6),
        play = Color(0xFF003B8D),
        next = Color(0xFF0099EA)
    )

    val Green = ThemeColors(
        menu = Color(0xFF30EB1F),
        mode = Color(0xFF00C43D),
        previous = Color(0xFF0D9F00),
        play = Color(0xFF007B21),
        next = Color(0xFF00C43D)
    )

    val Grey = ThemeColors(
        menu = Color(0xFFC8CACC),
        mode = Color(0xFF939599),
        previous = Color(0xFF565759),
        play = Color(0xFF3D3E40),
        next = Color(0xFF939599)
    )

    fun forName(name: String): ThemeColors = when (name) {
        "Blue" -> Blue
        "Green" -> Green
        "Grey" -> Grey
        else -> Red
    }

    val allThemeNames = listOf("Grey", "Red", "Blue", "Green")
}
