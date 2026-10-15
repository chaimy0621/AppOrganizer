package com.apporganizer.launcher.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apporganizer.launcher.data.AppRepository
import com.apporganizer.launcher.data.db.AppEntity
import kotlinx.coroutines.launch
import kotlin.math.abs
import kotlin.math.roundToInt

private val Highlight = Color(0xFF243052)
private val SlotLine = Color(0xFF3A3D47)
private val ICON = 54.dp

/**
 * Long-press an app and drag it into the pinned row to pin it.
 * Long-press a pinned app and drag it out to unpin it, or sideways to reorder.
 * Every change is saved immediately and the widget redraws.
 */
@Composable
fun EditPinnedScreen(repo: AppRepository, onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptics = LocalHapticFeedback.current
    val halfIconPx = with(LocalDensity.current) { (ICON / 2).toPx() }

    var all by remember { mutableStateOf<List<AppEntity>>(emptyList()) }
    val pinned = remember { mutableStateListOf<AppEntity>() }
    LaunchedEffect(Unit) {
        val p = repo.pinnedApps()
        pinned.clear()
        pinned.addAll(p)
        all = repo.recentApps()
    }
    val pinnedKeys = pinned.map { it.appKey }.toSet()
    val others = all.filter { it.appKey !in pinnedKeys }

    // Drag state (positions are in window-root coordinates)
    var dragApp by remember { mutableStateOf<AppEntity?>(null) }
    var dragFromPinned by remember { mutableStateOf(false) }
    var dragPos by remember { mutableStateOf(Offset.Zero) }
    var pinnedArea by remember { mutableStateOf(Rect.Zero) }
    val itemBounds = remember { HashMap<String, Rect>() }
    val slotBounds = remember { HashMap<Int, Rect>() }

    fun save() {
        val keys = pinned.map { it.appKey }
        scope.launch { repo.setPins(keys) }
    }

    fun drop() {
        val app = dragApp ?: return
        dragApp = null
        if (pinnedArea.contains(dragPos)) {
            val target = slotBounds.entries.minByOrNull { abs(it.value.center.x - dragPos.x) }?.key ?: pinned.size
            if (dragFromPinned) {
                val from = pinned.indexOfFirst { it.appKey == app.appKey }
                if (from < 0) return
                pinned.removeAt(from)
                pinned.add(target.coerceIn(0, pinned.size), app)
            } else {
                if (pinned.size >= AppRepository.MAX_PINS) {
                    Toast.makeText(context, "Pinned row is full. Drag one out first.", Toast.LENGTH_SHORT).show()
                    return
                }
                pinned.add(target.coerceIn(0, pinned.size), app)
            }
            save()
        } else if (dragFromPinned) {
            val from = pinned.indexOfFirst { it.appKey == app.appKey }
            if (from >= 0) {
                pinned.removeAt(from)
                save()
            }
        }
    }

    fun Modifier.draggableApp(app: AppEntity, fromPinned: Boolean): Modifier {
        val id = "${if (fromPinned) "p" else "a"}|${app.appKey}"
        return this
            .onGloballyPositioned { itemBounds[id] = it.boundsInRoot() }
            .pointerInput(id) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { offset ->
                        haptics.performHapticFeedback(HapticFeedbackType.LongPress)
                        val origin = itemBounds[id]?.topLeft ?: Offset.Zero
                        dragPos = origin + offset
                        dragFromPinned = fromPinned
                        dragApp = app
                    },
                    onDrag = { change, amount ->
                        change.consume()
                        dragPos += amount
                    },
                    onDragEnd = { drop() },
                    onDragCancel = { dragApp = null },
                )
            }
    }

    val overPinned = dragApp != null && pinnedArea.contains(dragPos)

    Box(Modifier.fillMaxSize()) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 18.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 18.dp)) {
                Text(
                    "Edit pinned", color = TextMain, fontSize = 24.sp, fontWeight = FontWeight.Medium,
                    modifier = Modifier.weight(1f),
                )
                Button(
                    onClick = onDone,
                    colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White),
                ) { Text("Done") }
            }
            Text(
                "Long-press an app and drag it up to pin it. Drag a pinned app down to unpin it.",
                color = TextDim, fontSize = 14.sp, lineHeight = 20.sp,
                modifier = Modifier.padding(top = 6.dp, bottom = 18.dp),
            )

            // Pinned row
            Column(
                Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(22.dp))
                    .background(if (overPinned) Highlight else Card)
                    .onGloballyPositioned { pinnedArea = it.boundsInRoot() }
                    .padding(vertical = 14.dp, horizontal = 4.dp),
            ) {
                SectionLabel("PINNED · ${pinned.size} OF ${AppRepository.MAX_PINS}", Modifier.padding(start = 10.dp, bottom = 10.dp))
                Row(Modifier.fillMaxWidth()) {
                    for (i in 0 until AppRepository.MAX_PINS) {
                        Box(
                            Modifier
                                .weight(1f)
                                .height(64.dp)
                                .onGloballyPositioned { slotBounds[i] = it.boundsInRoot() },
                            contentAlignment = Alignment.Center,
                        ) {
                            val app = pinned.getOrNull(i)
                            if (app != null) {
                                val lifted = dragApp?.appKey == app.appKey && dragFromPinned
                                Box(Modifier.draggableApp(app, true).alpha(if (lifted) 0.25f else 1f)) {
                                    AppIcon(app, ICON)
                                }
                            } else {
                                Box(
                                    Modifier
                                        .size(ICON)
                                        .border(1.5.dp, if (overPinned) Accent else SlotLine, RoundedCornerShape(16.dp))
                                )
                            }
                        }
                    }
                }
            }

            SectionLabel("ALL APPS", Modifier.padding(start = 6.dp, top = 22.dp, bottom = 12.dp))

            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                contentPadding = PaddingValues(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(20.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(others, key = { it.appKey }) { app ->
                    Box(Modifier.height(64.dp), contentAlignment = Alignment.Center) {
                        val lifted = dragApp?.appKey == app.appKey && !dragFromPinned
                        Box(Modifier.draggableApp(app, false).alpha(if (lifted) 0.25f else 1f)) {
                            AppIcon(app, ICON)
                        }
                    }
                }
            }
        }

        // The icon following your finger
        dragApp?.let { app ->
            Box(
                Modifier
                    .offset { IntOffset((dragPos.x - halfIconPx).roundToInt(), (dragPos.y - halfIconPx).roundToInt()) }
                    .border(2.dp, Accent, RoundedCornerShape(18.dp))
                    .padding(3.dp),
            ) {
                AppIcon(app, ICON)
            }
        }
    }
}

@Composable
private fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, color = TextDim, fontSize = 11.sp, letterSpacing = 1.sp, modifier = modifier)
}

