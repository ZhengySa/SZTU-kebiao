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

/**
 * 2 → 3：把节次表换成学校真实作息（15 节、8:30 开始）。
 *
 * 老版本只有 12 节，时间是按「每节 45 分钟、8:00 开始」猜的，和学校对不上；
 * 更糟的是同步教务时，被压成一行的「第13 14节 20:30-21:50」会让第 13 节显示到 21:50。
 *
 * 这里整表重写而不是逐行修：节次编号和时间是一套的，只改一半会留下
 * 「13 节有人手动改过、14 节还是旧的」这种更难查的状态。用户的课程引用的是节次编号
 * （start_slot / end_slot），编号不变，课表不会错位。
 */
val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("DELETE FROM time_slot")
        DefaultData.SLOT_SEEDS.forEach { seed ->
            db.execSQL(
                "INSERT INTO time_slot (slot_index, start_minute, end_minute, section) " +
                    "VALUES (?, ?, ?, ?)",
                arrayOf<Any>(seed.index, seed.startMinute, seed.endMinute, seed.section.name),
            )
        }
    }
}

/**
 * 3 → 4：清掉课程级地点里的拼接串。
 *
 * 3 版之前，同步教务时把一门课所有时段的教室拼成一串塞进 default_location
 * （「C-5-215 / C-5-215 / D-3-321」）。这条信息本身是错的 —— 它让三个时段
 * 看起来都在这三个地方。真正的地点现在已经存进每个时段的 location_override，
 * 但老数据的时段是空的，所以这里先把拼接串清掉，宁可显示「地点待填」，
 * 也不给一个看着像答案的假答案。用户重新同步一次教务就会补全。
 *
 * 判据用「包含 ' / '」：课程地点只有多个才会被拼，单个教室不会带这个分隔符。
 */
val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            "UPDATE course SET default_location = NULL WHERE default_location LIKE '% / %'",
        )
    }
}
