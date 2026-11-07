package com.classflow.timetable.ui.screen.today

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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.classflow.timetable.core.owner.OwnerView
import com.classflow.timetable.core.time.AppClock
import com.classflow.timetable.core.time.WeekMath
import com.classflow.timetable.core.ui.LoadingState
import com.classflow.timetable.data.local.SztuAccountStore
import com.classflow.timetable.domain.model.DAY_LABELS
import com.classflow.timetable.domain.model.DayCourse
import com.classflow.timetable.domain.model.OwnerKey
import com.classflow.timetable.domain.repository.TimetableRepository
import com.classflow.timetable.domain.usecase.GetAllWeekCoursesUseCase
import com.classflow.timetable.domain.usecase.WeekResolver
import com.classflow.timetable.ui.components.CourseCard
import com.classflow.timetable.ui.components.OwnerSwitchPill
import com.classflow.timetable.ui.components.PartnerEmptyCard
import com.classflow.timetable.ui.theme.GlassCard
import com.classflow.timetable.ui.theme.GlassPalette
import com.classflow.timetable.ui.theme.LocalGlassPalette
import com.classflow.timetable.ui.theme.glassSurface
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import javax.inject.Inject

/**
 * 今日页的状态。
 *
 * 这里最关键的一个决定：**日子不是「一周 7 页」，而是整个学期连成一条**。
 *
 * 之前是 7 页（周一到周日），划到周日就撞到 Pager 的尽头，手感上就是「卡住」——
 * 明明日历是连续的，第 9 周周日往前一步当然该是第 10 周周一。
 * 现在页码直接对应学期里的第几天：page = (周次 - 1) * 7 + (星期 - 1)，
 * 于是周日再划一下，翻到的就是下周周一，周次、日期、课表一起换掉，
 * 中间没有任何需要「换一屏」的断点。
 */
data class TodayUiState(
    val loading: Boolean = true,
    val hasSemester: Boolean = true,
    /** 今天真正落在第几周（已夹进 1..totalWeeks）。 */
    val currentWeek: Int = 1,
    val totalWeeks: Int = 20,
    val semesterStart: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    /** 整个学期的课：weeks[周次][dayOfWeek 1..7]。一次算好，翻页只读内存。 */
    val weeks: Map<Int, Map<Int, List<DayCourse>>> = emptyMap(),
    /** 现在看的是谁的课表：[OwnerKey.SELF] 或 [OwnerKey.PARTNER]。 */
    val owner: String = OwnerKey.SELF,
    val partnerName: String = "",
) {
    val isPartner: Boolean get() = owner == OwnerKey.PARTNER

    /** 连续日序列的总页数（整学期天数）。 */
    val totalPages: Int get() = (totalWeeks * 7).coerceAtLeast(1)

    fun weekOfPage(page: Int): Int = (page / 7 + 1).coerceIn(1, totalWeeks)

    fun dayOfPage(page: Int): Int = page % 7 + 1

    fun dateOfPage(page: Int): LocalDate =
        WeekMath.mondayOfWeek(semesterStart, weekOfPage(page)).plusDays((dayOfPage(page) - 1).toLong())

    fun coursesOfPage(page: Int): List<DayCourse> =
        weeks[weekOfPage(page)]?.get(dayOfPage(page)).orEmpty()

    /** 今天那一页。开学前 / 结课后 currentWeek 已被夹过，这里再兜一层越界。 */
    val todayPage: Int
        get() = ((currentWeek - 1) * 7 + (today.dayOfWeek.value - 1)).coerceIn(0, totalPages - 1)

    /** 正在看 TA 的课表，但 TA 一门课都没有 → 显示导入引导而不是空白。 */
    val showPartnerEmpty: Boolean
        get() = isPartner && weeks.values.all { days -> days.values.all { it.isEmpty() } }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class TodayViewModel @Inject constructor(
    private val getAllWeeks: GetAllWeekCoursesUseCase,
    private val repository: TimetableRepository,
    private val accountStore: SztuAccountStore,
    private val ownerView: OwnerView,
    private val clock: AppClock,
) : ViewModel() {

    /**
     * 和 [com.classflow.timetable.ui.screen.week.WeekViewModel] 一样：
     * 只在这里算「今天是第几周」和整学期的课，**当前翻到第几天完全交给 Pager**。
     * 页码不该回流到这条链里，否则每划一天就重查一次库。
     */
    val uiState: StateFlow<TodayUiState> = combine(
        repository.observeActiveSemester(),
        ownerView.owner,
    ) { semester, owner -> semester to owner }
        .flatMapLatest { (semester, owner) ->
            if (semester == null) {
                flowOf(TodayUiState(loading = false, hasSemester = false, owner = owner))
            } else {
                val today = clock.today()
                val currentWeek = WeekResolver.resolve(semester, today).week
                    .coerceIn(1, semester.totalWeeks)
                combine(
                    getAllWeeks(semester, owner),
                    accountStore.partnerUsername,
                ) { weeks, partnerName ->
                    TodayUiState(
                        loading = false,
                        hasSemester = true,
                        currentWeek = currentWeek,
                        totalWeeks = semester.totalWeeks,
                        semesterStart = semester.startDate,
                        today = today,
                        weeks = weeks,
                        owner = owner,
                        partnerName = partnerName,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TodayUiState())

    /** 「我 / TA」一键切换：全局状态，周课表跟着一起切。 */
    fun toggleOwner() = ownerView.toggle()
}

@Composable
fun TodayScreen(
    onCourseClick: (Long) -> Unit,
    onAddCourse: () -> Unit,
    onOpenSync: () -> Unit,
    onOpenNbw: () -> Unit,
    onImportPartner: () -> Unit,
    viewModel: TodayViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val palette = LocalGlassPalette.current
    val haptics = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

    // 页码 → 日期 的换算只有这一处，页头、圆点、内容页都从它派生
    val pagerState = rememberPagerState(initialPage = state.todayPage) { state.totalPages }

    /*
     * 首帧进来时数据可能还没到（state 是默认值），初始页只能按默认值算。
     * 数据一到就补一次定位，只在**第一次**做：用 rememberSaveable 记住已经定位过，
     * 之后从课程详情返回、切 tab 回来都不会把用户正在看的周拽回今天。
     */
    var anchored by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(state.loading, state.hasSemester) {
        if (!anchored && !state.loading && state.hasSemester) {
            pagerState.scrollToPage(state.todayPage)
            anchored = true
        }
    }

    // 每翻一天给一次轻触感；跨周那一下更重，闭着眼也知道自己翻篇了
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.drop(1).collect { page ->
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
    }

    val ready = !state.loading && state.hasSemester
    val page = pagerState.currentPage
    val shownWeek = state.weekOfPage(page)
    val isCurrentWeek = shownWeek == state.currentWeek

    Box(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            TodayHeader(
                state = state,
                shownWeek = shownWeek,
                onSync = onOpenSync,
                onOpenNbw = onOpenNbw,
                onBackToCurrent = {
                    scope.launch { pagerState.animateScrollToPage(state.todayPage) }
                },
                onToggleOwner = { viewModel.toggleOwner() },
            )

            when {
                state.loading -> LoadingState()

                !state.hasSemester -> NoSemesterPane(palette = palette, onSync = onOpenSync)

                state.showPartnerEmpty -> Box(Modifier.weight(1f)) {
                    PartnerEmptyCard(palette = palette, onImport = onImportPartner)
                }

                else -> {
                    WeekSwitcher(
                        week = shownWeek,
                        palette = palette,
                        onPrev = {
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    (pagerState.currentPage - 7).coerceAtLeast(0),
                                )
                            }
                        },
                        onNext = {
                            scope.launch {
                                pagerState.animateScrollToPage(
                                    (pagerState.currentPage + 7).coerceAtMost(state.totalPages - 1),
                                )
                            }
                        },
                    )

                    DayDots(
                        selected = page % 7,
                        todayIndex = if (isCurrentWeek) state.today.dayOfWeek.value - 1 else -1,
                        palette = palette,
                    )

                    HorizontalPager(
                        state = pagerState,
                        modifier = Modifier.weight(1f),
                        // 左右各预组一页：滑动时下一天已经是量好尺寸的，不会划到一半才排版
                        beyondViewportPageCount = 1,
                        /*
                         * 翻页门槛和周课表一致：12%（约 43dp）就算翻过去。
                         * 默认的 0.5 要拖过半屏，轻扫一下又弹回原页 —— 那才叫「死板」。
                         * atMost(1) 保证一次手势最多走一天，甩太快也不会一下跳过好几天。
                         */
                        flingBehavior = PagerDefaults.flingBehavior(
                            state = pagerState,
                            pagerSnapDistance = PagerSnapDistance.atMost(1),
                            snapPositionalThreshold = 0.12f,
                        ),
                    ) { p ->
                        val week = state.weekOfPage(p)
                        val date = state.dateOfPage(p)
                        val courses = state.weeks[week]?.get(state.dayOfPage(p)).orEmpty()
                        val isToday = ready && date == state.today

                        Column(Modifier.fillMaxSize()) {
                            DateHeader(
                                label = DAY_LABELS[p % 7],
                                date = date,
                                isToday = isToday,
                                courseCount = courses.size,
                                palette = palette,
                            )
                            if (courses.isEmpty()) {
                                EmptyDay(isToday = isToday, palette = palette)
                            } else {
                                LazyColumn(
                                    contentPadding = PaddingValues(
                                        start = 18.dp, end = 18.dp, top = 2.dp, bottom = 92.dp,
                                    ),
                                    verticalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    items(items = courses, key = { it.sessionId }) { course ->
                                        CourseCard(
                                            course = course,
                                            onClick = { onCourseClick(course.courseId) },
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        AddCourseFab(
            palette = palette,
            onClick = onAddCourse,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .navigationBarsPadding()
                .padding(end = 20.dp, bottom = 22.dp),
        )
    }
}

/**
 * 顶部标题栏：左侧标题 + 周次，右侧「我 / TA」切换与同步入口。
 *
 * [shownWeek] 来自 Pager 当前页，不是 ViewModel 里的"今天在第几周"——
 * 所以翻到第 10 周周日时这里就写第 10 周，切换点只有 Pager 一个来源。
 */
@Composable
private fun TodayHeader(
    state: TodayUiState,
    shownWeek: Int,
    onSync: () -> Unit,
    onOpenNbw: () -> Unit,
    onBackToCurrent: () -> Unit,
    onToggleOwner: () -> Unit,
) {
    val palette = LocalGlassPalette.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 22.dp, end = 18.dp, top = 14.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                text = if (state.isPartner) "TA 的课表" else "今日课表",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
                color = palette.textPrimary,
            )
            Text(
                text = if (state.hasSemester) {
                    buildString {
                        if (state.isPartner && state.partnerName.isNotBlank()) {
                            append(state.partnerName).append(" · ")
                        }
                        append("第 ").append(shownWeek).append(" 周 / 共 ")
                            .append(state.totalWeeks).append(" 周")
                    }
                } else {
                    "未设置学期"
                },
                style = MaterialTheme.typography.bodySmall,
                color = if (state.isPartner) palette.accent else palette.textTertiary,
            )
        }

        if (state.hasSemester && !state.loading && shownWeek != state.currentWeek) {
            Text(
                text = "回到本周",
                fontSize = 12.sp,
                fontWeight = FontWeight.Medium,
                color = palette.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(palette.accentSoft)
                    .clickable(onClick = onBackToCurrent)
                    .padding(horizontal = 12.dp, vertical = 7.dp),
            )
            Spacer(Modifier.width(8.dp))
        }

        if (state.hasSemester) {
            OwnerSwitchPill(
                palette = palette,
                owner = state.owner,
                onClick = onToggleOwner,
                onLongClick = onToggleOwner,
            )
            Spacer(Modifier.width(8.dp))
        }

        NbwIconButton(palette = palette, onClick = onOpenNbw)
        Spacer(Modifier.width(8.dp))
        SyncIconButton(palette = palette, onClick = onSync)
    }
}

/** 公文通入口：喇叭图标，和右上角的同步按钮并排。 */
@Composable
private fun NbwIconButton(palette: GlassPalette, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .size(42.dp)
            .glassSurface(palette, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Campaign,
            contentDescription = "公文通",
            tint = palette.textSecondary,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun SyncIconButton(palette: GlassPalette, onClick: () -> Unit) {
    val shape = RoundedCornerShape(16.dp)
    Box(
        modifier = Modifier
            .size(42.dp)
            .glassSurface(palette, shape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Refresh,
            contentDescription = "从教务系统同步",
            tint = palette.accent,
            modifier = Modifier.size(20.dp),
        )
    }
}

@Composable
private fun WeekSwitcher(
    week: Int,
    palette: GlassPalette,
    onPrev: () -> Unit,
    onNext: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 4.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
    ) {
        val shape = RoundedCornerShape(50)
        Row(
            modifier = Modifier
                .clip(shape)
                .glassSurface(palette, shape)
                .padding(horizontal = 4.dp, vertical = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ArrowButton(Icons.Filled.ChevronLeft, "上一周", palette, onPrev)
            Text(
                text = "第 $week 周",
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                color = palette.textPrimary,
                modifier = Modifier.padding(horizontal = 10.dp),
            )
            ArrowButton(Icons.Filled.ChevronRight, "下一周", palette, onNext)
        }
    }
}

@Composable
private fun ArrowButton(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    palette: GlassPalette,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = palette.textSecondary,
            modifier = Modifier.size(19.dp),
        )
    }
}

/** 7 个小圆点当页码：当前页拉长成胶囊，今天用一个实心点标记。 */
@Composable
private fun DayDots(selected: Int, todayIndex: Int, palette: GlassPalette) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.Center,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        repeat(7) { index ->
            val isSelected = index == selected
            val isToday = index == todayIndex
            Box(
                modifier = Modifier
                    .padding(horizontal = 3.dp)
                    .clip(CircleShape)
                    .background(
                        when {
                            isSelected -> palette.accent
                            isToday -> palette.accent.copy(alpha = 0.45f)
                            else -> palette.textTertiary.copy(alpha = 0.28f)
                        },
                    )
                    .size(
                        width = if (isSelected) 16.dp else 5.dp,
                        height = 5.dp,
                    ),
            )
        }
    }
}

private val dateFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("M 月 d 日")

@Composable
private fun DateHeader(
    label: String,
    date: LocalDate,
    isToday: Boolean,
    courseCount: Int,
    palette: GlassPalette,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
            color = if (isToday) palette.accent else palette.textPrimary,
        )
        Text(
            text = date.format(dateFormatter),
            style = MaterialTheme.typography.bodySmall,
            color = palette.textTertiary,
        )
        if (isToday) {
            Text(
                text = "今天",
                fontSize = 10.sp,
                fontWeight = FontWeight.SemiBold,
                color = palette.accent,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(palette.accentSoft)
                    .padding(horizontal = 7.dp, vertical = 3.dp),
            )
        }
        Spacer(Modifier.weight(1f))
        Text(
            text = if (courseCount == 0) "无课" else "$courseCount 节课",
            style = MaterialTheme.typography.bodySmall,
            color = palette.textTertiary,
        )
    }
}

@Composable
private fun EmptyDay(isToday: Boolean, palette: GlassPalette) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            text = if (isToday) "今日无课" else "这天没有课",
            style = MaterialTheme.typography.titleMedium,
            color = palette.textSecondary,
            textAlign = TextAlign.Center,
        )
        Text(
            text = if (isToday) "好好休息一下" else "空着也挺好",
            style = MaterialTheme.typography.bodySmall,
            color = palette.textTertiary,
            textAlign = TextAlign.Center,
        )
    }
}

/** 没有学期时，直接把「登录教务系统」摆在正中间，而不是让用户自己找入口。 */
@Composable
private fun NoSemesterPane(palette: GlassPalette, onSync: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 26.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        GlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 28.dp) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 26.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text(
                    text = "还没有课表",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textPrimary,
                )
                Text(
                    text = "用学号登录深圳技术大学教务系统，自动把本学期课表抓过来，节次时间和开学日期也一起带过来。",
                    style = MaterialTheme.typography.bodyMedium,
                    color = palette.textSecondary,
                    textAlign = TextAlign.Center,
                )
                Spacer(Modifier.size(4.dp))
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(18.dp))
                        .background(
                            Brush.horizontalGradient(
                                listOf(palette.accent, palette.accent.copy(alpha = 0.72f)),
                            ),
                        )
                        .clickable(onClick = onSync)
                        .padding(horizontal = 22.dp, vertical = 13.dp),
                ) {
                    Text(
                        text = "登录教务系统导入",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = Color.White,
                    )
                }
            }
        }
    }
}

@Composable
private fun AddCourseFab(
    palette: GlassPalette,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .size(54.dp)
            .glassSurface(palette, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Filled.Add,
            contentDescription = "添加课程",
            tint = palette.accent,
            modifier = Modifier.size(24.dp),
        )
    }
}
