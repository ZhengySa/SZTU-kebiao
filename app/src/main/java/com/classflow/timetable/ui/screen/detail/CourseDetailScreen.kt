package com.classflow.timetable.ui.screen.detail

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import com.classflow.timetable.ui.theme.GlassScreenHeader
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.classflow.timetable.domain.model.CourseDetail
import com.classflow.timetable.domain.model.CourseSession
import com.classflow.timetable.domain.model.TimeSlot
import com.classflow.timetable.domain.model.dayOfWeekLabel
import com.classflow.timetable.domain.repository.TimetableRepository
import com.classflow.timetable.domain.usecase.GetCourseDetailUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class CourseDetailViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    getCourseDetail: GetCourseDetailUseCase,
    private val repository: TimetableRepository,
) : ViewModel() {

    private val courseId: Long = savedStateHandle.get<Long>("courseId") ?: 0L

    val state: StateFlow<CourseDetail?> = getCourseDetail(courseId)
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun delete(onDeleted: () -> Unit) {
        viewModelScope.launch {
            repository.deleteCourse(courseId)
            onDeleted()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CourseDetailScreen(
    onBack: () -> Unit,
    onEdit: (Long) -> Unit,
    onDeleted: () -> Unit,
    viewModel: CourseDetailViewModel = hiltViewModel(),
) {
    val detail by viewModel.state.collectAsStateWithLifecycle()
    var showDeleteDialog by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            GlassScreenHeader(
                title = "课程详情",
                onBack = onBack,
                actions = {
                    val current = detail
                    if (current != null) {
                        IconButton(onClick = { onEdit(current.course.id) }) {
                            Icon(Icons.Filled.Edit, contentDescription = "编辑")
                        }
                        IconButton(onClick = { showDeleteDialog = true }) {
                            Icon(Icons.Filled.Delete, contentDescription = "删除")
                        }
                    }
                },
            )
        },
    ) { padding ->
        val current = detail
        if (current == null) {
            Box(Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("课程不存在或已删除", color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.padding(padding).fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        Modifier
                            .width(8.dp)
                            .height(44.dp)
                            .background(Color(current.course.colorArgb), RoundedCornerShape(4.dp)),
                    )
                    Column(Modifier.padding(start = 12.dp)) {
                        Text(current.course.name, style = MaterialTheme.typography.headlineSmall)
                        Text(
                            text = listOfNotNull(
                                current.course.teacher?.takeIf { it.isNotBlank() },
                                current.course.defaultLocation?.takeIf { it.isNotBlank() },
                            ).joinToString(" · ").ifBlank { "未填写教师/地点" },
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (!current.course.note.isNullOrBlank()) {
                item {
                    Card(shape = RoundedCornerShape(14.dp)) {
                        Column(Modifier.padding(14.dp)) {
                            Text("备注", style = MaterialTheme.typography.labelLarge)
                            Text(current.course.note.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }

            item {
                Text(
                    text = "共 ${current.sessions.size} 个上课时段",
                    style = MaterialTheme.typography.titleSmall,
                )
            }

            items(current.sessions, key = { it.id }) { session ->
                SessionRow(session = session, detail = current)
            }

            item {
                Text(
                    text = "学期：${current.semester.name} · 共 ${current.semester.totalWeeks} 周",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (showDeleteDialog) {
        AlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = { Text("删除课程") },
            text = {
                Text("删除后该课程的所有时段都会消失，且无法恢复。确定删除「${detail?.course?.name.orEmpty()}」吗？")
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        showDeleteDialog = false
                        viewModel.delete(onDeleted)
                    },
                ) { Text("删除") }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) { Text("取消") }
            },
        )
    }
}

@Composable
private fun SessionRow(session: CourseSession, detail: CourseDetail) {
    val startSlot: TimeSlot? = detail.timeSlots.firstOrNull { it.index == session.startSlot }
    val endSlot: TimeSlot? = detail.timeSlots.firstOrNull { it.index == session.endSlot }
    val timeText = if (startSlot != null && endSlot != null) {
        "${TimeSlot.format(startSlot.start)}-${TimeSlot.format(endSlot.end)}"
    } else {
        "时间未知"
    }
    val location = session.locationOverride?.takeIf { it.isNotBlank() }
        ?: detail.course.defaultLocation?.takeIf { it.isNotBlank() }
        ?: "地点待填"
    val weeks = session.weekMask.describe(detail.semester.totalWeeks)

    Card(shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                text = "${dayOfWeekLabel(session.dayOfWeek)} · ${session.slotLabel}",
                style = MaterialTheme.typography.titleSmall,
            )
            Text(
                text = "$timeText · $location",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = weeks,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            if (!session.note.isNullOrBlank()) {
                Text(session.note.orEmpty(), style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}
