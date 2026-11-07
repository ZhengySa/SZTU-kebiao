package com.classflow.timetable.domain.usecase

import com.classflow.timetable.domain.model.CourseDetail
import com.classflow.timetable.domain.repository.TimetableRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

class GetCourseDetailUseCase @Inject constructor(
    private val repository: TimetableRepository,
) {
    operator fun invoke(courseId: Long): Flow<CourseDetail?> = combine(
        repository.observeCourse(courseId),
        repository.observeSessionsOfCourse(courseId),
        repository.observeActiveSemester(),
        repository.observeTimeSlots(),
    ) { course, sessions, semester, slots ->
        if (course == null || semester == null) {
            null
        } else {
            CourseDetail(
                course = course,
                sessions = sessions.sortedWith(compareBy({ it.dayOfWeek }, { it.startSlot })),
                semester = semester,
                timeSlots = slots,
            )
        }
    }
}
