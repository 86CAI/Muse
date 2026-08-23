package com.caipan.music.ui

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.content.Intent
import android.provider.Settings
import android.os.Build
import android.graphics.ImageDecoder
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Logout
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.HighQuality
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import coil.compose.AsyncImage
import com.caipan.music.data.Playlist
import com.caipan.music.data.WebdavConfig
import com.caipan.music.data.MuseBackupManager
import com.caipan.music.model.Song
import com.caipan.music.player.LyricLine
import kotlinx.coroutines.launch
import kotlinx.coroutines.Job
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import com.caipan.music.ui.components.AboutScreen
import com.caipan.music.ui.components.EqualizerScreen
import com.caipan.music.ui.components.MiniPlayerBar
import com.caipan.music.ui.components.PlayerScreen
import com.caipan.music.ui.melox.MeloXPlayerScreen
import com.caipan.music.ui.melox.MeloXPersistentPlayer
import com.caipan.music.ui.melox.MeloXRoot
import com.caipan.music.ui.melox.GlassBackdropProvider
import com.caipan.music.ui.melox.LocalGlassColors
import com.caipan.music.ui.melox.defaultGlassColors
import com.caipan.music.ui.melox.hotSongsQueue
import com.caipan.music.ui.melox.meloXOnlineHomeContent
import com.caipan.music.ui.components.PlaylistDetailScreen
import com.caipan.music.ui.components.PlaylistListScreen
import com.caipan.music.ui.components.SongListItem
import com.caipan.music.ui.components.UISettingsScreen
import com.caipan.music.ui.components.SkinSettingsScreen
import com.caipan.music.ui.components.HomeScreen
import com.caipan.music.ui.components.LiquidHomeScreen
import com.caipan.music.ui.components.LiquidMiniPlayerBar
import com.caipan.music.ui.components.MineradioLyricsScreen
import com.caipan.music.ui.components.PluginListScreen
import com.caipan.music.ui.components.PluginWebUiScreen
import com.caipan.music.ui.components.OnlineSearchScreen
import com.caipan.music.ui.components.OnlineModeScreen
import com.caipan.music.ui.components.NeteaseLoginScreen
import com.caipan.music.ui.components.OnlineSourceUiModel
import com.caipan.music.ui.components.fullScreenOverlayEnter
import com.caipan.music.ui.components.fullScreenOverlayExit
import com.caipan.music.ui.components.rectReveal
import com.caipan.music.ui.components.pressScale
import com.caipan.music.ui.components.SkeletonSongRows
import com.caipan.music.ui.components.staggeredEnter
import com.caipan.music.ui.components.PlaybackSettingsSheet
import com.caipan.music.ui.components.CommentUiCallbacks
import com.caipan.music.ui.components.CommentUiItem
import com.caipan.music.ui.components.CommentUiState
import com.caipan.music.ui.components.CommentsPresentation
import com.caipan.music.ui.components.NeteaseCommentsScreen
import com.caipan.music.ui.components.NeteaseOnlineProfileScreen
import com.caipan.music.plugin.PluginWebUiSession
import com.caipan.music.ui.components.WebdavImportScreen
import com.caipan.music.ui.components.ProfileScreen
import com.caipan.music.ui.components.ImageCropDialog
import com.caipan.music.ui.components.SongActionsSheet
import com.caipan.music.ui.components.GitHubAccountSheet
import com.caipan.music.MuseApplication
import com.caipan.music.ui.components.DialogBlurEffect
import com.caipan.music.ui.components.LocalMuseBlurPolicy
import com.caipan.music.ui.components.LocalMusePerformancePolicy
import com.caipan.music.ui.components.LocalMuseBackdrop
import com.caipan.music.ui.components.MuseAlertDialog
import com.caipan.music.ui.components.MuseButton
import com.caipan.music.ui.components.MuseIconButton
import com.caipan.music.ui.components.MuseOutlinedButton
import com.caipan.music.ui.components.MuseTextButton
import com.caipan.music.ui.components.LocalMuseLiquidGlass
import com.caipan.music.ui.components.LocalMuseMonet
import com.caipan.music.ui.components.museGlass
import com.caipan.music.ui.theme.MusicPlayerTheme
import com.caipan.music.ui.theme.MuseMotion
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberCombinedBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.caipan.music.viewmodel.MusicViewModel
import com.caipan.music.viewmodel.UiStyle
import com.caipan.music.online.NeteaseCatalog
import com.caipan.music.online.NeteaseComment
import com.caipan.music.online.toSong

private enum class CropKind { WALLPAPER, AVATAR, COVER }

private data class CropRequest(
    val uri: Uri,
    val aspectRatio: Float,
    val kind: CropKind,
    val playlistId: String? = null
)

private val bgPresets = listOf(
    "动态" to null,
    "绿色" to Color(0xFF30D158),
    "蓝色" to Color(0xFF0A84FF),
    "紫色" to Color(0xFFAF52DE),
    "粉色" to Color(0xFFFC3C44),
    "橙色" to Color(0xFFFF9500)
)

@OptIn(ExperimentalMaterial3Api::class, ExperimentalAnimationApi::class)
@Composable
fun MainScreen(viewModel: MusicViewModel = viewModel()) {
    val uiState by viewModel.uiState.collectAsState()
    val externalPlayer by viewModel.externalPlayerState.collectAsState()
    val eqRevision by viewModel.eqManager.revision.collectAsState()
    val context = LocalContext.current
    val application = context.applicationContext as MuseApplication
    // Force registration before observing the built-in UI-effects plugin.
    application.pluginManager
    val blurPolicy by application.globalBlurControlPlugin.policy.collectAsState()
    val performancePolicy by application.performanceControlPlugin.policy.collectAsState()
    var showFullPlayer by remember { mutableStateOf(false) }
    // MeloX uses its own persistent BottomSheet-style player host. Unlike the
    // legacy route, toggling this state never removes the collapsed mini player.
    var meloXPlayerExpanded by remember { mutableStateOf(false) }
    var showMineradioLyrics by remember { mutableStateOf(false) }
    var mineradioLyrics by remember { mutableStateOf<List<LyricLine>>(emptyList()) }
    var miniArtworkBounds by remember { mutableStateOf<Rect?>(null) }
    var playerRevealOrigin by remember { mutableStateOf<Offset?>(null) }
    var playerTransitionRunning by remember { mutableStateOf(false) }
    var playerIsClosing by remember { mutableStateOf(false) }
    val playerOpenProgress = remember { Animatable(1f) }
    val playerTransitionProgress = remember { derivedStateOf { playerOpenProgress.value } }
    var playerTransitionJob by remember { mutableStateOf<Job?>(null) }
    var showSettings by remember { mutableStateOf(false) }
    var showGitHubAccount by remember { mutableStateOf(false) }
    var showPlaybackSettings by remember { mutableStateOf(false) }
    var showNeteaseComments by remember { mutableStateOf(false) }
    // Comments can be opened from an online-library row before that track becomes the player item.
    // Keep the target independently so the sheet always has the correct title and resource id.
    var neteaseCommentsTarget by remember { mutableStateOf<Song?>(null) }
    var showEqualizer by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showUiSettings by remember { mutableStateOf(false) }
    var showSkinSettings by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var showPlugins by remember { mutableStateOf(false) }
    var showOnlineSearch by remember { mutableStateOf(false) }
    var showNeteaseLogin by remember { mutableStateOf(false) }
    var neteaseLoginError by remember { mutableStateOf<String?>(null) }
    var pluginWebUi by remember { mutableStateOf<PluginWebUiSession?>(null) }
    var actionSong by remember { mutableStateOf<Song?>(null) }
    var actionPlaylistId by remember { mutableStateOf<String?>(null) }
    val isLight = uiState.isLightTheme
    val scope = rememberCoroutineScope()
    val backupManager = remember { MuseBackupManager(context) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    val prefs = context.getSharedPreferences("muse_prefs", 0)
    // MChat OAuth 会话：提升到 MainScreen 顶层，供登录同步与设置面板共用
    val oauthSession by application.oauthManager.session.collectAsState()
    val gitHubSession by application.gitHubSessionStore.session.collectAsState()
    // MChat 登录后自动把昵称/头像同步到个人页；登出会清除同步标记，再次登录时重新覆盖
    LaunchedEffect(oauthSession?.token) {
        val session = oauthSession ?: return@LaunchedEffect
        if (!session.allowProfile) return@LaunchedEffect  // 文档要求：allow_profile=false 时不得使用昵称/头像
        if (session.token == prefs.getString("oauth_synced_token", null)) return@LaunchedEffect
        if (session.nickname.isNotBlank()) viewModel.setProfileName(session.nickname)
        if (session.avatar.isNotBlank()) {
            // 头像下载成功才写同步标记；失败则下次（重启/重新登录）可重试
            viewModel.saveProfileAvatarUrl(session.avatar) { success ->
                if (success) prefs.edit().putString("oauth_synced_token", session.token).apply()
            }
        } else {
            prefs.edit().putString("oauth_synced_token", session.token).apply()
        }
    }
    var bgOpacity by remember { mutableStateOf(prefs.getFloat("ui_bg_opacity", -1f).let { it2 -> if (it2 < 0f) 0.5f else it2 }) }
    var bgBlur by remember { mutableStateOf(prefs.getFloat("ui_bg_blur", 0f).coerceIn(0f, 40f)) }
    // 待裁剪的导入图片（壁纸/头像/歌单封面统一走裁剪流程）
    var cropRequest by remember { mutableStateOf<CropRequest?>(null) }

    // ── 背景亮度感知：采样壁纸平均亮度，自动选择可读的文字/图标颜色 ──
    var wallpaperLuminance by remember(uiState.wallpaperUri) { mutableStateOf<Float?>(null) }
    LaunchedEffect(uiState.wallpaperUri) {
        wallpaperLuminance = uiState.wallpaperUri?.let { uri ->
            withContext(Dispatchers.IO) {
                runCatching {
                    val source = ImageDecoder.createSource(context.contentResolver, uri)
                    val bmp = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
                        val maxDim = maxOf(info.size.width, info.size.height)
                        if (maxDim > 64) decoder.setTargetSampleSize(maxDim / 64)
                        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                    }
                    val px = IntArray(bmp.width * bmp.height)
                    bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                    var sum = 0.0
                    for (p in px) {
                        val r = (p shr 16) and 0xFF
                        val g = (p shr 8) and 0xFF
                        val b = p and 0xFF
                        sum += (0.299 * r + 0.587 * g + 0.114 * b) / 255.0
                    }
                    if (!bmp.isRecycled) bmp.recycle()
                    (sum / px.size.coerceAtLeast(1)).toFloat()
                }.getOrNull()
            }
        }
    }
    // 主界面背景是否偏暗：壁纸按采样亮度、视频按深色处理、无背景跟随主题
    val isCloudStyle = uiState.uiStyle == UiStyle.CLOUD || uiState.uiStyle == UiStyle.MONET
    val bgIsDark = when {
        !isCloudStyle && uiState.wallpaperUri != null -> wallpaperLuminance?.let { it < 0.45f } ?: !isLight
        !isCloudStyle && uiState.videoUri != null -> true
        else -> !isLight
    }
    val contentText = if (bgIsDark) Color.White else Color(0xFF1C1C1E)
    val contentMuted = if (bgIsDark) Color.White.copy(alpha = 0.5f) else Color(0xFF8E8E93)
    val contentIconTint = if (bgIsDark) Color.White.copy(alpha = 0.85f) else Color(0xFF3A3A3C)
    var currentLang by remember { mutableStateOf(prefs.getString("app_language", "zh") ?: "zh") }
    val backdrop = rememberLayerBackdrop()
    // The mini player is composed after HomeScreen. Export its rendered layer
    // so the HomeScreen bottom lens can sample that foreground control too.
    val foregroundBackdrop = rememberLayerBackdrop()
    val bottomTabsPageBackdrop = rememberCombinedBackdrop(backdrop, foregroundBackdrop)
    // Keep the wallpaper recorder separate from foreground recorders. Glass
    // surfaces sample the combined backdrop only where they need foreground
    // controls (the home bottom dock).
    val glassBackdrop: com.kyant.backdrop.Backdrop? = backdrop
    val routeCornerRadiusPx = with(LocalDensity.current) { 18.dp.toPx() }
    // 非莫奈样式下的强调色；纯莫奈在 MusicPlayerTheme 内用动态取色的 primary 覆盖
    val accentColorRaw = uiState.customBgColor ?: Color(0xFFFA2D55)

    var playerErrorDialog by remember { mutableStateOf<String?>(null) }
    val contextForErrors = context
    LaunchedEffect(uiState.playerState.errorSerial) {
        uiState.playerState.errorMessage?.takeIf { it.isNotBlank() }?.let { message ->
            playerErrorDialog = message
            runCatching {
                val logFile = java.io.File(contextForErrors.filesDir, "playback_errors.log")
                logFile.appendText(
                    "[${java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.getDefault()).format(java.util.Date())}] v${com.caipan.music.BuildConfig.VERSION_NAME} $message\n"
                )
            }
        }
    }

    // Playlist navigation
    var showHome by remember { mutableStateOf(true) }
    var homeSelectedTab by rememberSaveable { mutableIntStateOf(0) }
    var onlineSelectedTab by rememberSaveable { mutableIntStateOf(0) }
    var localMusicBounds by remember { mutableStateOf<Rect?>(null) }
    var localMusicVisible by remember { mutableStateOf(false) }
    var localMusicTransitionRunning by remember { mutableStateOf(false) }
    val localMusicProgress = remember { Animatable(0f) }
    val localMusicTransitionProgress = remember { derivedStateOf { localMusicProgress.value } }
    var showPlaylistList by remember { mutableStateOf(false) }
    var playlistListBounds by remember { mutableStateOf<Rect?>(null) }
    val playlistListProgress = remember { Animatable(0f) }
    val playlistListTransitionProgress = remember { derivedStateOf { playlistListProgress.value } }
    var showPlaylistDetail by remember { mutableStateOf<String?>(null) }
    var playlistDetailBounds by remember { mutableStateOf<Rect?>(null) }
    val playlistDetailProgress = remember { Animatable(0f) }
    val playlistDetailTransitionProgress = remember { derivedStateOf { playlistDetailProgress.value } }
    var currentPlaylistName by remember { mutableStateOf("") }
    var playlistSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var showWebdavImport by remember { mutableStateOf(false) }
    var webdavBounds by remember { mutableStateOf<Rect?>(null) }
    val webdavProgress = remember { Animatable(0f) }
    val webdavTransitionProgress = remember { derivedStateOf { webdavProgress.value } }
    var selectedFolder by remember { mutableStateOf<String?>(null) }
    var playlistItems by remember { mutableStateOf(viewModel.getAllPlaylists()) }

    val audioPermission = if (Build.VERSION.SDK_INT >= 33) {
        Manifest.permission.READ_MEDIA_AUDIO
    } else {
        Manifest.permission.READ_EXTERNAL_STORAGE
    }
    var hasAudioPerm by remember {
        mutableStateOf(ContextCompat.checkSelfPermission(context, audioPermission) == PackageManager.PERMISSION_GRANTED)
    }
    var hasNotifPerm by remember {
        mutableStateOf(if (Build.VERSION.SDK_INT >= 33)
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        else true)
    }
    val audioPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        hasAudioPerm = granted
        if (granted) viewModel.refresh()
    }
    val notifPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted -> hasNotifPerm = granted }
    val screenAspect = with(LocalConfiguration.current) { screenWidthDp.toFloat() / screenHeightDp.toFloat() }
    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) cropRequest = CropRequest(uri, screenAspect, CropKind.WALLPAPER) }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) viewModel.saveVideo(uri) }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        val plId = showPlaylistDetail
        if (uri != null && plId != null) {
            cropRequest = CropRequest(uri, 1f, CropKind.COVER, plId)
        }
    }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) cropRequest = CropRequest(uri, 1f, CropKind.AVATAR)
    }
    val backupExporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri: Uri? ->
        if (uri != null) scope.launch { backupMessage = backupManager.exportTo(uri).fold({ "备份已导出" }, { "备份失败：${it.message}" }) }
    }
    val backupImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) pendingRestoreUri = uri
    }
    val deleteLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { result ->
        if (result.resultCode == android.app.Activity.RESULT_OK) viewModel.onDeleteCompleted()
    }
    // MeloX plugin page imports through the same ViewModel entry point as the legacy page.
    val meloXPluginImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) viewModel.installPlugin(uri)
    }
    var showPlaylistDialog by remember { mutableStateOf(false) }
    var newPlaylistName by remember { mutableStateOf("") }

    LaunchedEffect(showPlaylistList) {
        if (showPlaylistList) playlistItems = viewModel.getAllPlaylists()
    }

    LaunchedEffect(Unit) {
        if (!hasAudioPerm) audioPermLauncher.launch(audioPermission)
        if (Build.VERSION.SDK_INT >= 33 && !hasNotifPerm) {
            notifPermLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
    val dismissPlayer: () -> Unit = dismiss@{
        if (uiState.uiStyle == UiStyle.MELOX) {
            meloXPlayerExpanded = false
            return@dismiss
        }
        // 关闭全屏播放器后恢复系统方向（跟随自动旋转），不再强制回竖屏
        (context as? android.app.Activity)?.requestedOrientation =
            android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        if (!playerTransitionRunning || playerIsClosing) {
            playerIsClosing = true
            playerTransitionRunning = true
            playerTransitionJob?.cancel()
            playerTransitionJob = scope.launch {
                // 关闭:临界阻尼 spring,比打开快;可被连点打断,从当前进度平滑反向
                playerOpenProgress.animateTo(0f, spring(dampingRatio = 1f, stiffness = Spring.StiffnessMedium))
                if (!playerIsClosing) return@launch
                showFullPlayer = false
                playerTransitionRunning = false
                playerIsClosing = false
                withFrameNanos { }
                playerOpenProgress.snapTo(1f)
            }
        }
    }
    val openPlayer: () -> Unit = open@{
        if (uiState.uiStyle == UiStyle.MELOX) {
            meloXPlayerExpanded = true
            return@open
        }
        playerRevealOrigin = miniArtworkBounds?.center
        playerIsClosing = false
        playerTransitionRunning = true
        playerTransitionJob?.cancel()
        playerTransitionJob = scope.launch {
            playerOpenProgress.snapTo(0f)
            showFullPlayer = true
            // 打开:临界阻尼 spring(Apple damping 1.0),保留 500ms 级仪式感但可打断
            playerOpenProgress.animateTo(1f, spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow))
            playerTransitionRunning = false
        }
        Unit
    }
    val openPlayerDirect: () -> Unit = openDirect@{
        if (uiState.uiStyle == UiStyle.MELOX) {
            meloXPlayerExpanded = true
            return@openDirect
        }
        playerRevealOrigin = null
        playerIsClosing = false
        playerTransitionRunning = true
        playerTransitionJob?.cancel()
        playerTransitionJob = scope.launch {
            playerOpenProgress.snapTo(0f)
            showFullPlayer = true
            // 打开:临界阻尼 spring(Apple damping 1.0),保留 500ms 级仪式感但可打断
            playerOpenProgress.animateTo(1f, spring(dampingRatio = 1f, stiffness = Spring.StiffnessMediumLow))
            playerTransitionRunning = false
        }
        Unit
    }
    val openLocalMusic: () -> Unit = {
        if (!localMusicVisible) {
            localMusicTransitionRunning = true
            localMusicVisible = true
            scope.launch {
                localMusicProgress.snapTo(0f)
                localMusicProgress.animateTo(1f, tween(MuseMotion.EnterReveal, easing = FastOutSlowInEasing))
                localMusicTransitionRunning = false
            }
        }
    }
    val dismissLocalMusic: () -> Unit = {
        if (localMusicVisible) {
            localMusicTransitionRunning = true
            scope.launch {
                withFrameNanos { }
                localMusicProgress.animateTo(0f, tween(MuseMotion.ExitReveal, easing = FastOutSlowInEasing))
                localMusicVisible = false
                localMusicTransitionRunning = false
                selectedFolder = null
                if (uiState.batchMode) viewModel.toggleBatchMode()
            }
        }
    }
    val openPlaylistList: () -> Unit = {
        if (!showPlaylistList) {
            showPlaylistList = true
            scope.launch {
                playlistListProgress.snapTo(0f)
                playlistListProgress.animateTo(1f, tween(MuseMotion.EnterReveal, easing = FastOutSlowInEasing))
            }
        }
    }
    val dismissPlaylistList: () -> Unit = {
        if (showPlaylistList && showPlaylistDetail == null && !showWebdavImport) {
            scope.launch {
                playlistListProgress.animateTo(0f, tween(MuseMotion.ExitReveal, easing = FastOutSlowInEasing))
                showPlaylistList = false
            }
        }
    }
    val dismissPlaylistDetail: () -> Unit = {
        if (showPlaylistDetail != null) {
            scope.launch {
                // 先刷新并让底下的歌单列表完成一帧绘制，避免详情页退出期间出现空白。
                playlistItems = viewModel.getAllPlaylists()
                showPlaylistList = true
                playlistListProgress.snapTo(1f)
                withFrameNanos { }
                playlistDetailProgress.animateTo(0f, tween(MuseMotion.ExitReveal, easing = FastOutSlowInEasing))
                showPlaylistDetail = null
            }
        }
    }
    val openWebdavImport: () -> Unit = {
        if (!showWebdavImport) {
            showWebdavImport = true
            scope.launch {
                webdavProgress.snapTo(0f)
                webdavProgress.animateTo(1f, tween(MuseMotion.EnterReveal, easing = FastOutSlowInEasing))
            }
        }
    }
    val dismissWebdavImport: () -> Unit = {
        if (showWebdavImport) {
            scope.launch {
                webdavProgress.animateTo(0f, tween(MuseMotion.ExitReveal, easing = FastOutSlowInEasing))
                showWebdavImport = false
            }
        }
    }
    BackHandler(
        enabled = showNeteaseComments || showPlaybackSettings || showMineradioLyrics || showFullPlayer || showSettings || showGitHubAccount || showEqualizer || showAbout || showUiSettings || showSkinSettings || showProfile || showPlugins || showOnlineSearch || showNeteaseLogin || pluginWebUi != null ||
            showPlaylistList || showPlaylistDetail != null || showWebdavImport || selectedFolder != null || localMusicVisible || !showHome
    ) {
        when {
            showNeteaseComments -> showNeteaseComments = false
            showPlaybackSettings -> showPlaybackSettings = false
            showMineradioLyrics -> showMineradioLyrics = false
            pluginWebUi != null -> pluginWebUi = null
            showFullPlayer -> dismissPlayer()
            showEqualizer -> showEqualizer = false
            showAbout -> showAbout = false
            showUiSettings -> showUiSettings = false
            showSkinSettings -> showSkinSettings = false
            showProfile -> showProfile = false
            showGitHubAccount -> showGitHubAccount = false
            showPlugins -> showPlugins = false
            showNeteaseLogin -> showNeteaseLogin = false
            showOnlineSearch -> showOnlineSearch = false
            showSettings -> showSettings = false
            showPlaylistDetail != null -> dismissPlaylistDetail()
            showWebdavImport -> dismissWebdavImport()
            showPlaylistList -> dismissPlaylistList()
            selectedFolder != null -> { selectedFolder = null; if (uiState.batchMode) viewModel.toggleBatchMode() }
            else -> dismissLocalMusic()
        }
    }

    val zh = currentLang == "zh"

    // CLOUD is the renamed legacy Monet surface; keep its tonal/dynamic
    // behavior for existing users while Apple has its own route.
    val isPureMonet = isCloudStyle
    MusicPlayerTheme(
        darkTheme = !isLight,
        dynamicColor = isPureMonet,
        primaryColor = if (isPureMonet) null else accentColorRaw,
        skin = if (isPureMonet) null else application.skinManager.activeSkin()
    ) {
        val accentColor = if (isPureMonet) MaterialTheme.colorScheme.primary else accentColorRaw
        CompositionLocalProvider(
            LocalMuseLiquidGlass provides (uiState.uiStyle == UiStyle.LIQUID),
            LocalMuseMonet provides isPureMonet,
            LocalMuseBlurPolicy provides blurPolicy,
            LocalMusePerformancePolicy provides performancePolicy,
            LocalMuseBackdrop provides backdrop
        ) {
    // ── Settings Sheet ──
    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false },
            containerColor = Color.Transparent,
            scrimColor = Color.Black.copy(alpha = .18f),
            tonalElevation = 0.dp,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
            // 透明容器上手柄需不透明高对比才可见
            dragHandle = { BottomSheetDefaults.DragHandle(color = MaterialTheme.colorScheme.onSurface) }) {
            val settingsShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            Column(
                Modifier.fillMaxWidth()
                    .then(
                        Modifier.museGlass(
                            backdrop,
                            settingsShape,
                            MaterialTheme.colorScheme.surface.copy(alpha = .36f),
                            liquidGlass = uiState.uiStyle == UiStyle.LIQUID
                        )
                    )
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 24.dp).padding(bottom = 32.dp).navigationBarsPadding()
            ) {
                DialogBlurEffect()
                Text(if (zh) "设置" else "Settings", color = contentText, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LightMode, contentDescription = null, tint = contentIconTint)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (zh) "浅色模式" else "Light Mode", color = contentText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(if (zh) "切换浅色/深色主题" else "Switch between light and dark theme", color = Color(0xFF888888), fontSize = 12.sp)
                    }
                    com.caipan.music.ui.components.MuseGlassSwitch(
                        checked = isLight,
                        onCheckedChange = { viewModel.toggleTheme() },
                        accentColor = accentColor,
                        backdrop = backdrop
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth().clickable { showSettings = false; showEqualizer = true }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = contentIconTint)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (zh) "均衡器" else "Equalizer", color = contentText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(if (zh) "导入预设、调整频段" else "Import presets, adjust bands", color = Color(0xFF888888), fontSize = 12.sp)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF888888), modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth().clickable { showSettings = false; showPlugins = true }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Extension, contentDescription = null, tint = contentIconTint)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (zh) "插件" else "Plugins", color = contentText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(if (zh) "安装、授权和管理播放插件" else "Install and manage playback plugins", color = Color(0xFF888888), fontSize = 12.sp)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF888888), modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth().clickable { showSettings = false; showUiSettings = true }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = contentIconTint)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (zh) "更多设置" else "More Settings", color = contentText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(if (zh) "背景透明度等" else "Background opacity etc.", color = Color(0xFF888888), fontSize = 12.sp)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF888888), modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth().clickable { showSettings = false; showSkinSettings = true }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Palette, contentDescription = null, tint = contentIconTint)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (zh) "皮肤" else "Skins", color = contentText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(if (zh) "导入声明式皮肤包，自定义颜色/圆角/布局" else "Import declarative skin packs", color = Color(0xFF888888), fontSize = 12.sp)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF888888), modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth().clickable { showSettings = false; showAbout = true }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = contentIconTint)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (zh) "关于" else "About", color = contentText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(if (zh) "版本信息、语言切换" else "Version info, language", color = Color(0xFF888888), fontSize = 12.sp)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF888888), modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.height(16.dp))
                val isOnlineMode = uiState.musicMode == com.caipan.music.data.MusicMode.ONLINE
                Row(
                    Modifier.fillMaxWidth().clickable {
                        onlineSelectedTab = 0
                        viewModel.setMusicMode(
                            if (isOnlineMode) com.caipan.music.data.MusicMode.LOCAL
                            else com.caipan.music.data.MusicMode.ONLINE
                        )
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.AccountCircle, contentDescription = null, tint = contentIconTint)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(
                            if (isOnlineMode) {
                                if (zh) "在线模式" else "Online mode"
                            } else {
                                if (zh) "本地模式" else "Local mode"
                            },
                            color = contentText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Text(
                            if (isOnlineMode) {
                                if (zh) "正在使用网易云内容 · 点击切换到本地" else "Using NetEase content · tap to switch local"
                            } else {
                                if (zh) "正在使用设备音乐 · 点击切换到在线" else "Using device music · tap to switch online"
                            },
                            color = Color(0xFF888888),
                            fontSize = 12.sp
                        )
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF888888), modifier = Modifier.size(20.dp))
                }
                if (isOnlineMode) {
                    Spacer(Modifier.height(16.dp))
                    val neteaseSession = uiState.neteaseSession
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            if (neteaseSession == null) {
                                showSettings = false
                                neteaseLoginError = null
                                showNeteaseLogin = true
                            } else {
                                viewModel.logoutNetease()
                            }
                        },
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(Icons.Default.AccountCircle, contentDescription = null, tint = contentIconTint)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(
                                neteaseSession?.nickname?.takeIf { it.isNotBlank() }
                                    ?: if (zh) "登录网易云音乐" else "Sign in to NetEase Music",
                                color = contentText,
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                if (neteaseSession == null) {
                                    if (zh) "登录后同步推荐、歌单和收藏" else "Sign in for recommendations, playlists and likes"
                                } else {
                                    if (zh) "网易云音乐已登录 · 点击退出" else "NetEase Music signed in · tap to sign out"
                                },
                                color = Color(0xFF888888),
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Icon(
                            if (neteaseSession == null) Icons.Default.ChevronRight else Icons.Default.Logout,
                            if (zh) {
                                if (neteaseSession == null) "登录" else "退出登录"
                            } else {
                                if (neteaseSession == null) "Sign in" else "Sign out"
                            },
                            tint = if (neteaseSession == null) Color(0xFF888888) else MaterialTheme.colorScheme.error,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(Modifier.height(16.dp))
                // ── MChat OAuth Login ──
                val isLoggedIn = oauthSession?.isLoggedIn == true
                Row(
                    Modifier.fillMaxWidth().clickable {
                        if (isLoggedIn) {
                            // 已登录 → 登出（同时清除同步标记，再次登录会重新覆盖档案）
                            application.oauthManager.clearSession()
                            prefs.edit().remove("oauth_synced_token").apply()
                        } else {
                            // 未登录 → 唤起 MChat 授权
                            showSettings = false
                            launchMChatAuth(context)
                        }
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    val loggedSession = oauthSession?.takeIf { it.isLoggedIn }
                    if (loggedSession?.avatar?.isNotBlank() == true) {
                        AsyncImage(
                            model = loggedSession.avatar,
                            contentDescription = "MChat 头像",
                            modifier = Modifier.size(36.dp).clip(CircleShape),
                            contentScale = ContentScale.Crop
                        )
                    } else {
                        Icon(
                            if (isLoggedIn) Icons.Default.AccountCircle else Icons.Default.Person,
                            contentDescription = null,
                            tint = contentIconTint
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        if (isLoggedIn) {
                            val session = oauthSession!!
                            Text(session.nickname, color = contentText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(
                                if (zh) "MChat 已登录 · 点击退出" else "MChat signed in · tap to logout",
                                color = Color(0xFF888888), fontSize = 12.sp
                            )
                        } else {
                            Text(if (zh) "登录 MChat" else "Sign in with MChat", color = contentText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            Text(
                                if (zh) "使用 MChat 账号登录" else "Sign in with your MChat account",
                                color = Color(0xFF888888), fontSize = 12.sp
                            )
                        }
                    }
                    if (isLoggedIn) {
                        Icon(Icons.Default.Logout, if (zh) "退出" else "Logout", tint = Color(0xFF888888), modifier = Modifier.size(20.dp))
                    } else {
                        Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF888888), modifier = Modifier.size(20.dp))
                    }
                }
                Spacer(Modifier.height(16.dp))
                // GitHub account and Gist sync entry.
                Row(
                    Modifier.fillMaxWidth().clickable {
                        showSettings = false
                        showGitHubAccount = true
                    },
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.Link, contentDescription = null, tint = contentIconTint)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        val account = gitHubSession
                        Text(
                            if (account != null) (account.name ?: "@${account.login}")
                            else if (zh) "登录 GitHub" else "Sign in with GitHub",
                            color = contentText,
                            fontSize = 15.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Text(
                            if (account != null) {
                                if (zh) "GitHub 已登录 · 管理同步" else "GitHub signed in · manage sync"
                            } else {
                                if (zh) "登录后同步歌单与设置" else "Sync playlists and settings after signing in"
                            },
                            color = Color(0xFF888888),
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    Icon(
                        if (gitHubSession == null) Icons.Default.ChevronRight else Icons.Default.AccountCircle,
                        contentDescription = null,
                        tint = Color(0xFF888888),
                        modifier = Modifier.size(20.dp)
                    )
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.HighQuality, contentDescription = null, tint = contentIconTint)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (zh) "在线音质偏好" else "Online Quality", color = contentText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(if (zh) "在线歌曲优先请求的音质" else "Preferred bitrate for online songs", color = Color(0xFF888888), fontSize = 12.sp)
                    }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    com.caipan.music.player.AudioQuality.entries.forEach { q ->
                        val selected = uiState.playbackSettings.preferredQuality == q
                        com.caipan.music.ui.components.MuseFilterChip(
                            selected = selected,
                            onClick = { viewModel.updatePlaybackSettings { it.copy(preferredQuality = q) } },
                            label = { Text(q.label, fontSize = 12.sp, textAlign = TextAlign.Center, maxLines = 1) },
                            modifier = Modifier.weight(1f).height(44.dp)
                        )
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(if (zh) "背景" else "Background", color = contentText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(if (zh) "选择图片或视频作为歌曲列表与播放器背景" else "Choose an image or a looping video as background", color = Color(0xFF888888), fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                var bgMenuExpanded by remember { mutableStateOf(false) }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 当前背景预览：图片缩略图 / 视频图标
                    when {
                        uiState.wallpaperUri != null -> {
                            Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF333333))) {
                                AsyncImage(model = uiState.wallpaperUri, contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                            }
                        }
                        uiState.videoUri != null -> {
                            Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF333333)), contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.VideoFile, null, tint = Color(0xFFAAAAAA), modifier = Modifier.size(28.dp))
                            }
                        }
                    }
                    if (uiState.wallpaperUri != null || uiState.videoUri != null) Spacer(Modifier.width(12.dp))
                    // 单一入口：选择背景（图片 / 视频）
                    Box {
                        MuseButton(onClick = { bgMenuExpanded = true },
                            colors = ButtonDefaults.buttonColors(containerColor = if (bgIsDark) Color(0xFF333333) else Color(0xFFE5E5EA)),
                            shape = RoundedCornerShape(12.dp)) {
                            Icon(Icons.Default.Image, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text(if (uiState.wallpaperUri != null || uiState.videoUri != null) (if (zh) "更换背景" else "Change") else (if (zh) "选择背景" else "Choose"),
                                color = contentText)
                        }
                        DropdownMenu(expanded = bgMenuExpanded, onDismissRequest = { bgMenuExpanded = false }) {
                            DropdownMenuItem(
                                text = { Text(if (zh) "图片" else "Image") },
                                leadingIcon = { Icon(Icons.Default.Wallpaper, null) },
                                onClick = {
                                    bgMenuExpanded = false
                                    wallpaperPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                                }
                            )
                            DropdownMenuItem(
                                text = { Text(if (zh) "视频" else "Video") },
                                leadingIcon = { Icon(Icons.Default.VideoFile, null) },
                                onClick = {
                                    bgMenuExpanded = false
                                    videoPicker.launch("video/*")
                                }
                            )
                        }
                    }
                    if (uiState.wallpaperUri != null || uiState.videoUri != null) {
                        Spacer(Modifier.width(8.dp))
                        MuseIconButton(onClick = {
                            if (uiState.wallpaperUri != null) viewModel.clearWallpaper()
                            if (uiState.videoUri != null) viewModel.clearVideo()
                        }) { Icon(Icons.Default.Delete, if (zh) "移除" else "Remove", tint = Color(0xFF888888)) }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(if (zh) "强调色" else "Accent Color", color = contentText, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    if (uiState.uiStyle == UiStyle.LIQUID)
                        (if (zh) "仅用于文字、图标与交互状态，不会染色玻璃" else "Content and controls only; glass stays neutral")
                    else (if (zh) "用于界面重点内容" else "Used for highlighted content"),
                    color = Color(0xFF888888), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    bgPresets.forEach { (name, color) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { viewModel.setBackgroundColor(color) }) {
                            Box(Modifier.size(44.dp).clip(CircleShape).background(color ?: if (bgIsDark) Color(0xFF333333) else Color(0xFFE5E5EA)), contentAlignment = Alignment.Center) {
                                if (color == null) Text("🎨", fontSize = 18.sp)
                                if ((color == null && uiState.customBgColor == null) || (color != null && color == uiState.customBgColor)) Text("✓", color = if (bgIsDark) Color.White else Color.Black, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(name, color = Color(0xFF888888), fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }

    if (showGitHubAccount) {
        GitHubAccountSheet(
            accent = accentColor,
            isLight = uiState.isLightTheme,
            onDismiss = { showGitHubAccount = false }
        )
    }

        Box(Modifier.fillMaxSize()) {
            // Background only, while foreground controls stay clear.
            Box(Modifier.matchParentSize().layerBackdrop(backdrop)) {
            if (!isPureMonet && uiState.wallpaperUri != null) {
                AsyncImage(model = uiState.wallpaperUri, contentDescription = null,
                    modifier = Modifier.fillMaxSize().scale(1.1f)
                        .then(if (bgBlur > 0f && !isPureMonet) Modifier.blur(bgBlur.dp) else Modifier),
                    contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(
                    if (isLight) Color.White.copy(alpha = bgOpacity) else Color.Black.copy(alpha = bgOpacity)))
            } else if (!isPureMonet && uiState.videoUri != null) {
                val videoUri = uiState.videoUri!!
                val playVideoBackground = showHome && !localMusicVisible && !showFullPlayer && !showSettings && !showEqualizer &&
                    !showAbout && !showUiSettings && !showPlugins && !showOnlineSearch && !showPlaylistList && showPlaylistDetail == null && !showWebdavImport
                AndroidView(factory = { ctx ->
                    android.widget.VideoView(ctx).apply {
                        setVideoURI(videoUri)
                        setOnPreparedListener { mp ->
                            mp.setVolume(0f, 0f)
                            mp.isLooping = true
                            if (playVideoBackground) start()
                        }
                    }
                }, update = { video ->
                    if (playVideoBackground) {
                        if (!video.isPlaying) video.start()
                    } else if (video.isPlaying) {
                        video.pause()
                    }
                }, modifier = Modifier.fillMaxSize().scale(1.15f))
                Box(Modifier.fillMaxSize().background(
                    if (isLight) Color.White.copy(alpha = bgOpacity) else Color.Black.copy(alpha = bgOpacity)))
            } else {
                Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background))
            }
            }

            // ── Mini player display state（提前计算：MeloX 底栏内嵌迷你播放器需要）──
            // 只有当前显示外部播放器时才允许使用外部封面；Muse 本地播放必须使用本地歌曲封面。
            val isExternalActive = externalPlayer != null && uiState.playerState.currentSong == null
            val extArt = if (isExternalActive) externalPlayer?.cachedArtPath?.let { Uri.fromFile(java.io.File(it)) } else null
            val showMiniPlayer = (uiState.showPlayer || isExternalActive) &&
                (uiState.playerState.currentSong != null || externalPlayer != null) &&
                (!showFullPlayer || playerIsClosing) &&
                showPlaylistList == false && showPlaylistDetail == null &&
                showWebdavImport == false && showEqualizer == false && showSettings == false &&
                showAbout == false && showUiSettings == false && showProfile == false && showPlugins == false && pluginWebUi == null
            // Determine if showing external player data
            val ext = externalPlayer
            // Build synthetic song from external player if needed
            val displaySong: Song? = if (isExternalActive) {
                // 外部播放器每 500ms 会刷新进度，但同一首歌必须保持同一个 Compose identity；
                // 切歌时才更换 identity，否则 PlayerScreen 的 remember 状态会被错误复用并产生抽动。
                val trackKey = listOf(
                    ext!!.packageName,
                    ext.title,
                    ext.artist,
                    ext.album,
                    ext.durationMs
                ).joinToString("|").hashCode().toLong()
                Song(id = trackKey, title = ext.title, artist = ext.artist, album = ext.album,
                    durationMs = ext.durationMs, albumId = 0)
            } else uiState.playerState.currentSong
            val displayPlaying = if (isExternalActive) externalPlayer!!.isPlaying else uiState.playerState.isPlaying
            val displayProgress = if (isExternalActive) externalPlayer!!.progressMs else uiState.playerState.progressMs
            val displayDuration = if (isExternalActive) externalPlayer!!.durationMs else uiState.playerState.durationMs
            // External player callbacks
            val extPlayPause: () -> Unit = { viewModel.externalPlayerMonitor.togglePlay() }
            val extNext: () -> Unit = { viewModel.externalPlayerMonitor.next() }
            val extPrevious: () -> Unit = { viewModel.externalPlayerMonitor.previous() }
            val extSeek: (Long) -> Unit = { pos -> viewModel.externalPlayerMonitor.seekTo(pos) }
            val localPlayPause: () -> Unit = { viewModel.togglePlayPause() }
            val localNext: () -> Unit = { viewModel.next() }
            val localPrevious: () -> Unit = { viewModel.previous() }
            val localSeek: (Long) -> Unit = { pos -> viewModel.seekTo(pos) }
            val stagePlayPause: () -> Unit = if (isExternalActive) extPlayPause else localPlayPause
            val stageNext: () -> Unit = if (isExternalActive) extNext else localNext
            val stagePrevious: () -> Unit = if (isExternalActive) extPrevious else localPrevious
            val stageSeek: (Long) -> Unit = if (isExternalActive) extSeek else localSeek

            Column(
                Modifier.fillMaxSize().graphicsLayer {
                    if (localMusicVisible) {
                        val progress = localMusicTransitionProgress.value
                        alpha = progress.coerceIn(0f, 1f)
                    }
                }.then(
                    if (localMusicVisible) {
                        Modifier.rectReveal(localMusicTransitionProgress, localMusicBounds, routeCornerRadiusPx)
                    } else Modifier
                )
            ) {
                if (localMusicVisible) {
                    // ── Header：返回键一行，页面标题（本地歌曲/文件夹名）独立在下方，像播放列表 ──
                    Row(Modifier.fillMaxWidth().statusBarsPadding().padding(start = 8.dp, top = 4.dp, end = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                        MuseIconButton(onClick = { if (selectedFolder != null) selectedFolder = null else dismissLocalMusic(); showPlaylistList = false; showPlaylistDetail = null; showWebdavImport = false }) {
                            Icon(Icons.Default.ArrowBack, if (zh) "返回" else "Back",
                                tint = contentIconTint)
                        }
                        if (uiState.batchMode) {
                            Text(if (zh) "已选 ${uiState.selectedIds.size} 首" else "${uiState.selectedIds.size} selected",
                                color = accentColor, fontSize = 13.sp, modifier = Modifier.padding(start = 8.dp).weight(1f))
                        } else {
                            Spacer(Modifier.weight(1f))
                        }
                        val btnBg = if (bgIsDark) Color.White.copy(alpha = 0.1f) else Color.Black.copy(alpha = 0.05f)
                        val btnTint = contentIconTint
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (selectedFolder != null) Box(Modifier.size(40.dp).clip(CircleShape)
                                .background(if (uiState.batchMode) accentColor.copy(alpha = 0.18f) else btnBg)
                                .clickable { viewModel.toggleBatchMode() }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PlaylistAddCheck, if (zh) "多选" else "Batch",
                                    tint = if (uiState.batchMode) accentColor else btnTint, modifier = Modifier.size(22.dp))
                            }
                        }
                    }
                    Text(selectedFolder ?: if (zh) "本地歌曲" else "Local music",
                        color = contentText, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(start = 20.dp, top = 2.dp, bottom = 6.dp),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)

                    // ── Batch mode action bar ──
                    if (uiState.batchMode) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            MuseTextButton(onClick = { viewModel.selectSongs(uiState.filteredSongs.filter { it.folderPath == selectedFolder }.map { it.id }.toSet()) }) {
                                Text(if (zh) "全选" else "Select All", fontSize = 12.sp, color = accentColor)
                            }
                            MuseTextButton(onClick = { showPlaylistDialog = true }) {
                                Text(if (zh) "添加到歌单" else "Add to Playlist", fontSize = 12.sp, color = accentColor)
                            }
                            MuseTextButton(onClick = {
                                viewModel.createDeleteRequest()?.let { pending ->
                                    deleteLauncher.launch(IntentSenderRequest.Builder(pending.intentSender).build())
                                }
                            }, enabled = uiState.selectedIds.isNotEmpty()) {
                                Text(if (zh) "删除" else "Delete", fontSize = 12.sp, color = MaterialTheme.colorScheme.error)
                            }
                        }
                    }

                    // ── Search ──
                    if (selectedFolder != null) OutlinedTextField(value = uiState.searchQuery, onValueChange = { viewModel.setSearchQuery(it) },
                        placeholder = { Text(if (zh) "搜索歌曲…" else "Search songs...", color = contentMuted) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(12.dp)),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = contentText,
                            unfocusedTextColor = contentText,
                            focusedBorderColor = if (bgIsDark) Color.White.copy(alpha = 0.2f) else Color(0xFFD1D1D6),
                            unfocusedBorderColor = if (bgIsDark) Color.White.copy(alpha = 0.1f) else Color(0xFFE5E5EA),
                            cursorColor = accentColor),
                        singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {}), shape = RoundedCornerShape(12.dp))
                }

                // ── Content ──
                if (!localMusicVisible) {
                    if (uiState.uiStyle == UiStyle.MELOX) {
                        // MeloX 版式（移植自 NEORUAA/Mei_MeloX_Android）：
                        // 首页/发现/音乐库页面 + 悬浮胶囊底栏，本地与在线数据统一接入。
                        MeloXRoot(
                            dark = !isLight,
                            songs = uiState.songs,
                            playlists = playlistItems,
                            onlineLikedSongs = uiState.onlineLikedSongs,
                            onlinePlaylists = uiState.onlinePlaylists,
                            repeatCountsBySongId = uiState.repeatCountsBySongId,
                            profileName = uiState.profileName,
                            profileAvatar = uiState.profileAvatar,
                            onlineActive = uiState.musicMode == com.caipan.music.data.MusicMode.ONLINE,
                            onlineHomeContent = meloXOnlineHomeContent(uiState.onlineHome),
                            onlinePlaylistDetail = uiState.onlinePlaylistDetail,
                            currentSongId = uiState.playerState.currentSong?.id,
                            isPlaying = displayPlaying,
                            hasNext = true,
                            isLoading = uiState.isLoading,
                            onlineSearchEnabled = uiState.onlineSearchEnabled,
                            onPlayFromQueue = { queue, index ->
                                viewModel.playSongFromQueue(queue, index)
                                openPlayerDirect()
                            },
                            onPlaySong = viewModel::playSong,
                            onTogglePlayPause = stagePlayPause,
                            onNext = stageNext,
                            onOpenPlayer = openPlayer,
                            onOpenSettings = { showSettings = true },
                            onOpenOnlineSearch = { showOnlineSearch = true },
                            onOpenLocalMusic = openLocalMusic,
                            onOpenPlaylistDetail = { pl ->
                                currentPlaylistName = pl.name
                                playlistSongs = emptyList()
                                playlistDetailBounds = null
                                showPlaylistDetail = pl.id
                                scope.launch {
                                    val songs = viewModel.getPlaylistSongs(pl.id)
                                    if (showPlaylistDetail == pl.id) playlistSongs = songs
                                }
                                Unit
                            },
                            onOpenOnlinePlaylist = { summary -> viewModel.loadOnlinePlaylistDetail(summary.id) },
                            onDismissOnlinePlaylist = viewModel::clearOnlinePlaylistDetail,
                            onPlayOnlineTracks = { tracks, selected ->
                                viewModel.playOnlineTracks(tracks, selected)
                                openPlayerDirect()
                            },
                            onSongMore = { song ->
                                actionPlaylistId = null
                                actionSong = song
                            },
                            onDailyMix = {
                                val queue = uiState.songs
                                if (queue.isNotEmpty()) {
                                    if (!uiState.playerState.isShuffled && queue.size > 1) viewModel.toggleShuffle()
                                    viewModel.playSongFromQueue(queue, queue.indices.random())
                                    openPlayerDirect()
                                }
                            },
                            onHotSongs = {
                                val queue = hotSongsQueue(uiState.songs, uiState.repeatCountsBySongId)
                                if (queue.isNotEmpty()) {
                                    viewModel.playSongFromQueue(queue, 0)
                                    openPlayerDirect()
                                }
                            },
                            onHeartMode = {
                                if (uiState.musicMode == com.caipan.music.data.MusicMode.ONLINE) {
                                    val liked = uiState.onlineLikedSongs
                                    liked.randomOrNull()?.let { selected ->
                                        viewModel.playOnlineTracks(liked, selected)
                                        openPlayerDirect()
                                    }
                                } else {
                                    // Do not use the first arbitrary playlist. Heart mode starts
                                    // from the actual Favorites playlist, then falls back to the
                                    // local library only when Favorites is empty.
                                    val favorites = playlistItems.firstOrNull {
                                        it.id == "favorites" || it.name == "我喜欢的音乐"
                                    }
                                    scope.launch {
                                        val queue = favorites?.let { viewModel.getPlaylistSongs(it.id) }
                                            ?.ifEmpty { null }
                                            ?: uiState.songs
                                        if (queue.isNotEmpty()) {
                                            if (!uiState.playerState.isShuffled && queue.size > 1) {
                                                viewModel.toggleShuffle()
                                            }
                                            viewModel.playSongFromQueue(queue, queue.indices.random())
                                            openPlayerDirect()
                                        }
                                    }
                                }
                            },
                            miniSong = displaySong,
                            miniArtworkUri = extArt ?: displaySong?.albumArtUri,
                            onMiniArtworkBoundsChanged = { miniArtworkBounds = it },
                            playerExpanded = meloXPlayerExpanded,
                            onPlayerExpandedChange = { meloXPlayerExpanded = it },
                            settingsState = com.caipan.music.ui.melox.MeloXSettingsState(
                                isLight = isLight,
                                onlineMode = uiState.musicMode == com.caipan.music.data.MusicMode.ONLINE,
                                onlineSearchEnabled = uiState.onlineSearchEnabled,
                                neteaseNickname = uiState.neteaseSession?.nickname,
                                gitHubName = gitHubSession?.let { it.name ?: "@${it.login}" },
                                mchatNickname = oauthSession?.takeIf { it.isLoggedIn }?.nickname,
                                mchatAvatar = oauthSession?.takeIf { it.isLoggedIn }?.avatar,
                                preferredQuality = uiState.playbackSettings.preferredQuality,
                                wallpaperUri = uiState.wallpaperUri,
                                videoUri = uiState.videoUri,
                                accentColor = uiState.customBgColor,
                                accentPresets = bgPresets,
                                bgOpacity = bgOpacity,
                                bgBlur = bgBlur,
                            ),
                            settingsActions = com.caipan.music.ui.melox.MeloXSettingsActions(
                                onToggleTheme = { viewModel.toggleTheme() },
                                onToggleOnlineMode = {
                                    onlineSelectedTab = 0
                                    viewModel.setMusicMode(
                                        if (uiState.musicMode == com.caipan.music.data.MusicMode.ONLINE) {
                                            com.caipan.music.data.MusicMode.LOCAL
                                        } else {
                                            com.caipan.music.data.MusicMode.ONLINE
                                        }
                                    )
                                },
                                onOnlineSearchEnabledChange = viewModel::setOnlineSearchEnabled,
                                onNeteaseAccount = {
                                    if (uiState.neteaseSession == null) {
                                        neteaseLoginError = null
                                        showNeteaseLogin = true
                                    } else {
                                        viewModel.logoutNetease()
                                    }
                                },
                                onGitHubAccount = { showGitHubAccount = true },
                                onMchatAccount = {
                                    if (oauthSession?.isLoggedIn == true) {
                                        application.oauthManager.clearSession()
                                        prefs.edit().remove("oauth_synced_token").apply()
                                    } else {
                                        launchMChatAuth(context)
                                    }
                                },
                                onOpenEqualizer = { showEqualizer = true },
                                onOpenPlugins = { showPlugins = true },
                                onOpenUiSettings = { showUiSettings = true },
                                onOpenSkins = { showSkinSettings = true },
                                onOpenAbout = { showAbout = true },
                                onOpenProfile = { showProfile = true },
                                onPreferredQualityChange = { quality ->
                                    viewModel.updatePlaybackSettings { it.copy(preferredQuality = quality) }
                                },
                                onPickWallpaper = {
                                    wallpaperPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                },
                                onPickVideo = { videoPicker.launch("video/*") },
                                onClearBackground = {
                                    if (uiState.wallpaperUri != null) viewModel.clearWallpaper()
                                    if (uiState.videoUri != null) viewModel.clearVideo()
                                },
                                onAccentColorChange = { viewModel.setBackgroundColor(it) },
                                onBgOpacityChange = { value ->
                                    bgOpacity = value
                                    prefs.edit().putFloat("ui_bg_opacity", value).apply()
                                },
                                onBgBlurChange = { value ->
                                    bgBlur = value
                                    prefs.edit().putFloat("ui_bg_blur", value).apply()
                                },
                                onBackup = { backupExporter.launch("muse-backup.zip") },
                                onRestore = { backupImporter.launch(arrayOf("application/zip")) },
                            ),
                            pluginContent = { pluginBottomPadding, onBack ->
                                com.caipan.music.ui.melox.MeloXPluginScreen(
                                    plugins = uiState.plugins,
                                    isInstalling = uiState.pluginInstalling,
                                    message = uiState.pluginMessage,
                                    bottomPadding = pluginBottomPadding,
                                    onEnabledChange = viewModel::setPluginEnabled,
                                    onPermissionChange = viewModel::setPluginPermission,
                                    onImport = {
                                        meloXPluginImporter.launch(
                                            arrayOf("application/zip", "application/octet-stream", "*/*")
                                        )
                                    },
                                    onOpenWebUi = { id ->
                                        viewModel.openPluginWebUi(id)
                                            .onSuccess { pluginWebUi = it }
                                            .onFailure { error ->
                                                android.widget.Toast.makeText(
                                                    context,
                                                    error.message ?: "插件界面打开失败",
                                                    android.widget.Toast.LENGTH_SHORT,
                                                ).show()
                                            }
                                    },
                                    onDelete = viewModel::deleteExternalPlugin,
                                    onNavigateBack = onBack,
                                )
                            },
                            aboutContent = { aboutBottomPadding, onBack ->
                                com.caipan.music.ui.melox.MeloXAboutScreen(
                                    version = com.caipan.music.BuildConfig.VERSION_NAME,
                                    currentLanguage = currentLang,
                                    bottomPadding = aboutBottomPadding,
                                    onLanguageChanged = { code ->
                                        currentLang = code
                                        prefs.edit().putString("app_language", code).apply()
                                    },
                                    onOpenContributor = { url ->
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                                },
                                            )
                                        }
                                    },
                                    onNavigateBack = onBack,
                                )
                            },
                            moreSettingsContent = { moreBottomPadding, onBack ->
                                com.caipan.music.ui.melox.MeloXMoreSettingsScreen(
                                    bgOpacity = bgOpacity,
                                    bgBlur = bgBlur,
                                    uiStyle = uiState.uiStyle,
                                    onlineSearchEnabled = uiState.onlineSearchEnabled,
                                    isChinese = zh,
                                    bottomPadding = moreBottomPadding,
                                    onBgOpacityChange = { value ->
                                        bgOpacity = value
                                        prefs.edit().putFloat("ui_bg_opacity", value).apply()
                                    },
                                    onBgBlurChange = { value ->
                                        bgBlur = value
                                        prefs.edit().putFloat("ui_bg_blur", value).apply()
                                    },
                                    onUiStyleChange = viewModel::setUiStyle,
                                    onOnlineSearchEnabledChange = viewModel::setOnlineSearchEnabled,
                                    onBackup = { backupExporter.launch("muse-backup.zip") },
                                    onRestore = { backupImporter.launch(arrayOf("application/zip")) },
                                    onNavigateBack = onBack,
                                )
                            },
                            playerHost = { expanded, onExpandedChange, compactProgress, hostBackdrop, onProgressChange ->
                                displaySong?.let { hostSong ->
                                    MeloXPersistentPlayer(
                                        expanded = expanded,
                                        song = hostSong,
                                        isPlaying = displayPlaying,
                                        progressMs = displayProgress,
                                        durationMs = displayDuration,
                                        repeatMode = uiState.playerState.repeatMode,
                                        isShuffled = uiState.playerState.isShuffled,
                                        qualityLabel = uiState.playerState.quality,
                                        artworkUri = extArt,
                                        backdrop = hostBackdrop,
                                        compactNavigationProgress = compactProgress,
                                        onPlayPause = stagePlayPause,
                                        onNext = stageNext,
                                        onPrevious = stagePrevious,
                                        onSeek = stageSeek,
                                        onRepeatToggle = {
                                            if (!isExternalActive) {
                                                val next = when (uiState.playerState.repeatMode) {
                                                    com.caipan.music.player.RepeatMode.NONE -> com.caipan.music.player.RepeatMode.ALL
                                                    com.caipan.music.player.RepeatMode.ALL -> com.caipan.music.player.RepeatMode.ONE
                                                    com.caipan.music.player.RepeatMode.ONE -> com.caipan.music.player.RepeatMode.NONE
                                                }
                                                viewModel.setRepeatMode(next)
                                            }
                                        },
                                        onShuffleToggle = { if (!isExternalActive) viewModel.toggleShuffle() },
                                        onCycleQuality = {
                                            val entries = com.caipan.music.player.AudioQuality.entries
                                            val current = uiState.playbackSettings.preferredQuality
                                            val next = entries[(entries.indexOf(current) + 1) % entries.size]
                                            viewModel.updatePlaybackSettings { it.copy(preferredQuality = next) }
                                        },
                                        lyricsLoader = { track -> viewModel.loadLyrics(track) },
                                        onOpenActions = { actionPlaylistId = null; actionSong = hostSong },
                                        onOpenComments = {
                                            hostSong.neteaseCommentId()?.let { songId ->
                                                neteaseCommentsTarget = hostSong
                                                showNeteaseComments = true
                                                viewModel.loadNeteaseComments(songId)
                                            } ?: showCommentsUnavailableToast(context, zh)
                                        },
                                        onExpandedChange = onExpandedChange,
                                        onProgressChange = onProgressChange,
                                        bgMode = uiState.playerBgMode,
                                    )
                                }
                            },
                        )
                    } else if (uiState.musicMode == com.caipan.music.data.MusicMode.ONLINE) {
                        OnlineModeScreen(
                            home = uiState.onlineHome,
                            playlists = uiState.onlinePlaylists,
                            likedSongs = uiState.onlineLikedSongs,
                            recentSongs = uiState.onlineRecentSongs,
                            session = uiState.neteaseSession,
                            playlistDetail = uiState.onlinePlaylistDetail,
                            loading = uiState.onlineLoading,
                            error = uiState.onlineError,
                            selectedTab = onlineSelectedTab,
                            onSelectedTab = { onlineSelectedTab = it },
                            onRefresh = viewModel::refreshOnlineContent,
                            onLogin = {
                                neteaseLoginError = null
                                showNeteaseLogin = true
                            },
                            onSearch = { showOnlineSearch = true },
                            onSettings = { showSettings = true },
                            onOpenWebdav = {
                                // The online library has no source bounds for a reveal;
                                // clear any bounds retained from the local entry point.
                                webdavBounds = null
                                openWebdavImport()
                            },
                            onPlaylist = viewModel::loadOnlinePlaylistDetail,
                            onPlay = { track, queue -> viewModel.playOnlineTracks(queue, track) },
                            onPlayPodcast = viewModel::playOnlinePodcast,
                            onOpenComments = { track ->
                                if (track.source == NeteaseCatalog.NETEASE_SOURCE) {
                                    track.sourceId.toLongOrNull()?.let { songId ->
                                        neteaseCommentsTarget = track.toSong()
                                        showNeteaseComments = true
                                        viewModel.loadNeteaseComments(songId)
                                    }
                                }
                            },
                            onBackFromDetail = viewModel::clearOnlinePlaylistDetail,
                            accentColor = accentColor,
                            textColor = contentText,
                            mutedColor = contentMuted,
                            backdrop = backdrop,
                            bottomBarBackdrop = bottomTabsPageBackdrop,
                            liquidGlass = uiState.uiStyle == UiStyle.LIQUID,
                            isLightTheme = isLight,
                            isChinese = zh,
                            accountContent = { onOpenPlaylist ->
                                val onlineProfileDetails = uiState.onlineProfileDetails
                                val onlineProfileName = onlineProfileDetails?.nickname
                                    ?.takeIf { it.isNotBlank() }
                                    ?: uiState.neteaseSession?.nickname
                                    ?.takeIf { it.isNotBlank() }
                                    ?: uiState.profileName
                                val onlineProfileAvatar: String? = onlineProfileDetails?.avatarUrl
                                    ?.takeIf { it.isNotBlank() }
                                    ?: uiState.neteaseSession?.avatarUrl
                                    ?.takeIf { it.isNotBlank() }
                                    ?: uiState.profileAvatar?.toString()
                                NeteaseOnlineProfileScreen(
                                    session = uiState.neteaseSession,
                                    avatarUrl = onlineProfileAvatar,
                                    nickname = onlineProfileName,
                                    likedTracks = uiState.onlineLikedSongs,
                                    playlists = uiState.onlinePlaylists,
                                    recentTracks = uiState.onlineRecentSongs,
                                    loading = uiState.onlineLoading,
                                    backdrop = backdrop,
                                    accentColor = accentColor,
                                    isLightTheme = isLight,
                                    isChinese = zh,
                                    profileDetails = onlineProfileDetails,
                                    onDismiss = { onlineSelectedTab = 0 },
                                    onSearch = { showOnlineSearch = true },
                                    onSettings = { showSettings = true },
                                    onOpenWebdav = {
                                        webdavBounds = null
                                        openWebdavImport()
                                    },
                                    onPlaylist = onOpenPlaylist,
                                    onPlayLiked = {
                                        uiState.onlineLikedSongs.firstOrNull()?.let { first ->
                                            viewModel.playOnlineTracks(uiState.onlineLikedSongs, first)
                                        }
                                    },
                                    onLogin = {
                                        neteaseLoginError = null
                                        showNeteaseLogin = true
                                    },
                                    onPlayRecent = { track, queue ->
                                        viewModel.playOnlineTracks(queue, track)
                                    }
                                )
                            },
                            modifier = Modifier.fillMaxSize()
                        )
                    } else if (uiState.uiStyle == UiStyle.LIQUID) {
                            LiquidHomeScreen(
                                accentColor = accentColor,
                                isLightTheme = isLight,
                                contentText = contentText,
                                contentMuted = contentMuted,
                                onPlaylistsTap = openPlaylistList,
                                onLocalMusicTap = openLocalMusic,
                                onSettingsTap = { showSettings = true },
                                onWebdavTap = openWebdavImport,
                                onPickAvatar = { avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                onProfileNameChange = viewModel::setProfileName,
                                recentSongs = uiState.songs.take(12),
                                allSongs = uiState.songs,
                                profileName = uiState.profileName,
                                profileAvatar = uiState.profileAvatar,
                                listeningTimeMs = uiState.listeningTimeMs,
                                completedPlays = uiState.completedPlays,
                                repeatCount = uiState.repeatCount,
                                onRecentSongTap = { song ->
                                    viewModel.playSong(song)
                                    openPlayerDirect()
                                },
                                currentSong = uiState.playerState.currentSong,
                                isPlaying = uiState.playerState.isPlaying,
                                onPlayPause = { viewModel.togglePlayPause() },
                                onTapPlayer = openPlayer,
                                backdrop = backdrop,
                                bottomBarBackdrop = bottomTabsPageBackdrop,
                                onlineSearchEnabled = uiState.onlineSearchEnabled,
                                onOnlineSearchTap = { showOnlineSearch = true },
                                onLocalMusicBoundsChanged = { localMusicBounds = it },
                                onPlaylistsBoundsChanged = { playlistListBounds = it },
                                onWebdavBoundsChanged = { webdavBounds = it },
                                selectedTab = homeSelectedTab,
                                onSelectedTabChanged = { homeSelectedTab = it },
                                isLoading = uiState.isLoading
                            )
                    } else {
                        // CLOUD/MONET retain Muse's existing classic hierarchy.
                        HomeScreen(
                                accentColor = accentColor,
                                isLightTheme = isLight,
                                contentText = contentText,
                                contentMuted = contentMuted,
                                onPlaylistsTap = openPlaylistList,
                                onLocalMusicTap = openLocalMusic,
                                onSettingsTap = { showSettings = true },
                                onWebdavTap = openWebdavImport,
                                onPickAvatar = { avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                                onProfileNameChange = viewModel::setProfileName,
                                recentSongs = uiState.songs.take(12),
                                allSongs = uiState.songs,
                                profileName = uiState.profileName,
                                profileAvatar = uiState.profileAvatar,
                                listeningTimeMs = uiState.listeningTimeMs,
                                completedPlays = uiState.completedPlays,
                                repeatCount = uiState.repeatCount,
                                onRecentSongTap = { song ->
                                    viewModel.playSong(song)
                                    openPlayerDirect()
                                },
                                currentSong = uiState.playerState.currentSong,
                                isPlaying = uiState.playerState.isPlaying,
                                onPlayPause = { viewModel.togglePlayPause() },
                                onTapPlayer = openPlayer,
                                backdrop = backdrop,
                                bottomBarBackdrop = bottomTabsPageBackdrop,
                                onlineSearchEnabled = uiState.onlineSearchEnabled,
                                onOnlineSearchTap = { showOnlineSearch = true },
                                onLocalMusicBoundsChanged = { localMusicBounds = it },
                                onPlaylistsBoundsChanged = { playlistListBounds = it },
                                onWebdavBoundsChanged = { webdavBounds = it },
                                selectedTab = homeSelectedTab,
                                onSelectedTabChanged = { homeSelectedTab = it },
                                isLoading = uiState.isLoading
                            )
                        }
                }
                    if (localMusicVisible) {
                    val filteredSongs = uiState.filteredSongs
                    val folderSongs = remember(filteredSongs, selectedFolder) {
                        if (selectedFolder == null) emptyList() else filteredSongs.filter { it.folderPath == selectedFolder }
                    }
                    // 列表交错入场的记忆集:滚动回收后不重复播放
                    val folderStaggerKeys = remember { mutableSetOf<String>() }
                    val songStaggerKeys = remember { mutableSetOf<String>() }
                    when {
                        uiState.isLoading -> Box(Modifier.fillMaxSize()) {
                            // 骨架屏替代转圈:加载感知更快(Emil 感知性能)
                            SkeletonSongRows(count = 8) }
                        uiState.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(uiState.error!!, color = contentMuted,
                                textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp)) }
                        uiState.songs.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(if (zh) "未找到歌曲" else "No songs found",
                                color = contentMuted) }
                        selectedFolder == null -> {
                            val folders = remember(uiState.songs) { uiState.songs.groupBy { it.folderPath }.toList().sortedBy { it.first.lowercase() } }
                            LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = if (uiState.showPlayer || (externalPlayer != null && uiState.playerState.currentSong == null)) 240.dp else 24.dp)) {
                            items(folders.size, key = { folders[it].first }) { index ->
                                val (folder, songs) = folders[index]
                                Row(Modifier.fillMaxWidth().staggeredEnter(index, folder, folderStaggerKeys).pressScale().clickable { selectedFolder = folder }.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(accentColor.copy(alpha = .14f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Folder, null, tint = accentColor) }
                                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                                        Text(folder, color = contentText, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                        Text(if (zh) "${songs.size} 首歌曲" else "${songs.size} songs", color = contentMuted, fontSize = 13.sp)
                                    }
                                    Icon(Icons.Default.ChevronRight, null, tint = contentMuted)
                                }
                                if (index < folders.lastIndex) {
                                    HorizontalDivider(color = (if (bgIsDark) Color.White else Color.Black).copy(alpha = 0.08f), modifier = Modifier.padding(start = 84.dp, end = 20.dp))
                                }
                            }
                        }
                        }
                        folderSongs.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(if (zh) "未找到歌曲" else "No songs found", color = contentMuted)
                        }
                        else -> Box(Modifier.weight(1f)) {
                            val miniPlayerOverlay = uiState.showPlayer || (externalPlayer != null && uiState.playerState.currentSong == null)
                            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = if (miniPlayerOverlay) 240.dp else 24.dp)) {
                                itemsIndexed(folderSongs, key = { _, song -> song.id }) { index, song ->
                                    Box(Modifier.staggeredEnter(index, song.id.toString(), songStaggerKeys)) {
                                    SongListItem(
                                        song = song, isCurrentSong = uiState.playerState.currentSong?.id == song.id,
                                        isPlaying = uiState.playerState.isPlaying,
                                        textColor = contentText,
                                        subTextColor = contentMuted,
                                        accentColor = accentColor,
                                        batchMode = uiState.batchMode,
                                        isSelected = song.id in uiState.selectedIds,
                                        onToggleSelect = { viewModel.toggleSongSelection(song.id) },
                                        onMore = { actionPlaylistId = null; actionSong = song },
                                        onClick = {
                                            if (uiState.batchMode) viewModel.toggleSongSelection(song.id)
                                            else { viewModel.playSongFromQueue(folderSongs, index); openPlayerDirect() }
                                        }
                                    )
                                    }
                                    if (index < folderSongs.lastIndex) {
                                        HorizontalDivider(color = (if (bgIsDark) Color.White else Color.Black).copy(alpha = 0.08f), modifier = Modifier.padding(start = 76.dp, end = 20.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Mini player bar removed from here — moved to end after all overlays

            // Full Player moved to end (top of stack)

            // ── Equalizer ──
            AnimatedVisibility(visible = showEqualizer, enter = fullScreenOverlayEnter(), exit = fullScreenOverlayExit()) {
                EqualizerScreen(
                    bands = remember(eqRevision) { viewModel.eqManager.bands },
                    isEnabled = viewModel.eqManager.isEnabled,
                    presetName = viewModel.eqManager.presetName,
                    presets = viewModel.eqManager.presets,
                    onBandChange = { i, v -> viewModel.eqManager.setBandLevel(i, v) },
                    onToggle = { viewModel.eqManager.setEnabled(it) },
                    onReset = { viewModel.eqManager.resetAll() },
                    onSavePreset = { viewModel.eqManager.savePreset(it) },
                    onLoadPreset = { viewModel.eqManager.loadPreset(it) },
                    onDeletePreset = { viewModel.eqManager.deletePreset(it) },
                    onImport = { uri -> viewModel.eqManager.importFromUri(context, uri) },
                    onExport = { uri, name -> viewModel.eqManager.exportToUri(context, uri, name) },
                    onDismiss = { showEqualizer = false },
                    accentColor = accentColor,
                    isLightTheme = uiState.isLightTheme,
                    isChinese = zh,
                    backdrop = backdrop
                )
            }

            // ── Playback Plugins ──
            AnimatedVisibility(visible = showPlugins, enter = fullScreenOverlayEnter(), exit = fullScreenOverlayExit()) {
                PluginListScreen(
                    plugins = uiState.plugins,
                    onEnabledChange = viewModel::setPluginEnabled,
                    onPermissionChange = viewModel::setPluginPermission,
                    onImport = viewModel::installPlugin,
                    onOpenWebUi = { id -> viewModel.openPluginWebUi(id).onSuccess { pluginWebUi = it } },
                    onOpenExternalPlayerAccess = {
                        context.startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
                    },
                    onDeleteExternal = { id -> viewModel.deleteExternalPlugin(id) },
                    onDismiss = { showPlugins = false },
                    accentColor = accentColor,
                    isLightTheme = uiState.isLightTheme,
                    isInstalling = uiState.pluginInstalling,
                    message = uiState.pluginMessage,
                    backdrop = backdrop,
                    blurPolicy = blurPolicy,
                    onBlurMasterChange = application.globalBlurControlPlugin::setMasterEnabled,
                    onBlurLocationChange = application.globalBlurControlPlugin::setLocationEnabled,
                    onReadabilityBlurChange = application.globalBlurControlPlugin::setReadabilityBlur,
                    onlineSources = uiState.onlineSources.map { source ->
                        OnlineSourceUiModel(
                            id = source.id,
                            name = source.name,
                            version = source.version,
                            author = source.author,
                            description = source.description,
                            sourceUrl = source.originalSource,
                            enabled = source.enabled,
                            sha256 = source.sha256
                        )
                    },
                    isImportingOnlineSource = uiState.onlineSourceImporting,
                    onlineSourceMessage = uiState.onlineSourceMessage,
                    onImportOnlineSourceUrl = viewModel::importOnlineSource,
                    onImportOnlineSourceFile = viewModel::importOnlineSourceFromFile,
                    onOnlineSourceEnabledChange = viewModel::setOnlineSourceEnabled,
                    onDeleteOnlineSource = viewModel::deleteOnlineSource,
                    onOpenOnlineSourceIndex = { url ->
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                            .onFailure {
                                android.widget.Toast.makeText(context, "无法打开音源索引", android.widget.Toast.LENGTH_SHORT).show()
                            }
                    }
                )
            }

            AnimatedVisibility(
                visible = showOnlineSearch,
                enter = fadeIn(tween(MuseMotion.SearchEnter)) + scaleIn(initialScale = .96f, animationSpec = tween(MuseMotion.SearchEnter, easing = FastOutSlowInEasing)),
                exit = fadeOut(tween(MuseMotion.SearchExit)) + scaleOut(targetScale = .96f, animationSpec = tween(MuseMotion.SearchExit, easing = FastOutSlowInEasing))
            ) {
                OnlineSearchScreen(
                    catalogs = viewModel.onlineCatalogs,
                    search = viewModel::searchOnlineTracks,
                    onPlay = { track, queue ->
                        viewModel.playOnlineTracks(queue, track)
                    },
                    onBack = { showOnlineSearch = false },
                    accentColor = accentColor,
                    backdrop = backdrop,
                    isLightTheme = uiState.isLightTheme,
                    miniPlayerVisible = uiState.showPlayer && uiState.playerState.currentSong != null,
                    modifier = Modifier
                )
            }

            // ── About ──
            AnimatedVisibility(
                visible = showNeteaseLogin,
                enter = fullScreenOverlayEnter(),
                exit = fullScreenOverlayExit()
            ) {
                NeteaseLoginScreen(
                    onCookie = { cookie ->
                        viewModel.acceptNeteaseCookie(cookie) { result ->
                            result.fold(
                                onSuccess = {
                                    neteaseLoginError = null
                                    showNeteaseLogin = false
                                    viewModel.refreshOnlineContent()
                                },
                                onFailure = { neteaseLoginError = it.message ?: "NetEase login failed" }
                            )
                        }
                    },
                    onBack = { showNeteaseLogin = false },
                    errorMessage = neteaseLoginError,
                    isChinese = zh,
                    modifier = Modifier
                )
            }

            AnimatedVisibility(visible = showAbout, enter = fullScreenOverlayEnter(), exit = fullScreenOverlayExit()) {
                AboutScreen(
                    onDismiss = { showAbout = false },
                    onLanguageChanged = { lang ->
                        currentLang = lang
                        prefs.edit().putString("app_language", lang).apply()
                        (context as? android.app.Activity)?.recreate()
                    },
                    currentLanguage = currentLang,
                    accentColor = accentColor,
                    isLightTheme = uiState.isLightTheme,
                    backdrop = backdrop
                )
            }

            // ── Playlist List ──
            if (showPlaylistList) {
                Box(
                    Modifier.fillMaxSize().rectReveal(playlistListTransitionProgress, playlistListBounds, routeCornerRadiusPx)
                ) {
                PlaylistListScreen(
                    playlists = playlistItems,
                    accentColor = accentColor,
                    isLightTheme = uiState.isLightTheme,
                    onPlaylistTap = { pl, bounds ->
                        currentPlaylistName = pl.name
                        playlistSongs = emptyList()
                        playlistDetailBounds = bounds
                        showPlaylistDetail = pl.id
                        scope.launch {
                            playlistDetailProgress.snapTo(0f)
                            playlistDetailProgress.animateTo(1f, tween(MuseMotion.EnterReveal, easing = FastOutSlowInEasing))
                        }
                        scope.launch {
                            val songs = viewModel.getPlaylistSongs(pl.id)
                            if (showPlaylistDetail == pl.id) playlistSongs = songs
                        }
                    },
                    onDeletePlaylist = { id -> viewModel.deletePlaylist(id); playlistItems = viewModel.getAllPlaylists() },
                    onWebdavImport = { bounds ->
                        webdavBounds = bounds
                        openWebdavImport()
                    },
                    onDismiss = dismissPlaylistList,
                    backdrop = backdrop
                )
                }
            }

            // ── Playlist Detail ──
            showPlaylistDetail?.let { plId ->
                Box(
                    Modifier.fillMaxSize().rectReveal(playlistDetailTransitionProgress, playlistDetailBounds, routeCornerRadiusPx)
                ) {
                PlaylistDetailScreen(
                    playlistName = currentPlaylistName,
                    songs = playlistSongs,
                    coverUri = playlistItems.find { it.id == plId }?.coverUri,
                    accentColor = accentColor,
                    isLightTheme = uiState.isLightTheme,
                    onSongTap = { index -> viewModel.playPlaylist(plId, index); openPlayerDirect() },
                    onRemoveSong = { songId ->
                        viewModel.removeSongFromPlaylist(plId, songId)
                        scope.launch {
                            val songs = viewModel.getPlaylistSongs(plId)
                            if (showPlaylistDetail == plId) playlistSongs = songs
                        }
                    },
                    onSongMore = { song -> actionPlaylistId = plId; actionSong = song },
                    onChangeCover = {
                        coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onShufflePlay = {
                        viewModel.playPlaylistShuffled(plId)
                        openPlayerDirect()
                    },
                    onPlayAll = {
                        viewModel.playPlaylistSequential(plId)
                        openPlayerDirect()
                    },
                    onShare = {
                        val sendIntent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(
                                Intent.EXTRA_TEXT,
                                if (zh) "${currentPlaylistName} · ${playlistSongs.size} 首歌曲 — Muse" else "${currentPlaylistName} · ${playlistSongs.size} songs — Muse"
                            )
                        }
                        runCatching { context.startActivity(Intent.createChooser(sendIntent, null)) }
                    },
                    onDismiss = {
                        dismissPlaylistDetail()
                    },
                    backdrop = backdrop
                )
                }
            }

            AnimatedVisibility(visible = showProfile, enter = fullScreenOverlayEnter(), exit = fullScreenOverlayExit()) {
                val topPlayed = remember(uiState.repeatCountsBySongId, uiState.songs) {
                    val byId = uiState.songs.associateBy { it.id }
                    uiState.repeatCountsBySongId.mapNotNull { (id, count) -> byId[id]?.let { it to count } }
                        .sortedByDescending { it.second }
                }
                ProfileScreen(uiState.profileName, uiState.profileAvatar, uiState.listeningTimeMs,
                    uiState.completedPlays, uiState.repeatCount, uiState.songs.size,
                    topPlayed,
                    accentColor, uiState.isLightTheme, backdrop, viewModel::setProfileName,
                    // 回调稳定化：避免每 250ms uiState 刷新时因 lambda 引用变化导致 ProfileScreen 无法跳过重组
                    remember { { avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) } },
                    remember { { showProfile = false } },
                    oauthSession = oauthSession,
                    onOAuthLogin = { launchMChatAuth(context) },
                    onOAuthLogout = { application.oauthManager.clearSession() })
            }

            // ── 导入图片裁剪（壁纸/头像/歌单封面）──
            cropRequest?.let { request ->
                ImageCropDialog(
                    uri = request.uri,
                    aspectRatio = request.aspectRatio,
                    accentColor = accentColor,
                    onConfirm = { bitmap ->
                        when (request.kind) {
                            CropKind.WALLPAPER -> viewModel.saveWallpaper(bitmap)
                            CropKind.AVATAR -> viewModel.saveProfileAvatar(bitmap)
                            CropKind.COVER -> request.playlistId?.let { plId ->
                                viewModel.setPlaylistCover(plId, bitmap) { updated ->
                                    playlistItems = playlistItems.map { if (it.id == updated.id) updated else it }
                                }
                            }
                        }
                        cropRequest = null
                    },
                    onDismiss = { cropRequest = null }
                )
            }

            actionSong?.let { song ->
                SongActionsSheet(song, { actionSong = null; actionPlaylistId = null }, {
                    viewModel.selectSongs(setOf(song.id)); actionSong = null; actionPlaylistId = null; showPlaylistDialog = true
                }, actionPlaylistId?.let { playlistId -> {
                    viewModel.removeSongFromPlaylist(playlistId, song.id)
                    scope.launch {
                        val songs = viewModel.getPlaylistSongs(playlistId)
                        if (showPlaylistDetail == playlistId) playlistSongs = songs
                    }
                    actionSong = null; actionPlaylistId = null
                } }, backdrop)
            }

            // ── WebDAV Import ──
            if (showWebdavImport) {
                Box(
                    Modifier.fillMaxSize().rectReveal(webdavTransitionProgress, webdavBounds, routeCornerRadiusPx)
                ) {
                WebdavImportScreen(
                    initialConfig = viewModel.loadWebdavConfig(),
                    accentColor = accentColor,
                    isLightTheme = uiState.isLightTheme,
                    backdrop = backdrop,
                    onImport = { paths, config ->
                        viewModel.saveWebdavConfig(config)
                        val name = if (zh) "WebDAV 导入 ${java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}" else "WebDAV Import ${java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}"
                        viewModel.importFromWebdav(paths, config, name)
                        dismissWebdavImport()
                    },
                    onDismiss = dismissWebdavImport
                )
                }
            }

            // ── Add to playlist dialog (batch mode) ──
            if (showPlaylistDialog) {
                val playlists = remember { viewModel.playlistManager.getAll() }
                val playlistDialogShape = RoundedCornerShape(28.dp)
                MuseAlertDialog(
                    onDismissRequest = { showPlaylistDialog = false },
                    title = { DialogBlurEffect(if (uiState.uiStyle == UiStyle.LIQUID) 16 else 28); Text(if (zh) "添加到歌单" else "Add to Playlist", color = MaterialTheme.colorScheme.onSurface) },
                    text = {
                        Column {
                            if (playlists.isEmpty()) {
                                Text(if (zh) "还没有歌单，创建一个" else "No playlists yet, create one",
                                    color = Color(0xFF888888), fontSize = 14.sp)
                            }
                            playlists.forEach { pl ->
                                MuseTextButton(onClick = {
                                    viewModel.addSelectedToPlaylist(pl.id)
                                    showPlaylistDialog = false
                                }, modifier = Modifier.fillMaxWidth()) {
                                    Text("${pl.name} (${pl.songIds.size})", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                            OutlinedTextField(value = newPlaylistName, onValueChange = { newPlaylistName = it },
                                label = { Text(if (zh) "新建歌单名" else "New playlist name") },
                                singleLine = true, modifier = Modifier.padding(top = 8.dp))
                            MuseTextButton(onClick = {
                                if (newPlaylistName.isNotBlank()) {
                                    val id = java.util.UUID.randomUUID().toString()
                                    viewModel.playlistManager.save(Playlist(id, newPlaylistName))
                                    viewModel.addSelectedToPlaylist(id)
                                    newPlaylistName = ""
                                    showPlaylistDialog = false
                                }
                            }) {
                                Text(if (zh) "创建" else "Create", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    },
                    confirmButton = {
                        MuseTextButton(onClick = { showPlaylistDialog = false }) {
                            Text(if (zh) "取消" else "Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                    shape = playlistDialogShape
                )
            }

            // ── UI Settings ──
            AnimatedVisibility(visible = showUiSettings, enter = fullScreenOverlayEnter(), exit = fullScreenOverlayExit()) {
                UISettingsScreen(
                    initialBgOpacity = bgOpacity,
                    initialBgBlur = bgBlur,
                    isLightTheme = uiState.isLightTheme,
                    isChinese = zh,
                    accentColor = accentColor,
                    playerBgMode = uiState.playerBgMode,
                    onPlayerBgModeChanged = { mode -> viewModel.setPlayerBgMode(mode) },
                    onBgOpacityChanged = { newVal ->
                        bgOpacity = newVal
                        prefs.edit().putFloat("ui_bg_opacity", newVal).apply()
                    },
                    onBgBlurChanged = { newVal ->
                        bgBlur = newVal
                        prefs.edit().putFloat("ui_bg_blur", newVal).apply()
                    },
                    uiStyle = uiState.uiStyle,
                    onUiStyleChanged = { style -> viewModel.setUiStyle(style) },
                    onlineSearchEnabled = uiState.onlineSearchEnabled,
                    onOnlineSearchEnabledChange = viewModel::setOnlineSearchEnabled,
                    backdrop = backdrop,
                    onBackup = { backupExporter.launch("Muse-backup-${System.currentTimeMillis()}.zip") },
                    onRestore = { backupImporter.launch(arrayOf("application/zip", "application/octet-stream")) },
                    onDismiss = { showUiSettings = false }
                )
            }

            // ── Skins ──
            AnimatedVisibility(visible = showSkinSettings, enter = fullScreenOverlayEnter(), exit = fullScreenOverlayExit()) {
                SkinSettingsScreen(
                    accentColor = accentColor,
                    isLightTheme = uiState.isLightTheme,
                    onDismiss = { showSkinSettings = false }
                )
            }

            pendingRestoreUri?.let { uri ->
                MuseAlertDialog(
                    onDismissRequest = { pendingRestoreUri = null },
                    title = { Text("还原完整备份？") },
                    text = { Text("还原会覆盖当前设置、插件配置和背景文件，完成后需要重启 Muse。") },
                    confirmButton = {
                        MuseTextButton(onClick = {
                            pendingRestoreUri = null
                            scope.launch { backupMessage = backupManager.importFrom(uri).fold({ "还原完成，请重启 Muse" }, { "还原失败：${it.message}" }) }
                        }) { Text("还原") }
                    },
                    dismissButton = { MuseTextButton(onClick = { pendingRestoreUri = null }) { Text("取消") } }
                )
            }
            backupMessage?.let { message ->
                LaunchedEffect(message) {
                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                    kotlinx.coroutines.delay(2200)
                    backupMessage = null
                }
            }

            playerErrorDialog?.let { message ->
                MuseAlertDialog(
                    onDismissRequest = { playerErrorDialog = null },
                    title = { Text("播放失败") },
                    text = {
                        androidx.compose.foundation.rememberScrollState().let { scroll ->
                            Text(
                                message,
                                modifier = androidx.compose.ui.Modifier
                                    .verticalScroll(scroll)
                                    .heightIn(max = 320.dp),
                                style = androidx.compose.material3.MaterialTheme.typography.bodySmall
                            )
                        }
                    },
                    confirmButton = {
                        MuseTextButton(onClick = { playerErrorDialog = null }) { Text("知道了") }
                    }
                )
            }

            // ── Mini player bar (only on home/song list, hidden when overlays open) ──
            // MeloX 版式的迷你播放器由底部悬浮栏内嵌渲染，这里跳过。
            val miniPlayPause: () -> Unit = stagePlayPause
            val miniSeek: (Long) -> Unit = if (isExternalActive) extSeek else localSeek

            val activeSkin = application.skinManager.activeSkin()
            val skinLayout = activeSkin?.layout
            val showMiniPlayerEffective = showMiniPlayer && (skinLayout?.showMiniPlayer ?: true)
            if (showMiniPlayerEffective && displaySong != null && uiState.uiStyle != UiStyle.MELOX) {
                if (uiState.uiStyle == UiStyle.LIQUID) {
                    LiquidMiniPlayerBar(song = displaySong, isPlaying = displayPlaying,
                        progressMs = displayProgress, durationMs = displayDuration,
                        onPlayPause = miniPlayPause,
                        onTap = openPlayer,
                        onSwipeUp = openPlayer,
                        onSeek = miniSeek,
                        accentColor = accentColor,
                        backdrop = glassBackdrop,
                        externalArtUri = extArt,
                         modifier = Modifier.matchParentSize().layerBackdrop(foregroundBackdrop).graphicsLayer {
                            alpha = if (playerIsClosing) (1f - playerTransitionProgress.value).coerceIn(0f, 1f) else 1f
                        },
                        onArtworkBoundsChanged = { miniArtworkBounds = it })
                } else {
                    MiniPlayerBar(song = displaySong, isPlaying = displayPlaying,
                        progressMs = displayProgress, durationMs = displayDuration,
                        onPlayPause = miniPlayPause,
                        onTap = openPlayer,
                        onSwipeUp = openPlayer,
                        externalArtUri = extArt,
                        backdrop = glassBackdrop,
                         // Liquid keeps the floating record; custom skins still
                         // control other styles.
                         miniPlayerStyle = skinLayout?.miniPlayerStyle ?: com.caipan.music.skin.MiniPlayerStyle.RECORD,
                        showMiniArtwork = skinLayout?.showMiniArtwork ?: true,
                        onNext = stageNext,
                         modifier = Modifier.matchParentSize().layerBackdrop(foregroundBackdrop).graphicsLayer {
                            alpha = if (playerIsClosing) (1f - playerTransitionProgress.value).coerceIn(0f, 1f) else 1f
                        },
                        onArtworkBoundsChanged = { miniArtworkBounds = it })
                }
            }

            // ── Full Player (top of entire stack) ──
            if (showFullPlayer && uiState.uiStyle != UiStyle.MELOX) {
                displaySong?.let { song ->
                    val innerPlayPause: () -> Unit = stagePlayPause
                    val innerNext: () -> Unit = stageNext
                    val innerPrevious: () -> Unit = stagePrevious
                    val innerSeek: (Long) -> Unit = stageSeek
                    val revealShape = GenericShape { size, _ ->
                        val revealProgress = playerTransitionProgress.value
                        val revealOrigin = playerRevealOrigin ?: Offset(size.width / 2f, size.height / 2f)
                        val farthestX = maxOf(revealOrigin.x, size.width - revealOrigin.x)
                        val farthestY = maxOf(revealOrigin.y, size.height - revealOrigin.y)
                        val radius = kotlin.math.hypot(farthestX, farthestY) * revealProgress
                        addOval(Rect(revealOrigin - Offset(radius, radius), revealOrigin + Offset(radius, radius)))
                    }
                        if (uiState.uiStyle == UiStyle.MELOX) {
                            GlassBackdropProvider(backdrop) {
                                CompositionLocalProvider(
                                    LocalGlassColors provides defaultGlassColors(isDark = !isLight),
                                ) {
                                    MeloXPlayerScreen(
                                    song = song,
                                    isPlaying = displayPlaying,
                                    progressMs = displayProgress,
                                    durationMs = displayDuration,
                                    repeatMode = uiState.playerState.repeatMode,
                                    isShuffled = uiState.playerState.isShuffled,
                                    qualityLabel = uiState.playerState.quality,
                                    onPlayPause = innerPlayPause,
                                    onNext = innerNext,
                                    onPrevious = innerPrevious,
                                    onSeek = innerSeek,
                                    onRepeatToggle = {
                                    if (!isExternalActive) {
                                        val next = when (uiState.playerState.repeatMode) {
                                            com.caipan.music.player.RepeatMode.NONE -> com.caipan.music.player.RepeatMode.ALL
                                            com.caipan.music.player.RepeatMode.ALL -> com.caipan.music.player.RepeatMode.ONE
                                            com.caipan.music.player.RepeatMode.ONE -> com.caipan.music.player.RepeatMode.NONE
                                        }
                                        viewModel.setRepeatMode(next)
                                    }
                                    },
                                    onShuffleToggle = { if (!isExternalActive) viewModel.toggleShuffle() },
                                    onCycleQuality = {
                                    val entries = com.caipan.music.player.AudioQuality.entries
                                    val current = uiState.playbackSettings.preferredQuality
                                    val next = entries[(entries.indexOf(current) + 1) % entries.size]
                                    viewModel.updatePlaybackSettings { it.copy(preferredQuality = next) }
                                    },
                                    lyricsLoader = { s -> viewModel.loadLyrics(s) },
                                    onOpenActions = {
                                    actionPlaylistId = null
                                    actionSong = song
                                    },
                                    onOpenComments = {
                                        song.neteaseCommentId()?.let { songId ->
                                            neteaseCommentsTarget = song
                                            showNeteaseComments = true
                                            viewModel.loadNeteaseComments(songId)
                                        } ?: run {
                                            showCommentsUnavailableToast(context, zh)
                                        }
                                    },
                                    onDismiss = dismissPlayer,
                                    externalArtUri = extArt,
                                    bgMode = uiState.playerBgMode,
                                    transitionProgress = playerTransitionProgress.value,
                                    modifier = Modifier.graphicsLayer {
                                    val progress = playerTransitionProgress.value.coerceIn(0f, 1f)
                                    alpha = progress
                                    val scale = 0.96f + 0.04f * progress
                                    scaleX = scale
                                    scaleY = scale
                                    }.clip(revealShape)
                                    )
                                }
                            }
                        } else PlayerScreen(song = song, isPlaying = displayPlaying, progressMs = displayProgress, durationMs = displayDuration,
                        repeatMode = uiState.playerState.repeatMode, isShuffled = uiState.playerState.isShuffled,
                        externalArtUri = extArt,
                        onPlayPause = innerPlayPause, onNext = innerNext, onPrevious = innerPrevious, onSeek = innerSeek,
                        onRepeatToggle = {
                            if (!isExternalActive) {
                                val next = when (uiState.playerState.repeatMode) {
                                    com.caipan.music.player.RepeatMode.NONE -> com.caipan.music.player.RepeatMode.ALL
                                    com.caipan.music.player.RepeatMode.ALL -> com.caipan.music.player.RepeatMode.ONE
                                    com.caipan.music.player.RepeatMode.ONE -> com.caipan.music.player.RepeatMode.NONE
                                }; viewModel.setRepeatMode(next)
                            }
                        },
                        onShuffleToggle = { if (!isExternalActive) viewModel.toggleShuffle() },
                        onDismiss = dismissPlayer,
                        onTransferUp = {
                            val neteaseSongId = song.neteaseCommentId()
                            if (neteaseSongId != null) {
                                neteaseCommentsTarget = song
                                showNeteaseComments = true
                                viewModel.loadNeteaseComments(neteaseSongId)
                            } else {
                                val pluginGesture = viewModel.invokePluginPlayerGesture("artwork.swipeUp")
                                if (pluginGesture == null) {
                                    showCommentsUnavailableToast(context, zh)
                                } else {
                                    pluginGesture
                                        .onSuccess { pluginWebUi = it }
                                        .onFailure { error ->
                                            android.widget.Toast.makeText(
                                                context,
                                                error.message ?: if (zh) "无法打开上滑功能" else "Unable to open swipe action",
                                                android.widget.Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                }
                            }
                        },
                        onLandscapeToggle = {
                            val activity = context as? android.app.Activity
                            if (activity != null) {
                                // 横屏切换按钮：竖屏/自动旋转时点按强制横屏；已强制横屏时点按恢复系统自动旋转
                                activity.requestedOrientation =
                                    if (activity.requestedOrientation == android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE)
                                        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                    else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                            }
                        },
                        customBgColor = uiState.customBgColor, wallpaperUri = uiState.wallpaperUri, isLightTheme = uiState.isLightTheme,
                        bgMode = uiState.playerBgMode,
                        lyricsLoader = { song -> viewModel.loadLyrics(song) }, backdrop = if (isPureMonet) null else backdrop,
                        onOpenMineradioLyrics = {
                            scope.launch {
                                mineradioLyrics = viewModel.loadLyrics(song)
                                if (mineradioLyrics.isNotEmpty()) showMineradioLyrics = true
                            }
                        },
                        onFavoriteToggle = {
                            val isFavorite = viewModel.toggleFavorite(song)
                            android.widget.Toast.makeText(
                                context,
                                if (isFavorite) "已收藏到「我喜欢的音乐」" else "已取消收藏",
                                android.widget.Toast.LENGTH_SHORT
                            ).show()
                        },
                        onOpenPlaybackSettings = { showPlaybackSettings = true },
                        isChinese = zh,
                        onCycleQuality = {
                            val entries = com.caipan.music.player.AudioQuality.entries
                            val current = uiState.playbackSettings.preferredQuality
                            val next = entries[(entries.indexOf(current) + 1) % entries.size]
                            viewModel.updatePlaybackSettings { it.copy(preferredQuality = next) }
                        },
                        quality = uiState.playerState.quality,
                        showPrevNext = skinLayout?.showPrevNext == true,
                        skin = activeSkin,
                        modifier = Modifier.graphicsLayer {
                            alpha = playerTransitionProgress.value.coerceIn(0f, 1f)
                        }.clip(revealShape))
                }
            }

            if (showMineradioLyrics) {
                displaySong?.let { song ->
                    MineradioLyricsScreen(
                        song = song,
                        lyrics = mineradioLyrics,
                        progressMs = displayProgress,
                        durationMs = displayDuration,
                        isPlaying = displayPlaying,
                        accentColor = accentColor,
                        onDismiss = { showMineradioLyrics = false }
                    )
                }
            }

            if (showNeteaseComments) {
                (neteaseCommentsTarget ?: displaySong)
                    ?.takeIf { it.neteaseCommentId() != null }
                    ?.let { song ->
                        NeteaseCommentsScreen(
                            state = CommentUiState(
                                hotComments = uiState.neteaseHotComments.map { it.toCommentUiItem() },
                                latestComments = uiState.neteaseLatestComments.map { it.toCommentUiItem() },
                                totalCount = uiState.neteaseCommentsTotal.takeIf { it > 0 },
                                isInitialLoading = uiState.neteaseCommentsInitialLoading,
                                isRefreshing = uiState.neteaseCommentsRefreshing,
                                isLoadingMore = uiState.neteaseCommentsLoadingMore,
                                hasMore = uiState.neteaseCommentsHasMore,
                                errorMessage = uiState.neteaseCommentsError
                            ),
                            callbacks = CommentUiCallbacks(
                                onDismiss = {
                                    showNeteaseComments = false
                                    neteaseCommentsTarget = null
                                },
                                onRefresh = {
                                    song.neteaseCommentId()?.let {
                                        viewModel.loadNeteaseComments(it, refresh = true)
                                    }
                                },
                                onLoadMore = viewModel::loadMoreNeteaseComments
                            ),
                            songTitle = song.title,
                            songSubtitle = song.artist,
                            isChinese = zh,
                            isLightTheme = isLight,
                            accentColor = accentColor,
                            presentation = CommentsPresentation.BottomSheet,
                            backdrop = backdrop,
                            showComposer = false
                        )
                    }
            }

            // Plugin-contributed player UI must render above the full player.
            pluginWebUi?.let { session ->
                PluginWebUiScreen(
                    session = session,
                    onRequest = { request -> viewModel.executePluginWebRequest(session.pluginId, request) },
                    onHostRequest = { type, payload -> viewModel.executePluginHostRequest(session.pluginId, type, payload) },
                    onDismiss = { pluginWebUi = null },
                    isLightTheme = uiState.isLightTheme
                )
            }

            AnimatedVisibility(
                visible = showPlaybackSettings,
                enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(340, easing = FastOutSlowInEasing)) + fadeIn(tween(240, easing = FastOutSlowInEasing)),
                exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(280, easing = FastOutSlowInEasing)) + fadeOut(tween(200))
            ) {
                PlaybackSettingsSheet(
                    settings = uiState.playbackSettings,
                    sleepTimerRemainingMs = uiState.sleepTimerRemainingMs,
                    backdrop = backdrop,
                    liquidGlass = uiState.uiStyle == UiStyle.LIQUID,
                    onUpdate = { transform -> viewModel.updatePlaybackSettings(transform) },
                    onDismiss = { showPlaybackSettings = false }
                )
            }
            }
    }
    }
}

private fun NeteaseComment.toCommentUiItem(): CommentUiItem = CommentUiItem(
    id = id.toString(),
    authorName = userName,
    authorId = userId.takeIf { it > 0L }?.toString(),
    avatarUrl = avatarUrl,
    content = content,
    createdAt = timeText,
    likedCount = likedCount.coerceAtMost(Int.MAX_VALUE.toLong()).toInt(),
    likedByCurrentUser = likedByCurrentUser,
    replyCount = replyCount,
    replyToName = repliedTo?.userName,
    ipLocation = ipLocation
)

/** A NetEase comment endpoint is meaningful only for a catalog track with its provider id. */
private fun Song.neteaseCommentId(): Long? {
    if (!isOnline || onlineSource != NeteaseCatalog.NETEASE_SOURCE) return null
    return onlineSongId?.toLongOrNull()
}

private fun showCommentsUnavailableToast(context: Context, isChinese: Boolean) {
    android.widget.Toast.makeText(
        context,
        if (isChinese) "仅网易云在线歌曲支持评论" else "Comments are available for NetEase online tracks only",
        android.widget.Toast.LENGTH_SHORT
    ).show()
}

private fun launchMChatAuth(context: Context) {
    val appName = Uri.encode("Muse")
    val appPackage = Uri.encode("com.caipan.music")
    val redirectUri = Uri.encode("muse://oauth/callback")
    val authUri = Uri.parse(
        "mchat://auth?app_name=$appName&app_package=$appPackage&redirect_uri=$redirectUri"
    )
    val intent = Intent(Intent.ACTION_VIEW, authUri).apply {
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
    try {
        context.startActivity(intent)
    } catch (e: ActivityNotFoundException) {
        Toast.makeText(context, "请先安装 MChat", Toast.LENGTH_LONG).show()
    }
}
