/*
 * MeloX 发现页
 *
 * Ported from NEORUAA/Mei_MeloX_Android（发现/歌单网格结构）：
 * IosPinnedPage 固定标题 + 分类 chips + 双列歌单卡网格。
 *
 * Upstream: https://github.com/NEORUAA/Mei_MeloX_Android
 * License: GNU General Public License v3.0 (GPL-3.0) - see licenses/GPL-3.0.txt
 * Full attribution list: THIRD_PARTY_NOTICES.md
 */
package com.caipan.music.ui.melox

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caipan.music.data.Playlist
import com.caipan.music.online.RemotePlaylistSummary

@Composable
fun MeloXExploreScreen(
    onlineHome: MeloXOnlineHomeContent?,
    playlists: List<Playlist>,
    bottomPadding: Dp,
    onOpenOnlinePlaylist: (RemotePlaylistSummary) -> Unit,
    onOpenPlaylistDetail: (Playlist) -> Unit,
    modifier: Modifier = Modifier,
) {
    val glassColors = LocalGlassColors.current
    val categories = buildList {
        if (!onlineHome?.recommendedPlaylists.isNullOrEmpty()) add("推荐歌单")
        if (!onlineHome?.chartPlaylists.isNullOrEmpty()) add("排行榜")
        if (onlineHome == null ||
            (onlineHome.recommendedPlaylists.isEmpty() && onlineHome.chartPlaylists.isEmpty())
        ) add("歌单")
    }
    var category by rememberSaveable { mutableStateOf(categories.firstOrNull() ?: "歌单") }

    IosPinnedListPage(
        title = "发现",
        bottomPadding = bottomPadding,
        backgroundColor = if (glassColors.isDark) glassColors.groupedBackground else Color.White,
        modifier = modifier,
    ) {
        item(key = "explore-categories") {
            LazyRow(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(count = categories.size, key = { categories[it] }) { index ->
                    val item = categories[index]
                    Text(
                        text = item.removeSuffix("歌单"),
                        modifier = Modifier
                            .clip(RoundedCornerShape(18.dp))
                            .background(
                                if (category == item) glassColors.accent
                                else glassColors.content.copy(alpha = 0.06f)
                            )
                            .clickable { category = item }
                            .padding(horizontal = 14.dp, vertical = 8.dp),
                        color = if (category == item) Color.White else glassColors.content,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
        }

        val collections = when (category) {
            "排行榜" -> onlineHome?.chartPlaylists.orEmpty().map { it.toExploreCard(onOpenOnlinePlaylist) }
            "推荐歌单" -> onlineHome?.recommendedPlaylists.orEmpty().map { it.toExploreCard(onOpenOnlinePlaylist) }
            else -> playlists.map { it.toExploreCard(onOpenPlaylistDetail) }
        }
        if (collections.isEmpty()) {
            item(key = "explore-empty") {
                Text(
                    "暂无内容",
                    style = IosTypography.subheadline,
                    color = glassColors.secondaryContent,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 24.dp),
                )
            }
        } else {
            // 双列网格：按行数估算高度，交给外层 LazyColumn 统一滚动（与上游网格密度一致）
            val rows = (collections.size + 1) / 2
            item(key = "explore-grid") {
                BoxWithConstraints(Modifier.fillMaxWidth()) {
                    // 16dp content inset is applied by IosPinnedListPage; derive
                    // the exact cell width from the remaining page width so both
                    // columns have identical edges and no trailing void.
                    val cardSize = (maxWidth - 12.dp) / 2f
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(2),
                        contentPadding = PaddingValues(bottom = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp),
                        userScrollEnabled = false,
                        modifier = Modifier
                            .fillMaxWidth()
                            .height((cardSize + 54.dp) * rows.toFloat()),
                    ) {
                        items(collections.size, key = { collections[it].key }) { index ->
                            val collection = collections[index]
                            MeloXPlaylistCard(
                                title = collection.title,
                                coverImg = collection.coverUri ?: collection.coverUrl,
                                showPlay = true,
                                extInfo = collection.playCount.takeIf { it > 0 }?.let { compactCount(it) },
                                cardSize = cardSize,
                                onClick = collection.onClick,
                            )
                        }
                    }
                }
            }
        }
    }
}

private fun RemotePlaylistSummary.toExploreCard(
    onClick: (RemotePlaylistSummary) -> Unit,
): MeloXCollectionCard = MeloXCollectionCard(
    key = "explore-remote-$id",
    title = name,
    coverUrl = coverUrl,
    playCount = playCount,
    onClick = { onClick(this@toExploreCard) },
)

private fun Playlist.toExploreCard(
    onClick: (Playlist) -> Unit,
): MeloXCollectionCard = MeloXCollectionCard(
    key = "explore-playlist-$id",
    title = name,
    coverUri = coverUri?.let(Uri::parse),
    onClick = { onClick(this@toExploreCard) },
)
