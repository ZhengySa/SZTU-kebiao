package com.classflow.timetable.ui.navigation

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Timer
import androidx.compose.ui.graphics.vector.ImageVector
import com.classflow.timetable.domain.model.NavTab

/**
 * [NavTab] → 路由 / 图标。
 *
 * 单独放在 UI 层是因为 `domain.model` 不该 import Compose；枚举那边只管「有哪些入口」，
 * 怎么画、跳到哪，由这里决定。
 */
fun NavTab.route(): String = when (this) {
    NavTab.TODAY -> Routes.TODAY
    NavTab.WEEK -> Routes.WEEK
    NavTab.POMODORO -> Routes.POMODORO
    NavTab.BUS -> Routes.BUS
    NavTab.MAP -> Routes.MAP
    NavTab.SETTINGS -> Routes.SETTINGS
}

fun NavTab.icon(): ImageVector = when (this) {
    NavTab.TODAY -> Icons.Filled.DateRange
    NavTab.WEEK -> Icons.Filled.List
    NavTab.POMODORO -> Icons.Filled.Timer
    NavTab.BUS -> Icons.Filled.DirectionsBus
    NavTab.MAP -> Icons.Filled.Map
    NavTab.SETTINGS -> Icons.Filled.Settings
}

/** 完整的一句话给设置页用：告诉用户这个开关关掉以后会发生什么。 */
fun NavTab.hint(visible: Boolean): String = when {
    fixed -> "固定保留 · 收起来的页面都从这里重新打开"
    visible -> "显示在底部栏 · 点这一行可以直接打开"
    else -> "已从底部栏收起 · 点这一行照样能打开"
}
