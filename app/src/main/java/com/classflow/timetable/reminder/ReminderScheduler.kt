package com.classflow.timetable.reminder

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import com.classflow.timetable.core.di.appEntryPoint
import com.classflow.timetable.domain.usecase.ScheduleBuilder
import com.classflow.timetable.domain.usecase.WeekResolver
import kotlinx.coroutines.flow.first
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/**
 * 课前提醒调度。
 *
 * 这里**故意不用 WorkManager**：WorkManager 的周期任务最小 15 分钟、且会被 Doze 推迟，
 * 做不到「上课前 10 分钟准时响」。
 * 用 AlarmManager 精确闹钟（用户拒绝精确闹钟权限时降级成 ±10 分钟窗口）才是正解。
 *
 * 调度窗口是「今天起的 8 天」，每次数据或设置变化都会整体重排（先全部取消再重排），
 * 这样不用维护复杂的增量 diff，也不会漏排。
 */
object ReminderScheduler {

    private const val PREFS_NAME = "classflow_reminder_state"
    private const val KEY_CODES = "scheduled_request_codes"
    private const val HORIZON_DAYS = 8L
    private const val WINDOW_MILLIS = 10 * 60 * 1000L

    /** 完整重排：取消旧闹钟 → 按当前设置与课表重新排。幂等，可随便调。 */
    suspend fun rescheduleAll(context: Context) {
        val app = context.applicationContext
        cancelAll(app)

        val entry = appEntryPoint(app)
        val settings = entry.settingsRepository().settings.first()
        if (!settings.reminderEnabled) return
        if (!NotificationHelper.hasPermission(app)) return
        NotificationHelper.ensureChannel(app)

        val repository = entry.timetableRepository()
        val semester = repository.getActiveSemester() ?: return
        val slots = repository.getTimeSlots()
        val sessions = repository.observeSchedule(semester.id).first()
        if (sessions.isEmpty()) return

        val now = LocalDateTime.now()
        val codes = mutableListOf<Int>()

        for (offset in 0..HORIZON_DAYS) {
            val date = LocalDate.now().plusDays(offset)
            val week = WeekResolver.resolve(semester, date).week
            val courses = ScheduleBuilder.dayCourses(sessions, slots, date.dayOfWeek.value, week)

            for (course in courses) {
                val triggerAt = date.atTime(course.startTime)
                    .minusMinutes(settings.reminderMinutes.toLong())
                if (!triggerAt.isAfter(now)) continue

                val code = requestCode(course.sessionId, date)
                val detail = buildString {
                    append(course.name)
                    append(" · ")
                    append(course.timeLabel)
                    if (!course.location.isNullOrBlank()) {
                        append(" · ")
                        append(course.location)
                    }
                }
                schedule(app, code, triggerAt, course.name, detail)
                codes += code
            }
        }

        saveCodes(app, codes)
    }

    /** 只取消，不重排（关闭提醒开关时用）。 */
    fun cancelAll(context: Context) {
        val app = context.applicationContext
        val manager = app.getSystemService(AlarmManager::class.java) ?: return
        loadCodes(app).forEach { code ->
            runCatching { manager.cancel(pendingIntent(app, code)) }
        }
        saveCodes(app, emptyList())
    }

    private fun schedule(
        context: Context,
        code: Int,
        triggerAt: LocalDateTime,
        title: String,
        detail: String,
    ) {
        val manager = context.getSystemService(AlarmManager::class.java) ?: return
        val triggerMillis = triggerAt.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
        val pending = pendingIntent(context, code, title, detail)

        val canExact = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            manager.canScheduleExactAlarms()

        runCatching {
            if (canExact) {
                manager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, triggerMillis, pending)
            } else {
                // 用户拒绝了「闹钟和提醒」权限时的降级方案：允许系统合并、推迟最多 10 分钟
                manager.setWindow(AlarmManager.RTC_WAKEUP, triggerMillis, WINDOW_MILLIS, pending)
            }
        }
    }

    private fun pendingIntent(
        context: Context,
        code: Int,
        title: String = "",
        detail: String = "",
    ): PendingIntent {
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra(ReminderReceiver.EXTRA_TITLE, title)
            putExtra(ReminderReceiver.EXTRA_DETAIL, detail)
            putExtra(ReminderReceiver.EXTRA_ID, code)
        }
        return PendingIntent.getBroadcast(
            context,
            code,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    /**
     * requestCode 必须稳定且不重复：
     * sessionId 唯一，乘 1000 再叠加一年中的第几天，同一节课不同日期不会撞码。
     */
    private fun requestCode(sessionId: Long, date: LocalDate): Int =
        ((sessionId % 1_000_000L) * 1_000L + date.dayOfYear).toInt()

    private fun saveCodes(context: Context, codes: List<Int>) {
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_CODES, codes.joinToString(","))
            .apply()
    }

    private fun loadCodes(context: Context): List<Int> =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .getString(KEY_CODES, "")
            .orEmpty()
            .split(',')
            .mapNotNull { it.trim().toIntOrNull() }
}
