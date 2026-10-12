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
                title = { Text("Settings") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") }
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
            SettingCard("Usage data") {
                if (state.usageMode == UsageMode.SYSTEM) {
                    Text("Usage Access is on. Only app launch counts and last-used times are read.")
                } else {
                    Text("Learning from your taps: Usage Access is off, so only apps you open from this launcher are counted. Apps opened from notifications or links aren’t counted.")
                    Spacer(Modifier.height(8.dp))
                    Button(onClick = { SystemIntents.openUsageAccess(context) }) { Text("Allow Usage Access") }
                }
                Text(
                    "Never reads messages, passwords, files, notifications or screen content. Everything stays on this phone — the app has no internet permission.",
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }

            SettingCard("Auto-sorting") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Update frequent apps daily")
                        Text("When off, Frequently used keeps its current order.", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = state.settings.autoAdjust, onCheckedChange = vm::setAutoAdjust)
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = vm::recomputeNow) { Text("Refresh frequent apps now") }
            }

            SettingCard("Layout") {
                LayoutMode.entries.forEach { m ->
                    RadioRow("${m.displayName} — ${m.description}", selected = state.settings.layoutMode == m) { vm.setLayout(m) }
                }
            }

            SettingCard("Folders") {
                state.moveTargets.forEach { f ->
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(f.name, Modifier.weight(1f))
                        IconButton(onClick = { editing = f }) { Icon(Icons.Filled.Edit, "Edit ${f.name}") }
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    OutlinedTextField(
                        value = newFolder, onValueChange = { newFolder = it.take(20) },
                        label = { Text("New folder") }, singleLine = true, modifier = Modifier.weight(1f),
                    )
                    TextButton(enabled = newFolder.isNotBlank(), onClick = { vm.createFolder(newFolder); newFolder = "" }) { Text("Create") }
                }
                Text("Empty folders are hidden on the home screen. Long-press an app → Move to folder.", style = MaterialTheme.typography.bodySmall)
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = vm::rescan) { Text("Rescan apps") }
                OutlinedButton(onClick = { confirmReset = true }) { Text("Restore default categories") }
            }

            SettingCard("Default home screen") {
                Text(if (roleHeld) "Smart Launcher is your home screen." else "Another launcher (e.g. One UI Home) is your home screen.")
                Spacer(Modifier.height(8.dp))
                if (!roleHeld) {
                    Button(onClick = {
                        SystemIntents.homeRoleRequest(context)?.let { roleLauncher.launch(it) }
                            ?: SystemIntents.openHomeSettings(context)
                    }) { Text("Set as home screen") }
                }
                OutlinedButton(onClick = { SystemIntents.openHomeSettings(context) }) { Text("Switch back to One UI Home") }
                Text(SystemIntents.RESTORE_ONE_UI_STEPS, style = MaterialTheme.typography.bodySmall)
            }

            SettingCard("More") {
                OutlinedButton(onClick = { vm.restartOnboarding(); onBack() }) { Text("Run setup again") }
            }
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Restore default categories?") },
            text = { Text("Removes your manual folder choices and custom folders, then re-sorts all apps. Pins, Dock and usage history are kept.") },
            confirmButton = { TextButton(onClick = { vm.resetClassification(); confirmReset = false }) { Text("Restore") } },
            dismissButton = { TextButton(onClick = { confirmReset = false }) { Text("Cancel") } },
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
