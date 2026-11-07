package com.classflow.timetable.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * 午夜闹钟到点：把小组件刷成新的一天，并把明天的闹钟排上。
 *
 * 只做这两件事，不碰提醒（提醒有自己的 [com.classflow.timetable.reminder.ReminderScheduler]），
 * 免得跨天时同时拉起一堆 Room 查询。
 */
class WidgetDayTickReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val app = context.applicationContext

        // 先排下一次：万一协程里刷新失败，至少链条不会断
        WidgetRefreshScheduler.ensureScheduled(app)

        val pendingResult = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                WidgetRefreshScheduler.refreshNow(app)
            } catch (_: Throwable) {
                // 广播里不能崩；30 分钟轮询会再来一次
            } finally {
                pendingResult.finish()
            }
        }
    }

    companion object {
        const val ACTION_DAY_TICK = "com.classflow.timetable.action.WIDGET_DAY_TICK"
    }
}
