package com.classflow.timetable

import android.app.Application
import com.classflow.timetable.data.DataBootstrap
import com.classflow.timetable.reminder.NotificationHelper
import com.classflow.timetable.reminder.ReminderScheduler
import com.classflow.timetable.widget.WidgetRefreshScheduler
import dagger.hilt.android.HiltAndroidApp
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltAndroidApp
class ClassFlowApp : Application() {

    @Inject
    lateinit var bootstrap: DataBootstrap

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()

        // 通知渠道尽早建好，否则第一次提醒会因为渠道不存在而没有声音
        NotificationHelper.ensureChannel(this)
        // 小组件每日刷新排程（幂等，已存在会被替换成新的下一次）
        WidgetRefreshScheduler.ensureScheduled(this)

        appScope.launch {
            // 首次启动灌入 12 节默认时间 + 默认学期；幂等，重复调用无副作用
            bootstrap.ensureSeeded()
            // App 冷启动时把提醒重排一次，保证「装完就能用」「权限刚授予也能生效」
            runCatching { ReminderScheduler.rescheduleAll(this@ClassFlowApp) }
        }
    }
}
