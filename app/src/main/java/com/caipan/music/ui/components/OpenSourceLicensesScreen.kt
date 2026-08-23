/*
 * 开源许可页（Muse Original 风格）。
 *
 * 承担 GPL-3.0 / AGPL-3.0 的应用内告知义务：列出所有上游来源、许可证，
 * 并可直接阅读随 APK 分发的许可证全文。数据源为 OpenSourceRegistry。
 */
package com.caipan.music.ui.components

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caipan.music.R
import com.caipan.music.plugin.BlurLocation
import com.kyant.backdrop.Backdrop

@Composable
fun OpenSourceLicensesScreen(
    onDismiss: () -> Unit,
    currentLanguage: String,
    accentColor: Color = Color(0xFF1DB954),
    isLightTheme: Boolean = false,
    backdrop: Backdrop? = null,
) {
    val zh = currentLanguage == "zh"
    val context = LocalContext.current
    val textPrimary = MaterialTheme.colorScheme.onBackground
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    var viewing by remember { mutableStateOf<OssEntry?>(null) }

    // 内层 BackHandler 后注册，优先级高于「关于」页，保证返回键先退出许可页。
    BackHandler(enabled = true) {
        if (viewing != null) viewing = null else onDismiss()
    }

    val current = viewing
    if (current?.licenseAsset != null) {
        LicenseTextScreen(
            entry = current,
            isChinese = zh,
            isLightTheme = isLightTheme,
            backdrop = backdrop,
            onBack = { viewing = null },
        )
        return
    }

    FullScreenGlassRoute(backdrop = backdrop, isLightTheme = isLightTheme) {
        Column(
            Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())
        ) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MuseIconButton(onClick = onDismiss) {
                    Icon(
                        painterResource(R.drawable.ic_apple_arrow_left),
                        if (zh) "返回" else "Back",
                        tint = textPrimary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Text(
                    if (zh) "开源许可" else "Open-source licenses",
                    color = textPrimary,
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }

            Text(
                if (zh) {
                    "Muse 使用、移植或改编了下列开源项目的代码与素材，版权归各原作者所有。" +
                        "其中包含 GPL-3.0 与 AGPL-3.0 授权的代码，分发 Muse 时须同时提供对应源代码。"
                } else {
                    "Muse uses, ports, or adapts code and assets from the projects below; copyright " +
                        "remains with their authors. Some are GPL-3.0 / AGPL-3.0, so distributing Muse " +
                        "requires providing the corresponding source code."
                },
                color = textSecondary,
                fontSize = 13.sp,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
            )

            // AGPL-3.0 §5d: Appropriate Legal Notices —— Muse 自身的版权与担保声明。
            Text(
                if (zh) "本程序的许可证" else "This program's license",
                color = accentColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp),
            )
            Card(
                Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                    .museGlass(
                        backdrop,
                        RoundedCornerShape(16.dp),
                        MaterialTheme.colorScheme.surface.copy(alpha = .4f),
                        location = BlurLocation.CARDS,
                        readabilityBoost = true,
                    ),
                colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                shape = RoundedCornerShape(16.dp),
            ) {
                OssRow(
                    entry = OpenSourceRegistry.museSelf,
                    isChinese = zh,
                    accent = accentColor,
                    onOpenLicense = { viewing = OpenSourceRegistry.museSelf },
                    onOpenUrl = { url ->
                        runCatching {
                            context.startActivity(
                                Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                    addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                },
                            )
                        }
                    },
                )
            }

            OpenSourceRegistry.sections.forEach { section ->
                Text(
                    if (zh) section.titleZh else section.titleEn,
                    color = accentColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp, bottom = 8.dp),
                )
                Card(
                    Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                        .museGlass(
                            backdrop,
                            RoundedCornerShape(16.dp),
                            MaterialTheme.colorScheme.surface.copy(alpha = .4f),
                            location = BlurLocation.CARDS,
                            readabilityBoost = true,
                        ),
                    colors = CardDefaults.cardColors(containerColor = Color.Transparent),
                    shape = RoundedCornerShape(16.dp),
                ) {
                    Column {
                        section.entries.forEachIndexed { index, entry ->
                            if (index > 0) {
                                HorizontalDivider(
                                    color = textSecondary.copy(alpha = .1f),
                                    modifier = Modifier.padding(horizontal = 16.dp),
                                )
                            }
                            OssRow(
                                entry = entry,
                                isChinese = zh,
                                accent = accentColor,
                                onOpenLicense = { viewing = entry },
                                onOpenUrl = { url ->
                                    runCatching {
                                        context.startActivity(
                                            Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
                                                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                                            },
                                        )
                                    }
                                },
                            )
                        }
                    }
                }
            }

            Spacer(Modifier.height(24.dp))
            Text(
                if (zh) {
                    "如发现署名遗漏或许可证标注错误，欢迎通过仓库 Issues 反馈，我们会尽快更正。"
                } else {
                    "If attribution is missing or a license is stated incorrectly, please open an issue."
                },
                color = textSecondary,
                fontSize = 12.sp,
                modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 48.dp),
            )
        }
    }
}

@Composable
private fun OssRow(
    entry: OssEntry,
    isChinese: Boolean,
    accent: Color,
    onOpenLicense: () -> Unit,
    onOpenUrl: (String) -> Unit,
) {
    val textPrimary = MaterialTheme.colorScheme.onBackground
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    Column(
        Modifier.fillMaxWidth()
            .then(if (entry.licenseAsset != null) Modifier.clickable(onClick = onOpenLicense) else Modifier)
            .padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                entry.name,
                color = textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier.clip(RoundedCornerShape(6.dp)).background(accent.copy(alpha = .14f))
                    .padding(horizontal = 8.dp, vertical = 3.dp),
            ) {
                Text(entry.license, color = accent, fontSize = 11.sp, fontWeight = FontWeight.Medium)
            }
        }
        Text(
            if (isChinese) entry.usageZh else entry.usageEn,
            color = textSecondary,
            fontSize = 12.sp,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (entry.paths.isNotEmpty()) {
            Text(
                entry.paths.joinToString("  ·  "),
                color = textSecondary.copy(alpha = .7f),
                fontSize = 11.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        val warning = if (isChinese) entry.warningZh else entry.warningEn
        if (warning != null) {
            Row(Modifier.padding(top = 6.dp)) {
                Icon(
                    painterResource(R.drawable.ic_apple_circle_alert),
                    null,
                    tint = MaterialTheme.colorScheme.error,
                    modifier = Modifier.size(14.dp).padding(top = 1.dp),
                )
                Text(
                    warning,
                    color = MaterialTheme.colorScheme.error,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        Row(
            Modifier.padding(top = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            if (entry.licenseAsset != null) {
                Text(
                    if (isChinese) "查看许可证全文" else "Read license",
                    color = accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable(onClick = onOpenLicense),
                )
            }
            if (entry.url.isNotBlank()) {
                Text(
                    if (isChinese) "打开仓库" else "Open repository",
                    color = accent,
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Medium,
                    modifier = Modifier.clickable { onOpenUrl(entry.url) },
                )
            }
        }
    }
}

@Composable
private fun LicenseTextScreen(
    entry: OssEntry,
    isChinese: Boolean,
    isLightTheme: Boolean,
    backdrop: Backdrop?,
    onBack: () -> Unit,
) {
    val context = LocalContext.current
    val asset = entry.licenseAsset
    val text = remember(asset) {
        asset?.let { OpenSourceRegistry.readAsset(context, it) }
            ?: if (isChinese) "许可证全文未随此构建分发。" else "License text is not bundled in this build."
    }
    val textPrimary = MaterialTheme.colorScheme.onBackground
    FullScreenGlassRoute(backdrop = backdrop, isLightTheme = isLightTheme) {
        Column(Modifier.fillMaxSize().statusBarsPadding()) {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                MuseIconButton(onClick = onBack) {
                    Icon(
                        painterResource(R.drawable.ic_apple_arrow_left),
                        if (isChinese) "返回" else "Back",
                        tint = textPrimary,
                        modifier = Modifier.size(24.dp),
                    )
                }
                Column(Modifier.padding(start = 8.dp)) {
                    Text(entry.name, color = textPrimary, style = MaterialTheme.typography.titleMedium)
                    Text(
                        entry.license,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontSize = 12.sp,
                    )
                }
            }
            Box(
                Modifier.fillMaxSize().padding(horizontal = 16.dp)
                    .verticalScroll(rememberScrollState()),
            ) {
                Text(
                    text,
                    color = textPrimary.copy(alpha = .9f),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 48.dp),
                )
            }
        }
    }
}
