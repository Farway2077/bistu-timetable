package cn.edu.bistu.kebiao.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import cn.edu.bistu.kebiao.domain.CourseSource
import cn.edu.bistu.kebiao.domain.ScheduleException
import cn.edu.bistu.kebiao.domain.ScheduleExceptionKind
import cn.edu.bistu.kebiao.domain.ScheduleLessonDraft
import cn.edu.bistu.kebiao.domain.ScheduledCourse
import cn.edu.bistu.kebiao.domain.Semester
import cn.edu.bistu.kebiao.domain.WeekPattern
import cn.edu.bistu.kebiao.domain.toDraft
import java.time.LocalDate
import java.time.format.DateTimeParseException

private enum class AdjustmentMode {
    CANCEL,
    RESCHEDULE,
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditorScreen(
    viewModel: ScheduleEditorViewModel,
    onBack: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val snackbarHostState = remember { SnackbarHostState() }
    var editorOpen by rememberSaveable { mutableStateOf(false) }
    var editingCourse by remember { mutableStateOf<ScheduledCourse?>(null) }
    var adjustmentCourse by remember { mutableStateOf<ScheduledCourse?>(null) }
    var deletingCourse by remember { mutableStateOf<ScheduledCourse?>(null) }

    LaunchedEffect(state.message) {
        state.message?.let { message ->
            snackbarHostState.showSnackbar(message)
            viewModel.dismissMessage()
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        topBar = {
            TopAppBar(
                title = { Text("管理课表") },
                navigationIcon = { TextButton(onClick = onBack) { Text("返回") } },
            )
        },
        floatingActionButton = {
            if (state.semester != null) {
                FloatingActionButton(
                    onClick = {
                        editingCourse = null
                        editorOpen = true
                    },
                ) {
                    Text("＋", style = MaterialTheme.typography.headlineSmall)
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        val semester = state.semester
        if (semester == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text("请先从教务系统同步课表")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .navigationBarsPadding(),
                contentPadding = PaddingValues(start = 16.dp, top = 12.dp, end = 16.dp, bottom = 96.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                item {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = MaterialTheme.shapes.large,
                    ) {
                        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("同步不会覆盖本地内容", fontWeight = FontWeight.SemiBold)
                            Text(
                                "编辑教务课程会保存为本地修正；手动课程和临时调整在下次同步后仍然保留。",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                    }
                }

                if (state.hiddenOverrides.isNotEmpty()) {
                    item { SectionTitle("已隐藏的教务课程") }
                    items(state.hiddenOverrides, key = { it.sourceKey }) { override ->
                        Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Column(Modifier.weight(1f)) {
                                    Text(
                                        override.draft?.courseName ?: "已隐藏课程",
                                        fontWeight = FontWeight.SemiBold,
                                        color = MaterialTheme.colorScheme.onErrorContainer,
                                    )
                                    override.draft?.let { draft ->
                                        Text(
                                            "星期${weekdayShort(draft.weekday)} · 第${draft.startPeriod}-${draft.endPeriod}节",
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onErrorContainer,
                                        )
                                    }
                                }
                                TextButton(
                                    onClick = { viewModel.restoreImportedVersion(override.sourceKey) },
                                    enabled = !state.isSaving,
                                ) { Text("恢复") }
                            }
                        }
                    }
                }

                if (state.exceptions.isNotEmpty()) {
                    item { SectionTitle("临时调整") }
                    items(state.exceptions, key = ScheduleException::id) { exception ->
                        ExceptionCard(
                            exception = exception,
                            courseName = state.courses
                                .firstOrNull { it.meeting.sourceKey == exception.sourceKey }
                                ?.course?.name
                                ?: "已调整课程",
                            enabled = !state.isSaving,
                            onRestore = { viewModel.deleteException(exception.id) },
                        )
                    }
                }

                item { SectionTitle("全部课程") }
                items(state.courses, key = { it.meeting.sourceKey }) { scheduled ->
                    CourseManagementCard(
                        scheduled = scheduled,
                        enabled = !state.isSaving,
                        onEdit = {
                            editingCourse = scheduled
                            editorOpen = true
                        },
                        onAdjust = { adjustmentCourse = scheduled },
                        onDelete = { deletingCourse = scheduled },
                        onRestore = { viewModel.restoreImportedVersion(scheduled) },
                    )
                }
            }

            if (editorOpen) {
                LessonEditorDialog(
                    semester = semester,
                    original = editingCourse,
                    enabled = !state.isSaving,
                    onDismiss = { editorOpen = false },
                    onSave = { draft ->
                        viewModel.saveLesson(editingCourse, draft) { editorOpen = false }
                    },
                )
            }

            adjustmentCourse?.let { scheduled ->
                AdjustmentDialog(
                    semester = semester,
                    scheduled = scheduled,
                    enabled = !state.isSaving,
                    onDismiss = { adjustmentCourse = null },
                    onSave = { originalDate, kind, replacementDate, start, end, room ->
                        viewModel.saveException(
                            scheduled = scheduled,
                            originalDate = originalDate,
                            kind = kind,
                            replacementDate = replacementDate,
                            startPeriod = start,
                            endPeriod = end,
                            roomOverride = room,
                            onSaved = { adjustmentCourse = null },
                        )
                    },
                )
            }

            deletingCourse?.let { scheduled ->
                AlertDialog(
                    onDismissRequest = {
                        if (!state.isSaving) deletingCourse = null
                    },
                    title = { Text("移除课程？") },
                    text = {
                        Text(
                            if (scheduled.course.source == CourseSource.MANUAL) {
                                "“${scheduled.course.name}”将从本机删除。"
                            } else {
                                "“${scheduled.course.name}”将被本地隐藏，后续同步也不会自动恢复；你仍可恢复教务版本。"
                            },
                        )
                    },
                    dismissButton = {
                        TextButton(
                            onClick = { deletingCourse = null },
                            enabled = !state.isSaving,
                        ) { Text("取消") }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                viewModel.deleteLesson(scheduled) { deletingCourse = null }
                            },
                            enabled = !state.isSaving,
                        ) { Text("确认移除") }
                    },
                )
            }
        }
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text = text,
        modifier = Modifier.padding(top = 8.dp, bottom = 2.dp),
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun CourseManagementCard(
    scheduled: ScheduledCourse,
    enabled: Boolean,
    onEdit: () -> Unit,
    onAdjust: () -> Unit,
    onDelete: () -> Unit,
    onRestore: () -> Unit,
) {
    val sourceLabel = when {
        scheduled.isLocalOverride -> "本地修正"
        scheduled.course.source == CourseSource.MANUAL -> "手动课程"
        else -> "教务课程"
    }
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Row(verticalAlignment = Alignment.Top) {
                Column(Modifier.weight(1f)) {
                    Text(
                        scheduled.course.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "星期${weekdayShort(scheduled.meeting.weekday)} · " +
                            "第${scheduled.meeting.startPeriod}-${scheduled.meeting.endPeriod}节 · " +
                            WeekPattern.format(scheduled.meeting.weeks),
                        style = MaterialTheme.typography.bodySmall,
                    )
                    val detail = listOf(scheduled.meeting.room, scheduled.course.teacher)
                        .filter(String::isNotBlank)
                        .joinToString(" · ")
                    if (detail.isNotBlank()) {
                        Text(detail, style = MaterialTheme.typography.bodySmall)
                    }
                }
                Surface(
                    color = if (scheduled.isLocalOverride || scheduled.course.source == CourseSource.MANUAL) {
                        MaterialTheme.colorScheme.tertiaryContainer
                    } else {
                        MaterialTheme.colorScheme.secondaryContainer
                    },
                    shape = MaterialTheme.shapes.small,
                ) {
                    Text(sourceLabel, modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp), style = MaterialTheme.typography.labelSmall)
                }
            }
            HorizontalDivider()
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onEdit, enabled = enabled) { Text("编辑") }
                TextButton(onClick = onAdjust, enabled = enabled) { Text("临时调整") }
                if (scheduled.isLocalOverride) {
                    TextButton(onClick = onRestore, enabled = enabled) { Text("恢复教务版") }
                }
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onDelete, enabled = enabled) { Text("移除") }
            }
        }
    }
}

@Composable
private fun ExceptionCard(
    exception: ScheduleException,
    courseName: String,
    enabled: Boolean,
    onRestore: () -> Unit,
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text(courseName, fontWeight = FontWeight.SemiBold)
                Text(
                    when (exception.kind) {
                        ScheduleExceptionKind.CANCEL -> "${exception.originalDate} · 本次停课"
                        ScheduleExceptionKind.RESCHEDULE ->
                            "${exception.originalDate} → ${exception.replacementDate} · " +
                                "第${exception.startPeriod}-${exception.endPeriod}节"
                    },
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            TextButton(onClick = onRestore, enabled = enabled) { Text("恢复") }
        }
    }
}

@Composable
private fun LessonEditorDialog(
    semester: Semester,
    original: ScheduledCourse?,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (ScheduleLessonDraft) -> Unit,
) {
    val initial = original?.toDraft()
    var name by rememberSaveable(original?.meeting?.id) { mutableStateOf(initial?.courseName.orEmpty()) }
    var teacher by rememberSaveable(original?.meeting?.id) { mutableStateOf(initial?.teacher.orEmpty()) }
    var room by rememberSaveable(original?.meeting?.id) { mutableStateOf(initial?.room.orEmpty()) }
    var weekday by rememberSaveable(original?.meeting?.id) { mutableStateOf((initial?.weekday ?: 1).toString()) }
    var startPeriod by rememberSaveable(original?.meeting?.id) { mutableStateOf((initial?.startPeriod ?: 1).toString()) }
    var endPeriod by rememberSaveable(original?.meeting?.id) { mutableStateOf((initial?.endPeriod ?: 2).toString()) }
    var weeks by rememberSaveable(original?.meeting?.id) {
        mutableStateOf(initial?.weeks?.let(WeekPattern::format) ?: "1-${semester.totalWeeks}周")
    }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (enabled) onDismiss() },
        title = { Text(if (original == null) "添加手动课程" else "编辑课程") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                if (original != null && original.course.source == CourseSource.IMPORTED) {
                    Text(
                        "保存后会形成独立的本地修正，后续同步不会覆盖。",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                OutlinedTextField(name, { name = it }, label = { Text("课程名称*") }, singleLine = true)
                OutlinedTextField(teacher, { teacher = it }, label = { Text("教师") }, singleLine = true)
                OutlinedTextField(room, { room = it }, label = { Text("教室") }, singleLine = true)
                NumericField(weekday, { weekday = it }, "星期（1–7）")
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    NumericField(startPeriod, { startPeriod = it }, "开始节次", Modifier.weight(1f))
                    NumericField(endPeriod, { endPeriod = it }, "结束节次", Modifier.weight(1f))
                }
                OutlinedTextField(
                    value = weeks,
                    onValueChange = { weeks = it },
                    label = { Text("周次*") },
                    supportingText = { Text("例如：1-16周、1-15周(单)、2,4,6周") },
                    singleLine = true,
                )
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = enabled) { Text("取消") }
        },
        confirmButton = {
            Button(
                onClick = {
                    val parsedWeekday = weekday.toIntOrNull()
                    val parsedStart = startPeriod.toIntOrNull()
                    val parsedEnd = endPeriod.toIntOrNull()
                    val parsedWeeks = runCatching { WeekPattern.parse(weeks, semester.totalWeeks) }.getOrDefault(emptySet())
                    error = when {
                        name.isBlank() -> "请输入课程名称"
                        parsedWeekday !in 1..7 -> "星期应为 1–7"
                        parsedStart !in 1..14 -> "开始节次应为 1–14"
                        parsedEnd == null || parsedStart == null || parsedEnd !in parsedStart..14 -> "结束节次无效"
                        parsedWeeks.isEmpty() -> "没有识别到有效周次"
                        else -> null
                    }
                    if (error == null) {
                        onSave(
                            ScheduleLessonDraft(
                                courseName = name,
                                teacher = teacher,
                                room = room,
                                weekday = requireNotNull(parsedWeekday),
                                startPeriod = requireNotNull(parsedStart),
                                endPeriod = requireNotNull(parsedEnd),
                                weeks = parsedWeeks,
                            ),
                        )
                    }
                },
                enabled = enabled,
            ) { Text("保存") }
        },
    )
}

@Composable
private fun AdjustmentDialog(
    semester: Semester,
    scheduled: ScheduledCourse,
    enabled: Boolean,
    onDismiss: () -> Unit,
    onSave: (LocalDate, ScheduleExceptionKind, LocalDate?, Int?, Int?, String?) -> Unit,
) {
    val suggestedDate = remember(scheduled.meeting.sourceKey, semester.startDate) {
        suggestedOccurrenceDate(scheduled, semester, LocalDate.now())
    }
    var modeName by rememberSaveable(scheduled.meeting.sourceKey) { mutableStateOf(AdjustmentMode.CANCEL.name) }
    val mode = AdjustmentMode.valueOf(modeName)
    var originalDateText by rememberSaveable(scheduled.meeting.sourceKey) { mutableStateOf(suggestedDate.toString()) }
    var replacementDateText by rememberSaveable(scheduled.meeting.sourceKey) { mutableStateOf(suggestedDate.toString()) }
    var startPeriod by rememberSaveable(scheduled.meeting.sourceKey) { mutableStateOf(scheduled.meeting.startPeriod.toString()) }
    var endPeriod by rememberSaveable(scheduled.meeting.sourceKey) { mutableStateOf(scheduled.meeting.endPeriod.toString()) }
    var room by rememberSaveable(scheduled.meeting.sourceKey) { mutableStateOf(scheduled.meeting.room) }
    var error by remember { mutableStateOf<String?>(null) }

    AlertDialog(
        onDismissRequest = { if (enabled) onDismiss() },
        title = { Text("临时调整 · ${scheduled.course.name}") },
        text = {
            Column(
                modifier = Modifier.heightIn(max = 520.dp).verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text("只影响所选日期的这一次课程。", style = MaterialTheme.typography.bodySmall)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(
                        onClick = { modeName = AdjustmentMode.CANCEL.name },
                        modifier = Modifier.weight(1f),
                    ) { Text(if (mode == AdjustmentMode.CANCEL) "✓ 本次停课" else "本次停课") }
                    OutlinedButton(
                        onClick = { modeName = AdjustmentMode.RESCHEDULE.name },
                        modifier = Modifier.weight(1f),
                    ) { Text(if (mode == AdjustmentMode.RESCHEDULE) "✓ 临时调课" else "临时调课") }
                }
                OutlinedTextField(
                    originalDateText,
                    { originalDateText = it },
                    label = { Text("原上课日期（yyyy-MM-dd）") },
                    singleLine = true,
                )
                if (mode == AdjustmentMode.RESCHEDULE) {
                    OutlinedTextField(
                        replacementDateText,
                        { replacementDateText = it },
                        label = { Text("调课后日期（yyyy-MM-dd）") },
                        singleLine = true,
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        NumericField(startPeriod, { startPeriod = it }, "开始节次", Modifier.weight(1f))
                        NumericField(endPeriod, { endPeriod = it }, "结束节次", Modifier.weight(1f))
                    }
                    OutlinedTextField(room, { room = it }, label = { Text("新教室（可不变）") }, singleLine = true)
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodySmall) }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss, enabled = enabled) { Text("取消") }
        },
        confirmButton = {
            Button(
                onClick = {
                    val originalDate = parseDate(originalDateText)
                    val replacementDate = if (mode == AdjustmentMode.RESCHEDULE) parseDate(replacementDateText) else null
                    val start = if (mode == AdjustmentMode.RESCHEDULE) startPeriod.toIntOrNull() else null
                    val end = if (mode == AdjustmentMode.RESCHEDULE) endPeriod.toIntOrNull() else null
                    val originalWeek = originalDate?.let { teachingWeekForDate(semester, it) }
                    val replacementWeek = replacementDate?.let { teachingWeekForDate(semester, it) }
                    error = when {
                        originalDate == null -> "原上课日期格式应为 yyyy-MM-dd"
                        originalWeek == null -> "原上课日期不在当前学期内"
                        originalDate.dayOfWeek.value != scheduled.meeting.weekday ->
                            "原日期与这门课的星期不一致"
                        !scheduled.meeting.occursIn(originalWeek) -> "该日期没有这门课"
                        mode == AdjustmentMode.RESCHEDULE && replacementDate == null -> "调课后日期格式应为 yyyy-MM-dd"
                        mode == AdjustmentMode.RESCHEDULE && replacementWeek == null -> "调课后日期不在当前学期内"
                        mode == AdjustmentMode.RESCHEDULE && start !in 1..14 -> "开始节次应为 1–14"
                        mode == AdjustmentMode.RESCHEDULE && (end == null || start == null || end !in start..14) -> "结束节次无效"
                        else -> null
                    }
                    if (error == null) {
                        onSave(
                            requireNotNull(originalDate),
                            if (mode == AdjustmentMode.CANCEL) ScheduleExceptionKind.CANCEL else ScheduleExceptionKind.RESCHEDULE,
                            replacementDate,
                            start,
                            end,
                            room,
                        )
                    }
                },
                enabled = enabled,
            ) { Text("保存调整") }
        },
    )
}

@Composable
private fun NumericField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
) {
    OutlinedTextField(
        value = value,
        onValueChange = { text -> onValueChange(text.filter(Char::isDigit)) },
        label = { Text(label) },
        modifier = modifier,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        singleLine = true,
    )
}

private fun suggestedOccurrenceDate(
    scheduled: ScheduledCourse,
    semester: Semester,
    today: LocalDate,
): LocalDate {
    val dates = scheduled.meeting.weeks.sorted().map { week ->
        semester.startDate
            .plusWeeks((week - 1).toLong())
            .plusDays((scheduled.meeting.weekday - 1).toLong())
    }
    return dates.firstOrNull { it >= today } ?: dates.lastOrNull() ?: semester.startDate
}

private fun teachingWeekForDate(semester: Semester, date: LocalDate): Int? {
    val daysFromStart = date.toEpochDay() - semester.startDate.toEpochDay()
    if (daysFromStart !in 0 until semester.totalWeeks * 7L) return null
    return (daysFromStart / 7 + 1).toInt()
}

private fun parseDate(value: String): LocalDate? = try {
    LocalDate.parse(value.trim())
} catch (_: DateTimeParseException) {
    null
}

private fun weekdayShort(value: Int): String = listOf("一", "二", "三", "四", "五", "六", "日")[value - 1]
