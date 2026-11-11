package com.classflow.timetable.domain.usecase

import com.classflow.timetable.data.local.SztuAccountStore
import com.classflow.timetable.data.remote.sztu.SztuAuthClient
import com.classflow.timetable.data.remote.sztu.ZfCalendarParser
import com.classflow.timetable.data.remote.sztu.ZfKbParser
import com.classflow.timetable.domain.imports.ImportOutcome
import com.classflow.timetable.domain.imports.ImportPreview
import com.classflow.timetable.domain.imports.ImportedCourse
import com.classflow.timetable.domain.model.ImportMode
import com.classflow.timetable.domain.model.OwnerKey
import com.classflow.timetable.domain.model.TimeSlot
import com.classflow.timetable.domain.repository.TimetableRepository
import java.time.LocalTime
import javax.inject.Inject

/** 一次同步拿到的原始结果。 */
data class SztuSnapshot(
    val timetable: ZfKbParser.ZfTimetable,
    val calendar: ZfCalendarParser.ZfCalendar?,
    val semesterKey: String,
    val semesterName: String,
)

/**
 * 把教务系统的课表转成 App 自己的结构，并顺手把节次时间、开学日期一起带过来。
 *
 * 为什么要带节次时间：学校是 15 节课、8:30 才开始；App 默认 12 节、8:00 开始。
 * 不覆盖的话，"第 11 节 18:00" 会显示成"第 11 节 20:50"，整张课表时间全错。
 */
class SztuSyncUseCase @Inject constructor(
    private val accountStore: SztuAccountStore,
    private val prepareImport: PrepareImportUseCase,
) {

    /**
     * 用本地保存的会话恢复一个客户端；没存过就返回全新的（等于未登录）。
     *
     * 自己和 TA 的会话分开存：[OwnerKey.PARTNER] 用另一份 Cookie，
     * 否则给 TA 登录一次就会把自己的登录状态顶掉。
     */
    suspend fun restoreClient(ownerKey: String = OwnerKey.SELF): SztuAuthClient {
        val client = SztuAuthClient()
        val cookies = if (ownerKey == OwnerKey.PARTNER) {
            accountStore.savedPartnerCookies()
        } else {
            accountStore.savedCookies()
        }
        client.importCookies(cookies)
        return client
    }

    suspend fun savedSemester(ownerKey: String = OwnerKey.SELF): String =
        if (ownerKey == OwnerKey.PARTNER) accountStore.savedPartnerSemester() else accountStore.savedSemester()

    suspend fun snapshot(
        client: SztuAuthClient,
        semesterKey: String?,
        ownerKey: String = OwnerKey.SELF,
    ): SztuSnapshot {
        val key = semesterKey?.takeIf { it.isNotBlank() } ?: savedSemester(ownerKey).takeIf { it.isNotBlank() }
        val html = client.fetchTimetableHtml(key)
        val timetable = ZfKbParser.parse(html)

        val effectiveKey = key ?: timetable.currentSemester.orEmpty()
        val calendar = client.fetchCalendarHtml()?.let { html2 ->
            ZfCalendarParser.parse(html2, ZfCalendarParser.academicYearOf(effectiveKey))
        }

        return SztuSnapshot(
            timetable = timetable,
            calendar = calendar,
            semesterKey = effectiveKey,
            semesterName = semesterTitle(effectiveKey),
        )
    }

    suspend fun preview(snapshot: SztuSnapshot): ImportPreview {
        val courses = toImportedCourses(snapshot.timetable)
        val warnings = buildList {
            if (courses.isEmpty()) {
                add("课表是空的：可能这个学期还没排课，或者选错了学期。")
            }
            if (snapshot.calendar?.startDate == null) {
                add("没读到教学周历，开学日期沿用你原来的设置。")
            }
        }
        return prepareImport.fromCourses(courses, warnings = warnings)
    }

    suspend fun persistSession(
        client: SztuAuthClient,
        snapshot: SztuSnapshot,
        ownerKey: String = OwnerKey.SELF,
    ) {
        val cookies = client.exportCookies()
        if (ownerKey == OwnerKey.PARTNER) {
            accountStore.savePartnerSession(
                cookies = cookies,
                semesterKey = snapshot.semesterKey,
                semesterName = snapshot.semesterName,
            )
        } else {
            accountStore.saveSession(
                cookies = cookies,
                semesterKey = snapshot.semesterKey,
                semesterName = snapshot.semesterName,
            )
        }
    }

    private fun toImportedCourses(timetable: ZfKbParser.ZfTimetable): List<ImportedCourse> =
        SztuCourseAggregator.aggregate(timetable.entries)

    /** `2026-2027-1` → `2026-2027 学年 第 1 学期` */
    private fun semesterTitle(key: String): String {
        val parts = key.split('-')
        if (parts.size < 3) return key
        return "${parts[0]}-${parts[1]} 学年 第 ${parts[2]} 学期"
    }
}

/**
 * 落库：先对齐节次与学期，再写课程。
 * 三步都成功才算导入成功；中途失败不会留下"课表写了一半"的状态（写课程本身是单事务）。
 */
class ApplySztuImportUseCase @Inject constructor(
    private val repository: TimetableRepository,
    private val applyImport: ApplyImportUseCase,
) {

    suspend operator fun invoke(
        snapshot: SztuSnapshot,
        preview: ImportPreview,
        mode: ImportMode,
        ownerKey: String = OwnerKey.SELF,
    ): ImportOutcome {
        // 节次/学期只在同步「自己的」课表时覆盖：
        // 导入 TA 的课表不该动到我自己的开学日期和节次设置。
        if (ownerKey == OwnerKey.SELF) {
            applySharedSettings(snapshot)
        }
        return applyImport(preview, mode, snapshot.semesterName.ifBlank { "教务系统" }, ownerKey)
    }

    private suspend fun applySharedSettings(snapshot: SztuSnapshot) {
        if (snapshot.timetable.slots.isNotEmpty()) {
            repository.replaceTimeSlots(
                snapshot.timetable.slots.map {
                    TimeSlot(
                        index = it.index,
                        start = LocalTime.of((it.startMinute / 60).coerceIn(0, 23), (it.startMinute % 60).coerceIn(0, 59)),
                        end = LocalTime.of((it.endMinute / 60).coerceIn(0, 23), (it.endMinute % 60).coerceIn(0, 59)),
                        section = it.section,
                    )
                },
            )
        }

        repository.getActiveSemester()?.let { semester ->
            val calendar = snapshot.calendar
            val totalWeeks = (calendar?.totalWeeks ?: 0)
                .takeIf { it > 0 }
                ?: snapshot.timetable.maxWeek.takeIf { it > 0 }
                ?: semester.totalWeeks
            repository.upsertSemester(
                semester.copy(
                    name = snapshot.semesterName.takeIf { it.isNotBlank() } ?: semester.name,
                    startDate = calendar?.startDate ?: semester.startDate,
                    totalWeeks = totalWeeks,
                    // 学校课表周次是具体周号，单双周开关没有意义，关掉减少歧义
                    parityEnabled = false,
                    weekOverride = null,
                ),
            )
        }
    }
}
