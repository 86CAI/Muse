/*
 * MeloX 风格在线歌单详情页。
 *
 * 基于本目录下从 NEORUAA/Mei_MeloX_Android (GPL-3.0) 移植的 iOS/玻璃组件
 * （IosPinnedListPage、MeloXSettingsGroup、IosListRow、SfSymbol 等）搭建，
 * 沿用其分组列表与置顶大标题的界面语言。
 *
 * Upstream: https://github.com/NEORUAA/Mei_MeloX_Android
 * License: GNU General Public License v3.0 —— 见 licenses/GPL-3.0.txt
 */
package com.caipan.music.ui.melox

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.caipan.music.online.OnlineTrack
import com.caipan.music.online.RemotePlaylistDetail
import com.kyant.capsule.ContinuousRoundedRectangle

@Composable
fun MeloXOnlinePlaylistDetailScreen(
    detail: RemotePlaylistDetail,
    bottomPadding: Dp,
    onDismiss: () -> Unit,
    onPlay: (List<OnlineTrack>, OnlineTrack) -> Unit,
    modifier: Modifier = Modifier,
) {
    val colors = LocalGlassColors.current
    IosPinnedListPage(
        title = detail.summary.name,
        subtitle = detail.summary.creatorName.takeIf { it.isNotBlank() },
        bottomPadding = bottomPadding,
        horizontalContentPadding = 0.dp,
        onNavigateBack = onDismiss,
        backgroundColor = if (colors.isDark) colors.groupedBackground else androidx.compose.ui.graphics.Color.White,
        modifier = modifier,
        showsLargeTitle = false,
    ) {
        item(key = "playlist-hero") {
            Column(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                AsyncImage(
                    model = detail.summary.coverUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(172.dp)
                        .clip(ContinuousRoundedRectangle(14.dp)),
                )
                Text(
                    detail.summary.name,
                    style = IosTypography.title2,
                    color = colors.content,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(top = 14.dp),
                )
                detail.summary.description?.takeIf { it.isNotBlank() }?.let { description ->
                    Text(
                        description,
                        style = IosTypography.caption,
                        color = colors.secondaryContent,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.padding(top = 5.dp),
                    )
                }
            }
        }
        if (detail.tracks.isNotEmpty()) {
            item(key = "playlist-play-all") {
                IosGroupedList {
                    IosListRow(
                        title = "播放全部",
                        leading = { SfIcon(SfSymbol.PlayFilled, null, size = 23.dp, tint = colors.accent) },
                        showTopSeparator = false,
                        onClick = { onPlay(detail.tracks, detail.tracks.first()) },
                    )
                }
            }
        }
        itemsIndexed(detail.tracks, key = { _, track -> track.stableId }) { _, track ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable { onPlay(detail.tracks, track) }
                    .padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                AsyncImage(
                    model = track.artworkUrl,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier
                        .size(54.dp)
                        .clip(ContinuousRoundedRectangle(9.dp)),
                )
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        track.title,
                        style = IosTypography.body,
                        color = colors.content,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        track.artist,
                        style = IosTypography.caption,
                        color = colors.secondaryContent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Text(track.formattedDuration, style = IosTypography.caption, color = colors.secondaryContent)
            }
        }
    }
}
