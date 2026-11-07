package com.classflow.timetable.domain.usecase

import com.classflow.timetable.domain.model.DayCourse
import com.classflow.timetable.domain.model.OwnerKey
import com.classflow.timetable.domain.model.Semester
import com.classflow.timetable.domain.repository.TimetableRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import java.time.LocalDate
import javax.inject.Inject

/** 某一周的全部课程，按 dayOfWeek(1..7) 分组。今日页与周课表共用。 */
class GetWeekCoursesUseCase @Inject constructor(
    private val repository: TimetableRepository,
) {
    operator fun invoke(
        semester: Semester,
        week: Int,
        ownerKey: String = OwnerKey.SELF,
    ): Flow<Map<Int, List<DayCourse>>> =
        combine(
            repository.observeSchedule(semester.id, ownerKey),
            repository.observeTimeSlots(),
        ) { sessions, slots ->
            ScheduleBuilder.weekCourses(sessions, slots, week)
        }
}

/**
 * 整个学期的每一周，key 是周次（1..totalWeeks），value 是该周按 dayOfWeek 分组的课程。
 *
 * 为什么一次给全，而不是「翻到哪周再查哪周」：
 * 周课表要能左右滑动翻周，翻页时再异步查库会让新一页先空一下再跳出来；
 * 而一个学期 20 周 × 30 来节课的过滤，总共也就几百次比较，比一次翻页的卡顿便宜得多。
 * 数据量本身也没问题：只存"哪节课在哪一周"的引用，不是把课复制 20 份。
 */
class GetAllWeekCoursesUseCase @Inject constructor(
    private val repository: TimetableRepository,
) {
    operator fun invoke(
        semester: Semester,
        ownerKey: String = OwnerKey.SELF,
    ): Flow<Map<Int, Map<Int, List<DayCourse>>>> =
        combine(
            repository.observeSchedule(semester.id, ownerKey),
            repository.observeTimeSlots(),
        ) { sessions, slots ->
            (1..semester.totalWeeks).associateWith { week ->
                ScheduleBuilder.weekCourses(sessions, slots, week)
            }
        }
}

/** 某一天的全部课程（按节次升序），给单元测试与将来的小组件复用。 */
class GetDayCoursesUseCase @Inject constructor(
    private val repository: TimetableRepository,
) {
    operator fun invoke(
        semester: Semester,
        date: LocalDate,
        ownerKey: String = OwnerKey.SELF,
    ): Flow<List<DayCourse>> =
        combine(
            repository.observeSchedule(semester.id, ownerKey),
            repository.observeTimeSlots(),
        ) { sessions, slots ->
            val week = WeekResolver.resolve(semester, date).week
            ScheduleBuilder.dayCourses(sessions, slots, date.dayOfWeek.value, week)
        }
}
