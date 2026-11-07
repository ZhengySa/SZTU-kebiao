package com.classflow.timetable.widget

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.glance.appwidget.updateAll
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 「今日课表」小组件的刷新排程。
 *
 * 小组件显示的是**今天**的课，所以它必须在新的一天开始时变一次。
 * 之前只用 WorkManager 排了一个 delay 到 00:05 的一次性任务，在 MIUI/HyperOS 上很脆：
 * 应用被后台清理、被「省电策略」限制、或者用户从最近任务划掉之后，
 * 这个延迟任务会被直接丢掉，链断了就再也不刷新 —— 表现就是"周三了还显示周二的课"。
 *
 * 现在改成三层，任何一层活着都能把日期拉正：
 *
 * 1. **系统侧轮询（最可靠）**：`today_widget_info.xml` 的 `updatePeriodMillis = 30min`。
 *    由 AppWidget 服务主动找 App 要更新，进程可以被拉起来，不受厂商后台策略影响。
 * 2. **午夜精确闹钟（最快）**：每天 00:02 用 AlarmManager 打一枪，跨天立刻变对；
 *    打完自己排下一天。没有精确闹钟权限时降级成 1 小时窗口（有第 1 层兜底，慢一点无所谓）。
 * 3. **打开 App 时补刷**：用户一打开 App 就顺手把小组件刷一遍，见 MainActivity.onStart。
 */
object WidgetRefreshScheduler {

    /** 凌晨 2 分：留两分钟余量，避免 00:00 那一瞬系统日期还没完全切过去。 */
    private val REFRESH_AT: LocalTime = LocalTime.of(0, 2)

    /** 没有精确闹钟权限时的窗口：一小时内的任意时刻，反正有 30 分钟轮询兜底。 */
    private const val INEXACT_WINDOW_MILLIS = 60L * 60L * 1000L

    private const val REQUEST_CODE = 0x5744_0001 // 全局只有这一个闹钟，固定码即可

    /** logcat 过滤标签：`adb logcat -s ClassFlowWidget`。 */
    private const val TAG = "ClassFlowWidget"

    /** 排下一次午夜刷新。幂等：重复调用只是把同一个闹钟换成新的触发时间。 */
    fun ensureScheduled(context: Context) {
        val app = context.applicationContext
        val manager = app.getSystemService(AlarmManager::class.java) ?: return
        val triggerAt = nextTriggerAt(ZonedDateTime.now()).toInstant().toEpochMilli()
        val pending = pendingIntent(app)

        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            manager.canScheduleExactAlarms()

        runCatching {
            if (canExact) {
                // AllowWhileIdle：Doze 里也放行这一次，跨天是它存在的唯一理由
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerAt, pending)
            } else {
                manager.setWindow(AlarmManager.RTC_WAKEUP, triggerAt, INEXACT_WINDOW_MILLIS, pending)
            }
        }
    }

    /** 立刻刷新桌面上的所有「今日课表」实例。 */
    suspend fun refreshNow(context: Context) {
        // 这一行是排查"次日还是旧课表"的关键线索：能一眼看出刷新到底发生在什么时候、
        // 当时进程认为的"今天"和时区是什么。logcat 里过滤 ClassFlowWidget 即可。
        Log.i(TAG, "refreshNow today=${LocalDate.now()} tz=${ZoneId.systemDefault()}")
        runCatching { TodayWidget().updateAll(context.applicationContext) }
    }

    /** 供接收器用：排下一次 + 立刻刷新。 */
    fun refreshAndReschedule(context: Context) {
        ensureScheduled(context)
    }

    /** 下一次应该刷新的时刻：今天 00:02 还没到就是今天，否则明天。抽出来便于单元测试。 */
    internal fun nextTriggerAt(now: ZonedDateTime = ZonedDateTime.now()): ZonedDateTime {
        val today = now.toLocalDate().atTime(REFRESH_AT).atZone(now.zone)
        return if (today.isAfter(now)) today else today.plusDays(1)
    }

    private fun pendingIntent(context: Context): PendingIntent {
        val intent = Intent(context, WidgetDayTickReceiver::class.java).apply {
            action = WidgetDayTickReceiver.ACTION_DAY_TICK
        }
        return PendingIntent.getBroadcast(
            context,
            REQUEST_CODE,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}
