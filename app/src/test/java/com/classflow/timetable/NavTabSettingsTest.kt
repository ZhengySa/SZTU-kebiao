package com.classflow.timetable

import com.classflow.timetable.domain.model.AppSettings
import com.classflow.timetable.domain.model.NavTab
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 底部栏 / 首页这两个新设置的持久化规则。
 *
 * 这里的断言盯着一件事：**坏数据不能让底部栏消失**。
 * 设置是用户点出来的，一旦读崩了，他连"进设置改回来"的入口都没有。
 */
class NavTabSettingsTest {

    @Test
    fun `默认全开，和升级前一致`() {
        val settings = AppSettings()
        assertEquals(NavTab.entries.toSet(), settings.bottomTabs)
        assertEquals(NavTab.TODAY, settings.homeTab)
    }

    @Test
    fun `没存过就退回默认`() {
        assertEquals(NavTab.entries.toSet(), NavTab.decode(null))
    }

    @Test
    fun `认得出的名字照收，认不出的丢掉`() {
        assertEquals(
            setOf(NavTab.TODAY, NavTab.WEEK, NavTab.SETTINGS),
            NavTab.decode("TODAY,WEEK,PARTNER"),
        )
    }

    @Test
    fun `设置永远补回来——它是唯一能把入口重新打开的开关`() {
        val decoded = NavTab.decode("BUS")
        assertTrue(NavTab.SETTINGS in decoded)
        assertEquals(setOf(NavTab.BUS, NavTab.SETTINGS), decoded)
    }

    @Test
    fun `只留一个也不崩`() {
        assertEquals(setOf(NavTab.SETTINGS), NavTab.decode("SETTINGS"))
    }

    @Test
    fun `空串和垃圾串都退到只剩设置，不会变成默认全开`() {
        // 用户主动把四个入口全收起时写进去的就是 "SETTINGS"；
        // 而空串只可能来自外部写坏，此时给他一个能改回来的最小集合就够
        assertEquals(setOf(NavTab.SETTINGS), NavTab.decode(""))
        assertEquals(setOf(NavTab.SETTINGS), NavTab.decode(" , ,"))
    }

    @Test
    fun `写出去按枚举顺序，不跟着点击先后`() {
        val tabs = setOf(NavTab.SETTINGS, NavTab.TODAY, NavTab.MAP)
        assertEquals("TODAY,MAP,SETTINGS", NavTab.encode(tabs))
    }

    @Test
    fun `存取一轮回来还是原样`() {
        val tabs = setOf(NavTab.WEEK, NavTab.BUS, NavTab.SETTINGS)
        assertEquals(tabs, NavTab.decode(NavTab.encode(tabs)))
    }

    @Test
    fun `设置不能被隐藏`() {
        assertTrue(NavTab.SETTINGS.fixed)
        assertFalse(NavTab.TODAY.fixed)
        assertFalse(NavTab.WEEK.fixed)
        assertFalse(NavTab.BUS.fixed)
        assertFalse(NavTab.MAP.fixed)
    }
}
