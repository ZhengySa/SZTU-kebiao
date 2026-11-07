package com.classflow.timetable.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * 数据库版本 2。
 *
 * 迁移纪律：以后任何字段变更都必须写 Migration 并配迁移测试。
 * 绝对禁止 fallbackToDestructiveMigration()——它会静默清空用户的课表。
 *
 * v2：course 增加 owner_key（见 MIGRATION_1_2），用于「情侣课表」。
 */
@Database(
    entities = [
        SemesterEntity::class,
        TimeSlotEntity::class,
        ImportSourceEntity::class,
        CourseEntity::class,
        CourseSessionEntity::class,
    ],
    version = 2,
    exportSchema = true,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun semesterDao(): SemesterDao
    abstract fun timeSlotDao(): TimeSlotDao
    abstract fun courseDao(): CourseDao
    abstract fun courseSessionDao(): CourseSessionDao
    abstract fun importSourceDao(): ImportSourceDao
}
