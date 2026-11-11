package com.classflow.timetable.data.local

import com.classflow.timetable.core.time.WeekMath
import com.classflow.timetable.domain.model.SlotSection
import java.time.LocalDate

/**
 * 首次启动写入的种子数据。
 * 开学日取「本周一」而不是写死日期，避免第一次打开就显示「第 -30 周」。
 */
object DefaultData {

    data class SlotSeed(val index: Int, val startMinute: Int, val endMinute: Int, val section: SlotSection)

    /**
     * 深圳技术大学作息表（2025-2026 学年，教务处《上课时间表》）。
     *
     * 一共 15 节，且**不是每节 45 分钟、也不是每节都连着上**：
     *  - 上午 08:30 才开始，第 1-2 节之间只休息 5 分钟；
     *  - 第 7-8 节之间是大课间（20 分钟）；
     *  - 晚上 11-12 节连上 80 分钟（19:00-20:20），13-14 节同样连上 80 分钟（20:30-21:50），
     *    所以第 13 节单独上到 21:10 就下课，不是 21:50；
     *  - 第 15 节是傍晚的单独一节（18:00-18:40），时间早于 11-14 节，编号却排在最后 ——
     *    这是学校的原始安排，不要"顺手"把它挪到晚上。
     *
     * 教务系统课表页把相邻小节压成一行（"第11 12节 19:00-20:20"），
     * 这里存的是**拆分后每一节自己的时间**，两者由 [com.classflow.timetable.data.remote.sztu.ZfKbParser] 对齐。
     */
    val SLOT_SEEDS: List<SlotSeed> = listOf(
        SlotSeed(1, 8 * 60 + 30, 9 * 60 + 10, SlotSection.MORNING),
        SlotSeed(2, 9 * 60 + 15, 9 * 60 + 55, SlotSection.MORNING),
        SlotSeed(3, 10 * 60 + 15, 10 * 60 + 55, SlotSection.MORNING),
        SlotSeed(4, 11 * 60, 11 * 60 + 40, SlotSection.MORNING),
        SlotSeed(5, 11 * 60 + 45, 12 * 60 + 25, SlotSection.MORNING),
        SlotSeed(6, 14 * 60, 14 * 60 + 40, SlotSection.AFTERNOON),
        SlotSeed(7, 14 * 60 + 45, 15 * 60 + 25, SlotSection.AFTERNOON),
        SlotSeed(8, 15 * 60 + 45, 16 * 60 + 25, SlotSection.AFTERNOON),
        SlotSeed(9, 16 * 60 + 30, 17 * 60 + 10, SlotSection.AFTERNOON),
        SlotSeed(10, 17 * 60 + 15, 17 * 60 + 55, SlotSection.AFTERNOON),
        SlotSeed(11, 19 * 60, 19 * 60 + 40, SlotSection.EVENING),
        SlotSeed(12, 19 * 60 + 40, 20 * 60 + 20, SlotSection.EVENING),
        SlotSeed(13, 20 * 60 + 30, 21 * 60 + 10, SlotSection.EVENING),
        SlotSeed(14, 21 * 60 + 10, 21 * 60 + 50, SlotSection.EVENING),
        SlotSeed(15, 18 * 60, 18 * 60 + 40, SlotSection.EVENING),
    )

    const val DEFAULT_TOTAL_WEEKS = 20

    fun defaultSlots(): List<TimeSlotEntity> = SLOT_SEEDS.map {
        TimeSlotEntity(
            slotIndex = it.index,
            startMinute = it.startMinute,
            endMinute = it.endMinute,
            section = it.section.name,
        )
    }

    fun defaultSemester(today: LocalDate = LocalDate.now()): SemesterEntity = SemesterEntity(
        name = "当前学期",
        startDate = WeekMath.mondayOf(today),
        totalWeeks = DEFAULT_TOTAL_WEEKS,
        parityEnabled = true,
        weekOverride = null,
        isActive = true,
    )
}
