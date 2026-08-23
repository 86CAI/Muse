package com.caipan.music.ui.onboarding

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.LibraryMusic
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import com.caipan.music.ui.theme.MuseDesign
import com.caipan.music.ui.theme.MuseMotion
import com.caipan.music.viewmodel.MusicViewModel
import com.caipan.music.viewmodel.UiStyle

/** 引导完成标记，落 `muse_prefs`，仅首次启动展示。 */
object OnboardingPrefs {
    private const val PREFS = "muse_prefs"
    private const val KEY = "onboarding_complete"

    fun isComplete(context: Context): Boolean =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean(KEY, false)

    fun markComplete(context: Context) {
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean(KEY, true).apply()
    }
}

private const val STEP_COUNT = 7

private fun notificationGranted(context: Context): Boolean =
    Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

@Composable
fun OnboardingScreen(viewModel: MusicViewModel = viewModel(), onFinished: () -> Unit) {
    val appContext = LocalContext.current.applicationContext
    val prefs = remember { appContext.getSharedPreferences("muse_prefs", 0) }
    val zh = remember { (prefs.getString("app_language", "zh") ?: "zh") == "zh" }
    val uiState by viewModel.uiState.collectAsState()
    val accent = MaterialTheme.colorScheme.primary

    var step by rememberSaveable { mutableIntStateOf(0) }
    var lanRemote by remember { mutableStateOf(false) }
    var notifEnabled by remember { mutableStateOf(notificationGranted(appContext)) }

    val audioPermission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    val audioLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) viewModel.refresh()
        OnboardingPrefs.markComplete(appContext)
        onFinished()
    }
    val notifLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        notifEnabled = granted
    }

    val finish: () -> Unit = {
        OnboardingPrefs.markComplete(appContext)
        onFinished()
    }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(
            Modifier.fillMaxSize().windowInsetsPadding(WindowInsets.safeDrawing)
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    if (step > 0) {
                        IconButton(onClick = { step = step - 1 }) {
                            Icon(Icons.Filled.ArrowBack, if (zh) "上一步" else "Back", tint = MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
                Row(
                    Modifier.weight(1f).padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    repeat(STEP_COUNT) { i ->
                        Box(
                            Modifier.weight(1f).height(4.dp)
                                .clip(RoundedCornerShape(2.dp))
                                .background(if (i <= step) accent else MaterialTheme.colorScheme.surfaceVariant)
                        )
                    }
                }
                Box(Modifier.size(48.dp), contentAlignment = Alignment.Center) {
                    if (step < STEP_COUNT - 1) {
                        TextButton(onClick = finish) {
                            Text(if (zh) "跳过" else "Skip", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            AnimatedContent(
                targetState = step,
                modifier = Modifier.weight(1f).fillMaxWidth(),
                transitionSpec = {
                    if (targetState > initialState) {
                        (slideInHorizontally(tween(MuseMotion.EnterFull)) { it } + fadeIn(tween(MuseMotion.EnterFull)))
                            .togetherWith(slideOutHorizontally(tween(MuseMotion.ExitFull)) { -it } + fadeOut(tween(MuseMotion.ExitFull)))
                    } else {
                        (slideInHorizontally(tween(MuseMotion.EnterFull)) { -it } + fadeIn(tween(MuseMotion.EnterFull)))
                            .togetherWith(slideOutHorizontally(tween(MuseMotion.ExitFull)) { it } + fadeOut(tween(MuseMotion.ExitFull)))
                    }
                },
                label = "onboarding"
            ) { page ->
                Column(
                    Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp, vertical = 8.dp)
                ) {
                    when (page) {
                        0 -> WelcomeStep(zh, accent)
                        1 -> FeatureStep(zh, accent)
                        2 -> AppearanceStep(zh, uiState.uiStyle, viewModel::setUiStyle, accent)
                        3 -> PreferenceStep(
                            zh, uiState.onlineSearchEnabled, viewModel::setOnlineSearchEnabled,
                            notifEnabled, { enabled -> if (enabled) notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) else notifEnabled = false },
                            lanRemote, { enabled -> lanRemote = enabled; viewModel.setLanHosting(enabled) },
                            accent
                        )
                        4 -> GestureStep(zh, accent)
                        5 -> AccountStep(zh, accent, appContext, (appContext.applicationContext as? com.caipan.music.MuseApplication)?.oauthManager?.session?.value)
                        else -> PermissionStep(zh, accent)
                    }
                    Spacer(Modifier.height(24.dp))
                }
            }

            Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 16.dp)) {
                if (step == STEP_COUNT - 1) {
                    TextButton(onClick = finish, modifier = Modifier.align(Alignment.CenterHorizontally)) {
                        Text(if (zh) "跳过，稍后配置" else "Skip for now", color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Spacer(Modifier.height(8.dp))
                }
                Button(
                    onClick = {
                        if (step == STEP_COUNT - 1) {
                            audioLauncher.launch(audioPermission)
                        } else {
                            step = step + 1
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(56.dp),
                    shape = RoundedCornerShape(MuseDesign.RadiusStandard),
                    colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = MaterialTheme.colorScheme.onPrimary)
                ) {
                    Text(
                        when (step) {
                            0 -> if (zh) "开始设置" else "Get started"
                            STEP_COUNT - 1 -> if (zh) "允许并开始" else "Allow & start"
                            else -> if (zh) "继续" else "Continue"
                        },
                        style = MaterialTheme.typography.titleMedium
                    )
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.Filled.ArrowForward, null, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun StepHeader(title: String, subtitle: String, accent: Color) {
    Spacer(Modifier.height(12.dp))
    Text(title, style = MaterialTheme.typography.headlineMedium, color = MaterialTheme.colorScheme.onSurface)
    Spacer(Modifier.height(8.dp))
    Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    Spacer(Modifier.height(24.dp))
}

@Composable
private fun WelcomeStep(zh: Boolean, accent: Color) {
    Spacer(Modifier.height(56.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(88.dp).clip(RoundedCornerShape(24.dp))
                .background(accent),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.MusicNote, null, tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(48.dp))
        }
    }
    Spacer(Modifier.height(28.dp))
    Text(
        if (zh) "欢迎使用 Muse" else "Welcome to Muse",
        style = MaterialTheme.typography.headlineLarge,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(12.dp))
    Text(
        if (zh) "本地优先、纯净极简的音乐播放器。" else "A clean, local-first music player.",
        style = MaterialTheme.typography.bodyLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(48.dp))
}

@Composable
private fun FeatureStep(zh: Boolean, accent: Color) {
    StepHeader(if (zh) "Muse 能做什么" else "What Muse does", if (zh) "三件事，专注做好。" else "Three things, done well.", accent)
    FeatureRow(Icons.Filled.LibraryMusic, if (zh) "本地无损播放" else "Lossless local playback", if (zh) "扫描并播放设备中的本地音乐" else "Play music stored on your device", accent)
    FeatureRow(Icons.Filled.Search, if (zh) "多源在线搜索" else "Multi-source search", if (zh) "网易·酷我·酷狗·QQ 一键搜索" else "Search NetEase, Kuwo, Kugou & QQ Music", accent)
    FeatureRow(Icons.Filled.Link, if (zh) "多端联动" else "Multi-device control", if (zh) "局域网遥控与 MChat 开放 API" else "LAN remote & MChat open API", accent)
}

@Composable
private fun FeatureRow(icon: ImageVector, title: String, subtitle: String, accent: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun AppearanceStep(zh: Boolean, current: UiStyle, onSelect: (UiStyle) -> Unit, accent: Color) {
    StepHeader(if (zh) "选择你的外观" else "Choose your look", if (zh) "随时可在设置中更改。" else "Change it anytime in settings.", accent)
    StyleCard(UiStyle.MELOX, "MeloX", if (zh) "悬浮胶囊底栏与大标题首页，移植自开源项目 Mei_MeloX_Android" else "Floating capsule tab bar with a large-title home, ported from Mei_MeloX_Android", current == UiStyle.MELOX, onSelect, accent)
    Spacer(Modifier.height(12.dp))
    StyleCard(UiStyle.CLOUD, if (zh) "云版" else "Cloud", if (zh) "Muse 现有首页与资料库布局，不使用玻璃" else "Muse's existing home and library layout — no glass", current == UiStyle.CLOUD, onSelect, accent)
    Spacer(Modifier.height(12.dp))
    StyleCard(UiStyle.LIQUID, "Liquid Glass", if (zh) "动态折射、RGB 色散、高光与弹性玻璃" else "Refraction, RGB dispersion, highlights and elastic glass", current == UiStyle.LIQUID, onSelect, accent)
}

@Composable
private fun StyleCard(style: UiStyle, title: String, subtitle: String, selected: Boolean, onSelect: (UiStyle) -> Unit, accent: Color) {
    val shape = RoundedCornerShape(MuseDesign.RadiusStandard)
    val borderColor = if (selected) accent else MaterialTheme.colorScheme.surfaceVariant
    val bg = if (selected) accent.copy(alpha = 0.12f) else MaterialTheme.colorScheme.surface
    Row(
        Modifier.fillMaxWidth().clip(shape).background(bg).border(1.dp, borderColor, shape)
            .clickable { onSelect(style) }.padding(16.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (selected) Icon(Icons.Filled.Check, null, tint = accent, modifier = Modifier.size(20.dp))
    }
}

@Composable
private fun PreferenceStep(
    zh: Boolean,
    onlineSearch: Boolean,
    onOnlineSearch: (Boolean) -> Unit,
    notifEnabled: Boolean,
    onNotifToggle: (Boolean) -> Unit,
    lanRemote: Boolean,
    onLanRemote: (Boolean) -> Unit,
    accent: Color
) {
    StepHeader(if (zh) "个性化偏好" else "Personalize", if (zh) "之后都能在设置中调整。" else "All adjustable later in settings.", accent)
    SwitchRow(Icons.Filled.Cloud, if (zh) "在线模式" else "Online mode", if (zh) "首页显示网易云推荐，资料库显示网易云歌单" else "Use NetEase recommendations and remote playlists", onlineSearch, onOnlineSearch, accent)
    SwitchRow(Icons.Filled.Notifications, if (zh) "通知栏控制" else "Notification controls", if (zh) "在通知栏控制播放" else "Control playback from notifications", notifEnabled, onNotifToggle, accent)
    SwitchRow(Icons.Filled.Wifi, if (zh) "局域网遥控" else "LAN remote", if (zh) "允许配对设备远程控制" else "Let paired devices control playback", lanRemote, onLanRemote, accent)
}

@Composable
private fun SwitchRow(icon: ImageVector, title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit, accent: Color) {
    Row(Modifier.fillMaxWidth().padding(vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(icon, null, tint = accent, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface)
            Spacer(Modifier.height(2.dp))
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = accent, checkedThumbColor = MaterialTheme.colorScheme.onPrimary)
        )
    }
}

@Composable
private fun GestureStep(zh: Boolean, accent: Color) {
    StepHeader(if (zh) "手势操作" else "Gestures", if (zh) "在播放页里，一切尽在指尖。" else "Everything at your fingertips on the player.", accent)
    FeatureRow(Icons.Filled.Swipe, if (zh) "左右滑动" else "Swipe sideways", if (zh) "切换上一首 / 下一首" else "Previous / next track", accent)
    FeatureRow(Icons.Filled.MusicNote, if (zh) "上滑封面" else "Swipe up artwork", if (zh) "查看歌词" else "View lyrics", accent)
    FeatureRow(Icons.Filled.PlayArrow, if (zh) "点击 / 长按" else "Tap / long-press", if (zh) "播放暂停 / 更多操作" else "Play/pause / more", accent)
}

@Composable
private fun AccountStep(zh: Boolean, accent: Color, context: android.content.Context, oauthSession: com.caipan.music.data.OAuthSession?) {
    StepHeader(if (zh) "登录 MChat" else "Connect MChat", if (zh) "可选，不影响本地播放。" else "Optional — doesn't affect local playback.", accent)
    val shape = RoundedCornerShape(MuseDesign.RadiusStandard)
    val isLoggedIn = oauthSession?.isLoggedIn == true

    // 已登录状态卡片
    Column(
        Modifier.fillMaxWidth().clip(shape).background(MaterialTheme.colorScheme.surface).border(1.dp, MaterialTheme.colorScheme.surfaceVariant, shape).padding(16.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(14.dp)).background(accent.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(Icons.Filled.Person, null, tint = accent, modifier = Modifier.size(22.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (isLoggedIn) (oauthSession!!.nickname.takeIf { it.isNotBlank() } ?: (if (zh) "已登录" else "Logged in"))
                    else (if (zh) "MChat 账号" else "MChat account"),
                    style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    if (isLoggedIn) (if (zh) "已授权 MChat 登录" else "MChat authorized")
                    else (if (zh) "本地播放不强制登录；点击下方按钮发起 MChat 授权" else "No login required. Tap below to start MChat authorization."),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (isLoggedIn) {
                Icon(Icons.Filled.Check, null, tint = accent, modifier = Modifier.size(20.dp))
            }
        }
        if (!isLoggedIn) {
            Spacer(Modifier.height(16.dp))
            Button(
                onClick = {
                    // 尝试通过 mchat:// scheme 打开 MChat 客户端
                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse("mchat://authorize?callback=muse%3A%2F%2Foauth%2Fcallback"))
                    intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    try {
                        context.startActivity(intent)
                    } catch (_: Exception) {
                        // MChat 未安装，尝试打开浏览器
                        val fallback = Intent(Intent.ACTION_VIEW, Uri.parse("https://mchat.cai.chat"))
                        try { context.startActivity(fallback) } catch (_: Exception) { }
                    }
                },
                modifier = Modifier.fillMaxWidth().height(48.dp),
                shape = RoundedCornerShape(MuseDesign.RadiusStandard),
                colors = ButtonDefaults.buttonColors(containerColor = accent, contentColor = MaterialTheme.colorScheme.onPrimary)
            ) {
                Text(if (zh) "打开 MChat 授权" else "Open MChat to authorize", style = MaterialTheme.typography.titleSmall)
            }
        }
    }
}

@Composable
private fun PermissionStep(zh: Boolean, accent: Color) {
    Spacer(Modifier.height(40.dp))
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Box(
            Modifier.size(88.dp).clip(RoundedCornerShape(24.dp))
                .background(accent.copy(alpha = 0.14f)),
            contentAlignment = Alignment.Center
        ) {
            Icon(Icons.Filled.Folder, null, tint = accent, modifier = Modifier.size(44.dp))
        }
    }
    Spacer(Modifier.height(28.dp))
    Text(
        if (zh) "最后一步：读取本地音乐" else "One last step: access your music",
        style = MaterialTheme.typography.headlineMedium,
        color = MaterialTheme.colorScheme.onSurface,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(12.dp))
    Text(
        if (zh) "Muse 需要读取本地音频文件以建立你的音乐库；授权仅用于本地扫描。" else "Muse needs access to local audio to build your library. Used only for local scanning.",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        textAlign = TextAlign.Center,
        modifier = Modifier.fillMaxWidth()
    )
    Spacer(Modifier.height(48.dp))
}
