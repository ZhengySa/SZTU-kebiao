package com.classflow.timetable.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey
import java.time.LocalDate

/**
 * 学期。
 * 当前周不落库，由 startDate + weekOverride 推导（WeekResolver），避免出现「存了周次但日期变了」的不一致。
 */
@Entity(
    tableName = "semester",
    indices = [Index(value = ["is_active"])],
)
data class SemesterEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "start_date") val startDate: LocalDate,
    @ColumnInfo(name = "total_weeks") val totalWeeks: Int,
    @ColumnInfo(name = "parity_enabled") val parityEnabled: Boolean = true,
    @ColumnInfo(name = "week_override") val weekOverride: Int? = null,
    @ColumnInfo(name = "is_active") val isActive: Boolean = false,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

/** 节次表。slot_index 唯一，且全 App 固定 1..12，用户只能改时间。 */
@Entity(
    tableName = "time_slot",
    indices = [Index(value = ["slot_index"], unique = true)],
)
data class TimeSlotEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "slot_index") val slotIndex: Int,
    @ColumnInfo(name = "start_minute") val startMinute: Int,
    @ColumnInfo(name = "end_minute") val endMinute: Int,
    @ColumnInfo(name = "section") val section: String,
    @ColumnInfo(name = "label") val label: String? = null,
)

/** 导入来源流水。手动录入也会写一条，便于「这条课是哪来的」追溯。 */
@Entity(tableName = "import_source", indices = [Index(value = ["imported_at"])])
data class ImportSourceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "type") val type: String,
    @ColumnInfo(name = "display_name") val displayName: String,
    @ColumnInfo(name = "imported_at") val importedAt: Long,
    @ColumnInfo(name = "mode") val mode: String,
    @ColumnInfo(name = "item_count") val itemCount: Int = 0,
    @ColumnInfo(name = "payload_hash") val payloadHash: String? = null,
    @ColumnInfo(name = "warnings_json") val warningsJson: String? = null,
)

/**
 * 课程。一门课可以对应多个 [CourseSessionEntity]（周一 3-4 节 + 周三 5-6 节），
 * 这样课程名/教师/颜色只需要维护一份。
 *
 * [CourseEntity.ownerKey] 区分「我的课表」和「情侣课表（另一个人的课表）」：
 * 两者共用同一个学期（同一周），只是课程归属不同。
 */
@Entity(
    tableName = "course",
    foreignKeys = [
        ForeignKey(
            entity = SemesterEntity::class,
            parentColumns = ["id"],
            childColumns = ["semester_id"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ImportSourceEntity::class,
            parentColumns = ["id"],
            childColumns = ["import_source_id"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index(value = ["semester_id"]),
        Index(value = ["import_source_id"]),
        Index(value = ["semester_id", "dedupe_key"], unique = true),
    ],
)
data class CourseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "semester_id") val semesterId: Long,
    @ColumnInfo(name = "name") val name: String,
    @ColumnInfo(name = "teacher") val teacher: String? = null,
    @ColumnInfo(name = "default_location") val defaultLocation: String? = null,
    @ColumnInfo(name = "color_argb") val colorArgb: Long,
    @ColumnInfo(name = "note") val note: String? = null,
    @ColumnInfo(name = "import_source_id") val importSourceId: Long? = null,
    /** 归属：见 [com.classflow.timetable.domain.model.OwnerKey]。 */
    @ColumnInfo(name = "owner_key", defaultValue = "'self'")
    val ownerKey: String = "self",
    /** 导入去重键；手动录入为 null（SQLite 唯一索引允许多个 NULL）。 */
    @ColumnInfo(name = "dedupe_key") val dedupeKey: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)

/** 上课时段。周次用位图存，week_rule 只用于回显。 */
@Entity(
    tableName = "course_session",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["course_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["course_id"]),
        Index(value = ["day_of_week", "start_slot"]),
    ],
)
data class CourseSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    @ColumnInfo(name = "course_id") val courseId: Long,
    @ColumnInfo(name = "day_of_week") val dayOfWeek: Int,
    @ColumnInfo(name = "start_slot") val startSlot: Int,
    @ColumnInfo(name = "end_slot") val endSlot: Int,
    @ColumnInfo(name = "week_rule") val weekRule: String,
    @ColumnInfo(name = "week_mask") val weekMask: Long,
    @ColumnInfo(name = "location_override") val locationOverride: String? = null,
    @ColumnInfo(name = "note") val note: String? = null,
    @ColumnInfo(name = "created_at") val createdAt: Long = System.currentTimeMillis(),
    @ColumnInfo(name = "updated_at") val updatedAt: Long = System.currentTimeMillis(),
)
