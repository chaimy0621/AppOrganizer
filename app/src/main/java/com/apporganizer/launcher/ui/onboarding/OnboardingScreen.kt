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
                if (step > 0) TextButton(onClick = { step-- }) { Text("上一步") } else Spacer(Modifier)
                if (step < STEPS - 1) {
                    Button(onClick = { step++ }) { Text(if (step == 1 || step == 4) "下一步 / 跳过" else "下一步") }
                } else {
                    Button(onClick = vm::finishOnboarding) { Text("开始使用") }
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
    Title("欢迎使用智能桌面")
    Body("自动整理常用 App，并建立智能文件夹。")
    Body("• 首页顶部显示你最常用的应用，每天整理一次，不会频繁跳动\n• 应用自动归入工作、通讯、金融、影音等文件夹\n• 可以随时锁定位置、修改分类或关闭自动调整\n• 所有数据只保存在这部手机上，本应用没有网络权限")
}

@Composable
private fun UsageAccessStep(vm: HomeViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    Title("使用情况访问（可选）")
    Body("为了知道哪些应用最常用，需要读取：\n• 每个应用的启动次数\n• 最后使用时间")
    Body("不会读取：聊天内容、密码、文件、照片、通知或屏幕内容。数据只保存在本机，不会上传。")
    Spacer(Modifier.height(16.dp))
    if (state.usageMode == UsageMode.SYSTEM) {
        Granted("已授权")
    } else {
        Button(onClick = { SystemIntents.openUsageAccess(context) }) { Text("前往授权") }
        Text(
            "在列表中找到「智能桌面」并打开开关，然后返回这里。\n\n如果拒绝：将使用“内部学习模式”，只根据你在本桌面里点击应用的次数学习，功能照常可用，只是需要几天才会准确。",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun ScanStep(vm: HomeViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    LaunchedEffect(Unit) { vm.scanForOnboarding() }
    Title("扫描应用")
    Body("已找到 ${state.allApps.size} 个可启动应用，建议分类如下（之后可随时修改）：")
    Spacer(Modifier.height(12.dp))
    if (state.folders.isEmpty()) {
        CircularProgressIndicator()
    } else {
        state.folders.forEach { f ->
            Card(Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                Column(Modifier.padding(12.dp)) {
                    Text("${f.name}（${f.apps.size}）", style = MaterialTheme.typography.titleSmall)
                    Text(
                        f.apps.take(6).joinToString("、") { it.label } + if (f.apps.size > 6) " …" else "",
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
    Title("选择布局")
    Body("可以稍后在设置中修改。")
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
    Title("设为默认桌面")
    Body("设为默认桌面后，按 Home 键就会回到智能桌面。One UI Home 不会被删除，随时可以切换回去。")
    Spacer(Modifier.height(16.dp))
    if (roleHeld) {
        Granted("已设为默认桌面")
    } else {
        Button(onClick = {
            val intent = SystemIntents.homeRoleRequest(context)
            if (intent != null) launcher.launch(intent) else SystemIntents.openHomeSettings(context)
        }) { Text("设为默认桌面") }
        Text(
            "也可以先跳过：本应用仍可从应用列表中打开，之后再到设置中切换。",
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.padding(top = 12.dp),
        )
    }
}

@Composable
private fun DoneStep() {
    val context = LocalContext.current
    Title("完成")
    Body("• 按 Home 键：回到首页；已在首页时再按一次会关闭打开的文件夹\n• 长按应用：锁定位置、移动文件夹、加入 Dock、不参与排序\n• 长按文件夹：改名或删除\n• 常用应用每天整理一次（约凌晨 4 点）")
    Body("如何恢复 One UI Home：\n${SystemIntents.RESTORE_ONE_UI_STEPS}")
    Spacer(Modifier.height(12.dp))
    OutlinedButton(onClick = { SystemIntents.openHomeSettings(context) }) { Text("打开默认桌面设置") }
}

@Composable
private fun Granted(text: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(Icons.Filled.CheckCircle, null, tint = MaterialTheme.colorScheme.primary)
        Text(text, modifier = Modifier.padding(start = 8.dp), style = MaterialTheme.typography.titleMedium)
    }
}
