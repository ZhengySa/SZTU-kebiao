package com.classflow.timetable.ui.screen.nbw

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.WifiOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.classflow.timetable.domain.model.NbwCategory
import com.classflow.timetable.domain.model.NbwNotice
import com.classflow.timetable.ui.theme.GlassCard
import com.classflow.timetable.ui.theme.GlassPalette
import com.classflow.timetable.ui.theme.GlassScreenHeader
import com.classflow.timetable.ui.theme.LocalGlassPalette
import kotlinx.coroutines.flow.drop

/**
 * 公文通（学校通知公告）。
 *
 * 为什么是原生列表而不是套一个 WebView：
 *  · 站点是服务端渲染的静态 HTML（VSB 站群），解析很稳，不用处理 JS；
 *  · 手机浏览器打开 PC 版列表，一屏只能看三四条，部门名还会被截断；
 *  · 只在校内网可达，原生页能做缓存 —— WebView 断网就是一页错误。
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NbwScreen(
    onOpenDetail: (String) -> Unit,
    onBack: (() -> Unit)? = null,
    vm: NbwViewModel = hiltViewModel(),
) {
    val palette = LocalGlassPalette.current
    val state by vm.state.collectAsStateWithLifecycle()
    val listState = rememberLazyListState()

    LaunchedEffect(Unit) { vm.start() }

    // 滑到倒数第三行就预取下一页，不用等真的滑到底
    LaunchedEffect(listState) {
        snapshotFlow {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            last to listState.layoutInfo.totalItemsCount
        }
            .drop(1)
            .collect { (last, total) ->
                if (total > 0 && last >= total - 3) vm.loadMore()
            }
    }

    // 切换栏目后回到顶部，否则会停在上一个栏目的滚动位置
    LaunchedEffect(state.category) {
        if (listState.firstVisibleItemIndex > 0) listState.scrollToItem(0)
    }

    Column(Modifier.fillMaxSize()) {
        GlassScreenHeader(
            title = "公文通",
            subtitle = buildString {
                append(
                    when {
                        state.fromCache -> "离线缓存"
                        state.loading -> "正在获取…"
                        state.totalCount > 0 -> "共 ${state.totalCount} 条"
                        else -> "深技大通知公告"
                    },
                )
            },
            onBack = onBack,
            actions = {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(50))
                        .background(palette.glassFillTop)
                        .clickable { vm.refresh() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "刷新",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(19.dp),
                    )
                }
            },
        )

        CategoryRow(
            selected = state.category,
            palette = palette,
            onSelect = vm::select,
        )

        state.notice?.let { msg ->
            NoticeBanner(msg, palette) { vm.dismissNotice() }
        }

        Box(Modifier.weight(1f)) {
            when {
                state.error != null -> ErrorPane(
                    message = state.error!!,
                    palette = palette,
                    onRetry = { vm.refresh() },
                )

                state.loading && state.notices.isEmpty() -> Box(
                    Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    CircularProgressIndicator(
                        color = palette.accent,
                        strokeWidth = 2.5.dp,
                        modifier = Modifier.size(28.dp),
                    )
                }

                state.notices.isEmpty() -> EmptyPane(palette)

                else -> PullToRefreshBox(
                    isRefreshing = state.refreshing,
                    onRefresh = { vm.refresh() },
                    modifier = Modifier.fillMaxSize(),
                ) {
                    LazyColumn(
                        state = listState,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(
                            start = 14.dp, end = 14.dp, top = 4.dp, bottom = 24.dp,
                        ),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        itemsIndexed(state.notices) { index, notice ->
                            NoticeCard(
                                notice = notice,
                                palette = palette,
                                // 同一天里同一部门会连着发好几条，日期只在换天时显示
                                showDate = index == 0 || state.notices[index - 1].date != notice.date,
                                onClick = { onOpenDetail(notice.path) },
                            )
                        }
                        if (state.loadingMore) {
                            item {
                                Box(
                                    Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 14.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    CircularProgressIndicator(
                                        color = palette.accent,
                                        strokeWidth = 2.dp,
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        } else if (!state.hasMore && state.notices.size > 20) {
                            item {
                                Text(
                                    text = "— 已经到底了 —",
                                    fontSize = 11.sp,
                                    color = palette.textTertiary,
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(vertical = 14.dp),
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CategoryRow(
    selected: NbwCategory,
    palette: GlassPalette,
    onSelect: (NbwCategory) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 14.dp)
            .padding(bottom = 10.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        NbwCategory.entries.forEach { c ->
            val on = c == selected
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (on) palette.accent else palette.glassFillTop)
                    .clickable { onSelect(c) }
                    .padding(horizontal = 13.dp, vertical = 6.dp),
            ) {
                Text(
                    text = c.label,
                    fontSize = 12.sp,
                    fontWeight = if (on) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (on) Color.White else palette.textSecondary,
                )
            }
        }
    }
}

@Composable
private fun NoticeCard(
    notice: NbwNotice,
    palette: GlassPalette,
    showDate: Boolean,
    onClick: () -> Unit,
) {
    GlassCard(
        modifier = Modifier.fillMaxWidth(),
        cornerRadius = 18.dp,
        elevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    if (notice.category.isNotBlank()) {
                        Tag(notice.category, palette.accent, palette)
                    }
                    if (showDate && notice.date.isNotBlank()) {
                        Text(notice.date, fontSize = 10.sp, color = palette.textTertiary)
                    }
                    if (notice.hasAttachment) {
                        Icon(
                            Icons.Filled.AttachFile,
                            contentDescription = "有附件",
                            tint = palette.textTertiary,
                            modifier = Modifier.size(12.dp),
                        )
                    }
                }
                Spacer(Modifier.height(5.dp))
                Text(
                    text = notice.title,
                    fontSize = 14.sp,
                    lineHeight = 20.sp,
                    fontWeight = FontWeight.Medium,
                    color = palette.textPrimary,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (notice.department.isNotBlank()) {
                    Spacer(Modifier.height(5.dp))
                    Text(
                        text = notice.department,
                        fontSize = 11.sp,
                        color = palette.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            }
            Spacer(Modifier.width(6.dp))
            Icon(
                Icons.AutoMirrored.Filled.KeyboardArrowRight,
                contentDescription = null,
                tint = palette.textTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

@Composable
private fun Tag(text: String, color: Color, palette: GlassPalette) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(color.copy(alpha = 0.12f))
            .padding(horizontal = 6.dp, vertical = 1.dp),
    ) {
        Text(text, fontSize = 9.sp, color = color, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun NoticeBanner(message: String, palette: GlassPalette, onDismiss: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 14.dp)
            .padding(bottom = 8.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(palette.accentSoft)
            .clickable(onClick = onDismiss)
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = message,
            fontSize = 11.sp,
            color = palette.textSecondary,
            modifier = Modifier.weight(1f),
        )
        Text("知道了", fontSize = 11.sp, color = palette.accent, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun ErrorPane(message: String, palette: GlassPalette, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Icon(
            Icons.Filled.WifiOff,
            contentDescription = null,
            tint = palette.textTertiary,
            modifier = Modifier.size(34.dp),
        )
        Spacer(Modifier.height(12.dp))
        Text(
            text = message,
            fontSize = 13.sp,
            color = palette.textSecondary,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            lineHeight = 20.sp,
        )
        Spacer(Modifier.height(14.dp))
        Box(
            modifier = Modifier
                .clip(RoundedCornerShape(50))
                .background(palette.accent)
                .clickable(onClick = onRetry)
                .padding(horizontal = 20.dp, vertical = 8.dp),
        ) {
            Text("重试", fontSize = 12.sp, color = Color.White, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun EmptyPane(palette: GlassPalette) {
    Box(
        Modifier
            .fillMaxSize()
            .padding(40.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text("这里还没有公告", fontSize = 13.sp, color = palette.textTertiary)
    }
}
