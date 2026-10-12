package com.apporganizer.launcher.ui.home

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
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
import com.apporganizer.launcher.ui.theme.OnWallpaperLabel

/** 图标出现的位置决定长按菜单内容。 */
sealed interface TileContext {
    val id: String
    data class Frequent(val slot: Int) : TileContext { override val id = "freq$slot" }
    data class InFolder(val folderId: Long) : TileContext { override val id = "folder$folderId" }
    data object Dock : TileContext { override val id = "dock" }
    data object Search : TileContext { override val id = "search" }
}

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
    // 作为 Home 应用，返回键永远不退出，只关闭浮层
    BackHandler(enabled = true) { closeAll() }

    val tile: @Composable (AppUi, TileContext, Modifier, Boolean) -> Unit = { app, ctx, mod, onWallpaper ->
        val anchor = "${ctx.id}|${app.key}"
        AppTile(
            app = app,
            labelStyle = if (onWallpaper) OnWallpaperLabel else MaterialTheme.typography.labelSmall,
            onClick = { vm.launch(app); menuAnchor = null },
            onLongClick = { menuAnchor = anchor },
            modifier = mod,
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
            .padding(horizontal = 12.dp)
    ) {
        TopBar(
            usageMode = state.usageMode,
            searching = searching,
            query = query,
            onQuery = { query = it },
            onToggleSearch = { searching = !searching; query = "" },
            onOpenSettings = onOpenSettings,
        )

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
        ) {
            if (searching) {
                val results = state.allApps.filter {
                    query.isNotBlank() && (it.label.contains(query, true) || it.packageName.contains(query, true))
                }
                GridRows(results, 4) { app, _ -> tile(app, TileContext.Search, Modifier, true) }
            } else {
                SectionTitle("常用")
                GridRows(state.frequent, 4) { app, i ->
                    if (app == null) EmptySlot() else tile(app, TileContext.Frequent(i), Modifier, true)
                }
                Spacer(Modifier.height(12.dp))
                SectionTitle("智能文件夹")
                val cols = if (state.settings.layoutMode == LayoutMode.FOLDERS_FIRST) 3 else 4
                GridRows(state.folders, cols) { folder, _ ->
                    FolderTile(
                        folder = folder,
                        onClick = { openFolderId = folder.id },
                        onLongClick = { if (folder.editable) editFolder = folder },
                    )
                }
            }
        }

        // Dock
        Row(
            Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp)
                .clip(RoundedCornerShape(24.dp))
                .background(Color(0x26FFFFFF))
                .padding(horizontal = 8.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
        ) {
            state.dock.forEach { app -> tile(app, TileContext.Dock, Modifier, true) }
            if (state.dock.isEmpty()) {
                Text("长按应用 →「添加到 Dock」", style = OnWallpaperLabel, modifier = Modifier.padding(16.dp))
            }
        }
    }

    // 文件夹内容
    val openFolder = state.folders.firstOrNull { it.id == openFolderId }
    if (openFolder != null) {
        ModalBottomSheet(onDismissRequest = { openFolderId = null; menuAnchor = null }) {
            Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        openFolder.name,
                        style = MaterialTheme.typography.titleLarge,
                        modifier = Modifier.weight(1f),
                    )
                    if (openFolder.editable) {
                        IconButton(onClick = { editFolder = openFolder }) {
                            Icon(Icons.Filled.Edit, contentDescription = "编辑文件夹")
                        }
                    }
                }
                if (openFolder.isRecent) {
                    Text(
                        "新安装的应用会在这里停留 7 天，之后自动进入对应分类。",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Spacer(Modifier.height(8.dp))
                GridRows(openFolder.apps, 4) { app, _ ->
                    tile(app, TileContext.InFolder(openFolder.id), Modifier, false)
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
private fun TopBar(
    usageMode: UsageMode,
    searching: Boolean,
    query: String,
    onQuery: (String) -> Unit,
    onToggleSearch: () -> Unit,
    onOpenSettings: () -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth().height(56.dp)) {
        if (searching) {
            OutlinedTextField(
                value = query,
                onValueChange = onQuery,
                placeholder = { Text("搜索应用") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                colors = TextFieldDefaults.colors(
                    focusedContainerColor = Color(0xCCFFFFFF),
                    unfocusedContainerColor = Color(0xCCFFFFFF),
                ),
                modifier = Modifier.weight(1f),
            )
            IconButton(onClick = onToggleSearch) { Icon(Icons.Filled.Close, "关闭搜索", tint = Color.White) }
        } else {
            if (usageMode == UsageMode.INTERNAL) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .clip(RoundedCornerShape(16.dp))
                        .background(Color(0x40000000))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Icon(Icons.Filled.Info, null, tint = Color.White, modifier = Modifier.size(14.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("内部学习模式", style = OnWallpaperLabel)
                }
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onToggleSearch) { Icon(Icons.Filled.Search, "搜索", tint = Color.White) }
            IconButton(onClick = onOpenSettings) { Icon(Icons.Filled.Settings, "设置", tint = Color.White) }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = OnWallpaperLabel.copy(fontWeight = FontWeight.SemiBold),
        modifier = Modifier.padding(start = 4.dp, top = 4.dp, bottom = 2.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FolderTile(folder: FolderUi, onClick: () -> Unit, onLongClick: () -> Unit) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(6.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .aspectRatio(1f)
                .clip(RoundedCornerShape(18.dp))
                .background(Color(0x40FFFFFF))
                .padding(8.dp)
        ) {
            Column(verticalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxSize()) {
                folder.apps.take(4).chunked(2).forEach { row ->
                    Row(horizontalArrangement = Arrangement.SpaceEvenly, modifier = Modifier.fillMaxWidth()) {
                        row.forEach { AppIcon(it, 22.dp) }
                        if (row.size == 1) Spacer(Modifier.size(22.dp))
                    }
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Text("${folder.name} · ${folder.apps.size}", style = OnWallpaperLabel, maxLines = 1)
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
            ctx is TileContext.Frequent && app.locked -> item("取消锁定") { vm.unlock(app) }
            ctx is TileContext.Frequent -> item("锁定在此位置") { vm.lockAt(app, ctx.slot) }
            app.locked -> item("取消常用区锁定") { vm.unlock(app) }
            else -> item("固定到常用区") { vm.pinToFrequent(app) }
        }
        item(if (app.excluded) "恢复参与自动排序" else "不参与自动排序") { vm.setExcluded(app, !app.excluded) }
        DropdownMenuItem(text = { Text("移动到文件夹…") }, onClick = onMove)
        if (app.inDock) item("从 Dock 移除") { vm.removeFromDock(app) } else item("添加到 Dock") { vm.addToDock(app) }
        item("应用信息") { openAppInfo(context, app.packageName) }
    }
}

private fun openAppInfo(context: Context, pkg: String) {
    context.startActivity(
        Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", pkg, null))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    )
}

/** 简单的等宽网格（项目数量少，不需要 Lazy）。 */
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
