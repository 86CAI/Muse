/*
 * MeloX 迷你播放器
 *
 * Ported from NEORUAA/Mei_MeloX_Android
 * (app/src/main/java/com/ljyh/mei/ui/component/player/MiniPlayer.kt)：
 * Navigation 风格玻璃胶囊(48dp 高)、32dp 封面圆角 ThumbnailCornerRadius、
 * 标题 15sp SemiBold、播放/前进 SfIcon 22dp、compact 时收窄并隐藏前进键。
 *
 * Upstream: https://github.com/NEORUAA/Mei_MeloX_Android
 * License: GNU General Public License v3.0 (GPL-3.0) - see licenses/GPL-3.0.txt
 * Full attribution list: THIRD_PARTY_NOTICES.md
 */
package com.caipan.music.ui.melox

import androidx.compose.foundation.basicMarquee
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.caipan.music.model.Song
import com.kyant.backdrop.Backdrop
import com.kyant.capsule.ContinuousRoundedRectangle
import com.kyant.shapes.Capsule

internal val ThumbnailCornerRadius = 8.dp

@Composable
fun MeloXMiniPlayer(
    song: Song,
    isPlaying: Boolean,
    compactProgress: Float = 0f,
    modifier: Modifier = Modifier,
    backdrop: Backdrop = LocalGlassBackdrop.current,
    artworkUri: android.net.Uri? = null,
    hasNext: Boolean = true,
    onArtworkBoundsChanged: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
    onClick: () -> Unit,
    onTogglePlayPause: () -> Unit,
    onNext: () -> Unit,
) {
    val progress = compactProgress.coerceIn(0f, 1f)
    val horizontalInset = 12.dp + 60.dp * progress
    val nextVisibility = 1f - progress

    GlassSurface(
        modifier = modifier
            .fillMaxWidth()
            .height(MiniPlayerHeight)
            .padding(horizontal = horizontalInset),
        backdrop = backdrop,
        shape = Capsule(),
        style = GlassSurfaceStyle.Navigation,
        onClick = onClick,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 4.dp, vertical = 2.dp),
        ) {
            Box(Modifier.weight(1f)) {
                MiniMediaInfo(
                    song = song,
                    artworkUri = artworkUri,
                    modifier = Modifier.padding(horizontal = 2.dp),
                    onArtworkBoundsChanged = onArtworkBoundsChanged,
                )
            }

            androidx.compose.material3.IconButton(
                modifier = Modifier.size(40.dp),
                onClick = onTogglePlayPause,
            ) {
                SfIcon(
                    symbol = if (isPlaying) SfSymbol.PauseFilled else SfSymbol.PlayFilled,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurface,
                    size = 22.dp,
                    weight = FontWeight.SemiBold,
                )
            }

            if (nextVisibility > 0.001f) {
                Box(
                    modifier = Modifier
                        .width(40.dp * nextVisibility)
                        .graphicsLayer {
                            alpha = nextVisibility
                            val scale = 0.82f + 0.18f * nextVisibility
                            scaleX = scale
                            scaleY = scale
                            clip = true
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    androidx.compose.material3.IconButton(
                        modifier = Modifier.size(40.dp),
                        enabled = hasNext,
                        onClick = onNext,
                    ) {
                        SfIcon(
                            symbol = SfSymbol.ForwardFilled,
                            contentDescription = "下一首",
                            tint = MaterialTheme.colorScheme.onSurface,
                            size = 22.dp,
                            weight = FontWeight.SemiBold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun MiniMediaInfo(
    song: Song,
    artworkUri: android.net.Uri?,
    modifier: Modifier = Modifier,
    onArtworkBoundsChanged: ((androidx.compose.ui.geometry.Rect) -> Unit)? = null,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = modifier,
    ) {
        Box(modifier = Modifier.padding(4.dp)) {
            Spacer(modifier = Modifier.size(32.dp))
            AsyncImage(
                model = artworkUri ?: song.albumArtUri,
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .size(32.dp)
                    .onGloballyPositioned { coordinates ->
                        onArtworkBoundsChanged?.invoke(coordinates.boundsInRoot())
                    }
                    .alpha(1f)
                    .clip(ContinuousRoundedRectangle(ThumbnailCornerRadius)),
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 6.dp),
        ) {
            Text(
                text = song.title,
                color = MaterialTheme.colorScheme.onSurface,
                fontSize = 15.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee(),
            )
            Text(
                text = song.artist,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                fontSize = 12.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.basicMarquee(),
            )
        }
    }
}
