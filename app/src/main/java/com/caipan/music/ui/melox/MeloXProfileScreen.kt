/*
 * MeloX 个人页
 *
 * Uses the same grouped iOS list language as NEORUAA/Mei_MeloX_Android's
 * account screens while retaining Muse's existing settings destination.
 *
 * Upstream: https://github.com/NEORUAA/Mei_MeloX_Android
 * License: GNU General Public License v3.0 (GPL-3.0) - see licenses/GPL-3.0.txt
 * Full attribution list: THIRD_PARTY_NOTICES.md
 */
package com.caipan.music.ui.melox

import android.net.Uri
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.kyant.capsule.ContinuousRoundedRectangle

@Composable
fun MeloXProfileScreen(
    profileName: String,
    profileAvatar: Uri?,
    bottomPadding: Dp,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalGlassColors.current
    IosPinnedListPage(
        title = "我的",
        bottomPadding = bottomPadding,
        horizontalContentPadding = 0.dp,
        backgroundColor = if (colors.isDark) colors.groupedBackground else androidx.compose.ui.graphics.Color.White,
        modifier = modifier,
    ) {
        item(key = "profile-header") {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Box(
                    modifier = Modifier
                        .size(88.dp)
                        .clip(ContinuousRoundedRectangle(44.dp)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (profileAvatar != null) {
                        AsyncImage(
                            model = profileAvatar,
                            contentDescription = null,
                            modifier = Modifier.matchParentSize(),
                            contentScale = ContentScale.Crop,
                        )
                    } else {
                        SfIcon(SfSymbol.PersonFilled, null, size = 42.dp, tint = colors.secondaryContent)
                    }
                }
                Text(
                    profileName.ifBlank { "Muse 用户" },
                    style = IosTypography.title2,
                    color = colors.content,
                    modifier = Modifier.padding(top = 12.dp),
                )
                Text(
                    "我的音乐与偏好",
                    style = IosTypography.subheadline,
                    color = colors.secondaryContent,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
        }
        item(key = "profile-settings") {
            MeloXSettingsGroup("我的音乐") {
                MeloXSettingsEntry(
                    title = "设置",
                    subtitle = "账号、播放、外观与扩展",
                    symbol = SfSymbol.Gearshape,
                    showTopSeparator = false,
                    onClick = onOpenSettings,
                )
            }
        }
    }
}
