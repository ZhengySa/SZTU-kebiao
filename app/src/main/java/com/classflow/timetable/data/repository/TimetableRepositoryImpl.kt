package com.classflow.timetable.data.repository

import androidx.room.withTransaction
import com.classflow.timetable.data.local.AppDatabase
import com.classflow.timetable.data.local.CourseDao
import com.classflow.timetable.data.local.CourseEntity
import com.classflow.timetable.data.local.CourseSessionDao
import com.classflow.timetable.data.local.CourseSessionEntity
import com.classflow.timetable.data.local.DefaultData
import com.classflow.timetable.data.local.ImportSourceDao
import com.classflow.timetable.data.local.ImportSourceEntity
import com.classflow.timetable.data.local.SemesterDao
import com.classflow.timetable.data.local.SemesterEntity
import com.classflow.timetable.data.local.TimeSlotDao
import com.classflow.timetable.data.mapper.toDomain
import com.classflow.timetable.data.mapper.toEntity
import com.classflow.timetable.domain.imports.ImportOutcome
import com.classflow.timetable.domain.imports.ImportedCourse
import com.classflow.timetable.domain.model.Course
import com.classflow.timetable.domain.model.CourseSession
import com.classflow.timetable.domain.model.ImportMode
import com.classflow.timetable.domain.model.ImportSourceType
import com.classflow.timetable.domain.model.ScheduledSession
import com.classflow.timetable.domain.model.Semester
import com.classflow.timetable.domain.model.TimeSlot
import com.classflow.timetable.domain.model.WeekMask
import com.classflow.timetable.domain.model.WeekRuleType
import com.classflow.timetable.domain.repository.TimetableRepository
import com.classflow.timetable.domain.sync.ScheduleChangeNotifier
import com.classflow.timetable.domain.usecase.SessionMergePlan
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class TimetableRepositoryImpl @Inject constructor(
    private val db: AppDatabase,
    private val semesterDao: SemesterDao,
    private val timeSlotDao: TimeSlotDao,
    private val courseDao: CourseDao,
    private val sessionDao: CourseSessionDao,
    private val importSourceDao: ImportSourceDao,
    private val notifier: ScheduleChangeNotifier,
) : TimetableRepository {

    // ---------- 学期 ----------
    override fun observeActiveSemester(): Flow<Semester?> =
        semesterDao.observeActive().map { it?.toDomain() }

    override fun observeSemesters(): Flow<List<Semester>> =
        semesterDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getActiveSemester(): Semester? = semesterDao.getActive()?.toDomain()

    override suspend fun upsertSemester(semester: Semester): Long {
        val id = db.withTransaction {
            if (semester.id == 0L) {
                semesterDao.insert(semester.toEntity())
            } else {
                semesterDao.update(semester.toEntity())
                semester.id
            }
        }
        notifier.notifyScheduleChanged()
        return id
    }

    override suspend fun setWeekOverride(semesterId: Long, week: Int?) {
        semesterDao.setWeekOverride(semesterId, week, System.currentTimeMillis())
        notifier.notifyScheduleChanged()
    }

    override suspend fun switchActiveSemester(semesterId: Long) {
        db.withTransaction {
            semesterDao.clearActive()
            semesterDao.activate(semesterId)
        }
        notifier.notifyScheduleChanged()
    }

    override suspend fun createSemester(name: String, startDate: LocalDate, totalWeeks: Int): Long {
        val id = db.withTransaction {
            semesterDao.insert(
                SemesterEntity(
                    name = name,
                    startDate = startDate,
                    totalWeeks = totalWeeks,
                    isActive = false,
                )
            )
        }
        notifier.notifyScheduleChanged()
        return id
    }

    // ---------- 节次 ----------
    override fun observeTimeSlots(): Flow<List<TimeSlot>> =
        timeSlotDao.observeAll().map { list -> list.map { it.toDomain() } }

    override suspend fun getTimeSlots(): List<TimeSlot> = timeSlotDao.getAll().map { it.toDomain() }

    override suspend fun upsertTimeSlot(slot: TimeSlot) {
        if (slot.id == 0L) {
            timeSlotDao.insert(slot.toEntity())
        } else {
            timeSlotDao.update(slot.toEntity())
        }
        notifier.notifyScheduleChanged()
    }

    override suspend fun upsertTimeSlots(slots: List<TimeSlot>) {
        timeSlotDao.insertAll(slots.map { it.toEntity() })
        notifier.notifyScheduleChanged()
    }

    override suspend fun resetTimeSlots() {
        db.withTransaction {
            timeSlotDao.deleteAll()
            timeSlotDao.insertAll(DefaultData.defaultSlots())
        }
        notifier.notifyScheduleChanged()
    }

    override suspend fun replaceTimeSlots(slots: List<TimeSlot>) {
        if (slots.isEmpty()) return
        db.withTransaction {
            timeSlotDao.deleteAll()
            timeSlotDao.insertAll(slots.map { it.toEntity().copy(id = 0L) })
        }
        notifier.notifyScheduleChanged()
    }

    // ---------- 课程 / 时段 ----------
    override fun observeSchedule(
        semesterId: Long,
        ownerKey: String,
    ): Flow<List<ScheduledSession>> =
        sessionDao.observeSchedule(semesterId, ownerKey).map { rows -> rows.map { it.toDomain() } }

    override fun observeCourse(courseId: Long): Flow<Course?> =
        courseDao.observeById(courseId).map { it?.toDomain() }

    override fun observeSessionsOfCourse(courseId: Long): Flow<List<CourseSession>> =
        sessionDao.observeByCourse(courseId).map { list -> list.map { it.toDomain() } }

    override suspend fun getCourse(courseId: Long): Course? = courseDao.getById(courseId)?.toDomain()

    override suspend fun getSessionsOfCourse(courseId: Long): List<CourseSession> =
        sessionDao.getByCourse(courseId).map { it.toDomain() }

    /**
     * 保存课程 + 整体替换它的时段，全程在一个事务里：
     * 中途失败不会留下「课程存了但时段没存」的半条数据。
     *
     * 时段 id 会被保留（显式 id 插入），这样提醒的 PendingIntent requestCode 不会漂移。
     */
    override suspend fun saveCourse(course: Course, sessions: List<CourseSession>): Long {
        val courseId = db.withTransaction {
            val now = System.currentTimeMillis()
            val id = if (course.id == 0L) {
                courseDao.insert(course.toEntity(now))
            } else {
                courseDao.update(course.toEntity(now))
                course.id
            }

            sessionDao.deleteByCourse(id)
            if (sessions.isNotEmpty()) {
                sessionDao.insertAll(sessions.map { it.toEntity(id, now) })
            }
            id
        }
        notifier.notifyScheduleChanged()
        return courseId
    }

    override suspend fun deleteCourse(courseId: Long) {
        // 外键 ON DELETE CASCADE 会连带删掉 course_session
        courseDao.deleteById(courseId)
        notifier.notifyScheduleChanged()
    }

    // ---------- 导入 ----------

    override suspend fun importCourses(
        semesterId: Long,
        courses: List<ImportedCourse>,
        mode: ImportMode,
        sourceType: ImportSourceType,
        sourceName: String,
        ownerKey: String,
    ): ImportOutcome {
        val outcome = db.withTransaction {
            val now = System.currentTimeMillis()

            val existingByName = if (mode == ImportMode.MERGE) {
                courseDao.getAllInSemester(semesterId, ownerKey).associateBy { it.name }
            } else {
                emptyMap()
            }
            if (mode == ImportMode.OVERWRITE) {
                // 只清同一个归属的：给 TA 导入课表不能把自己的课删了
                courseDao.deleteBySemester(semesterId, ownerKey)
            }

            val sourceId = importSourceDao.insert(
                ImportSourceEntity(
                    type = sourceType.name,
                    displayName = sourceName,
                    importedAt = now,
                    mode = mode.name,
                    itemCount = courses.size,
                )
            )

            var insertedCourses = 0
            var insertedSessions = 0
            var updatedCourses = 0
            var repairedSessions = 0
            var skipped = 0

            courses.forEachIndexed { index, imported ->
                if (imported.sessions.isEmpty()) {
                    skipped++
                    return@forEachIndexed
                }

                val existing = existingByName[imported.name]
                if (existing != null) {
                    // 同名视为同一门课：不新增课程，但要把这次拿到的信息补上去。
                    //
                    // 这条以前是「直接跳过」，于是 1.0.2 之前导入的课永远修不好：
                    // 那些课的教室只存在课程级，时段的 location_override 是空的，
                    // 用户重新同步多少次都还是「地点待填」，只能自己一个个手输。
                    // 具体补哪些由 SessionMergePlan 决定（只补空地点、不删不改用户填过的）。
                    val oldSessions = sessionDao.getByCourse(existing.id)
                    val plan = SessionMergePlan.plan(
                        existing = oldSessions.map {
                            SessionMergePlan.Existing(
                                id = it.id,
                                dayOfWeek = it.dayOfWeek,
                                startSlot = it.startSlot,
                                endSlot = it.endSlot,
                                location = it.locationOverride,
                            )
                        },
                        incoming = imported.sessions,
                    )
                    var touched = false

                    if (existing.teacher.isNullOrBlank() && !imported.teacher.isNullOrBlank()) {
                        courseDao.update(existing.copy(teacher = imported.teacher, updatedAt = now))
                        touched = true
                    }

                    plan.patches.forEach { patch ->
                        oldSessions.firstOrNull { it.id == patch.sessionId }?.let { session ->
                            sessionDao.update(
                                session.copy(locationOverride = patch.location, updatedAt = now),
                            )
                            repairedSessions++
                            touched = true
                        }
                    }

                    plan.inserts.forEach { session ->
                        // 教务新排出来的时段，上次导入时还没有
                        sessionDao.insert(
                            CourseSessionEntity(
                                courseId = existing.id,
                                dayOfWeek = session.dayOfWeek,
                                startSlot = session.startSlot,
                                endSlot = session.endSlot,
                                weekRule = guessRule(session.weeks).name,
                                weekMask = WeekMask.fromWeeks(session.weeks).bits,
                                locationOverride = session.location,
                                createdAt = now,
                                updatedAt = now,
                            )
                        )
                        insertedSessions++
                        touched = true
                    }

                    // 所有时段现在地点一致的话，课程级地点也补上，列表页不用逐条翻
                    val rooms = sessionDao.getByCourse(existing.id)
                        .map { it.locationOverride?.trim()?.takeIf(String::isNotEmpty) }
                    val shared = rooms.distinct().singleOrNull()
                        ?.takeIf { rooms.isNotEmpty() && rooms.all { room -> room != null } }
                    if (shared != null && existing.defaultLocation != shared) {
                        courseDao.getById(existing.id)?.let { fresh ->
                            courseDao.update(fresh.copy(defaultLocation = shared, updatedAt = now))
                            touched = true
                        }
                    }

                    if (touched) updatedCourses++ else skipped++
                    return@forEachIndexed
                }

                val courseId = courseDao.insert(
                    CourseEntity(
                        semesterId = semesterId,
                        name = imported.name,
                        teacher = imported.teacher,
                        defaultLocation = imported.location,
                        colorArgb = IMPORT_PALETTE[index % IMPORT_PALETTE.size],
                        note = imported.note,
                        importSourceId = sourceId,
                        ownerKey = ownerKey,
                        createdAt = now,
                        updatedAt = now,
                    )
                )

                val sessions = imported.sessions.map { session ->
                    CourseSessionEntity(
                        courseId = courseId,
                        dayOfWeek = session.dayOfWeek,
                        startSlot = session.startSlot,
                        endSlot = session.endSlot,
                        weekRule = guessRule(session.weeks).name,
                        weekMask = WeekMask.fromWeeks(session.weeks).bits,
                        // 每个时段自己的教室；为空说明课表没写，回落到课程级地点
                        locationOverride = session.location,
                        createdAt = now,
                        updatedAt = now,
                    )
                }
                sessionDao.insertAll(sessions)
                insertedCourses++
                insertedSessions += sessions.size
            }

            ImportOutcome(
                insertedCourses = insertedCourses,
                insertedSessions = insertedSessions,
                skippedCourses = skipped,
                mode = mode,
                updatedCourses = updatedCourses,
                repairedSessions = repairedSessions,
            )
        }
        notifier.notifyScheduleChanged()
        return outcome
    }

    override suspend fun clearCourses(semesterId: Long, ownerKey: String) {
        db.withTransaction { courseDao.deleteBySemester(semesterId, ownerKey) }
        notifier.notifyScheduleChanged()
    }

    override suspend fun countCourses(semesterId: Long, ownerKey: String): Int =
        courseDao.countInSemester(semesterId, ownerKey)

    override suspend fun countSessionsMissingLocation(semesterId: Long, ownerKey: String): Int =
        sessionDao.countMissingLocation(semesterId, ownerKey)

    /** 只用于「编辑页回显」：把周次集合反推成最简单的那条规则。 */
    private fun guessRule(weeks: Set<Int>): WeekRuleType = when {
        weeks.isEmpty() -> WeekRuleType.CUSTOM
        weeks.all { it % 2 == 1 } -> WeekRuleType.ODD
        weeks.all { it % 2 == 0 } -> WeekRuleType.EVEN
        else -> WeekRuleType.CUSTOM
    }

    private companion object {
        /** 导入时按顺序分配颜色，保证同一批导入的课一眼能区分。 */
        val IMPORT_PALETTE = longArrayOf(
            0xFF3A5BF0, 0xFF00897B, 0xFFE65100, 0xFF8E24AA,
            0xFFC2185B, 0xFF00838F, 0xFF558B2F, 0xFF6D4C41,
        )
    }
}
