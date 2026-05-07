package com.refsix.wear.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.wear.compose.material.MaterialTheme
import androidx.wear.compose.material.Colors

val RefGreen = Color(0xFF4CAF50)
val RefYellow = Color(0xFFFFC107)
val RefRed = Color(0xFFF44336)
val RefOrange = Color(0xFFFF9800)
val RefBlue = Color(0xFF2196F3)
val RefSurface = Color(0xFF1E1E1E)
val RefBackground = Color(0xFF000000)

private val Ref6Colors = Colors(
    primary = RefGreen,
    primaryVariant = Color(0xFF388E3C),
    secondary = RefBlue,
    secondaryVariant = Color(0xFF1565C0),
    background = RefBackground,
    surface = RefSurface,
    error = RefRed,
    onPrimary = Color.Black,
    onSecondary = Color.White,
    onBackground = Color.White,
    onSurface = Color.White,
    onError = Color.White
)

@Composable
fun Ref6Theme(content: @Composable () -> Unit) {
    MaterialTheme(
        colors = Ref6Colors,
        content = content
    )
}
