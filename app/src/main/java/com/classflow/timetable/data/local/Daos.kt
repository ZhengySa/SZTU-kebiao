package com.classflow.timetable.data.local

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

/**
 * 课程 + 时段的 join 结果。仓库层只把这个 POJO 转成 domain 的 ScheduledSession，
 * 时间换算留给 ScheduleBuilder，保证「节次时间改了，全 App 立刻正确」。
 */
data class ScheduleRow(
    @ColumnInfo(name = "session_id") val sessionId: Long,
    @ColumnInfo(name = "course_id") val courseId: Long,
    @ColumnInfo(name = "course_name") val courseName: String,
    @ColumnInfo(name = "teacher") val teacher: String?,
    @ColumnInfo(name = "color_argb") val colorArgb: Long,
    @ColumnInfo(name = "day_of_week") val dayOfWeek: Int,
    @ColumnInfo(name = "start_slot") val startSlot: Int,
    @ColumnInfo(name = "end_slot") val endSlot: Int,
    @ColumnInfo(name = "week_rule") val weekRule: String,
    @ColumnInfo(name = "week_mask") val weekMask: Long,
    @ColumnInfo(name = "location") val location: String?,
    @ColumnInfo(name = "note") val note: String?,
)

@Dao
interface SemesterDao {

    @Query("SELECT * FROM semester WHERE is_active = 1 LIMIT 1")
    fun observeActive(): Flow<SemesterEntity?>

    @Query("SELECT * FROM semester WHERE is_active = 1 LIMIT 1")
    suspend fun getActive(): SemesterEntity?

    @Query("SELECT * FROM semester ORDER BY is_active DESC, start_date DESC")
    fun observeAll(): Flow<List<SemesterEntity>>

    @Insert
    suspend fun insert(entity: SemesterEntity): Long

    @Update
    suspend fun update(entity: SemesterEntity)

    @Query("UPDATE semester SET week_override = :week, updated_at = :now WHERE id = :id")
    suspend fun setWeekOverride(id: Long, week: Int?, now: Long)

    @Query("UPDATE semester SET is_active = 0")
    suspend fun clearActive()

    @Query("UPDATE semester SET is_active = 1 WHERE id = :id")
    suspend fun activate(id: Long)

    @Query("SELECT COUNT(*) FROM semester")
    suspend fun count(): Int

    @Query("SELECT * FROM semester ORDER BY start_date")
    suspend fun getAll(): List<SemesterEntity>
}

@Dao
interface TimeSlotDao {

    @Query("SELECT * FROM time_slot ORDER BY slot_index")
    fun observeAll(): Flow<List<TimeSlotEntity>>

    @Query("SELECT * FROM time_slot ORDER BY slot_index")
    suspend fun getAll(): List<TimeSlotEntity>

    @Query("SELECT COUNT(*) FROM time_slot")
    suspend fun count(): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(items: List<TimeSlotEntity>)

    @Insert
    suspend fun insert(entity: TimeSlotEntity): Long

    @Update
    suspend fun update(entity: TimeSlotEntity)

    @Query("DELETE FROM time_slot")
    suspend fun deleteAll()
}

@Dao
interface CourseDao {

    @Query("SELECT * FROM course WHERE id = :id LIMIT 1")
    fun observeById(id: Long): Flow<CourseEntity?>

    @Query("SELECT * FROM course WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): CourseEntity?

    @Insert
    suspend fun insert(entity: CourseEntity): Long

    @Update
    suspend fun update(entity: CourseEntity)

    @Query("DELETE FROM course WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("SELECT COUNT(*) FROM course WHERE semester_id = :semesterId AND owner_key = :ownerKey")
    suspend fun countInSemester(semesterId: Long, ownerKey: String): Int

    @Query("SELECT * FROM course WHERE semester_id = :semesterId AND owner_key = :ownerKey ORDER BY id")
    suspend fun getAllInSemester(semesterId: Long, ownerKey: String): List<CourseEntity>

    @Query("DELETE FROM course WHERE semester_id = :semesterId AND owner_key = :ownerKey")
    suspend fun deleteBySemester(semesterId: Long, ownerKey: String)

    @Query("SELECT * FROM course ORDER BY semester_id, id")
    suspend fun getAll(): List<CourseEntity>
}

@Dao
interface CourseSessionDao {

    @Query("SELECT * FROM course_session WHERE course_id = :courseId ORDER BY day_of_week, start_slot")
    fun observeByCourse(courseId: Long): Flow<List<CourseSessionEntity>>

    @Query("SELECT * FROM course_session WHERE course_id = :courseId ORDER BY day_of_week, start_slot")
    suspend fun getByCourse(courseId: Long): List<CourseSessionEntity>

    @Insert
    suspend fun insert(entity: CourseSessionEntity): Long

    @Insert
    suspend fun insertAll(items: List<CourseSessionEntity>): List<Long>

    @Update
    suspend fun update(entity: CourseSessionEntity)

    @Query("DELETE FROM course_session WHERE course_id = :courseId")
    suspend fun deleteByCourse(courseId: Long)

    @Query("""
        SELECT s.id AS session_id, s.course_id AS course_id, c.name AS course_name, c.teacher AS teacher,
               c.color_argb AS color_argb, s.day_of_week AS day_of_week, s.start_slot AS start_slot,
               s.end_slot AS end_slot, s.week_rule AS week_rule, s.week_mask AS week_mask,
               COALESCE(s.location_override, c.default_location) AS location, s.note AS note
        FROM course_session s
        INNER JOIN course c ON c.id = s.course_id
        WHERE c.semester_id = :semesterId AND c.owner_key = :ownerKey
        ORDER BY s.day_of_week, s.start_slot
        """)
    fun observeSchedule(semesterId: Long, ownerKey: String): Flow<List<ScheduleRow>>
}

@Dao
interface ImportSourceDao {

    @Query("SELECT * FROM import_source ORDER BY imported_at DESC")
    fun observeAll(): Flow<List<ImportSourceEntity>>

    @Insert
    suspend fun insert(entity: ImportSourceEntity): Long
}
