package com.classflow.timetable.ui.screen.imports

import android.content.Context
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.graphics.Color
import com.classflow.timetable.ui.theme.GlassScreenHeader
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
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
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.classflow.timetable.domain.imports.ImportOutcome
import com.classflow.timetable.domain.imports.ImportPreview
import com.classflow.timetable.domain.model.ImportMode
import com.classflow.timetable.domain.model.ImportSourceType
import com.classflow.timetable.domain.model.dayOfWeekLabel
import com.classflow.timetable.domain.usecase.ApplyImportUseCase
import com.classflow.timetable.domain.usecase.ImportTemplates
import com.classflow.timetable.domain.usecase.PrepareImportUseCase
import com.classflow.timetable.domain.usecase.describeWeeks
import com.classflow.timetable.domain.usecase.slotsLabelOf
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.nio.charset.Charset
import javax.inject.Inject

sealed interface ImportUiState {
    data object Idle : ImportUiState
    data object Loading : ImportUiState
    data class Ready(val fileName: String, val preview: ImportPreview) : ImportUiState
    data class Applied(val outcome: ImportOutcome) : ImportUiState
    data class Failed(val message: String) : ImportUiState
}

@HiltViewModel
class ImportViewModel @Inject constructor(
    private val prepareImport: PrepareImportUseCase,
    private val applyImport: ApplyImportUseCase,
) : ViewModel() {

    private val _state = MutableStateFlow<ImportUiState>(ImportUiState.Idle)
    val state: StateFlow<ImportUiState> = _state.asStateFlow()

    private var pendingText: String = ""

    fun load(text: String, fileName: String, type: ImportSourceType) {
        pendingText = text
        _state.value = ImportUiState.Loading
        viewModelScope.launch {
            _state.value = runCatching { prepareImport(text, type) }
                .fold(
                    onSuccess = { ImportUiState.Ready(fileName, it) },
                    onFailure = { ImportUiState.Failed("解析失败：${it.message ?: "文件格式不认识"}") },
                )
        }
    }

    fun apply(mode: ImportMode) {
        val ready = _state.value as? ImportUiState.Ready ?: return
        _state.value = ImportUiState.Loading
        viewModelScope.launch {
            _state.value = runCatching {
                applyImport(ready.preview, mode, ready.fileName)
            }.fold(
                onSuccess = { ImportUiState.Applied(it) },
                onFailure = { ImportUiState.Failed("导入失败：${it.message ?: "写入数据库出错"}") },
            )
        }
    }

    fun reset() {
        pendingText = ""
        _state.value = ImportUiState.Idle
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ImportScreen(
    onBack: () -> Unit,
    viewModel: ImportViewModel = hiltViewModel(),
) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var mode by remember { mutableStateOf(ImportMode.MERGE) }
    var showTemplate by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
    ) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val name = queryDisplayName(context, uri)
        val type = if (name.endsWith(".csv", ignoreCase = true)) ImportSourceType.CSV else ImportSourceType.ICS
        val text = readTextSmart(context, uri)
        if (text == null) {
            viewModel.load("", name, type) // 走失败分支，统一提示
        } else {
            viewModel.load(text, name, type)
        }
    }

    Scaffold(
        containerColor = Color.Transparent,
        topBar = {
            GlassScreenHeader(title = "导入课表", onBack = onBack)
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                "支持 .ics 日历文件与 CSV 表格。导入前会先给你看预览和冲突提示，确认后才会写入。",
                style = MaterialTheme.typography.bodyMedium,
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { picker.launch(arrayOf("text/calendar", "text/plain", "*/*")) },
                ) { Text("选择 ICS 文件") }
                OutlinedButton(
                    onClick = { picker.launch(arrayOf("text/csv", "text/plain", "*/*")) },
                ) { Text("选择 CSV 文件") }
            }

            when (val current = state) {
                ImportUiState.Idle -> Unit

                ImportUiState.Loading -> Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    CircularProgressIndicator(modifier = Modifier.padding(4.dp))
                    Text("正在处理…", style = MaterialTheme.typography.bodyMedium)
                }

                is ImportUiState.Failed -> Card(
                    colors = CardDefaults.cardColors(
                        containerColor = MaterialTheme.colorScheme.errorContainer,
                    ),
                ) {
                    Column(Modifier.padding(12.dp)) {
                        Text("没有导入成功", style = MaterialTheme.typography.titleSmall)
                        Text(current.message, style = MaterialTheme.typography.bodySmall)
                        TextButton(onClick = { viewModel.reset() }) { Text("重来") }
                    }
                }

                is ImportUiState.Applied -> Card {
                    Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Text("导入完成", style = MaterialTheme.typography.titleMedium)
                        Text(
                            buildString {
                                append("新增 ${current.outcome.insertedCourses} 门课 / ")
                                append("${current.outcome.insertedSessions} 个时段")
                                if (current.outcome.updatedCourses > 0) {
                                    append("，更新 ${current.outcome.updatedCourses} 门同名课")
                                }
                                if (current.outcome.repairedSessions > 0) {
                                    append("（补了 ${current.outcome.repairedSessions} 个时段的教室）")
                                }
                                if (current.outcome.skippedCourses > 0) {
                                    append("，跳过 ${current.outcome.skippedCourses} 门（同名且没有新信息，或数据不完整）")
                                }
                            },
                            style = MaterialTheme.typography.bodyMedium,
                        )
                        TextButton(onClick = onBack) { Text("回到课表") }
                    }
                }

                is ImportUiState.Ready -> PreviewSection(
                    fileName = current.fileName,
                    preview = current.preview,
                    mode = mode,
                    onModeChange = { mode = it },
                    onApply = { viewModel.apply(mode) },
                    onCancel = { viewModel.reset() },
                )
            }

            HorizontalDivider(Modifier.padding(vertical = 8.dp))

            Text("CSV 格式说明", style = MaterialTheme.typography.titleSmall)
            Text(
                "第一行是表头，列顺序随意，认中英文列名。必填：课程名、星期、开始节、结束节。" +
                    "周次可写 1-16、1-8,10-16、1-16单、1-16双，留空表示每周。",
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = { showTemplate = true }) { Text("查看 / 复制模板") }
        }
    }

    if (showTemplate) {
        val clipboard = LocalClipboardManager.current
        AlertDialog(
            onDismissRequest = { showTemplate = false },
            title = { Text("CSV 模板") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        ImportTemplates.CSV_TEMPLATE,
                        style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                    )
                    TextButton(onClick = {
                        clipboard.setText(AnnotatedString(ImportTemplates.CSV_TEMPLATE))
                        showTemplate = false
                    }) { Text("复制到剪贴板") }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTemplate = false }) { Text("关闭") }
            },
        )
    }
}

@Composable
private fun PreviewSection(
    fileName: String,
    preview: ImportPreview,
    mode: ImportMode,
    onModeChange: (ImportMode) -> Unit,
    onApply: () -> Unit,
    onCancel: () -> Unit,
) {
    Card {
        Column(Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(fileName, style = MaterialTheme.typography.titleSmall, maxLines = 2)
            Text(
                "解析到 ${preview.courses.size} 门课 / ${preview.sessionCount} 个时段" +
                    if (preview.skippedCourses > 0) "，跳过 ${preview.skippedCourses} 条" else "",
                style = MaterialTheme.typography.bodyMedium,
            )

            if (preview.courses.isNotEmpty()) {
                preview.courses.take(20).forEach { course ->
                    Column {
                        Text(course.name, style = MaterialTheme.typography.bodyMedium)
                        Text(
                            course.sessions.joinToString("；") { session ->
                                // 地点跟在每个时段后面，不用「@」分隔就分不清
                                // 哪个时间是哪间教室 —— 这正是导入前要确认的东西
                                buildString {
                                    append(dayOfWeekLabel(session.dayOfWeek))
                                    append(' ')
                                    append(slotsLabelOf(session.startSlot, session.endSlot))
                                    append(' ')
                                    append(describeWeeks(session.weeks))
                                    session.location?.takeIf { it.isNotBlank() }?.let {
                                        append(" @")
                                        append(it)
                                    }
                                }
                            },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                if (preview.courses.size > 20) {
                    Text("……还有 ${preview.courses.size - 20} 门", style = MaterialTheme.typography.bodySmall)
                }
            }

            if (preview.conflicts.isNotEmpty()) {
                Text(
                    "发现 ${preview.conflicts.size} 处时间冲突（仍可导入）",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.error,
                )
                preview.conflicts.take(8).forEach { conflict ->
                    Text(
                        "· ${conflict.courseName}：${conflict.detail}",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
            }

            if (preview.warnings.isNotEmpty()) {
                Text("提示", style = MaterialTheme.typography.titleSmall)
                preview.warnings.take(8).forEach { warning ->
                    Text("· $warning", style = MaterialTheme.typography.bodySmall)
                }
            }

            HorizontalDivider()

            ModeRow(
                title = "合并",
                subtitle = "保留现有课程，同名课程跳过",
                selected = mode == ImportMode.MERGE,
                onSelect = { onModeChange(ImportMode.MERGE) },
            )
            ModeRow(
                title = "覆盖",
                subtitle = "先清空本学期课程，再写入这批",
                selected = mode == ImportMode.OVERWRITE,
                onSelect = { onModeChange(ImportMode.OVERWRITE) },
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onCancel) { Text("取消") }
                TextButton(
                    onClick = onApply,
                    enabled = preview.courses.isNotEmpty(),
                ) { Text("确认导入") }
            }
        }
    }
}

@Composable
private fun ModeRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onSelect: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .selectable(selected = selected, onClick = onSelect)
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onSelect)
        Column(Modifier.padding(start = 4.dp)) {
            Text(title, style = MaterialTheme.typography.bodyMedium)
            Text(
                subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

// ---------- 文件读取 ----------

private const val MAX_IMPORT_BYTES = 4 * 1024 * 1024

private fun queryDisplayName(context: Context, uri: Uri): String {
    val projection = arrayOf(android.provider.OpenableColumns.DISPLAY_NAME)
    context.contentResolver.query(uri, projection, null, null, null)?.use { cursor ->
        if (cursor.moveToFirst()) {
            val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
            if (index >= 0) return cursor.getString(index) ?: "导入文件"
        }
    }
    return uri.lastPathSegment ?: "导入文件"
}

/**
 * 读文件并猜测编码：Excel 另存的 CSV 在国内常见 GBK，
 * 直接按 UTF-8 读会得到一堆「锟斤拷」，所以先试 UTF-8，出现替换字符再退回 GBK。
 */
private fun readTextSmart(context: Context, uri: Uri): String? = runCatching {
    val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return null
    if (bytes.size > MAX_IMPORT_BYTES) return null
    val utf8 = String(bytes, Charsets.UTF_8)
    if (utf8.contains('\uFFFD')) {
        String(bytes, Charset.forName("GBK"))
    } else {
        utf8
    }
}.getOrNull()
