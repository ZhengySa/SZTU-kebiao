package com.classflow.timetable

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.classflow.timetable.data.local.AppDatabase
import com.classflow.timetable.data.local.CourseEntity
import com.classflow.timetable.data.local.CourseSessionEntity
import com.classflow.timetable.data.local.ImportSourceEntity
import com.classflow.timetable.data.local.SemesterEntity
import com.classflow.timetable.data.mapper.toEntity
import com.classflow.timetable.data.repository.TimetableRepositoryImpl
import com.classflow.timetable.domain.imports.ImportedCourse
import com.classflow.timetable.domain.imports.ImportedSession
import com.classflow.timetable.domain.model.ImportMode
import com.classflow.timetable.domain.model.ImportSourceType
import com.classflow.timetable.domain.model.OwnerKey
import com.classflow.timetable.domain.model.WeekMask
import com.classflow.timetable.domain.sync.NoopScheduleChangeNotifier
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import java.time.LocalDate

/**
 * 真实 Room 库上的「老数据补教室」验证。
 *
 * 单测只能验规则，验不了 SQL 和事务 —— 而这次出问题的地方恰恰在写库：
 * 1.0.2 之前导入的课，教室只存在课程级，时段是空的；合并模式又把同名课整门跳过，
 * 于是用户重新同步多少次都是「地点待填」。
 *
 * 这里造一份那样的老数据，跑一次合并导入，断言每个时段的教室按时间对齐补上了。
 */
@RunWith(AndroidJUnit4::class)
class MergeImportRoomTest {

    private lateinit var db: AppDatabase
    private lateinit var repo: TimetableRepositoryImpl
    private var semesterId: Long = 0L

    @Before
    fun setUp() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AppDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repo = TimetableRepositoryImpl(
            db = db,
            semesterDao = db.semesterDao(),
            timeSlotDao = db.timeSlotDao(),
            courseDao = db.courseDao(),
            sessionDao = db.courseSessionDao(),
            importSourceDao = db.importSourceDao(),
            notifier = NoopScheduleChangeNotifier,
        )
        semesterId = db.semesterDao().insert(
            SemesterEntity(
                name = "2026-2027 第 1 学期",
                startDate = LocalDate.of(2026, 9, 1),
                totalWeeks = 18,
                isActive = true,
            ),
        )
    }

    @After
    fun tearDown() {
        db.close()
    }

    /** 造一门 1.0.2 之前的课：课程级地点是拼接串（已被迁移清掉），时段没有地点。 */
    private suspend fun seedLegacyCourse(name: String, slots: List<Triple<Int, Int, Int>>): Long {
        // course.import_source_id 有外键，先落一条来源流水
        val sourceId = db.importSourceDao().insert(
            ImportSourceEntity(
                type = ImportSourceType.PROVIDER.name,
                displayName = "教务系统",
                importedAt = System.currentTimeMillis(),
                mode = ImportMode.OVERWRITE.name,
            ),
        )
        val courseId = db.courseDao().insert(
            CourseEntity(
                semesterId = semesterId,
                name = name,
                teacher = null,
                defaultLocation = null,
                colorArgb = 0xFF3A5BF0,
                ownerKey = OwnerKey.SELF,
                importSourceId = sourceId,
            ),
        )
        db.courseSessionDao().insertAll(
            slots.map { (day, start, end) ->
                CourseSessionEntity(
                    courseId = courseId,
                    dayOfWeek = day,
                    startSlot = start,
                    endSlot = end,
                    weekRule = "ALL",
                    weekMask = WeekMask.fromWeeks((1..16).toSet()).bits,
                    locationOverride = null,
                )
            },
        )
        return courseId
    }

    private fun session(day: Int, start: Int, end: Int, room: String) = ImportedSession(
        dayOfWeek = day,
        startSlot = start,
        endSlot = end,
        weeks = (1..16).toSet(),
        location = room,
    )

    @Test
    fun 合并导入给老数据逐时段补上教室() = runBlocking {
        val courseId = seedLegacyCourse(
            name = "传感器原理及应用",
            slots = listOf(Triple(3, 6, 7), Triple(4, 8, 10), Triple(2, 11, 13)),
        )
        assertEquals("开始前应该有 3 个时段缺教室", 3, repo.countSessionsMissingLocation(semesterId, OwnerKey.SELF))

        val outcome = repo.importCourses(
            semesterId = semesterId,
            courses = listOf(
                ImportedCourse(
                    name = "传感器原理及应用",
                    teacher = "文克春",
                    location = null, // 三个教室不一致，课程级就该是空的
                    sessions = listOf(
                        session(3, 6, 7, "C-5-215"),
                        session(4, 8, 10, "C-5-215"),
                        session(2, 11, 13, "D-3-321"),
                    ),
                ),
            ),
            mode = ImportMode.MERGE,
            sourceType = ImportSourceType.PROVIDER,
            sourceName = "教务系统",
            ownerKey = OwnerKey.SELF,
        )

        assertEquals("合并模式不该新增课程", 0, outcome.insertedCourses)
        assertEquals("应该补上 3 个时段的教室", 3, outcome.repairedSessions)
        assertEquals("补完之后不该再缺", 0, repo.countSessionsMissingLocation(semesterId, OwnerKey.SELF))

        val sessions = repo.getSessionsOfCourse(courseId)
        assertEquals(3, sessions.size)
        assertEquals(
            "每个时段拿自己那间教室，不能串",
            mapOf(
                Triple(3, 6, 7) to "C-5-215",
                Triple(4, 8, 10) to "C-5-215",
                Triple(2, 11, 13) to "D-3-321",
            ),
            sessions.associate { Triple(it.dayOfWeek, it.startSlot, it.endSlot) to it.locationOverride },
        )
        assertEquals("不该留下重复课程", 1, repo.countCourses(semesterId, OwnerKey.SELF))
    }

    @Test
    fun 合并导入不动用户手填的地点() = runBlocking {
        val courseId = seedLegacyCourse(
            name = "传感器原理及应用",
            slots = listOf(Triple(3, 6, 7)),
        )
        val original = repo.getSessionsOfCourse(courseId).single()
        db.courseSessionDao().update(
            original.copy(locationOverride = "老师说换到 D-3-101").toEntity(courseId),
        )

        val outcome = repo.importCourses(
            semesterId = semesterId,
            courses = listOf(
                ImportedCourse(
                    name = "传感器原理及应用",
                    sessions = listOf(session(3, 6, 7, "C-5-215")),
                ),
            ),
            mode = ImportMode.MERGE,
            sourceType = ImportSourceType.PROVIDER,
            sourceName = "教务系统",
            ownerKey = OwnerKey.SELF,
        )

        assertEquals(0, outcome.repairedSessions)
        assertEquals(
            "手填的地点优先级最高",
            "老师说换到 D-3-101",
            repo.getSessionsOfCourse(courseId).single().locationOverride,
        )
    }

    @Test
    fun 全部时段同教室时课程级地点也补上() = runBlocking {
        val courseId = seedLegacyCourse(
            name = "复变函数与积分变换",
            slots = listOf(Triple(1, 1, 2), Triple(4, 3, 4)),
        )

        repo.importCourses(
            semesterId = semesterId,
            courses = listOf(
                ImportedCourse(
                    name = "复变函数与积分变换",
                    location = "C-5-211",
                    sessions = listOf(
                        session(1, 1, 2, "C-5-211"),
                        session(4, 3, 4, "C-5-211"),
                    ),
                ),
            ),
            mode = ImportMode.MERGE,
            sourceType = ImportSourceType.PROVIDER,
            sourceName = "教务系统",
            ownerKey = OwnerKey.SELF,
        )

        assertEquals("C-5-211", repo.getCourse(courseId)?.defaultLocation)
        assertNull(
            "时段已经各自带了教室，课程级留着是为了列表页少翻一层",
            repo.getSessionsOfCourse(courseId).firstOrNull { it.locationOverride == null },
        )
    }
}
