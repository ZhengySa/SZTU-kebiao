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

    val SLOT_SEEDS: List<SlotSeed> = listOf(
        SlotSeed(1, 8 * 60, 8 * 60 + 45, SlotSection.MORNING),
        SlotSeed(2, 8 * 60 + 55, 9 * 60 + 40, SlotSection.MORNING),
        SlotSeed(3, 10 * 60, 10 * 60 + 45, SlotSection.MORNING),
        SlotSeed(4, 10 * 60 + 55, 11 * 60 + 40, SlotSection.MORNING),
        SlotSeed(5, 14 * 60, 14 * 60 + 45, SlotSection.AFTERNOON),
        SlotSeed(6, 14 * 60 + 55, 15 * 60 + 40, SlotSection.AFTERNOON),
        SlotSeed(7, 16 * 60, 16 * 60 + 45, SlotSection.AFTERNOON),
        SlotSeed(8, 16 * 60 + 55, 17 * 60 + 40, SlotSection.AFTERNOON),
        SlotSeed(9, 19 * 60, 19 * 60 + 45, SlotSection.EVENING),
        SlotSeed(10, 19 * 60 + 55, 20 * 60 + 40, SlotSection.EVENING),
        SlotSeed(11, 20 * 60 + 50, 21 * 60 + 35, SlotSection.EVENING),
        SlotSeed(12, 21 * 60 + 45, 22 * 60 + 30, SlotSection.EVENING),
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
