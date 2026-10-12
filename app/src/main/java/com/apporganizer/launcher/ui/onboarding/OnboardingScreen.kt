package com.apporganizer.launcher.ui.onboarding

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apporganizer.launcher.ui.HomeViewModel
import com.apporganizer.launcher.ui.SystemIntents
import com.apporganizer.launcher.ui.UsageMode
import com.apporganizer.launcher.ui.components.RadioRow

private const val STEPS = 6

/** 六屏首次安装向导：欢迎 → 使用情况权限 → 扫描 → 布局 → 默认桌面 → 完成 */
@Composable
fun OnboardingScreen(vm: HomeViewModel) {
    var step by rememberSaveable { mutableIntStateOf(0) }
    BackHandler(enabled = step > 0) { step-- }

    Surface(Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(24.dp)
        ) {
            StepDots(step)
            Spacer(Modifier.height(24.dp))
            Column(
                Modifier
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                when (step) {
                    0 -> WelcomeStep()
                    1 -> UsageAccessStep(vm)
                    2 -> ScanStep(vm)
                    3 -> LayoutStep(vm)
                    4 -> DefaultHomeStep(vm)
                    else -> DoneStep()
                }
            }
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                if (step > 0) TextButton(onClick = { step-- }) { Text("Back") } else Spacer(Modifier)
                if (step < STEPS - 1) {
                    Button(onClick = { step++ }) { Text(if (step == 1 || step == 4) "Next / Skip" else "Next") }
                } else {
                    Button(onClick = vm::finishOnboarding) { Text("Get started") }
                }
            }
        }
    }
}

@Composable
private fun StepDots(step: Int) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(STEPS) { i ->
            Box(
                Modifier
                    .size(if (i == step) 10.dp else 8.dp)
                    .clip(CircleShape)
                    .background(if (i <= step) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
            )
        }
    }
}

@Composable
private fun Title(text: String) =
    Text(text, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)

@Composable
private fun Body(text: String) =
    Text(text, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.padding(top = 12.dp))

@Composable
private fun WelcomeStep() {
    Title("Welcome to Smart Launcher")
    Body("Keeps your most-used apps up front and sorts everything else into smart folders.")
    Body("• Your most-used apps sit at the top, refreshed once a day so icons don’t jump around\n• Apps are sorted into Work, Social, Finance, Media and more\n• Pin apps, change folders or turn off auto-sorting at any time\n• Everything stays on this phone — the app has no internet permission")
}

@Composable
private fun UsageAccessStep(vm: HomeViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Title("Usage Access (optional)")
    Body("To know which apps you use most, Smart Launcher reads:\n• how often each app is opened\n• when it was last used")
    Body("It never reads messages, passwords, files, photos, notifications or screen content. Data stays on this phone and is never uploaded.")
    Spacer(Modifier.height(16.dp))
    if (state.usageMode == UsageMode.SYSTEM) {
        Granted("Allowed")
    } else {
        Button(onClick = { SystemIntents.openUsageAccess(context) }) { Text("Open settings") }
        Text(
            "Find “Smart Launcher” in the list, turn it on, then come back here.\n\nIf you skip this, the launcher learns only from apps you open on it. Everything still works — it just takes a few days to get accurate.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun ScanStep(vm: HomeViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.scanForOnboarding() }
    Title("Your apps")
    Body("Found ${state.allApps.size} apps. Here’s how they’ll be sorted — you can change this any time:")
    Spacer(Modifier.height(12.dp))
    if (state.folders.isEmpty()) {
        CircularProgressIndicator()
    } else {
        state.folders.forEach { f ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("${f.name} (${f.apps.size})", style = MaterialTheme.typography.titleSmall)
                    Text(
                        f.apps.take(6).joinToString(", ") { it.label } + if (f.apps.size > 6) " …" else "",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }
        }
    }
}

@Composable
private fun LayoutStep(vm: HomeViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    Title("Choose a layout")
    Body("You can change this later in Settings.")
    Spacer(Modifier.height(12.dp))
    com.apporganizer.launcher.data.prefs.LayoutMode.entries.forEach { m ->
        RadioRow("${m.displayName} — ${m.description}", selected = state.settings.layoutMode == m) { vm.setLayout(m) }
    }
}

@Composable
private fun DefaultHomeStep(vm: HomeViewModel) {
    val roleHeld by vm.roleHeld.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { vm.onResume() }
    Title("Make it your home screen")
    Body("Then the Home button brings you here. One UI Home isn’t removed — you can switch back any time.")
    Spacer(Modifier.height(16.dp))
    if (roleHeld) {
        Granted("Set as home screen")
    } else {
        Button(onClick = {
            val intent = SystemIntents.homeRoleRequest(context)
            if (intent != null) launcher.launch(intent) else SystemIntents.openHomeSettings(context)
        }) { Text("Set as home screen") }
        Text(
            "You can skip this for now and open the app from your app list, then switch later in Settings.",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun DoneStep() {
    val context = LocalContext.current
    Title("You’re all set")
    Body("• Home button: back to the home screen; press again to close an open folder\n• Long-press an app: pin it, move it, add it to the Dock or exclude it from sorting\n• Long-press a folder: rename or delete it\n• Frequent apps refresh once a day (around 4 am)")
    Body("To go back to One UI Home:\n${SystemIntents.RESTORE_ONE_UI_STEPS}")
    Spacer(Modifier.height(12.dp))
    OutlinedButton(onClick = { SystemIntents.openHomeSettings(context) }) { Text("Open home screen settings") }
}

@Composable
private fun Granted(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
        Text(text, modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium)
    }
}
