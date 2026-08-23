/*
 * MeloX 关于页
 *
 * Ported from NEORUAA/Mei_MeloX_Android (ui/screen/about/AboutScreen.kt)：
 * 居中 logo 84dp / 22dp 圆角 + 名称 headlineMedium Bold + 版本 secondaryContent，
 * 其后为 SettingsGroup + AboutEntry 分组行。内容替换为 Muse 自身信息。
 *
 * Upstream: https://github.com/NEORUAA/Mei_MeloX_Android
 * License: GNU General Public License v3.0 (GPL-3.0) - see licenses/GPL-3.0.txt
 * Full attribution list: THIRD_PARTY_NOTICES.md
 */
package com.caipan.music.ui.melox

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.caipan.music.R
import com.kyant.capsule.ContinuousRoundedRectangle

@Composable
fun MeloXAboutScreen(
    version: String,
    currentLanguage: String,
    bottomPadding: Dp,
    onLanguageChanged: (String) -> Unit,
    onOpenContributor: (String) -> Unit,
    onNavigateBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var showLicenses by remember { mutableStateOf(false) }
    if (showLicenses) {
        MeloXLicensesScreen(
            currentLanguage = currentLanguage,
            bottomPadding = bottomPadding,
            onNavigateBack = { showLicenses = false },
            modifier = modifier,
        )
        return
    }
    val onOpenLicenses = { showLicenses = true }
    val colors = LocalGlassColors.current
    val zh = currentLanguage == "zh"
    IosPinnedListPage(
        title = if (zh) "关于" else "About",
        bottomPadding = bottomPadding,
        horizontalContentPadding = 0.dp,
        onNavigateBack = onNavigateBack,
        modifier = modifier,
    ) {
        item(key = "about-header") {
            Column(
                Modifier.fillMaxWidth().padding(vertical = 18.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Image(
                    painter = painterResource(R.drawable.app_icon),
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(84.dp)
                        .clip(ContinuousRoundedRectangle(22.dp)),
                )
                Text(
                    "Muse",
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    color = colors.content,
                )
                Text(
                    (if (zh) "版本 " else "Version ") + version,
                    style = IosTypography.subheadline,
                    color = colors.secondaryContent,
                )
            }
        }

        item(key = "about-info") {
            MeloXSettingsGroup(if (zh) "信息" else "Information") {
                IosListRow(
                    title = if (zh) "开发者" else "Developer",
                    detail = "Cai & Caiyu",
                    showTopSeparator = false,
                )
                IosListRow(
                    title = "AI",
                    detail = "DeepSeek · Claude · ChatGPT",
                )
                IosListRow(
                    title = if (zh) "界面来源" else "UI source",
                    detail = "Mei_MeloX_Android",
                )
            }
        }

        item(key = "about-open-source") {
            MeloXSettingsGroup(if (zh) "开源" else "Open source") {
                MeloXSettingsEntry(
                    title = if (zh) "开源许可" else "Open-source licenses",
                    subtitle = if (zh) "来源、许可证与全文" else "Sources, licenses and full texts",
                    symbol = SfSymbol.InfoCircle,
                    showTopSeparator = false,
                    onClick = onOpenLicenses,
                )
                MeloXSettingsHintRow(
                    if (zh) {
                        "MeloX 界面移植自 Mei_MeloX_Android (GPL-3.0)；另含 Symphony (AGPL-3.0) 与 " +
                            "AndroidLiquidGlass (Apache-2.0) 的改编代码。分发本应用时须一并提供源代码。"
                    } else {
                        "The MeloX UI is ported from Mei_MeloX_Android (GPL-3.0); Symphony (AGPL-3.0) and " +
                            "AndroidLiquidGlass (Apache-2.0) code is also adapted here. Distributing this " +
                            "app requires providing the corresponding source code."
                    },
                )
            }
        }

        item(key = "about-contributors") {
            MeloXSettingsGroup(if (zh) "项目成员" else "Contributors") {
                MeloXSettingsEntry(
                    title = "开发菜",
                    subtitle = if (zh) "规划与调教" else "Planning & direction",
                    symbol = SfSymbol.PersonCropCircle,
                    showTopSeparator = false,
                    onClick = { onOpenContributor("https://www.coolapk.com/u/34225684") },
                )
                MeloXSettingsEntry(
                    title = "caiyu",
                    subtitle = if (zh) "测试与支持" else "Testing & support",
                    symbol = SfSymbol.PersonCropCircle,
                    onClick = { onOpenContributor("https://www.coolapk.com/u/39666287") },
                )
            }
        }

        item(key = "about-language") {
            MeloXSettingsGroup(if (zh) "语言" else "Language") {
                MeloXSettingsChoiceRow(
                    title = if (zh) "界面语言" else "App language",
                    symbol = SfSymbol.Safari,
                    selected = currentLanguage,
                    values = listOf("zh", "en"),
                    valueLabel = { if (it == "zh") "中文" else "English" },
                    onSelected = onLanguageChanged,
                )
            }
        }

        item(key = "about-slogan") {
            Row(
                Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 24.dp),
                horizontalArrangement = Arrangement.Center,
            ) {
                Text(
                    if (zh) "为热爱音乐的人而做" else "Made for people who love music",
                    style = IosTypography.caption,
                    color = colors.secondaryContent,
                )
            }
        }
    }
}
