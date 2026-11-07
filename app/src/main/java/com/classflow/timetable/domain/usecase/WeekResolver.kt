package com.classflow.timetable.domain.usecase

import com.classflow.timetable.core.time.WeekMath
import com.classflow.timetable.domain.model.Semester
import com.classflow.timetable.domain.model.WeekState
import java.time.LocalDate

/**
 * 当前周推导。纯函数，已被 WeekMathTest 覆盖。
 *
 * 规则：手动覆盖优先；否则按「开学日所在周 = 第 1 周」线性推导；
 * 开学前夹到第 1 周并标记 beforeTerm，学期后夹到最后一周并标记 afterTerm。
 */
object WeekResolver {

    fun resolve(semester: Semester, today: LocalDate): WeekState {
        val override = semester.weekOverride
        if (override != null) {
            return WeekState(
                week = override.coerceIn(1, semester.totalWeeks),
                isOverridden = true,
            )
        }

        val raw = WeekMath.weekOf(semester.startDate, today)
        return when {
            raw < 1 -> WeekState(week = 1, beforeTerm = true)
            raw > semester.totalWeeks -> WeekState(week = semester.totalWeeks, afterTerm = true)
            else -> WeekState(week = raw)
        }
    }
}
