package com.classflow.timetable.widget

import android.content.Context
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalContext
import androidx.glance.LocalSize
import androidx.glance.action.ActionParameters
import androidx.glance.action.actionStartActivity
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.ActionCallback
import androidx.glance.appwidget.action.actionRunCallback
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
// Glance 里有两套同名工厂函数：
//   androidx.glance.color.ColorProvider(day, night)  —— 跟随系统深浅色
//   androidx.glance.unit.ColorProvider(color)        —— 固定颜色
// 同时 import 会歧义，所以用别名区分。
import androidx.glance.color.ColorProvider as DayNightColor
import androidx.glance.unit.ColorProvider as FixedColor
import com.classflow.timetable.MainActivity
import com.classflow.timetable.R

/**
 * 「今日课表」桌面小组件。
 *
 * 视觉参照 iOS 的玻璃组件：一块半透明玻璃片 + 内部一张更亮的子卡 + 底部学期进度条。
 * 关于「为什么不是真模糊」见 `res/drawable/widget_glass_bg.xml` 的注释 ——
 * 桌面小组件拿不到壁纸像素，任何号称"实时模糊"的 AppWidget 都是静态图或伪色块。
 *
 * 尺寸策略（SizeMode.Responsive）：
 *  - 2x2 / 4x2（矮）：课程一行一条，密度优先，看得见今天的全部
 *  - 4x3 / 4x4（高）：加一张「下一节」高亮卡 + 学期进度条
 *
 * 所有文案都在渲染前算好（见 WidgetDataLoader），这里只负责排版，
 * 不出现第二份「单双周 / 节次时间」逻辑。
 */
class TodayWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SIZE_SHORT, SIZE_WIDE, SIZE_MEDIUM, SIZE_TALL),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snapshot = WidgetDataLoader.load(context)
        provideContent {
            WidgetContent(snapshot = snapshot, size = LocalSize.current)
        }
    }

    companion object {
        val SIZE_SHORT = DpSize(150.dp, 110.dp)
        val SIZE_WIDE = DpSize(250.dp, 110.dp)
        val SIZE_MEDIUM = DpSize(250.dp, 190.dp)
        val SIZE_TALL = DpSize(250.dp, 260.dp)
    }
}

/** 点小组件右上角的刷新图标：就地重新读库并重绘，不用打开 App。 */
class WidgetRefreshAction : ActionCallback {
    override suspend fun onAction(
        context: Context,
        glanceId: GlanceId,
        parameters: ActionParameters,
    ) {
        TodayWidget().update(context, glanceId)
    }
}

// ---------- 颜色 ----------

private val TitleColor = DayNightColor(day = Color(0xFF14161C), night = Color(0xFFF4F5F9))
private val BodyColor = DayNightColor(day = Color(0xFF2B2F38), night = Color(0xFFE2E4EC))
private val MutedColor = DayNightColor(day = Color(0xFF6C7484), night = Color(0xFFA5ABBB))
private val AccentColor = DayNightColor(day = Color(0xFF3D5AFE), night = Color(0xFF8FA4FF))
private val TrackColor = DayNightColor(day = Color(0x1F1B2540), night = Color(0x33FFFFFF))

/** 卡片在深色壁纸下要换一套玻璃底，否则白玻璃会把桌面压得发灰。 */
private fun isNightMode(context: Context): Boolean =
    (context.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
        Configuration.UI_MODE_NIGHT_YES

/** 矮版（2x2 / 4x2）最多几条；高版（4x3 / 4x4）最多几条。 */
private fun maxRows(size: DpSize, tall: Boolean): Int {
    if (!tall) return if (size.height < 130.dp) 2 else 3
    /*
     * 高版的条数必须按"剩余高度"算，不能拍脑袋给 3 或 4。
     * 高版里有「下一节」卡（约 54dp）、底部进度条（约 24dp）、内边距 22dp，
     * 4x3 的 190dp 里塞 3 节课之后进度条就被挤出卡片外 ——
     * RemoteViews 不会滚动，超出卡片的部分是**直接看不见**的，不会报错。
     */
    val available = size.height.value - TALL_CHROME_HEIGHT
    return (available / TALL_ROW_HEIGHT).toInt().coerceIn(1, 4)
}

/** 一行课 ≈ 22dp 文字 + 5dp 间隔。 */
private const val TALL_ROW_HEIGHT = 27f

/** 高版里课程列表之外的全部垂直开销，含「还有 N 节」那一行。 */
private const val TALL_CHROME_HEIGHT = 135f

@Composable
private fun WidgetContent(snapshot: WidgetSnapshot, size: DpSize) {
    val tall = size.height >= 190.dp
    val narrow = size.width < 200.dp
    val night = isNightMode(LocalContext.current)

    Box(
        modifier = GlanceModifier
            .fillMaxSize()
            .background(
                ImageProvider(
                    if (night) R.drawable.widget_glass_bg_dark else R.drawable.widget_glass_bg,
                ),
            )
            .clickable(actionStartActivity<MainActivity>()),
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .padding(horizontal = 13.dp, vertical = 11.dp),
        ) {
            TopBar(snapshot = snapshot, narrow = narrow)
            Spacer(GlanceModifier.height(if (tall) 9.dp else 5.dp))

            if (tall) {
                NextHighlight(snapshot = snapshot)
                Spacer(GlanceModifier.height(8.dp))
            }

            if (snapshot.courses.isEmpty()) {
                EmptyHint(snapshot)
            } else {
                val rows = maxRows(size, tall)
                val visible = snapshot.courses.take(rows)
                val overflow = snapshot.courses.size - visible.size
                /*
                 * 课程列表必须单独包一层 Column。
                 *
                 * RemoteViews 的线性布局**最多只接受 10 个子 View**，超出的会被 Glance 直接截掉
                 * （logcat 里那条 `Column container cannot have more than 10 elements`）。
                 * 之前 4 节课 + 4 个间隔 + 顶部两条 + 底部进度条一共 12 个孩子，
                 * 结果就是「课程一多，学期进度条就消失」——看起来像随机丢内容，其实是硬上限。
                 * 包一层之后外层最多 7 个，内层最多 4×2+1=9 个，都在上限内。
                 */
                Column(GlanceModifier.fillMaxWidth()) {
                    visible.forEach { course ->
                        CourseLine(course, compact = !tall)
                        Spacer(GlanceModifier.height(if (tall) 5.dp else 3.dp))
                    }
                    if (overflow > 0) {
                        Text(
                            text = "还有 $overflow 节",
                            style = TextStyle(color = AccentColor, fontSize = 8.5.sp),
                        )
                    }
                }
            }

            if (tall) {
                // 把进度条推到最底部（内容少时也不会浮在中间）
                Spacer(GlanceModifier.defaultWeight())
                ProgressFooter(snapshot = snapshot, width = size.width - 26.dp)
            }
        }
    }
}

@Composable
private fun TopBar(snapshot: WidgetSnapshot, narrow: Boolean) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // 品牌标识：一条强调色竖线 + 名字，比放 logo 图更省空间、也更清楚
        Box(
            modifier = GlanceModifier
                .width(2.5.dp)
                .height(11.dp)
                .cornerRadius(1.5.dp)
                .background(AccentColor),
        ) {}
        Spacer(GlanceModifier.width(5.dp))
        Text(
            text = "ClassFlow",
            style = TextStyle(
                color = TitleColor,
                fontSize = 10.5.sp,
                fontWeight = FontWeight.Bold,
            ),
            maxLines = 1,
        )
        Spacer(GlanceModifier.width(6.dp))
        Text(
            text = snapshot.dateText,
            style = TextStyle(color = MutedColor, fontSize = 9.sp),
            maxLines = 1,
        )
        Spacer(GlanceModifier.defaultWeight())
        if (!narrow) {
            Text(
                text = snapshot.weekText,
                style = TextStyle(color = MutedColor, fontSize = 9.sp),
                maxLines = 1,
            )
            Spacer(GlanceModifier.width(6.dp))
        }
        Image(
            provider = ImageProvider(R.drawable.ic_widget_refresh),
            contentDescription = "刷新",
            modifier = GlanceModifier
                .width(13.dp)
                .height(13.dp)
                .clickable(actionRunCallback<WidgetRefreshAction>()),
        )
    }
}

/** 高版专有：一张「下一节」卡，比逐条读列表更贴近"我现在该去干嘛"。 */
@Composable
private fun NextHighlight(snapshot: WidgetSnapshot) {
    val next = snapshot.next
    Column(
        modifier = GlanceModifier
            .fillMaxWidth()
            .cornerRadius(16.dp)
            .background(ImageProvider(R.drawable.widget_inner_card))
            .padding(horizontal = 11.dp, vertical = 9.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = GlanceModifier
                    .cornerRadius(50.dp)
                    .background(ImageProvider(R.drawable.widget_pill_accent))
                    .padding(horizontal = 6.dp, vertical = 1.dp),
            ) {
                Text(
                    text = if (next == null) "今天已结束" else "下一节",
                    style = TextStyle(
                        color = FixedColor(Color.White),
                        fontSize = 8.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                )
            }
            Spacer(GlanceModifier.width(7.dp))
            Text(
                text = if (next == null) "好好休息" else next.name,
                style = TextStyle(
                    color = TitleColor,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
        }
        if (next != null) {
            Spacer(GlanceModifier.height(3.dp))
            Text(
                text = buildString {
                    append("${next.shortSlot} 节 · ${next.timeLabel}")
                    next.location?.let { append(" · $it") }
                },
                style = TextStyle(color = BodyColor, fontSize = 9.sp),
                maxLines = 1,
            )
        }
    }
}

/** 课程行：色条 + 课程名 + 节次/时间。矮版压成一行，高版给两行。 */
@Composable
private fun CourseLine(course: WidgetCourse, compact: Boolean) {
    Row(
        modifier = GlanceModifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .width(2.5.dp)
                .height(if (compact) 14.dp else 20.dp)
                .cornerRadius(1.5.dp)
                .background(FixedColor(Color(course.colorArgb))),
        ) {}
        Spacer(GlanceModifier.width(7.dp))

        if (compact) {
            Text(
                text = course.name,
                style = TextStyle(
                    color = BodyColor,
                    fontSize = 10.5.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                text = "${course.shortSlot} · ${course.startLabel}",
                style = TextStyle(color = MutedColor, fontSize = 8.5.sp),
                maxLines = 1,
            )
        } else {
            Column(modifier = GlanceModifier.defaultWeight()) {
                Text(
                    text = course.name,
                    style = TextStyle(
                        color = BodyColor,
                        fontSize = 11.5.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                )
                Text(
                    text = buildString {
                        append("${course.shortSlot} 节 · ${course.timeLabel}")
                        course.location?.let { append(" · $it") }
                    },
                    style = TextStyle(color = MutedColor, fontSize = 8.5.sp),
                    maxLines = 1,
                )
            }
        }
    }
}

@Composable
private fun EmptyHint(snapshot: WidgetSnapshot) {
    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Text(
            text = if (snapshot.semesterMissing) "还没有课表" else "今天没有课",
            style = TextStyle(
                color = TitleColor,
                fontSize = 13.sp,
                fontWeight = FontWeight.Medium,
            ),
            maxLines = 1,
        )
        Spacer(GlanceModifier.height(2.dp))
        Text(
            text = if (snapshot.semesterMissing) "点这里去设置里同步课表" else "点这里看看本周安排",
            style = TextStyle(color = MutedColor, fontSize = 9.sp),
            maxLines = 1,
        )
    }
}

/**
 * 底部进度：左"学期进度"，右百分比，下面一条细进度条 + 周次。
 * 进度条自己用两个 Box 拼 —— Glance 的 LinearProgressIndicator 在不同启动器上
 * 高度表现不一致，自己算反而稳定。
 */
@Composable
private fun ProgressFooter(snapshot: WidgetSnapshot, width: Dp) {
    if (snapshot.totalWeeks <= 0) return

    Column(modifier = GlanceModifier.fillMaxWidth()) {
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = "学期进度",
                style = TextStyle(color = MutedColor, fontSize = 8.5.sp),
                maxLines = 1,
                modifier = GlanceModifier.defaultWeight(),
            )
            Text(
                text = snapshot.progressPercent,
                style = TextStyle(
                    color = AccentColor,
                    fontSize = 8.5.sp,
                    fontWeight = FontWeight.Bold,
                ),
                maxLines = 1,
            )
        }
        Spacer(GlanceModifier.height(3.dp))
        val fillWidth = (width * snapshot.progress)
            .coerceAtLeast(if (snapshot.progress > 0f) 6.dp else 0.dp)
            .coerceAtMost(width)
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .height(4.dp)
                .cornerRadius(2.dp)
                .background(TrackColor),
        ) {
            if (fillWidth > 0.dp) {
                Box(
                    modifier = GlanceModifier
                        .width(fillWidth)
                        .height(4.dp)
                        .cornerRadius(2.dp)
                        .background(ImageProvider(R.drawable.widget_progress_fill)),
                ) {}
            }
        }
        Spacer(GlanceModifier.height(2.dp))
        Text(
            text = snapshot.progressLabel,
            style = TextStyle(color = MutedColor, fontSize = 7.5.sp),
            maxLines = 1,
        )
    }
}
