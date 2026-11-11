package com.classflow.timetable.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.DirectionsBus
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Map
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.classflow.timetable.ui.ShellViewModel
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.classflow.timetable.ui.screen.backup.BackupScreen
import com.classflow.timetable.ui.screen.backup.WidgetGuideScreen
import com.classflow.timetable.ui.screen.bus.BusScreen
import com.classflow.timetable.ui.screen.map.MapScreen
import com.classflow.timetable.ui.screen.nbw.NbwDetailScreen
import com.classflow.timetable.ui.screen.nbw.NbwScreen
import com.classflow.timetable.ui.screen.detail.CourseDetailScreen
import com.classflow.timetable.ui.screen.edit.CourseEditScreen
import com.classflow.timetable.ui.screen.grade.GradeScreen
import com.classflow.timetable.ui.screen.luck.LuckScreen
import com.classflow.timetable.ui.screen.help.AboutScreen
import com.classflow.timetable.ui.screen.help.PrivacyScreen
import com.classflow.timetable.ui.screen.imports.ImportScreen
import com.classflow.timetable.ui.screen.semester.SemesterScreen
import com.classflow.timetable.ui.screen.settings.SettingsScreen
import com.classflow.timetable.ui.screen.slots.SlotScreen
import com.classflow.timetable.ui.screen.sztu.SztuLoginScreen
import com.classflow.timetable.ui.screen.today.TodayScreen
import com.classflow.timetable.ui.screen.week.WeekScreen
import com.classflow.timetable.ui.theme.LiquidBackdrop
import com.classflow.timetable.ui.theme.LocalGlassPalette

private data class BottomItem(val route: String, val label: String, val icon: ImageVector)

/**
 * 底部 Tab 之间只做淡入淡出。
 *
 * 用默认转场（Compose Navigation 自带 700ms 淡入 + 缩放）会让人感觉"按了半天才过去"；
 * Tab 之间也不需要方向感，横滑反而多余。260~280ms 的短转场用在二三级页面就够了。
 */
private val tabEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition =
    { fadeIn(animationSpec = tween(170)) }

private val tabExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition =
    { fadeOut(animationSpec = tween(140)) }

private val BOTTOM_ITEMS = listOf(
    BottomItem(Routes.TODAY, "今日", Icons.Filled.DateRange),
    BottomItem(Routes.WEEK, "课表", Icons.Filled.List),
    BottomItem(Routes.BUS, "巴士", Icons.Filled.DirectionsBus),
    BottomItem(Routes.MAP, "地图", Icons.Filled.Map),
    BottomItem(Routes.SETTINGS, "设置", Icons.Filled.Settings),
)

@Composable
fun ClassFlowNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val palette = LocalGlassPalette.current
    // 自定义背景图：整个 App 只解码一次，铺在 NavHost 底下所有页面共用
    val shell: ShellViewModel = hiltViewModel()
    val backdrop by shell.backdrop.collectAsStateWithLifecycle()

    val onTabRoutes = BOTTOM_ITEMS.any { item ->
        currentDestination?.hierarchy?.any { it.route == item.route } == true
    }

    LiquidBackdrop(palette = palette, backdrop = backdrop) {
        Scaffold(
            containerColor = Color.Transparent,
            // 外层只管底部悬浮条；系统栏内边距交给各页面自己处理，
            // 否则内层 Scaffold 的 TopAppBar 会再叠一次状态栏高度，内容被顶下去一大截。
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (onTabRoutes) {
                    FloatingGlassBar(
                        selectedRoute = currentDestination?.route,
                        onSelect = { route ->
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) { saveState = true }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                    )
                }
            },
        ) { padding ->
            NavHost(
                navController = navController,
                startDestination = Routes.TODAY,
                modifier = Modifier.padding(padding),
                // 默认转场是 700ms 的淡入淡出，太慢；这里压到 280ms 并只给一点位移
                enterTransition = {
                    slideInHorizontally(
                        animationSpec = tween(280, easing = FastOutSlowInEasing),
                        initialOffsetX = { it / 10 },
                    ) + fadeIn(tween(200))
                },
                exitTransition = { fadeOut(tween(140)) },
                popEnterTransition = { fadeIn(tween(200)) },
                popExitTransition = {
                    slideOutHorizontally(
                        animationSpec = tween(260, easing = FastOutSlowInEasing),
                        targetOffsetX = { it / 10 },
                    ) + fadeOut(tween(180))
                },
            ) {
                composable(
                    route = Routes.TODAY,
                    enterTransition = tabEnter,
                    exitTransition = tabExit,
                    popEnterTransition = tabEnter,
                    popExitTransition = tabExit,
                ) {
                    TodayScreen(
                        onCourseClick = { courseId -> navController.navigate(Routes.courseDetail(courseId)) },
                        onAddCourse = { navController.navigate(Routes.courseEdit(null)) },
                        onOpenSync = { navController.navigate(Routes.SZTU_LOGIN) },
                        onOpenNbw = { navController.navigate(Routes.NBW) },
                        onImportPartner = { navController.navigate(Routes.PARTNER_LOGIN) },
                    )
                }

                composable(
                    route = Routes.WEEK,
                    enterTransition = tabEnter,
                    exitTransition = tabExit,
                    popEnterTransition = tabEnter,
                    popExitTransition = tabExit,
                ) {
                    WeekScreen(
                        onCourseClick = { courseId -> navController.navigate(Routes.courseDetail(courseId)) },
                        onAddCourse = { navController.navigate(Routes.courseEdit(null)) },
                        onImportPartner = { navController.navigate(Routes.PARTNER_LOGIN) },
                    )
                }

                composable(
                    route = Routes.BUS,
                    enterTransition = tabEnter,
                    exitTransition = tabExit,
                    popEnterTransition = tabEnter,
                    popExitTransition = tabExit,
                ) {
                    BusScreen()
                }

                composable(
                    route = Routes.MAP,
                    enterTransition = tabEnter,
                    exitTransition = tabExit,
                    popEnterTransition = tabEnter,
                    popExitTransition = tabExit,
                ) {
                    MapScreen()
                }

                composable(Routes.PARTNER_LOGIN) {
                    SztuLoginScreen(
                        forPartner = true,
                        onBack = { navController.popBackStack() },
                        onDone = { navController.popBackStack() },
                    )
                }

                composable(Routes.GRADES) {
                    GradeScreen(
                        onBack = { navController.popBackStack() },
                        onLogin = { navController.navigate(Routes.SZTU_LOGIN) },
                    )
                }

                composable(Routes.NBW) {
                    NbwScreen(
                        onOpenDetail = { path -> navController.navigate(Routes.nbwDetail(path)) },
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(
                    route = Routes.NBW_DETAIL,
                    arguments = listOf(
                        navArgument(Routes.ARG_NBW_PATH) {
                            type = NavType.StringType
                            defaultValue = ""
                        },
                    ),
                ) { entry ->
                    val path = entry.arguments?.getString(Routes.ARG_NBW_PATH).orEmpty()
                    NbwDetailScreen(
                        path = path,
                        // 详情页里点「上一篇/下一篇」不叠新栈，直接替换当前条目
                        onOpenDetail = { next ->
                            navController.navigate(Routes.nbwDetail(next)) {
                                popUpTo(Routes.NBW_DETAIL) { inclusive = true }
                            }
                        },
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(Routes.LUCK) {
                    LuckScreen(onBack = { navController.popBackStack() })
                }

                composable(
                    route = Routes.SETTINGS,
                    enterTransition = tabEnter,
                    exitTransition = tabExit,
                    popEnterTransition = tabEnter,
                    popExitTransition = tabExit,
                ) {
                    SettingsScreen(
                        onOpenSemester = { navController.navigate(Routes.SEMESTER) },
                        onOpenSlots = { navController.navigate(Routes.SLOTS) },
                        onOpenImport = { navController.navigate(Routes.IMPORT) },
                        onOpenSztu = { navController.navigate(Routes.SZTU_LOGIN) },
                        onOpenGrades = { navController.navigate(Routes.GRADES) },
                        onOpenLuck = { navController.navigate(Routes.LUCK) },
                        onOpenNbw = { navController.navigate(Routes.NBW) },
                        onOpenBackup = { navController.navigate(Routes.BACKUP) },
                        onOpenWidgetGuide = { navController.navigate(Routes.WIDGET_GUIDE) },
                        onOpenPrivacy = { navController.navigate(Routes.PRIVACY) },
                        onOpenAbout = { navController.navigate(Routes.ABOUT) },
                    )
                }

                composable(Routes.SZTU_LOGIN) {
                    SztuLoginScreen(
                        onBack = { navController.popBackStack() },
                        onDone = { navController.popBackStack() },
                    )
                }

                composable(Routes.IMPORT) {
                    ImportScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.BACKUP) {
                    BackupScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.WIDGET_GUIDE) {
                    WidgetGuideScreen(onBack = { navController.popBackStack() })
                }

                composable(
                    route = Routes.COURSE_DETAIL,
                    arguments = listOf(navArgument(Routes.ARG_COURSE_ID) { type = NavType.LongType }),
                ) {
                    CourseDetailScreen(
                        onBack = { navController.popBackStack() },
                        onEdit = { courseId -> navController.navigate(Routes.courseEdit(courseId)) },
                        onDeleted = { navController.popBackStack() },
                    )
                }

                composable(
                    route = Routes.COURSE_EDIT,
                    arguments = listOf(
                        navArgument(Routes.ARG_COURSE_ID) {
                            type = NavType.LongType
                            defaultValue = 0L
                        },
                    ),
                ) {
                    CourseEditScreen(
                        onDone = { navController.popBackStack() },
                        onBack = { navController.popBackStack() },
                    )
                }

                composable(Routes.SEMESTER) {
                    SemesterScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.SLOTS) {
                    SlotScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.PRIVACY) {
                    PrivacyScreen(onBack = { navController.popBackStack() })
                }

                composable(Routes.ABOUT) {
                    AboutScreen(onBack = { navController.popBackStack() })
                }
            }
        }
    }
}

/** 悬浮玻璃胶囊导航：不贴底、不铺满，让背景透出来。 */
@Composable
private fun FloatingGlassBar(
    selectedRoute: String?,
    onSelect: (String) -> Unit,
) {
    val palette = LocalGlassPalette.current
    val shape = RoundedCornerShape(28.dp)

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 34.dp)
            .padding(bottom = 14.dp),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(shape)
                .background(palette.glassFillTop)
                .border(1.dp, palette.glassBorderTop, shape)
                .padding(horizontal = 8.dp, vertical = 8.dp),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            BOTTOM_ITEMS.forEach { item ->
                val selected = selectedRoute == item.route
                // Tab 切换是最高频的交互，颜色和图标都跟着手指走，而不是"啪"地跳变
                val tint by animateColorAsState(
                    targetValue = if (selected) palette.accent else palette.textTertiary,
                    animationSpec = tween(200),
                    label = "tabTint",
                )
                val iconScale by animateFloatAsState(
                    targetValue = if (selected) 1.08f else 1f,
                    animationSpec = spring(stiffness = Spring.StiffnessMediumLow),
                    label = "tabScale",
                )
                Column(
                    modifier = Modifier
                        .clip(RoundedCornerShape(20.dp))
                        .clickable { onSelect(item.route) }
                        // 5 个 Tab 时横向留白要收窄，否则最右边的「设置」会被挤出屏幕
                        .padding(horizontal = 13.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        item.icon,
                        contentDescription = item.label,
                        tint = tint,
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer {
                                scaleX = iconScale
                                scaleY = iconScale
                            },
                    )
                    Text(
                        item.label,
                        color = tint,
                        fontSize = 9.5.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
