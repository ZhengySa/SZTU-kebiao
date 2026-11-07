package com.classflow.timetable.domain.repository

import com.classflow.timetable.domain.model.Course
import com.classflow.timetable.domain.model.CourseSession
import com.classflow.timetable.domain.model.ImportMode
import com.classflow.timetable.domain.model.ImportSourceType
import com.classflow.timetable.domain.model.ScheduledSession
import com.classflow.timetable.domain.model.Semester
import com.classflow.timetable.domain.model.TimeSlot
import com.classflow.timetable.domain.imports.ImportOutcome
import com.classflow.timetable.domain.imports.ImportedCourse
import com.classflow.timetable.domain.model.OwnerKey
import kotlinx.coroutines.flow.Flow
import java.time.LocalDate

/**
 * 课表数据的唯一入口。
 * 读用 Flow（Room 自动推送变更，UI 不需要手动刷新），写用 suspend。
 */
interface TimetableRepository {

    // ---------- 学期 ----------
    fun observeActiveSemester(): Flow<Semester?>
    fun observeSemesters(): Flow<List<Semester>>
    suspend fun getActiveSemester(): Semester?
    suspend fun upsertSemester(semester: Semester): Long
    suspend fun setWeekOverride(semesterId: Long, week: Int?)
    suspend fun switchActiveSemester(semesterId: Long)
    suspend fun createSemester(name: String, startDate: LocalDate, totalWeeks: Int): Long

    // ---------- 节次 ----------
    fun observeTimeSlots(): Flow<List<TimeSlot>>
    suspend fun getTimeSlots(): List<TimeSlot>
    suspend fun upsertTimeSlot(slot: TimeSlot)
    suspend fun upsertTimeSlots(slots: List<TimeSlot>)
    suspend fun resetTimeSlots()
    /** 整体替换节次表（教务系统同步时用：节数可能是 15 节，和默认的 12 节不一样）。 */
    suspend fun replaceTimeSlots(slots: List<TimeSlot>)

    // ---------- 课程 / 时段 ----------
    /** [ownerKey] 见 [com.classflow.timetable.domain.model.OwnerKey]，默认读自己的课表。 */
    fun observeSchedule(
        semesterId: Long,
        ownerKey: String = OwnerKey.SELF,
    ): Flow<List<ScheduledSession>>
    fun observeCourse(courseId: Long): Flow<Course?>
    fun observeSessionsOfCourse(courseId: Long): Flow<List<CourseSession>>
    suspend fun getCourse(courseId: Long): Course?
    suspend fun getSessionsOfCourse(courseId: Long): List<CourseSession>
    /** 整体替换一门课的时段（新 id = 0 则新增）。返回课程 id。 */
    suspend fun saveCourse(course: Course, sessions: List<CourseSession>): Long
    suspend fun deleteCourse(courseId: Long)

    // ---------- 导入 ----------
    /**
     * 把解析好的课程写入当前学期。
     *
     * [ImportMode.OVERWRITE] 会先清空该学期全部课程；
     * [ImportMode.MERGE] 会跳过同名课程（同名视为同一门课，避免重复导入变成两份）。
     * 两种模式都会写一条 import_source 流水，便于日后追溯「这批课是哪来的」。
     */
    suspend fun importCourses(
        semesterId: Long,
        courses: List<ImportedCourse>,
        mode: ImportMode,
        sourceType: ImportSourceType,
        sourceName: String,
        ownerKey: String = OwnerKey.SELF,
    ): ImportOutcome

    /** 清掉某个归属在该学期的全部课程（情侣课表的「清除」用）。 */
    suspend fun clearCourses(semesterId: Long, ownerKey: String)

    /** 某个归属下有几门课，用来判断「TA 的课表导过没有」。 */
    suspend fun countCourses(semesterId: Long, ownerKey: String): Int
}
