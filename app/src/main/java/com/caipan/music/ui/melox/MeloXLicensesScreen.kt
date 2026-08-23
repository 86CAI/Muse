/*
 * MeloX 风格开源许可页。
 *
 * 复用本目录下从 NEORUAA/Mei_MeloX_Android (GPL-3.0) 移植的 iOS 分组列表组件，
 * 数据源与 Muse Original 风格的许可页共用 OpenSourceRegistry，
 * 用于在应用内履行 GPL-3.0 / AGPL-3.0 的来源与许可证告知义务。
 *
 * Upstream: https://github.com/NEORUAA/Mei_MeloX_Android
 * License: GNU General Public License v3.0 (GPL-3.0) - see licenses/GPL-3.0.txt
 * Full attribution list: THIRD_PARTY_NOTICES.md
 */
package com.caipan.music.ui.melox

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caipan.music.ui.components.OpenSourceRegistry
import com.caipan.music.ui.components.OssEntry

@Composable
fun MeloXLicensesScreen(
    currentLanguage: String,
    bottomPadding: Dp,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val zh = currentLanguage == "zh"
    val context = LocalContext.current
    val colors = LocalGlassColors.current
    var viewing by remember { mutableStateOf<OssEntry?>(null) }

    val current = viewing
    if (current?.licenseAsset != null) {
        val text = remember(current.licenseAsset) {
            OpenSourceRegistry.readAsset(context, current.licenseAsset)
                ?: if (zh) "许可证全文未随此构建分发。" else "License text is not bundled in this build."
        }
        IosPinnedListPage(
            title = current.name,
            subtitle = current.license,
            bottomPadding = bottomPadding,
            showsLargeTitle = false,
            onNavigateBack = { viewing = null },
            modifier = modifier,
        ) {
            item(key = "license-text") {
                Text(
                    text,
                    color = colors.content.copy(alpha = .9f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 11.sp,
                    modifier = Modifier.horizontalScroll(rememberScrollState()).padding(bottom = 24.dp),
                )
            }
        }
        return
    }

    IosPinnedListPage(
        title = if (zh) "开源许可" else "Open-source licenses",
        bottomPadding = bottomPadding,
        horizontalContentPadding = 0.dp,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    ) {
        item(key = "licenses-intro") {
            Text(
                if (zh) {
                    "Muse 使用、移植或改编了下列开源项目的代码与素材，版权归各原作者所有。" +
                        "其中包含 GPL-3.0 与 AGPL-3.0 授权的代码，分发 Muse 时须同时提供对应源代码。"
                } else {
                    "Muse uses, ports, or adapts code and assets from the projects below; copyright " +
                        "remains with their authors. Some are GPL-3.0 / AGPL-3.0, so distributing Muse " +
                        "requires providing the corresponding source code."
                },
                style = MaterialTheme.typography.bodySmall,
                color = colors.secondaryContent,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )
        }

        item(key = "licenses-self") {
            // AGPL-3.0 §5d: Appropriate Legal Notices —— Muse 自身的版权与担保声明。
            MeloXSettingsGroup(if (zh) "本程序的许可证" else "This program's license") {
                IosListRow(
                    title = OpenSourceRegistry.museSelf.name,
                    subtitle = if (zh) OpenSourceRegistry.museSelf.usageZh else OpenSourceRegistry.museSelf.usageEn,
                    detail = OpenSourceRegistry.museSelf.license,
                    showTopSeparator = false,
                    onClick = { viewing = OpenSourceRegistry.museSelf },
                )
            }
        }

        OpenSourceRegistry.sections.forEach { section ->
            item(key = "section:${section.titleEn}") {
                MeloXSettingsGroup(if (zh) section.titleZh else section.titleEn) {
                    section.entries.forEachIndexed { index, entry ->
                        IosListRow(
                            title = entry.name,
                            subtitle = if (zh) entry.usageZh else entry.usageEn,
                            detail = entry.license,
                            showTopSeparator = index > 0,
                            onClick = entry.licenseAsset?.let { { viewing = entry } },
                        )
                        val warning = if (zh) entry.warningZh else entry.warningEn
                        if (warning != null) {
                            Text(
                                warning,
                                style = MaterialTheme.typography.bodySmall,
                                color = colors.destructive,
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                        if (entry.url.isNotBlank()) {
                            Row(
                                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.End,
                            ) {
                                Text(
                                    if (zh) "打开仓库" else "Open repository",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colors.accent,
                                    modifier = Modifier.clickable {
                                        runCatching {
                                            context.startActivity(
                                                Intent(Intent.ACTION_VIEW, Uri.parse(entry.url)).apply {
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
            }
        }

        item(key = "licenses-footer") {
            Column(Modifier.padding(horizontal = 16.dp, vertical = 16.dp)) {
                Text(
                    if (zh) {
                        "如发现署名遗漏或许可证标注错误，欢迎通过仓库 Issues 反馈，我们会尽快更正。"
                    } else {
                        "If attribution is missing or a license is stated incorrectly, please open an issue."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = colors.secondaryContent,
                )
            }
        }
    }
}
