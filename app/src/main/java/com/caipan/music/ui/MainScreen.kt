package com.caipan.music.ui

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.content.Intent
import android.provider.Settings
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.VideoFile
import androidx.compose.material.icons.filled.Wallpaper
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
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
import kotlinx.coroutines.launch
import com.caipan.music.ui.components.AboutScreen
import com.caipan.music.ui.components.EqualizerScreen
import com.caipan.music.ui.components.MiniPlayerBar
import com.caipan.music.ui.components.PlayerScreen
import com.caipan.music.ui.components.PlaylistDetailScreen
import com.caipan.music.ui.components.PlaylistListScreen
import com.caipan.music.ui.components.SongListItem
import com.caipan.music.ui.components.UISettingsScreen
import com.caipan.music.ui.components.HomeScreen
import com.caipan.music.ui.components.MonetHomeScreen
import com.caipan.music.ui.components.MonetMiniPlayerBar
import com.caipan.music.ui.components.MonetPlayerScreen
import com.caipan.music.ui.components.PluginListScreen
import com.caipan.music.ui.components.PluginWebUiScreen
import com.caipan.music.plugin.PluginWebUiSession
import com.caipan.music.ui.components.WebdavImportScreen
import com.caipan.music.ui.components.ProfileScreen
import com.caipan.music.ui.components.SongActionsSheet
import com.caipan.music.MuseApplication
import com.caipan.music.ui.components.DialogBlurEffect
import com.caipan.music.ui.components.LocalMuseBlurPolicy
import com.caipan.music.ui.components.LocalMuseLiquidGlass
import com.caipan.music.ui.components.museGlass
import com.caipan.music.ui.theme.MusicPlayerTheme
import com.kyant.backdrop.backdrops.layerBackdrop
import com.kyant.backdrop.backdrops.rememberLayerBackdrop
import com.caipan.music.viewmodel.MusicViewModel
import com.caipan.music.viewmodel.UiStyle

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
    var showFullPlayer by remember { mutableStateOf(false) }
    var showSettings by remember { mutableStateOf(false) }
    var showEqualizer by remember { mutableStateOf(false) }
    var showAbout by remember { mutableStateOf(false) }
    var showUiSettings by remember { mutableStateOf(false) }
    var showProfile by remember { mutableStateOf(false) }
    var showPlugins by remember { mutableStateOf(false) }
    var pluginWebUi by remember { mutableStateOf<PluginWebUiSession?>(null) }
    var actionSong by remember { mutableStateOf<Song?>(null) }
    var actionPlaylistId by remember { mutableStateOf<String?>(null) }
    val isLight = uiState.isLightTheme
    val scope = rememberCoroutineScope()
    val backupManager = remember { MuseBackupManager(context) }
    var pendingRestoreUri by remember { mutableStateOf<Uri?>(null) }
    var backupMessage by remember { mutableStateOf<String?>(null) }
    val prefs = context.getSharedPreferences("muse_prefs", 0)
    var bgOpacity by remember { mutableStateOf(prefs.getFloat("ui_bg_opacity", -1f).let { it2 -> if (it2 < 0f) 0.5f else it2 }) }
    var currentLang by remember { mutableStateOf(prefs.getString("app_language", "zh") ?: "zh") }
    val backdrop = rememberLayerBackdrop()
    // 强调色始终用于文字、图标和交互状态；Liquid Glass 材质本身在 museGlass() 内保持中性。
    val accentColor = uiState.customBgColor ?: Color(0xFFFA2D55)

    // Playlist navigation
    var showHome by remember { mutableStateOf(true) }
    var showPlaylistList by remember { mutableStateOf(false) }
    var showPlaylistDetail by remember { mutableStateOf<String?>(null) }
    var currentPlaylistName by remember { mutableStateOf("") }
    var playlistSongs by remember { mutableStateOf<List<Song>>(emptyList()) }
    var showWebdavImport by remember { mutableStateOf(false) }
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
    val wallpaperPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) viewModel.saveWallpaper(uri) }
    val videoPicker = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
        if (uri != null) viewModel.saveVideo(uri) }
    val coverPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        val plId = showPlaylistDetail
        if (uri != null && plId != null) {
            viewModel.setPlaylistCover(plId, uri) { updated ->
                playlistItems = playlistItems.map { if (it.id == updated.id) updated else it }
            }
        }
    }
    val avatarPicker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) viewModel.saveProfileAvatar(uri)
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
    BackHandler(
        enabled = showFullPlayer || showSettings || showEqualizer || showAbout || showUiSettings || showProfile || showPlugins || pluginWebUi != null ||
            showPlaylistList || showPlaylistDetail != null || showWebdavImport || selectedFolder != null || !showHome
    ) {
        when {
            pluginWebUi != null -> pluginWebUi = null
            showFullPlayer -> showFullPlayer = false
            showSettings -> showSettings = false
            showEqualizer -> showEqualizer = false
            showAbout -> showAbout = false
            showUiSettings -> showUiSettings = false
            showProfile -> showProfile = false
            showPlugins -> showPlugins = false
            showPlaylistDetail != null -> { showPlaylistDetail = null; showPlaylistList = true }
            showWebdavImport -> showWebdavImport = false
            showPlaylistList -> showPlaylistList = false
            selectedFolder != null -> { selectedFolder = null; if (uiState.batchMode) viewModel.toggleBatchMode() }
            else -> showHome = true
        }
    }

    val zh = currentLang == "zh"

    MusicPlayerTheme(darkTheme = !isLight, primaryColor = accentColor) {
    // ── Settings Sheet ──
    if (showSettings) {
        ModalBottomSheet(onDismissRequest = { showSettings = false },
            containerColor = Color.Transparent,
            scrimColor = Color.Black.copy(alpha = .18f),
            tonalElevation = 0.dp,
            shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)) {
            val settingsShape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
            Column(
                Modifier.fillMaxWidth()
                    .museGlass(
                        backdrop,
                        settingsShape,
                        MaterialTheme.colorScheme.surface.copy(alpha = .36f),
                        liquidGlass = uiState.uiStyle == UiStyle.MONET
                    )
                    .padding(horizontal = 24.dp).padding(bottom = 32.dp).navigationBarsPadding()
            ) {
                DialogBlurEffect()
                Text(if (zh) "设置" else "Settings", color = if (isLight) Color(0xFF1C1C1E) else Color.White, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(20.dp))
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.LightMode, contentDescription = null, tint = if (isLight) Color(0xFF1C1C1E) else Color(0xFF888888))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (zh) "浅色模式" else "Light Mode", color = if (isLight) Color(0xFF1C1C1E) else Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(if (zh) "切换浅色/深色主题" else "Switch between light and dark theme", color = Color(0xFF888888), fontSize = 12.sp)
                    }
                    Switch(checked = isLight, onCheckedChange = { viewModel.toggleTheme() }, colors = SwitchDefaults.colors(checkedTrackColor = accentColor))
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth().clickable { showSettings = false; showEqualizer = true }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.GraphicEq, contentDescription = null, tint = if (isLight) Color(0xFF1C1C1E) else Color(0xFF888888))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (zh) "均衡器" else "Equalizer", color = if (isLight) Color(0xFF1C1C1E) else Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(if (zh) "导入预设、调整频段" else "Import presets, adjust bands", color = Color(0xFF888888), fontSize = 12.sp)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF888888), modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth().clickable { showSettings = false; showPlugins = true }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Extension, contentDescription = null, tint = if (isLight) Color(0xFF1C1C1E) else Color(0xFF888888))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (zh) "插件" else "Plugins", color = if (isLight) Color(0xFF1C1C1E) else Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(if (zh) "安装、授权和管理播放插件" else "Install and manage playback plugins", color = Color(0xFF888888), fontSize = 12.sp)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF888888), modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth().clickable { showSettings = false; showUiSettings = true }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Settings, contentDescription = null, tint = if (isLight) Color(0xFF1C1C1E) else Color(0xFF888888))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (zh) "界面设置" else "UI Settings", color = if (isLight) Color(0xFF1C1C1E) else Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(if (zh) "背景透明度等" else "Background opacity etc.", color = Color(0xFF888888), fontSize = 12.sp)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF888888), modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.height(16.dp))
                Row(Modifier.fillMaxWidth().clickable { showSettings = false; showAbout = true }, verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.Info, contentDescription = null, tint = if (isLight) Color(0xFF1C1C1E) else Color(0xFF888888))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(if (zh) "关于" else "About", color = if (isLight) Color(0xFF1C1C1E) else Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(if (zh) "版本信息、语言切换" else "Version info, language", color = Color(0xFF888888), fontSize = 12.sp)
                    }
                    Icon(Icons.Default.ChevronRight, null, tint = Color(0xFF888888), modifier = Modifier.size(20.dp))
                }
                Spacer(Modifier.height(24.dp))
                Text(if (zh) "壁纸" else "Wallpaper", color = if (isLight) Color(0xFF1C1C1E) else Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(if (zh) "作为歌曲列表和播放器背景" else "Background for song list & player", color = Color(0xFF888888), fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (uiState.wallpaperUri != null) {
                        Box(Modifier.size(64.dp).clip(RoundedCornerShape(12.dp)).background(Color(0xFF333333))) {
                            AsyncImage(model = uiState.wallpaperUri, contentDescription = null, modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                        }
                        Spacer(Modifier.width(12.dp))
                    }
                    Button(onClick = { wallpaperPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isLight) Color(0xFFE5E5EA) else Color(0xFF333333)),
                        shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.Wallpaper, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (uiState.wallpaperUri != null) (if (zh) "更换" else "Change") else (if (zh) "选择图片" else "Choose Image"),
                            color = if (isLight) Color(0xFF1C1C1E) else Color.White)
                    }
                    if (uiState.wallpaperUri != null) {
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = { viewModel.clearWallpaper() }) { Icon(Icons.Default.Delete, if (zh) "移除" else "Remove", tint = Color(0xFF888888)) }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(if (zh) "视频背景" else "Video Background", color = if (isLight) Color(0xFF1C1C1E) else Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(4.dp))
                Text(if (zh) "歌曲列表背后循环播放视频" else "Looping video behind song list", color = Color(0xFF888888), fontSize = 12.sp)
                Spacer(Modifier.height(12.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Button(onClick = { videoPicker.launch("video/*") },
                        colors = ButtonDefaults.buttonColors(containerColor = if (isLight) Color(0xFFE5E5EA) else Color(0xFF333333)),
                        shape = RoundedCornerShape(12.dp)) {
                        Icon(Icons.Default.VideoFile, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (uiState.videoUri != null) (if (zh) "更换" else "Change") else (if (zh) "选择视频" else "Choose Video"),
                            color = if (isLight) Color(0xFF1C1C1E) else Color.White)
                    }
                    if (uiState.videoUri != null) {
                        Spacer(Modifier.width(8.dp))
                        IconButton(onClick = { viewModel.clearVideo() }) { Icon(Icons.Default.Delete, if (zh) "移除" else "Remove", tint = Color(0xFF888888)) }
                    }
                }
                Spacer(Modifier.height(24.dp))
                Text(if (zh) "强调色" else "Accent Color", color = if (isLight) Color(0xFF1C1C1E) else Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Text(
                    if (uiState.uiStyle == UiStyle.MONET)
                        (if (zh) "仅用于文字、图标与交互状态，不会染色玻璃" else "Content and controls only; glass stays neutral")
                    else (if (zh) "用于界面重点内容" else "Used for highlighted content"),
                    color = Color(0xFF888888), fontSize = 12.sp, modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
                    bgPresets.forEach { (name, color) ->
                        Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.clickable { viewModel.setBackgroundColor(color) }) {
                            Box(Modifier.size(44.dp).clip(CircleShape).background(color ?: if (isLight) Color(0xFFE5E5EA) else Color(0xFF333333)), contentAlignment = Alignment.Center) {
                                if (color == null) Text("🎨", fontSize = 18.sp)
                                if ((color == null && uiState.customBgColor == null) || (color != null && color == uiState.customBgColor)) Text("✓", color = if (isLight) Color.Black else Color.White, fontSize = 14.sp, fontWeight = FontWeight.Bold)
                            }
                            Text(name, color = Color(0xFF888888), fontSize = 10.sp, modifier = Modifier.padding(top = 4.dp))
                        }
                    }
                }
            }
        }
    }

        CompositionLocalProvider(
            LocalMuseLiquidGlass provides (uiState.uiStyle == UiStyle.MONET),
            LocalMuseBlurPolicy provides blurPolicy
        ) {
        Box(Modifier.fillMaxSize()) {
            // Capture only the background. Glass overlays must remain siblings of this node.
            Box(Modifier.matchParentSize().layerBackdrop(backdrop)) {
            if (uiState.wallpaperUri != null) {
                AsyncImage(model = uiState.wallpaperUri, contentDescription = null,
                    modifier = Modifier.fillMaxSize().scale(1.1f), contentScale = ContentScale.Crop)
                Box(Modifier.fillMaxSize().background(
                    if (isLight) Color.White.copy(alpha = bgOpacity) else Color.Black.copy(alpha = bgOpacity)))
            } else if (uiState.videoUri != null) {
                val videoUri = uiState.videoUri!!
                val playVideoBackground = showHome && !showFullPlayer && !showSettings && !showEqualizer &&
                    !showAbout && !showUiSettings && !showPlugins && !showPlaylistList && showPlaylistDetail == null && !showWebdavImport
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

            Column(Modifier.fillMaxSize()) {
                if (!showHome) {
                    // ── Header (only visible when NOT on home) ──
                    Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 48.dp, end = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { if (selectedFolder != null) selectedFolder = null else showHome = true; showPlaylistList = false; showPlaylistDetail = null; showWebdavImport = false }) {
                            Icon(Icons.Default.ArrowBack, null,
                                tint = if (isLight) Color(0xFF3A3A3C) else Color.White.copy(alpha = 0.85f))
                        }
                        Text(selectedFolder ?: if (zh) "本地歌曲" else "Local music", color = if (isLight) Color(0xFF1C1C1E) else Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f), maxLines = 1)
                        if (uiState.batchMode) {
                            Text(if (zh) "已选 ${uiState.selectedIds.size} 首" else "${uiState.selectedIds.size} selected",
                                color = accentColor, fontSize = 13.sp, modifier = Modifier.padding(end = 8.dp))
                        }
                        val btnBg = if (isLight) Color.Black.copy(alpha = 0.05f) else Color.White.copy(alpha = 0.1f)
                        val btnTint = if (isLight) Color(0xFF3A3A3C) else Color.White.copy(alpha = 0.85f)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                            if (selectedFolder != null) Box(Modifier.size(40.dp).clip(CircleShape)
                                .background(if (uiState.batchMode) accentColor.copy(alpha = 0.18f) else btnBg)
                                .clickable { viewModel.toggleBatchMode() }, contentAlignment = Alignment.Center) {
                                Icon(Icons.Default.PlaylistAddCheck, if (zh) "多选" else "Batch",
                                    tint = if (uiState.batchMode) accentColor else btnTint, modifier = Modifier.size(22.dp))
                            }
                        }
                    }

                    // ── Batch mode action bar ──
                    if (uiState.batchMode) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            TextButton(onClick = { viewModel.selectSongs(uiState.filteredSongs.filter { it.folderPath == selectedFolder }.map { it.id }.toSet()) }) {
                                Text(if (zh) "全选" else "Select All", fontSize = 12.sp, color = accentColor)
                            }
                            TextButton(onClick = { showPlaylistDialog = true }) {
                                Text(if (zh) "添加到歌单" else "Add to Playlist", fontSize = 12.sp, color = accentColor)
                            }
                            TextButton(onClick = {
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
                        placeholder = { Text(if (zh) "搜索歌曲…" else "Search songs...", color = if (isLight) Color(0xFF8E8E93) else Color.White.copy(alpha = 0.3f)) },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp).clip(RoundedCornerShape(12.dp)),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedTextColor = if (isLight) Color(0xFF1C1C1E) else Color.White,
                            unfocusedTextColor = if (isLight) Color(0xFF1C1C1E) else Color.White,
                            focusedBorderColor = if (isLight) Color(0xFFD1D1D6) else Color.White.copy(alpha = 0.2f),
                            unfocusedBorderColor = if (isLight) Color(0xFFE5E5EA) else Color.White.copy(alpha = 0.1f),
                            cursorColor = accentColor),
                        singleLine = true, keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = {}), shape = RoundedCornerShape(12.dp))
                }

                // ── Content ──
                if (showHome) {
                    Box(Modifier.weight(1f)) {
                        if (uiState.uiStyle == UiStyle.MONET) {
                            MonetHomeScreen(
                                accentColor = accentColor,
                                isLightTheme = isLight,
                                onPlaylistsTap = { showPlaylistList = true },
                                onLocalMusicTap = { showHome = false },
                                onSettingsTap = { showSettings = true },
                                onWebdavTap = {
                                    showWebdavImport = true
                                },
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
                                    showFullPlayer = true
                                },
                                currentSong = uiState.playerState.currentSong,
                                isPlaying = uiState.playerState.isPlaying,
                                onPlayPause = { viewModel.togglePlayPause() },
                                onTapPlayer = { showFullPlayer = true },
                                backdrop = backdrop
                            )
                        } else {
                            HomeScreen(
                                accentColor = accentColor,
                                isLightTheme = isLight,
                                onPlaylistsTap = { showPlaylistList = true },
                                onLocalMusicTap = { showHome = false },
                                onSettingsTap = { showSettings = true },
                                onWebdavTap = {
                                    showWebdavImport = true
                                },
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
                                    showFullPlayer = true
                                },
                                currentSong = uiState.playerState.currentSong,
                                isPlaying = uiState.playerState.isPlaying,
                                onPlayPause = { viewModel.togglePlayPause() },
                                onTapPlayer = { showFullPlayer = true },
                                backdrop = backdrop
                            )
                        }
                    }
                } else {
                    val folderSongs = if (selectedFolder == null) emptyList() else uiState.filteredSongs.filter { it.folderPath == selectedFolder }
                    when {
                        uiState.isLoading -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(color = accentColor) }
                        uiState.error != null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(uiState.error!!, color = if (isLight) Color(0xFF8E8E93) else Color.White.copy(alpha = 0.4f),
                                textAlign = TextAlign.Center, modifier = Modifier.padding(32.dp)) }
                        uiState.songs.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(if (zh) "未找到歌曲" else "No songs found",
                                color = if (isLight) Color(0xFF8E8E93) else Color.White.copy(alpha = 0.3f)) }
                        selectedFolder == null -> LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 96.dp)) {
                            val folders = uiState.songs.groupBy { it.folderPath }.toList().sortedBy { it.first.lowercase() }
                            items(folders.size) { index ->
                                val (folder, songs) = folders[index]
                                Row(Modifier.fillMaxWidth().clickable { selectedFolder = folder }.padding(horizontal = 20.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(accentColor.copy(alpha = .14f)), contentAlignment = Alignment.Center) { Icon(Icons.Default.Folder, null, tint = accentColor) }
                                    Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                                        Text(folder, color = if (isLight) Color(0xFF1C1C1E) else Color.White, fontWeight = FontWeight.SemiBold, maxLines = 1)
                                        Text(if (zh) "${songs.size} 首歌曲" else "${songs.size} songs", color = if (isLight) Color(0xFF8E8E93) else Color.White.copy(.5f), fontSize = 13.sp)
                                    }
                                    Icon(Icons.Default.ChevronRight, null, tint = if (isLight) Color.Gray else Color.White.copy(.4f))
                                }
                                if (index < folders.lastIndex) {
                                    HorizontalDivider(color = (if (isLight) Color.Black else Color.White).copy(alpha = 0.08f), modifier = Modifier.padding(start = 84.dp, end = 20.dp))
                                }
                            }
                        }
                        folderSongs.isEmpty() -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                            Text(if (zh) "未找到歌曲" else "No songs found", color = if (isLight) Color.Gray else Color.White.copy(.4f))
                        }
                        else -> Box(Modifier.weight(1f)) {
                            LazyColumn(modifier = Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = if (uiState.showPlayer) 96.dp else 24.dp)) {
                                itemsIndexed(folderSongs) { index, song ->
                                    SongListItem(
                                        song = song, isCurrentSong = uiState.playerState.currentSong?.id == song.id,
                                        isPlaying = uiState.playerState.isPlaying,
                                        textColor = if (isLight) Color(0xFF1C1C1E) else Color.White,
                                        subTextColor = if (isLight) Color(0xFF8E8E93) else Color.White.copy(alpha = 0.5f),
                                        accentColor = accentColor,
                                        batchMode = uiState.batchMode,
                                        isSelected = song.id in uiState.selectedIds,
                                        onToggleSelect = { viewModel.toggleSongSelection(song.id) },
                                        onMore = { actionPlaylistId = null; actionSong = song },
                                        onClick = {
                                            if (uiState.batchMode) viewModel.toggleSongSelection(song.id)
                                            else { viewModel.playSongFromQueue(folderSongs, index); showFullPlayer = true }
                                        }
                                    )
                                    if (index < folderSongs.lastIndex) {
                                        HorizontalDivider(color = (if (isLight) Color.Black else Color.White).copy(alpha = 0.08f), modifier = Modifier.padding(start = 76.dp, end = 20.dp))
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
            AnimatedVisibility(visible = showEqualizer, enter = slideInHorizontally(initialOffsetX = { it / 3 }, animationSpec = tween(320)) + fadeIn(tween(220)), exit = slideOutHorizontally(targetOffsetX = { it / 3 }, animationSpec = tween(260)) + fadeOut(tween(180))) {
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
            AnimatedVisibility(visible = showPlugins, enter = slideInHorizontally(initialOffsetX = { it / 3 }, animationSpec = tween(320)) + fadeIn(tween(220)), exit = slideOutHorizontally(targetOffsetX = { it / 3 }, animationSpec = tween(260)) + fadeOut(tween(180))) {
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
                    onReadabilityBlurChange = application.globalBlurControlPlugin::setReadabilityBlur
                )
            }

            // ── About ──
            AnimatedVisibility(visible = showAbout, enter = slideInHorizontally(initialOffsetX = { it / 3 }, animationSpec = tween(320)) + fadeIn(tween(220)), exit = slideOutHorizontally(targetOffsetX = { it / 3 }, animationSpec = tween(260)) + fadeOut(tween(180))) {
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
            AnimatedVisibility(visible = showPlaylistList, enter = slideInHorizontally(initialOffsetX = { it / 3 }, animationSpec = tween(320)) + fadeIn(tween(220)), exit = slideOutHorizontally(targetOffsetX = { it / 3 }, animationSpec = tween(260)) + fadeOut(tween(180))) {
                PlaylistListScreen(
                    playlists = playlistItems,
                    accentColor = accentColor,
                    isLightTheme = uiState.isLightTheme,
                    onPlaylistTap = { pl ->
                        currentPlaylistName = pl.name
                        playlistSongs = emptyList()
                        showPlaylistList = false
                        showPlaylistDetail = pl.id
                        scope.launch {
                            playlistSongs = viewModel.getPlaylistSongs(pl.id)
                        }
                    },
                    onDeletePlaylist = { id -> viewModel.deletePlaylist(id); playlistItems = viewModel.getAllPlaylists() },
                    onWebdavImport = {
                        showPlaylistList = false
                        showWebdavImport = true
                    },
                    onDismiss = { showPlaylistList = false },
                    backdrop = backdrop
                )
            }

            // ── Playlist Detail ──
            AnimatedVisibility(
                visible = showPlaylistDetail != null,
                enter = slideInHorizontally(initialOffsetX = { it / 3 }, animationSpec = tween(320)) + fadeIn(tween(220)),
                exit = slideOutHorizontally(targetOffsetX = { it / 3 }, animationSpec = tween(260)) + fadeOut(tween(180))
            ) {
                val plId = showPlaylistDetail ?: return@AnimatedVisibility
                PlaylistDetailScreen(
                    playlistName = currentPlaylistName,
                    songs = playlistSongs,
                    coverUri = playlistItems.find { it.id == plId }?.coverUri,
                    accentColor = accentColor,
                    isLightTheme = uiState.isLightTheme,
                    onSongTap = { index -> viewModel.playPlaylist(plId, index); showFullPlayer = true },
                    onRemoveSong = { songId ->
                        viewModel.removeSongFromPlaylist(plId, songId)
                        scope.launch { playlistSongs = viewModel.getPlaylistSongs(plId) }
                    },
                    onSongMore = { song -> actionPlaylistId = plId; actionSong = song },
                    onChangeCover = {
                        coverPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    },
                    onDismiss = {
                        showPlaylistDetail = null
                        showPlaylistList = true
                    },
                    backdrop = backdrop
                )
            }

            AnimatedVisibility(visible = showProfile, enter = fadeIn() + slideInHorizontally { it / 3 }, exit = fadeOut() + slideOutHorizontally { it / 3 }) {
                ProfileScreen(uiState.profileName, uiState.profileAvatar, uiState.listeningTimeMs,
                    uiState.completedPlays, uiState.repeatCount, uiState.songs.size,
                    uiState.repeatCountsBySongId.mapNotNull { (id, count) -> uiState.songs.find { it.id == id }?.let { it to count } }.sortedByDescending { it.second },
                    accentColor, uiState.isLightTheme, backdrop, viewModel::setProfileName,
                    { avatarPicker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    { showProfile = false })
            }

            actionSong?.let { song ->
                SongActionsSheet(song, { actionSong = null; actionPlaylistId = null }, {
                    viewModel.selectSongs(setOf(song.id)); actionSong = null; actionPlaylistId = null; showPlaylistDialog = true
                }, actionPlaylistId?.let { playlistId -> {
                    viewModel.removeSongFromPlaylist(playlistId, song.id)
                    scope.launch { playlistSongs = viewModel.getPlaylistSongs(playlistId) }
                    actionSong = null; actionPlaylistId = null
                } }, backdrop)
            }

            // ── WebDAV Import ──
            AnimatedVisibility(visible = showWebdavImport, enter = slideInHorizontally(initialOffsetX = { it / 3 }, animationSpec = tween(320)) + fadeIn(tween(220)), exit = slideOutHorizontally(targetOffsetX = { it / 3 }, animationSpec = tween(260)) + fadeOut(tween(180))) {
                WebdavImportScreen(
                    initialConfig = viewModel.loadWebdavConfig(),
                    accentColor = accentColor,
                    isLightTheme = uiState.isLightTheme,
                    backdrop = backdrop,
                    onImport = { paths, config ->
                        viewModel.saveWebdavConfig(config)
                        val name = if (zh) "WebDAV 导入 ${java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}" else "WebDAV Import ${java.text.SimpleDateFormat("MM/dd HH:mm", java.util.Locale.getDefault()).format(java.util.Date())}"
                        viewModel.importFromWebdav(paths, config, name)
                        showWebdavImport = false
                        showPlaylistList = true
                    },
                    onDismiss = {
                        showWebdavImport = false
                        showPlaylistList = true
                    }
                )
            }

            // ── Add to playlist dialog (batch mode) ──
            if (showPlaylistDialog) {
                val playlists = remember { viewModel.playlistManager.getAll() }
                val playlistDialogShape = RoundedCornerShape(28.dp)
                AlertDialog(
                    onDismissRequest = { showPlaylistDialog = false },
                    modifier = Modifier.museGlass(
                        if (uiState.uiStyle == UiStyle.MONET) null else backdrop,
                        playlistDialogShape,
                        MaterialTheme.colorScheme.surface.copy(alpha = .36f)
                    ),
                    title = { DialogBlurEffect(if (uiState.uiStyle == UiStyle.MONET) 16 else 28); Text(if (zh) "添加到歌单" else "Add to Playlist", color = MaterialTheme.colorScheme.onSurface) },
                    text = {
                        Column {
                            if (playlists.isEmpty()) {
                                Text(if (zh) "还没有歌单，创建一个" else "No playlists yet, create one",
                                    color = Color(0xFF888888), fontSize = 14.sp)
                            }
                            playlists.forEach { pl ->
                                TextButton(onClick = {
                                    viewModel.addSelectedToPlaylist(pl.id)
                                    showPlaylistDialog = false
                                }, modifier = Modifier.fillMaxWidth()) {
                                    Text("${pl.name} (${pl.songIds.size})", color = accentColor)
                                }
                            }
                            OutlinedTextField(value = newPlaylistName, onValueChange = { newPlaylistName = it },
                                label = { Text(if (zh) "新建歌单名" else "New playlist name") },
                                singleLine = true, modifier = Modifier.padding(top = 8.dp))
                            TextButton(onClick = {
                                if (newPlaylistName.isNotBlank()) {
                                    val id = java.util.UUID.randomUUID().toString()
                                    viewModel.playlistManager.save(Playlist(id, newPlaylistName))
                                    viewModel.addSelectedToPlaylist(id)
                                    newPlaylistName = ""
                                    showPlaylistDialog = false
                                }
                            }) {
                                Text(if (zh) "创建" else "Create", color = accentColor)
                            }
                        }
                    },
                    confirmButton = {
                        TextButton(onClick = { showPlaylistDialog = false }) {
                            Text(if (zh) "取消" else "Cancel", color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    },
                    containerColor = Color.Transparent,
                    tonalElevation = 0.dp,
                    shape = playlistDialogShape
                )
            }

            // ── UI Settings ──
            AnimatedVisibility(visible = showUiSettings, enter = slideInHorizontally(initialOffsetX = { it / 3 }, animationSpec = tween(320)) + fadeIn(tween(220)), exit = slideOutHorizontally(targetOffsetX = { it / 3 }, animationSpec = tween(260)) + fadeOut(tween(180))) {
                UISettingsScreen(
                    initialBgOpacity = bgOpacity,
                    isLightTheme = uiState.isLightTheme,
                    isChinese = zh,
                    accentColor = accentColor,
                    playerBgMode = uiState.playerBgMode,
                    onPlayerBgModeChanged = { mode -> viewModel.setPlayerBgMode(mode) },
                    onBgOpacityChanged = { newVal ->
                        bgOpacity = newVal
                        prefs.edit().putFloat("ui_bg_opacity", newVal).apply()
                    },
                    uiStyle = uiState.uiStyle,
                    onUiStyleChanged = { style -> viewModel.setUiStyle(style) },
                    backdrop = backdrop,
                    onBackup = { backupExporter.launch("Muse-backup-${System.currentTimeMillis()}.zip") },
                    onRestore = { backupImporter.launch(arrayOf("application/zip", "application/octet-stream")) },
                    onDismiss = { showUiSettings = false }
                )
            }

            pendingRestoreUri?.let { uri ->
                AlertDialog(
                    onDismissRequest = { pendingRestoreUri = null },
                    title = { Text("还原完整备份？") },
                    text = { Text("还原会覆盖当前设置、插件配置和背景文件，完成后需要重启 Muse。") },
                    confirmButton = {
                        TextButton(onClick = {
                            pendingRestoreUri = null
                            scope.launch { backupMessage = backupManager.importFrom(uri).fold({ "还原完成，请重启 Muse" }, { "还原失败：${it.message}" }) }
                        }) { Text("还原") }
                    },
                    dismissButton = { TextButton(onClick = { pendingRestoreUri = null }) { Text("取消") } }
                )
            }
            backupMessage?.let { message ->
                LaunchedEffect(message) {
                    android.widget.Toast.makeText(context, message, android.widget.Toast.LENGTH_LONG).show()
                    kotlinx.coroutines.delay(2200)
                    backupMessage = null
                }
            }

            // ── Mini player bar (only on home/song list, hidden when overlays open) ──
            // 只有当前显示外部播放器时才允许使用外部封面；Muse 本地播放必须使用本地歌曲封面。
            val isExternalActive = externalPlayer != null && uiState.playerState.currentSong == null
            val extArt = if (isExternalActive) externalPlayer?.cachedArtPath?.let { Uri.fromFile(java.io.File(it)) } else null
            val showMiniPlayer = (uiState.showPlayer || isExternalActive) &&
                (uiState.playerState.currentSong != null || externalPlayer != null) &&
                showFullPlayer == false &&
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
            val miniPlayPause: () -> Unit = if (isExternalActive) extPlayPause else localPlayPause
            val miniSeek: (Long) -> Unit = if (isExternalActive) extSeek else localSeek

            if (showMiniPlayer && displaySong != null) {
                if (uiState.uiStyle == UiStyle.MONET) {
                    MonetMiniPlayerBar(song = displaySong, isPlaying = displayPlaying,
                        progressMs = displayProgress, durationMs = displayDuration,
                        onPlayPause = miniPlayPause,
                        onTap = { showFullPlayer = true },
                        onSwipeUp = { showFullPlayer = true },
                        onSeek = miniSeek,
                        accentColor = accentColor,
                        backdrop = backdrop,
                        externalArtUri = extArt,
                        modifier = Modifier.matchParentSize())
                } else {
                    MiniPlayerBar(song = displaySong, isPlaying = displayPlaying,
                        progressMs = displayProgress, durationMs = displayDuration,
                        onPlayPause = miniPlayPause,
                        onTap = { showFullPlayer = true },
                        onSwipeUp = { showFullPlayer = true },
                        externalArtUri = extArt,
                        backdrop = backdrop,
                        modifier = Modifier.matchParentSize())
                }
            }

            // ── Full Player (top of entire stack) ──
            AnimatedVisibility(visible = showFullPlayer,
                enter = slideInVertically(initialOffsetY = { it }, animationSpec = tween(400, easing = FastOutSlowInEasing)) +
                    fadeIn(animationSpec = tween(350)),
                exit = slideOutVertically(targetOffsetY = { it }, animationSpec = tween(300, easing = FastOutSlowInEasing)) +
                    fadeOut(animationSpec = tween(250))) {
                displaySong?.let { song ->
                    val innerPlayPause: () -> Unit = if (isExternalActive) extPlayPause else localPlayPause
                    val innerNext: () -> Unit = if (isExternalActive) extNext else localNext
                    val innerPrevious: () -> Unit = if (isExternalActive) extPrevious else localPrevious
                    val innerSeek: (Long) -> Unit = if (isExternalActive) extSeek else localSeek
                    PlayerScreen(song = song, isPlaying = displayPlaying, progressMs = displayProgress, durationMs = displayDuration,
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
                        onDismiss = { showFullPlayer = false },
                        onTransferUp = {
                            viewModel.invokePluginPlayerGesture("artwork.swipeUp")
                                ?.onSuccess { pluginWebUi = it }
                        },
                        onLandscapeToggle = {
                            val activity = context as? android.app.Activity
                            activity?.requestedOrientation =
                                if (activity.resources.configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE)
                                    android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
                                else android.content.pm.ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
                        },
                        customBgColor = uiState.customBgColor, wallpaperUri = uiState.wallpaperUri, isLightTheme = uiState.isLightTheme,
                        bgMode = uiState.playerBgMode,
                        lyricsLoader = { id -> viewModel.loadLyrics(id) }, backdrop = backdrop)
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
    }
    }
}
}
