package com.classflow.timetable.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.classflow.timetable.domain.model.DayCourse
import com.classflow.timetable.ui.theme.GlassCard
import com.classflow.timetable.ui.theme.LocalGlassPalette

/**
 * 今日页 / 日课表的一张课程卡。
 * 必须一眼看到：课程名、第几节到第几节、起止时间、地点、教师、颜色条。
 *
 * 玻璃化改造：原来的实心 Surface 换成半透明玻璃面，
 * 左侧颜色条改成分段渐变 + 小圆点，避免"色块贴纸"的廉价感。
 */
@Composable
fun CourseCard(
    course: DayCourse,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalGlassPalette.current
    val accent = Color(course.colorArgb)

    GlassCard(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        cornerRadius = 20.dp,
        // 课表里一屏有七八张卡，阴影压到 6dp：观感差别很小，滚动却明显更跟手
        elevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .height(IntrinsicSize.Min)
                .padding(start = 13.dp, end = 16.dp, top = 14.dp, bottom = 14.dp),
        ) {
            Box(
                Modifier
                    .width(3.dp)
                    .fillMaxHeight()
                    .clip(RoundedCornerShape(50))
                    .background(
                        Brush.verticalGradient(
                            listOf(accent, accent.copy(alpha = 0.35f)),
                        ),
                    ),
            )
            Column(
                modifier = Modifier
                    .padding(start = 13.dp)
                    .fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(5.dp),
            ) {
                Text(
                    text = course.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textPrimary,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Box(
                        Modifier
                            .size(5.dp)
                            .clip(CircleShape)
                            .background(accent),
                    )
                    Text(
                        text = "${course.slotLabel} · ${course.timeLabel}",
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textSecondary,
                    )
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val location = course.location?.takeIf { it.isNotBlank() } ?: "地点待填"
                    Text(
                        text = location,
                        style = MaterialTheme.typography.bodySmall,
                        color = palette.textTertiary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val teacher = course.teacher?.takeIf { it.isNotBlank() }
                    if (teacher != null) {
                        Text(
                            text = "· $teacher",
                            style = MaterialTheme.typography.bodySmall,
                            color = palette.textTertiary,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
    }
}
