package com.classflow.timetable.widget

import android.appwidget.AppWidgetManager
import android.content.Context
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver

/**
 * 小组件在系统侧的入口。
 * 系统在「首次添加 / 尺寸变化 / 主题变化 / updatePeriodMillis 到点（30 分钟）」时会找这个 Receiver。
 */
class TodayWidgetReceiver : GlanceAppWidgetReceiver() {

    override val glanceAppWidget: GlanceAppWidget = TodayWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        // 第一次被添加到桌面时，把午夜刷新闹钟排上
        WidgetRefreshScheduler.ensureScheduled(context)
    }

    override fun onUpdate(
        context: Context,
        appWidgetManager: AppWidgetManager,
        appWidgetIds: IntArray,
    ) {
        super.onUpdate(context, appWidgetManager, appWidgetIds)
        // 尺寸变化、系统轮询、从备份恢复（onEnabled 不会被调用）都会走到这里，
        // 顺手补一次排程，保证闹钟链在任何进入方式下都存在
        WidgetRefreshScheduler.ensureScheduled(context)
    }
}
