package com.caipan.music.ui.components

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.Image
import com.caipan.music.R
import com.caipan.music.plugin.BlurLocation
import com.kyant.backdrop.Backdrop

private class AboutStrings(private val isZh: Boolean) {
    val about get() = if (isZh) "关于" else "About"
    val version get() = if (isZh) "版本" else "Version"
    val developer get() = if (isZh) "开发者" else "Developer"
    val language get() = if (isZh) "语言" else "Language"
    val subtitle get() = if (isZh) "安卓音乐播放器" else "Android Music Player"
    val slogan get() = if (isZh) "🎵 用音乐点亮生活" else "🎵 Music lights up life"
    val openSource get() = if (isZh) "开源" else "Open source"
    val licenses get() = if (isZh) "开源许可" else "Open-source licenses"
    val licensesHint get() = if (isZh) "来源、许可证与全文" else "Sources, licenses and full texts"
}

@Composable
fun AboutScreen(
    onDismiss: () -> Unit,
    onLanguageChanged: (String) -> Unit,
    currentLanguage: String,
    accentColor: Color = Color(0xFF1DB954),
    isLightTheme: Boolean = false,
    backdrop: Backdrop? = null
) {
    var showLicenses by remember { mutableStateOf(false) }
    if (showLicenses) {
        OpenSourceLicensesScreen(
            onDismiss = { showLicenses = false },
            currentLanguage = currentLanguage,
            accentColor = accentColor,
            isLightTheme = isLightTheme,
            backdrop = backdrop,
        )
        return
    }
    val s = remember(currentLanguage) { AboutStrings(currentLanguage == "zh") }
    val textPrimary = MaterialTheme.colorScheme.onBackground
    val textSecondary = MaterialTheme.colorScheme.onSurfaceVariant
    val cardColor = Color.Transparent
    val context = LocalContext.current
    fun openCoolApkUser(url: String) {
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(url)).apply {
            setPackage("com.coolapk.market")
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        runCatching { context.startActivity(intent) }
            .onFailure { Toast.makeText(context, "请先安装酷安 App", Toast.LENGTH_SHORT).show() }
    }

    FullScreenGlassRoute(backdrop = backdrop, isLightTheme = isLightTheme) {
        Column(Modifier.fillMaxSize().statusBarsPadding().verticalScroll(rememberScrollState())) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically) {
                MuseIconButton(onClick = onDismiss) {
                    Icon(painterResource(R.drawable.ic_apple_arrow_left), if (currentLanguage == "zh") "返回" else "Back", tint = textPrimary, modifier = Modifier.size(24.dp))
                }
                Text(s.about, color = textPrimary, style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(start = 8.dp))
            }

            Spacer(Modifier.height(40.dp))

            // App icon
            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                Box(Modifier.size(80.dp).clip(RoundedCornerShape(20.dp)), contentAlignment = Alignment.Center) {
                    Image(painter = painterResource(id = R.drawable.app_icon), contentDescription = "App Icon",
                        modifier = Modifier.matchParentSize(), contentScale = ContentScale.Crop)
                }
            }

            Spacer(Modifier.height(16.dp))
            Text("Muse", color = textPrimary, fontSize = 24.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(4.dp))
            Text(s.subtitle, color = textSecondary, fontSize = 14.sp,
                modifier = Modifier.fillMaxWidth(), textAlign = TextAlign.Center)
            Spacer(Modifier.height(32.dp))

            // Info
            Card(Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                .museGlass(backdrop, RoundedCornerShape(16.dp), MaterialTheme.colorScheme.surface.copy(alpha = .4f),
                    location = BlurLocation.CARDS, readabilityBoost = true),
                colors = CardDefaults.cardColors(containerColor = cardColor),
                shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(20.dp)) {
                    InfoRow(s.version, "2.717", textPrimary, textSecondary)
                    HorizontalDivider(color = textSecondary.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 8.dp))
                    InfoRow(s.developer, "Cai & Caiyu", textPrimary, textSecondary)
                    HorizontalDivider(color = textSecondary.copy(alpha = 0.1f), modifier = Modifier.padding(vertical = 8.dp))
                    InfoRow("AI", "DeepSeek · Claude · ChatGPT", textPrimary, textSecondary)
                }
            }

            Spacer(Modifier.height(24.dp))

            Text(if (currentLanguage == "zh") "项目成员" else "Contributors", color = accentColor,
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            Card(Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                .museGlass(backdrop, RoundedCornerShape(16.dp), MaterialTheme.colorScheme.surface.copy(alpha = .4f),
                    location = BlurLocation.CARDS, readabilityBoost = true),
                colors = CardDefaults.cardColors(containerColor = cardColor),
                shape = RoundedCornerShape(16.dp)) {
                Column {
                    ContributorRow(
                        name = "开发菜",
                        role = if (currentLanguage == "zh") "规划与调教" else "Planning & direction",
                        accent = accentColor,
                        onClick = { openCoolApkUser("https://www.coolapk.com/u/34225684") }
                    )
                    HorizontalDivider(color = textSecondary.copy(alpha = .1f), modifier = Modifier.padding(start = 68.dp))
                    ContributorRow(
                        name = "caiyu",
                        role = if (currentLanguage == "zh") "测试与支持" else "Testing & support",
                        accent = accentColor,
                        onClick = { openCoolApkUser("https://www.coolapk.com/u/39666287") }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Open source attribution
            Text(s.openSource, color = accentColor,
                fontSize = 13.sp, fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp))
            Card(Modifier.fillMaxWidth().padding(horizontal = 24.dp)
                .museGlass(backdrop, RoundedCornerShape(16.dp), MaterialTheme.colorScheme.surface.copy(alpha = .4f),
                    location = BlurLocation.CARDS, readabilityBoost = true),
                colors = CardDefaults.cardColors(containerColor = cardColor),
                shape = RoundedCornerShape(16.dp)) {
                Column {
                    Row(
                        Modifier.fillMaxWidth().clickable { showLicenses = true }
                            .padding(horizontal = 16.dp, vertical = 14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(accentColor.copy(alpha = .14f)),
                            contentAlignment = Alignment.Center) {
                            Icon(painterResource(R.drawable.ic_apple_file_text), null, tint = accentColor,
                                modifier = Modifier.size(22.dp))
                        }
                        Column(Modifier.weight(1f).padding(start = 12.dp)) {
                            Text(s.licenses, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
                            Text(s.licensesHint, color = textSecondary, fontSize = 12.sp,
                                modifier = Modifier.padding(top = 2.dp))
                        }
                        Icon(painterResource(R.drawable.ic_apple_chevron_right), null, tint = textSecondary,
                            modifier = Modifier.size(18.dp))
                    }
                    HorizontalDivider(color = textSecondary.copy(alpha = .1f), modifier = Modifier.padding(start = 68.dp))
                    Text(
                        if (currentLanguage == "zh")
                            "Muse 的界面移植自 Mei_MeloX_Android (GPL-3.0)、Symphony (AGPL-3.0) 与 " +
                                "AndroidLiquidGlass (Apache-2.0) 等开源项目。分发本应用时须一并提供对应源代码。"
                        else
                            "Muse's UI is ported from Mei_MeloX_Android (GPL-3.0), Symphony (AGPL-3.0), " +
                                "AndroidLiquidGlass (Apache-2.0) and others. Distributing this app requires " +
                                "providing the corresponding source code.",
                        color = textSecondary, fontSize = 12.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                    )
                }
            }

            Spacer(Modifier.height(24.dp))

            // Language
            Card(Modifier.fillMaxWidth().padding(horizontal = 20.dp)
                .museGlass(backdrop, MaterialTheme.shapes.large, MaterialTheme.colorScheme.surface.copy(alpha = .4f),
                    location = BlurLocation.CARDS, readabilityBoost = true),
                colors = CardDefaults.cardColors(containerColor = cardColor),
                shape = MaterialTheme.shapes.large) {
                Column(Modifier.padding(20.dp)) {
                    Text(s.language, color = textPrimary, fontSize = 16.sp, fontWeight = FontWeight.SemiBold)
                    Spacer(Modifier.height(12.dp))
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                        LangChip("中文", "zh", currentLanguage, onLanguageChanged, accentColor)
                        LangChip("English", "en", currentLanguage, onLanguageChanged, accentColor)
                    }
                }
            }

            Spacer(Modifier.height(32.dp))
            Text(s.slogan, color = textSecondary, fontSize = 13.sp,
                modifier = Modifier.fillMaxWidth().padding(bottom = 40.dp), textAlign = TextAlign.Center)
        }
    }
}

@Composable
private fun ContributorRow(name: String, role: String, accent: Color, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(Modifier.size(40.dp).clip(RoundedCornerShape(12.dp)).background(accent.copy(alpha = .14f)),
            contentAlignment = Alignment.Center) {
            Icon(painterResource(R.drawable.ic_apple_user), null, tint = accent, modifier = Modifier.size(22.dp))
        }
        Column(Modifier.weight(1f).padding(start = 12.dp)) {
            Text(name, fontSize = 15.sp, fontWeight = FontWeight.SemiBold)
            Text(role, color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 12.sp,
                modifier = Modifier.padding(top = 2.dp))
        }
        Icon(painterResource(R.drawable.ic_apple_external_link), "在酷安打开", tint = accent, modifier = Modifier.size(19.dp))
    }
}

@Composable
private fun InfoRow(label: String, value: String, primary: Color, secondary: Color) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, color = secondary, fontSize = 14.sp)
        Text(value, color = primary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun LangChip(label: String, code: String, current: String, onSelect: (String) -> Unit, accent: Color) {
    val sel = current == code
    MuseFilterChip(selected = sel, onClick = { if (!sel) onSelect(code) },
        label = { Text(label, color = if (sel) accent else MaterialTheme.colorScheme.onSurface, fontSize = 14.sp) })
}
