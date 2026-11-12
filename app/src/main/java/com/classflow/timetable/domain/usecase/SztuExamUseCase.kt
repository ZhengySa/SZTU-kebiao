package com.classflow.timetable.domain.usecase

import com.classflow.timetable.data.local.SztuAccountStore
import com.classflow.timetable.data.remote.sztu.SztuAuthClient
import com.classflow.timetable.data.remote.sztu.ZfExamParser
import com.classflow.timetable.domain.model.ExamEntry
import com.classflow.timetable.domain.model.ExamSemester
import javax.inject.Inject

/**
 * 期末考试查询用例。
 *
 * 只查期末：教务端的筛选项能选「期末」就选上；选不上就靠返回的考试名称过滤，
 * 名称里也没写期末的，宁可全部列出来并明说，也不假装"没有考试"。
 *
 * **不入库**：考试信息只在内存里活着，不写 DataStore / Room / 小组件。
 * 成绩可以缓存是因为它不会变；考试安排涉及考场座位，没有理由长期留在磁盘上。
 */
class SztuExamUseCase @Inject constructor(
    private val accountStore: SztuAccountStore,
) {

    data class Loaded(
        val rows: List<ExamEntry>,
        val semesters: List<ExamSemester>,
        val semesterLabel: String,
        val note: String?,
    )

    suspend fun load(semesterKey: String?): Loaded {
        val cookies = accountStore.savedCookies()
        if (cookies.isBlank()) throw NotLoggedInException()

        val client = SztuAuthClient().apply { importCookies(cookies) }
        val page = client.fetchExams(semesterKey)

        // 教务顺手刷新过会话就存回去，下次不用重新登录
        runCatching { accountStore.refreshCookies(client.exportCookies()) }

        val key = semesterKey?.takeIf { it.isNotBlank() } ?: accountStore.savedSemester()
        val result = ZfExamParser.parse(page.html, key, finalOnly = true)
        return Loaded(
            rows = result.rows,
            semesters = page.semesters,
            semesterLabel = page.semesterLabel.ifBlank { key },
            note = result.note,
        )
    }
}
