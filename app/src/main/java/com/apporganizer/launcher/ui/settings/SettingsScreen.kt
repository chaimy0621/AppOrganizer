package com.apporganizer.launcher.ui.settings

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apporganizer.launcher.data.db.FolderEntity
import com.apporganizer.launcher.data.prefs.LayoutMode
import com.apporganizer.launcher.domain.Category
import com.apporganizer.launcher.ui.HomeViewModel
import com.apporganizer.launcher.ui.SystemIntents
import com.apporganizer.launcher.ui.UsageMode
import com.apporganizer.launcher.ui.components.FolderEditDialog
import com.apporganizer.launcher.ui.components.RadioRow

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: HomeViewModel, onBack: () -> Unit) {
    val state by vm.state.collectAsStateWithLifecycle()
    val roleHeld by vm.roleHeld.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var confirmReset by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf<FolderEntity?>(null) }
    var newFolder by remember { mutableStateOf("") }
    val roleLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.onResume() }

    BackHandler(onBack = onBack)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("设置") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "返回") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            SettingCard("使用数据来源") {
                if (state.usageMode == UsageMode.SYSTEM) {
                    Text("已授权“使用情况访问”。仅读取应用的启动次数和最后使用时间。")
                } else {
                    Text("内部学习模式：未授权“使用情况访问”，只根据你在本桌面内点击应用的次数学习。从其他入口（通知、分享等）打开的应用不会被统计。")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { SystemIntents.openUsageAccess(context) }) { Text("授予使用情况访问权限") }
                }
                Text(
                    "不会读取聊天、密码、文件、通知或屏幕内容。所有数据只保存在本机，本应用没有网络权限。",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            SettingCard("自动整理") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("每天自动调整常用应用")
                        Text("关闭后常用区保持当前顺序，不再变化。", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = state.settings.autoAdjust, onCheckedChange = vm::setAutoAdjust)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = vm::recomputeNow) { Text("立即重新整理常用应用") }
            }

            SettingCard("布局模式") {
                LayoutMode.entries.forEach { m ->
                    RadioRow("${m.displayName} — ${m.description}", selected = state.settings.layoutMode == m) { vm.setLayout(m) }
                }
            }

            SettingCard("文件夹") {
                state.moveTargets.forEach { f ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(f.name, Modifier.weight(1f))
                        IconButton(onClick = { editing = f }) { Icon(Icons.Filled.Edit, "编辑 ${f.name}") }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newFolder, onValueChange = { newFolder = it.take(20) },
                        label = { Text("新建文件夹") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                    TextButton(enabled = newFolder.isNotBlank(), onClick = { vm.createFolder(newFolder); newFolder = "" }) { Text("创建") }
                }
                Text("空文件夹不会显示在首页；长按应用 →「移动到文件夹」即可放入。", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = vm::rescan) { Text("重新扫描应用") }
                OutlinedButton(onClick = { confirmReset = true }) { Text("恢复默认分类") }
            }

            SettingCard("默认桌面") {
                Text(if (roleHeld) "当前默认桌面：智能桌面" else "当前默认桌面：其他（如 One UI Home）")
                Spacer(Modifier.height(8.dp))
                if (!roleHeld) {
                    Button(onClick = {
                        SystemIntents.homeRoleRequest(context)?.let { roleLauncher.launch(it) }
                            ?: SystemIntents.openHomeSettings(context)
                    }) { Text("设为默认桌面") }
                }
                OutlinedButton(onClick = { SystemIntents.openHomeSettings(context) }) { Text("切换回 One UI Home") }
                Text(SystemIntents.RESTORE_ONE_UI_STEPS, style = MaterialTheme.typography.bodySmall)
            }

            SettingCard("其他") {
                OutlinedButton(onClick = { vm.restartOnboarding(); onBack() }) { Text("重新运行安装向导") }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("恢复默认分类？") },
            text = { Text("将清除所有手动分类和自建文件夹，并重新自动分类。锁定位置、Dock 和使用记录不受影响。") },
            confirmButton = { TextButton(onClick = { vm.resetClassification(); confirmReset = false }) { Text("恢复") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("取消") } },
        )
    }

    editing?.let { f ->
        FolderEditDialog(
            initialName = f.name,
            deletable = f.category != Category.OTHER.name,
            onDismiss = { editing = null },
            onRename = { vm.renameFolder(f.id, it); editing = null },
            onDelete = { vm.deleteFolder(f.id); editing = null },
        )
    }
}

@Composable
private fun SettingCard(title: String, content: @Composable () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(16.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(bottom = 8.dp))
            content()
        }
    }
}
