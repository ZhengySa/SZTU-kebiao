package com.classflow.timetable.core.time

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.ChronoUnit

/**
 * 周次换算。全部做成纯函数，方便单元测试（第二阶段的 WeekMathTest 会覆盖边界）。
 */
object WeekMath {

    /**
     * 开学日所在周 = 第 1 周。
     * 必须用 floorDiv：开学日之前的天数是负数，用 % 会得到 -1 导致「第 0 周」这种 bug。
     */
    fun weekOf(startDate: LocalDate, date: LocalDate): Int =
        Math.floorDiv(ChronoUnit.DAYS.between(startDate, date), 7L).toInt() + 1

    /** 第 [week] 周的周一。 */
    fun mondayOfWeek(startDate: LocalDate, week: Int): LocalDate =
        startDate.plusWeeks((week - 1).toLong())

    /** 第 [week] 周的第 [dayOfWeek] 天（1=周一）。 */
    fun dateOf(startDate: LocalDate, week: Int, dayOfWeek: Int): LocalDate =
        mondayOfWeek(startDate, week).plusDays((dayOfWeek - 1).toLong())

    /** 某天所在周的周一。 */
    fun mondayOf(date: LocalDate): LocalDate = date.with(DayOfWeek.MONDAY)
}
