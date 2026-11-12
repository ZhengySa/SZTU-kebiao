package com.classflow.timetable.reminder

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.classflow.timetable.MainActivity
import com.classflow.timetable.R

/** 通知渠道与权限判断集中在这里，避免各处零散判断导致「明明开了提醒却没声音」。 */
object NotificationHelper {

    const val CHANNEL_ID = "class_reminder"

    /** 番茄钟单独一个渠道：用户可以只留上课提醒、关掉番茄钟，互不牵连。 */
    const val POMODORO_CHANNEL_ID = "pomodoro"

    fun ensureChannel(context: Context) {
        ensureChannel(
            context = context,
            channelId = CHANNEL_ID,
            name = context.getString(R.string.reminder_channel_name),
            description = context.getString(R.string.reminder_channel_description),
        )
    }

    fun ensurePomodoroChannel(context: Context) {
        ensureChannel(
            context = context,
            channelId = POMODORO_CHANNEL_ID,
            name = context.getString(R.string.pomodoro_channel_name),
            description = context.getString(R.string.pomodoro_channel_description),
        )
    }

    private fun ensureChannel(context: Context, channelId: String, name: String, description: String) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(channelId) != null) return

        val channel = NotificationChannel(channelId, name, NotificationManager.IMPORTANCE_HIGH).apply {
            this.description = description
            enableVibration(true)
        }
        manager.createNotificationChannel(channel)
    }

    /**
     * Android 13+ 需要 POST_NOTIFICATIONS 运行时权限；
     * 低版本只要用户没在系统设置里关掉通知就算有权限。
     */
    fun hasPermission(context: Context): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS,
            ) == PackageManager.PERMISSION_GRANTED
            if (!granted) return false
        }
        return NotificationManagerCompat.from(context).areNotificationsEnabled()
    }

    fun notifyReminder(
        context: Context,
        notificationId: Int,
        title: String,
        body: String,
    ) {
        if (!hasPermission(context)) return
        ensureChannel(context)
        post(context, CHANNEL_ID, notificationId, title, body, NotificationCompat.CATEGORY_REMINDER)
    }

    /** 番茄钟结束。点通知只是回到 App —— 没有做「直接跳到番茄钟页」的深链。 */
    fun notifyPomodoro(context: Context, title: String, body: String) {
        if (!hasPermission(context)) return
        ensurePomodoroChannel(context)
        post(context, POMODORO_CHANNEL_ID, POMODORO_NOTIFICATION_ID, title, body, NotificationCompat.CATEGORY_ALARM)
    }

    private fun post(
        context: Context,
        channelId: String,
        notificationId: Int,
        title: String,
        body: String,
        category: String,
    ) {
        val contentIntent = PendingIntent.getActivity(
            context,
            0,
            Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(category)
            .setAutoCancel(true)
            .setContentIntent(contentIntent)
            .build()

        runCatching {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
        }
    }

    /** 固定 id：新的一段结束时覆盖上一条，抽屉里不会堆一列「专注结束」。 */
    private const val POMODORO_NOTIFICATION_ID = 2001
}
