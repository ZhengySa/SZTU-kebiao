package com.classflow.timetable.data

import androidx.room.withTransaction
import com.classflow.timetable.data.local.AppDatabase
import com.classflow.timetable.data.local.DefaultData
import com.classflow.timetable.data.local.SemesterDao
import com.classflow.timetable.data.local.TimeSlotDao
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import javax.inject.Inject
import javax.inject.Singleton

/**
 * 首次启动写入 12 节默认时间 + 一个默认学期。
 * 用 Mutex + 幂等标记，避免多个入口同时写种子数据。
 */
@Singleton
class DataBootstrap @Inject constructor(
    private val db: AppDatabase,
    private val semesterDao: SemesterDao,
    private val timeSlotDao: TimeSlotDao,
) {
    private val mutex = Mutex()

    @Volatile
    private var seeded = false

    suspend fun ensureSeeded() {
        if (seeded) return
        mutex.withLock {
            if (seeded) return
            db.withTransaction {
                if (timeSlotDao.count() == 0) {
                    timeSlotDao.insertAll(DefaultData.defaultSlots())
                }
                if (semesterDao.count() == 0) {
                    semesterDao.insert(DefaultData.defaultSemester())
                }
            }
            seeded = true
        }
    }
}
