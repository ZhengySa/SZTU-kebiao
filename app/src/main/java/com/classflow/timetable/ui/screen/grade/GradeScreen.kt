package com.classflow.timetable.ui.screen.grade

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
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
import androidx.compose.foundation.lazy.LazyRow
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
import com.classflow.timetable.data.local.GradeCache
import com.classflow.timetable.data.local.GradeCacheStore
import com.classflow.timetable.data.remote.sztu.SessionExpiredException
import com.classflow.timetable.data.remote.sztu.ZfGradeParser
import com.classflow.timetable.domain.model.GradeRank
import com.classflow.timetable.domain.model.GradeRecord
import com.classflow.timetable.domain.model.GradeSemester
import com.classflow.timetable.domain.model.GradeStats
import com.classflow.timetable.domain.model.GradeSummary
import com.classflow.timetable.domain.model.gradeSemesterTitle
import com.classflow.timetable.domain.usecase.NotLoggedInException
import com.classflow.timetable.domain.usecase.SztuGradeUseCase
import com.classflow.timetable.ui.theme.GlassCard
import com.classflow.timetable.ui.theme.GlassPalette
import com.classflow.timetable.ui.theme.GlassScreenHeader
import com.classflow.timetable.ui.theme.LocalGlassPalette
import com.classflow.timetable.ui.theme.glassSurface
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.inject.Inject

data class GradeUiState(
    val loading: Boolean = true,
    val refreshing: Boolean = false,
    val needLogin: Boolean = false,
    val error: String? = null,
    val semesters: List<GradeSemester> = emptyList(),
    /** 空串 = 全部学期。 */
    val selected: String = ZfGradeParser.ALL_SEMESTERS,
    val rows: List<GradeRecord> = emptyList(),
    val summary: GradeSummary = GradeSummary(),
    val rank: GradeRank? = null,
    val loadingSemester: Boolean = false,
    val fromCache: Boolean = false,
    val cachedAt: Long = 0L,
) {
    val isEmpty: Boolean get() = rows.isEmpty()
}

/**
 * 成绩页的状态机。
 *
 * 数据分两次到位：先渲染本地缓存（秒开），再联网刷新；
 * 刷新的顺序是「默认学期 → 其余学期逐个补 → 排名」，每补一屏就刷一次 UI，
 * 用户不会对着一个转圈等八次请求。
 */
@HiltViewModel
class GradeViewModel @Inject constructor(
    private val grades: SztuGradeUseCase,
    private val cacheStore: GradeCacheStore,
) : ViewModel() {

    private val _state = MutableStateFlow(GradeUiState())
    val state: StateFlow<GradeUiState> = _state.asStateFlow()

    /** key = 学期；空串是「教务系统默认那一档」，一般是全部或本学期。 */
    private val bySemester = linkedMapOf<String, List<GradeRecord>>()
    private var semesters: List<GradeSemester> = emptyList()
    private var rank: GradeRank? = null
    private var started = false
    private var refreshing = false

    fun start() {
        if (started) return
        started = true
        viewModelScope.launch {
            val cached = cacheStore.cache.first()
            if (!cached.isEmpty) applyCache(cached)
            refresh()
        }
    }

    fun select(key: String) {
        if (_state.value.selected == key) return
        _state.value = _state.value.copy(selected = key, loadingSemester = false)
        publish()
        if (key.isNotBlank() && bySemester[key] == null) {
            viewModelScope.launch {
                _state.value = _state.value.copy(loadingSemester = true)
                runCatching { grades.load(key, grades.cookies()) }
                    .onSuccess { result ->
                        indexRows(key, result.rows)
                        if (result.semesters.isNotEmpty()) {
                            semesters = mergeSemesters(semesters, result.semesters)
                        }
                        publish()
                        saveCache()
                    }
                    .onFailure { _state.value = _state.value.copy(error = friendly(it)) }
                _state.value = _state.value.copy(loadingSemester = false)
            }
        }
    }

    fun refresh() {
        if (refreshing) return
        refreshing = true
        _state.value = _state.value.copy(refreshing = true, error = null)
        viewModelScope.launch {
            try {
                val cookies = grades.cookies()

                val first = grades.load(null, cookies)
                indexRows("", first.rows)
                semesters = mergeSemesters(semesters, first.semesters)
                rank = first.rank ?: rank
                publish(clearError = true)

                // 默认那一档只覆盖了部分学期时，把剩下的补齐，否则「总绩点」是残的
                val covered = first.rows.map { it.semester }.toSet()
                val missing = semesters.map { it.key }.filter { it !in covered && bySemester[it] == null }
                missing.forEach { key ->
                    runCatching { grades.load(key, cookies) }
                        .onSuccess { result ->
                            indexRows(key, result.rows)
                            if (result.semesters.isNotEmpty()) {
                                semesters = mergeSemesters(semesters, result.semesters)
                            }
                            publish()
                        }
                    // 单个学期失败不算整体失败：可能是那学期还没成绩
                }

                // 排名就在成绩页顶部那行「专业绩点排名/专业总人数:95/157」里
                rank = first.rank ?: rank
                _state.value = _state.value.copy(
                    needLogin = false,
                    loading = false,
                    fromCache = false,
                    cachedAt = 0L,
                )
                publish()
                saveCache()
            } catch (e: NotLoggedInException) {
                _state.value = _state.value.copy(loading = false, needLogin = true, error = null)
            } catch (e: SessionExpiredException) {
                _state.value = _state.value.copy(loading = false, needLogin = true, error = null)
            } catch (e: Throwable) {
                _state.value = _state.value.copy(
                    loading = false,
                    error = friendly(e),
                )
            } finally {
                refreshing = false
                _state.value = _state.value.copy(refreshing = false, loadingSemester = false)
            }
        }
    }

    // ---------- 内部 ----------

    private fun applyCache(cache: GradeCache) {
        semesters = mergeSemesters(semesters, cache.semesters)
        indexRows("", cache.rows)
        rank = cache.rank
        _state.value = _state.value.copy(
            loading = false,
            fromCache = true,
            cachedAt = cache.savedAt,
            needLogin = false,
        )
        publish()
    }

    /**
     * 按学期分桶。默认那一档（不指定学期）一次就把所有学期的成绩都拿回来了，
     * 这里顺手拆到各自的学期 key 下 —— 用户点学期胶囊时就不用再联网，直接出结果。
     */
    private fun indexRows(key: String, rows: List<GradeRecord>) {
        bySemester[key] = rows
        rows.groupBy { it.semester }
            .filterKeys { it.isNotBlank() }
            .forEach { (semester, list) -> bySemester[semester] = list }
    }

    private fun publish(clearError: Boolean = false) {
        val current = _state.value
        val rows = rowsOf(current.selected)
        _state.value = current.copy(
            semesters = semesters,
            rows = rows,
            summary = GradeStats.of(rows),
            rank = if (current.selected.isBlank()) rank else null,
            loading = false,
            error = if (clearError) null else current.error,
        )
    }

    private fun rowsOf(selected: String): List<GradeRecord> =
        if (selected.isBlank()) {
            bySemester.values.flatten()
                .distinctBy { "${it.course}|${it.semester}|${it.level}|${it.score}|${it.credit}" }
                .sortedWith(compareByDescending<GradeRecord> { it.semester }.thenBy { it.course })
        } else {
            bySemester[selected].orEmpty().sortedBy { it.course }
        }

    private fun mergeSemesters(
        old: List<GradeSemester>,
        new: List<GradeSemester>,
    ): List<GradeSemester> = (old + new).distinctBy { it.key }.sortedByDescending { it.key }

    private fun saveCache() {
        val rows = rowsOf(ZfGradeParser.ALL_SEMESTERS)
        if (rows.isEmpty()) return
        viewModelScope.launch {
            cacheStore.save(
                GradeCache(
                    savedAt = System.currentTimeMillis(),
                    semesters = semesters,
                    rows = rows,
                    rank = rank,
                ),
            )
        }
    }

    private fun friendly(error: Throwable): String = when (error) {
        is SessionExpiredException -> "登录状态已失效，请重新登录学校账号。"
        is java.net.UnknownHostException -> "连不上学校服务器，请确认手机能打开校园网。"
        is java.net.SocketTimeoutException -> "学校服务器响应超时，换个网络（或稍后）再试。"
        else -> error.message ?: "加载成绩失败，请重试。"
    }
}

@Composable
fun GradeScreen(
    onBack: () -> Unit,
    onLogin: () -> Unit,
    viewModel: GradeViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = LocalGlassPalette.current

    LaunchedEffect(Unit) { viewModel.start() }

    Column(Modifier.fillMaxSize()) {
        GlassScreenHeader(
            title = "成绩",
            subtitle = when {
                state.needLogin -> "会话已失效"
                state.semesters.isEmpty() -> "课程 · 绩点 · 排名"
                else -> "共 ${state.semesters.size} 个学期"
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
                        contentDescription = "刷新成绩",
                        tint = if (state.refreshing) palette.textTertiary else palette.accent,
                        modifier = Modifier.size(18.dp),
                    )
                }
            },
        )

        when {
            state.loading && state.isEmpty -> LoadingState()

            state.needLogin && state.isEmpty -> NeedLoginPane(
                palette = palette,
                message = state.error,
                onLogin = onLogin,
            )

            state.error != null && state.isEmpty -> MessagePane(
                palette = palette,
                title = "没拿到成绩",
                message = state.error.orEmpty(),
                actionLabel = "重试",
                onAction = viewModel::refresh,
            )

            else -> GradeContent(
                state = state,
                palette = palette,
                onSelect = viewModel::select,
                onRefresh = viewModel::refresh,
            )
        }
    }
}

@Composable
private fun GradeContent(
    state: GradeUiState,
    palette: GlassPalette,
    onSelect: (String) -> Unit,
    onRefresh: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 18.dp, end = 18.dp, top = 6.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item(key = "summary") {
            SummaryCard(state = state, palette = palette)
        }

        item(key = "semesters") {
            SemesterChips(
                semesters = state.semesters,
                selected = state.selected,
                palette = palette,
                onSelect = onSelect,
            )
        }

        val errorText = state.error
        if (errorText != null) {
            item(key = "error") {
                OfflineHint(
                    palette = palette,
                    message = errorText,
                    cachedAt = state.cachedAt,
                    onRefresh = onRefresh,
                )
            }
        }

        if (state.loadingSemester) {
            item(key = "loading") {
                Text(
                    text = "正在取这个学期的成绩…",
                    color = palette.textTertiary,
                    fontSize = 12.sp,
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }

        if (state.isEmpty) {
            item(key = "empty") {
                MessagePane(
                    palette = palette,
                    title = "这个学期还没有成绩",
                    message = "成绩是老师录入后才有的，过几天再来看看。",
                )
            }
        } else if (state.selected.isBlank()) {
            // 看「全部」时按学期分段，比在每一行里重复学期名干净得多
            state.rows.groupBy { it.semester }.forEach { (semester, list) ->
                item(key = "semester-$semester") {
                    SemesterHeader(
                        title = semester.ifBlank { "未标注学期" },
                        count = list.size,
                        palette = palette,
                    )
                }
                items(
                    items = list,
                    key = { "${it.course}-${it.semester}-${it.credit}-${it.score}-${it.level}" },
                ) { row ->
                    GradeRowCard(row = row, palette = palette, showSemester = false)
                }
            }
        } else {
            items(
                items = state.rows,
                key = { "${it.course}-${it.semester}-${it.credit}-${it.score}-${it.level}" },
            ) { row ->
                GradeRowCard(row = row, palette = palette, showSemester = false)
            }
        }
    }
}

/** 分段标题：一个学期一段，右侧给出门数。 */
@Composable
private fun SemesterHeader(title: String, count: Int, palette: GlassPalette) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 6.dp, top = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = gradeSemesterTitle(title),
            color = palette.textSecondary,
            fontSize = 12.sp,
            fontWeight = FontWeight.Medium,
        )
        Spacer(Modifier.width(6.dp))
        Text(text = "$count 门", color = palette.textTertiary, fontSize = 11.sp)
    }
}

/** 顶部的总绩点卡：一眼看到 GPA，右边是学分和门数，下面一行排名。 */
@Composable
private fun SummaryCard(state: GradeUiState, palette: GlassPalette) {
    val summary = state.summary
    GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 28.dp) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 18.dp)) {
            Row(verticalAlignment = Alignment.Bottom) {
                Column(Modifier.weight(1f)) {
                    Text(
                        text = if (state.selected.isBlank()) "总绩点" else "本学期绩点",
                        color = palette.textTertiary,
                        fontSize = 11.sp,
                    )
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = summary.gpaText,
                            color = if (summary.gpa == null) palette.textTertiary else palette.accent,
                            fontSize = 40.sp,
                            fontWeight = FontWeight.Bold,
                        )
                        Text(
                            text = " / 4.5",
                            color = palette.textTertiary,
                            fontSize = 12.sp,
                            modifier = Modifier.padding(bottom = 9.dp),
                        )
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = "${formatCredit(summary.earnedCredits)} 学分",
                        color = palette.textPrimary,
                        fontSize = 15.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = "${summary.courseCount} 门课",
                        color = palette.textTertiary,
                        fontSize = 11.sp,
                    )
                }
            }

            Spacer(Modifier.height(12.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val rank = state.rank
                if (state.selected.isBlank()) {
                    InfoPill(
                        text = if (rank != null) {
                            "${rank.label.ifBlank { "专业" }}排名 ${rank.display()}"
                        } else {
                            "排名 · 学校未提供"
                        },
                        palette = palette,
                        highlight = rank != null,
                    )
                } else {
                    InfoPill(text = "单学期统计", palette = palette, highlight = false)
                }
                if (summary.failedCount > 0) {
                    InfoPill(
                        text = "${summary.failedCount} 门未通过",
                        palette = palette,
                        highlight = false,
                        danger = true,
                    )
                }
                Spacer(Modifier.weight(1f))
                if (state.refreshing) {
                    Text("同步中…", color = palette.textTertiary, fontSize = 11.sp)
                } else if (state.fromCache && state.cachedAt > 0) {
                    Text(
                        text = "缓存 ${formatTime(state.cachedAt)}",
                        color = palette.textTertiary,
                        fontSize = 11.sp,
                    )
                }
            }
        }
    }
}

@Composable
private fun InfoPill(
    text: String,
    palette: GlassPalette,
    highlight: Boolean,
    danger: Boolean = false,
) {
    val shape = RoundedCornerShape(50)
    val color = when {
        danger -> palette.danger
        highlight -> palette.accent
        else -> palette.textSecondary
    }
    val bg = when {
        danger -> palette.danger.copy(alpha = 0.12f)
        highlight -> palette.accentSoft
        else -> palette.textTertiary.copy(alpha = 0.10f)
    }
    Text(
        text = text,
        color = color,
        fontSize = 11.sp,
        fontWeight = if (highlight || danger) FontWeight.Medium else FontWeight.Normal,
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .padding(horizontal = 10.dp, vertical = 5.dp),
    )
}

@Composable
private fun SemesterChips(
    semesters: List<GradeSemester>,
    selected: String,
    palette: GlassPalette,
    onSelect: (String) -> Unit,
) {
    if (semesters.isEmpty()) return
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(end = 4.dp),
    ) {
        item(key = "all") {
            SemesterChip(
                label = "全部",
                selected = selected.isBlank(),
                palette = palette,
                onClick = { onSelect(ZfGradeParser.ALL_SEMESTERS) },
            )
        }
        items(items = semesters, key = { it.key }) { semester ->
            SemesterChip(
                // 「2025-2026 学年 第 2 学期」太长，一个胶囊就吃掉半行；列表里用短名
                label = gradeSemesterShort(semester.key),
                selected = selected == semester.key,
                palette = palette,
                onClick = { onSelect(semester.key) },
            )
        }
    }
}

/** 选中态用颜色动画过渡，切换学期时不会"闪"一下。 */
@Composable
private fun SemesterChip(
    label: String,
    selected: Boolean,
    palette: GlassPalette,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(50)
    val bg by animateColorAsState(
        targetValue = if (selected) palette.accent else palette.glassFillTop,
        animationSpec = tween(180),
        label = "chipBg",
    )
    val fg by animateColorAsState(
        targetValue = if (selected) Color.White else palette.textSecondary,
        animationSpec = tween(180),
        label = "chipFg",
    )
    Text(
        text = label,
        color = fg,
        fontSize = 12.sp,
        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        maxLines = 1,
        modifier = Modifier
            .clip(shape)
            .background(bg)
            .border(1.dp, if (selected) Color.Transparent else palette.glassBorderTop, shape)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

/**
 * 单门课：左边课程名 + 学分/性质，右边成绩和绩点。
 * 不堆字段 —— 一屏能扫完 6 门课，比"每门课一张大卡片"更适合查完成绩的场景。
 */
@Composable
private fun GradeRowCard(row: GradeRecord, palette: GlassPalette, showSemester: Boolean = false) {
    val shape = RoundedCornerShape(20.dp)
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .glassSurface(palette, shape)
            .padding(horizontal = 16.dp, vertical = 13.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = row.course,
                    color = palette.textPrimary,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Spacer(Modifier.height(3.dp))
                Text(
                    text = metaOf(row, showSemester),
                    color = palette.textTertiary,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = row.score.ifBlank { row.level.ifBlank { "—" } },
                    color = scoreColor(row, palette),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = levelAndPointOf(row),
                    color = palette.textTertiary,
                    fontSize = 11.sp,
                )
            }
        }
    }
}

private fun metaOf(row: GradeRecord, showSemester: Boolean): String = buildList {
    row.credit?.let { add("${formatCredit(it)} 学分") }
    if (row.nature.isNotBlank()) add(row.nature)
    if (row.examType.isNotBlank()) add(row.examType)
    if (row.tag.isNotBlank()) add(row.tag)
    if (showSemester && row.semester.isNotBlank()) add(gradeSemesterShort(row.semester))
}.joinToString(" · ").ifBlank { "—" }

private fun levelAndPointOf(row: GradeRecord): String = buildList {
    if (row.level.isNotBlank()) add(row.level)
    row.point?.let { add("${formatPoint(it)} 绩点") }
    if (row.point == null && row.level.isBlank()) add("不计绩点")
}.joinToString(" · ").ifBlank { "—" }

@Composable
private fun scoreColor(row: GradeRecord, palette: GlassPalette): Color {
    val numeric = row.numericScore
    return when {
        numeric == null -> when (row.level.uppercase()) {
            "F" -> palette.danger
            "" -> palette.textTertiary
            else -> palette.textPrimary
        }
        numeric < 60 -> palette.danger
        numeric >= 90 -> palette.accent
        else -> palette.textPrimary
    }
}

@Composable
private fun OfflineHint(
    palette: GlassPalette,
    message: String,
    cachedAt: Long,
    onRefresh: () -> Unit,
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(palette.danger.copy(alpha = 0.10f))
            .padding(horizontal = 14.dp, vertical = 12.dp),
    ) {
        Text(
            text = message,
            color = palette.danger,
            fontSize = 12.sp,
        )
        Spacer(Modifier.height(6.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = if (cachedAt > 0) {
                    "下面显示的是 ${formatTime(cachedAt)} 的缓存"
                } else {
                    "下面显示的是上次的缓存"
                },
                color = palette.textSecondary,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f),
            )
            Text(
                text = "重试",
                color = palette.accent,
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                modifier = Modifier.clickable(onClick = onRefresh),
            )
        }
    }
}

@Composable
private fun NeedLoginPane(
    palette: GlassPalette,
    message: String?,
    onLogin: () -> Unit,
) {
    MessagePane(
        palette = palette,
        title = "还没登录教务系统",
        message = message ?: "成绩要从学校教务系统取，登录一次就行，之后看成绩不用再登。",
        actionLabel = "去登录",
        onAction = onLogin,
    )
}

@Composable
private fun MessagePane(
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

private fun formatCredit(value: Double): String =
    if (value % 1.0 == 0.0) value.toInt().toString() else String.format(Locale.US, "%.1f", value)

private fun formatPoint(value: Double): String = String.format(Locale.US, "%.1f", value)

private fun formatTime(epochMillis: Long): String =
    SimpleDateFormat("M-d HH:mm", Locale.CHINA).format(Date(epochMillis))

/** `2025-2026-1` → `25-26 第 1 学期`，塞进一行小字里。 */
private fun gradeSemesterShort(key: String): String {
    val parts = key.split('-')
    if (parts.size < 3) return key
    val a = parts[0].takeLast(2)
    val b = parts[1].takeLast(2)
    return "$a-$b 第 ${parts[2]} 学期"
}
