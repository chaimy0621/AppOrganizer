package com.apporganizer.launcher.ui.components

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.apporganizer.launcher.ui.AppUi
import com.apporganizer.launcher.ui.theme.LocalIconLoader

@Composable
fun AppIcon(app: AppUi, size: Dp, modifier: Modifier = Modifier) {
    val loader = LocalIconLoader.current
    val bitmap: ImageBitmap? by produceState(loader.cached(app.key), app.key) {
        if (value == null) value = loader.load(app.packageName, app.className, app.key)
    }
    val bmp = bitmap
    if (bmp != null) {
        Image(bitmap = bmp, contentDescription = app.label, modifier = modifier.size(size))
    } else {
        Box(modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(Color(0x33FFFFFF)))
    }
}

/** App icon with label. Tap to open, long-press for the action menu (supplied by caller). */
@OptIn(ExperimentalFoundationApi::class)
@Composable
fun AppTile(
    app: AppUi,
    labelStyle: TextStyle,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    iconSize: Dp = 54.dp,
    showLabel: Boolean = true,
    menu: @Composable () -> Unit = {},
) {
    Box(modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier
                .clip(RoundedCornerShape(16.dp))
                .combinedClickable(onClick = onClick, onLongClick = onLongClick, onLongClickLabel = "More options")
                .padding(vertical = 6.dp, horizontal = 2.dp),
        ) {
            Box {
                AppIcon(app, iconSize)
                if (app.locked) {
                    Icon(
                        Icons.Filled.PushPin, contentDescription = "Pinned",
                        tint = Color.White,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .offset(x = 4.dp, y = (-4).dp)
                            .size(18.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF3D7BFF))
                            .padding(3.dp),
                    )
                }
            }
            if (showLabel) {
                Spacer(Modifier.height(6.dp))
                Text(
                    app.label,
                    style = labelStyle,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    textAlign = TextAlign.Center,
                    modifier = Modifier.width(iconSize + 18.dp),
                )
            }
        }
        menu()
    }
}

/** Invisible placeholder that keeps the grid stable when a frequent slot is empty. */
@Composable
fun EmptySlot(modifier: Modifier = Modifier, iconSize: Dp = 54.dp) {
    Spacer(modifier.padding(vertical = 6.dp).size(iconSize))
}
