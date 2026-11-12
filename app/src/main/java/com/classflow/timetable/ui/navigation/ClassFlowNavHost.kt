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
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.classflow.timetable.domain.model.BackdropStrength
import com.classflow.timetable.domain.model.NavTab
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
import com.classflow.timetable.ui.screen.pomodoro.PomodoroScreen
import com.classflow.timetable.ui.screen.detail.CourseDetailScreen
import com.classflow.timetable.ui.screen.edit.CourseEditScreen
import com.classflow.timetable.ui.screen.exam.ExamScreen
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

@Composable
fun ClassFlowNavHost() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val palette = LocalGlassPalette.current
    // 自定义背景图：整个 App 只解码一次，铺在 NavHost 底下所有页面共用
    val shell: ShellViewModel = hiltViewModel()
    val backdrop by shell.backdrop.collectAsStateWithLifecycle()
    val loadedSettings by shell.settings.collectAsStateWithLifecycle()

    // 首页 / 底部栏都由设置决定，所以设置没读上来之前先不建导航图。
    // 先按默认值建一次、读到真值再重建，会让返回栈被重置，肉眼上就是「闪一下回首页」；
    // 更糟的是首页设置会停留在默认的「今日」，用户在设置里选了课表也不生效。
    // collectAsStateWithLifecycle 读出来的值每次读取都可能变，编译器不会替我们收窄类型，
    // 所以先落成一个局部 val —— 过了这个 null 检查它就是非空的。
    val settingsOrNull = loadedSettings
    if (settingsOrNull == null) {
        LiquidBackdrop(
            palette = palette,
            backdrop = backdrop,
            strength = BackdropStrength.MEDIUM,
        ) {
            Box(Modifier.fillMaxSize())
        }
        return
    }
    val shellSettings = settingsOrNull

    // 收起 / 放回入口只是过滤这一个集合；按枚举顺序排，不跟用户点开关的先后乱跳
    val bottomTabs = remember(shellSettings.bottomTabs) {
        shellSettings.bottomTabs.sortedBy { it.ordinal }
    }

    // 底部栏是否出现，看的永远是「所有入口」，不是「当前显示的那几个」。
    // 否则用户把某个 Tab 收起来之后，从设置里点进去就再也看不到底栏，等于被关在里面。
    val onTabRoutes = NavTab.entries.any { tab ->
        currentDestination?.hierarchy?.any { it.route == tab.route() } == true
    }

    // 只在建图那一刻取一次：在设置里改「首页」不该把用户从当前页拽走，下次冷启动才生效
    val startRoute = remember { shellSettings.homeTab.route() }

    LiquidBackdrop(
        palette = palette,
        backdrop = backdrop,
        strength = shellSettings.backdropStrength,
    ) {
        Scaffold(
            containerColor = Color.Transparent,
            // 外层只管底部悬浮条；系统栏内边距交给各页面自己处理，
            // 否则内层 Scaffold 的 TopAppBar 会再叠一次状态栏高度，内容被顶下去一大截。
            contentWindowInsets = WindowInsets(0, 0, 0, 0),
            bottomBar = {
                if (onTabRoutes) {
                    FloatingGlassBar(
                        tabs = bottomTabs,
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
                startDestination = startRoute,
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
                    route = Routes.POMODORO,
                    enterTransition = tabEnter,
                    exitTransition = tabExit,
                    popEnterTransition = tabEnter,
                    popExitTransition = tabExit,
                ) {
                    PomodoroScreen(onBack = { navController.popBackStack() })
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

                composable(Routes.EXAMS) {
                    ExamScreen(
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
                        onOpenExams = { navController.navigate(Routes.EXAMS) },
                        onOpenLuck = { navController.navigate(Routes.LUCK) },
                        onOpenNbw = { navController.navigate(Routes.NBW) },
                        onOpenBackup = { navController.navigate(Routes.BACKUP) },
                        onOpenWidgetGuide = { navController.navigate(Routes.WIDGET_GUIDE) },
                        onOpenPrivacy = { navController.navigate(Routes.PRIVACY) },
                        onOpenAbout = { navController.navigate(Routes.ABOUT) },
                        onOpenPomodoro = { navController.navigate(Routes.POMODORO) },
                        // 从设置里点开一个页面：只往后压一层，**不做 popUpTo**。
                        // 1.0.5 用的是底部栏那套「回到起始页再进去」，结果从设置点开、
                        // 按返回时人已经被弹回首页了 —— 看起来就像「点开一下就被拿走」。
                        // 现在返回一定回到设置，收起来的页面随时能再来一次。
                        onOpenTab = { tab ->
                            if (tab != NavTab.SETTINGS) {
                                navController.navigate(tab.route()) { launchSingleTop = true }
                            }
                        },
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

/**
 * 悬浮玻璃胶囊导航：不贴底、不铺满，让背景透出来。
 *
 * [tabs] 是用户在设置里挑出来的入口，数量会变，所以横向留白跟着数量走：
 * 五个按钮时不能留太多白（最右边会被挤出屏幕），只剩一两个时也不能让胶囊横成一条面包。
 */
@Composable
private fun FloatingGlassBar(
    tabs: List<NavTab>,
    selectedRoute: String?,
    onSelect: (String) -> Unit,
) {
    val palette = LocalGlassPalette.current
    val shape = RoundedCornerShape(28.dp)
    val sideInset = when (tabs.size) {
        0, 1 -> 110.dp
        2 -> 72.dp
        3 -> 48.dp
        else -> 34.dp
    }
    val itemPadding = if (tabs.size <= 3) 22.dp else 13.dp

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = sideInset)
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
            tabs.forEach { tab ->
                val selected = selectedRoute == tab.route()
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
                        .clickable { onSelect(tab.route()) }
                        .padding(horizontal = itemPadding, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(3.dp),
                ) {
                    Icon(
                        tab.icon(),
                        contentDescription = tab.label,
                        tint = tint,
                        modifier = Modifier
                            .size(20.dp)
                            .graphicsLayer {
                                scaleX = iconScale
                                scaleY = iconScale
                            },
                    )
                    Text(
                        tab.label,
                        color = tint,
                        fontSize = 9.5.sp,
                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                    )
                }
            }
        }
    }
}
