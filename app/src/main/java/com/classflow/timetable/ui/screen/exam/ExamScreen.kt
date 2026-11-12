package com.classflow.timetable.ui.screen.exam

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.classflow.timetable.core.ui.LoadingState
import com.classflow.timetable.data.remote.sztu.SessionExpiredException
import com.classflow.timetable.domain.model.ExamEntry
import com.classflow.timetable.domain.usecase.NotLoggedInException
import com.classflow.timetable.domain.usecase.SztuExamUseCase
import com.classflow.timetable.ui.theme.GlassCard
import com.classflow.timetable.ui.theme.GlassPalette
import com.classflow.timetable.ui.theme.GlassScreenHeader
import com.classflow.timetable.ui.theme.LocalGlassPalette
import com.classflow.timetable.ui.theme.glassSurface
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import javax.inject.Inject

data class ExamUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val needLogin: Boolean = false,
    val error: String? = null,
    val rows: List<ExamEntry> = emptyList(),
    /** 教务给的一句提示，例如"没标注期末，先全部列出"。 */
    val note: String? = null,
    val semesterLabel: String = "",
    /** 是否已经成功查过一次（用来区分"还没查"和"查了是空的"）。 */
    val loaded: Boolean = false,
) {
    val isEmpty: Boolean get() = rows.isEmpty()
}

@HiltViewModel
class ExamViewModel @Inject constructor(
    private val exams: SztuExamUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow(ExamUiState())
    val state: StateFlow<ExamUiState> = _state.asStateFlow()

    private var started = false
    private var inFlight = false

    fun start() {
        if (!started) {
            started = true
            refresh()
        } else if (_state.value.needLogin) {
            // 从登录页回来时再试一次：ViewModel 挂在返回栈上不会重建，
            // 不主动重查的话，界面会一直停在"会话已失效"。
            refresh()
        }
    }

    fun refresh() {
        if (inFlight) return
        inFlight = true
        _state.value = _state.value.copy(
            refreshing = !_state.value.loaded,
            loading = !_state.value.loaded,
            error = null,
        )
        viewModelScope.launch {
            try {
                val loaded = exams.load(null)
                _state.value = _state.value.copy(
                    rows = loaded.rows,
                    note = loaded.note,
                    semesterLabel = loaded.semesterLabel,
                    needLogin = false,
                    error = null,
                    loaded = true,
                )
            } catch (e: NotLoggedInException) {
                _state.value = _state.value.copy(needLogin = true, error = null)
            } catch (e: SessionExpiredException) {
                _state.value = _state.value.copy(needLogin = true, error = null)
            } catch (e: Throwable) {
                _state.value = _state.value.copy(error = friendly(e))
            } finally {
                inFlight = false
                _state.value = _state.value.copy(loading = false, refreshing = false)
            }
        }
    }

    private fun friendly(error: Throwable): String = when (error) {
        is SessionExpiredException -> "登录状态已失效，请重新登录学校账号。"
        is java.net.UnknownHostException -> "连不上学校服务器，请确认手机能打开校园网。"
        is java.net.SocketTimeoutException -> "学校服务器响应超时，换个网络（或稍后）再试。"
        else -> error.message ?: "加载考试安排失败，请重试。"
    }
}

@Composable
fun ExamScreen(
    onBack: () -> Unit,
    onLogin: () -> Unit,
    viewModel: ExamViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = LocalGlassPalette.current

    LaunchedEffect(Unit) { viewModel.start() }

    Column(Modifier.fillMaxSize()) {
        GlassScreenHeader(
            title = "考试",
            subtitle = when {
                state.needLogin -> "会话已失效"
                state.semesterLabel.isNotBlank() -> state.semesterLabel
                else -> "期末安排 · 时间 · 地点 · 座位"
            },
            onBack = onBack,
            actions = {
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .glassSurface(palette, RoundedCornerShape(14.dp))
                        .clickable(enabled = !state.refreshing) { viewModel.refresh() },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        Icons.Filled.Refresh,
                        contentDescription = "刷新考试安排",
                        tint = if (state.refreshing) palette.textTertiary else palette.accent,
                        modifier = Modifier.size(18.dp),
                    )
                }
            },
        )

        when {
            state.loading && state.isEmpty -> LoadingState()

            state.needLogin && state.isEmpty -> ExamMessagePane(
                palette = palette,
                title = "还没登录教务系统",
                message = "考试安排要从学校教务系统取，登录一次就行，之后不用再登。",
                actionLabel = "去登录",
                onAction = onLogin,
            )

            state.error != null && state.isEmpty -> ExamMessagePane(
                palette = palette,
                title = "没拿到考试安排",
                message = state.error.orEmpty(),
                actionLabel = "重试",
                onAction = viewModel::refresh,
            )

            else -> ExamContent(
                state = state,
                palette = palette,
                onRetry = viewModel::refresh,
            )
        }
    }
}

@Composable
private fun ExamContent(
    state: ExamUiState,
    palette: GlassPalette,
    onRetry: () -> Unit,
) {
    val today = LocalDate.now()
    val next = state.rows.firstOrNull { it.date != null && !it.date.isBefore(today) }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 40.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "summary") {
            ExamSummaryCard(
                palette = palette,
                count = state.rows.size,
                next = next,
                semesterLabel = state.semesterLabel,
            )
        }

        val note = state.note
        if (!note.isNullOrBlank()) {
            item(key = "note") {
                HintCard(palette = palette, text = note)
            }
        }

        val error = state.error
        if (error != null) {
            item(key = "error") {
                ErrorCard(palette = palette, text = error, onRetry = onRetry)
            }
        }

        if (state.isEmpty) {
            item(key = "empty") {
                ExamMessagePane(
                    palette = palette,
                    title = if (state.loaded) "期末考试安排还没出来" else "还没查到考试",
                    message = "教务一般考前 3–4 周才排考场。安排一出来，进这一页刷新就能看到，" +
                        "不用重新登录。",
                    actionLabel = "重新查一次",
                    onAction = onRetry,
                )
            }
        } else {
            val groups = state.rows
                .groupBy { it.date }
                .toList()
                .sortedBy { (date, _) -> date ?: LocalDate.MAX }

            groups.forEach { (date, list) ->
                item(key = "day-${date ?: "unknown"}") {
                    DayHeader(date = date, count = list.size, palette = palette)
                }
                items(items = list, key = { it.identity + it.name }) { entry ->
                    ExamRowCard(entry = entry, palette = palette)
                }
            }
        }

        item(key = "privacy") {
            Text(
                text = "这些信息每次都是从教务现取的，只留在内存里：不进数据库、不写本地备份、也不进桌面小组件。",
                color = palette.textTertiary,
                fontSize = 11.sp,
                lineHeight = 17.sp,
                modifier = Modifier.padding(horizontal = 6.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun ExamSummaryCard(
    palette: GlassPalette,
    count: Int,
    next: ExamEntry?,
    semesterLabel: String,
) {
    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 28.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
            Text(
                text = "期末考试",
                color = palette.textTertiary,
                fontSize = 11.sp,
            )
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = if (count == 0) "还没排" else "$count",
                    color = if (count == 0) palette.textTertiary else palette.accent,
                    fontSize = 38.sp,
                    fontWeight = FontWeight.Bold,
                )
                if (count > 0) {
                    Text(
                        text = " 场",
                        color = palette.textTertiary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(bottom = 8.dp),
                    )
                }
            }
            Spacer(Modifier.height(6.dp))
            val nextDate = next?.date
            Text(
                text = if (nextDate == null) {
                    "有安排后这里会显示最近一场"
                } else {
                    "最近一场 ${dateText(nextDate)} ${next?.start?.let(::timeText).orEmpty()} · ${countdownText(nextDate)}"
                },
                color = palette.textSecondary,
                fontSize = 13.sp,
            )
            if (semesterLabel.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = semesterLabel,
                    color = palette.textTertiary,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun DayHeader(date: LocalDate?, count: Int, palette: GlassPalette) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(start = 6.dp, top = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (date == null) "时间待定" else dateText(date),
            color = palette.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.width(6.dp))
        Text(text = "$count 场", color = palette.textTertiary, fontSize = 11.sp)
        if (date != null) {
            Spacer(Modifier.weight(1f))
            Text(text = countdownText(date), color = palette.textTertiary, fontSize = 11.sp)
        }
    }
}

/**
 * 一场考试：左边时间，右边课程 / 教室 / 座位。
 *
 * 不把"考试名称"当标题——同一门课可能既有期末又有补考，标题用课程名，
 * 考试名称放到下面的小字里，一眼先看到考什么。
 */
@Composable
private fun ExamRowCard(entry: ExamEntry, palette: GlassPalette) {
    val shape = RoundedCornerShape(20.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(palette, shape)
            .padding(horizontal = 16.dp, vertical = 14.dp),
    ) {
        Column(modifier = Modifier.width(60.dp)) {
            if (entry.start == null) {
                // 教务只写了"第19周 星期五 上午"这种，解析不出具体时刻，别硬编一个
                Text(
                    text = "待定",
                    color = palette.textTertiary,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                )
            } else {
                Text(
                    text = timeText(entry.start),
                    color = palette.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    text = entry.end?.let(::timeText).orEmpty(),
                    color = palette.textTertiary,
                    fontSize = 11.sp,
                )
            }
        }

        Spacer(Modifier.width(14.dp))
        Box(
            modifier = Modifier
                .width(1.dp)
                .height(36.dp)
                .background(palette.glassBorderTop),
        )
        Spacer(Modifier.width(14.dp))

        Column(Modifier.weight(1f)) {
            Text(
                text = entry.course,
                color = palette.textPrimary,
                fontSize = 15.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (entry.location.isNotBlank()) {
                Spacer(Modifier.height(4.dp))
                Text(
                    text = entry.location,
                    color = palette.accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            val meta = buildList {
                if (entry.seat.isNotBlank()) add("座位 ${entry.seat}")
                if (entry.form.isNotBlank()) add(entry.form)
                if (entry.name.isNotBlank() && !entry.name.contains(entry.course)) add(entry.name)
            }.joinToString(" · ")
            if (meta.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = meta,
                    color = palette.textTertiary,
                    fontSize = 11.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // 时间没解析出来（教务写的是"第19周 星期五 上午"这种）就把原文放出来
            if (entry.start == null && entry.rawTime.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = entry.rawTime,
                    color = palette.textTertiary,
                    fontSize = 11.sp,
                )
            }
            if (entry.note.isNotBlank()) {
                Spacer(Modifier.height(3.dp))
                Text(
                    text = entry.note,
                    color = palette.textTertiary,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

@Composable
private fun HintCard(palette: GlassPalette, text: String) {
    Text(
        text = text,
        color = palette.textSecondary,
        fontSize = 12.sp,
        lineHeight = 18.sp,
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(palette.accentSoft)
            .padding(horizontal = 14.dp, vertical = 10.dp),
    )
}

@Composable
private fun ErrorCard(palette: GlassPalette, text: String, onRetry: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.danger.copy(alpha = 0.10f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(text = text, color = palette.danger, fontSize = 12.sp)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "重试",
            color = palette.accent,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.clickable(onClick = onRetry),
        )
    }
}

@Composable
private fun ExamMessagePane(
    palette: GlassPalette,
    title: String,
    message: String,
    actionLabel: String? = null,
    onAction: (() -> Unit)? = null,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text(
            text = title,
            color = palette.textPrimary,
            fontSize = 16.sp,
            fontWeight = FontWeight.SemiBold,
            textAlign = TextAlign.Center,
        )
        Text(
            text = message,
            color = palette.textTertiary,
            fontSize = 12.sp,
            lineHeight = 18.sp,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(6.dp))
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        Brush.horizontalGradient(
                            listOf(palette.accent, palette.accent.copy(alpha = 0.75f)),
                        ),
                    )
                    .clickable(onClick = onAction)
                    .padding(horizontal = 20.dp, vertical = 11.dp),
            ) {
                Text(
                    text = actionLabel,
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}

// ---------- 格式化 ----------

private val DATE_FORMAT = DateTimeFormatter.ofPattern("M月d日", Locale.CHINA)

private fun dateText(date: LocalDate): String =
    date.format(DATE_FORMAT) + " " + date.dayOfWeek.getDisplayName(
        java.time.format.TextStyle.SHORT,
        Locale.CHINA,
    )

private fun timeText(time: LocalTime): String =
    String.format(Locale.US, "%d:%02d", time.hour, time.minute)

private fun countdownText(date: LocalDate): String {
    val days = ChronoUnit.DAYS.between(LocalDate.now(), date)
    return when {
        days < 0 -> "已结束"
        days == 0L -> "今天"
        days == 1L -> "明天"
        else -> "还有 $days 天"
    }
}
