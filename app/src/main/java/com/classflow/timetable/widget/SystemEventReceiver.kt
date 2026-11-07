package com.classflow.timetable.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.classflow.timetable.reminder.ReminderScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.util.TimeZone

/**
 * 系统级事件 → 刷新小组件 + 重排提醒。
 *
 * 覆盖的场景：
 * - 开机（BOOT_COMPLETED）：AlarmManager 的闹钟在重启后全部丢失
 * - 手动改时间 / 改时区（TIME_SET / TIMEZONE_CHANGED）：所有提醒的绝对时刻都错了
 * - 应用升级（MY_PACKAGE_REPLACED）：包被替换后闹钟同样会丢
 *
 * DATE_CHANGED 仍然留在清单里，但 Android 8 之后它属于隐式广播，**多数 ROM 不会投递**，
 * 所以跨天不能指望它 —— 真正负责跨天的是 WidgetRefreshScheduler 的午夜闹钟
 * 加上 updatePeriodMillis=30min 的系统轮询。
 */
class SystemEventReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext
        Log.i("ClassFlowWidget", "system event: ${intent.action}")

        // 时区变了要顺手把本进程缓存的默认时区也清掉。
        // java.util.TimeZone 把默认时区缓存在静态字段里，进程不重启就不会刷新；
        // 不清的话，已经在跑的进程会继续按旧时区算"今天"，小组件就一直显示旧的那一天
        // —— 和"跨天不刷新"长得一模一样，但根因完全不同。
        if (intent.action == Intent.ACTION_TIMEZONE_CHANGED) {
            runCatching { TimeZone.setDefault(null) }
        }

        WidgetRefreshScheduler.ensureScheduled(app)

        // goAsync 让广播活到协程结束，避免 onReceive 返回后进程被回收导致刷新做一半
        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                WidgetRefreshScheduler.refreshNow(app)
                ReminderScheduler.rescheduleAll(app)
            } catch (_: Throwable) {
                // 广播里不能崩，失败下次系统事件会再来一遍
            } finally {
                pendingResult.finish()
            }
        }
    }
}
