package com.caipan.music.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.caipan.music.MuseApplication
import com.caipan.music.BuildConfig
import com.caipan.music.data.GitHubGistBackup
import com.caipan.music.data.GitHubSession
import com.caipan.music.data.MuseSettingsSync
import com.caipan.music.data.PlaylistManager
import kotlinx.coroutines.Job
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.util.UUID

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitHubAccountSheet(
    accent: Color,
    isLight: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as MuseApplication
    val store = app.gitHubSessionStore
    val client = app.gitHubOAuthClient
    val session by store.session.collectAsState()
    val scope = rememberCoroutineScope()

    var deviceFlowStep by remember { mutableStateOf<DeviceFlowStep>(DeviceFlowStep.Idle) }
    var devicePollingJob by remember { mutableStateOf<Job?>(null) }

    var isSyncing by remember { mutableStateOf(false) }
    var isRestoring by remember { mutableStateOf(false) }
    var syncResult by remember { mutableStateOf<String?>(null) }
    var syncError by remember { mutableStateOf<String?>(null) }

    DisposableEffect(Unit) {
        onDispose { devicePollingJob?.cancel() }
    }

    val clientIdConfigured = BuildConfig.GITHUB_CLIENT_ID.isNotBlank()
    val hasProxy = BuildConfig.GITHUB_TOKEN_PROXY_URL.isNotBlank()

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Color.Transparent,
        scrimColor = Color.Black.copy(alpha = .18f),
        tonalElevation = 0.dp,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp)
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .museGlass(null, RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                    MaterialTheme.colorScheme.surface.copy(alpha = .36f))
                .padding(horizontal = 24.dp).padding(bottom = 32.dp).navigationBarsPadding()
        ) {
            DialogBlurEffect()
            Text("GitHub", color = if (isLight) Color(0xFF1C1C1E) else Color.White,
                fontSize = 20.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(20.dp))

            when {
                // ── Logged in ──
                session != null -> {
                    val s = session!!
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(64.dp).clip(CircleShape).background(Color(0xFF333333))) {
                            AsyncImage(s.avatarUrl, "avatar", Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                        }
                        Column(Modifier.padding(start = 16.dp).weight(1f)) {
                            Text(s.name ?: s.login, color = if (isLight) Color(0xFF1C1C1E) else Color.White,
                                fontSize = 18.sp, fontWeight = FontWeight.Bold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                            Text("@${s.login}", color = Color(0xFF888888), fontSize = 13.sp)
                            if (s.gistId != null) {
                                Text("Gist: ${s.gistId.take(7)}...", color = Color(0xFF888888), fontSize = 11.sp,
                                    modifier = Modifier.padding(top = 2.dp))
                            }
                        }
                    }

                    Spacer(Modifier.height(20.dp))

                    // 上传到 Gist
                    Button(
                        onClick = {
                            isSyncing = true; syncResult = null; syncError = null
                            scope.launch {
                                try {
                                    val backup = GitHubGistBackup(
                                        PlaylistManager(context),
                                        MuseSettingsSync(context),
                                        client
                                    )
                                    val existingGistId = s.gistId
                                        ?: backup.findExistingBackupGist(s.token)
                                    val result = backup.syncAll(s.token, existingGistId)
                                    store.updateGistId(result.gistId)
                                    syncResult = "已上传 ${result.playlistCount} 个歌单 + 设置"
                                } catch (e: Exception) {
                                    syncError = "上传失败: ${e.message}"
                                }
                                isSyncing = false
                            }
                        },
                        enabled = !isSyncing && !isRestoring,
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isSyncing) {
                            CircularProgressIndicator(Modifier.size(18.dp), color = Color.White, strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Default.CloudUpload, null, Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(if (isSyncing) "上传中..." else "上传到 Gist", color = Color.White)
                    }

                    Spacer(Modifier.height(12.dp))

                    // 从 Gist 恢复
                    Button(
                        onClick = {
                            isRestoring = true; syncResult = null; syncError = null
                            scope.launch {
                                try {
                                    val backup = GitHubGistBackup(
                                        PlaylistManager(context),
                                        MuseSettingsSync(context),
                                        client
                                    )
                                    // 如果本地没有 gistId，尝试搜索
                                    val gistId = s.gistId ?: backup.findExistingBackupGist(s.token)
                                        ?: throw Exception("未找到备份，请先上传一次")
                                    val result = backup.restoreAll(s.token, gistId)
                                    if (s.gistId == null) store.updateGistId(gistId)
                                    syncResult = "已恢复 ${result.playlistCount} 个歌单 + ${result.settingsKeys} 项设置（重启 App 生效）"
                                } catch (e: Exception) {
                                    syncError = "恢复失败: ${e.message}"
                                }
                                isRestoring = false
                            }
                        },
                        enabled = !isSyncing && !isRestoring,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isLight) Color(0xFFE5E5EA) else Color(0xFF333333)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (isRestoring) {
                            CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp,
                                color = if (isLight) Color(0xFF1C1C1E) else Color.White)
                        } else {
                            Icon(Icons.Default.CloudDownload, null, Modifier.size(18.dp),
                                tint = if (isLight) Color(0xFF1C1C1E) else Color.White)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(if (isRestoring) "恢复中..." else "从 Gist 恢复",
                            color = if (isLight) Color(0xFF1C1C1E) else Color.White)
                    }

                    syncResult?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = accent, fontSize = 13.sp)
                    }
                    syncError?.let {
                        Spacer(Modifier.height(8.dp))
                        Text(it, color = Color(0xFFFF453A), fontSize = 13.sp)
                    }

                    Spacer(Modifier.height(16.dp))
                    TextButton(
                        onClick = { store.clearSession(); onDismiss() },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("退出 GitHub 登录", color = Color(0xFFFF453A)) }
                }

                // ── Device Flow active ──
                deviceFlowStep is DeviceFlowStep.ShowingCode -> {
                    val step = deviceFlowStep as DeviceFlowStep.ShowingCode
                    Text("Device Flow 授权", fontSize = 16.sp, fontWeight = FontWeight.Bold,
                        color = if (isLight) Color(0xFF1C1C1E) else Color.White)
                    Spacer(Modifier.height(12.dp))
                    Text("请在浏览器中点击「Authorize」",
                        color = Color(0xFF888888), fontSize = 13.sp)

                    Spacer(Modifier.height(8.dp))
                    // Verification URL — auto-open browser
                    Button(
                        onClick = {
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(step.verificationUri))
                            context.startActivity(intent)
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Share, null, Modifier.size(16.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("打开授权页面", color = Color.White)
                    }

                    Spacer(Modifier.height(12.dp))
                    Text("验证码", color = Color(0xFF888888), fontSize = 12.sp)
                    Text(
                        step.userCode,
                        fontSize = 32.sp,
                        fontWeight = FontWeight.Black,
                        color = accent,
                        letterSpacing = 4.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        "等待授权...${step.remainingLabel}",
                        color = Color(0xFF888888),
                        fontSize = 12.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Center
                    )
                    Spacer(Modifier.height(12.dp))
                    CircularProgressIndicator(Modifier.size(24.dp).align(Alignment.CenterHorizontally), color = accent, strokeWidth = 2.dp)
                    Spacer(Modifier.height(12.dp))
                    TextButton(
                        onClick = {
                            devicePollingJob?.cancel()
                            deviceFlowStep = DeviceFlowStep.Idle
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("取消", color = Color(0xFF888888)) }
                }

                deviceFlowStep is DeviceFlowStep.Error -> {
                    val step = deviceFlowStep as DeviceFlowStep.Error
                    Text("授权失败", color = Color(0xFFFF453A), fontSize = 16.sp, fontWeight = FontWeight.Bold)
                    Spacer(Modifier.height(8.dp))
                    Text(step.message, color = Color(0xFF888888), fontSize = 13.sp)
                    Spacer(Modifier.height(16.dp))
                    Button(onClick = { deviceFlowStep = DeviceFlowStep.Idle },
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("重试", color = Color.White) }
                }

                // ── Not logged in, idle ──
                else -> {
                    if (!clientIdConfigured) {
                        Text("未配置 GitHub Client ID", color = Color(0xFFFF9500), fontSize = 14.sp, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text("请在 local.properties 中设置 github.client.id 后重新编译",
                            color = Color(0xFF888888), fontSize = 12.sp)
                        Spacer(Modifier.height(16.dp))
                    }

                    // Device Flow
                    Button(
                        onClick = {
                            scope.launch {
                                try {
                                    val resp = client.requestDeviceCode()
                                    deviceFlowStep = DeviceFlowStep.ShowingCode(
                                        userCode = resp.userCode,
                                        verificationUri = resp.verificationUri,
                                        expiresIn = resp.expiresIn,
                                        startedAt = System.currentTimeMillis()
                                    )
                                    // 自动打开浏览器
                                    val intent = Intent(Intent.ACTION_VIEW, Uri.parse(resp.verificationUri))
                                    context.startActivity(intent)
                                    // 开始轮询
                                    devicePollingJob = launch {
                                        try {
                                            val token = client.pollDeviceToken(resp.deviceCode, resp.interval)
                                            val user = client.fetchUser(token.accessToken)
                                            store.saveSession(
                                                GitHubSession(
                                                    login = user.login,
                                                    avatarUrl = user.avatarUrl,
                                                    name = user.name,
                                                    token = token.accessToken
                                                )
                                            )
                                            // 同步头像和昵称到 Muse Profile
                                            syncProfileToMuse(context, user.name ?: user.login, user.avatarUrl)
                                            deviceFlowStep = DeviceFlowStep.Idle
                                            Toast.makeText(context, "GitHub 登录成功: ${user.name ?: user.login}", Toast.LENGTH_SHORT).show()
                                        } catch (e: Exception) {
                                            if (isActive) {
                                                deviceFlowStep = DeviceFlowStep.Error(e.message ?: "授权失败")
                                            }
                                        }
                                    }
                                } catch (e: Exception) {
                                    deviceFlowStep = DeviceFlowStep.Error(e.message ?: "请求失败")
                                }
                            }
                        },
                        enabled = clientIdConfigured,
                        colors = ButtonDefaults.buttonColors(containerColor = accent),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Link, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Device Flow 登录（零后端）", color = Color.White)
                    }

                    Spacer(Modifier.height(12.dp))

                    // Web Flow
                    Button(
                        onClick = {
                            val redirectUri = "muse://oauth/github/callback"
                            val url = client.buildAuthorizeUrl(redirectUri)
                            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url))
                            context.startActivity(intent)
                            Toast.makeText(context, "请用浏览器完成 GitHub 授权", Toast.LENGTH_SHORT).show()
                            onDismiss()
                        },
                        enabled = clientIdConfigured && hasProxy,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = if (isLight) Color(0xFFE5E5EA) else Color(0xFF333333)
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Default.Share, null, Modifier.size(18.dp),
                            tint = if (isLight) Color(0xFF1C1C1E) else Color.White)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            if (hasProxy) "浏览器登录（Web Flow）" else "浏览器登录（需配置后端）",
                            color = if (isLight) Color(0xFF1C1C1E) else Color.White
                        )
                    }
                    if (!hasProxy) {
                        Text(
                            "Web Flow 需要后端代理 URL，在 local.properties 中配置 github.token.proxy.url",
                            color = Color(0xFF888888),
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 4.dp, start = 4.dp)
                        )
                    }

                    Spacer(Modifier.height(16.dp))
                    Text(
                        "登录后可上传歌单和设置到 Gist，换设备时一键恢复。",
                        color = Color(0xFF888888),
                        fontSize = 12.sp
                    )
                }
            }
        }
    }
}

/** 把 GitHub 头像和昵称同步到 Muse 个人页 */
private fun syncProfileToMuse(ctx: android.content.Context, name: String, avatarUrl: String) {
    val prefs = ctx.getSharedPreferences("muse_prefs", 0)
    prefs.edit().putString("profile_name", name).apply()

    Thread {
        try {
            val client = OkHttpClient()
            val req = Request.Builder().url(avatarUrl).build()
            val resp = client.newCall(req).execute()
            if (resp.isSuccessful) {
                val bytes = resp.body?.bytes() ?: return@Thread
                val dir = File(ctx.filesDir, "profile_avatars").apply { mkdirs() }
                val file = File(dir, "avatar_${UUID.randomUUID()}.jpg")
                file.writeBytes(bytes)
                prefs.edit().putString("profile_avatar", file.absolutePath).apply()
            }
        } catch (_: Exception) { }
    }.start()
}

private sealed class DeviceFlowStep {
    data object Idle : DeviceFlowStep()
    data class ShowingCode(
        val userCode: String,
        val verificationUri: String,
        val expiresIn: Int,
        val startedAt: Long
    ) : DeviceFlowStep() {
        val remainingLabel: String
            get() {
                val elapsed = (System.currentTimeMillis() - startedAt) / 1000
                val remaining = (expiresIn - elapsed).coerceAtLeast(0)
                return if (remaining > 60) "剩余 ${remaining / 60} 分钟"
                else "剩余 $remaining 秒"
            }
    }
    data class Error(val message: String) : DeviceFlowStep()
}