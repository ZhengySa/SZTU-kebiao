package com.classflow.timetable.ui.screen.edit

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import com.classflow.timetable.ui.theme.GlassScreenHeader
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.classflow.timetable.core.ui.PRESET_COLORS
import com.classflow.timetable.domain.model.Course
import com.classflow.timetable.domain.model.CourseSession
import com.classflow.timetable.domain.model.Conflict
import com.classflow.timetable.domain.model.DAY_LABELS
import com.classflow.timetable.domain.model.WeekMask
import com.classflow.timetable.domain.model.WeekRuleType
import com.classflow.timetable.domain.repository.TimetableRepository
import com.classflow.timetable.domain.usecase.DetectConflictsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

const val ARG_COURSE_ID = "courseId"

data class SessionDraft(
    val id: Long = 0L,
    val dayOfWeek: Int = 1,
    val startSlot: Int = 1,
    val endSlot: Int = 2,
    val weekRule: WeekRuleType = WeekRuleType.ALL,
    val customWeeks: Set<Int> = emptySet(),
    val locationOverride: String = "",
)

data class CourseEditUiState(
    val loading: Boolean = true,
    val semesterMissing: Boolean = false,
    val isNew: Boolean = true,
    val courseId: Long = 0L,
    val name: String = "",
    val teacher: String = "",
    val location: String = "",
    val colorArgb: Long = Course.DEFAULT_COLOR,
    val note: String = "",
    val sessions: List<SessionDraft> = listOf(SessionDraft()),
    val totalWeeks: Int = 20,
    val slotCount: Int = 12,
    val nameError: String? = null,
    val sessionError: String? = null,
    val conflicts: List<Conflict> = emptyList(),
    val showConflictDialog: Boolean = false,
    val saved: Boolean = false,
)

@HiltViewModel
class CourseEditViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    private val repository: TimetableRepository,
    private val detectConflicts: DetectConflictsUseCase,
) : ViewModel() {

    private val courseId: Long = savedStateHandle.get<Long>(ARG_COURSE_ID) ?: 0L

    private val _state = MutableStateFlow(CourseEditUiState())
    val uiState: StateFlow<CourseEditUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val semester = repository.getActiveSemester()
            val slots = repository.getTimeSlots()
            val slotCount = slots.size.coerceAtLeast(1)
            if (semester == null) {
                _state.update { it.copy(loading = false, semesterMissing = true) }
                return@launch
            }
            if (courseId == 0L) {
                _state.update {
                    it.copy(
                        loading = false,
                        isNew = true,
                        totalWeeks = semester.totalWeeks,
                        slotCount = slotCount,
                        sessions = listOf(SessionDraft(endSlot = if (slotCount >= 2) 2 else 1)),
                    )
                }
            } else {
                val course = repository.getCourse(courseId)
                if (course == null) {
                    _state.update { it.copy(loading = false) }
                    return@launch
                }
                val sessions = repository.getSessionsOfCourse(courseId)
                _state.value = CourseEditUiState(
                    loading = false,
                    isNew = false,
                    courseId = course.id,
                    name = course.name,
                    teacher = course.teacher.orEmpty(),
                    location = course.defaultLocation.orEmpty(),
                    colorArgb = course.colorArgb,
                    note = course.note.orEmpty(),
                    sessions = sessions.map { it.toDraft() }.ifEmpty { listOf(SessionDraft()) },
                    totalWeeks = semester.totalWeeks,
                    slotCount = slotCount,
                )
            }
        }
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value, nameError = null) }
    fun onTeacherChange(value: String) = _state.update { it.copy(teacher = value) }
    fun onLocationChange(value: String) = _state.update { it.copy(location = value) }
    fun onNoteChange(value: String) = _state.update { it.copy(note = value) }
    fun onColorChange(value: Long) = _state.update { it.copy(colorArgb = value) }

    /**
     * 所有时段都在同一个教室时，别让人一格一格填。
     *
     * 做法是清掉每个时段自己的地点，让它们统一回落到「默认地点」——
     * 而不是把地点抄进每一格。抄进去的话，之后改默认地点就会有格子不跟着变。
     */
    fun useLocationForAllSessions() = _state.update { state ->
        state.copy(sessions = state.sessions.map { it.copy(locationOverride = "") })
    }

    fun addSession() = _state.update {
        it.copy(sessions = it.sessions + SessionDraft(), sessionError = null)
    }

    fun removeSession(index: Int) = _state.update { state ->
        val next = state.sessions.filterIndexed { i, _ -> i != index }
        state.copy(sessions = next.ifEmpty { listOf(SessionDraft()) }, sessionError = null)
    }

    fun updateSession(index: Int, transform: (SessionDraft) -> SessionDraft) = _state.update { state ->
        state.copy(
            sessions = state.sessions.mapIndexed { i, draft -> if (i == index) transform(draft) else draft },
            sessionError = null,
        )
    }

    fun dismissConflictDialog() = _state.update { it.copy(showConflictDialog = false) }

    fun save(force: Boolean = false) {
        val state = _state.value
        val name = state.name.trim()
        if (name.isEmpty()) {
            _state.update { it.copy(nameError = "请填写课程名称") }
            return
        }
        if (state.sessions.isEmpty()) {
            _state.update { it.copy(sessionError = "至少需要一个上课时段") }
            return
        }
        state.sessions.forEachIndexed { index, draft ->
            if (draft.endSlot < draft.startSlot) {
                _state.update { it.copy(sessionError = "第 ${index + 1} 个时段：结束节次不能小于起始节次") }
                return
            }
            if (draft.weekRule == WeekRuleType.CUSTOM && draft.customWeeks.isEmpty()) {
                _state.update { it.copy(sessionError = "第 ${index + 1} 个时段：请至少选择一个周次") }
                return
            }
        }

        viewModelScope.launch {
            val semester = repository.getActiveSemester() ?: return@launch
            val domainSessions = state.sessions.map { draft ->
                CourseSession(
                    id = draft.id,
                    courseId = if (state.isNew) 0L else state.courseId,
                    dayOfWeek = draft.dayOfWeek,
                    startSlot = draft.startSlot,
                    endSlot = draft.endSlot,
                    weekRule = draft.weekRule,
                    weekMask = WeekMask.of(draft.weekRule, draft.customWeeks, semester.totalWeeks),
                    locationOverride = draft.locationOverride.trim().ifBlank { null },
                )
            }

            if (!force) {
                val conflicts = detectConflicts(
                    semester = semester,
                    courseId = if (state.isNew) 0L else state.courseId,
                    candidates = domainSessions,
                )
                if (conflicts.isNotEmpty()) {
                    _state.update { it.copy(conflicts = conflicts, showConflictDialog = true) }
                    return@launch
                }
            }

            val course = Course(
                id = if (state.isNew) 0L else state.courseId,
                semesterId = semester.id,
                name = name,
                teacher = state.teacher.trim().ifBlank { null },
                defaultLocation = state.location.trim().ifBlank { null },
                colorArgb = state.colorArgb,
                note = state.note.trim().ifBlank { null },
            )
            repository.saveCourse(course, domainSessions)
            _state.update { it.copy(saved = true) }
        }
    }
}

private fun CourseSession.toDraft() = SessionDraft(
    id = id,
    dayOfWeek = dayOfWeek,
    startSlot = startSlot,
    endSlot = endSlot,
    weekRule = weekRule,
    customWeeks = if (weekRule == WeekRuleType.CUSTOM) weekMask.toWeeks().toSet() else emptySet(),
    locationOverride = locationOverride.orEmpty(),
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseEditScreen(
    onDone: () -> Unit,
    onBack: () -> Unit,
    viewModel: CourseEditViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) {
        if (state.saved) onDone()
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            GlassScreenHeader(
                title = if (state.isNew) "新增课程" else "编辑课程",
                onBack = onBack,
                actions = {
                    TextButton(onClick = { viewModel.save() }) { Text("保存") }
                },
            )
        },
    ) { padding ->
        if (state.semesterMissing) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("还没有学期，请先到设置里添加学期")
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Card(shape = RoundedCornerShape(16.dp)) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = state.name,
                            onValueChange = viewModel::onNameChange,
                            label = { Text("课程名称 *") },
                            isError = state.nameError != null,
                            supportingText = state.nameError?.let { { Text(it) } },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = state.teacher,
                            onValueChange = viewModel::onTeacherChange,
                            label = { Text("教师") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = state.location,
                            onValueChange = viewModel::onLocationChange,
                            label = { Text("默认地点") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        if (state.sessions.size > 1) {
                            // 课表里大量课其实全学期同一个教室，逐格填是纯负担
                            TextButton(
                                onClick = viewModel::useLocationForAllSessions,
                                contentPadding = PaddingValues(horizontal = 4.dp, vertical = 0.dp),
                            ) {
                                Text("所有时段都用这个地点", style = MaterialTheme.typography.labelLarge)
                            }
                        }
                        OutlinedTextField(
                            value = state.note,
                            onValueChange = viewModel::onNoteChange,
                            label = { Text("备注") },
                            modifier = Modifier.fillMaxWidth(),
                        )
                        Text("颜色", style = MaterialTheme.typography.labelLarge)
                        ColorRow(selected = state.colorArgb, onSelect = viewModel::onColorChange)
                    }
                }
            }

            item {
                Text(
                    text = "上课时段（可添加多个）",
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            itemsIndexed(state.sessions) { index, draft ->
                SessionEditor(
                    index = index,
                    draft = draft,
                    slotCount = state.slotCount,
                    totalWeeks = state.totalWeeks,
                    canRemove = state.sessions.size > 1,
                    onUpdate = { transform -> viewModel.updateSession(index, transform) },
                    onRemove = { viewModel.removeSession(index) },
                )
            }

            item {
                OutlinedButton(onClick = viewModel::addSession, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Text("  添加时段")
                }
            }

            state.sessionError?.let { error ->
                item {
                    Text(
                        text = error,
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            item {
                Text(
                    text = "提示：同一门课的多个时段共用课程名、教师和颜色，只需在这里添加多次。",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (state.showConflictDialog) {
        AlertDialog(
            onDismissRequest = viewModel::dismissConflictDialog,
            title = { Text("时间冲突") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    state.conflicts.forEach { conflict ->
                        Text("· ${conflict.message}", style = MaterialTheme.typography.bodySmall)
                    }
                    Text("仍然可以保存，只是同一时间会有两门课。", style = MaterialTheme.typography.bodySmall)
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.dismissConflictDialog()
                        viewModel.save(force = true)
                    },
                ) { Text("仍然保存") }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissConflictDialog) { Text("返回修改") }
            },
        )
    }
}

@Composable
private fun SessionEditor(
    index: Int,
    draft: SessionDraft,
    slotCount: Int,
    totalWeeks: Int,
    canRemove: Boolean,
    onUpdate: ((SessionDraft) -> SessionDraft) -> Unit,
    onRemove: () -> Unit,
) {
    Card(
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)),
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("时段 ${index + 1}", style = MaterialTheme.typography.titleSmall)
                Box(Modifier.weight(1f))
                if (canRemove) {
                    IconButton(onClick = onRemove) {
                        Icon(Icons.Filled.Delete, contentDescription = "删除时段")
                    }
                }
            }

            Text("星期", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                DAY_LABELS.forEachIndexed { dayIndex, label ->
                    FilterChip(
                        selected = draft.dayOfWeek == dayIndex + 1,
                        onClick = { onUpdate { it.copy(dayOfWeek = dayIndex + 1) } },
                        label = { Text(label.removePrefix("周")) },
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                SlotDropdown(
                    label = "起始节",
                    value = draft.startSlot,
                    slotCount = slotCount,
                    modifier = Modifier.weight(1f),
                    onSelect = { slot ->
                        onUpdate {
                            it.copy(startSlot = slot, endSlot = if (it.endSlot < slot) slot else it.endSlot)
                        }
                    },
                )
                SlotDropdown(
                    label = "结束节",
                    value = draft.endSlot,
                    slotCount = slotCount,
                    modifier = Modifier.weight(1f),
                    onSelect = { slot ->
                        onUpdate { it.copy(endSlot = if (slot < it.startSlot) it.startSlot else slot) }
                    },
                )
            }

            Text("周次", style = MaterialTheme.typography.labelMedium)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                WeekRuleChip("每周", WeekRuleType.ALL, draft, onUpdate)
                WeekRuleChip("单周", WeekRuleType.ODD, draft, onUpdate)
                WeekRuleChip("双周", WeekRuleType.EVEN, draft, onUpdate)
                WeekRuleChip("指定", WeekRuleType.CUSTOM, draft, onUpdate)
            }

            if (draft.weekRule == WeekRuleType.CUSTOM) {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    (1..totalWeeks).chunked(7).forEach { rowWeeks ->
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            rowWeeks.forEach { week ->
                                FilterChip(
                                    selected = week in draft.customWeeks,
                                    onClick = {
                                        onUpdate {
                                            val next = it.customWeeks.toMutableSet()
                                            if (week in next) next.remove(week) else next.add(week)
                                            it.copy(customWeeks = next)
                                        }
                                    },
                                    label = { Text("$week") },
                                )
                            }
                        }
                    }
                }
            }

            OutlinedTextField(
                value = draft.locationOverride,
                onValueChange = { value -> onUpdate { it.copy(locationOverride = value) } },
                label = { Text("此时段地点（留空用默认地点）") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )
        }
    }
}

@Composable
private fun WeekRuleChip(
    label: String,
    rule: WeekRuleType,
    draft: SessionDraft,
    onUpdate: ((SessionDraft) -> SessionDraft) -> Unit,
) {
    FilterChip(
        selected = draft.weekRule == rule,
        onClick = { onUpdate { it.copy(weekRule = rule) } },
        label = { Text(label) },
    )
}

@Composable
private fun SlotDropdown(
    label: String,
    value: Int,
    slotCount: Int,
    modifier: Modifier = Modifier,
    onSelect: (Int) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Box(modifier) {
        OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
            Text("$label：第 $value 节")
        }
        DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            (1..slotCount).forEach { slot ->
                DropdownMenuItem(
                    text = { Text("第 $slot 节") },
                    onClick = {
                        expanded = false
                        onSelect(slot)
                    },
                )
            }
        }
    }
}

@Composable
private fun ColorRow(selected: Long, onSelect: (Long) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PRESET_COLORS.take(6).forEach { color ->
            ColorDot(color = color, selected = color == selected, onSelect = onSelect)
        }
    }
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        PRESET_COLORS.drop(6).forEach { color ->
            ColorDot(color = color, selected = color == selected, onSelect = onSelect)
        }
    }
}

@Composable
private fun ColorDot(color: Long, selected: Boolean, onSelect: (Long) -> Unit) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .clip(CircleShape)
            .background(Color(color))
            .border(
                width = if (selected) 3.dp else 0.dp,
                color = if (selected) MaterialTheme.colorScheme.onSurface else Color.Transparent,
                shape = CircleShape,
            )
            .clickable { onSelect(color) },
    )
}
