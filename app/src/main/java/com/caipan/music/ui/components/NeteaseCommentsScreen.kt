package com.caipan.music.ui.components

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Favorite
import androidx.compose.material.icons.filled.FavoriteBorder
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Reply
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.caipan.music.plugin.BlurLocation
import com.kyant.backdrop.Backdrop

/**
 * A UI-only comment model.  Keep the online-service DTO mapping outside the composable so this
 * screen can also render comments from a local service, a plug-in, or a preview.
 */
data class CommentUiItem(
    val id: String,
    val authorName: String,
    val content: String,
    val authorId: String? = null,
    val avatarUrl: String? = null,
    /** A preformatted timestamp, e.g. `昨天 21:35` / `Yesterday 21:35`. */
    val createdAt: String = "",
    val likedCount: Int = 0,
    val likedByCurrentUser: Boolean = false,
    val replyCount: Int = 0,
    /** The display name of the person this comment replies to, if any. */
    val replyToName: String? = null,
    val ipLocation: String? = null,
    val isPinned: Boolean = false
)

/**
 * Presentation state for [NeteaseCommentsScreen]. It deliberately has no NetEase dependency.
 */
data class CommentUiState(
    val hotComments: List<CommentUiItem> = emptyList(),
    val latestComments: List<CommentUiItem> = emptyList(),
    /** `null` means the server did not expose a count yet. */
    val totalCount: Int? = null,
    val isInitialLoading: Boolean = false,
    val isRefreshing: Boolean = false,
    val isLoadingMore: Boolean = false,
    val hasMore: Boolean = false,
    /** A user-safe error message supplied by the caller. */
    val errorMessage: String? = null
)

/**
 * Event boundary for [NeteaseCommentsScreen].  Network calls, login checks and optimistic state
 * updates belong to the caller; this component only emits intent.
 */
data class CommentUiCallbacks(
    val onDismiss: () -> Unit,
    val onRefresh: () -> Unit = {},
    val onLoadMore: () -> Unit = {},
    val onToggleLike: ((CommentUiItem) -> Unit)? = null,
    val onReply: ((CommentUiItem) -> Unit)? = null,
    val onSubmitComment: ((content: String, replyTo: CommentUiItem?) -> Unit)? = null
)

/** Whether comments occupy the whole route or float up from the lower edge of the player. */
enum class CommentsPresentation { BottomSheet, FullScreen }

private enum class CommentFeed { Hot, Latest }

/**
 * Reusable song-comment route with a translucent, player-originating bottom-sheet treatment.
 *
 * The host decides when to show it. It is intentionally separate from player gestures and uses a
 * visible back button plus Android back handling so dismissal is always unambiguous.
 */
@Composable
fun NeteaseCommentsScreen(
    state: CommentUiState,
    callbacks: CommentUiCallbacks,
    songTitle: String? = null,
    songSubtitle: String? = null,
    isChinese: Boolean = true,
    isLightTheme: Boolean = false,
    /** `Color.Unspecified` follows the active Muse theme. */
    accentColor: Color = Color.Unspecified,
    presentation: CommentsPresentation = CommentsPresentation.BottomSheet,
    backdrop: Backdrop? = null,
    modifier: Modifier = Modifier,
    /** The composer is only rendered when [CommentUiCallbacks.onSubmitComment] is supplied. */
    showComposer: Boolean = true
) {
    val effectiveBackdrop = backdrop ?: LocalMuseBackdrop.current
    val effectiveAccent = if (accentColor == Color.Unspecified) MaterialTheme.colorScheme.primary else accentColor
    val sheetShape = when (presentation) {
        CommentsPresentation.BottomSheet -> RoundedCornerShape(topStart = 30.dp, topEnd = 30.dp)
        CommentsPresentation.FullScreen -> RoundedCornerShape(0.dp)
    }
    val sheetHeight = if (presentation == CommentsPresentation.BottomSheet) 0.93f else 1f
    val textColor = MaterialTheme.colorScheme.onSurface
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    val tint = (if (isLightTheme) MaterialTheme.colorScheme.background else MaterialTheme.colorScheme.surface).copy(
        alpha = if (LocalMuseLiquidGlass.current) 0.18f else 0.96f
    )
    val composerEnabled = showComposer && callbacks.onSubmitComment != null
    val dismiss by rememberUpdatedState(callbacks.onDismiss)
    var replyTarget by remember { mutableStateOf<CommentUiItem?>(null) }
    // Keep this composable alive through the exit transition. The host callback is deliberately
    // deferred until the sheet has settled below the player, so the route is never removed mid-frame.
    val sheetVisibility = remember {
        MutableTransitionState(false).apply { targetState = true }
    }
    var dismissRequested by remember { mutableStateOf(false) }
    var dismissDelivered by remember { mutableStateOf(false) }
    val requestDismiss = {
        if (!dismissRequested) {
            dismissRequested = true
            sheetVisibility.targetState = false
        }
    }
    val targetScrimAlpha = when (presentation) {
        CommentsPresentation.BottomSheet -> if (isLightTheme) 0.16f else 0.28f
        CommentsPresentation.FullScreen -> 0f
    }
    val scrimAlpha by animateFloatAsState(
        targetValue = if (sheetVisibility.targetState) targetScrimAlpha else 0f,
        animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
        label = "commentsScrim"
    )

    LaunchedEffect(songTitle, songSubtitle) { replyTarget = null }

    LaunchedEffect(dismissRequested, sheetVisibility.currentState, sheetVisibility.targetState) {
        if (
            dismissRequested && !dismissDelivered &&
            !sheetVisibility.currentState && !sheetVisibility.targetState
        ) {
            dismissDelivered = true
            dismiss()
        }
    }

    BackHandler(
        enabled = sheetVisibility.currentState || sheetVisibility.targetState,
        onBack = requestDismiss
    )

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(
                MaterialTheme.colorScheme.scrim.copy(alpha = scrimAlpha)
            )
    ) {
        AnimatedVisibility(
            visibleState = sheetVisibility,
            enter = slideInVertically(
                initialOffsetY = { fullHeight -> fullHeight },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeIn(
                animationSpec = tween(durationMillis = 140, easing = FastOutSlowInEasing)
            ),
            exit = slideOutVertically(
                targetOffsetY = { fullHeight -> fullHeight },
                animationSpec = spring(
                    dampingRatio = Spring.DampingRatioNoBouncy,
                    stiffness = Spring.StiffnessMediumLow
                )
            ) + fadeOut(
                animationSpec = tween(durationMillis = 120, easing = FastOutSlowInEasing)
            ),
            modifier = Modifier.fillMaxSize(),
            label = "commentsSheetVisibility"
        ) {
            Box(Modifier.fillMaxSize()) {
                Column(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .fillMaxHeight(sheetHeight)
                        .clip(sheetShape)
                        .museGlass(
                            backdrop = effectiveBackdrop,
                            shape = sheetShape,
                            tint = tint,
                            blurRadius = 28.dp,
                            borderColor = Color.White.copy(alpha = if (LocalMuseLiquidGlass.current) 0.23f else 0.08f),
                            location = if (presentation == CommentsPresentation.BottomSheet) BlurLocation.SHEETS else BlurLocation.FULL_SCREEN,
                            readabilityBoost = true,
                            cornerRadius = if (presentation == CommentsPresentation.BottomSheet) 30.dp else 0.dp
                        )
                        .then(if (presentation == CommentsPresentation.FullScreen) Modifier.statusBarsPadding() else Modifier)
                        .imePadding()
                ) {
            if (presentation == CommentsPresentation.BottomSheet) {
                Box(
                    Modifier
                        .padding(top = 10.dp, bottom = 3.dp)
                        .width(36.dp)
                        .height(4.dp)
                        .clip(CircleShape)
                        .background(textColor.copy(alpha = 0.24f))
                        .align(Alignment.CenterHorizontally)
                )
            }

            CommentsTopBar(
                songTitle = songTitle,
                songSubtitle = songSubtitle,
                totalCount = state.totalCount,
                isRefreshing = state.isRefreshing,
                isChinese = isChinese,
                textColor = textColor,
                mutedColor = mutedColor,
                accentColor = effectiveAccent,
                onDismiss = requestDismiss,
                onRefresh = callbacks.onRefresh
            )

            val latestAvailable = state.latestComments.isNotEmpty()
            val hotAvailable = state.hotComments.isNotEmpty()
            var selectedFeed by rememberSaveable(hotAvailable, latestAvailable) {
                mutableStateOf(if (hotAvailable) CommentFeed.Hot else CommentFeed.Latest)
            }
            // If a refresh removes the current feed, do not leave the user on an empty tab while
            // the other feed already contains comments.
            LaunchedEffect(hotAvailable, latestAvailable, selectedFeed) {
                if (selectedFeed == CommentFeed.Hot && !hotAvailable && latestAvailable) {
                    selectedFeed = CommentFeed.Latest
                }
            }

            CommentFeedTabs(
                selected = selectedFeed,
                hotCount = state.hotComments.size,
                latestCount = state.latestComments.size,
                isChinese = isChinese,
                textColor = textColor,
                mutedColor = mutedColor,
                accentColor = effectiveAccent,
                onSelect = { selectedFeed = it }
            )

            val listState = rememberLazyListState()
            val comments = if (selectedFeed == CommentFeed.Hot) state.hotComments else state.latestComments
            CommentFeedContent(
                state = state,
                feed = selectedFeed,
                comments = comments,
                listState = listState,
                isChinese = isChinese,
                textColor = textColor,
                mutedColor = mutedColor,
                accentColor = effectiveAccent,
                contentPadding = PaddingValues(
                    start = 20.dp,
                    end = 20.dp,
                    top = 2.dp,
                    bottom = if (composerEnabled) 12.dp else 28.dp
                ),
                onRefresh = callbacks.onRefresh,
                onLoadMore = callbacks.onLoadMore,
                onToggleLike = callbacks.onToggleLike,
                onReply = if (callbacks.onReply != null || composerEnabled) {
                    { item ->
                        replyTarget = item
                        callbacks.onReply?.invoke(item)
                    }
                } else null,
                modifier = Modifier.weight(1f)
            )

            if (composerEnabled) {
                CommentComposer(
                    isChinese = isChinese,
                    textColor = textColor,
                    mutedColor = mutedColor,
                    accentColor = effectiveAccent,
                    replyTarget = replyTarget,
                    onCancelReply = { replyTarget = null },
                    onSubmit = callbacks.onSubmitComment!!
                )
            } else {
                Spacer(Modifier.navigationBarsPadding())
            }
                }
            }
        }
    }
}

@Composable
private fun CommentsTopBar(
    songTitle: String?,
    songSubtitle: String?,
    totalCount: Int?,
    isRefreshing: Boolean,
    isChinese: Boolean,
    textColor: Color,
    mutedColor: Color,
    accentColor: Color,
    onDismiss: () -> Unit,
    onRefresh: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 8.dp, end = 12.dp, top = 1.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(
            onClick = onDismiss,
            colors = IconButtonDefaults.iconButtonColors(contentColor = textColor)
        ) {
            Icon(
                imageVector = Icons.Default.ArrowBack,
                contentDescription = if (isChinese) "返回" else "Back"
            )
        }
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = if (isChinese) "评论" else "Comments",
                color = textColor,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold
            )
            val context = listOfNotNull(songTitle, songSubtitle).joinToString(" · ")
            val countText = totalCount?.let { commentCountText(it, isChinese) }
            val detail = listOfNotNull(context.takeIf { it.isNotBlank() }, countText).joinToString(" · ")
            if (detail.isNotBlank()) {
                Text(
                    text = detail,
                    color = mutedColor,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
        Box(contentAlignment = Alignment.Center) {
            IconButton(
                onClick = onRefresh,
                enabled = !isRefreshing,
                colors = IconButtonDefaults.iconButtonColors(contentColor = textColor)
            ) {
                if (isRefreshing) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        strokeWidth = 2.dp,
                        color = accentColor
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = if (isChinese) "刷新评论" else "Refresh comments"
                    )
                }
            }
        }
    }
}

@Composable
private fun CommentFeedTabs(
    selected: CommentFeed,
    hotCount: Int,
    latestCount: Int,
    isChinese: Boolean,
    textColor: Color,
    mutedColor: Color,
    accentColor: Color,
    onSelect: (CommentFeed) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 3.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        CommentFeedTab(
            title = if (isChinese) "热门" else "Hot",
            count = hotCount,
            selected = selected == CommentFeed.Hot,
            activeColor = accentColor,
            textColor = textColor,
            mutedColor = mutedColor,
            isChinese = isChinese,
            onClick = { onSelect(CommentFeed.Hot) },
            modifier = Modifier.weight(1f)
        )
        CommentFeedTab(
            title = if (isChinese) "最新评论" else "Latest",
            count = latestCount,
            selected = selected == CommentFeed.Latest,
            activeColor = accentColor,
            textColor = textColor,
            mutedColor = mutedColor,
            isChinese = isChinese,
            onClick = { onSelect(CommentFeed.Latest) },
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun CommentFeedTab(
    title: String,
    count: Int,
    selected: Boolean,
    activeColor: Color,
    textColor: Color,
    mutedColor: Color,
    isChinese: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val shape = RoundedCornerShape(14.dp)
    Row(
        modifier = modifier
            .clip(shape)
            .background(if (selected) activeColor.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title,
            color = if (selected) activeColor else textColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Medium
        )
        if (count > 0) {
            Spacer(Modifier.width(5.dp))
            Text(
                text = compactCount(count, isChinese),
                color = if (selected) activeColor.copy(alpha = 0.82f) else mutedColor,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun CommentFeedContent(
    state: CommentUiState,
    feed: CommentFeed,
    comments: List<CommentUiItem>,
    listState: LazyListState,
    isChinese: Boolean,
    textColor: Color,
    mutedColor: Color,
    accentColor: Color,
    contentPadding: PaddingValues,
    onRefresh: () -> Unit,
    onLoadMore: () -> Unit,
    onToggleLike: ((CommentUiItem) -> Unit)?,
    onReply: ((CommentUiItem) -> Unit)?,
    modifier: Modifier = Modifier
) {
    // Do not auto-retry a failed page while the list is resting at its end. The footer remains
    // available for an intentional retry.
    val canLoadMore = feed == CommentFeed.Latest &&
        state.hasMore && !state.isLoadingMore && state.errorMessage == null
    val latestRequest = rememberUpdatedState(onLoadMore)
    val shouldRequestMore = remember(listState, canLoadMore) {
        androidx.compose.runtime.derivedStateOf {
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: -1
            val itemCount = listState.layoutInfo.totalItemsCount
            canLoadMore && itemCount > 0 && lastVisible >= itemCount - 3
        }
    }
    // The latest-comment count is part of the key: after the caller appends a page, this may
    // request the next page only if the viewport is still genuinely at the end.
    LaunchedEffect(comments.size, state.isLoadingMore, state.hasMore, shouldRequestMore.value) {
        if (shouldRequestMore.value && canLoadMore) latestRequest.value()
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        state = listState,
        contentPadding = contentPadding
    ) {
        item(key = "section-heading-$feed") {
            Text(
                text = when (feed) {
                    CommentFeed.Hot -> if (isChinese) "热门评论" else "Hot comments"
                    CommentFeed.Latest -> if (isChinese) "最新评论" else "Latest comments"
                },
                color = textColor,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.padding(top = 8.dp, bottom = 6.dp)
            )
        }

        when {
            state.isInitialLoading && comments.isEmpty() -> {
                item(key = "initial-loading") {
                    CommentCenteredState(
                        text = if (isChinese) "正在加载评论…" else "Loading comments…",
                        color = mutedColor,
                        accentColor = accentColor,
                        progress = true
                    )
                }
            }

            comments.isEmpty() && state.errorMessage != null -> {
                item(key = "initial-error") {
                    CommentErrorState(
                        message = state.errorMessage,
                        isChinese = isChinese,
                        accentColor = accentColor,
                        mutedColor = mutedColor,
                        onRetry = onRefresh
                    )
                }
            }

            comments.isEmpty() -> {
                item(key = "empty") {
                    CommentCenteredState(
                        text = if (feed == CommentFeed.Hot) {
                            if (isChinese) "暂时没有热门评论" else "No hot comments yet"
                        } else {
                            if (isChinese) "还没有评论，来说点什么吧" else "No comments yet — start the conversation"
                        },
                        color = mutedColor,
                        accentColor = accentColor
                    )
                }
            }

            else -> {
                items(comments, key = { it.id }) { comment ->
                    CommentRow(
                        comment = comment,
                        isChinese = isChinese,
                        textColor = textColor,
                        mutedColor = mutedColor,
                        accentColor = accentColor,
                        onToggleLike = onToggleLike,
                        onReply = onReply?.let { callback -> { callback(comment) } }
                    )
                }
                if (state.errorMessage != null) {
                    item(key = "inline-error") {
                        CommentErrorState(
                            message = state.errorMessage,
                            isChinese = isChinese,
                            accentColor = accentColor,
                            mutedColor = mutedColor,
                            onRetry = onRefresh
                        )
                    }
                }
                if (feed == CommentFeed.Latest) {
                    item(key = "pagination") {
                        CommentPaginationFooter(
                            hasMore = state.hasMore,
                            isLoadingMore = state.isLoadingMore,
                            isChinese = isChinese,
                            mutedColor = mutedColor,
                            accentColor = accentColor,
                            onLoadMore = onLoadMore
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CommentRow(
    comment: CommentUiItem,
    isChinese: Boolean,
    textColor: Color,
    mutedColor: Color,
    accentColor: Color,
    onToggleLike: ((CommentUiItem) -> Unit)?,
    onReply: (() -> Unit)?
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 11.dp),
        verticalAlignment = Alignment.Top
    ) {
        CommentAvatar(comment = comment)
        Spacer(Modifier.width(11.dp))
        Column(modifier = Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = comment.authorName.ifBlank { if (isChinese) "网易云用户" else "NetEase user" },
                    color = if (comment.isPinned) accentColor else textColor,
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f, fill = false)
                )
                if (comment.isPinned) {
                    Spacer(Modifier.width(6.dp))
                    Text(
                        text = if (isChinese) "置顶" else "Pinned",
                        color = accentColor,
                        style = MaterialTheme.typography.labelSmall,
                        modifier = Modifier
                            .clip(RoundedCornerShape(5.dp))
                            .background(accentColor.copy(alpha = 0.12f))
                            .padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
            val metadata = listOfNotNull(
                comment.createdAt.takeIf { it.isNotBlank() },
                comment.ipLocation?.takeIf { it.isNotBlank() }
            ).joinToString(" · ")
            if (metadata.isNotBlank()) {
                Text(
                    text = metadata,
                    color = mutedColor,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(top = 1.dp)
                )
            }
            if (!comment.replyToName.isNullOrBlank()) {
                Text(
                    text = if (isChinese) "回复 @${comment.replyToName}" else "Reply to @${comment.replyToName}",
                    color = accentColor.copy(alpha = 0.9f),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 6.dp)
                )
            }
            Text(
                text = comment.content,
                color = textColor,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(top = 5.dp)
            )
            Row(
                modifier = Modifier.padding(top = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                CommentAction(
                    icon = if (comment.likedByCurrentUser) Icons.Default.Favorite else Icons.Default.FavoriteBorder,
                    label = if (comment.likedCount > 0) compactCount(comment.likedCount, isChinese) else if (isChinese) "赞" else "Like",
                    active = comment.likedByCurrentUser,
                    accentColor = accentColor,
                    mutedColor = mutedColor,
                    onClick = onToggleLike?.let { callback -> { callback(comment) } }
                )
                if (onReply != null) {
                    Spacer(Modifier.width(12.dp))
                    CommentAction(
                        icon = Icons.Default.Reply,
                        label = when {
                            comment.replyCount > 0 && isChinese -> "回复 ${compactCount(comment.replyCount, true)}"
                            comment.replyCount > 0 -> "Reply ${compactCount(comment.replyCount, false)}"
                            isChinese -> "回复"
                            else -> "Reply"
                        },
                        active = false,
                        accentColor = accentColor,
                        mutedColor = mutedColor,
                        onClick = onReply
                    )
                }
            }
            HorizontalDivider(
                modifier = Modifier.padding(top = 11.dp),
                color = mutedColor.copy(alpha = 0.14f)
            )
        }
    }
}

@Composable
private fun CommentAvatar(comment: CommentUiItem) {
    Box(
        modifier = Modifier
            .size(39.dp)
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.surfaceVariant),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = comment.authorName.trim().take(1).ifBlank { "·" },
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
        comment.avatarUrl?.takeIf { it.isNotBlank() }?.let { avatarUrl ->
            AsyncImage(
                model = avatarUrl,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}

@Composable
private fun CommentAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    active: Boolean,
    accentColor: Color,
    mutedColor: Color,
    onClick: (() -> Unit)?
) {
    val color = if (active) accentColor else mutedColor
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(9.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(horizontal = 2.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = label,
            tint = color,
            modifier = Modifier.size(15.dp)
        )
        Spacer(Modifier.width(4.dp))
        Text(label, color = color, style = MaterialTheme.typography.labelSmall)
    }
}

@Composable
private fun CommentCenteredState(
    text: String,
    color: Color,
    accentColor: Color,
    progress: Boolean = false
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 52.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        if (progress) {
            CircularProgressIndicator(
                modifier = Modifier.size(25.dp),
                strokeWidth = 2.dp,
                color = accentColor
            )
            Spacer(Modifier.height(12.dp))
        }
        Text(text = text, color = color, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun CommentErrorState(
    message: String,
    isChinese: Boolean,
    accentColor: Color,
    mutedColor: Color,
    onRetry: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(MaterialTheme.colorScheme.error.copy(alpha = 0.09f))
            .padding(14.dp)
    ) {
        Text(
            text = if (isChinese) "评论加载失败" else "Couldn't load comments",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = message,
            color = mutedColor,
            style = MaterialTheme.typography.bodySmall,
            modifier = Modifier.padding(top = 3.dp)
        )
        Text(
            text = if (isChinese) "重试" else "Try again",
            color = accentColor,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier
                .padding(top = 10.dp)
                .clickable(onClick = onRetry)
        )
    }
}

@Composable
private fun CommentPaginationFooter(
    hasMore: Boolean,
    isLoadingMore: Boolean,
    isChinese: Boolean,
    mutedColor: Color,
    accentColor: Color,
    onLoadMore: () -> Unit
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 14.dp),
        contentAlignment = Alignment.Center
    ) {
        when {
            isLoadingMore -> CircularProgressIndicator(
                modifier = Modifier.size(22.dp),
                strokeWidth = 2.dp,
                color = accentColor
            )

            hasMore -> Text(
                text = if (isChinese) "加载更多评论" else "Load more comments",
                color = accentColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable(onClick = onLoadMore)
            )

            else -> Text(
                text = if (isChinese) "已经到底了" else "You're all caught up",
                color = mutedColor,
                style = MaterialTheme.typography.labelSmall
            )
        }
    }
}

@Composable
private fun CommentComposer(
    isChinese: Boolean,
    textColor: Color,
    mutedColor: Color,
    accentColor: Color,
    replyTarget: CommentUiItem?,
    onCancelReply: () -> Unit,
    onSubmit: (String, CommentUiItem?) -> Unit
) {
    var draft by rememberSaveable { mutableStateOf("") }
    val canSubmit = draft.trim().isNotEmpty()
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(start = 16.dp, end = 12.dp, top = 8.dp, bottom = 10.dp)
    ) {
        if (replyTarget != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 10.dp, bottom = 5.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isChinese) "回复 @${replyTarget.authorName}" else "Replying to @${replyTarget.authorName}",
                    color = accentColor,
                    style = MaterialTheme.typography.labelMedium,
                    modifier = Modifier.weight(1f),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                IconButton(
                    onClick = onCancelReply,
                    modifier = Modifier.size(30.dp),
                    colors = IconButtonDefaults.iconButtonColors(contentColor = mutedColor)
                ) {
                    Icon(
                        imageVector = Icons.Default.Close,
                        contentDescription = if (isChinese) "取消回复" else "Cancel reply",
                        modifier = Modifier.size(16.dp)
                    )
                }
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.weight(1f),
                placeholder = {
                    Text(
                        text = if (replyTarget == null) {
                            if (isChinese) "说点什么…" else "Add a comment…"
                        } else if (isChinese) {
                            "回复 @${replyTarget.authorName}…"
                        } else {
                            "Reply to @${replyTarget.authorName}…"
                        },
                        color = mutedColor
                    )
                },
                textStyle = MaterialTheme.typography.bodyMedium.copy(color = textColor),
                singleLine = true,
                shape = RoundedCornerShape(22.dp),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = accentColor.copy(alpha = 0.7f),
                    unfocusedBorderColor = mutedColor.copy(alpha = 0.24f),
                    focusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.36f),
                    unfocusedContainerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.22f),
                    cursorColor = accentColor
                )
            )
            Spacer(Modifier.width(7.dp))
            IconButton(
                enabled = canSubmit,
                onClick = {
                    val content = draft.trim()
                    if (content.isNotEmpty()) {
                        onSubmit(content, replyTarget)
                        draft = ""
                        onCancelReply()
                    }
                },
                colors = IconButtonDefaults.iconButtonColors(
                    contentColor = accentColor,
                    disabledContentColor = mutedColor.copy(alpha = 0.42f)
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Send,
                    contentDescription = if (isChinese) "发送评论" else "Send comment"
                )
            }
        }
    }
}

private fun commentCountText(count: Int, isChinese: Boolean): String =
    if (isChinese) "${compactCount(count, true)} 条评论" else "${compactCount(count, false)} comments"

private fun compactCount(value: Int, isChinese: Boolean): String = if (isChinese) {
    when {
        value >= 100_000_000 -> "${formatOneDecimal(value / 100_000_000f)}亿"
        value >= 10_000 -> "${formatOneDecimal(value / 10_000f)}万"
        else -> value.toString()
    }
} else {
    when {
        value >= 1_000_000 -> "${formatOneDecimal(value / 1_000_000f)}M"
        value >= 1_000 -> "${formatOneDecimal(value / 1_000f)}k"
        else -> value.toString()
    }
}

private fun formatOneDecimal(value: Float): String =
    if (value >= 10 || value % 1f == 0f) value.toInt().toString() else String.format(java.util.Locale.US, "%.1f", value)
