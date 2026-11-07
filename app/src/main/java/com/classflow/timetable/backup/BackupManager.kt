package com.classflow.timetable.backup

import androidx.room.withTransaction
import com.classflow.timetable.data.local.AppDatabase
import com.classflow.timetable.data.local.CourseEntity
import com.classflow.timetable.data.local.CourseSessionEntity
import com.classflow.timetable.data.local.SemesterEntity
import com.classflow.timetable.data.local.TimeSlotEntity
import com.classflow.timetable.domain.model.ThemeMode
import com.classflow.timetable.domain.repository.SettingsRepository
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.time.LocalDate
import javax.inject.Inject
import javax.inject.Singleton

/**
 * JSON 备份与恢复。
 *
 * 设计取舍：
 * - 备份用「全量快照」，不做增量。课表数据量很小（几百条），全量最不容易出错。
 * - 恢复是「整体替换」：先清空再写入。这样恢复到哪份文件就是哪份状态，
 *   不会出现「恢复了一半、旧数据还在」的中间状态。
 * - 整个过程在一个 Room 事务里（清空用 clearAllTables，它在事务外自己处理），
 *   写入失败会整体回滚。
 */
@Singleton
class BackupManager @Inject constructor(
    private val db: AppDatabase,
    private val settingsRepository: SettingsRepository,
) {

    private val json = Json {
        prettyPrint = true
        ignoreUnknownKeys = true
        encodeDefaults = true
    }

    suspend fun exportJson(): String {
        val semesters = db.semesterDao().getAll()
        val slots = db.timeSlotDao().getAll()
        val courses = db.courseDao().getAll()
        val settings = settingsRepository.settings.first()

        val coursesBySemester = courses.groupBy { it.semesterId }
        val backupCourses = mutableListOf<BackupCourse>()

        semesters.forEachIndexed { index, semester ->
            coursesBySemester[semester.id].orEmpty().forEach { course ->
                val sessions = db.courseSessionDao().getByCourse(course.id)
                backupCourses += BackupCourse(
                    semesterIndex = index,
                    name = course.name,
                    teacher = course.teacher,
                    location = course.defaultLocation,
                    colorArgb = course.colorArgb,
                    note = course.note,
                    sessions = sessions.map { session ->
                        BackupSession(
                            dayOfWeek = session.dayOfWeek,
                            startSlot = session.startSlot,
                            endSlot = session.endSlot,
                            weekRule = session.weekRule,
                            weekMask = session.weekMask,
                            locationOverride = session.locationOverride,
                            note = session.note,
                        )
                    },
                )
            }
        }

        val file = BackupFile(
            exportedAt = System.currentTimeMillis(),
            semesters = semesters.map { it.toBackup() },
            timeSlots = slots.map {
                BackupTimeSlot(
                    index = it.slotIndex,
                    startMinute = it.startMinute,
                    endMinute = it.endMinute,
                    section = it.section,
                )
            },
            courses = backupCourses,
            settings = BackupSettings(
                themeMode = settings.themeMode.name,
                reminderEnabled = settings.reminderEnabled,
                reminderMinutes = settings.reminderMinutes,
            ),
        )

        return json.encodeToString(BackupFile.serializer(), file)
    }

    suspend fun restoreFromJson(text: String): RestoreResult {
        val file = json.decodeFromString(BackupFile.serializer(), text)
        require(file.version <= BackupFile.CURRENT_VERSION) {
            "备份文件版本 ${file.version} 比当前应用新，请升级应用后再恢复"
        }

        // clearAllTables 必须在事务外调用，Room 内部会自己开一个
        db.clearAllTables()

        var sessionCount = 0
        db.withTransaction {
            val semesterIds = file.semesters.map { semester ->
                db.semesterDao().insert(
                    SemesterEntity(
                        name = semester.name,
                        startDate = LocalDate.ofEpochDay(semester.startDateEpochDay),
                        totalWeeks = semester.totalWeeks,
                        parityEnabled = semester.parityEnabled,
                        weekOverride = semester.weekOverride,
                        isActive = semester.isActive,
                    )
                )
            }

            if (file.timeSlots.isNotEmpty()) {
                db.timeSlotDao().insertAll(
                    file.timeSlots.map {
                        TimeSlotEntity(
                            slotIndex = it.index,
                            startMinute = it.startMinute,
                            endMinute = it.endMinute,
                            section = it.section,
                        )
                    }
                )
            }

            file.courses.forEach { course ->
                val semesterId = semesterIds.getOrNull(course.semesterIndex) ?: return@forEach
                val courseId = db.courseDao().insert(
                    CourseEntity(
                        semesterId = semesterId,
                        name = course.name,
                        teacher = course.teacher,
                        defaultLocation = course.location,
                        colorArgb = course.colorArgb,
                        note = course.note,
                    )
                )
                if (course.sessions.isNotEmpty()) {
                    db.courseSessionDao().insertAll(
                        course.sessions.map { session ->
                            CourseSessionEntity(
                                courseId = courseId,
                                dayOfWeek = session.dayOfWeek,
                                startSlot = session.startSlot,
                                endSlot = session.endSlot,
                                weekRule = session.weekRule,
                                weekMask = session.weekMask,
                                locationOverride = session.locationOverride,
                                note = session.note,
                            )
                        }
                    )
                    sessionCount += course.sessions.size
                }
            }

            file.settings?.let { settings ->
                settingsRepository.setThemeMode(
                    ThemeMode.entries.firstOrNull { it.name == settings.themeMode } ?: ThemeMode.SYSTEM
                )
                settingsRepository.setReminderEnabled(settings.reminderEnabled)
                settingsRepository.setReminderMinutes(settings.reminderMinutes)
            }
        }

        return RestoreResult(
            semesterCount = file.semesters.size,
            courseCount = file.courses.size,
            sessionCount = sessionCount,
        )
    }

    private fun SemesterEntity.toBackup() = BackupSemester(
        name = name,
        startDateEpochDay = startDate.toEpochDay(),
        totalWeeks = totalWeeks,
        parityEnabled = parityEnabled,
        isActive = isActive,
        weekOverride = weekOverride,
    )
}
