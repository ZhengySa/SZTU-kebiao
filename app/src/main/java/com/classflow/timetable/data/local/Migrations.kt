package com.classflow.timetable.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * 迁移纪律（和 AppDatabase 里那条注释一致）：
 * 任何 schema 变更都必须写明 Migration，**禁止** fallbackToDestructiveMigration。
 *
 * 1 → 2：给 course 加 owner_key（区分"我的课表"和"情侣课表"）。
 * 老数据全部是自己的课，所以 DEFAULT 'self' 正好。
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "ALTER TABLE course ADD COLUMN owner_key TEXT NOT NULL DEFAULT 'self'",
        )
    }
}
