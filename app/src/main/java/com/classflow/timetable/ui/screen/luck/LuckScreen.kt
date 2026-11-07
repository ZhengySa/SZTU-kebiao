package com.classflow.timetable.ui.screen.luck

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.classflow.timetable.core.time.AppClock
import com.classflow.timetable.data.local.LuckSaltStore
import com.classflow.timetable.domain.model.LuckMeter
import com.classflow.timetable.domain.model.LuckTier
import com.classflow.timetable.ui.theme.GlassCard
import com.classflow.timetable.ui.theme.GlassPalette
import com.classflow.timetable.ui.theme.GlassScreenHeader
import com.classflow.timetable.ui.theme.LocalGlassPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.time.LocalDate
import javax.inject.Inject
import kotlin.math.roundToInt

data class LuckUiState(
    val loaded: Boolean = false,
    val value: Int = 0,
    val tier: LuckTier = LuckTier.PLAIN,
    val line: String = "",
    val date: LocalDate = LocalDate.now(),
)

@HiltViewModel
class LuckViewModel @Inject constructor(
    private val saltStore: LuckSaltStore,
    private val clock: AppClock,
) : ViewModel() {

    private val _state = MutableStateFlow(LuckUiState())
    val state: StateFlow<LuckUiState> = _state.asStateFlow()

    init {
        reload()
    }

    /**
     * 重算一次。
     *
     * 每次进页面都调，因为 ViewModel 比页面活得久：手机在后台放一夜，
     * 第二天点进来还拿着昨天的分数就穿帮了。算法本身保证同一天结果不变。
     */
    fun reload() {
        val today = clock.today()
        val luck = LuckMeter.of(today, saltStore.salt())
        _state.value = LuckUiState(
            loaded = true,
            value = luck.value,
            tier = luck.tier,
            line = luck.line,
            date = today,
        )
    }
}

/**
 * 百宝箱 → 今日运气。
 *
 * 只有一个数，所以这一页只做三件事：把数摆大、给一句人话、把"这只是个随机数"写在明面上。
 * 数字从 0 走到结果（约 1.1 秒），比直接蹦出来更像"测"了一次。
 */
@Composable
fun LuckScreen(
    onBack: () -> Unit,
    viewModel: LuckViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val palette = LocalGlassPalette.current

    LaunchedEffect(Unit) { viewModel.reload() }

    var revealed by remember { mutableStateOf(false) }
    LaunchedEffect(state.loaded) {
        if (state.loaded) revealed = true
    }
    val progress by animateFloatAsState(
        targetValue = if (revealed) state.value / 100f else 0f,
        animationSpec = tween(durationMillis = 1100, easing = FastOutSlowInEasing),
        label = "luckProgress",
    )

    Column(Modifier.fillMaxSize()) {
        GlassScreenHeader(
            title = "百宝箱",
            subtitle = "一点没什么用、但挺好玩的东西",
            onBack = onBack,
        )

        /*
         * 内容比屏幕短的时候要竖直居中，否则下半屏是一片空的。
         * 垂直滚动本身做不到这件事：滚动容器给子项的高度是无界的，
         * Arrangement.Center 没有"容器高度"可以居中。
         *
         * 做法是先把可视高度取出来（BoxWithConstraints 放在滚动外面，
         * 否则它测到的 maxHeight 也是无界），再用 heightIn 把它设成 Column 的高度下限。
         * 注意 heightIn 必须写在 verticalScroll **后面**（也就是更内层），
         * 这样被撑高的是 Column 自己，Center 才有余量可以分。
         */
        BoxWithConstraints(Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .heightIn(min = this.maxHeight)
                    .padding(horizontal = 20.dp)
                    .padding(top = 8.dp, bottom = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                Text(
                    text = dateLabel(state.date) + " · 今日运气",
                    fontSize = 12.sp,
                    color = palette.textTertiary,
                )

                Spacer(Modifier.height(18.dp))

                val tint = tintOf(state.tier, palette)
                LuckGauge(
                    progress = progress,
                    tint = tint,
                    palette = palette,
                    modifier = Modifier.size(230.dp),
                ) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = (progress * 100).roundToInt().toString(),
                            fontSize = 54.sp,
                            fontWeight = FontWeight.Bold,
                            color = palette.textPrimary,
                        )
                        Text(
                            text = "/100",
                            fontSize = 12.sp,
                            color = palette.textTertiary,
                            modifier = Modifier.padding(start = 2.dp, bottom = 12.dp),
                        )
                    }
                    Spacer(Modifier.height(8.dp))
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(50))
                            .background(tint.copy(alpha = if (palette.isDark) 0.22f else 0.14f))
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                    ) {
                        Text(
                            text = state.tier.label,
                            fontSize = 11.5.sp,
                            fontWeight = FontWeight.Medium,
                            color = tint,
                        )
                    }
                }

                Spacer(Modifier.height(22.dp))

                GlassCard(Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
                    Text(
                        text = state.line,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 18.dp),
                        fontSize = 15.sp,
                        lineHeight = 24.sp,
                        color = palette.textPrimary,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(Modifier.height(16.dp))

                Text(
                    text = "同一天进来分数不会变，凌晨换一个。\n" +
                        "它就是个数，别拿它做决定。",
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
 * 刻度环：一条 270° 的底环 + 一段走到结果为止的彩色弧。
 *
 * 起点放在左下（135°），缺口留在正下方 —— 这样数字居中时，
 * 视觉重心落在缺口两侧，不会被环"顶"得上移。
 */
@Composable
private fun LuckGauge(
    progress: Float,
    tint: Color,
    palette: GlassPalette,
    modifier: Modifier = Modifier,
    content: @Composable ColumnScope.() -> Unit,
) {
    Box(modifier, contentAlignment = Alignment.Center) {
        Canvas(Modifier.fillMaxSize()) {
            val stroke = 13.dp.toPx()
            val inset = stroke / 2f + 3.dp.toPx()
            val arcSize = Size(size.width - inset * 2, size.height - inset * 2)
            val topLeft = Offset(inset, inset)
            val startAngle = 135f
            val fullSweep = 270f

            drawArc(
                color = palette.textPrimary.copy(alpha = 0.06f),
                startAngle = startAngle,
                sweepAngle = fullSweep,
                useCenter = false,
                topLeft = topLeft,
                size = arcSize,
                style = Stroke(width = stroke, cap = StrokeCap.Round),
            )
            if (progress > 0.004f) {
                drawArc(
                    brush = Brush.linearGradient(listOf(tint.copy(alpha = 0.40f), tint)),
                    startAngle = startAngle,
                    sweepAngle = fullSweep * progress.coerceIn(0f, 1f),
                    useCenter = false,
                    topLeft = topLeft,
                    size = arcSize,
                    style = Stroke(width = stroke, cap = StrokeCap.Round),
                )
            }
        }
        // 数字、"/100"、档位标签要竖着排；这里必须给个 Column，
        // 否则三个兄弟节点会在 Box 里叠在一起
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            content()
        }
    }
}

/** 分数越高越暖；60 分以下换成安静的青灰，扫一眼就知道"今天一般"。 */
private fun tintOf(tier: LuckTier, palette: GlassPalette): Color = when (tier) {
    LuckTier.EXCELLENT -> Color(0xFFE8963A)
    LuckTier.GREAT -> palette.accent
    LuckTier.LUCKY -> Color(0xFF15A0B8)
    LuckTier.PLAIN -> Color(0xFF6E86B8)
    LuckTier.MEH -> Color(0xFF8490A8)
    LuckTier.LOW -> Color(0xFF7C8497)
}

private val WEEKDAY = listOf("周一", "周二", "周三", "周四", "周五", "周六", "周日")

private fun dateLabel(date: LocalDate): String =
    "${date.monthValue} 月 ${date.dayOfMonth} 日 ${WEEKDAY[date.dayOfWeek.value - 1]}"
