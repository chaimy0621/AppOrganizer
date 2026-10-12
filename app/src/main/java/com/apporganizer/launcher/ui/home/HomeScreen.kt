package com.apporganizer.launcher.ui.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apporganizer.launcher.data.prefs.LayoutMode
import com.apporganizer.launcher.ui.AppUi
import com.apporganizer.launcher.ui.FolderUi
import com.apporganizer.launcher.ui.HomeViewModel
import com.apporganizer.launcher.ui.UsageMode
import com.apporganizer.launcher.ui.components.AppIcon
import com.apporganizer.launcher.ui.components.AppTile
import com.apporganizer.launcher.ui.components.EmptySlot
import com.apporganizer.launcher.ui.components.FolderEditDialog
import com.apporganizer.launcher.ui.components.MoveToFolderDialog
import com.apporganizer.launcher.ui.theme.ClockStyle
import com.apporganizer.launcher.ui.theme.DateStyle
import com.apporganizer.launcher.ui.theme.Glass
import com.apporganizer.launcher.ui.theme.GlassStrong
import com.apporganizer.launcher.ui.theme.OnWallpaperLabel
import com.apporganizer.launcher.ui.theme.OnWallpaperTitle
import kotlinx.coroutines.delay
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Where an icon appears decides what its long-press menu offers. */
sealed interface TileContext {
    val id: String
    data class Frequent(val slot: Int) : TileContext { override val id = "freq$slot" }
    data class InFolder(val folderId: Long) : TileContext { override val id = "folder$folderId" }
    data object Dock : TileContext { override val id = "dock" }
    data object Search : TileContext { override val id = "search" }
}

private val PanelShape = RoundedCornerShape(28.dp)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: HomeViewModel, onOpenSettings: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val homeSignal by vm.homeSignal.collectAsStateWithLifecycle()

    var openFolderId by rememberSaveable { mutableStateOf<Long?>(null) }
    var menuAnchor by remember { mutableStateOf<String?>(null) }   // "contextId|appKey"
    var moveApp by remember { mutableStateOf<AppUi?>(null) }
    var editFolder by remember { mutableStateOf<FolderUi?>(null) }
    var searching by rememberSaveable { mutableStateOf(false) }
    var query by rememberSaveable { mutableStateOf("") }

    fun closeAll() {
        openFolderId = null; menuAnchor = null; searching = false; query = ""
    }
    LaunchedEffect(homeSignal) { if (homeSignal > 0) closeAll() }
    // As the Home app, Back never exits — it only closes overlays.
    BackHandler(enabled = true) { closeAll() }

    val tile: @Composable (AppUi, TileContext, Boolean) -> Unit = { app, ctx, onWallpaper ->
        val anchor = "${ctx.id}|${app.key}"
        AppTile(
            app = app,
            labelStyle = if (onWallpaper) OnWallpaperLabel else MaterialTheme.typography.labelMedium,
            onClick = { vm.launch(app); menuAnchor = null },
            onLongClick = { menuAnchor = anchor },
            showLabel = ctx !is TileContext.Dock,
            menu = {
                AppMenu(
                    expanded = menuAnchor == anchor,
                    onDismiss = { menuAnchor = null },
                    app = app,
                    ctx = ctx,
                    vm = vm,
                    onMove = { menuAnchor = null; moveApp = app },
                )
            },
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp)
    ) {
        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            Spacer(Modifier.height(28.dp))
            ClockHeader(usageMode = state.usageMode)
            Spacer(Modifier.height(20.dp))

            SearchPill(
                searching = searching,
                query = query,
                onQuery = { query = it },
                onOpen = { searching = true },
                onClose = { searching = false; query = "" },
                onOpenSettings = onOpenSettings,
            )
            Spacer(Modifier.height(16.dp))

            if (searching) {
                val results = state.allApps.filter {
                    query.isNotBlank() && (it.label.contains(query, true) || it.packageName.contains(query, true))
                }
                Panel {
                    if (query.isBlank()) {
                        PanelHint("Type to search all ${state.allApps.size} apps")
                    } else if (results.isEmpty()) {
                        PanelHint("No apps match “$query”")
                    } else {
                        GridRows(results, 4) { app, _ -> tile(app, TileContext.Search, true) }
                    }
                }
            } else {
                Panel(title = "Frequently used") {
                    if (state.frequent.all { it == null }) {
                        PanelHint("Your most-used apps will appear here as you use your phone.")
                    } else {
                        GridRows(state.frequent, 4) { app, i ->
                            if (app == null) EmptySlot() else tile(app, TileContext.Frequent(i), true)
                        }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text("Folders", style = OnWallpaperTitle, modifier = Modifier.padding(start = 8.dp, bottom = 4.dp))
                val cols = if (state.settings.layoutMode == LayoutMode.FOLDERS_FIRST) 3 else 4
                val folderSize = if (cols == 3) 72.dp else 58.dp
                GridRows(state.folders, cols) { folder, _ ->
                    FolderTile(
                        folder = folder,
                        size = folderSize,
                        onClick = { openFolderId = folder.id },
                        onLongClick = { if (folder.editable) editFolder = folder },
                    )
                }
                Spacer(Modifier.height(16.dp))
            }
        }

        // Dock
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 8.dp, bottom = 12.dp)
                .clip(PanelShape)
                .background(Glass)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            state.dock.forEach { app -> tile(app, TileContext.Dock, true) }
            if (state.dock.isEmpty()) {
                Text("Long-press any app → Add to Dock", style = OnWallpaperLabel, modifier = Modifier.padding(18.dp))
            }
        }
    }

    // Folder contents
    val openFolder = state.folders.firstOrNull { it.id == openFolderId }
    if (openFolder != null) {
        ModalBottomSheet(onDismissRequest = { openFolderId = null; menuAnchor = null }) {
            Column(Modifier.padding(horizontal = 20.dp).padding(bottom = 28.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(openFolder.name, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            if (openFolder.isRecent) "Installed in the last 7 days" else "${openFolder.apps.size} apps",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (openFolder.editable) {
                        IconButton(onClick = { editFolder = openFolder }) {
                            Icon(Icons.Filled.Edit, contentDescription = "Edit folder")
                        }
                    }
                }
                Spacer(Modifier.height(16.dp))
                GridRows(openFolder.apps, 4) { app, _ ->
                    tile(app, TileContext.InFolder(openFolder.id), false)
                }
            }
        }
    }

    moveApp?.let { app ->
        MoveToFolderDialog(
            app = app,
            targets = state.moveTargets,
            onDismiss = { moveApp = null },
            onPick = { vm.moveToFolder(app, it); moveApp = null },
            onCreateAndMove = { vm.moveToNewFolder(app, it); moveApp = null },
        )
    }

    editFolder?.let { f ->
        FolderEditDialog(
            initialName = f.name,
            deletable = f.deletable,
            onDismiss = { editFolder = null },
            onRename = { vm.renameFolder(f.id, it); editFolder = null },
            onDelete = { vm.deleteFolder(f.id); editFolder = null; openFolderId = null },
        )
    }
}

@Composable
private fun ClockHeader(usageMode: UsageMode) {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(15_000)
        }
    }
    Column(Modifier.padding(start = 8.dp)) {
        Text(now.format(DateTimeFormatter.ofPattern("HH:mm")), style = ClockStyle)
        Text(now.format(DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)), style = DateStyle)
        if (usageMode == UsageMode.INTERNAL) {
            Text(
                "Learning from your taps · enable Usage Access in Settings for better results",
                style = OnWallpaperLabel.copy(color = Color(0xCCFFFFFF)),
                modifier = Modifier.padding(top = 6.dp),
            )
        }
    }
}

@Composable
private fun SearchPill(
    searching: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    onOpen: () -> Unit,
    onClose: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    val focus = remember { FocusRequester() }
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .height(52.dp)
            .clip(PanelShape)
            .background(GlassStrong)
            .clickable(enabled = !searching, onClick = onOpen)
            .padding(start = 18.dp, end = 4.dp),
    ) {
        Icon(Icons.Outlined.Search, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.width(12.dp))
        Box(Modifier.weight(1f)) {
            if (searching) {
                LaunchedEffect(Unit) { focus.requestFocus() }
                BasicTextField(
                    value = query,
                    onValueChange = onQuery,
                    singleLine = true,
                    textStyle = TextStyle(color = Color.White, fontSize = 16.sp),
                    cursorBrush = SolidColor(Color.White),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
                if (query.isEmpty()) Text("Search apps", color = Color(0x99FFFFFF), fontSize = 16.sp)
            } else {
                Text("Search apps", color = Color(0xCCFFFFFF), fontSize = 16.sp)
            }
        }
        if (searching) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close search", tint = Color.White) }
        } else {
            IconButton(onClick = onOpenSettings) { Icon(Icons.Outlined.Settings, "Settings", tint = Color.White) }
        }
    }
}

@Composable
private fun Panel(title: String? = null, content: @Composable () -> Unit) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(PanelShape)
            .background(Glass)
            .padding(horizontal = 8.dp, vertical = 14.dp)
    ) {
        if (title != null) {
            Text(title, style = OnWallpaperTitle, modifier = Modifier.padding(start = 12.dp, bottom = 6.dp))
        }
        content()
    }
}

@Composable
private fun PanelHint(text: String) {
    Text(
        text,
        style = OnWallpaperLabel.copy(fontSize = 14.sp, color = Color(0xCCFFFFFF)),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 16.dp),
    )
}

/** One UI–style folder: a rounded glass square with a 2×2 icon preview, label underneath. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderTile(folder: FolderUi, size: androidx.compose.ui.unit.Dp, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(vertical = 8.dp, horizontal = 2.dp),
    ) {
        val mini = size * 0.36f
        Box(
            Modifier
                .size(size)
                .clip(RoundedCornerShape(size * 0.3f))
                .background(Color(0x40FFFFFF)),
            contentAlignment = Alignment.Center,
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(size * 0.06f)) {
                folder.apps.take(4).chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(size * 0.06f)) {
                        row.forEach { AppIcon(it, mini) }
                        if (row.size == 1) Spacer(Modifier.size(mini))
                    }
                }
            }
        }
        Spacer(Modifier.height(6.dp))
        Text(folder.name, style = OnWallpaperLabel, maxLines = 1)
    }
}

@Composable
private fun AppMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    app: AppUi,
    ctx: TileContext,
    vm: HomeViewModel,
    onMove: () -> Unit,
) {
    val context = LocalContext.current
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        @Composable
        fun item(text: String, action: () -> Unit) =
            DropdownMenuItem(text = { Text(text) }, onClick = { action(); onDismiss() })

        when {
            ctx is TileContext.Frequent && app.locked -> item("Unpin") { vm.unlock(app) }
            ctx is TileContext.Frequent -> item("Pin to this spot") { vm.lockAt(app, ctx.slot) }
            app.locked -> item("Unpin from Frequent") { vm.unlock(app) }
            else -> item("Pin to Frequent") { vm.pinToFrequent(app) }
        }
        item(if (app.excluded) "Include in auto-sorting" else "Exclude from auto-sorting") { vm.setExcluded(app, !app.excluded) }
        DropdownMenuItem(text = { Text("Move to folder…") }, onClick = onMove)
        if (app.inDock) item("Remove from Dock") { vm.removeFromDock(app) } else item("Add to Dock") { vm.addToDock(app) }
        item("App info") { openAppInfo(context, app.packageName) }
    }
}

private fun openAppInfo(context: Context, pkg: String) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

/** Simple equal-width grid (item counts are small, no Lazy needed). */
@Composable
fun <T> GridRows(items: List<T>, columns: Int, cell: @Composable (T, Int) -> Unit) {
    items.chunked(columns).forEachIndexed { rowIndex, row ->
        Row(Modifier.fillMaxWidth()) {
            row.forEachIndexed { colIndex, item ->
                Box(Modifier.weight(1f), contentAlignment = Alignment.TopCenter) {
                    cell(item, rowIndex * columns + colIndex)
                }
            }
            repeat(columns - row.size) { Spacer(Modifier.weight(1f)) }
        }
    }
}
