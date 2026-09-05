package cn.edu.bistu.kebiao.ui.study

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import cn.edu.bistu.kebiao.domain.BistuPeriodTimes
import cn.edu.bistu.kebiao.domain.StudyTask
import cn.edu.bistu.kebiao.domain.StudyTaskKind
import cn.edu.bistu.kebiao.domain.coursesOnDate
import cn.edu.bistu.kebiao.domain.freePeriodRanges
import cn.edu.bistu.kebiao.domain.searchCourses
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.UUID
import kotlinx.coroutines.delay

private val dueFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StudyToolsScreen(viewModel: StudyToolsViewModel, onBack: () -> Unit) {
    val state by viewModel.state.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val error by viewModel.error.collectAsStateWithLifecycle()
    var tab by rememberSaveable { mutableStateOf(0) }
    var showEditor by rememberSaveable { mutableStateOf(false) }
    var editingId by rememberSaveable { mutableStateOf<String?>(null) }
    var suggestedCourse by rememberSaveable { mutableStateOf("") }
    var deleteId by rememberSaveable { mutableStateOf<String?>(null) }
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            delay(15_000)
        }
    }
    BackHandler(enabled = busy) { }
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("学习助手") },
                navigationIcon = { TextButton(onClick = onBack, enabled = !busy) { Text("返回") } },
            )
        },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            TabRow(selectedTabIndex = tab) {
                Tab(selected = tab == 0, onClick = { tab = 0 }, text = { Text("作业与考试") })
                Tab(selected = tab == 1, onClick = { tab = 1 }, text = { Text("查课与空闲") })
                Tab(selected = tab == 2, onClick = { tab = 2 }, text = { Text("课程搜索") })
            }
            if (error != null && !showEditor) {
                Text(error.orEmpty(), Modifier.padding(16.dp), color = MaterialTheme.colorScheme.error)
            }
            if (state.loading) {
                CircularProgressIndicator(Modifier.padding(24.dp))
            } else if (tab == 0) {
                TaskList(
                    tasks = state.tasks,
                    now = now,
                    busy = busy,
                    onAdd = {
                        editingId = null
                        suggestedCourse = ""
                        viewModel.error.value = null
                        showEditor = true
                    },
                    onEdit = {
                        editingId = it.id
                        viewModel.error.value = null
                        showEditor = true
                    },
                    onComplete = viewModel::complete,
                    onDelete = {
                        viewModel.error.value = null
                        deleteId = it.id
                    },
                )
            } else if (tab == 1) {
                CoursePlanner(state = state, onAddTask = { courseName ->
                    editingId = null
                    suggestedCourse = courseName
                    viewModel.error.value = null
                    showEditor = true
                })
            } else {
                CourseSearch(state = state, onAddTask = { courseName ->
                    editingId = null
                    suggestedCourse = courseName
                    viewModel.error.value = null
                    showEditor = true
                })
            }
        }
    }
    if (showEditor && !state.loading) {
        TaskEditor(
            task = state.tasks.firstOrNull { it.id == editingId },
            suggestedCourse = suggestedCourse,
            busy = busy,
            error = error,
            onDismiss = { showEditor = false },
            onSave = { task -> viewModel.save(task) { showEditor = false } },
        )
    }
    deleteId?.let { id ->
        AlertDialog(
            onDismissRequest = { if (!busy) deleteId = null },
            title = { Text("删除这条待办？") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("删除后无法恢复。也可以使用“完成”保留记录。")
                    error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                }
            },
            confirmButton = {
                TextButton(enabled = !busy, onClick = { viewModel.delete(id) { deleteId = null } }) { Text("删除") }
            },
            dismissButton = { TextButton(enabled = !busy, onClick = { deleteId = null }) { Text("取消") } },
        )
    }
}

@Composable
private fun TaskList(
    tasks: List<StudyTask>,
    now: LocalDateTime,
    busy: Boolean,
    onAdd: () -> Unit,
    onEdit: (StudyTask) -> Unit,
    onComplete: (StudyTask) -> Unit,
    onDelete: (StudyTask) -> Unit,
) {
    var filter by rememberSaveable { mutableStateOf(0) }
    val visible = tasks.filter { task ->
        when (filter) {
            0 -> !task.completed
            1 -> task.isOverdue(now)
            else -> task.completed
        }
    }.sortedWith(compareBy({ it.dueAt }, { it.title }, { it.id }))
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Column {
                    Text("把截止日期记在这里", style = MaterialTheme.typography.titleMedium)
                    Text("${tasks.count { !it.completed }} 项未完成 · ${tasks.count { it.isOverdue(now) }} 项逾期",
                        style = MaterialTheme.typography.bodySmall)
                }
                TextButton(onClick = onAdd, enabled = !busy) { Text("＋ 新建") }
            }
        }
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("待完成", "已逾期", "已完成").forEachIndexed { index, label ->
                    FilterChip(selected = filter == index, onClick = { filter = index }, label = { Text(label) })
                }
            }
        }
        if (visible.isEmpty()) {
            item { Text(if (filter == 0) "暂时没有待办，记下下一份作业或考试吧。" else "这里还没有记录。") }
        }
        items(visible, key = { it.id }) { task ->
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("${task.kind.label} · ${task.deadlineLabel(now)}",
                        color = if (task.isOverdue(now)) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                        style = MaterialTheme.typography.labelLarge)
                    Text(task.title, style = MaterialTheme.typography.titleMedium)
                    if (task.courseName.isNotBlank()) Text(task.courseName)
                    Text("截止 ${task.dueAt.format(dueFormatter)}", style = MaterialTheme.typography.bodyMedium)
                    if (task.notes.isNotBlank()) Text(task.notes, style = MaterialTheme.typography.bodyMedium)
                    Row {
                        TextButton(enabled = !busy, onClick = { onComplete(task) }) {
                            Text(if (task.completed) "恢复待办" else "完成")
                        }
                        TextButton(enabled = !busy, onClick = { onEdit(task) }) { Text("编辑") }
                        TextButton(enabled = !busy, onClick = { onDelete(task) }) { Text("删除") }
                    }
                }
            }
        }
        item {
            Text("待办保存在本机，不受课表同步影响。截止提示在应用内显示。",
                style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CoursePlanner(state: StudyToolsState, onAddTask: (String) -> Unit) {
    var dateText by rememberSaveable { mutableStateOf(LocalDate.now().toString()) }
    var query by rememberSaveable { mutableStateOf("") }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    val date = LocalDate.parse(dateText)
    val semester = state.semester
    val courses = remember(semester, state.courses, state.exceptions, date) {
        semester?.let { coursesOnDate(it, state.courses, state.exceptions, date) }
    }
    val results = searchCourses(courses.orEmpty(), query)
    LazyColumn(
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text("找课程，也给自习留点时间", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                TextButton(onClick = { dateText = date.minusDays(1).toString() }) { Text("前一天") }
                TextButton(onClick = { pickingDate = true }) { Text(dateText) }
                TextButton(onClick = { dateText = date.plusDays(1).toString() }) { Text("后一天") }
            }
            Row {
                TextButton(onClick = { dateText = LocalDate.now().toString() }) { Text("今天") }
                TextButton(onClick = { dateText = LocalDate.now().plusDays(1).toString() }) { Text("明天") }
                Text("周${listOf("一", "二", "三", "四", "五", "六", "日")[date.dayOfWeek.value - 1]}",
                    Modifier.padding(12.dp))
            }
        }
        if (courses == null) {
            item {
                Text(if (semester == null) "先导入课表，即可查询课程和空闲节次。"
                    else "所选日期不在当前学期内（${semester.startDate} 至 ${semester.startDate.plusWeeks(semester.totalWeeks.toLong()).minusDays(1)}）。")
            }
        } else {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text("当天空闲节次", style = MaterialTheme.typography.titleMedium)
                        val free = freePeriodRanges(courses)
                        if (free.isEmpty()) Text("1–14 节均有课程")
                        free.forEach { range ->
                            val start = BistuPeriodTimes[range.start - 1].startsAt
                            val end = BistuPeriodTimes[range.end - 1].endsAt
                            Text("第 ${range.start}–${range.end} 节  ·  $start–$end")
                        }
                        Text("仅依据个人课表，不代表教室空闲；区间可能包含课间或午休。",
                            style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
            item {
                OutlinedTextField(
                    value = query, onValueChange = { query = it }, singleLine = true,
                    label = { Text("搜索当天的课程、老师或教室") },
                    trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("清除") } },
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            item { Text("当天 ${courses.size} 次课 · 找到 ${results.size} 次", style = MaterialTheme.typography.labelLarge) }
            if (results.isEmpty()) {
                item { Text(if (courses.isEmpty()) "这一天没有安排课程。" else "没有匹配结果，试试课程名或教室编号。") }
            }
            items(results, key = { it.meeting.id }) { scheduled ->
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(scheduled.course.name, style = MaterialTheme.typography.titleMedium)
                        Text("第 ${scheduled.meeting.startPeriod}–${scheduled.meeting.endPeriod} 节 · ${scheduled.meeting.room.ifBlank { "教室待定" }}")
                        if (scheduled.course.teacher.isNotBlank()) Text(scheduled.course.teacher)
                        if (scheduled.isDateException) Text("已计入临时调课", color = MaterialTheme.colorScheme.primary)
                        TextButton(onClick = { onAddTask(scheduled.course.name) }) { Text("为这门课记待办") }
                    }
                }
            }
        }
    }
    if (pickingDate) {
        PickDate(initial = date, onDismiss = { pickingDate = false }) {
            dateText = it.toString()
            pickingDate = false
        }
    }
}

@Composable
private fun TaskEditor(
    task: StudyTask?,
    suggestedCourse: String,
    busy: Boolean,
    error: String?,
    onDismiss: () -> Unit,
    onSave: (StudyTask) -> Unit,
) {
    val id by rememberSaveable { mutableStateOf(task?.id ?: UUID.randomUUID().toString()) }
    var title by rememberSaveable { mutableStateOf(task?.title.orEmpty()) }
    var kindName by rememberSaveable { mutableStateOf((task?.kind ?: StudyTaskKind.HOMEWORK).name) }
    var course by rememberSaveable { mutableStateOf(task?.courseName ?: suggestedCourse) }
    var date by rememberSaveable { mutableStateOf((task?.dueAt?.toLocalDate() ?: LocalDate.now()).toString()) }
    var time by rememberSaveable { mutableStateOf(task?.dueAt?.toLocalTime()?.format(DateTimeFormatter.ofPattern("HH:mm")) ?: "23:59") }
    var notes by rememberSaveable { mutableStateOf(task?.notes.orEmpty()) }
    var validationError by rememberSaveable { mutableStateOf<String?>(null) }
    var pickingDate by rememberSaveable { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = { if (!busy) onDismiss() },
        properties = DialogProperties(dismissOnBackPress = !busy, dismissOnClickOutside = !busy),
        title = { Text(if (task == null) "新建待办" else "编辑待办") },
        text = {
            Column(
                Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(title, { title = it }, enabled = !busy, label = { Text("标题 *") }, modifier = Modifier.fillMaxWidth())
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    StudyTaskKind.entries.forEach { kind ->
                        FilterChip(selected = kindName == kind.name, enabled = !busy,
                            onClick = { kindName = kind.name }, label = { Text(kind.label) })
                    }
                }
                OutlinedTextField(course, { course = it }, enabled = !busy, label = { Text("课程名（可选）") }, modifier = Modifier.fillMaxWidth())
                TextButton(enabled = !busy, onClick = { pickingDate = true }) { Text("截止日期  $date") }
                OutlinedTextField(
                    time, { time = it }, enabled = !busy, singleLine = true,
                    label = { Text("截止时间（24 小时制 HH:mm）") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(notes, { notes = it }, enabled = !busy, label = { Text("备注 / 考试地点（可选）") },
                    modifier = Modifier.fillMaxWidth(), minLines = 2, maxLines = 5)
                (validationError ?: error)?.let { Text(it, color = MaterialTheme.colorScheme.error) }
            }
        },
        confirmButton = {
            Button(enabled = !busy, onClick = {
                val parsedTime = if (Regex("\\d{2}:\\d{2}").matches(time)) runCatching { LocalTime.parse(time) }.getOrNull() else null
                if (parsedTime == null) {
                    validationError = "请输入有效时间，例如 09:30 或 23:59"
                } else {
                    val draft = StudyTask(id, title, StudyTaskKind.valueOf(kindName), course,
                        LocalDate.parse(date).atTime(parsedTime), notes, task?.completed ?: false)
                    val result = runCatching { draft.validated() }
                    validationError = result.exceptionOrNull()?.message
                    result.getOrNull()?.let(onSave)
                }
            }) { Text(if (busy) "保存中…" else "保存") }
        },
        dismissButton = { TextButton(enabled = !busy, onClick = onDismiss) { Text("取消") } },
    )
    if (pickingDate) {
        PickDate(initial = LocalDate.parse(date), onDismiss = { pickingDate = false }) {
            date = it.toString()
            pickingDate = false
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PickDate(initial: LocalDate, onDismiss: () -> Unit, onPicked: (LocalDate) -> Unit) {
    val picker = rememberDatePickerState(initialSelectedDateMillis = initial.atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli())
    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(enabled = picker.selectedDateMillis != null, onClick = {
                picker.selectedDateMillis?.let { onPicked(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate()) }
            }) { Text("确定") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    ) { DatePicker(state = picker) }
}
