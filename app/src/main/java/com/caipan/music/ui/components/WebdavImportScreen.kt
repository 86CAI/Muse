package com.caipan.music.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.res.painterResource
import com.caipan.music.R
import com.caipan.music.data.WebdavConfig
import com.caipan.music.data.WebdavItem
import kotlinx.coroutines.launch
import com.kyant.backdrop.Backdrop

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WebdavImportScreen(
    initialConfig: WebdavConfig,
    accentColor: Color = Color(0xFF1DB954),
    isLightTheme: Boolean = false,
    backdrop: Backdrop? = null,
    onImport: (List<String>, WebdavConfig) -> Unit,
    onDismiss: () -> Unit
) {
    val textPrimary = MaterialTheme.colorScheme.onBackground
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val bgColor = Color.Transparent
    val cardBg = MaterialTheme.colorScheme.surfaceContainerHigh.copy(alpha = if (isLightTheme) .78f else .70f)

    var configUrl by remember { mutableStateOf(initialConfig.url) }
    var configUser by remember { mutableStateOf(initialConfig.username) }
    var configPass by remember { mutableStateOf(initialConfig.password) }
    var connected by remember { mutableStateOf(false) }
    var currentPath by remember { mutableStateOf("") }
    var items by remember { mutableStateOf<List<WebdavItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    var selectedPaths by remember { mutableStateOf<Set<String>>(emptySet()) }
    var phase by remember { mutableStateOf(0) }
    val scope = rememberCoroutineScope()
    val manager = remember { com.caipan.music.data.WebdavManager() }

    FullScreenGlassRoute(backdrop, isLightTheme) {
    Box(Modifier.fillMaxSize()) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                MuseIconButton(onClick = onDismiss) {
                    Icon(painterResource(R.drawable.ic_apple_arrow_left), "返回", tint = textPrimary.copy(alpha = 0.8f), modifier = Modifier.size(24.dp))
                }
                Spacer(Modifier.weight(1f))
            }
            Text("WebDAV", color = textPrimary, fontSize = 34.sp, lineHeight = 38.sp, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 20.dp))
            Text(if (phase == 0) "连接你的远程音乐资料库" else "选择要导入的音乐", color = textSecondary, fontSize = 13.sp, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))

            if (phase == 0) {
                Column(Modifier.fillMaxSize().imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 20.dp)) {
                    MuseGlassBox(Modifier.fillMaxWidth(), backdrop, RoundedCornerShape(16.dp), cardBg) {
                        Column(Modifier.padding(20.dp)) {
                            Text("WebDAV 连接配置", color = textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                            Spacer(Modifier.height(16.dp))
                            OutlinedTextField(value = configUrl, onValueChange = { configUrl = it },
                                label = { Text("服务器地址") },
                                placeholder = { Text("https://example.com/dav") },
                                singleLine = true, modifier = Modifier.fillMaxWidth(),
                                colors = fieldColors(textPrimary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                shape = RoundedCornerShape(12.dp))
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(value = configUser, onValueChange = { configUser = it },
                                label = { Text("用户名") },
                                placeholder = { Text("可选") },
                                singleLine = true, modifier = Modifier.fillMaxWidth(),
                                colors = fieldColors(textPrimary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                                shape = RoundedCornerShape(12.dp))
                            Spacer(Modifier.height(12.dp))
                            OutlinedTextField(value = configPass, onValueChange = { configPass = it },
                                label = { Text("密码") },
                                singleLine = true, modifier = Modifier.fillMaxWidth(),
                                visualTransformation = PasswordVisualTransformation(),
                                colors = fieldColors(textPrimary),
                                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                                shape = RoundedCornerShape(12.dp))
                            Spacer(Modifier.height(20.dp))
                            MuseButton(onClick = {
                                if (configUrl.isBlank()) {
                                    errorMsg = "请输入 WebDAV 服务器地址"
                                    return@MuseButton
                                }
                                isLoading = true; errorMsg = null
                                scope.launch {
                                    val config = WebdavConfig(configUrl.trim(), configUser, configPass)
                                    val result = manager.listDirectory(config)
                                    isLoading = false
                                    result.fold(
                                        onSuccess = { connected = true; items = it; phase = 1 },
                                        onFailure = { errorMsg = "连接失败: " + it.message }
                                    )
                                }
                            }, modifier = Modifier.fillMaxWidth(),
                                colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                shape = RoundedCornerShape(12.dp)) {
                                if (isLoading) CircularProgressIndicator(color = Color.White, modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                else Text("连接", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                            }
                            if (errorMsg != null) {
                                Spacer(Modifier.height(8.dp))
                                Text(errorMsg!!, color = Color(0xFFFF4444), fontSize = 13.sp)
                            }
                        }
                    }
                    Spacer(Modifier.height(12.dp))
                    Text("填写你的 WebDAV 服务地址；部分服务不需要用户名和密码。",
                        color = textSecondary, fontSize = 12.sp, lineHeight = 18.sp)
                }
            } else {
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically) {
                    MuseTextButton(onClick = {
                        if (currentPath.isEmpty()) return@MuseTextButton
                        val parent = currentPath.substringBeforeLast('/').ifEmpty { "" }
                        isLoading = true
                        scope.launch {
                            val r = manager.listDirectory(WebdavConfig(configUrl, configUser, configPass), parent)
                            isLoading = false
                            r.onSuccess { currentPath = parent; items = it; selectedPaths = emptySet() }
                            r.onFailure { errorMsg = it.message }
                        }
                    }, enabled = currentPath.isNotEmpty()) {
                        Icon(painterResource(R.drawable.ic_apple_arrow_left), null, tint = if (currentPath.isNotEmpty()) accentColor else textSecondary,
                            modifier = Modifier.size(16.dp))
                    }
                    Text(if (currentPath.isEmpty()) "/" else currentPath,
                        color = textSecondary, fontSize = 14.sp, modifier = Modifier.weight(1f),
                        maxLines = 1, overflow = TextOverflow.Ellipsis)
                    MuseTextButton(onClick = {
                        isLoading = true
                        scope.launch {
                            val r = manager.listDirectory(WebdavConfig(configUrl, configUser, configPass), currentPath)
                            isLoading = false
                            r.onSuccess { items = it }
                            r.onFailure { errorMsg = it.message }
                        }
                    }) { Icon(painterResource(R.drawable.ic_apple_refresh), "刷新", tint = accentColor, modifier = Modifier.size(18.dp)) }
                }

                if (isLoading) {
                    Box(Modifier.fillMaxSize()) {
                        // 骨架屏替代转圈:目录加载感知更快
                        SkeletonSongRows(count = 6)
                    }
                } else if (errorMsg != null) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(errorMsg!!, color = Color(0xFFFF4444), fontSize = 14.sp)
                            Spacer(Modifier.height(8.dp))
                            MuseTextButton(onClick = { phase = 0; errorMsg = null; connected = false }) {
                                Text("返回配置", color = accentColor)
                            }
                        }
                    }
                } else if (items.isEmpty()) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text("目录为空", color = textSecondary, fontSize = 15.sp)
                    }
                } else {
                    LazyColumn(Modifier.fillMaxSize().weight(1f)) {
                        val dirs = items.filter { it.isDirectory }
                        val files = items.filter { !it.isDirectory }

                        items(dirs, key = { it.path }) { item ->
                            Row(Modifier.fillMaxWidth().clickable {
                                isLoading = true
                                scope.launch {
                                    val r = manager.listDirectory(WebdavConfig(configUrl, configUser, configPass), item.path)
                                    isLoading = false
                                    r.onSuccess { currentPath = item.path; items = it; selectedPaths = emptySet() }
                                    r.onFailure { errorMsg = it.message }
                                }
                            }.padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                                    .background(accentColor.copy(alpha = 0.15f)),
                                    contentAlignment = Alignment.Center) {
                                    Icon(painterResource(R.drawable.ic_apple_folder), null, tint = accentColor, modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Text(item.name, color = textPrimary, fontSize = 15.sp,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f))
                                Icon(painterResource(R.drawable.ic_apple_chevron_right), null, tint = textSecondary, modifier = Modifier.size(20.dp))
                            }
                            if (item != dirs.lastOrNull()) {
                                HorizontalDivider(color = textSecondary.copy(alpha = 0.08f), modifier = Modifier.padding(start = 64.dp, end = 16.dp))
                            }
                        }

                        if (files.isNotEmpty() && dirs.isNotEmpty()) {
                            item {
                                HorizontalDivider(color = textSecondary.copy(alpha = 0.1f),
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                            }
                        }

                        items(files, key = { it.path }) { item ->
                            val isAudio = item.name.let { n ->
                                n.endsWith(".mp3", true) || n.endsWith(".flac", true) ||
                                n.endsWith(".wav", true) || n.endsWith(".ogg", true) ||
                                n.endsWith(".m4a", true) || n.endsWith(".aac", true) ||
                                n.endsWith(".wma", true) || n.endsWith(".opus", true)
                            }
                            val isSel = item.path in selectedPaths
                            Row(Modifier.fillMaxWidth().clickable {
                                if (isAudio) {
                                    selectedPaths = if (isSel) selectedPaths - item.path
                                    else selectedPaths + item.path
                                }
                            }.padding(horizontal = 16.dp, vertical = 8.dp),
                                verticalAlignment = Alignment.CenterVertically) {
                                Box(Modifier.size(36.dp).clip(RoundedCornerShape(8.dp))
                                    .background(if (isSel) accentColor.copy(alpha = 0.25f) else cardBg),
                                    contentAlignment = Alignment.Center) {
                                    val fileIcon = if (isAudio) painterResource(R.drawable.ic_apple_music) else painterResource(R.drawable.ic_apple_file_text)
                                    Icon(fileIcon, null,
                                        tint = if (isSel) accentColor else textSecondary, modifier = Modifier.size(20.dp))
                                }
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(item.name, color = if (isSel) accentColor else textPrimary,
                                        fontSize = 14.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    Text(formatFileSize(item.size), color = textSecondary, fontSize = 11.sp)
                                }
                                if (isAudio) {
                                    val actionIcon = if (isSel) painterResource(R.drawable.ic_apple_circle_check) else painterResource(R.drawable.ic_apple_plus)
                                    Icon(actionIcon, null,
                                        tint = if (isSel) accentColor else textSecondary.copy(alpha = 0.4f),
                                        modifier = Modifier.size(22.dp).padding(start = 8.dp))
                                }
                            }
                            if (item != files.lastOrNull()) {
                                HorizontalDivider(color = textSecondary.copy(alpha = 0.08f), modifier = Modifier.padding(start = 64.dp, end = 16.dp))
                            }
                        }
                        item { Spacer(Modifier.height(80.dp)) }
                    }
                }

                if (selectedPaths.isNotEmpty()) {
                    MuseGlassBox(Modifier.fillMaxWidth(), backdrop, RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp), cardBg) {
                        Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically) {
                            Text("已选 " + selectedPaths.size + " 首", color = textPrimary,
                                fontSize = 14.sp, fontWeight = FontWeight.Medium)
                            Spacer(Modifier.weight(1f))
                            MuseButton(onClick = {
                                val config = WebdavConfig(configUrl, configUser, configPass)
                                onImport(selectedPaths.toList(), config)
                            }, colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                                shape = RoundedCornerShape(12.dp)) {
                                Text("导入歌单", color = Color.White, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }
        }
    }
    }
}

@Composable
private fun fieldColors(textColor: Color) = OutlinedTextFieldDefaults.colors(
    focusedTextColor = textColor, unfocusedTextColor = textColor,
    focusedBorderColor = textColor.copy(alpha = 0.3f),
    unfocusedBorderColor = textColor.copy(alpha = 0.1f),
    cursorColor = Color(0xFF1DB954),
    focusedLabelColor = Color(0xFF1DB954)
)

private fun formatFileSize(bytes: Long): String = when {
    bytes < 1024 -> bytes.toString() + " B"
    bytes < 1024 * 1024 -> (bytes / 1024).toString() + " KB"
    bytes < 1024 * 1024 * 1024 -> String.format("%.1f", bytes.toDouble() / (1024 * 1024)) + " MB"
    else -> String.format("%.2f", bytes.toDouble() / (1024 * 1024 * 1024)) + " GB"
}
