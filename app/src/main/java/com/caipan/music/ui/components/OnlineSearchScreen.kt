package com.caipan.music.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusManager
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.painterResource
import com.caipan.music.R
import coil.compose.AsyncImage
import com.caipan.music.online.OnlineCatalog
import com.caipan.music.online.OnlineSearchResult
import com.caipan.music.online.OnlineTrack
import kotlinx.coroutines.delay
import com.caipan.music.plugin.BlurLocation
import com.kyant.backdrop.Backdrop

@Composable
fun OnlineSearchScreen(
    catalogs: List<OnlineCatalog>,
    search: suspend (String, String) -> Result<OnlineSearchResult>,
    onPlay: (OnlineTrack, List<OnlineTrack>) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    accentColor: Color = MaterialTheme.colorScheme.primary,
    backdrop: Backdrop? = null,
    isLightTheme: Boolean = false,
    miniPlayerVisible: Boolean = false
) {
    BackHandler(onBack = onBack)

    var query by rememberSaveable { mutableStateOf("") }
    var retryToken by rememberSaveable { mutableIntStateOf(0) }
    var selectedSourceId by rememberSaveable {
        mutableStateOf(catalogs.firstOrNull()?.sourceId ?: "wy")
    }
    var contentState by remember { mutableStateOf<OnlineSearchState>(OnlineSearchState.Initial) }
    val focusManager = LocalFocusManager.current
    val inputBlocker = remember { androidx.compose.foundation.interaction.MutableInteractionSource() }

    LaunchedEffect(query, retryToken, selectedSourceId) {
        val normalizedQuery = query.trim()
        if (normalizedQuery.isEmpty()) {
            contentState = OnlineSearchState.Initial
            return@LaunchedEffect
        }

        delay(SEARCH_DEBOUNCE_MS)
        contentState = OnlineSearchState.Loading
        contentState = search(normalizedQuery, selectedSourceId).fold(
            onSuccess = { result ->
                if (result.tracks.isEmpty()) OnlineSearchState.Empty(normalizedQuery)
                else OnlineSearchState.Results(result.tracks, result.sourceLabel)
            },
            onFailure = { error ->
                OnlineSearchState.Error(
                    message = error.message?.takeIf(String::isNotBlank) ?: "搜索失败，请稍后重试"
                )
            }
        )
    }

    val glassTint = MaterialTheme.colorScheme.background.copy(alpha = if (isLightTheme) 0.58f else 0.52f)
    Column(
        modifier = modifier
            .fillMaxSize()
            .museGlass(
                backdrop = backdrop,
                shape = RoundedCornerShape(0.dp),
                tint = glassTint,
                blurRadius = 24.dp,
                location = BlurLocation.FULL_SCREEN,
                readabilityBoost = true,
                cornerRadius = 0.dp
            )
            .clickable(interactionSource = inputBlocker, indication = null) {}
            .statusBarsPadding()
            .imePadding()
    ) {
        SearchHeader(
            query = query,
            onQueryChange = { query = it },
            onBack = onBack,
            onSubmit = { focusManager.clearFocus() },
            accentColor = accentColor,
            focusManager = focusManager
        )

        SourceTabs(
            catalogs = catalogs,
            selectedSourceId = selectedSourceId,
            onSelect = { selectedSourceId = it },
            accentColor = accentColor
        )

        AnimatedContent(
            targetState = contentState,
            transitionSpec = { fadeIn() togetherWith fadeOut() },
            label = "searchState",
            modifier = Modifier.fillMaxSize()
        ) { state ->
        when (state) {
            OnlineSearchState.Initial -> SearchMessage(
                icon = painterResource(R.drawable.ic_apple_search),
                title = "搜索在线音乐",
                detail = "输入歌曲、艺术家或专辑名称"
            )

            OnlineSearchState.Loading -> Box(
                modifier = Modifier.fillMaxSize(),
                contentAlignment = Alignment.TopCenter
            ) {
                // 骨架屏替代转圈:搜索加载感知更快
                SkeletonSongRows(count = 6)
            }

            is OnlineSearchState.Error -> SearchMessage(
                icon = painterResource(R.drawable.ic_apple_cloud_off),
                title = "暂时无法搜索",
                detail = state.message,
                action = {
                    Button(
                        onClick = { retryToken++ },
                        colors = ButtonDefaults.buttonColors(containerColor = accentColor),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Icon(painterResource(R.drawable.ic_apple_refresh), contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("重试")
                    }
                }
            )

            is OnlineSearchState.Empty -> SearchMessage(
                icon = painterResource(R.drawable.ic_apple_music),
                title = "没有找到结果",
                detail = "没有与“${state.query}”匹配的歌曲"
            )

            is OnlineSearchState.Results -> TrackList(
                tracks = state.tracks,
                sourceLabel = state.sourceLabel,
                accentColor = accentColor,
                miniPlayerVisible = miniPlayerVisible,
                onPlay = { track -> onPlay(track, state.tracks) }
            )
        }
        }
    }
}

@Composable
private fun SearchHeader(
    query: String,
    onQueryChange: (String) -> Unit,
    onBack: () -> Unit,
    onSubmit: () -> Unit,
    accentColor: Color,
    focusManager: FocusManager
) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 8.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) {
            Icon(painterResource(R.drawable.ic_apple_arrow_left), contentDescription = "返回")
        }
        OutlinedTextField(
            value = query,
            onValueChange = { onQueryChange(it.take(MAX_QUERY_LENGTH)) },
            modifier = Modifier.weight(1f),
            placeholder = { Text("搜索歌曲、艺术家或专辑") },
            leadingIcon = { Icon(painterResource(R.drawable.ic_apple_search), contentDescription = null) },
            trailingIcon = {
                if (query.isNotEmpty()) {
                    IconButton(onClick = {
                        onQueryChange("")
                        focusManager.clearFocus()
                    }) {
                        Icon(painterResource(R.drawable.ic_apple_x), contentDescription = "清除")
                    }
                }
            },
            singleLine = true,
            shape = RoundedCornerShape(16.dp),
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
            keyboardActions = KeyboardActions(onSearch = { onSubmit() }),
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = accentColor,
                cursorColor = accentColor,
                focusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                unfocusedBorderColor = Color.Transparent
            )
        )
    }
}

@Composable
private fun SourceTabs(
    catalogs: List<OnlineCatalog>,
    selectedSourceId: String,
    onSelect: (String) -> Unit,
    accentColor: Color
) {
    if (catalogs.size <= 1) return
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 16.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        catalogs.forEach { catalog ->
            val selected = catalog.sourceId == selectedSourceId
            val bg = if (selected) accentColor else MaterialTheme.colorScheme.surfaceContainerHigh
            val fg = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(20.dp))
                    .background(bg)
                    .clickable { onSelect(catalog.sourceId) }
                    .padding(horizontal = 16.dp, vertical = 8.dp)
            ) {
                Text(
                    text = catalog.displayName,
                    color = fg,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
private fun TrackList(
    tracks: List<OnlineTrack>,
    sourceLabel: String,
    accentColor: Color,
    miniPlayerVisible: Boolean,
    onPlay: (OnlineTrack) -> Unit
) {
    val animatedIds = remember { mutableSetOf<String>() }
    LazyColumn(
        modifier = Modifier.fillMaxSize().navigationBarsPadding(),
        contentPadding = PaddingValues(bottom = if (miniPlayerVisible) 124.dp else 24.dp)
    ) {
        // 记录已播放过入场动画的项，避免滚动回收后重复播放
        item(key = "search-source") {
            Row(
                modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "搜索来源",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.labelMedium
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = sourceLabel,
                    color = accentColor,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        items(tracks.size, key = { tracks[it].stableId }) { index ->
            val track = tracks[index]
            // 交错入场动画：仅首次出现时播放
            val enterProgress = remember(track.stableId) { Animatable(if (track.stableId in animatedIds) 1f else 0f) }
            LaunchedEffect(track.stableId) {
                if (track.stableId !in animatedIds) {
                    animatedIds.add(track.stableId)
                    delay((index * 30L).coerceAtMost(360L))
                    enterProgress.animateTo(1f, spring(stiffness = Spring.StiffnessMediumLow, dampingRatio = Spring.DampingRatioNoBouncy))
                }
            }
            Column(Modifier.graphicsLayer {
                alpha = enterProgress.value
                translationY = (1f - enterProgress.value) * 24.dp.toPx()
            }) {
                OnlineTrackRow(track = track, accentColor = accentColor, onPlay = onPlay)
                HorizontalDivider(
                    modifier = Modifier.padding(start = 88.dp, end = 16.dp),
                    color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f)
                )
            }
        }
    }
}

@Composable
private fun OnlineTrackRow(
    track: OnlineTrack,
    accentColor: Color,
    onPlay: (OnlineTrack) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .pressScale()
            .clickable { onPlay(track) }
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(56.dp)
                .clip(RoundedCornerShape(8.dp))
                .background(MaterialTheme.colorScheme.surfaceContainerHigh),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                painterResource(R.drawable.ic_apple_music),
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.45f)
            )
            AsyncImage(
                model = coil.request.ImageRequest.Builder(androidx.compose.ui.platform.LocalContext.current)
                    .data(track.artworkUrl).size(112).crossfade(true).build(),
                contentDescription = "${track.album.ifBlank { track.title }}封面",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }

        Column(
            modifier = Modifier.weight(1f).padding(horizontal = 14.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                text = track.title,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = buildString {
                    append(track.artist)
                    if (track.album.isNotBlank()) append(" · ${track.album}")
                },
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        if (track.durationMs > 0L) {
            Text(
                text = track.formattedDuration,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium
            )
        }
        IconButton(onClick = { onPlay(track) }) {
            Icon(painterResource(R.drawable.ic_apple_play_arrow), contentDescription = "播放 ${track.title}", tint = accentColor)
        }
    }
}

@Composable
private fun SearchMessage(
    icon: androidx.compose.ui.graphics.painter.Painter,
    title: String,
    detail: String,
    action: (@Composable () -> Unit)? = null
) {
    Box(modifier = Modifier.fillMaxSize().navigationBarsPadding(), contentAlignment = Alignment.Center) {
        Column(
            modifier = Modifier.padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                painter = icon,
                contentDescription = null,
                modifier = Modifier.size(36.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f)
            )
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
            Text(
                text = detail,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium
            )
            if (action != null) {
                Spacer(Modifier.height(4.dp))
                action()
            }
        }
    }
}

private sealed interface OnlineSearchState {
    data object Initial : OnlineSearchState
    data object Loading : OnlineSearchState
    data class Empty(val query: String) : OnlineSearchState
    data class Error(val message: String) : OnlineSearchState
    data class Results(val tracks: List<OnlineTrack>, val sourceLabel: String) : OnlineSearchState
}

private const val SEARCH_DEBOUNCE_MS = 450L
private const val MAX_QUERY_LENGTH = 100
