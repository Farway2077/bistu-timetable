package cn.edu.bistu.kebiao.ui.timetable

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import cn.edu.bistu.kebiao.domain.BistuPeriodTimes
import cn.edu.bistu.kebiao.domain.Course
import cn.edu.bistu.kebiao.domain.Meeting
import cn.edu.bistu.kebiao.domain.ScheduledCourse
import cn.edu.bistu.kebiao.domain.Semester
import cn.edu.bistu.kebiao.domain.WeekPattern
import cn.edu.bistu.kebiao.ui.theme.KebiaoTheme
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.distinctUntilChanged

private enum class TimetableViewMode {
    WEEK,
    TODAY,
}

private data class CoursePalette(
    val container: Color,
    val content: Color,
    val accent: Color,
)

private val lightCoursePalettes = listOf(
    CoursePalette(Color(0xFFD7E8E1), Color(0xFF173A31), Color(0xFF477D6E)),
    CoursePalette(Color(0xFFF1D9CF), Color(0xFF4A2920), Color(0xFFAA6955)),
    CoursePalette(Color(0xFFD9E1F1), Color(0xFF253653), Color(0xFF607BA9)),
    CoursePalette(Color(0xFFF0E2BC), Color(0xFF463A18), Color(0xFFA98A35)),
    CoursePalette(Color(0xFFE4D7EA), Color(0xFF402B49), Color(0xFF8A6397)),
    CoursePalette(Color(0xFFD2E7E8), Color(0xFF1D3B3D), Color(0xFF4B8588)),
    CoursePalette(Color(0xFFE9D9CA), Color(0xFF493426), Color(0xFF9A7356)),
    CoursePalette(Color(0xFFDDE6CA), Color(0xFF34401E), Color(0xFF74894A)),
)

private val darkCoursePalettes = listOf(
    CoursePalette(Color(0xFF29483F), Color(0xFFE5F5EE), Color(0xFF8FC7B5)),
    CoursePalette(Color(0xFF533A34), Color(0xFFFFE9E1), Color(0xFFE2A18C)),
    CoursePalette(Color(0xFF33425C), Color(0xFFEAF0FF), Color(0xFFA9BCE4)),
    CoursePalette(Color(0xFF504728), Color(0xFFFFF3C9), Color(0xFFD8BF68)),
    CoursePalette(Color(0xFF493852), Color(0xFFF6E8FA), Color(0xFFC6A3D1)),
    CoursePalette(Color(0xFF2E4A4C), Color(0xFFE5F7F8), Color(0xFF91C9CC)),
    CoursePalette(Color(0xFF4E4034), Color(0xFFFFEFE1), Color(0xFFD0AA87)),
    CoursePalette(Color(0xFF3E482E), Color(0xFFF0F7DF), Color(0xFFAEC47A)),
)

private val weekdays = listOf("一", "二", "三", "四", "五", "六", "日")
private val fullWeekdays = listOf("星期一", "星期二", "星期三", "星期四", "星期五", "星期六", "星期日")

@Composable
fun TimetableScreen(
    viewModel: TimetableViewModel,
    onImport: () -> Unit,
    onManageSchedule: () -> Unit,
    onOpenStudyTools: () -> Unit,
) {
    val state by viewModel.uiState.collectAsState()
    val appContext = LocalContext.current.applicationContext
    val displayPreferences = remember(appContext) { TimetableDisplayPreferences(appContext) }
    var cardTextSizeName by rememberSaveable {
        mutableStateOf(displayPreferences.getCourseCardTextSize().name)
    }
    val cardTextSize = CourseCardTextSize.fromStorage(cardTextSizeName)
    TimetableContent(
        state = state,
        onWeekSelected = viewModel::selectWeek,
        onPreviousWeek = viewModel::previousWeek,
        onNextWeek = viewModel::nextWeek,
        onThisWeek = viewModel::goToThisWeek,
        onImport = onImport,
        onManageSchedule = onManageSchedule,
        onOpenStudyTools = onOpenStudyTools,
        onUndoImport = viewModel::undoLastImport,
        onStartDateChange = viewModel::updateSemesterStartDate,
        cardTextSize = cardTextSize,
        onCardTextSizeChange = { newSize ->
            cardTextSizeName = newSize.name
            displayPreferences.setCourseCardTextSize(newSize)
        },
    )

    state.settingsError?.let { message ->
        AlertDialog(
            onDismissRequest = viewModel::dismissSettingsError,
            confirmButton = {
                TextButton(onClick = viewModel::dismissSettingsError) { Text("知道了") }
            },
            title = { Text("课表提示") },
            text = { Text(message) },
        )
    }
}

@Composable
private fun TimetableContent(
    state: TimetableUiState,
    onWeekSelected: (Int) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onThisWeek: () -> Unit,
    onImport: () -> Unit,
    onManageSchedule: () -> Unit,
    onUndoImport: () -> Unit,
    onStartDateChange: (LocalDate) -> Unit,
    cardTextSize: CourseCardTextSize,
    onCardTextSizeChange: (CourseCardTextSize) -> Unit,
    initialMode: TimetableViewMode = TimetableViewMode.WEEK,
    previewNow: LocalDateTime? = null,
    onOpenStudyTools: () -> Unit = {},
) {
    Scaffold(
        bottomBar = {
            Surface(tonalElevation = 2.dp) {
                TextButton(
                    onClick = onOpenStudyTools,
                    modifier = Modifier.fillMaxWidth().navigationBarsPadding(),
                ) { Text("学习助手 · 作业考试 / 查课 / 空闲节次") }
            }
        },
        modifier = Modifier.fillMaxSize(),
        containerColor = MaterialTheme.colorScheme.background,
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
    ) { innerPadding ->
        when {
            state.isLoading -> LoadingSchedule(Modifier.fillMaxSize().padding(innerPadding))
            state.semester == null -> EmptySchedule(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                onImport = onImport,
            )
            else -> LoadedSchedule(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                state = state,
                onWeekSelected = onWeekSelected,
                onPreviousWeek = onPreviousWeek,
                onNextWeek = onNextWeek,
                onThisWeek = onThisWeek,
                onImport = onImport,
                onManageSchedule = onManageSchedule,
                onUndoImport = onUndoImport,
                onStartDateChange = onStartDateChange,
                cardTextSize = cardTextSize,
                onCardTextSizeChange = onCardTextSizeChange,
                initialMode = initialMode,
                previewNow = previewNow,
            )
        }
    }
}

@Composable
private fun LoadingSchedule(modifier: Modifier) {
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            CircularProgressIndicator(strokeWidth = 2.dp)
            Spacer(Modifier.height(14.dp))
            Text("正在铺开课表…", color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun EmptySchedule(modifier: Modifier, onImport: () -> Unit) {
    Column(
        modifier = modifier
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 22.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Surface(shape = RoundedCornerShape(999.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Text(
                    text = "为北信科学生准备",
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
            }
            Spacer(Modifier.height(20.dp))
            Text(
                text = "纸间课表",
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Spacer(Modifier.height(10.dp))
            Text(
                text = "把教务系统里拥挤的表格，整理成每天一眼就能看懂的安排。",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.widthIn(max = 320.dp),
            )
        }

        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Surface(modifier = Modifier.size(132.dp), shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Box(contentAlignment = Alignment.Center) {
                    Text(
                        text = "课",
                        style = MaterialTheme.typography.displaySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer,
                    )
                }
            }
        }

        Surface(
            color = MaterialTheme.colorScheme.surface,
            shape = RoundedCornerShape(28.dp),
            tonalElevation = 1.dp,
            shadowElevation = 2.dp,
        ) {
            Column(Modifier.padding(22.dp)) {
                Text("从学校课表开始", style = MaterialTheme.typography.headlineSmall)
                Spacer(Modifier.height(8.dp))
                Text(
                    "登录发生在学校统一认证页面，纸间课表不会读取或保存你的密码。",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(18.dp))
                Button(onClick = onImport, modifier = Modifier.fillMaxWidth()) {
                    Text("从教务系统导入")
                }
            }
        }
    }
}

@Composable
private fun LoadedSchedule(
    modifier: Modifier,
    state: TimetableUiState,
    onWeekSelected: (Int) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onThisWeek: () -> Unit,
    onImport: () -> Unit,
    onManageSchedule: () -> Unit,
    onUndoImport: () -> Unit,
    onStartDateChange: (LocalDate) -> Unit,
    cardTextSize: CourseCardTextSize,
    onCardTextSizeChange: (CourseCardTextSize) -> Unit,
    initialMode: TimetableViewMode,
    previewNow: LocalDateTime?,
) {
    val semester = requireNotNull(state.semester)
    val liveNow = rememberCurrentMinute()
    val now = previewNow ?: liveNow
    val todayCourses = remember(state.courses, state.exceptions, semester, now.toLocalDate()) {
        teachingWeekForDate(semester, now.toLocalDate())
            ?.let(state::coursesForWeek)
            .orEmpty()
    }
    val todaySummary = remember(todayCourses, semester, now) {
        buildTodayScheduleSummary(todayCourses, semester, now)
    }
    var viewModeName by rememberSaveable { mutableStateOf(initialMode.name) }
    val viewMode = TimetableViewMode.entries.firstOrNull { it.name == viewModeName } ?: TimetableViewMode.WEEK
    var selectedCourse by remember { mutableStateOf<ScheduledCourse?>(null) }
    var showSettings by rememberSaveable { mutableStateOf(false) }
    var showStartDatePicker by rememberSaveable { mutableStateOf(false) }
    var showUndoConfirmation by rememberSaveable { mutableStateOf(false) }
    val displayedCourses = if (viewMode == TimetableViewMode.WEEK) state.visibleCourses else todaySummary.courses
    val conflictIds = remember(displayedCourses) { findConflicts(displayedCourses) }

    Column(modifier = modifier.statusBarsPadding().navigationBarsPadding()) {
        ScheduleHeader(
            semester = semester,
            selectedWeek = state.selectedWeek,
            viewMode = viewMode,
            now = now,
            todaySummary = todaySummary,
            onImport = onImport,
            onSettingsClick = { showSettings = true },
        )
        ViewModeToggle(
            selected = viewMode,
            onSelected = { viewModeName = it.name },
            modifier = Modifier.padding(horizontal = 18.dp, vertical = 4.dp),
        )

        when (viewMode) {
            TimetableViewMode.WEEK -> WeekScheduleBody(
                modifier = Modifier.weight(1f),
                state = state,
                todayWeek = todaySummary.teachingWeek,
                cardTextSize = cardTextSize,
                onWeekSelected = onWeekSelected,
                onPreviousWeek = onPreviousWeek,
                onNextWeek = onNextWeek,
                onThisWeek = onThisWeek,
                onCourseClick = { selectedCourse = it },
            )
            TimetableViewMode.TODAY -> TodayScheduleBody(
                modifier = Modifier.weight(1f),
                summary = todaySummary,
                now = now,
                cardTextSize = cardTextSize,
                conflictIds = conflictIds,
                onShowWeek = { viewModeName = TimetableViewMode.WEEK.name },
                onCourseClick = { selectedCourse = it },
            )
        }
    }

    selectedCourse?.let { scheduled ->
        CourseDetailDialog(
            scheduled = scheduled,
            hasConflict = scheduled.meeting.id in conflictIds,
            onDismiss = { selectedCourse = null },
        )
    }

    if (showSettings) {
        TimetableSettingsDialog(
            semester = semester,
            cardTextSize = cardTextSize,
            canUndoLastImport = state.canUndoLastImport,
            onManageSchedule = {
                showSettings = false
                onManageSchedule()
            },
            onUndoImport = {
                showSettings = false
                showUndoConfirmation = true
            },
            onStartDateClick = {
                showSettings = false
                showStartDatePicker = true
            },
            onCardTextSizeChange = onCardTextSizeChange,
            onDismiss = { showSettings = false },
        )
    }

    if (showStartDatePicker) {
        FirstWeekStartDateDialog(
            initialDate = semester.startDate,
            onDismiss = {
                showStartDatePicker = false
                showSettings = true
            },
            onConfirm = { date ->
                onStartDateChange(date)
                showStartDatePicker = false
                showSettings = true
            },
        )
    }

    if (showUndoConfirmation) {
        AlertDialog(
            onDismissRequest = { showUndoConfirmation = false },
            title = { Text("撤销上次同步？") },
            text = { Text("教务课程会恢复到同步前；手动课程、本地修正和临时调整不会受影响。") },
            dismissButton = {
                TextButton(onClick = { showUndoConfirmation = false }) { Text("取消") }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showUndoConfirmation = false
                        onUndoImport()
                    },
                ) { Text("确认撤销") }
            },
        )
    }
}

@Composable
private fun ScheduleHeader(
    semester: Semester,
    selectedWeek: Int,
    viewMode: TimetableViewMode,
    now: LocalDateTime,
    todaySummary: TodayScheduleSummary,
    onImport: () -> Unit,
    onSettingsClick: () -> Unit,
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("M月d日") }
    val displayWeek = if (viewMode == TimetableViewMode.TODAY) todaySummary.teachingWeek else selectedWeek

    Column(modifier = Modifier.padding(start = 18.dp, top = 4.dp, end = 18.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(
                    text = dateFormatter.format(now),
                    style = MaterialTheme.typography.displaySmall,
                    color = MaterialTheme.colorScheme.onBackground,
                )
                Text(
                    text = "${fullWeekdays[now.dayOfWeek.value - 1]} · ${displayWeek?.let { "第 $it 周" } ?: "学期外"}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = semester.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                TextButton(
                    onClick = onSettingsClick,
                    modifier = Modifier.semantics { contentDescription = "课表设置" },
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp),
                ) {
                    Text("设置", style = MaterialTheme.typography.labelMedium)
                }
                Spacer(Modifier.width(4.dp))
                OutlinedButton(
                    onClick = onImport,
                    modifier = Modifier.semantics { contentDescription = "同步课表" },
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                ) {
                    Text("同步")
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        TodayStatusCard(summary = todaySummary)
    }
}

@Composable
private fun TimetableSettingsDialog(
    semester: Semester,
    cardTextSize: CourseCardTextSize,
    canUndoLastImport: Boolean,
    onManageSchedule: () -> Unit,
    onUndoImport: () -> Unit,
    onStartDateClick: () -> Unit,
    onCardTextSizeChange: (CourseCardTextSize) -> Unit,
    onDismiss: () -> Unit,
) {
    val dateFormatter = remember { DateTimeFormatter.ofPattern("yyyy年M月d日") }
    val previewPalette = coursePalette(0)

    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("完成") } },
        title = { Text("课表设置") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text("课程与同步", style = MaterialTheme.typography.labelLarge)
                Surface(
                    onClick = onManageSchedule,
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.primaryContainer,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("管理课程", style = MaterialTheme.typography.titleSmall)
                            Text(
                                "手动添加、长期修正、停课与调课",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                            )
                        }
                        Text("打开", style = MaterialTheme.typography.labelLarge)
                    }
                }
                if (canUndoLastImport) {
                    Surface(
                        onClick = onUndoImport,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Column(Modifier.weight(1f)) {
                                Text("撤销上次同步", style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "只恢复教务课程，保留所有本地内容",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onTertiaryContainer,
                                )
                            }
                            Text("撤销", style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }

                Text("第一周", style = MaterialTheme.typography.labelLarge)
                Surface(
                    onClick = onStartDateClick,
                    modifier = Modifier.fillMaxWidth().semantics {
                        contentDescription =
                            "设置第一周第一天，当前${dateFormatter.format(semester.startDate)}"
                    },
                    shape = RoundedCornerShape(14.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Column(Modifier.weight(1f)) {
                            Text("第 1 周星期一", style = MaterialTheme.typography.titleSmall)
                            Text(
                                dateFormatter.format(semester.startDate),
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        Text("更改", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("课程卡片字号", style = MaterialTheme.typography.labelLarge)
                    Text(
                        "只调整本周和今日课程卡片内的文字",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    CourseCardTextSize.entries.forEach { option ->
                        val selected = option == cardTextSize
                        Surface(
                            onClick = { onCardTextSizeChange(option) },
                            modifier = Modifier
                                .weight(1f)
                                .semantics {
                                    this.selected = selected
                                    role = Role.RadioButton
                                    contentDescription = "${option.label}号课程卡片文字"
                                },
                            shape = RoundedCornerShape(12.dp),
                            color = if (selected) {
                                MaterialTheme.colorScheme.primaryContainer
                            } else {
                                MaterialTheme.colorScheme.surfaceVariant
                            },
                        ) {
                            Box(
                                modifier = Modifier.padding(vertical = 10.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Text(
                                    option.label,
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.onPrimaryContainer
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            }
                        }
                    }
                }

                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("预览", style = MaterialTheme.typography.labelLarge)
                    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.CenterStart) {
                        Surface(
                            modifier = Modifier.width(104.dp).height(82.dp),
                            shape = RoundedCornerShape(10.dp),
                            color = previewPalette.container,
                            contentColor = previewPalette.content,
                        ) {
                            Column(Modifier.padding(horizontal = 7.dp, vertical = 8.dp)) {
                                Text(
                                    text = "高等数学",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = cardTextSize.scaledSp(10f),
                                    lineHeight = cardTextSize.scaledSp(12f),
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis,
                                )
                                Spacer(Modifier.weight(1f))
                                Text(
                                    text = "教二 301",
                                    fontSize = cardTextSize.scaledSp(8f),
                                    lineHeight = cardTextSize.scaledSp(9f),
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    color = previewPalette.content.copy(alpha = 0.76f),
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FirstWeekStartDateDialog(
    initialDate: LocalDate,
    onDismiss: () -> Unit,
    onConfirm: (LocalDate) -> Unit,
) {
    val pickerState = rememberDatePickerState(
        initialSelectedDateMillis = initialDate.toDatePickerUtcMillis(),
    )
    val selectedDate = pickerState.selectedDateMillis?.let(::localDateFromDatePickerUtcMillis)
    val isValid = selectedDate?.let(::isValidFirstWeekStart) == true

    DatePickerDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(
                enabled = isValid,
                onClick = { selectedDate?.let(onConfirm) },
            ) {
                Text("保存")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("取消") } },
    ) {
        Column {
            DatePicker(
                state = pickerState,
                title = {
                    Column(Modifier.padding(start = 24.dp, top = 16.dp, end = 24.dp)) {
                        Text("设置第一周第一天", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "请选择第 1 周的星期一",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                },
                showModeToggle = false,
            )
            if (selectedDate != null && !isValid) {
                Text(
                    text = "所选日期不是星期一，请重新选择。",
                    modifier = Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                )
            }
        }
    }
}

@Composable
private fun TodayStatusCard(summary: TodayScheduleSummary) {
    val (title, subtitle) = when (summary.phase) {
        TodayPhase.NO_COURSES -> {
            if (summary.teachingWeek == null) {
                "当前不在教学周" to "周课表仍可浏览，今日没有需要追赶的安排"
            } else {
                "今天没有课" to "留一点空白，也是一种安排"
            }
        }
        TodayPhase.UPCOMING -> {
            val course = requireNotNull(summary.focusCourse)
            "距下节课 ${formatMinutes(summary.minutesRemaining)}" to
                "${course.course.name} · ${courseClockRange(course)}${course.meeting.room.withLeadingDot()}"
        }
        TodayPhase.IN_PROGRESS -> {
            val course = requireNotNull(summary.focusCourse)
            "正在上课 · ${formatMinutes(summary.minutesRemaining)}后下课" to
                "${course.course.name}${course.meeting.room.withLeadingDot()}"
        }
        TodayPhase.FINISHED -> "今天的课已结束" to "共 ${summary.courses.size} 条上课安排，辛苦了"
    }
    val accent = when (summary.phase) {
        TodayPhase.IN_PROGRESS -> MaterialTheme.colorScheme.secondary
        TodayPhase.UPCOMING -> MaterialTheme.colorScheme.primary
        TodayPhase.FINISHED, TodayPhase.NO_COURSES -> MaterialTheme.colorScheme.outline
    }

    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        color = MaterialTheme.colorScheme.surface,
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(modifier = Modifier.size(10.dp).clip(CircleShape).background(accent))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun ViewModeToggle(
    selected: TimetableViewMode,
    onSelected: (TimetableViewMode) -> Unit,
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        color = MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Row(Modifier.padding(4.dp)) {
            ModeButton("本周", selected == TimetableViewMode.WEEK) { onSelected(TimetableViewMode.WEEK) }
            ModeButton("今日", selected == TimetableViewMode.TODAY) { onSelected(TimetableViewMode.TODAY) }
        }
    }
}

@Composable
private fun RowScope.ModeButton(
    text: String,
    isSelected: Boolean,
    onClick: () -> Unit,
) {
    val shape = RoundedCornerShape(14.dp)
    Box(
        modifier = Modifier
            .weight(1f)
            .clip(shape)
            .background(if (isSelected) MaterialTheme.colorScheme.surface else Color.Transparent)
            .clickable(role = Role.Tab, onClick = onClick)
            .semantics {
                selected = isSelected
                role = Role.Tab
            }
            .padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.labelLarge,
            color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WeekScheduleBody(
    modifier: Modifier,
    state: TimetableUiState,
    todayWeek: Int?,
    cardTextSize: CourseCardTextSize,
    onWeekSelected: (Int) -> Unit,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onThisWeek: () -> Unit,
    onCourseClick: (ScheduledCourse) -> Unit,
) {
    val semester = requireNotNull(state.semester)
    val totalWeeks = semester.totalWeeks.coerceAtLeast(1)
    val pagerState = rememberPagerState(
        initialPage = pageIndexForWeek(state.selectedWeek, totalWeeks),
        pageCount = { totalWeeks },
    )
    val latestOnWeekSelected by rememberUpdatedState(onWeekSelected)

    LaunchedEffect(pagerState, totalWeeks) {
        snapshotFlow { pagerState.settledPage }
            .distinctUntilChanged()
            .collect { pageIndex ->
                latestOnWeekSelected(weekForPageIndex(pageIndex, totalWeeks))
            }
    }

    LaunchedEffect(state.selectedWeek, totalWeeks) {
        val targetPage = pageIndexForWeek(state.selectedWeek, totalWeeks)
        if (pagerState.settledPage != targetPage) {
            pagerState.animateScrollToPage(targetPage)
        }
    }

    HorizontalPager(
        state = pagerState,
        modifier = modifier.clipToBounds(),
        beyondBoundsPageCount = 1,
        key = { pageIndex -> "${semester.id}-$pageIndex" },
    ) { pageIndex ->
        val displayedWeek = weekForPageIndex(pageIndex, totalWeeks)
        WeekPage(
            modifier = Modifier.fillMaxSize(),
            semester = semester,
            displayedWeek = displayedWeek,
            todayWeek = todayWeek,
            visibleCourses = state.coursesForWeek(displayedWeek),
            cardTextSize = cardTextSize,
            onPreviousWeek = onPreviousWeek,
            onNextWeek = onNextWeek,
            onThisWeek = onThisWeek,
            onCourseClick = onCourseClick,
        )
    }
}

@Composable
private fun WeekPage(
    modifier: Modifier,
    semester: Semester,
    displayedWeek: Int,
    todayWeek: Int?,
    visibleCourses: List<ScheduledCourse>,
    cardTextSize: CourseCardTextSize,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onThisWeek: () -> Unit,
    onCourseClick: (ScheduledCourse) -> Unit,
) {
    val weekStart = semester.startDate.plusWeeks((displayedWeek - 1).toLong())
    val conflictIds = remember(visibleCourses) { findConflicts(visibleCourses) }

    Column(modifier = modifier) {
        WeekNavigator(
            selectedWeek = displayedWeek,
            totalWeeks = semester.totalWeeks,
            weekStart = weekStart,
            isThisWeek = displayedWeek == todayWeek,
            onPreviousWeek = onPreviousWeek,
            onNextWeek = onNextWeek,
            onThisWeek = onThisWeek,
        )
        Column(modifier = Modifier.fillMaxSize().verticalScroll(rememberScrollState())) {
            if (visibleCourses.isEmpty()) EmptyWeekNotice()
            TimetableGrid(
                courses = visibleCourses,
                weekStart = weekStart,
                cardTextSize = cardTextSize,
                conflictIds = conflictIds,
                onCourseClick = onCourseClick,
            )
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun WeekNavigator(
    selectedWeek: Int,
    totalWeeks: Int,
    weekStart: LocalDate,
    isThisWeek: Boolean,
    onPreviousWeek: () -> Unit,
    onNextWeek: () -> Unit,
    onThisWeek: () -> Unit,
) {
    val formatter = remember { DateTimeFormatter.ofPattern("M.d") }
    val range = "${formatter.format(weekStart)} — ${formatter.format(weekStart.plusDays(6))}"

    Row(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 10.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextButton(onClick = onPreviousWeek, enabled = selectedWeek > 1, modifier = Modifier.width(58.dp)) {
            Text("‹ 上周")
        }
        Surface(
            onClick = onThisWeek,
            modifier = Modifier.weight(1f),
            shape = RoundedCornerShape(14.dp),
            color = Color.Transparent,
        ) {
            Column(modifier = Modifier.padding(vertical = 7.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = if (isThisWeek) "第 $selectedWeek 周 · 本周" else "第 $selectedWeek 周",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isThisWeek) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onBackground,
                )
                Text(range, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        TextButton(onClick = onNextWeek, enabled = selectedWeek < totalWeeks, modifier = Modifier.width(58.dp)) {
            Text("下周 ›")
        }
    }
}

@Composable
private fun EmptyWeekNotice() {
    Surface(
        modifier = Modifier.fillMaxWidth().padding(horizontal = 18.dp, vertical = 8.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.secondaryContainer,
    ) {
        Text(
            text = "这一周没有课程安排",
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSecondaryContainer,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun TimetableGrid(
    courses: List<ScheduledCourse>,
    weekStart: LocalDate,
    cardTextSize: CourseCardTextSize,
    conflictIds: Set<String>,
    onCourseClick: (ScheduledCourse) -> Unit,
) {
    val gutter = 46.dp
    val periodHeight = 60.dp
    val headerHeight = 50.dp
    val periodCount = BistuPeriodTimes.size
    val gridHeight = periodHeight * periodCount
    val today = LocalDate.now()

    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val dayWidth = (maxWidth - gutter) / 7
        Column(Modifier.fillMaxWidth()) {
            Row(Modifier.height(headerHeight)) {
                Box(Modifier.width(gutter).fillMaxHeight(), contentAlignment = Alignment.Center) {
                    Text("节次", color = MaterialTheme.colorScheme.onSurfaceVariant, fontSize = 10.sp)
                }
                weekdays.forEachIndexed { index, name ->
                    val date = weekStart.plusDays(index.toLong())
                    val isToday = date == today
                    Column(
                        modifier = Modifier
                            .width(dayWidth)
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp))
                            .background(
                                if (isToday) MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f)
                                else Color.Transparent,
                            ),
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Text(
                            text = name,
                            fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                            fontSize = 12.sp,
                            color = if (isToday) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onBackground,
                        )
                        Text(
                            text = "${date.monthValue}/${date.dayOfMonth}",
                            fontSize = 9.sp,
                            color = if (isToday) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            }
            Box(modifier = Modifier.fillMaxWidth().height(gridHeight)) {
                val todayIndex = (0..6).firstOrNull { weekStart.plusDays(it.toLong()) == today }
                if (todayIndex != null) {
                    Box(
                        modifier = Modifier
                            .offset(x = gutter + dayWidth * todayIndex)
                            .width(dayWidth)
                            .fillMaxHeight()
                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.045f)),
                    )
                }

                val lineColor = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.8f)
                Canvas(Modifier.matchParentSize()) {
                    val gutterPx = gutter.toPx()
                    val dayPx = dayWidth.toPx()
                    val periodPx = periodHeight.toPx()
                    for (row in 0..periodCount) {
                        val y = row * periodPx
                        drawLine(lineColor, Offset(0f, y), Offset(size.width, y), strokeWidth = 1f)
                    }
                    for (column in 0..7) {
                        val x = gutterPx + column * dayPx
                        drawLine(lineColor, Offset(x, 0f), Offset(x, size.height), strokeWidth = 1f)
                    }
                    drawRect(lineColor, style = Stroke(width = 1f))
                }

                BistuPeriodTimes.forEachIndexed { index, period ->
                    Box(
                        modifier = Modifier.offset(y = periodHeight * index).width(gutter).height(periodHeight),
                        contentAlignment = Alignment.Center,
                    ) {
                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${period.period}",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                            Text(
                                text = "${period.startsAt}\n${period.endsAt}",
                                fontSize = 8.sp,
                                lineHeight = 9.sp,
                                textAlign = TextAlign.Center,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                            )
                        }
                    }
                }

                courses.forEach { scheduled ->
                    val meeting = scheduled.meeting
                    if (meeting.weekday in 1..7 && meeting.startPeriod <= periodCount) {
                        val span = meeting.endPeriod.coerceAtMost(periodCount) - meeting.startPeriod + 1
                        CompactCourseCard(
                            scheduled = scheduled,
                            hasConflict = meeting.id in conflictIds,
                            showRoom = span >= 2,
                            textSize = cardTextSize,
                            modifier = Modifier
                                .offset(
                                    x = gutter + dayWidth * (meeting.weekday - 1) + 2.dp,
                                    y = periodHeight * (meeting.startPeriod - 1) + 2.dp,
                                )
                                .width(dayWidth - 4.dp)
                                .height(periodHeight * span - 4.dp),
                            onClick = { onCourseClick(scheduled) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CompactCourseCard(
    scheduled: ScheduledCourse,
    hasConflict: Boolean,
    showRoom: Boolean,
    textSize: CourseCardTextSize,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    val palette = coursePalette(scheduled.course.colorIndex)
    val shape = RoundedCornerShape(9.dp)
    val borderedModifier = if (hasConflict) modifier.border(1.dp, MaterialTheme.colorScheme.error, shape) else modifier
    Card(
        modifier = borderedModifier
            .clip(shape)
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = courseAccessibilityText(scheduled, hasConflict)
            },
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = palette.container, contentColor = palette.content),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column(Modifier.fillMaxSize().padding(horizontal = 4.dp, vertical = 5.dp)) {
            if (hasConflict) {
                Text(
                    text = "冲突",
                    color = MaterialTheme.colorScheme.error,
                    fontSize = textSize.scaledSp(8f),
                    lineHeight = textSize.scaledSp(9f),
                    fontWeight = FontWeight.Bold,
                )
            }
            Text(
                text = scheduled.course.name,
                fontWeight = FontWeight.Bold,
                fontSize = textSize.scaledSp(10f),
                lineHeight = textSize.scaledSp(12f),
                maxLines = if (showRoom) 4 else 3,
                overflow = TextOverflow.Ellipsis,
                color = palette.content,
            )
            Spacer(Modifier.weight(1f))
            if (showRoom && scheduled.meeting.room.isNotBlank()) {
                Text(
                    text = scheduled.meeting.room,
                    fontSize = textSize.scaledSp(8f),
                    lineHeight = textSize.scaledSp(9f),
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    color = palette.content.copy(alpha = 0.76f),
                )
            }
        }
    }
}

@Composable
private fun TodayScheduleBody(
    modifier: Modifier,
    summary: TodayScheduleSummary,
    now: LocalDateTime,
    cardTextSize: CourseCardTextSize,
    conflictIds: Set<String>,
    onShowWeek: () -> Unit,
    onCourseClick: (ScheduledCourse) -> Unit,
) {
    if (summary.courses.isEmpty()) {
        Column(
            modifier = modifier.padding(horizontal = 24.dp, vertical = 30.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Surface(shape = CircleShape, color = MaterialTheme.colorScheme.secondaryContainer) {
                Box(Modifier.size(86.dp), contentAlignment = Alignment.Center) {
                    Text("今", style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.onSecondaryContainer)
                }
            }
            Spacer(Modifier.height(18.dp))
            Text(
                text = if (summary.teachingWeek == null) "当前不在教学周" else "今天没有课程",
                style = MaterialTheme.typography.headlineSmall,
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = "可以回到本周视图，提前看看之后的安排。",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(18.dp))
            OutlinedButton(onClick = onShowWeek) { Text("查看本周") }
        }
        return
    }

    LazyColumn(
        modifier = modifier.fillMaxWidth(),
        contentPadding = PaddingValues(start = 18.dp, top = 4.dp, end = 18.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("今日课程", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                Text(
                    "${summary.courses.size} 条安排",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(summary.courses, key = { it.meeting.id }) { scheduled ->
            TodayCourseCard(
                scheduled = scheduled,
                now = now.toLocalTime(),
                isFocus = scheduled.meeting.id == summary.focusCourse?.meeting?.id,
                focusPhase = summary.phase,
                textSize = cardTextSize,
                hasConflict = scheduled.meeting.id in conflictIds,
                onClick = { onCourseClick(scheduled) },
            )
        }
    }
}

@Composable
private fun TodayCourseCard(
    scheduled: ScheduledCourse,
    now: LocalTime,
    isFocus: Boolean,
    focusPhase: TodayPhase,
    textSize: CourseCardTextSize,
    hasConflict: Boolean,
    onClick: () -> Unit,
) {
    val palette = coursePalette(scheduled.course.colorIndex)
    val status = courseStatus(scheduled, now, isFocus, focusPhase)
    val shape = RoundedCornerShape(20.dp)
    val cardModifier = if (hasConflict) {
        Modifier.fillMaxWidth().border(1.dp, MaterialTheme.colorScheme.error, shape)
    } else {
        Modifier.fillMaxWidth()
    }

    Card(
        modifier = cardModifier
            .clickable(onClick = onClick)
            .semantics(mergeDescendants = true) {
                contentDescription = courseAccessibilityText(scheduled, hasConflict)
            },
        shape = shape,
        colors = CardDefaults.cardColors(containerColor = palette.container, contentColor = palette.content),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 15.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.width(62.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = courseStartTime(scheduled)?.toString().orEmpty(),
                    fontSize = textSize.scaledSp(16f),
                    lineHeight = textSize.scaledSp(20f),
                    fontWeight = FontWeight.SemiBold,
                    color = palette.content,
                )
                Box(
                    modifier = Modifier
                        .padding(vertical = 5.dp)
                        .width(18.dp)
                        .height(2.dp)
                        .background(palette.accent, RoundedCornerShape(999.dp)),
                )
                Text(
                    text = courseEndTime(scheduled)?.toString().orEmpty(),
                    fontSize = textSize.scaledSp(12f),
                    lineHeight = textSize.scaledSp(17f),
                    color = palette.content.copy(alpha = 0.72f),
                )
            }
            Box(
                modifier = Modifier
                    .padding(horizontal = 12.dp)
                    .width(3.dp)
                    .height(66.dp)
                    .background(palette.accent, RoundedCornerShape(999.dp)),
            )
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = scheduled.course.name,
                        fontSize = textSize.scaledSp(16f),
                        lineHeight = textSize.scaledSp(20f),
                        fontWeight = FontWeight.SemiBold,
                        color = palette.content,
                        modifier = Modifier.weight(1f),
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (status.isNotBlank()) {
                        Surface(shape = RoundedCornerShape(999.dp), color = palette.accent.copy(alpha = 0.18f)) {
                            Text(
                                text = status,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                fontSize = textSize.scaledSp(10f),
                                lineHeight = textSize.scaledSp(13f),
                                fontWeight = FontWeight.SemiBold,
                                color = palette.content,
                            )
                        }
                    }
                }
                Spacer(Modifier.height(7.dp))
                Text(
                    text = "第 ${scheduled.meeting.startPeriod}-${scheduled.meeting.endPeriod} 节 · ${courseClockRange(scheduled)}",
                    fontSize = textSize.scaledSp(12f),
                    lineHeight = textSize.scaledSp(17f),
                    color = palette.content.copy(alpha = 0.80f),
                )
                val metadata = listOf(scheduled.meeting.room, scheduled.course.teacher)
                    .filter(String::isNotBlank)
                    .joinToString(" · ")
                if (metadata.isNotBlank()) {
                    Text(
                        text = metadata,
                        fontSize = textSize.scaledSp(12f),
                        lineHeight = textSize.scaledSp(17f),
                        color = palette.content.copy(alpha = 0.72f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                if (hasConflict) {
                    Text(
                        text = "与另一门课程时间冲突",
                        fontSize = textSize.scaledSp(10f),
                        lineHeight = textSize.scaledSp(13f),
                        fontWeight = FontWeight.SemiBold,
                        color = MaterialTheme.colorScheme.error,
                    )
                }
            }
        }
    }
}

@Composable
private fun CourseDetailDialog(
    scheduled: ScheduledCourse,
    hasConflict: Boolean,
    onDismiss: () -> Unit,
) {
    val palette = coursePalette(scheduled.course.colorIndex)
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("知道了") } },
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(12.dp).clip(CircleShape).background(palette.accent))
                Spacer(Modifier.width(10.dp))
                Column {
                    Text(
                        text = scheduled.course.name,
                        style = MaterialTheme.typography.titleLarge,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = "课程详情",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                when {
                    scheduled.isDateException -> DetailLine(
                        "状态",
                        "临时调课" + scheduled.originalDate?.let { "（原 $it）" }.orEmpty(),
                    )
                    scheduled.isLocalOverride -> DetailLine("状态", "本地修正，后续同步会保留")
                    scheduled.course.source == cn.edu.bistu.kebiao.domain.CourseSource.MANUAL -> {
                        DetailLine("状态", "手动课程")
                    }
                }
                DetailLine(
                    "时间",
                    "${fullWeekdays[scheduled.meeting.weekday - 1]}  第 ${scheduled.meeting.startPeriod}-${scheduled.meeting.endPeriod} 节",
                )
                DetailLine("钟点", courseClockRange(scheduled))
                DetailLine("周次", WeekPattern.format(scheduled.meeting.weeks))
                if (scheduled.meeting.room.isNotBlank()) DetailLine("教室", scheduled.meeting.room)
                if (scheduled.course.teacher.isNotBlank()) DetailLine("教师", scheduled.course.teacher)
                if (hasConflict) {
                    Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.errorContainer) {
                        Text(
                            text = "这个时间段还有另一门课程，请核对教务系统。",
                            modifier = Modifier.padding(12.dp),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer,
                        )
                    }
                }
            }
        },
    )
}

@Composable
private fun DetailLine(label: String, value: String) {
    Row(verticalAlignment = Alignment.Top) {
        Text(
            text = label,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.width(58.dp),
            style = MaterialTheme.typography.bodyMedium,
        )
        Text(value, modifier = Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
    }
}

private fun CourseCardTextSize.scaledSp(baseSize: Float) = (baseSize * scale).sp

@Composable
private fun coursePalette(colorIndex: Int): CoursePalette {
    val dark = MaterialTheme.colorScheme.background.luminance() < 0.35f
    val palettes = if (dark) darkCoursePalettes else lightCoursePalettes
    val index = ((colorIndex % palettes.size) + palettes.size) % palettes.size
    return palettes[index]
}

@Composable
private fun rememberCurrentMinute(): LocalDateTime {
    var now by remember { mutableStateOf(LocalDateTime.now()) }
    LaunchedEffect(Unit) {
        while (true) {
            now = LocalDateTime.now()
            val millisIntoMinute = now.second * 1_000L + now.nano / 1_000_000L
            delay((60_000L - millisIntoMinute).coerceAtLeast(1_000L))
        }
    }
    return now
}

private fun courseStartTime(scheduled: ScheduledCourse): LocalTime? =
    BistuPeriodTimes.getOrNull(scheduled.meeting.startPeriod - 1)?.startsAt?.let(LocalTime::parse)

private fun courseEndTime(scheduled: ScheduledCourse): LocalTime? =
    BistuPeriodTimes.getOrNull(scheduled.meeting.endPeriod - 1)?.endsAt?.let(LocalTime::parse)

private fun courseClockRange(scheduled: ScheduledCourse): String {
    val start = courseStartTime(scheduled)?.toString() ?: "--:--"
    val end = courseEndTime(scheduled)?.toString() ?: "--:--"
    return "$start–$end"
}

private fun courseStatus(
    scheduled: ScheduledCourse,
    now: LocalTime,
    isFocus: Boolean,
    focusPhase: TodayPhase,
): String {
    if (isFocus && focusPhase == TodayPhase.IN_PROGRESS) return "进行中"
    if (isFocus && focusPhase == TodayPhase.UPCOMING) return "下一节"
    val start = courseStartTime(scheduled) ?: return ""
    val end = courseEndTime(scheduled) ?: return ""
    return when {
        now < start -> "待上课"
        now >= end -> "已结束"
        else -> "进行中"
    }
}

private fun formatMinutes(minutes: Long?): String {
    val value = minutes ?: return ""
    if (value <= 0) return "不足1分钟"
    val hours = value / 60
    val remaining = value % 60
    return when {
        hours == 0L -> "${remaining}分钟"
        remaining == 0L -> "${hours}小时"
        else -> "${hours}小时${remaining}分钟"
    }
}

private fun String.withLeadingDot(): String = if (isBlank()) "" else " · $this"

private fun courseAccessibilityText(scheduled: ScheduledCourse, hasConflict: Boolean): String = buildString {
    append(scheduled.course.name)
    append("，${fullWeekdays[scheduled.meeting.weekday - 1]}")
    append("，第${scheduled.meeting.startPeriod}到${scheduled.meeting.endPeriod}节")
    if (scheduled.meeting.room.isNotBlank()) append("，${scheduled.meeting.room}")
    if (hasConflict) append("，时间冲突")
}

private fun findConflicts(courses: List<ScheduledCourse>): Set<String> = buildSet {
    courses.forEachIndexed { index, first ->
        courses.drop(index + 1).forEach { second ->
            val overlaps = first.meeting.weekday == second.meeting.weekday &&
                first.meeting.startPeriod <= second.meeting.endPeriod &&
                second.meeting.startPeriod <= first.meeting.endPeriod
            if (overlaps) {
                add(first.meeting.id)
                add(second.meeting.id)
            }
        }
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun WeekTimetablePreview() {
    val now = LocalDateTime.of(2026, 9, 4, 10, 5)
    val semester = Semester("preview", "2026-2027学年第一学期", LocalDate.of(2026, 8, 24), 20)
    val samples = previewCourses(semester)
    KebiaoTheme(darkTheme = false) {
        TimetableContent(
            state = TimetableUiState(false, semester, 2, samples),
            onWeekSelected = {},
            onPreviousWeek = {},
            onNextWeek = {},
            onThisWeek = {},
            onImport = {},
            onManageSchedule = {},
            onUndoImport = {},
            onStartDateChange = {},
            cardTextSize = CourseCardTextSize.STANDARD,
            onCardTextSizeChange = {},
            previewNow = now,
        )
    }
}

@Preview(showBackground = true, widthDp = 390, heightDp = 844)
@Composable
private fun TodayTimetableDarkPreview() {
    val now = LocalDateTime.of(2026, 9, 4, 14, 32)
    val semester = Semester("preview", "2026-2027学年第一学期", LocalDate.of(2026, 8, 24), 20)
    val samples = previewCourses(semester)
    KebiaoTheme(darkTheme = true) {
        TimetableContent(
            state = TimetableUiState(false, semester, 2, samples),
            onWeekSelected = {},
            onPreviousWeek = {},
            onNextWeek = {},
            onThisWeek = {},
            onImport = {},
            onManageSchedule = {},
            onUndoImport = {},
            onStartDateChange = {},
            cardTextSize = CourseCardTextSize.LARGE,
            onCardTextSizeChange = {},
            initialMode = TimetableViewMode.TODAY,
            previewNow = now,
        )
    }
}

private fun previewCourses(semester: Semester): List<ScheduledCourse> = listOf(
    previewCourse(semester, "高等数学", "张老师", "教二301", 1, 1, 2, 0),
    previewCourse(semester, "大学物理", "李老师", "XXD-407", 3, 3, 4, 2),
    previewCourse(semester, "程序设计基础", "王老师", "信息楼B305", 5, 5, 7, 5),
    previewCourse(semester, "大学英语", "赵老师", "教一206", 5, 1, 2, 1),
)

private fun previewCourse(
    semester: Semester,
    name: String,
    teacher: String,
    room: String,
    weekday: Int,
    start: Int,
    end: Int,
    color: Int,
): ScheduledCourse {
    val id = "preview-$weekday-$start"
    return ScheduledCourse(
        semester = semester,
        course = Course(id, name, teacher, color),
        meeting = Meeting("meeting-$id", id, weekday, start, end, room, (1..16).toSet()),
    )
}
