package com.classflow.timetable.domain

import com.classflow.timetable.domain.model.BusData
import com.google.common.truth.Truth.assertThat
import org.junit.Test
import java.time.LocalTime

/**
 * 校园巴士时刻表的数据完整性测试 —— 数据是照截图手工录的，
 * 最怕录漏一个格子，这里用总量和抽检双重校验。
 */
class BusDataTest {

    @Test
    fun `每个方向的四张表都存在且非空`() {
        BusData.Direction.entries.forEach { direction ->
            BusData.Stop.entries.forEach { stop ->
                val table = BusData.timetable(direction, stop, workday = true)
                assertThat(table).isNotNull()
                assertThat(table!!.keys).hasSize(16) // 07~22 点整
                assertThat(table.values.sumOf { it.size }).isAtLeast(60)
            }
        }
    }

    @Test
    fun `所有分钟值合法且递增`() {
        BusData.Direction.entries.forEach { direction ->
            BusData.Stop.entries.forEach { stop ->
                BusData.timetable(direction, stop, workday = true)!!.forEach { (hour, minutes) ->
                    assertThat(hour).isIn(7..22)
                    assertThat(minutes).isInOrder()
                    minutes.forEach { minute ->
                        assertThat(minute).isIn(0..59)
                    }
                }
            }
        }
    }

    @Test
    fun `北区发车表抽检与截图一致`() {
        val table = BusData.timetable(BusData.Direction.NORTH, BusData.Stop.DEPART, workday = true)!!
        assertThat(table[7]).containsExactly(0, 10, 20, 30, 40, 45, 50, 52, 56).inOrder()
        assertThat(table[12]).containsExactly(0, 5, 10, 15, 26, 28, 35, 45, 55).inOrder()
        assertThat(table[22]).containsExactly(0, 15, 30).inOrder()
    }

    @Test
    fun `南区教学楼表抽检与截图一致`() {
        val table = BusData.timetable(BusData.Direction.SOUTH, BusData.Stop.TEACH, workday = true)!!
        assertThat(table[7]).containsExactly(4, 14, 24, 34, 44, 48, 50, 56).inOrder()
        assertThat(table[18]).containsExactly(4, 11, 21, 29, 34, 36, 42, 44, 46, 52, 57, 59).inOrder()
        assertThat(table[22]).containsExactly(4, 19).inOrder()
    }

    @Test
    fun `下一班车返回不早于当前时刻的最近一班`() {
        val next = BusData.nextDeparture(
            BusData.Direction.NORTH,
            BusData.Stop.DEPART,
            LocalTime.of(12, 0),
        )
        assertThat(next).isEqualTo(LocalTime.of(12, 0))

        val after = BusData.nextDeparture(
            BusData.Direction.NORTH,
            BusData.Stop.DEPART,
            LocalTime.of(12, 1),
        )
        assertThat(after).isEqualTo(LocalTime.of(12, 5))
    }

    @Test
    fun `末班车之后返回null`() {
        val next = BusData.nextDeparture(
            BusData.Direction.SOUTH,
            BusData.Stop.TERMINAL,
            LocalTime.of(22, 30),
        )
        assertThat(next).isNull()
    }

    @Test
    fun `休息日也有完整时刻表`() {
        BusData.Direction.entries.forEach { direction ->
            BusData.Stop.entries.forEach { stop ->
                val table = BusData.timetable(direction, stop, workday = false)
                assertThat(table).isNotNull()
                assertThat(table!!.keys).hasSize(16)
                assertThat(table.values.sumOf { it.size }).isAtLeast(40)

                // 休息日班次一定比工作日少（截图里两页对比确实如此）
                val workday = BusData.timetable(direction, stop, workday = true)!!
                assertThat(table.values.sumOf { it.size })
                    .isLessThan(workday.values.sumOf { it.size })
            }
        }
    }

    @Test
    fun `休息日发车表抽检与截图一致`() {
        val north = BusData.timetable(BusData.Direction.NORTH, BusData.Stop.DEPART, workday = false)!!
        assertThat(north[7]).containsExactly(0, 12, 22, 32, 42, 52).inOrder()
        assertThat(north[9]).containsExactly(2, 7, 12, 17, 22, 32, 44, 56).inOrder()
        assertThat(north[11]).containsExactly(8, 20, 32, 47).inOrder()
        assertThat(north[22]).containsExactly(2, 15, 30).inOrder()

        val south = BusData.timetable(BusData.Direction.SOUTH, BusData.Stop.DEPART, workday = false)!!
        assertThat(south[8]).containsExactly(0, 10, 20, 30, 40, 50, 55).inOrder()
        assertThat(south[15]).containsExactly(0, 10, 20, 30, 35, 40, 50).inOrder()
        assertThat(south[22]).containsExactly(2, 15).inOrder()
    }

    @Test
    fun `休息日到站表比对应发车表晚到`() {
        // 休息日北区：发车 07:00 → 地铁站 07:01 → 教学楼 07:04 → 北区 07:08
        val depart = BusData.timetable(BusData.Direction.NORTH, BusData.Stop.DEPART, workday = false)!!
        val metro = BusData.timetable(BusData.Direction.NORTH, BusData.Stop.METRO, workday = false)!!
        val teach = BusData.timetable(BusData.Direction.NORTH, BusData.Stop.TEACH, workday = false)!!
        val terminal = BusData.timetable(BusData.Direction.NORTH, BusData.Stop.TERMINAL, workday = false)!!

        assertThat(depart[7]!!.first()).isEqualTo(0)
        assertThat(metro[7]!!.first()).isEqualTo(1)
        assertThat(teach[7]!!.first()).isEqualTo(4)
        assertThat(terminal[7]!!.first()).isEqualTo(8)
    }

    @Test
    fun `休息日的下一班车`() {
        val next = BusData.nextDeparture(
            BusData.Direction.NORTH,
            BusData.Stop.DEPART,
            LocalTime.of(13, 0),
            workday = false,
        )
        // 休息日北区 13 点：02 12 22 32 42 52
        assertThat(next).isEqualTo(LocalTime.of(13, 2))
    }

    @Test
    fun `工作日判断`() {
        assertThat(BusData.isWorkday(1)).isTrue()
        assertThat(BusData.isWorkday(5)).isTrue()
        assertThat(BusData.isWorkday(6)).isFalse()
        assertThat(BusData.isWorkday(7)).isFalse()
    }

    @Test
    fun `班次总数与表格一致`() {
        val north = BusData.timetable(BusData.Direction.NORTH, BusData.Stop.METRO, workday = true)!!
        val expected = north.values.sumOf { it.size }
        assertThat(BusData.departuresOfDay(BusData.Direction.NORTH, BusData.Stop.METRO)).isEqualTo(expected)
    }
}
