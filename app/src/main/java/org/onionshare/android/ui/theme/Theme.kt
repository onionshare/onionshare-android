package org.onionshare.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material.Colors
import androidx.compose.material.MaterialTheme
import androidx.compose.material.darkColors
import androidx.compose.material.lightColors
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorPalette = darkColors(
    primary = PurpleOnionLight,
    primaryVariant = PurpleOnionLight,
    secondary = PurpleOnionVariant,
    background = Color.Black,
    onPrimary = Color.White,
    error = Error,
)
private val LightColorPalette = lightColors(
    primary = PurpleOnionDark,
    primaryVariant = PurpleOnionDark,
    secondary = BlueLight,
    onPrimary = Color.White,
    error = Error,
)

val Colors.OnionBlue: Color get() = if (isLight) BlueLight else BlueDark
val Colors.OnionRed: Color get() = if (isLight) RedLight else RedDark
val Colors.OnionAccent: Color get() = if (isLight) PurpleOnionDark else onBackground
val Colors.topBar: Color get() = if (isLight) primary else Color.Black
val Colors.Fab: Color get() = if (isLight) surface else Grey

@Composable
fun OnionshareTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) {
        DarkColorPalette
    } else {
        LightColorPalette
    }

    MaterialTheme(
        colors = colors,
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}
