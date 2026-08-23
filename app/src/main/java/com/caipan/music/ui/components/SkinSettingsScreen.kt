package com.caipan.music.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.caipan.music.MuseApplication
import com.caipan.music.R
import com.caipan.music.skin.MuseSkin
import java.io.File

/**
 * 皮肤管理页（方案 A 声明式皮肤包）：
 * 导入 .museskin（zip）→ 解压校验 → 列表 → 应用/删除。
 * 皮肤只声明颜色/圆角/模糊/布局参数与资源，不执行代码。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SkinSettingsScreen(
    accentColor: Color,
    isLightTheme: Boolean,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as MuseApplication
    val manager = app.skinManager
    val zh = true

    val textPrimary = MaterialTheme.colorScheme.onBackground
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant

    // 导入皮肤包
    val skinImporter = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            manager.import(uri)
                .onSuccess { skin -> manager.apply(skin.id) }
                .onFailure { e ->
                    android.widget.Toast.makeText(context, "皮肤导入失败：${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
        }
    }

    var refresh by remember { mutableStateOf(0) }
    val skins = remember(refresh, manager.activeSkinId) { manager.skins }

    Box(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background)) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            // Header
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                MuseIconButton(onClick = onDismiss) {
                    Icon(painterResource(R.drawable.ic_apple_arrow_left), "返回", tint = textPrimary, modifier = Modifier.size(24.dp))
                }
                Text("皮肤", color = textPrimary, style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f).padding(start = 8.dp))
                MuseIconButton(onClick = { skinImporter.launch(arrayOf("application/zip", "application/octet-stream")) }) {
                    Icon(painterResource(R.drawable.ic_apple_plus), "导入皮肤", tint = accentColor, modifier = Modifier.size(24.dp))
                }
            }

            Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp)) {
                Text("界面外观", color = accentColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(top = 8.dp, bottom = 8.dp))

                // ── 默认皮肤 ──
                SkinRow(
                    title = if (zh) "默认（无皮肤）" else "Default",
                    subtitle = "Muse 内置外观，颜色跟随强调色与动态取色",
                    active = manager.activeSkinId == null,
                    accentColor = accentColor,
                    isLightTheme = isLightTheme,
                    onClick = { manager.clear(); refresh++ }
                )

                // ── 已安装皮肤 ──
                skins.forEach { skin ->
                    SkinRow(
                        title = skin.name,
                        subtitle = "${skin.author} · v${skin.version}" +
                            (if (skin.description.isNotBlank()) "\n${skin.description}" else ""),
                        active = manager.activeSkinId == skin.id,
                        accentColor = accentColor,
                        isLightTheme = isLightTheme,
                        preview = if (skin.hasPreview) File(skin.directory, "preview.png") else null,
                        onClick = { manager.apply(skin.id); refresh++ },
                        onDelete = {
                            manager.delete(skin.id)
                            refresh++
                        }
                    )
                }

                Spacer(Modifier.height(24.dp))
                Text("如何制作皮肤", color = accentColor, fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(bottom = 8.dp))
                Card(Modifier.fillMaxWidth(),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f)),
                    shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(18.dp)) {
                        Text("皮肤包是一个 zip 文件（.museskin），只包含配置与图片，不执行任何代码：",
                            color = textSecondary, fontSize = 12.sp, lineHeight = 19.sp)
                        Spacer(Modifier.height(10.dp))
                        Text(
                            "• skin.json — 名称/作者/颜色/圆角/模糊/布局/字体\n" +
                            "• preview.png — 皮肤预览图（可选）\n" +
                            "• wallpaper.png — 皮肤背景（可选）\n" +
                            "• icons/ — 图标包（后续版本支持）\n\n" +
                            "颜色：primary、onPrimary、primaryContainer、secondary、background、surface、surfaceVariant、onBackground、onSurface、onSurfaceVariant、error、outline、scrim\n" +
                            "模糊：lyrics（歌词）、background（背景）\n" +
                            "布局：showPrevNext（切歌按钮）、miniPlayerStyle（record/classic 迷你播放器）、albumArtShape（circle/rounded/square 封面）、progressStyle（thin/thick/none 进度条）、showTimeLabels、showMiniPlayer、showMiniArtwork\n" +
                            "字体：scale（0.8~1.3 字号缩放）\n" +
                            "壁纸：dim（0~0.6 背景暗化）",
                            color = textPrimary, fontSize = 12.sp, lineHeight = 20.sp
                        )
                    }
                }
                Spacer(Modifier.height(100.dp))
            }
        }
    }
}

@Composable
private fun SkinRow(
    title: String,
    subtitle: String,
    active: Boolean,
    accentColor: Color,
    isLightTheme: Boolean,
    preview: File? = null,
    onClick: () -> Unit,
    onDelete: (() -> Unit)? = null
) {
    val textPrimary = MaterialTheme.colorScheme.onBackground
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    Card(
        Modifier.fillMaxWidth().padding(vertical = 4.dp)
            .clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (active) accentColor.copy(alpha = 0.12f)
            else MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.5f)
        ),
        shape = RoundedCornerShape(16.dp)
    ) {
        Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            // 预览
            Box(Modifier.size(56.dp).clip(RoundedCornerShape(12.dp))
                .background(if (isLightTheme) Color(0xFFE5E5EA) else Color(0xFF333333)),
                contentAlignment = Alignment.Center) {
                if (preview != null) {
                    AsyncImage(model = preview, contentDescription = null,
                        modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                } else {
                    Icon(painterResource(R.drawable.ic_apple_palette), null, tint = accentColor, modifier = Modifier.size(26.dp))
                }
            }
            Column(Modifier.weight(1f).padding(horizontal = 14.dp)) {
                Text(title, color = textPrimary, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                Spacer(Modifier.height(2.dp))
                Text(subtitle, color = textSecondary, fontSize = 12.sp, lineHeight = 17.sp, maxLines = 3)
            }
            if (active) {
                Text("使用中", color = accentColor, fontSize = 12.sp, fontWeight = FontWeight.Bold)
            } else {
                onDelete?.let { del ->
                    MuseIconButton(onClick = del) {
                        Icon(painterResource(R.drawable.ic_apple_trash), "删除", tint = textSecondary, modifier = Modifier.size(20.dp))
                    }
                }
            }
        }
    }
}
