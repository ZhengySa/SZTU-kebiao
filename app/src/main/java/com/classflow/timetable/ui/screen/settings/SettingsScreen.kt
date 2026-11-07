package com.classflow.timetable.ui.screen.settings

import android.Manifest
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.classflow.timetable.core.time.AppClock
import com.classflow.timetable.domain.model.AppSettings
import com.classflow.timetable.domain.model.Semester
import com.classflow.timetable.domain.model.ThemeMode
import com.classflow.timetable.domain.repository.SettingsRepository
import com.classflow.timetable.domain.repository.TimetableRepository
import com.classflow.timetable.domain.usecase.WeekResolver
import com.classflow.timetable.reminder.NotificationHelper
import com.classflow.timetable.reminder.ReminderScheduler
import com.classflow.timetable.ui.theme.GlassCard
import com.classflow.timetable.ui.theme.GlassPalette
import com.classflow.timetable.ui.theme.LiquidBackdrop
import com.classflow.timetable.ui.theme.LocalGlassPalette
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val settingsRepository: SettingsRepository,
    timetableRepository: TimetableRepository,
    private val clock: AppClock,
    private val accountStore: com.classflow.timetable.data.local.SztuAccountStore,
    @ApplicationContext private val context: android.content.Context,
) : ViewModel() {

    val settings: StateFlow<AppSettings> = settingsRepository.settings.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AppSettings(),
    )

    val semester: StateFlow<Semester?> = timetableRepository.observeActiveSemester()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /**
     * 教务系统是否同步过。
     * 注意不能拿「有没有学期」来判断——首次启动就有一个默认学期，
     * 那样一进设置页就显示"已连接"，是假的。这里只认真正登录同步成功后写下的学期名。
     */
    val sztuSemesterName: StateFlow<String> = accountStore.semesterName
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    /** 情侣课表：导过谁的课表，用来在设置里显示状态。 */
    val partnerUsername: StateFlow<String> = accountStore.partnerUsername
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "")

    /** 清掉学校的会话 Cookie / 学期记录；下次进同步页要重新走一遍学校登录页。 */
    fun forgetSztuLogin() {
        viewModelScope.launch { accountStore.forgetEverything() }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setReminderEnabled(enabled: Boolean) {
        viewModelScope.launch {
            settingsRepository.setReminderEnabled(enabled)
            if (enabled) ReminderScheduler.rescheduleAll(context) else ReminderScheduler.cancelAll(context)
        }
    }

    fun setReminderMinutes(minutes: Int) {
        viewModelScope.launch {
            settingsRepository.setReminderMinutes(minutes)
            ReminderScheduler.rescheduleAll(context)
        }
    }

    fun hasNotificationPermission(): Boolean = NotificationHelper.hasPermission(context)

    fun currentWeekLabel(semester: Semester?): String {
        if (semester == null) return "未设置"
        val state = WeekResolver.resolve(semester, clock.today())
        return "第 ${state.week} 周" + if (state.isOverridden) "（手动）" else ""
    }
}

private val REMINDER_MINUTE_OPTIONS = listOf(5, 10, 15, 20, 30)

@Composable
fun SettingsScreen(
    onOpenSemester: () -> Unit,
    onOpenSlots: () -> Unit,
    onOpenImport: () -> Unit,
    onOpenSztu: () -> Unit,
    onOpenGrades: () -> Unit,
    onOpenLuck: () -> Unit,
    onOpenNbw: () -> Unit,
    onOpenBackup: () -> Unit,
    onOpenWidgetGuide: () -> Unit,
    onOpenPrivacy: () -> Unit,
    onOpenAbout: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val semester by viewModel.semester.collectAsStateWithLifecycle()
    val sztuSemester by viewModel.sztuSemesterName.collectAsStateWithLifecycle()
    val palette = LocalGlassPalette.current
    var permissionHint by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        viewModel.setReminderEnabled(granted)
        permissionHint = !granted
    }

    fun toggleReminder(enabled: Boolean) {
        if (enabled && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            !viewModel.hasNotificationPermission()
        ) {
            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            viewModel.setReminderEnabled(enabled)
            permissionHint = enabled && !viewModel.hasNotificationPermission()
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .statusBarsPadding()
            .padding(horizontal = 18.dp)
            .padding(top = 12.dp, bottom = 120.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    Icons.Filled.Settings,
                    contentDescription = null,
                    tint = palette.textSecondary,
                    modifier = Modifier.size(20.dp),
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "设置",
                    color = palette.textPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                )
            }

            SyncHeroCard(palette = palette, sztuSemester = sztuSemester, onClick = onOpenSztu)

            if (sztuSemester.isNotBlank()) {
                Text(
                    "忘记学校登录（清掉本机保存的会话）",
                    color = palette.danger,
                    fontSize = 12.sp,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = viewModel::forgetSztuLogin)
                        .padding(start = 6.dp, top = 2.dp, bottom = 2.dp),
                )
            }

            GlassSection(palette, "百宝箱") {
                GlassRow(
                    palette = palette,
                    title = "公文通",
                    subtitle = "学校通知公告 · 按教务/科研/行政/学工/校园筛选",
                    onClick = onOpenNbw,
                )
                GlassRow(
                    palette = palette,
                    title = "今日运气",
                    subtitle = "抽一个 0-100 的数，看看今天顺不顺",
                    onClick = onOpenLuck,
                    last = true,
                )
            }

            GlassSection(palette, "成绩") {
                GlassRow(
                    palette = palette,
                    title = "成绩与绩点",
                    subtitle = "课程成绩 · 总绩点 · 排名，可切换学期",
                    onClick = onOpenGrades,
                    last = true,
                )
            }

            GlassSection(palette, "课表") {
                GlassRow(palette, "学期", semester?.let {
                    "${it.name} · ${viewModel.currentWeekLabel(it)} · 共 ${it.totalWeeks} 周"
                } ?: "未设置", onOpenSemester)
                GlassRow(palette, "节次时间", "每节课的开始 / 结束时间", onOpenSlots)
                GlassRow(palette, "文件导入", "ICS 日历、CSV 表格，导入前可预览", onOpenImport, last = true)
            }

            GlassSection(palette, "课前提醒") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        Text("上课前提醒", color = palette.textPrimary, fontSize = 15.sp)
                        Text(
                            if (viewModel.hasNotificationPermission()) {
                                "提前 ${settings.reminderMinutes} 分钟通知"
                            } else {
                                "通知权限未开启，提醒不会出现"
                            },
                            color = palette.textTertiary,
                            fontSize = 11.sp,
                        )
                    }
                    Switch(
                        checked = settings.reminderEnabled,
                        onCheckedChange = { toggleReminder(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = palette.accent),
                    )
                }

                if (settings.reminderEnabled) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp).padding(bottom = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        REMINDER_MINUTE_OPTIONS.forEach { minutes ->
                            GlassChip(
                                label = "$minutes 分",
                                selected = settings.reminderMinutes == minutes,
                                palette = palette,
                            ) { viewModel.setReminderMinutes(minutes) }
                        }
                    }
                }

                if (permissionHint) {
                    Text(
                        "系统没给通知权限，提醒不会显示。可去「设置 → 应用 → 课表 → 通知」里打开。",
                        color = palette.danger,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 18.dp).padding(bottom = 12.dp),
                    )
                }
            }

            GlassSection(palette, "桌面与数据") {
                GlassRow(palette, "桌面小组件", "今日课表 · 4×2 / 4×4", onOpenWidgetGuide)
                GlassRow(palette, "备份与恢复", "导出 JSON，换手机时导回来", onOpenBackup, last = true)
            }

            GlassSection(palette, "外观") {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(18.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    GlassChip("跟随系统", settings.themeMode == ThemeMode.SYSTEM, palette) {
                        viewModel.setThemeMode(ThemeMode.SYSTEM)
                    }
                    GlassChip("浅色", settings.themeMode == ThemeMode.LIGHT, palette) {
                        viewModel.setThemeMode(ThemeMode.LIGHT)
                    }
                    GlassChip("深色", settings.themeMode == ThemeMode.DARK, palette) {
                        viewModel.setThemeMode(ThemeMode.DARK)
                    }
                }
            }

            GlassSection(palette, "其他") {
                GlassRow(palette, "隐私说明", "数据存在哪、发去哪", onOpenPrivacy)
                GlassRow(palette, "关于", "版本 1.0.0", onOpenAbout, last = true)
            }
        }
}

/** 教务系统同步的入口做成整块高亮卡片，它现在是这个 App 最主要的功能。 */
@Composable
private fun SyncHeroCard(palette: GlassPalette, sztuSemester: String, onClick: () -> Unit) {
    val shape = RoundedCornerShape(26.dp)
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(shape)
            .background(
                Brush.linearGradient(
                    listOf(
                        palette.accent.copy(alpha = if (palette.isDark) 0.42f else 0.86f),
                        (if (palette.isDark) Color(0xFF6E5BD8) else Color(0xFF7B62FF))
                            .copy(alpha = if (palette.isDark) 0.38f else 0.86f),
                    ),
                ),
            )
            .border(1.dp, Color.White.copy(alpha = 0.35f), shape)
            .clickable(onClick = onClick)
            .padding(20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    "同步学校教务系统",
                    color = Color.White,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(
                    sztuSemester.takeIf { it.isNotBlank() }?.let { "已同步 · $it" }
                        ?: "登录一次，自动抓取整学期课表",
                    color = Color.White.copy(alpha = 0.85f),
                    fontSize = 12.sp,
                )
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = Color.White,
            )
        }
    }
}

@Composable
private fun GlassSection(
    palette: GlassPalette,
    title: String,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            title,
            color = palette.textTertiary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.padding(start = 8.dp),
        )
        GlassCard(Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
            Column(content = content)
        }
    }
}

@Composable
private fun GlassRow(
    palette: GlassPalette,
    title: String,
    subtitle: String,
    onClick: () -> Unit,
    last: Boolean = false,
) {
    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onClick)
                .padding(horizontal = 18.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = palette.textPrimary, fontSize = 15.sp)
                Text(subtitle, color = palette.textTertiary, fontSize = 11.sp)
            }
            Icon(
                Icons.Filled.ChevronRight,
                contentDescription = null,
                tint = palette.textTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
        if (!last) {
            HorizontalDivider(
                modifier = Modifier.padding(start = 18.dp),
                color = palette.textPrimary.copy(alpha = 0.06f),
            )
        }
    }
}

@Composable
private fun GlassChip(
    label: String,
    selected: Boolean,
    palette: GlassPalette,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(12.dp)
    Box(
        modifier = Modifier
            .clip(shape)
            .background(if (selected) palette.accentSoft else Color.Transparent)
            .border(
                width = 1.dp,
                color = if (selected) palette.accent.copy(alpha = 0.5f) else palette.textPrimary.copy(alpha = 0.10f),
                shape = shape,
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 13.dp, vertical = 8.dp),
    ) {
        Text(
            label,
            color = if (selected) palette.accent else palette.textSecondary,
            fontSize = 12.sp,
            fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
        )
    }
}
