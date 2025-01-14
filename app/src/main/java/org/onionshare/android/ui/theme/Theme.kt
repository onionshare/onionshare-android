package org.onionshare.android.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val DarkColorPalette = darkColorScheme(
    primary = PurpleOnionLight,
    secondary = PurpleOnionVariant,
    background = Color.Black,
    onPrimary = Color.White,
    error = Error,
)
private val LightColorPalette = lightColorScheme(
    primary = PurpleOnionDark,
    secondary = BlueLight,
    onPrimary = Color.White,
    error = Error,
)

val ColorScheme.OnionBlue: Color get() = if (isLight) BlueLight else BlueDark
val ColorScheme.OnionRed: Color get() = if (isLight) RedLight else RedDark
val ColorScheme.OnionAccent: Color get() = if (isLight) PurpleOnionDark else onBackground
val ColorScheme.topBar: Color get() = if (isLight) primary else Color.Black
val ColorScheme.Fab: Color get() = if (isLight) surface else Grey

val ColorScheme.isLight: Boolean get() = primary == PurpleOnionDark

@Composable
fun OnionshareTheme(darkTheme: Boolean = isSystemInDarkTheme(), content: @Composable () -> Unit) {
    val colors = if (darkTheme) {
        DarkColorPalette
    } else {
        LightColorPalette
    }
    MaterialTheme(
        colorScheme = colors,
        typography = Typography,
        shapes = Shapes,
        content = content,
    )
}
