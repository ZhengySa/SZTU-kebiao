package com.classflow.timetable.ui.screen.pomodoro

import android.Manifest
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.classflow.timetable.domain.pomodoro.PomodoroPhase
import com.classflow.timetable.pomodoro.PomodoroEngine
import com.classflow.timetable.pomodoro.PomodoroUiState
import com.classflow.timetable.reminder.NotificationHelper
import com.classflow.timetable.ui.theme.GlassCard
import com.classflow.timetable.ui.theme.GlassPalette
import com.classflow.timetable.ui.theme.GlassScreenHeader
import com.classflow.timetable.ui.theme.LocalGlassPalette
import com.classflow.timetable.ui.theme.glassSurface
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.StateFlow
import javax.inject.Inject

@HiltViewModel
class PomodoroViewModel @Inject constructor(
    private val engine: PomodoroEngine,
) : ViewModel() {
    /** 直接转发引擎的状态：计时归引擎管，ViewModel 只做一层壳，页面销毁了计时也不停。 */
    val state: StateFlow<PomodoroUiState> = engine.state

    fun toggle() = engine.toggle()
    fun reset() = engine.reset()
    fun skip() = engine.skip()
    fun refresh() = engine.refresh()
    fun setFocusMinutes(minutes: Int) = engine.setFocusMinutes(minutes)
    fun setBreakMinutes(minutes: Int) = engine.setBreakMinutes(minutes)
    fun setLongBreakMinutes(minutes: Int) = engine.setLongBreakMinutes(minutes)
    fun setAutoStartNext(enabled: Boolean) = engine.setAutoStartNext(enabled)
}

private val FOCUS_MINUTE_OPTIONS = listOf(15, 25, 30, 45, 60)
private val BREAK_MINUTE_OPTIONS = listOf(5, 10, 15)
private val LONG_BREAK_MINUTE_OPTIONS = listOf(15, 20, 30)

/** 休息用一个偏冷的青色，和专注的紫色拉开距离：瞥一眼就知道现在该干什么。 */
private val BREAK_TINT = Color(0xFF15A0B8)

/**
 * 番茄钟。
 *
 * 一屏只放一个东西：还剩多久。时长调节收在下面，因为它是「装好一次就不用再动」的东西，
 * 摆太显眼反而每做一轮都想调一下。
 */
@Composable
fun PomodoroScreen(
    onBack: () -> Unit,
    viewModel: PomodoroViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = LocalGlassPalette.current
    val tint = if (state.phase == PomodoroPhase.BREAK) BREAK_TINT else palette.accent

    // 每次进页面重新算一次：跨天了要清「今日」，久别回来也不该再看到上次那句「刚结束」
    LaunchedEffect(Unit) { viewModel.refresh() }

    val context = LocalContext.current
    var notificationsOn by remember { mutableStateOf(NotificationHelper.hasPermission(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { notificationsOn = NotificationHelper.hasPermission(context) }

    Column(Modifier.fillMaxSize()) {
        GlassScreenHeader(
            title = "番茄钟",
            subtitle = "专注 ${state.settings.focusMinutes} 分钟，休息 ${state.settings.breakMinutes} 分钟",
            onBack = onBack,
        )

        BoxWithConstraints(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = this.maxHeight)
                    .padding(horizontal = 20.dp)
                    .padding(top = 6.dp, bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                PhaseHeadline(state = state, tint = tint, palette = palette)

                Spacer(Modifier.height(12.dp))

                TimerRing(
                    progress = state.progress,
                    tint = tint,
                    palette = palette,
                    modifier = Modifier.size(222.dp),
                ) {
                    Text(
                        text = state.clockText,
                        fontSize = 56.sp,
                        fontWeight = FontWeight.Bold,
                        color = palette.textPrimary,
                        // 等宽数字：否则 1 比 0 窄，秒数跳动时整串数字会左右抖
                        style = TextStyle(fontFeatureSettings = "tnum"),
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = phaseLabel(state),
                        fontSize = 12.sp,
                        color = palette.textTertiary,
                    )
                }

                Spacer(Modifier.height(14.dp))

                RoundDots(
                    total = state.settings.roundsPerCycle,
                    done = state.roundsDone,
                    tint = tint,
                    palette = palette,
                )

                Spacer(Modifier.height(18.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    RoundAction(
                        icon = Icons.Filled.Refresh,
                        label = "重置",
                        palette = palette,
                        onClick = viewModel::reset,
                    )
                    PrimaryAction(
                        running = state.running,
                        tint = tint,
                        onClick = viewModel::toggle,
                    )
                    RoundAction(
                        icon = Icons.Filled.SkipNext,
                        label = "跳过",
                        palette = palette,
                        onClick = viewModel::skip,
                    )
                }

                Spacer(Modifier.height(20.dp))

                TodayCard(state = state, tint = tint, palette = palette)

                Spacer(Modifier.height(12.dp))

                DurationSection(
                    state = state,
                    tint = tint,
                    palette = palette,
                    onFocus = viewModel::setFocusMinutes,
                    onBreak = viewModel::setBreakMinutes,
                    onLongBreak = viewModel::setLongBreakMinutes,
                    onAutoNext = viewModel::setAutoStartNext,
                )

                Spacer(Modifier.height(12.dp))

                if (!notificationsOn) {
                    // 没通知权限的话计时照样走，但"到点了"只能自己盯屏幕 —— 明说，别让人等一声响
                    Row(
                        modifier = Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .background(palette.danger.copy(alpha = if (palette.isDark) 0.18f else 0.08f))
                            .clickable {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    context.openNotificationSettings()
                                }
                            }
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            text = "通知没开，结束时不会有提醒",
                            fontSize = 11.sp,
                            color = palette.danger,
                        )
                        Text(
                            text = "去开启",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.danger,
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                }

                Text(
                    text = "切到后台也会继续走，结束时通知你。系统把 App 彻底清了，回来会按真实时间把账算清。",
                    fontSize = 10.5.sp,
                    lineHeight = 16.sp,
                    color = palette.textTertiary,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

/**
 * 圆环上方的阶段提示。
 *
 * 「刚结束一段」时这里换成一句过渡话——用户放下手机再拿起来，最需要知道的是
 * 「我是不是错过了一次休息」，而不是现在几点。
 */
@Composable
private fun PhaseHeadline(state: PomodoroUiState, tint: Color, palette: GlassPalette) {
    val finished = state.justFinished
    if (finished != null) {
        val text = if (finished == PomodoroPhase.FOCUS) {
            if (state.phase == PomodoroPhase.BREAK) "专注结束了，休息一下" else "专注结束了"
        } else {
            "休息结束了，回来继续"
        }
        Pill(text = text, tint = tint, palette = palette)
        return
    }
    val text = when {
        state.running && state.phase == PomodoroPhase.FOCUS -> "专注中 · 别碰手机"
        state.running && state.isLongBreak -> "长休息中 · 走远一点"
        state.running -> "休息中 · 让眼睛离开屏幕"
        state.idle && state.phase == PomodoroPhase.FOCUS -> "准备好就开始"
        state.idle && state.isLongBreak -> "长休息 · ${state.settings.longBreakMinutes} 分钟"
        state.idle -> "休息 · ${state.settings.breakMinutes} 分钟"
        else -> "已暂停"
    }
    Pill(text = text, tint = tint, palette = palette)
}

@Composable
private fun Pill(text: String, tint: Color, palette: GlassPalette) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(tint.copy(alpha = if (palette.isDark) 0.22f else 0.13f))
            .padding(horizontal = 14.dp, vertical = 6.dp),
    ) {
        Text(
            text = text,
            fontSize = 11.5.sp,
            fontWeight = FontWeight.Medium,
            color = tint,
        )
    }
}

private fun phaseLabel(state: PomodoroUiState): String = when {
    state.running && state.phase == PomodoroPhase.FOCUS -> "FOCUS"
    state.running && state.isLongBreak -> "LONG BREAK"
    state.running -> "BREAK"
    state.phase == PomodoroPhase.FOCUS -> "第 ${state.roundsDone + 1} 个番茄"
    else -> "休息"
}

/** 一整圈的进度环。从 12 点开始顺时针走，走完正好一段。 */
@Composable
private fun TimerRing(
    progress: Float,
    tint: Color,
    palette: GlassPalette,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 15.dp.toPx()
            val inset = stroke / 2f + 2.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)

            drawArc(
                color = palette.textPrimary.copy(alpha = 0.07f),
                startAngle = -90f,
                sweepAngle = 360f,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (progress > 0.002f) {
                drawArc(
                    brush = Brush.sweepGradient(listOf(tint.copy(alpha = 0.35f), tint, tint.copy(alpha = 0.35f))),
                    startAngle = -90f,
                    sweepAngle = 360f * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) { content() }
    }
}

/** 本轮进度：做满 4 个点就进长休息。 */
@Composable
private fun RoundDots(total: Int, done: Int, tint: Color, palette: GlassPalette) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        repeat(total) { index ->
            val filled = index < done
            Box(
                Modifier
                    .size(if (filled) 8.dp else 7.dp)
                    .clip(CircleShape)
                    .background(
                        if (filled) tint else palette.textPrimary.copy(alpha = 0.14f),
                    ),
            )
        }
    }
}

@Composable
private fun PrimaryAction(running: Boolean, tint: Color, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .height(52.dp)
            .clip(RoundedCornerShape(26.dp))
            .background(tint)
            .clickable(onClick = onClick)
            .padding(horizontal = 26.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = if (running) Icons.Filled.Pause else Icons.Filled.PlayArrow,
            contentDescription = null,
            tint = Color.White,
            modifier = Modifier.size(20.dp),
        )
        Text(
            text = if (running) "暂停" else "开始",
            color = Color.White,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
        )
    }
}

@Composable
private fun RoundAction(
    icon: ImageVector,
    label: String,
    palette: GlassPalette,
    onClick: () -> Unit,
) {
    Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(
            modifier = Modifier
                .size(46.dp)
                .glassSurface(palette, CircleShape)
                .clickable(onClick = onClick),
            contentAlignment = Alignment.Center,
        ) {
            Icon(icon, contentDescription = label, tint = palette.textSecondary, modifier = Modifier.size(19.dp))
        }
        Text(label, fontSize = 10.sp, color = palette.textTertiary)
    }
}

/** 今日战绩。只统计走完的专注段 —— 跳过和中途重置都不算，不然数字会虚。 */
@Composable
private fun TodayCard(state: PomodoroUiState, tint: Color, palette: GlassPalette) {
    GlassCard(Modifier.fillMaxWidth(), cornerRadius = 22.dp, elevation = 6.dp) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            StatCell(
                label = "今日番茄",
                value = "${state.todayCount}",
                unit = "个",
                tint = tint,
                palette = palette,
                modifier = Modifier.weight(1f),
            )
            Box(
                Modifier
                    .width(1.dp)
                    .height(28.dp)
                    .background(palette.textPrimary.copy(alpha = 0.08f)),
            )
            StatCell(
                label = "专注时长",
                value = "${state.todayFocusMinutes}",
                unit = "分钟",
                tint = tint,
                palette = palette,
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@Composable
private fun StatCell(
    label: String,
    value: String,
    unit: String,
    tint: Color,
    palette: GlassPalette,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier,
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(text = label, fontSize = 10.5.sp, color = palette.textTertiary)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(
                text = value,
                fontSize = 22.sp,
                fontWeight = FontWeight.Bold,
                color = tint,
                style = TextStyle(fontFeatureSettings = "tnum"),
            )
            Text(
                text = unit,
                fontSize = 10.5.sp,
                color = palette.textTertiary,
                modifier = Modifier.padding(start = 3.dp, bottom = 2.dp),
            )
        }
    }
}

/**
 * 时长设置。
 *
 * 默认收起：这一页的主角是「还剩多久」，时长是装好一次就不再动的东西。
 * 摊开摆三排按钮，每做一轮都会手痒去调一下 —— 那是这个页面上最不该有的诱惑。
 */
@Composable
private fun DurationSection(
    state: PomodoroUiState,
    tint: Color,
    palette: GlassPalette,
    onFocus: (Int) -> Unit,
    onBreak: (Int) -> Unit,
    onLongBreak: (Int) -> Unit,
    onAutoNext: (Boolean) -> Unit,
) {
    var expanded by rememberSaveable { mutableStateOf(false) }

    GlassCard(Modifier.fillMaxWidth(), cornerRadius = 22.dp, elevation = 6.dp) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(horizontal = 18.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text("时长", color = palette.textPrimary, fontSize = 14.sp)
                    Text(
                        "专注 ${state.settings.focusMinutes} · 休息 ${state.settings.breakMinutes} · " +
                            "长休息 ${state.settings.longBreakMinutes} 分钟",
                        color = palette.textTertiary,
                        fontSize = 11.sp,
                    )
                }
                Text("调整", color = tint, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                Icon(
                    imageVector = if (expanded) Icons.Filled.KeyboardArrowUp else Icons.Filled.KeyboardArrowDown,
                    contentDescription = null,
                    tint = tint,
                    modifier = Modifier.size(18.dp),
                )
            }

            AnimatedVisibility(visible = expanded) {
                Column(
                    modifier = Modifier.padding(start = 18.dp, end = 18.dp, bottom = 16.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ChipRow(
                        label = "专注",
                        options = FOCUS_MINUTE_OPTIONS,
                        selected = state.settings.focusMinutes,
                        tint = tint,
                        palette = palette,
                        onSelect = onFocus,
                    )
                    ChipRow(
                        label = "休息",
                        options = BREAK_MINUTE_OPTIONS,
                        selected = state.settings.breakMinutes,
                        tint = tint,
                        palette = palette,
                        onSelect = onBreak,
                    )
                    ChipRow(
                        label = "长休息",
                        options = LONG_BREAK_MINUTE_OPTIONS,
                        selected = state.settings.longBreakMinutes,
                        tint = tint,
                        palette = palette,
                        onSelect = onLongBreak,
                    )

                    HorizontalDivider(color = palette.textPrimary.copy(alpha = 0.06f))

                    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                            Text("自动开始下一段", color = palette.textPrimary, fontSize = 14.sp)
                            Text(
                                "一段结束直接进下一段，不用再按一次开始",
                                color = palette.textTertiary,
                                fontSize = 11.sp,
                            )
                        }
                        Switch(
                            checked = state.settings.autoStartNext,
                            onCheckedChange = onAutoNext,
                            colors = SwitchDefaults.colors(checkedTrackColor = tint),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ChipRow(
    label: String,
    options: List<Int>,
    selected: Int,
    tint: Color,
    palette: GlassPalette,
    onSelect: (Int) -> Unit,
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            fontSize = 12.sp,
            color = palette.textTertiary,
            modifier = Modifier.width(44.dp),
        )
        Row(
            modifier = Modifier.horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            options.forEach { minutes ->
                OptionChip(
                    label = "$minutes 分",
                    selected = minutes == selected,
                    tint = tint,
                    palette = palette,
                ) { onSelect(minutes) }
            }
        }
    }
}

@Composable
private fun OptionChip(
    label: String,
    selected: Boolean,
    tint: Color,
    palette: GlassPalette,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(11.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) tint.copy(alpha = if (palette.isDark) 0.24f else 0.13f) else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) tint.copy(alpha = 0.55f) else palette.textPrimary.copy(alpha = 0.10f),
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp),
    ) {
        Text(
            text = label,
            color = if (selected) tint else palette.textSecondary,
            fontSize = 11.5.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}

/** Android 12 及以下没有 POST_NOTIFICATIONS 权限，只能把用户送到系统通知设置页。 */
private fun Context.openNotificationSettings() {
    val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    runCatching { startActivity(intent) }
}
