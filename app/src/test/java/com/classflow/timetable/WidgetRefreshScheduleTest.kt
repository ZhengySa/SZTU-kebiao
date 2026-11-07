package com.classflow.timetable

import com.classflow.timetable.widget.WidgetRefreshScheduler
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZonedDateTime

/**
 * 小组件午夜闹钟的触发时刻。
 *
 * 这曾经是"周三还显示周二的课"的现场：刷新链一旦断掉就永远停在旧的那一天。
 * 现在即使闹钟被推迟，也还有 updatePeriodMillis 兜底，但这个函数的边界
 * （今天 00:02 之前 / 之后）必须是对的，否则每天都会晚一天。
 */
class WidgetRefreshScheduleTest {

    private val zone = ZoneId.of("Asia/Shanghai")

    private fun at(day: Int, hour: Int, minute: Int): ZonedDateTime =
        ZonedDateTime.of(LocalDate.of(2026, 10, day).atTime(hour, minute), zone)

    @Test
    fun `夜里零点刚过时排在当天`() {
        val next = WidgetRefreshScheduler.nextTriggerAt(at(day = 28, hour = 0, minute = 0))
        assertEquals(LocalDate.of(2026, 10, 28), next.toLocalDate())
        assertEquals(0, next.hour)
        assertEquals(2, next.minute)
    }

    @Test
    fun `白天时排在第二天`() {
        val next = WidgetRefreshScheduler.nextTriggerAt(at(day = 28, hour = 13, minute = 22))
        assertEquals(LocalDate.of(2026, 10, 29), next.toLocalDate())
    }

    @Test
    fun `刚好到点时也不重复触发当天`() {
        val next = WidgetRefreshScheduler.nextTriggerAt(at(day = 28, hour = 0, minute = 2))
        assertEquals(LocalDate.of(2026, 10, 29), next.toLocalDate())
    }

    @Test
    fun `跨月也正确`() {
        val next = WidgetRefreshScheduler.nextTriggerAt(at(day = 31, hour = 23, minute = 59))
        assertEquals(LocalDate.of(2026, 11, 1), next.toLocalDate())
    }
}
