package com.classflow.timetable.reminder

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/**
 * 闹钟到点的接收器：只负责弹一条通知。
 *
 * 注意不要在 onReceive 里做重活（它在主线程、有 10 秒预算），
 * 这里只发通知，属于最轻的操作。
 */
class ReminderReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val title = intent.getStringExtra(EXTRA_TITLE).orEmpty().ifBlank { "快要上课了" }
        val detail = intent.getStringExtra(EXTRA_DETAIL).orEmpty()
        val id = intent.getIntExtra(EXTRA_ID, title.hashCode())

        NotificationHelper.notifyReminder(
            context = context,
            notificationId = id,
            title = "即将上课：$title",
            body = detail.ifBlank { "点开查看今天的课表" },
        )
    }

    companion object {
        const val EXTRA_TITLE = "extra_title"
        const val EXTRA_DETAIL = "extra_detail"
        const val EXTRA_ID = "extra_id"
    }
}
