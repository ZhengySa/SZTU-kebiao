package com.classflow.timetable.ui.screen.week

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerDefaults
import androidx.compose.foundation.pager.PagerSnapDistance
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
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
import com.classflow.timetable.domain.model.DayCourse
import com.classflow.timetable.domain.model.OwnerKey
import com.classflow.timetable.domain.model.TimeSlot
import com.classflow.timetable.domain.repository.TimetableRepository
import com.classflow.timetable.domain.usecase.GetAllWeekCoursesUseCase
import com.classflow.timetable.domain.usecase.WeekResolver
import com.classflow.timetable.ui.components.OwnerSwitchPill
import com.classflow.timetable.ui.components.PartnerEmptyCard
import com.classflow.timetable.ui.components.PartnerManageDialog
import com.classflow.timetable.ui.components.WeekGrid
import com.classflow.timetable.ui.theme.GlassCard
import com.classflow.timetable.ui.theme.GlassPalette
import com.classflow.timetable.ui.theme.LocalGlassPalette
import com.classflow.timetable.ui.theme.glassSurface
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import javax.inject.Inject

data class WeekUiState(
    val loading: Boolean = true,
    val hasSemester: Boolean = true,
    /** 正在看第几周（＝Pager 的页码 + 1）。 */
    val week: Int = 1,
    /** 今天真正所在的周，用来画时间线、判断要不要显示「回到本周」。 */
    val currentWeek: Int = 1,
    val totalWeeks: Int = 20,
    val semesterStart: LocalDate = LocalDate.now(),
    val today: LocalDate = LocalDate.now(),
    val slots: List<TimeSlot> = emptyList(),
    /** 整个学期每一周的课：key 是周次。滑到哪周直接用，不再查库。 */
    val weeks: Map<Int, Map<Int, List<DayCourse>>> = emptyMap(),
    /** 现在看的是谁的课表：[OwnerKey.SELF] 或 [OwnerKey.PARTNER]。 */
    val owner: String = OwnerKey.SELF,
    /** TA 的学号，用来显示「已导入 xxx 的课表」；没导过是空串。 */
    val partnerName: String = "",
) {
    val isPartner: Boolean get() = owner == OwnerKey.PARTNER

    val isCurrentWeek: Boolean get() = week == currentWeek

    /** 当前页那一周的周一。 */
    val weekStart: LocalDate get() = weekStartOf(week)

    fun weekStartOf(week: Int): LocalDate = WeekMath.mondayOfWeek(semesterStart, week)

    fun daysOf(week: Int): Map<Int, List<DayCourse>> = weeks[week].orEmpty()

    /** 正在看 TA 的课表，但 TA 一门课都没有 → 显示导入引导而不是空白网格。 */
    val showPartnerEmpty: Boolean
        get() = isPartner && weeks.values.all { days -> days.values.all { it.isEmpty() } }
}

@OptIn(ExperimentalCoroutinesApi::class)
@HiltViewModel
class WeekViewModel @Inject constructor(
    private val getAllWeeks: GetAllWeekCoursesUseCase,
    private val repository: TimetableRepository,
    private val accountStore: SztuAccountStore,
    private val ownerView: OwnerView,
    private val clock: AppClock,
) : ViewModel() {

    /** 用户手动翻到的周；null 表示"跟着今天走"。 */
    private val selectedWeek = MutableStateFlow<Int?>(null)

    /**
     * 重数据：只在「学期 / 课表 / 时间 / 谁」变化时重算。
     *
     * 注意 [selectedWeek] **不能**参与这条链：一旦参与，每一次左右翻页都会
     * 重新订阅 Room 并重建 20 周的课表，滑动手感会一顿一顿的。
     * 翻周只改一个 Int，在 [uiState] 里合并即可。
     */
    private val data: StateFlow<WeekUiState> = combine(
        repository.observeActiveSemester(),
        ownerView.owner,
    ) { semester, owner -> semester to owner }
        .flatMapLatest { (semester, owner) ->
            if (semester == null) {
                flowOf(WeekUiState(loading = false, hasSemester = false, owner = owner))
            } else {
                val today = clock.today()
                val currentWeek = WeekResolver.resolve(semester, today).week
                    .coerceIn(1, semester.totalWeeks)
                combine(
                    getAllWeeks(semester, owner),
                    repository.observeTimeSlots(),
                    accountStore.partnerUsername,
                ) { weeks, slots, partnerName ->
                    WeekUiState(
                        loading = false,
                        hasSemester = true,
                        week = currentWeek,
                        currentWeek = currentWeek,
                        totalWeeks = semester.totalWeeks,
                        semesterStart = semester.startDate,
                        today = today,
                        slots = slots,
                        weeks = weeks,
                        owner = owner,
                        partnerName = partnerName,
                    )
                }
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeekUiState())

    val uiState: StateFlow<WeekUiState> = combine(data, selectedWeek) { state, selected ->
        if (selected == null) state else state.copy(week = selected.coerceIn(1, state.totalWeeks))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WeekUiState())

    /** Pager 翻到第 [week] 周。 */
    fun showWeek(week: Int) {
        selectedWeek.value = week
    }

    fun backToCurrentWeek() {
        selectedWeek.value = null
    }

    /** 「我 / TA」一键切换：全局状态，今日页跟着一起切。 */
    fun toggleOwner() = ownerView.toggle()

    /** 清空 TA 的课表（课程 + 会话 + 学号），然后切回自己的课表。 */
    fun clearPartner() {
        viewModelScope.launch {
            repository.getActiveSemester()?.let { repository.clearCourses(it.id, OwnerKey.PARTNER) }
            accountStore.forgetPartner()
            ownerView.backToSelf()
        }
    }
}

/**
 * 周课表页。
 *
 * 布局预算（这是这一页最重要的设计约束）：
 * 手机竖屏约 890dp 高，扣掉状态栏 24 + 底部导航 ~110，只剩 ~750dp。
 * 页头压到 ~48dp（单行周次 + 小字日期区间），网格高度按剩余空间自动均分，
 * 于是 7 天 × 15 节全在一屏里。
 *
 * 翻周交互：**左右滑动**。上一版是页头右侧两个箭头，一天翻十几次的时候很别扭：
 * 手指要跨越半个屏幕去点一个 30dp 的按钮，还得先瞄准再点。滑动手势本身就在网格上，
 * 滑完手指停在原地就能接着看，这才是翻周这个动作的自然形态。
 */
@Composable
fun WeekScreen(
    onCourseClick: (Long) -> Unit,
    onAddCourse: () -> Unit,
    onImportPartner: () -> Unit,
    viewModel: WeekViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val palette = LocalGlassPalette.current

    var nowMinutes by remember { mutableIntStateOf(currentMinutes()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMinutes = currentMinutes()
            delay(60_000L)
        }
    }
    var managingPartner by remember { mutableStateOf(false) }
    // 「回到本周」的一次性请求：非 null 时 Pager 跳到今天所在的周，跳完清空
    var backRequest by remember { mutableStateOf<Int?>(null) }
    /*
     * 页头拖动 / 箭头的一次性「相对翻一页」请求（+1 / -1）。
     *
     * 这里**必须**传方向而不是目标周次。目标周次得靠 state.week 算，而 state.week
     * 是组合时捕获的快照：连点两下箭头，两次都读到同一个旧周次，第二次就白点了。
     * 传方向，由 Pager 拿自己实时的 currentPage 去加，才不会丢手势。
     */
    var stepRequest by remember { mutableStateOf<Int?>(null) }

    Column(
        Modifier
            .fillMaxSize()
            .statusBarsPadding(),
    ) {
        CompactHeader(
            state = state,
            palette = palette,
            onBackToThisWeek = {
                viewModel.backToCurrentWeek()
                backRequest = state.currentWeek
            },
            onAddCourse = onAddCourse,
            onToggleOwner = viewModel::toggleOwner,
            onManagePartner = { managingPartner = true },
            onPrevWeek = { stepRequest = -1 },
            onNextWeek = { stepRequest = 1 },
            onDragWeek = { dir -> stepRequest = dir.toInt() },
        )

        when {
            state.loading -> LoadingState()

            !state.hasSemester -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                GlassCard(modifier = Modifier.padding(horizontal = 26.dp)) {
                    Column(
                        modifier = Modifier.padding(horizontal = 22.dp, vertical = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Text(
                            "还没有学期",
                            fontSize = 16.sp,
                            fontWeight = FontWeight.SemiBold,
                            color = palette.textPrimary,
                        )
                        Text(
                            "去「设置 → 教务系统同步」登录一次，课表和开学日期会自动填好。",
                            fontSize = 12.sp,
                            color = palette.textSecondary,
                            textAlign = TextAlign.Center,
                            lineHeight = 17.sp,
                        )
                    }
                }
            }

            state.showPartnerEmpty -> Box(Modifier.weight(1f)) {
                PartnerEmptyCard(palette = palette, onImport = onImportPartner)
            }

            else -> WeekPager(
                state = state,
                nowMinutes = nowMinutes,
                onWeekShown = viewModel::showWeek,
                onCourseClick = { onCourseClick(it.courseId) },
                // 点了「回到本周」才用一次性信号让 Pager 跳过去。
                // 不能用 state.week 去 follow —— 那会让翻页方向反过来打自己（详情见 WeekPager 注释）
                backRequest = backRequest,
                onBackHandled = { backRequest = null },
                stepRequest = stepRequest,
                onStepHandled = { stepRequest = null },
                // weight(1f) 是「一屏放下」的前提：让 Pager 拿到"页面剩余的全部高度"，
                // 每页里的网格再按节次数均分。
                modifier = Modifier.weight(1f),
            )
        }
    }

    if (managingPartner) {
        PartnerManageDialog(
            palette = palette,
            partnerName = state.partnerName,
            onDismiss = { managingPartner = false },
            onReimport = {
                managingPartner = false
                onImportPartner()
            },
            onClear = {
                managingPartner = false
                viewModel.clearPartner()
            },
        )
    }
}

/**
 * 一周一页的横向 Pager。
 *
 * 只保留一个方向的状态流：**Pager 是唯一真相**。
 * 手指翻页 / 点「回到本周」/ 代码滚动，最后都是 Pager 的页码变了，再由它通知 ViewModel。
 *
 * 上一版反过来做（ViewModel 存当前周，Pager 去 follow），结果两边互相拽：
 * - 点「回到本周」→ VM 变 → Pager 动画 → 动画期间 currentPage 逐页变 → 回写 VM
 * - 期间手指一碰，VM 又被手指改写 → 动画目标被改 → 停半路，看着就是"划不动"
 *
 * 现在没有任何"程序化滚动"，`LaunchedEffect` 也**绝不**在 `isScrollInProgress` 时动手，
 * 手势永远优先。滚动途中只记页码，不停手。
 */
@Composable
private fun WeekPager(
    state: WeekUiState,
    nowMinutes: Int,
    onWeekShown: (Int) -> Unit,
    onCourseClick: (DayCourse) -> Unit,
    backRequest: Int?,
    onBackHandled: () -> Unit,
    stepRequest: Int?,
    onStepHandled: () -> Unit,
    modifier: Modifier = Modifier,
) {
    // 这一页只在数据就绪后才被组合，所以初始页可以直接用真实的当前周，不会先闪一下第 1 周
    /*
     * initialPage 只在**第一次组合**时用一次。
     * rememberPagerState 内部是 rememberSaveable(initialPage)，一旦 initialPage 变了
     * 就会重建 PagerState —— 而 initialPage 是从 state.week 算的，state.week 又由
     * Pager 自己上报，于是每次翻页都会重建 Pager、页码被重置、手势被打断。
     * 用 remember 把首帧的初始页钉死，后续翻页只改 PagerState 自身。
     */
    val startPage = remember { (state.week - 1).coerceAtLeast(0) }
    val pagerState = rememberPagerState(
        initialPage = startPage,
        pageCount = { state.totalWeeks },
    )
    val haptics = LocalHapticFeedback.current

    /*
     * 页码一变就上报，不等落定。
     * 早先这里用 snapshotFlow{currentPage}，但它只在新组合时启动，配合下面那条
     * "VM → Pager" 的 Effect 很容易自己打自己；改成单向监听 + 单向跳转后没这个问题了。
     */
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            onWeekShown((page + 1).coerceIn(1, state.totalWeeks))
        }
    }

    // 每落定一周给一次轻微触感：不用盯着页头也知道翻了几周
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.drop(1).collect {
            haptics.performHapticFeedback(HapticFeedbackType.SegmentTick)
        }
    }

    /*
     * 「回到本周」：只响应一次显式请求。
     *
     * 早先这里是 `LaunchedEffect(state.week)`，只要 VM 的周次变了就去滚 Pager ——
     * 而手指翻页也会让 VM 的周次变（Pager→VM 是实时的），于是这次滚动立刻把手指刚翻到的页
     * 拉回上一个周次：看到的现象是"划一下弹一下，只有连续快划才能往前挪"。
     * 现在翻页方向只有一个（Pager→VM），反方向的跳转只由这个一次性请求触发。
     */
    LaunchedEffect(backRequest, pagerState) {
        val week = backRequest ?: return@LaunchedEffect
        val target = (week - 1).coerceIn(0, (state.totalWeeks - 1).coerceAtLeast(0))
        pagerState.animateScrollToPage(target)
        onBackHandled()
    }

    /*
     * 页头拖动 / 箭头的一步翻页：**以 Pager 自己的当前页为基准**，不用 state.week。
     *
     * 曾经是 `viewModel.showWeek(state.week + dir)`，看起来没问题，实际是个陷阱：
     * 那个 lambda 捕获的是**组合那一刻的 state.week 快照**。连续拖动时每次读到的
     * 都是同一个旧值（比如恒为 1），于是每次都跳向 0 或 2，中间的翻页全被吃掉 ——
     * 现象就是"拖了好几下，只动了一下甚至一动不动"。
     * 这里直接读 pagerState.currentPage（永远是最新值）。
     */
    LaunchedEffect(stepRequest, pagerState) {
        val dir = stepRequest ?: return@LaunchedEffect
        // 以「目标页」为基准算，而不是 currentPage：连点时上一次的动画可能还没走完，
        // currentPage 还停在半路，用它会算出和上一帧相同的目标，于是这一次点击被吃掉。
        val base = pagerState.targetPage.takeIf { pagerState.isScrollInProgress }
            ?: pagerState.currentPage
        val target = stepPage(base, dir, state.totalWeeks)
        if (target != pagerState.currentPage) {
            pagerState.animateScrollToPage(target)
        }
        onStepHandled()
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier,
        // 预先组合左右各一页：滑到一半时下一页已经量好尺寸，
        // 不会在中途才第一次布局（那种"滑到一半卡一下"最破坏手感）
        beyondViewportPageCount = 1,
        /*
         * 翻页门槛。
         *
         * 系统的默认值是「拖过半页才认」（snapPositionalThreshold = 0.5）：
         * 360dp 宽的屏要滑过 180dp，慢一点、短一点抬手就弹回原页。
         * 日历式的翻周不该这么沉 —— 轻扫一下就过去，才叫"翻"。
         *
         * 12% 大约 43dp，比触摸阈值（约 8dp）高得多，点课程不会误翻；
         * 一次最多翻一页（atMost(1)），甩太快也不会一下跳五周。
         */
        flingBehavior = PagerDefaults.flingBehavior(
            state = pagerState,
            pagerSnapDistance = PagerSnapDistance.atMost(1),
            snapPositionalThreshold = 0.12f,
        ),
    ) { page ->
        val week = page + 1
        WeekGrid(
            slots = state.slots,
            days = state.daysOf(week),
            weekStart = state.weekStartOf(week),
            today = state.today,
            // 时间线只画在"今天真正所在的那一周"上
            showTimeLine = week == state.currentWeek,
            nowMinutes = nowMinutes,
            onCourseClick = onCourseClick,
            modifier = Modifier
                .fillMaxSize()
                .padding(start = 8.dp, end = 8.dp, bottom = 6.dp),
        )
    }
}

/**
 * 页头。
 *
 * 「能滑」这件事必须被看见：网格本身长得像一张静止的表格，用户不会想到去划它。
 * 所以页头这里放一条**可拖动**的周次胶囊（左右拖也能翻周），
 * 两端各一个箭头（点一下就翻一周）。三条路通向同一件事，怎么试着都能翻。
 */
@Composable
private fun CompactHeader(
    state: WeekUiState,
    palette: GlassPalette,
    onBackToThisWeek: () -> Unit,
    onAddCourse: () -> Unit,
    onToggleOwner: () -> Unit,
    onManagePartner: () -> Unit,
    onPrevWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onDragWeek: (Float) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            // 整条页头都是"翻周条"：左右拖空白处 / 拖标题 / 拖日期文字都能翻
            .then(if (state.hasSemester) Modifier.horizontalDrag(onDragEnd = onDragWeek) else Modifier)
            .padding(start = 16.dp, end = 10.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 整块左侧区域也能左右拖动翻周；注意这个 Modifier 要放在 Column 上，
        // 放在里面某个 Text 上会被旁边的「回到本周」按钮吃掉右滑的手势
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = if (state.hasSemester) "第 ${state.week} 周" else "周课表",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    color = palette.textPrimary,
                )
                if (state.hasSemester && !state.isCurrentWeek) {
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "回到本周",
                        fontSize = 10.5.sp,
                        fontWeight = FontWeight.Medium,
                        color = palette.accent,
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(palette.accentSoft)
                            .clickable(onClick = onBackToThisWeek)
                            .padding(horizontal = 8.dp, vertical = 3.dp),
                    )
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (state.hasSemester) {
                    WeekStepArrow(
                        icon = Icons.Filled.ChevronLeft,
                        description = "上一周",
                        enabled = state.week > 1,
                        palette = palette,
                        onClick = onPrevWeek,
                    )
                    Text(
                        text = if (state.isPartner) {
                            "TA 的课表 · ${rangeText(state.weekStart)}"
                        } else {
                            "${rangeText(state.weekStart)} · 共 ${state.totalWeeks} 周"
                        },
                        fontSize = 9.5.sp,
                        color = if (state.isPartner) palette.accent else palette.textTertiary,
                        maxLines = 1,
                    )
                    WeekStepArrow(
                        icon = Icons.Filled.ChevronRight,
                        description = "下一周",
                        enabled = state.week < state.totalWeeks,
                        palette = palette,
                        onClick = onNextWeek,
                    )
                } else {
                    Text(
                        text = "未设置学期",
                        fontSize = 9.5.sp,
                        color = palette.textTertiary,
                        maxLines = 1,
                    )
                }
            }
        }

        if (state.hasSemester) {
            OwnerSwitchPill(
                palette = palette,
                owner = state.owner,
                onClick = onToggleOwner,
                onLongClick = onManagePartner,
            )
            Spacer(Modifier.width(6.dp))
        }

        Box(
            modifier = Modifier
                .size(32.dp)
                .glassSurface(palette, CircleShape)
                .clickable(onClick = onAddCourse),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                Icons.Filled.Add,
                contentDescription = "添加课程",
                tint = palette.accent,
                modifier = Modifier.size(18.dp),
            )
        }
    }
}

/** 页头的小箭头：点一下翻一周。宽度压到 18dp，只占两三个字符的位置但不低于触摸下限。 */
@Composable
private fun WeekStepArrow(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    description: String,
    enabled: Boolean,
    palette: GlassPalette,
    onClick: () -> Unit,
) {
    Box(
        modifier = Modifier
            .size(width = 20.dp, height = 22.dp)
            .clip(CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            icon,
            contentDescription = description,
            tint = palette.textTertiary.copy(alpha = if (enabled) 0.95f else 0.3f),
            modifier = Modifier.size(14.dp),
        )
    }
}

/** 10/26 - 11/01，比 "10月26日 - 11月1日" 少一半字符，小字下更好读。 */
private fun rangeText(monday: LocalDate): String {
    val sunday = monday.plusDays(6)
    fun f(d: LocalDate) = "${d.monthValue}/${d.dayOfMonth.toString().padStart(2, '0')}"
    return "${f(monday)} - ${f(sunday)}"
}

private fun currentMinutes(): Int {
    val now = LocalTime.now()
    return now.hour * 60 + now.minute
}

/**
 * 横向拖动翻周。
 *
 * 只在**明确是横向**的拖动上抢手势（`slop` 之后的第一个方向决定），
 * 且阈值放到 48dp —— 点按、轻微抖动都不会误翻，明确地"甩一下"才翻一周。
 * 手势消费掉事件，所以它不会漏给下面的网格。
 */
/**
 * 「相对当前页翻一页」的纯逻辑：给定基准页、方向和总周数，算出目标页（0 基）。
 *
 * 抽成纯函数是为了能脱离 Compose 测：
 * 这一条曾经出过 bug —— 拖动/箭头提交的是**目标周次**（`state.week + dir`），
 * 而 `state.week` 是组合时的快照，连点时每次读到同一个旧值，翻页被吞掉。
 * 改成传「方向」之后，基准页必须来自 Pager 自己的实时状态，边界也由这里统一 clamp。
 */
internal fun stepPage(base: Int, dir: Int, totalWeeks: Int): Int {
    val last = (totalWeeks - 1).coerceAtLeast(0)
    return (base + dir).coerceIn(0, last)
}

private fun Modifier.horizontalDrag(onDragEnd: (Float) -> Unit): Modifier = this.then(    Modifier.pointerInput(Unit) {
        var total = 0f
        val threshold = 48.dp.toPx()
        detectHorizontalDragGestures(
            onDragStart = { total = 0f },
            onDragEnd = {
                // ⚠️ 这里绝不能清零 total。
                // detectHorizontalDragGestures 在手指抬起时**也会**走一遍 cancel 分支，
                // 如果在 onDragCancel 里清零，onDragEnd 读到的累计位移就永远是 0，
                // 页头拖动一次都翻不动（这个 bug 实际发生过，见 docs/18）。
                if (total <= -threshold) onDragEnd(-1f)
                else if (total >= threshold) onDragEnd(1f)
            },
            // 真被打断（多指、系统手势抢走）时才丢弃累计位移
            onDragCancel = { total = 0f },
        ) { _, dragAmount ->
            total += dragAmount
        }
    },
)
