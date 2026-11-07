package com.classflow.timetable.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.classflow.timetable.domain.model.DayCourse
import com.classflow.timetable.domain.model.TimeSlot
import com.classflow.timetable.ui.theme.GlassCard
import com.classflow.timetable.ui.theme.LocalGlassPalette
import java.time.LocalDate

/**
 * 周课表网格 —— 设计目标是「一屏看完一周 + 一整天」。
 *
 * 这一版跟上一版最大的区别是：**不做横向滚动**。
 * 手机横着只有 ~360dp 可用宽度，7 天均分后每天 ~46dp；
 * 与其让用户左右滑着找"周五在哪"，不如把字号压到 9sp、教室省成一行省略号，
 * 换来「7 天 × 15 节全在一屏里」——课表的本质是"扫一眼看形状"，
 * 而不是"逐格读文字"。要看细节，点进详情页即可。
 *
 * 三处尺寸都是算出来的，不写死：
 *  1. 节次格高度 = 可用高度 / 节次数（15 节的学校会自动压到 ~42dp）
 *  2. 每天列宽   = (可用宽度 - 节次栏) / 7
 *  3. 表头高度   = 40dp（两行小字，够放"周一 / 10/26"）
 */
private val HeaderHeight: Dp = 38.dp
private val TimeColumnWidth: Dp = 28.dp

/** 节次格高度下限：再矮就放不下课程名了，此时允许纵向滚动。 */
private val MinSlotHeight: Dp = 30.dp

/** 上限：只有 8 节课的学校（或屏幕特别高）也不会拉成一格 100dp 的巨块。 */
private val MaxSlotHeight: Dp = 60.dp

@Composable
fun WeekGrid(
    slots: List<TimeSlot>,
    days: Map<Int, List<DayCourse>>,
    weekStart: LocalDate,
    today: LocalDate,
    showTimeLine: Boolean,
    nowMinutes: Int,
    onCourseClick: (DayCourse) -> Unit,
    modifier: Modifier = Modifier,
) {
    val palette = LocalGlassPalette.current

    if (slots.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text("节次表为空，请到设置里恢复默认节次", color = palette.textSecondary, fontSize = 13.sp)
        }
        return
    }

    GlassCard(
        modifier = modifier.fillMaxSize(),
        cornerRadius = 20.dp,
        fillTop = Color.White.copy(alpha = if (palette.isDark) 0.10f else 0.72f),
        fillBottom = Color.White.copy(alpha = if (palette.isDark) 0.05f else 0.46f),
    ) {
        BoxWithConstraints(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 3.dp, vertical = 5.dp),
        ) {
            val availableBody = (this.maxHeight - HeaderHeight).coerceAtLeast(0.dp)
            val slotH = (availableBody / slots.size).coerceIn(MinSlotHeight, MaxSlotHeight)
            val gridH = slotH * slots.size
            // 精确按剩余宽度均分；不滚动是这一版的核心约束，宁可窄也不要横向滑
            val dayW = ((this.maxWidth - TimeColumnWidth) / 7).coerceAtLeast(40.dp)
            val scrollable = gridH > availableBody + 0.5.dp
            val vScroll = rememberScrollState()

            Column(Modifier.fillMaxSize()) {
                Row(Modifier.fillMaxWidth()) {
                    Spacer(Modifier.width(TimeColumnWidth).height(HeaderHeight))
                    for (day in 1..7) {
                        val date = weekStart.plusDays((day - 1).toLong())
                        DayHeaderCell(width = dayW, day = day, date = date, isToday = date == today)
                    }
                }

                HorizontalDivider(
                    thickness = 0.5.dp,
                    color = palette.textTertiary.copy(alpha = 0.20f),
                )

                Column(
                    if (scrollable) Modifier.verticalScroll(vScroll) else Modifier,
                ) {
                    Row {
                        Column(Modifier.width(TimeColumnWidth)) {
                            // 学校把两小节合成一个时间段（1、2 节都是 08:30-09:55）。
                            // 每格都写一遍起止时间，左栏就变成"同样的两行字刷 15 遍"。
                            // 只在区间的首格写开始、末格写结束，中间格只留节次号。
                            slots.forEachIndexed { i, slot ->
                                val prev = slots.getOrNull(i - 1)
                                val next = slots.getOrNull(i + 1)
                                TimeCell(
                                    slot = slot,
                                    height = slotH,
                                    showStart = prev == null ||
                                        prev.startMinute != slot.startMinute ||
                                        prev.endMinute != slot.endMinute,
                                    showEnd = next == null ||
                                        next.startMinute != slot.startMinute ||
                                        next.endMinute != slot.endMinute,
                                )
                            }
                        }
                        for (day in 1..7) {
                            val date = weekStart.plusDays((day - 1).toLong())
                            DayColumn(
                                courses = days[day].orEmpty(),
                                slotCount = slots.size,
                                slotHeight = slotH,
                                gridHeight = gridH,
                                dayWidth = dayW,
                                isToday = date == today,
                                showTimeLine = showTimeLine,
                                nowMinutes = nowMinutes,
                                firstStartMinute = slots.first().startMinute,
                                lastEndMinute = slots.last().endMinute,
                                onCourseClick = onCourseClick,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DayHeaderCell(width: Dp, day: Int, date: LocalDate, isToday: Boolean) {
    val palette = LocalGlassPalette.current
    Box(
        modifier = Modifier
            .width(width)
            .height(HeaderHeight),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            Text(
                text = WEEK_SHORT[day - 1],
                fontSize = 9.5.sp,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                color = if (isToday) palette.accent else palette.textTertiary,
                maxLines = 1,
            )
            Text(
                text = "${date.monthValue}/${date.dayOfMonth}",
                fontSize = 9.sp,
                fontWeight = if (isToday) FontWeight.SemiBold else FontWeight.Normal,
                color = if (isToday) palette.accent else palette.textTertiary.copy(alpha = 0.75f),
                maxLines = 1,
            )
        }
        if (isToday) {
            // 今天那一列在表头下面压一条 2dp 的强调线，扫视时不用读字就能定位
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(palette.accent.copy(alpha = 0.65f)),
            )
        }
    }
}

@Composable
private fun TimeCell(slot: TimeSlot, height: Dp, showStart: Boolean, showEnd: Boolean) {
    val palette = LocalGlassPalette.current
    // 节次号是"骨架"，必须一直在；时间只是辅助，空间不够时先让它让位。
    val roomy = height >= 40.dp
    Column(
        modifier = Modifier
            .width(TimeColumnWidth)
            .height(height),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text(
            text = slot.index.toString(),
            fontSize = 10.5.sp,
            fontWeight = FontWeight.SemiBold,
            color = palette.textSecondary,
        )
        if (roomy && showStart) {
            Text(
                text = TimeSlot.format(slot.start),
                fontSize = 7.5.sp,
                color = palette.textTertiary,
                maxLines = 1,
            )
        }
        if (roomy && showEnd) {
            Text(
                text = TimeSlot.format(slot.end),
                fontSize = 7.5.sp,
                color = palette.textTertiary.copy(alpha = 0.55f),
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DayColumn(
    courses: List<DayCourse>,
    slotCount: Int,
    slotHeight: Dp,
    gridHeight: Dp,
    dayWidth: Dp,
    isToday: Boolean,
    showTimeLine: Boolean,
    nowMinutes: Int,
    firstStartMinute: Int,
    lastEndMinute: Int,
    onCourseClick: (DayCourse) -> Unit,
) {
    val palette = LocalGlassPalette.current
    Box(
        modifier = Modifier
            .width(dayWidth)
            .height(gridHeight)
            .background(
                if (isToday) palette.accent.copy(alpha = 0.045f) else Color.Transparent,
            ),
    ) {
        Column(Modifier.fillMaxSize()) {
            repeat(slotCount) {
                Box(Modifier.height(slotHeight).fillMaxWidth()) {
                    HorizontalDivider(
                        modifier = Modifier.align(Alignment.BottomCenter),
                        thickness = 0.5.dp,
                        color = palette.textTertiary.copy(alpha = 0.14f),
                    )
                }
            }
        }

        courses.forEach { course ->
            val span = (course.endSlot - course.startSlot + 1).coerceAtLeast(1)
            val top = slotHeight * (course.startSlot - 1)
            val blockHeight = (slotHeight * span - 2.dp).coerceAtLeast(14.dp)
            val accent = Color(course.colorArgb)
            val shape = RoundedCornerShape(6.dp)

            Row(
                modifier = Modifier
                    .offset(x = 1.dp, y = top + 1.dp)
                    .width(dayWidth - 2.dp)
                    .height(blockHeight)
                    .clip(shape)
                    .background(
                        Brush.verticalGradient(
                            listOf(accent.copy(alpha = 0.30f), accent.copy(alpha = 0.15f)),
                        ),
                    )
                    .border(
                        width = 0.8.dp,
                        brush = Brush.verticalGradient(
                            listOf(accent.copy(alpha = 0.70f), accent.copy(alpha = 0.26f)),
                        ),
                        shape = shape,
                    )
                    .clickable { onCourseClick(course) },
            ) {
                Box(
                    Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(accent),
                )
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 2.5.dp, end = 1.5.dp, top = 1.5.dp, bottom = 1.dp),
                ) {
                    // 课程名是唯一必须读的内容，给满它；教室在横向极窄的一列里
                    // 只能显示两三个字，价值低于它占掉的那一行，所以只在大块里出现。
                    Text(
                        text = course.name,
                        fontSize = 9.sp,
                        lineHeight = 10.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.textPrimary,
                        overflow = TextOverflow.Clip,
                    )
                    val location = course.location?.takeIf { it.isNotBlank() }
                    if (location != null && span >= 3) {
                        Text(
                            text = location,
                            fontSize = 7.5.sp,
                            lineHeight = 8.5.sp,
                            color = palette.textSecondary.copy(alpha = 0.85f),
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }

        if (isToday && showTimeLine && lastEndMinute > firstStartMinute) {
            val fraction = ((nowMinutes - firstStartMinute).toFloat() /
                (lastEndMinute - firstStartMinute).toFloat()).coerceIn(0f, 1f)
            val y = (gridHeight.value * fraction).dp
            Box(
                Modifier
                    .offset(y = y)
                    .fillMaxWidth()
                    .height(1.2.dp)
                    .background(Color(0xFFF0453A)),
            )
            Box(
                Modifier
                    .offset(x = (-2.5).dp, y = y - 2.dp)
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(Color(0xFFF0453A)),
            )
        }
    }
}

private val WEEK_SHORT = listOf("一", "二", "三", "四", "五", "六", "日")
