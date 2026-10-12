package com.apporganizer.launcher.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.apporganizer.launcher.data.system.IconLoader

val LocalIconLoader = staticCompositionLocalOf<IconLoader> { error("IconLoader not provided") }

/** Frosted dark glass used for panels on top of the wallpaper. Works on light and dark wallpapers. */
val Glass = Color(0x47000000)
val GlassStrong = Color(0x66000000)

private val WallpaperShadow = Shadow(color = Color(0x80000000), blurRadius = 8f)

/** Text drawn directly on the wallpaper: white with a soft shadow. */
val OnWallpaperLabel = TextStyle(color = Color.White, fontSize = 12.sp, shadow = WallpaperShadow)
val OnWallpaperTitle = TextStyle(
    color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold,
    letterSpacing = 0.2.sp, shadow = WallpaperShadow,
)
val ClockStyle = TextStyle(
    color = Color.White, fontSize = 64.sp, fontWeight = FontWeight.Light,
    letterSpacing = (-1).sp, shadow = WallpaperShadow,
)
val DateStyle = TextStyle(color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Medium, shadow = WallpaperShadow)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S ->
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, content = content)
}
