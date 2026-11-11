package com.classflow.timetable.ui.icon

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import androidx.annotation.DrawableRes
import com.classflow.timetable.R

/**
 * 桌面图标款式。
 *
 * Android 没有"运行时换图标"的 API —— manifest 里的 `android:icon` 是编译期定死的。
 * 唯一的正路是 activity-alias：每款图标声明一个 alias，每个 alias 都有自己的
 * icon 和 LAUNCHER intent-filter，切换时把用不上的 alias 禁用掉。
 *
 * 代价写在 [LauncherIconManager.apply] 的注释里，属于这套方案的固有缺陷。
 */
enum class LauncherIcon(
    /** manifest 里 activity-alias 的相对类名，必须和 AndroidManifest 一字不差。 */
    val relativeName: String,
    /** 设置页上显示的名字。 */
    val label: String,
    /** 一句话说明这个图形是什么。 */
    val description: String,
    /**
     * 预览用的前景 vector。
     *
     * 这里刻意用 `@drawable/ic_fg_*` 而不是 `@mipmap/ic_launcher_*`：
     * 后者是 adaptive-icon XML，`painterResource` 画不出来（它只认 BitmapDrawable 和
     * VectorDrawable），拿它当预览会直接崩。预览就自己拼：底色一填 + 前景一叠。
     */
    @param:DrawableRes val foregroundRes: Int,
    /** 图标底色，和 mipmap 里 `<background>` 引用的 color 保持一致。 */
    val backgroundColor: Long,
) {
    GRID(".icon.IconGrid", "格", "横是节次，竖是星期", R.drawable.ic_fg_grid, 0xFF1B1F2E),
    CLOCK(".icon.IconClock", "钟", "一个圆，两根指针", R.drawable.ic_fg_clock, 0xFF3A5BF0),
    BARS(".icon.IconBars", "条", "一天切成三段", R.drawable.ic_fg_bars, 0xFFF3F4F8),
    BOOK(".icon.IconBook", "本", "方框加一条书脊", R.drawable.ic_fg_book, 0xFF7C5CFF),
    DOT(".icon.IconDot", "点", "圆环套圆点，不解释", R.drawable.ic_fg_dot, 0xFF111318),
    CLASSIC(".icon.IconClassic", "课本", "原来那张课表", R.drawable.ic_fg_classic, 0xFFEEEDFE),
    ;

    fun componentName(context: Context) = ComponentName(context, context.packageName + relativeName)
}

object LauncherIconManager {

    /** manifest 里默认启用的是「格」，读不到状态时按它兜底。 */
    private val DEFAULT = LauncherIcon.GRID

    fun current(context: Context): LauncherIcon {
        val pm = context.packageManager
        return LauncherIcon.entries.firstOrNull { icon ->
            val component = icon.componentName(context)
            when (pm.getComponentEnabledSetting(component)) {
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
                PackageManager.COMPONENT_ENABLED_STATE_DISABLED -> false
                // DEFAULT 表示没被运行时改过，得回去看 manifest 里声明的 enabled
                else -> runCatching { pm.getActivityInfo(component, 0).enabled }
                    .getOrDefault(icon == DEFAULT)
            }
        } ?: DEFAULT
    }

    /**
     * 切到 [target]。
     *
     * 顺序不能反：**先启用新的，再禁用旧的**。反过来的话，从点下去到另一半代码执行之间
     * 有一次短暂的空窗期——如果这中间进程被系统杀掉（低内存、切换动画卡住），
     * 用户就会得到一个"装好的 App 在桌面上没有图标"的状态，只能去系统设置里翻。
     *
     * 不能避免的副作用：component 换了一个，launcher 会把它当成新装的 App，
     * 原来的图标位置、文件夹归属都会失效。这不是我们的 bug，activity-alias 这条路
     * 必然如此，所以设置页上要提示用户切换后手动把图标拖回原处。
     */
    fun apply(context: Context, target: LauncherIcon): Boolean = runCatching {
        val pm = context.packageManager
        pm.setComponentEnabledSetting(
            target.componentName(context),
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP,
        )
        LauncherIcon.entries
            .filter { it != target }
            .forEach { icon ->
                pm.setComponentEnabledSetting(
                    icon.componentName(context),
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    PackageManager.DONT_KILL_APP,
                )
            }
        true
    }.getOrDefault(false)
}
