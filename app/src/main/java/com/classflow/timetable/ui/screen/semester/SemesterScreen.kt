package com.classflow.timetable.ui.screen.semester

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.Color
import com.classflow.timetable.ui.theme.GlassScreenHeader
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.Card
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
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
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.classflow.timetable.core.time.AppClock
import com.classflow.timetable.domain.model.WeekMask
import com.classflow.timetable.domain.repository.TimetableRepository
import com.classflow.timetable.domain.usecase.WeekResolver
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import javax.inject.Inject

data class SemesterUiState(
    val loading: Boolean = true,
    val semesterId: Long = 0L,
    val name: String = "",
    val startDate: LocalDate = LocalDate.now(),
    val totalWeeksText: String = "20",
    val autoWeek: Int = 1,
    val manualWeek: Int? = null,
    val error: String? = null,
    val saved: Boolean = false,
)

@HiltViewModel
class SemesterViewModel @Inject constructor(
    private val repository: TimetableRepository,
    private val clock: AppClock,
) : ViewModel() {

    private val _state = MutableStateFlow(SemesterUiState())
    val state: StateFlow<SemesterUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val semester = repository.getActiveSemester()
            if (semester == null) {
                val today = clock.today()
                _state.update {
                    it.copy(
                        loading = false,
                        name = "当前学期",
                        startDate = today,
                        autoWeek = 1,
                    )
                }
            } else {
                val weekState = WeekResolver.resolve(semester, clock.today())
                _state.value = SemesterUiState(
                    loading = false,
                    semesterId = semester.id,
                    name = semester.name,
                    startDate = semester.startDate,
                    totalWeeksText = semester.totalWeeks.toString(),
                    autoWeek = weekState.week,
                    manualWeek = semester.weekOverride,
                )
            }
        }
    }

    fun onNameChange(value: String) = _state.update { it.copy(name = value, error = null) }
    fun onStartDateChange(value: LocalDate) = _state.update { it.copy(startDate = value, error = null) }
    fun onTotalWeeksChange(value: String) = _state.update {
        if (value.all { ch -> ch.isDigit() }) it.copy(totalWeeksText = value, error = null) else it
    }

    fun useAutoWeek() = _state.update { it.copy(manualWeek = null, error = null) }

    fun useManualWeek() = _state.update { it.copy(manualWeek = it.autoWeek, error = null) }

    fun shiftManualWeek(delta: Int) = _state.update { st ->
        val total = st.totalWeeksText.toIntOrNull() ?: 20
        val base = st.manualWeek ?: st.autoWeek
        st.copy(manualWeek = (base + delta).coerceIn(1, total), error = null)
    }

    fun save() {
        val st = _state.value
        val name = st.name.trim()
        val totalWeeks = st.totalWeeksText.toIntOrNull() ?: 0
        when {
            name.isEmpty() -> {
                _state.update { it.copy(error = "请填写学期名称") }
                return
            }
            totalWeeks !in 1..WeekMask.MAX_WEEKS -> {
                _state.update { it.copy(error = "总周数需在 1~${WeekMask.MAX_WEEKS} 之间") }
                return
            }
        }

        viewModelScope.launch {
            val existing = repository.getActiveSemester()
            if (existing == null) {
                val id = repository.createSemester(name, st.startDate, totalWeeks)
                repository.switchActiveSemester(id)
                repository.setWeekOverride(id, st.manualWeek)
            } else {
                repository.upsertSemester(
                    existing.copy(
                        name = name,
                        startDate = st.startDate,
                        totalWeeks = totalWeeks,
                        weekOverride = st.manualWeek?.coerceIn(1, totalWeeks),
                    )
                )
            }
            _state.update { it.copy(saved = true) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SemesterScreen(
    onBack: () -> Unit,
    viewModel: SemesterViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    var showDatePicker by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            GlassScreenHeader(
                title = "学期设置",
                onBack = onBack,
                actions = {
                    TextButton(onClick = viewModel::save) { Text("保存") }
                },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            OutlinedTextField(
                value = state.name,
                onValueChange = viewModel::onNameChange,
                label = { Text("学期名称") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Column {
                Text("开学日期（该周为第 1 周）", style = MaterialTheme.typography.labelLarge)
                OutlinedButton(
                    onClick = { showDatePicker = true },
                    modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                ) {
                    Text(state.startDate.format(DateTimeFormatter.ofPattern("yyyy 年 M 月 d 日")))
                }
            }

            OutlinedTextField(
                value = state.totalWeeksText,
                onValueChange = viewModel::onTotalWeeksChange,
                label = { Text("总周数") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth(),
            )

            Card {
                Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("当前周", style = MaterialTheme.typography.titleSmall)
                    Text(
                        text = "按日期自动计算：第 ${state.autoWeek} 周",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = state.manualWeek == null,
                            onClick = viewModel::useAutoWeek,
                            label = { Text("自动") },
                        )
                        FilterChip(
                            selected = state.manualWeek != null,
                            onClick = viewModel::useManualWeek,
                            label = { Text("手动指定") },
                        )
                    }
                    if (state.manualWeek != null) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            IconButton(onClick = { viewModel.shiftManualWeek(-1) }) {
                                Icon(Icons.Filled.ChevronLeft, contentDescription = "上一周")
                            }
                            Text("第 ${state.manualWeek} 周", style = MaterialTheme.typography.titleMedium)
                            IconButton(onClick = { viewModel.shiftManualWeek(1) }) {
                                Icon(Icons.Filled.ChevronRight, contentDescription = "下一周")
                            }
                        }
                        Text(
                            text = "手动指定后，周课表、今日页与提醒都会按这个周次计算。",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            }

            state.error?.let {
                Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall)
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = state.startDate
                .atStartOfDay(ZoneOffset.UTC)
                .toInstant()
                .toEpochMilli(),
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(
                    onClick = {
                        pickerState.selectedDateMillis?.let { millis ->
                            viewModel.onStartDateChange(
                                Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                            )
                        }
                        showDatePicker = false
                    },
                ) { Text("确定") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("取消") }
            },
        ) {
            DatePicker(state = pickerState)
        }
    }
}
