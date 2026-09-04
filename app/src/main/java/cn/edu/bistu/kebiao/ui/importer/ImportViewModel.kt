package cn.edu.bistu.kebiao.ui.importer

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cn.edu.bistu.kebiao.data.ScheduleRepository
import cn.edu.bistu.kebiao.domain.ImportedLesson
import cn.edu.bistu.kebiao.domain.ImportedSchedule
import cn.edu.bistu.kebiao.domain.Semester
import cn.edu.bistu.kebiao.importer.BistuPageExtractor
import cn.edu.bistu.kebiao.importer.TimetableTextParser
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.temporal.TemporalAdjusters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ImportUiState(
    val currentUrl: String = BistuPageExtractor.START_URL,
    val isAnalyzing: Boolean = false,
    val message: String = "请在学校页面完成登录，然后进入“我的课表”。",
    val warnings: List<String> = emptyList(),
    val pendingSchedule: ImportedSchedule? = null,
    val isSaving: Boolean = false,
    val saved: Boolean = false,
)

class ImportViewModel(
    private val repository: ScheduleRepository,
    private val parser: TimetableTextParser = TimetableTextParser(),
) : ViewModel() {
    private val _uiState = MutableStateFlow(ImportUiState())
    val uiState: StateFlow<ImportUiState> = _uiState.asStateFlow()

    fun onUrlChanged(url: String) {
        _uiState.update { it.copy(currentUrl = url) }
    }

    fun beginAnalysis() {
        _uiState.update {
            it.copy(
                isAnalyzing = true,
                message = "正在读取完整学期课表…",
                warnings = emptyList(),
            )
        }
    }

    fun reportExtractionTimeout() {
        _uiState.update {
            it.copy(
                isAnalyzing = false,
                message = "课表读取超时，请确认七列课表已加载完成后重试。",
            )
        }
    }

    fun analyzeJavascriptResult(raw: String?) {
        if (raw.isNullOrBlank() || raw == "null") {
            _uiState.update {
                it.copy(
                    isAnalyzing = false,
                    message = "页面暂时无法读取，请等待课表加载完成后重试。",
                )
            }
            return
        }
        viewModelScope.launch {
            _uiState.update { it.copy(isAnalyzing = true, message = "正在识别课程…") }
            val result = withContext(Dispatchers.Default) {
                runCatching {
                    val page = BistuPageExtractor.decodeJavascriptResult(raw)
                    parser.parse(page)
                }
            }
            result.onSuccess { outcome ->
                if (outcome.lessons.isEmpty()) {
                    _uiState.update {
                        it.copy(
                            isAnalyzing = false,
                            message = outcome.warnings.firstOrNull() ?: "当前页面没有可导入课程。",
                            warnings = outcome.warnings,
                            pendingSchedule = null,
                        )
                    }
                } else {
                    val schedule = outcome.toImportedSchedule()
                    _uiState.update {
                        it.copy(
                            isAnalyzing = false,
                            message = "识别到 ${schedule.lessons.size} 条上课安排",
                            warnings = schedule.warnings,
                            pendingSchedule = schedule,
                        )
                    }
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isAnalyzing = false,
                        message = "识别失败：${error.message ?: "页面结构暂不支持"}",
                    )
                }
            }
        }
    }

    fun dismissPreview() {
        _uiState.update { it.copy(pendingSchedule = null) }
    }

    fun confirmImport() {
        val schedule = _uiState.value.pendingSchedule ?: return
        viewModelScope.launch {
            _uiState.update { it.copy(isSaving = true) }
            runCatching {
                withContext(Dispatchers.IO) { repository.replaceSchedule(schedule) }
            }.onSuccess {
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        saved = true,
                        message = "课表已保存到本机",
                        pendingSchedule = null,
                    )
                }
            }.onFailure { error ->
                _uiState.update {
                    it.copy(
                        isSaving = false,
                        message = "保存失败：${error.message ?: "未知错误"}",
                    )
                }
            }
        }
    }

    fun reportPageError(description: String) {
        _uiState.update { it.copy(message = "页面加载失败：$description") }
    }

    private fun cn.edu.bistu.kebiao.importer.ParseOutcome.toImportedSchedule(): ImportedSchedule {
        val now = LocalDate.now()
        val monday = now.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY))
        val week = (currentWeek ?: 1).coerceAtLeast(1)
        val startDate = monday.minusWeeks((week - 1).toLong())
        val name = semesterName ?: "${now.year}-${now.year + 1}学年课表"
        val maxWeek = lessons.flatMap { it.weeks }.maxOrNull()?.coerceAtLeast(20) ?: 20
        return ImportedSchedule(
            semester = Semester(
                id = name.replace(Regex("[^0-9一二学期-]"), "").ifBlank { "semester-${now.year}" },
                name = name,
                startDate = startDate,
                totalWeeks = maxWeek.coerceAtMost(30),
            ),
            lessons = lessons.map { lesson ->
                ImportedLesson(
                    courseName = lesson.courseName,
                    teacher = lesson.teacher,
                    room = lesson.room,
                    weekday = lesson.weekday,
                    startPeriod = lesson.startPeriod,
                    endPeriod = lesson.endPeriod,
                    weeks = lesson.weeks,
                )
            },
            warnings = warnings,
        )
    }

    companion object {
        fun factory(repository: ScheduleRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ImportViewModel(repository) as T
            }
    }
}
