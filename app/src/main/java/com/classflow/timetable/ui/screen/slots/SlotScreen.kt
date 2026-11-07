package com.classflow.timetable.ui.screen.slots

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.Color
import com.classflow.timetable.ui.theme.GlassScreenHeader
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.classflow.timetable.domain.model.SlotSection
import com.classflow.timetable.domain.model.TimeSlot
import com.classflow.timetable.domain.repository.TimetableRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.time.LocalTime
import javax.inject.Inject

data class SlotDraft(
    val id: Long,
    val index: Int,
    val start: String,
    val end: String,
    val section: SlotSection,
)

data class SlotUiState(
    val loading: Boolean = true,
    val drafts: List<SlotDraft> = emptyList(),
    val error: String? = null,
    val saved: Boolean = false,
)

@HiltViewModel
class SlotViewModel @Inject constructor(
    private val repository: TimetableRepository,
) : ViewModel() {

    private val _state = MutableStateFlow(SlotUiState())
    val state: StateFlow<SlotUiState> = _state.asStateFlow()

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val slots = repository.getTimeSlots()
        _state.value = SlotUiState(
            loading = false,
            drafts = slots.map {
                SlotDraft(
                    id = it.id,
                    index = it.index,
                    start = TimeSlot.format(it.start),
                    end = TimeSlot.format(it.end),
                    section = it.section,
                )
            },
        )
    }

    fun updateStart(index: Int, value: String) = _state.update { st ->
        st.copy(
            drafts = st.drafts.map { if (it.index == index) it.copy(start = value) else it },
            error = null,
        )
    }

    fun updateEnd(index: Int, value: String) = _state.update { st ->
        st.copy(
            drafts = st.drafts.map { if (it.index == index) it.copy(end = value) else it },
            error = null,
        )
    }

    fun save() {
        val drafts = _state.value.drafts
        val parsed = mutableListOf<TimeSlot>()
        for (draft in drafts) {
            val start = parseTime(draft.start)
            val end = parseTime(draft.end)
            if (start == null || end == null) {
                _state.update { it.copy(error = "第 ${draft.index} 节时间格式不对，请写成 08:00 这样") }
                return
            }
            if (!start.isBefore(end)) {
                _state.update { it.copy(error = "第 ${draft.index} 节：开始时间要早于结束时间") }
                return
            }
            parsed += TimeSlot(
                id = draft.id,
                index = draft.index,
                start = start,
                end = end,
                section = draft.section,
            )
        }
        viewModelScope.launch {
            repository.upsertTimeSlots(parsed)
            _state.update { it.copy(saved = true) }
        }
    }

    fun resetToDefault() {
        viewModelScope.launch {
            repository.resetTimeSlots()
            load()
        }
    }

    private fun parseTime(raw: String): LocalTime? {
        val text = raw.trim().replace('：', ':')
        val parts = text.split(":")
        if (parts.size != 2) return null
        val hour = parts[0].toIntOrNull() ?: return null
        val minute = parts[1].toIntOrNull() ?: return null
        if (hour !in 0..23 || minute !in 0..59) return null
        return LocalTime.of(hour, minute)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SlotScreen(
    onBack: () -> Unit,
    viewModel: SlotViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()

    LaunchedEffect(state.saved) { if (state.saved) onBack() }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            GlassScreenHeader(
                title = "节次时间",
                onBack = onBack,
                actions = {
                    TextButton(onClick = viewModel::resetToDefault) { Text("恢复默认") }
                    TextButton(onClick = viewModel::save) { Text("保存") }
                },
            )
        },
    ) { padding ->
        Column(Modifier.padding(padding).fillMaxSize()) {
            state.error?.let {
                Text(
                    text = it,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
                )
            }
            LazyColumn(
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.drafts, key = { it.index }) { draft ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = "${draft.index}",
                            style = MaterialTheme.typography.titleMedium,
                            modifier = Modifier.width(28.dp),
                        )
                        OutlinedTextField(
                            value = draft.start,
                            onValueChange = { viewModel.updateStart(draft.index, it) },
                            label = { Text("开始") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Next,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                        OutlinedTextField(
                            value = draft.end,
                            onValueChange = { viewModel.updateEnd(draft.index, it) },
                            label = { Text("结束") },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Number,
                                imeAction = ImeAction.Done,
                            ),
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                item {
                    Text(
                        text = "格式：24 小时制，例如 08:00、14:30。改完保存后，周课表、今日页和小组件会自动用新时间。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}
