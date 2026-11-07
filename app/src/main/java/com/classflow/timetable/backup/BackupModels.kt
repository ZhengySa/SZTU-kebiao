package com.classflow.timetable.backup

import kotlinx.serialization.Serializable

/**
 * 备份文件结构（version 1）。
 *
 * 刻意不复用 Room 实体：实体是存储细节，字段可能随版本变；
 * 备份格式一旦发出去就要能被老版本读懂，所以单独定义并带 version 号。
 * 新增字段全部给默认值，保证老备份仍能恢复。
 */
@Serializable
data class BackupFile(
    val version: Int = CURRENT_VERSION,
    val exportedAt: Long = 0L,
    val appName: String = "ClassFlow",
    val semesters: List<BackupSemester> = emptyList(),
    val timeSlots: List<BackupTimeSlot> = emptyList(),
    val courses: List<BackupCourse> = emptyList(),
    val settings: BackupSettings? = null,
) {
    companion object {
        const val CURRENT_VERSION = 1
    }
}

@Serializable
data class BackupSemester(
    val name: String,
    val startDateEpochDay: Long,
    val totalWeeks: Int,
    val parityEnabled: Boolean = true,
    val isActive: Boolean = false,
    val weekOverride: Int? = null,
)

@Serializable
data class BackupTimeSlot(
    val index: Int,
    val startMinute: Int,
    val endMinute: Int,
    val section: String,
)

@Serializable
data class BackupCourse(
    /** 指向 [BackupFile.semesters] 的下标，而不是数据库 id（id 在另一台设备上没有意义）。 */
    val semesterIndex: Int,
    val name: String,
    val teacher: String? = null,
    val location: String? = null,
    val colorArgb: Long = 0xFF3A5BF0,
    val note: String? = null,
    val sessions: List<BackupSession> = emptyList(),
)

@Serializable
data class BackupSession(
    val dayOfWeek: Int,
    val startSlot: Int,
    val endSlot: Int,
    val weekRule: String = "ALL",
    /** 周次位图：bit0 = 第 1 周。整数比字符串紧凑，且天然可做位运算校验。 */
    val weekMask: Long = 0L,
    val locationOverride: String? = null,
    val note: String? = null,
)

@Serializable
data class BackupSettings(
    val themeMode: String = "SYSTEM",
    val reminderEnabled: Boolean = true,
    val reminderMinutes: Int = 15,
)

/** 恢复结果，用来给用户一句人话反馈。 */
data class RestoreResult(
    val semesterCount: Int,
    val courseCount: Int,
    val sessionCount: Int,
)
