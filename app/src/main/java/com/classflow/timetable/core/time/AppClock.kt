package com.classflow.timetable.core.time

import java.time.LocalDate
import java.time.LocalTime

/**
 * 可注入的「现在」。所有依赖当前时间的地方都走它，
 * 单元测试里替换成固定时间即可，不需要 mock 静态方法。
 */
interface AppClock {
    fun today(): LocalDate
    fun now(): LocalTime
    fun nowMinutes(): Int = now().hour * 60 + now().minute
}

class SystemAppClock : AppClock {
    override fun today(): LocalDate = LocalDate.now()
    override fun now(): LocalTime = LocalTime.now()
}
