package com.classflow.timetable.ui.screen.bus

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.classflow.timetable.domain.model.BusData
import com.classflow.timetable.ui.theme.GlassCard
import com.classflow.timetable.ui.theme.GlassPalette
import com.classflow.timetable.ui.theme.GlassScreenHeader
import com.classflow.timetable.ui.theme.LocalGlassPalette
import com.classflow.timetable.ui.theme.glassSurface
import kotlinx.coroutines.delay
import java.time.LocalTime
import java.time.format.DateTimeFormatter

/**
 * 校园巴士（直峰专线 655）时刻页。
 *
 * 结构：方向切换（北区/南区）→ 站点 Tab（发车/地铁站/教学楼/终点）→
 * 日期类型（工作日/休息日）→ 「下一班」高亮卡 → 按小时分组的分钟矩阵。
 * 数据全部硬编码在 [BusData]，与教务系统无关，不需要 ViewModel / 数据库。
 */
/** 作为底部 Tab 打开时 [onBack] 传 null，不显示返回箭头。 */
@Composable
fun BusScreen(onBack: (() -> Unit)? = null) {
    val palette = LocalGlassPalette.current

    var directionIndex by remember { mutableIntStateOf(0) }
    var stopIndex by remember { mutableIntStateOf(0) }
    var workday by remember { mutableStateOf(BusData.isWorkday(java.time.LocalDate.now().dayOfWeek.value)) }

    // 每 30 秒刷新一次"现在"，让过去班次自动变淡、下一班自动前移
    var now by remember { mutableStateOf(LocalTime.now().withSecond(0)) }
    LaunchedEffect(Unit) {
        while (true) {
            delay(30_000)
            now = LocalTime.now().withSecond(0)
        }
    }

    val direction = BusData.Direction.entries[directionIndex]
    val stop = BusData.Stop.entries[stopIndex]
    val table = BusData.timetable(direction, stop, workday)
    val next = BusData.nextDeparture(direction, stop, now, workday)

    Column(Modifier.fillMaxSize()) {
        GlassScreenHeader(
            title = "校园巴士",
            subtitle = "直峰专线 655 · 数据取自「校园公交」小程序",
            onBack = onBack,
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(
                start = 18.dp, end = 18.dp, top = 4.dp, bottom = 32.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                DirectionSwitch(
                    palette = palette,
                    selected = directionIndex,
                    onSelect = {
                        directionIndex = it
                        stopIndex = 0
                    },
                )
            }

            item { StopTabs(palette = palette, direction = direction, selected = stopIndex, onSelect = { stopIndex = it }) }

            item { DayTypeSwitch(palette = palette, workday = workday, onSelect = { workday = it }) }

            item {
                if (table != null) {
                    NextBusCard(
                        palette = palette,
                        stop = stop,
                        terminal = BusData.terminalLabel(direction),
                        next = next,
                        now = now,
                        total = BusData.departuresOfDay(direction, stop, workday),
                        workday = workday,
                    )
                }
            }

            if (table != null) {
                item {
                    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 14.dp)) {
                            Text(
                                text = BusData.stopNote(direction, stop),
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = palette.textPrimary,
                            )
                            Spacer(Modifier.height(10.dp))
                            table.forEach { (hour, minutes) ->
                                HourRow(
                                    palette = palette,
                                    hour = hour,
                                    minutes = minutes,
                                    now = now,
                                    next = next,
                                )
                                Spacer(Modifier.height(7.dp))
                            }
                            Spacer(Modifier.height(2.dp))
                            Text(
                                text = "灰色为已过班次 · 高亮为下一班 · ${if (workday) "工作日" else "休息日"}表",
                                style = MaterialTheme.typography.bodySmall,
                                color = palette.textTertiary,
                            )
                        }
                    }
                }
            }
        }
    }
}

// ---------- 方向切换 ----------

@Composable
private fun DirectionSwitch(
    palette: GlassPalette,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    val shape = RoundedCornerShape(18.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .glassSurface(palette, shape)
            .padding(4.dp),
    ) {
        BusData.Direction.entries.forEachIndexed { index, dir ->
            val isSelected = index == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .clip(RoundedCornerShape(14.dp))
                    .background(
                        if (isSelected) {
                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                listOf(palette.accent, palette.accent.copy(alpha = 0.75f)),
                            )
                        } else {
                            androidx.compose.ui.graphics.Brush.horizontalGradient(
                                listOf(Color.Transparent, Color.Transparent),
                            )
                        },
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 10.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = if (dir == BusData.Direction.NORTH) "→ 北区方向" else "→ 南区方向",
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) Color.White else palette.textSecondary,
                )
            }
        }
    }
}

// ---------- 站点 Tab ----------

@Composable
private fun StopTabs(
    palette: GlassPalette,
    direction: BusData.Direction,
    selected: Int,
    onSelect: (Int) -> Unit,
) {
    Row(horizontalArrangement = Arrangement.spacedBy(7.dp)) {
        BusData.Stop.entries.forEachIndexed { index, stop ->
            val isSelected = index == selected
            val label = if (stop == BusData.Stop.TERMINAL) BusData.terminalLabel(direction) else stop.label
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (isSelected) palette.accent else palette.glassFillTop,
                    )
                    .clickable { onSelect(index) }
                    .padding(horizontal = 13.dp, vertical = 7.dp),
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) Color.White else palette.textSecondary,
                )
            }
        }
    }
}

// ---------- 工作日 / 休息日 ----------

@Composable
private fun DayTypeSwitch(
    palette: GlassPalette,
    workday: Boolean,
    onSelect: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        listOf("工作日" to true, "休息日" to false).forEach { (label, value) ->
            val isSelected = workday == value
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) palette.accentSoft else palette.glassFillTop)
                    .clickable { onSelect(value) }
                    .padding(horizontal = 13.dp, vertical = 6.dp),
            ) {
                Text(
                    text = label,
                    fontSize = 12.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    color = if (isSelected) palette.accent else palette.textSecondary,
                )
            }
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = if (workday) "周一至周五按此表" else "周末与节假日按此表",
            fontSize = 10.sp,
            color = palette.textTertiary,
        )
    }
}

// ---------- 下一班 ----------

private val timeFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")

@Composable
private fun NextBusCard(
    palette: GlassPalette,
    stop: BusData.Stop,
    terminal: String,
    next: LocalTime?,
    now: LocalTime,
    total: Int,
    workday: Boolean,
) {
    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 24.dp) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = Modifier
                    .size(42.dp)
                    .clip(CircleShape)
                    .background(palette.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    Icons.Filled.DirectionsBus,
                    contentDescription = "下一班",
                    tint = palette.accent,
                    modifier = Modifier.size(23.dp),
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    text = if (next != null) "下一班 ${next.format(timeFormatter)}" else "今天已收班",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textPrimary,
                )
                Text(
                    text = if (next != null) {
                        val wait = java.time.Duration.between(now, next).toMinutes()
                        "去往$terminal · 约 $wait 分钟后 · ${if (workday) "工作日" else "休息日"}全天 $total 班"
                    } else {
                        "末班车已过，明天再赶早吧"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = palette.textTertiary,
                )
            }
            if (next != null) {
                val wait = java.time.Duration.between(now, next).toMinutes()
                Text(
                    text = "$wait",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = palette.accent,
                )
                Text(
                    text = " 分钟",
                    fontSize = 10.sp,
                    color = palette.textTertiary,
                )
            }
        }
    }
}

// ---------- 小时行 ----------

@Composable
private fun HourRow(
    palette: GlassPalette,
    hour: Int,
    minutes: List<Int>,
    now: LocalTime,
    next: LocalTime?,
) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = "%02d".format(hour),
            fontSize = 15.sp,
            fontWeight = FontWeight.Bold,
            color = palette.textPrimary,
            modifier = Modifier.width(30.dp),
        )
        Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
            minutes.chunked(6).forEach { rowMinutes ->
                Row(horizontalArrangement = Arrangement.spacedBy(5.dp)) {
                    rowMinutes.forEach { minute ->
                        val time = LocalTime.of(hour, minute)
                        val isNext = next != null && time == next
                        val isPast = time.isBefore(now) && !isNext
                        val chipColor = when {
                            isNext -> palette.accent
                            isPast -> palette.textTertiary.copy(alpha = 0.10f)
                            else -> palette.textTertiary.copy(alpha = 0.20f)
                        }
                        val textColor = when {
                            isNext -> Color.White
                            isPast -> palette.textTertiary.copy(alpha = 0.55f)
                            else -> palette.textSecondary
                        }
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(50))
                                .background(chipColor)
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                        ) {
                            Text(
                                text = "%02d".format(minute),
                                fontSize = 11.sp,
                                fontWeight = if (isNext) FontWeight.Bold else FontWeight.Normal,
                                color = textColor,
                            )
                        }
                    }
                }
            }
        }
    }
}
