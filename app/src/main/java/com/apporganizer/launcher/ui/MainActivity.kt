package com.apporganizer.launcher.ui

import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.apporganizer.launcher.App
import com.apporganizer.launcher.data.db.AppEntity
import com.apporganizer.launcher.ui.theme.LocalIconLoader
import com.apporganizer.launcher.widget.RecentAppsWidget

private val Bg = Color(0xFF121318)
private val Card = Color(0xFF1C1E25)
private val TextMain = Color(0xFFF2F2F4)
private val TextDim = Color(0xFF9A9CA5)
private val Accent = Color(0xFF4C7DFF)

/** Two screens: setup (from the app list) and search (from the widget's search button). */
class MainActivity : ComponentActivity() {
    private val container by lazy { (application as App).container }
    private var searchMode by mutableStateOf(false)
    private var hasUsageAccess by mutableStateOf(false)
    private var widgetPlaced by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        searchMode = intent?.action == ACTION_SEARCH
        setContent {
            CompositionLocalProvider(LocalIconLoader provides container.iconLoader) {
                Box(Modifier.fillMaxSize().background(Bg)) {
                    if (searchMode) {
                        SearchScreen(onLaunch = ::launch, onClose = ::finish)
                    } else {
                        SetupScreen(
                            hasUsageAccess = hasUsageAccess,
                            widgetPlaced = widgetPlaced,
                            onAllowUsage = { SystemIntents.openUsageAccess(this@MainActivity) },
                            onAddWidget = ::requestWidget,
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        searchMode = intent.action == ACTION_SEARCH
    }

    override fun onResume() {
        super.onResume()
        hasUsageAccess = container.repository.hasUsageAccess()
        widgetPlaced = RecentAppsWidget.isPlaced(this)
        RecentAppsWidget.refresh(this)
    }

    private fun launch(app: AppEntity) {
        if (container.repository.launchComponent(app.packageName, app.className)) finish()
        else Toast.makeText(this, "Can't open ${app.label}", Toast.LENGTH_SHORT).show()
    }

    private fun requestWidget() {
        val manager = AppWidgetManager.getInstance(this)
        val ok = manager.isRequestPinAppWidgetSupported &&
            manager.requestPinAppWidget(ComponentName(this, RecentAppsWidget::class.java), null, null)
        if (!ok) Toast.makeText(this, "Long-press your home screen → Widgets → Smart Launcher", Toast.LENGTH_LONG).show()
    }

    @Composable
    private fun SearchScreen(onLaunch: (AppEntity) -> Unit, onClose: () -> Unit) {
        val apps by produceState(emptyList<AppEntity>()) { value = container.repository.recentApps() }
        var query by remember { mutableStateOf("") }
        val focus = remember { FocusRequester() }
        val shown = if (query.isBlank()) apps else apps.filter {
            it.label.contains(query.trim(), ignoreCase = true)
        }

        Column(Modifier.fillMaxSize().statusBarsPadding().imePadding()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .padding(16.dp)
                    .fillMaxWidth()
                    .height(52.dp)
                    .clip(RoundedCornerShape(26.dp))
                    .background(Card)
                    .padding(start = 18.dp, end = 4.dp),
            ) {
                Icon(Icons.Outlined.Search, null, tint = TextDim, modifier = Modifier.size(22.dp))
                Spacer(Modifier.width(12.dp))
                Box(Modifier.weight(1f)) {
                    BasicTextField(
                        value = query,
                        onValueChange = { query = it },
                        singleLine = true,
                        textStyle = TextStyle(color = TextMain, fontSize = 17.sp),
                        cursorBrush = SolidColor(Accent),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                        keyboardActions = androidx.compose.foundation.text.KeyboardActions(
                            onGo = { shown.firstOrNull()?.let(onLaunch) },
                        ),
                        modifier = Modifier.fillMaxWidth().focusRequester(focus),
                    )
                    if (query.isEmpty()) Text("Search apps", color = TextDim, fontSize = 17.sp)
                }
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close", tint = TextDim) }
            }
            LaunchedEffect(Unit) { focus.requestFocus() }

            LazyVerticalGrid(
                columns = GridCells.Fixed(5),
                contentPadding = PaddingValues(start = 12.dp, end = 12.dp, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.weight(1f),
            ) {
                items(shown, key = { it.appKey }) { app ->
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .clip(RoundedCornerShape(14.dp))
                            .clickable { onLaunch(app) }
                            .padding(vertical = 6.dp),
                    ) {
                        AppIcon(app, 50.dp)
                        Spacer(Modifier.height(6.dp))
                        Text(
                            app.label, color = TextMain, fontSize = 12.sp, maxLines = 1,
                            overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }
    }

    companion object {
        const val ACTION_SEARCH = "com.apporganizer.launcher.SEARCH"
    }
}

@Composable
private fun AppIcon(app: AppEntity, size: Dp) {
    val loader = LocalIconLoader.current
    val bmp: ImageBitmap? by produceState(loader.cached(app.appKey), app.appKey) {
        if (value == null) value = loader.load(app.packageName, app.className, app.appKey)
    }
    val b = bmp
    if (b != null) Image(b, app.label, Modifier.size(size))
    else Box(Modifier.size(size).clip(RoundedCornerShape(size * 0.3f)).background(Card))
}

@Composable
private fun SetupScreen(
    hasUsageAccess: Boolean,
    widgetPlaced: Boolean,
    onAllowUsage: () -> Unit,
    onAddWidget: () -> Unit,
) {
    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding()
            .navigationBarsPadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = 20.dp, vertical = 28.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text("Smart Launcher", color = TextMain, fontSize = 30.sp, fontWeight = FontWeight.Medium)
        Text(
            "A home screen widget with all your apps, most recently used at the top. Your One UI home screen stays exactly as it is.",
            color = TextDim, fontSize = 15.sp, lineHeight = 22.sp,
        )
        Spacer(Modifier.height(6.dp))

        StepCard(
            number = 1,
            done = hasUsageAccess,
            title = "Allow Usage Access",
            body = "Lets the widget see when you last opened each app. It never reads messages, files or screen content, and nothing leaves your phone.",
            button = if (hasUsageAccess) null else "Allow",
            onClick = onAllowUsage,
            note = if (hasUsageAccess) null else "Without it, only apps you open from the widget move to the top.",
        )

        StepCard(
            number = 2,
            done = widgetPlaced,
            title = "Add the widget",
            body = "Tap Add, then choose where to place it. Swipe to an empty page on your home screen first if you want it on its own page.",
            button = if (widgetPlaced) "Add another" else "Add",
            onClick = onAddWidget,
        )

        StepCard(
            number = 3,
            done = false,
            title = "Make it full page",
            body = "Long-press the widget, then drag the blue edges until it fills the page.",
            button = null,
            onClick = {},
        )

        Spacer(Modifier.height(8.dp))
        Text(
            "To remove it, long-press the widget → Remove.",
            color = TextDim, fontSize = 13.sp,
        )
    }
}

@Composable
private fun StepCard(
    number: Int,
    done: Boolean,
    title: String,
    body: String,
    button: String?,
    onClick: () -> Unit,
    note: String? = null,
) {
    Column(
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(24.dp))
            .background(Card)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            if (done) {
                Icon(Icons.Filled.CheckCircle, null, tint = Accent, modifier = Modifier.size(28.dp))
            } else {
                Box(
                    Modifier.size(28.dp).clip(CircleShape).background(Color(0xFF2A2D36)),
                    contentAlignment = Alignment.Center,
                ) { Text("$number", color = TextMain, fontSize = 14.sp) }
            }
            Spacer(Modifier.width(12.dp))
            Text(title, color = TextMain, fontSize = 17.sp, fontWeight = FontWeight.Medium)
        }
        Spacer(Modifier.height(10.dp))
        Text(body, color = TextDim, fontSize = 14.sp, lineHeight = 20.sp)
        if (button != null) {
            Spacer(Modifier.height(14.dp))
            Button(
                onClick = onClick,
                colors = ButtonDefaults.buttonColors(containerColor = Accent, contentColor = Color.White),
            ) { Text(button) }
        }
        if (note != null) {
            Spacer(Modifier.height(10.dp))
            Text(note, color = TextDim, fontSize = 12.sp)
        }
    }
}
