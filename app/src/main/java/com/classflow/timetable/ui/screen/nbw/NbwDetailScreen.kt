package com.classflow.timetable.ui.screen.nbw

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.CloudOff
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.classflow.timetable.domain.model.NbwAttachment
import com.classflow.timetable.ui.theme.GlassCard
import com.classflow.timetable.ui.theme.GlassPalette
import com.classflow.timetable.ui.theme.GlassScreenHeader
import com.classflow.timetable.ui.theme.LocalGlassPalette

/**
 * 公文通详情。
 *
 * 附件用系统浏览器打开，不在 App 内做 PDF/Office 预览：
 * 附件是校方文件，用户手里有 WPS / 系统 PDF 阅读器，套个内置查看器反而更差
 * （字体缺失、排版错乱、还得再维护一套渲染）。
 */
@Composable
fun NbwDetailScreen(
    path: String,
    onOpenDetail: (String) -> Unit,
    onBack: () -> Unit,
    vm: NbwDetailViewModel = hiltViewModel(),
) {
    val palette = LocalGlassPalette.current
    val state by vm.state.collectAsStateWithLifecycle()
    val context = LocalContext.current

    LaunchedEffect(path) { vm.load(path) }

    val detail = state.detail

    Column(Modifier.fillMaxSize()) {
        GlassScreenHeader(
            title = "公文详情",
            subtitle = when {
                state.fromCache -> "离线缓存"
                detail?.publishedAt?.isNotBlank() == true -> detail.publishedAt
                else -> null
            },
            onBack = onBack,
            actions = {
                Box(
                    modifier = Modifier
                        .size(40.dp)
                        .clip(RoundedCornerShape(50))
                        .background(palette.glassFillTop)
                        .clickable {
                            runCatching {
                                context.startActivity(
                                    Intent(
                                        Intent.ACTION_VIEW,
                                        Uri.parse("https://nbw.sztu.edu.cn/$path"),
                                    ),
                                )
                            }
                        },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.OpenInBrowser,
                        contentDescription = "在浏览器打开",
                        tint = palette.textSecondary,
                        modifier = Modifier.size(18.dp),
                    )
                }
            },
        )

        when {
            state.error != null -> Box(
                Modifier
                    .fillMaxSize()
                    .padding(40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(
                        Icons.Filled.CloudOff,
                        contentDescription = null,
                        tint = palette.textTertiary,
                        modifier = Modifier.size(34.dp),
                    )
                    Spacer(Modifier.height(12.dp))
                    Text(
                        text = state.error!!,
                        fontSize = 13.sp,
                        color = palette.textSecondary,
                        textAlign = TextAlign.Center,
                        lineHeight = 20.sp,
                    )
                }
            }

            detail == null -> Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                CircularProgressIndicator(
                    color = palette.accent,
                    strokeWidth = 2.5.dp,
                    modifier = Modifier.size(28.dp),
                )
            }

            else -> LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(start = 14.dp, end = 14.dp, top = 4.dp, bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 20.dp,
                        elevation = 8.dp,
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            Text(
                                text = detail.title,
                                fontSize = 17.sp,
                                lineHeight = 24.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.textPrimary,
                            )
                            Spacer(Modifier.height(8.dp))
                            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                if (detail.author.isNotBlank()) {
                                    Text(
                                        detail.author,
                                        fontSize = 11.sp,
                                        color = palette.textTertiary,
                                    )
                                }
                                if (detail.publishedAt.isNotBlank()) {
                                    Text(
                                        detail.publishedAt,
                                        fontSize = 11.sp,
                                        color = palette.textTertiary,
                                    )
                                }
                            }
                        }
                    }
                }

                item {
                    GlassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 20.dp,
                        elevation = 6.dp,
                    ) {
                        Column(Modifier.padding(16.dp)) {
                            detail.paragraphs.forEachIndexed { i, para ->
                                if (i > 0) Spacer(Modifier.height(9.dp))
                                Text(
                                    text = para,
                                    fontSize = 14.sp,
                                    // 1.75 倍行距：正文段落长，行近了看着发晕
                                    lineHeight = 25.sp,
                                    color = palette.textPrimary,
                                )
                            }
                        }
                    }
                }

                if (detail.attachments.isNotEmpty()) {
                    item {
                        Text(
                            "附件 ${detail.attachments.size}",
                            fontSize = 11.sp,
                            color = palette.textTertiary,
                            modifier = Modifier.padding(start = 4.dp, top = 2.dp),
                        )
                    }
                    items(detail.attachments) { att ->
                        AttachmentRow(
                            attachment = att,
                            palette = palette,
                            onClick = {
                                runCatching {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(att.url)))
                                }
                            },
                        )
                    }
                }

                if (detail.prev != null || detail.next != null) {
                    item {
                        Column(Modifier.padding(top = 4.dp)) {
                            detail.prev?.let { p ->
                                SiblingRow("上一篇", p.title, palette) {
                                    onOpenDetail(com.classflow.timetable.data.remote.sztu.NbwParser.resolveSibling(path, p.path))
                                }
                            }
                            detail.next?.let { n ->
                                SiblingRow("下一篇", n.title, palette) {
                                    onOpenDetail(com.classflow.timetable.data.remote.sztu.NbwParser.resolveSibling(path, n.path))
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentRow(
    attachment: NbwAttachment,
    palette: GlassPalette,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(palette.glassFillTop)
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Filled.AttachFile,
            contentDescription = null,
            tint = palette.accent,
            modifier = Modifier.size(16.dp),
        )
        Spacer(Modifier.size(8.dp))
        Text(
            text = attachment.name,
            fontSize = 12.sp,
            color = palette.accent,
            modifier = Modifier.weight(1f),
            maxLines = 2,
        )
        Text("打开", fontSize = 11.sp, color = palette.textTertiary)
    }
}

@Composable
private fun SiblingRow(label: String, title: String, palette: GlassPalette, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 9.dp),
    ) {
        Text("$label · ", fontSize = 11.sp, color = palette.textTertiary)
        Text(
            text = title,
            fontSize = 11.sp,
            color = palette.textSecondary,
            maxLines = 1,
            modifier = Modifier.weight(1f),
        )
    }
}
