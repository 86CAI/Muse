/*
 * MeloX 首页
 *
 * Ported from NEORUAA/Mei_MeloX_Android
 * (ui/screen/main/home/HomeScreen.kt)：IosPinnedPage 固定标题容器 +
 * 34sp 大标题(滚动模糊收起) + 问候语 RecommendCard 行(160dp 方卡) +
 * 分区标题(20sp Bold) + 歌单卡 LazyRow(spacedBy 12dp)。数值与上游一致。
 *
 * Upstream: https://github.com/NEORUAA/Mei_MeloX_Android
 * License: GNU General Public License v3.0 (GPL-3.0) - see licenses/GPL-3.0.txt
 * Full attribution list: THIRD_PARTY_NOTICES.md
 */
package com.caipan.music.ui.melox

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import androidx.compose.ui.draw.clip
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import com.kyant.capsule.ContinuousRoundedRectangle

/** 首页问候区大卡数据（每日推荐/热歌榜等）。 */
data class MeloXRecommendCardData(
    val key: String,
    val cover: Any?,
    val title: String,
    val eyebrow: String,
    val onClick: () -> Unit = {},
)

/** 首页歌单分区。 */
data class MeloXHomePlaylistSection(
    val key: String,
    val title: String,
    val playlists: List<MeloXCollectionCard>,
)

/** 对应上游 PAGE_RECOMMEND_PRIVATE_RCMD_SONG 的三行歌曲区块。 */
data class MeloXHomeTrackSection(
    val key: String,
    val title: String,
    val tracks: List<MeloXHomeTrack>,
)

data class MeloXHomeTrack(
    val key: String,
    val title: String,
    val artist: String,
    val artwork: Any?,
    val onClick: () -> Unit,
)

@Composable
fun MeloXHomeScreen(
    title: String,
    greetingTitle: String,
    recommendCards: List<MeloXRecommendCardData>,
    playlistSections: List<MeloXHomePlaylistSection>,
    trackSections: List<MeloXHomeTrackSection>,
    bottomPadding: Dp,
    modifier: Modifier = Modifier,
) {
    val glassColors = LocalGlassColors.current
    IosPinnedListPage(
        title = title,
        bottomPadding = bottomPadding,
        horizontalContentPadding = 0.dp,
        backgroundColor = if (glassColors.isDark) glassColors.groupedBackground else Color.White,
        modifier = modifier,
    ) {
        item(key = "home-greeting") {
            Column {
                Text(
                    text = greetingTitle,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        count = recommendCards.size,
                        key = { recommendCards[it].key },
                    ) { index ->
                        val card = recommendCards[index]
                        MeloXRecommendCard(
                            cover = card.cover,
                            title = card.title,
                            extInfo = CardExtInfo(icon = null, text = card.eyebrow),
                            cardWidth = RecommendCardWidth,
                            cardHeight = RecommendCardHeight,
                            onClick = card.onClick,
                        )
                    }
                }
            }
        }
        playlistSections.forEach { section ->
            if (section.playlists.isEmpty()) return@forEach
            item(key = "section-title-${section.key}") {
                Text(
                    text = section.title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    maxLines = 1,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp)
                )
            }
            item(key = "section-row-${section.key}") {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(horizontal = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(
                        count = section.playlists.size,
                        key = { section.playlists[it].key },
                    ) { index ->
                        val collection = section.playlists[index]
                        MeloXPlaylistCard(
                            title = collection.title,
                            coverImg = collection.coverUri ?: collection.coverUrl,
                            showPlay = true,
                            extInfo = collection.playCount.takeIf { it > 0 }?.let { compactCount(it) },
                            cardSize = PlaylistCardSize,
                            onClick = collection.onClick,
                        )
                    }
                }
            }
        }
        trackSections.forEach { section ->
            if (section.tracks.isEmpty()) return@forEach
            item(key = "track-title-${section.key}") {
                Text(
                    text = section.title,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                )
            }
            item(key = "track-list-${section.key}") {
                Column(Modifier.padding(horizontal = 16.dp)) {
                    section.tracks.take(6).forEach { track ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable(onClick = track.onClick)
                                .padding(vertical = 6.dp),
                            verticalAlignment = androidx.compose.ui.Alignment.CenterVertically,
                        ) {
                            AsyncImage(
                                model = track.artwork,
                                contentDescription = null,
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .size(56.dp)
                                    .clip(ContinuousRoundedRectangle(8.dp)),
                            )
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(track.title, fontSize = 15.sp, fontWeight = FontWeight.Bold,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(track.artist, fontSize = 12.sp,
                                    color = LocalGlassColors.current.secondaryContent,
                                    maxLines = 1, overflow = TextOverflow.Ellipsis)
                            }
                            SfIcon(SfSymbol.PlayFilled, null, size = 24.dp, tint = LocalGlassColors.current.accent)
                        }
                    }
                }
            }
        }
    }
}
