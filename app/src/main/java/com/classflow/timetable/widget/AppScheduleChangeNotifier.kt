package com.classflow.timetable.widget

import android.content.Context
import androidx.glance.appwidget.updateAll
import com.classflow.timetable.domain.sync.ScheduleChangeNotifier
import com.classflow.timetable.reminder.ReminderScheduler
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 数据变更 → 小组件刷新 + 提醒重排。
 *
 * 去抖是必须的：CSV 导入一次会触发几百次 saveCourse，如果每次都 updateAll，
 * 系统会排队几百次 RemoteViews 更新，桌面直接卡住。
 * 这里用「合并信号」的方式：300ms 内的所有变更只算一次，
 * 并且正在处理时到来的信号会被合并掉，不会堆积。
 */
@Singleton
class AppScheduleChangeNotifier @Inject constructor(
    @ApplicationContext private val context: Context,
) : ScheduleChangeNotifier {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val signals = Channel<Unit>(Channel.CONFLATED)

    init {
        scope.launch {
            for (signal in signals) {
                delay(DEBOUNCE_MS)
                // 把等待期间堆积的信号全部吃掉，只刷新一次
                while (signals.tryReceive().isSuccess) {
                    // drain
                }
                runCatching { TodayWidget().updateAll(context) }
                runCatching { ReminderScheduler.rescheduleAll(context) }
            }
        }
    }

    override fun notifyScheduleChanged() {
        signals.trySend(Unit)
    }

    private companion object {
        const val DEBOUNCE_MS = 300L
    }
}
