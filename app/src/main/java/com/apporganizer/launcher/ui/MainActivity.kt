package com.apporganizer.launcher.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import android.widget.Toast
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.apporganizer.launcher.ui.home.HomeScreen
import com.apporganizer.launcher.ui.onboarding.OnboardingScreen
import com.apporganizer.launcher.ui.settings.SettingsScreen
import com.apporganizer.launcher.ui.theme.AppTheme
import com.apporganizer.launcher.ui.theme.LocalIconLoader

class MainActivity : ComponentActivity() {
    private val vm: HomeViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
        )
        super.onCreate(savedInstanceState)
        setContent {
            CompositionLocalProvider(LocalIconLoader provides vm.iconLoader) {
                AppTheme {
                    val state by vm.state.collectAsStateWithLifecycle()
                    var showSettings by rememberSaveable { mutableStateOf(false) }
                    when {
                        !state.loaded -> Unit // 首帧前不闪烁向导
                        !state.settings.onboardingDone -> OnboardingScreen(vm)
                        showSettings -> SettingsScreen(vm, onBack = { showSettings = false })
                        else -> HomeScreen(vm, onOpenSettings = { showSettings = true })
                    }
                    val homeSignal by vm.homeSignal.collectAsStateWithLifecycle()
                    LaunchedEffect(homeSignal) { if (homeSignal > 0) showSettings = false }
                    val message by vm.message.collectAsStateWithLifecycle()
                    LaunchedEffect(message) {
                        message?.let {
                            Toast.makeText(this@MainActivity, it, Toast.LENGTH_SHORT).show()
                            vm.consumeMessage()
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        vm.onResume()
    }

    /** 已在首页时再次按 Home 键：关闭文件夹 / 设置页，回到首页初始状态。 */
    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        if (intent.hasCategory(Intent.CATEGORY_HOME)) vm.onHomePressed()
    }
}
