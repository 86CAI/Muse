package com.caipan.music.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.caipan.music.player.LyricLine
import kotlinx.coroutines.flow.first

@Composable
fun LyricsView(
    lyrics: List<LyricLine>,
    currentLine: Int,
    accentColor: Color,
    textPrimary: Color,
    textSecondary: Color,
    onTapLine: (Long) -> Unit,
    onClose: () -> Unit,
    showCloseButton: Boolean = true
) {
    val listState = rememberLazyListState()

    LaunchedEffect(currentLine, lyrics.size) {
        if (currentLine !in lyrics.indices) return@LaunchedEffect
        // The first composition can happen before LazyColumn has a measured viewport.
        // Waiting here prevents the initial lyric from remaining off-center.
        val viewportHeight = snapshotFlow { listState.layoutInfo.viewportSize.height }
            .first { it > 0 }
        val viewportCenter = listState.layoutInfo.viewportStartOffset + viewportHeight / 2
        val visibleItem = listState.layoutInfo.visibleItemsInfo
            .firstOrNull { it.index == currentLine }

        if (visibleItem != null) {
            // Normal playback moves to an adjacent, already visible lyric.
            // One animation based on its actual measured center is stable in landscape.
            val itemCenter = visibleItem.offset + visibleItem.size / 2
            val distance = (itemCenter - viewportCenter).toFloat()
            if (kotlin.math.abs(distance) > 1f) listState.animateScrollBy(distance)
        } else {
            // A user seek may target a far-away item; position it directly to avoid
            // a long traversal through the whole lyric list.
            listState.scrollToItem(currentLine)
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        if (showCloseButton) {
            IconButton(
                onClick = onClose,
                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).size(48.dp)
            ) {
                Icon(Icons.Default.Close, "关闭歌词",
                    tint = textPrimary.copy(alpha = 0.72f), modifier = Modifier.size(22.dp))
            }
        }

        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(vertical = 200.dp, horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            itemsIndexed(lyrics) { index, line ->
                val dist = kotlin.math.abs(index - currentLine)
                val isCurrent = dist == 0

                val targetAlpha = when {
                    isCurrent -> 1f
                    dist <= 2 -> 0.62f
                    dist <= 4 -> 0.38f
                    else -> 0.18f
                }
                val displayAlpha by animateFloatAsState(
                    targetValue = targetAlpha,
                    animationSpec = tween(220, easing = FastOutSlowInEasing),
                    label = "lyricAlpha"
                )
                val interactionSource = remember { MutableInteractionSource() }

                Text(
                    text = line.text,
                    color = if (isCurrent) textPrimary else textSecondary,
                    fontSize = 20.sp,
                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .alpha(displayAlpha)
                        .clickable(
                            interactionSource = interactionSource,
                            indication = null
                        ) { onTapLine(line.timeMs) },
                    maxLines = 2,
                    softWrap = true
                )
            }
        }
    }
}
