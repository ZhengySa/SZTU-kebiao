package com.classflow.timetable.domain.usecase

import com.classflow.timetable.domain.model.Conflict
import com.classflow.timetable.domain.model.CourseSession
import com.classflow.timetable.domain.model.Semester
import com.classflow.timetable.domain.repository.TimetableRepository
import kotlinx.coroutines.flow.first
import javax.inject.Inject

/**
 * 冲突检测：同一天 + 节次区间相交 + 周次位图相交。
 * 三个条件全是常量级运算，即使一学期 1000 个时段也只需遍历一次。
 *
 * 注意：这里只做「提示」，不阻断保存 —— 调课、临时借教室都是真实需求。
 */
class DetectConflictsUseCase @Inject constructor(
    private val repository: TimetableRepository,
) {

    suspend operator fun invoke(
        semester: Semester,
        courseId: Long,
        candidates: List<CourseSession>,
    ): List<Conflict> {
        if (candidates.isEmpty()) return emptyList()
        val existing = repository.observeSchedule(semester.id).first()

        return candidates
            .filter { !it.weekMask.isEmpty() }
            .flatMap { candidate ->
                existing.asSequence()
                    .filter { it.courseId != courseId }
                    .filter { it.dayOfWeek == candidate.dayOfWeek }
                    .filter { it.startSlot <= candidate.endSlot && candidate.startSlot <= it.endSlot }
                    .filter { it.weekMask.intersects(candidate.weekMask) }
                    .map {
                        Conflict(
                            existingCourseName = it.courseName,
                            dayOfWeek = it.dayOfWeek,
                            startSlot = it.startSlot,
                            endSlot = it.endSlot,
                            weeksDescription = it.weekMask.describe(semester.totalWeeks),
                        )
                    }
                    .toList()
            }
            .distinctBy { "${it.existingCourseName}-${it.dayOfWeek}-${it.startSlot}-${it.endSlot}-${it.weeksDescription}" }
    }
}
