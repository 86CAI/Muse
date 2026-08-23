package com.caipan.music.ui.components

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caipan.music.R
import com.caipan.music.plugin.BlurLocation
import com.kyant.backdrop.Backdrop

private fun safeParseUrl(raw: String): Uri? = runCatching { Uri.parse(raw.trim()) }.getOrNull()

const val ONLINE_SOURCE_INDEX_URL =
    "https://blog.umrs.cc/archives/lx-music-zui-xin-zui-quan-yin-yuan-chi-xu-geng-xin-zhong-geng-xin"

data class OnlineSourceUiModel(
    val id: String,
    val name: String,
    val version: String = "",
    val author: String = "",
    val description: String = "",
    val sourceUrl: String = "",
    val enabled: Boolean = true,
    val sha256: String = ""
)

@Composable
internal fun OnlineSourceSettingsSection(
    sources: List<OnlineSourceUiModel>,
    accentColor: Color,
    backdrop: Backdrop?,
    isImporting: Boolean,
    message: String?,
    onImportUrl: (String) -> Unit,
    onImportFile: (Uri, String) -> Unit,
    onEnabledChange: (String, Boolean) -> Unit,
    onDelete: (String) -> Unit,
    onOpenIndex: (String) -> Unit
) {
    val primary = MaterialTheme.colorScheme.onBackground
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    var scriptUrl by rememberSaveable { mutableStateOf("") }
    var deleteTarget by remember { mutableStateOf<OnlineSourceUiModel?>(null) }
    val normalizedUrl = scriptUrl.trim()
    val parsedUrl = remember(normalizedUrl) { safeParseUrl(normalizedUrl) }
    val isValidUrl = parsedUrl?.scheme?.lowercase() in setOf("http", "https") &&
        !parsedUrl?.host.isNullOrBlank()

    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        Text(
            "自定义在线音源",
            color = accentColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 4.dp, bottom = 8.dp)
        )

        Card(
            modifier = Modifier.fillMaxWidth().museGlass(
                backdrop = backdrop,
                shape = RoundedCornerShape(16.dp),
                tint = MaterialTheme.colorScheme.surface.copy(alpha = .34f),
                location = BlurLocation.CARDS,
                readabilityBoost = true
            ),
            shape = RoundedCornerShape(16.dp),
            colors = CardDefaults.cardColors(containerColor = Color.Transparent)
        ) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text("从脚本 URL 导入", color = primary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                Spacer(Modifier.height(3.dp))
                Text(
                    "粘贴 LX Music 兼容音源脚本的完整地址。",
                    color = secondary,
                    fontSize = 12.sp,
                    lineHeight = 17.sp
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = scriptUrl,
                    onValueChange = { scriptUrl = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("脚本 URL") },
                    placeholder = { Text("https://example.com/source.js") },
                    leadingIcon = { Icon(painterResource(R.drawable.ic_apple_link), contentDescription = null) },
                    singleLine = true,
                    isError = scriptUrl.isNotBlank() && !isValidUrl,
                    supportingText = if (scriptUrl.isNotBlank() && !isValidUrl) {
                        { Text("请输入完整的 http 或 https 地址") }
                    } else null
                )
                Spacer(Modifier.height(8.dp))
                val filePicker = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.GetContent()
                ) { uri ->
                    if (uri != null) {
                        val name = uri.lastPathSegment?.substringAfterLast('/') ?: "本地音源"
                        onImportFile(uri, name)
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    MuseButton(
                        onClick = { onImportUrl(normalizedUrl) },
                        enabled = isValidUrl && !isImporting,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isImporting) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(16.dp),
                                strokeWidth = 2.dp,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        } else {
                            Icon(painterResource(R.drawable.ic_apple_plus), contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(Modifier.width(7.dp))
                        Text(if (isImporting) "正在导入" else "导入URL", maxLines = 1)
                    }
                    MuseOutlinedButton(
                        onClick = { filePicker.launch("*/*") },
                        enabled = !isImporting,
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(painterResource(R.drawable.ic_apple_puzzle), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(7.dp))
                        Text("导入本地脚本", maxLines = 1)
                    }
                }
                message?.let {
                    Text(
                        it,
                        color = if (it.contains("失败")) MaterialTheme.colorScheme.error else accentColor,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        modifier = Modifier.padding(top = 10.dp)
                    )
                }
                HorizontalDivider(
                    modifier = Modifier.padding(vertical = 12.dp),
                    color = secondary.copy(alpha = .12f)
                )
                Text(
                    "第三方脚本仅由你主动导入，Muse 不提供、审核或自动更新。脚本可执行网络请求，请只使用可信来源。",
                    color = secondary,
                    fontSize = 12.sp,
                    lineHeight = 18.sp
                )
            }
        }

        Spacer(Modifier.height(12.dp))

        if (sources.isEmpty()) {
            Text(
                "尚未导入自定义在线音源",
                color = secondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 10.dp)
            )
        } else {
            sources.forEach { source ->
                OnlineSourceCard(
                    source = source,
                    accentColor = accentColor,
                    backdrop = backdrop,
                    onEnabledChange = { onEnabledChange(source.id, it) },
                    onDelete = { deleteTarget = source }
                )
                Spacer(Modifier.height(10.dp))
            }
        }

        Text(
            "播放插件",
            color = accentColor,
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(top = 8.dp, bottom = 2.dp)
        )
    }

    deleteTarget?.let { source ->
        MuseAlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("删除在线音源？") },
            text = { Text("将移除 ${source.name} 的本地脚本与配置。之后需要重新导入才能使用。") },
            confirmButton = {
                MuseTextButton(onClick = {
                    deleteTarget = null
                    onDelete(source.id)
                }) { Text("删除") }
            },
            dismissButton = { MuseTextButton(onClick = { deleteTarget = null }) { Text("取消") } }
        )
    }
}

@Composable
private fun OnlineSourceCard(
    source: OnlineSourceUiModel,
    accentColor: Color,
    backdrop: Backdrop?,
    onEnabledChange: (Boolean) -> Unit,
    onDelete: () -> Unit
) {
    val primary = MaterialTheme.colorScheme.onBackground
    val secondary = MaterialTheme.colorScheme.onSurfaceVariant
    val metadata = listOf(source.version.takeIf { it.isNotBlank() }?.let { "v$it" }, source.author)
        .filterNotNull()
        .filter { it.isNotBlank() }
        .joinToString(" · ")
    val origin = remember(source.sourceUrl) {
        safeParseUrl(source.sourceUrl)?.host?.removePrefix("www.").orEmpty()
    }

    Card(
        modifier = Modifier.fillMaxWidth().museGlass(
            backdrop = backdrop,
            shape = RoundedCornerShape(16.dp),
            tint = MaterialTheme.colorScheme.surface.copy(alpha = .34f),
            location = BlurLocation.CARDS,
            readabilityBoost = true
        ),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                Modifier.size(42.dp).background(accentColor.copy(alpha = .14f), RoundedCornerShape(12.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(painterResource(R.drawable.ic_apple_puzzle), contentDescription = null, tint = accentColor)
            }
            Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                Text(
                    source.name,
                    color = primary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (metadata.isNotBlank()) {
                    Text(metadata, color = secondary, fontSize = 12.sp, maxLines = 1)
                }
                if (source.description.isNotBlank()) {
                    Text(
                        source.description,
                        color = secondary,
                        fontSize = 12.sp,
                        lineHeight = 17.sp,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 4.dp)
                    )
                }
                if (origin.isNotBlank()) {
                    Text(
                        origin,
                        color = accentColor,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 5.dp)
                    )
                }
                if (source.sha256.isNotBlank()) {
                    Text(
                        "SHA-256 ${source.sha256.take(12)}…",
                        color = secondary,
                        fontSize = 10.sp,
                        maxLines = 1,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                MuseGlassSwitch(
                    checked = source.enabled,
                    onCheckedChange = onEnabledChange,
                    accentColor = accentColor,
                    backdrop = backdrop
                )
                MuseIconButton(onClick = onDelete) {
                    Icon(
                        painterResource(R.drawable.ic_apple_trash),
                        contentDescription = "删除 ${source.name}",
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
