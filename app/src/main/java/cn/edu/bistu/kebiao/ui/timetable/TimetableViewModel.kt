package cn.edu.bistu.kebiao.ui.timetable

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cn.edu.bistu.kebiao.data.ScheduleRepository
import cn.edu.bistu.kebiao.domain.ScheduledCourse
import cn.edu.bistu.kebiao.domain.Semester
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlin.math.max
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TimetableUiState(
    val isLoading: Boolean = true,
    val semester: Semester? = null,
    val selectedWeek: Int = 1,
    val courses: List<ScheduledCourse> = emptyList(),
    val settingsError: String? = null,
) {
    val visibleCourses: List<ScheduledCourse>
        get() = courses.filter { it.meeting.occursIn(selectedWeek) }
}

class TimetableViewModel(
    private val repository: ScheduleRepository,
) : ViewModel() {
    private val selectedWeek = MutableStateFlow<Int?>(null)
    private val settingsError = MutableStateFlow<String?>(null)

    val uiState: StateFlow<TimetableUiState> = combine(
        repository.activeSemester,
        repository.scheduledCourses,
        selectedWeek,
        settingsError,
    ) { semester, courses, requestedWeek, error ->
        val thisWeek = semester?.let(::weekForToday) ?: 1
        val week = (requestedWeek ?: thisWeek).coerceIn(1, semester?.totalWeeks ?: 20)
        TimetableUiState(
            isLoading = false,
            semester = semester,
            selectedWeek = week,
            courses = courses,
            settingsError = error,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = TimetableUiState(),
    )

    fun selectWeek(week: Int) {
        val total = uiState.value.semester?.totalWeeks ?: 20
        selectedWeek.value = week.coerceIn(1, total.coerceAtLeast(1))
    }

    fun previousWeek() {
        selectedWeek.value = max(1, uiState.value.selectedWeek - 1)
    }

    fun nextWeek() {
        val total = uiState.value.semester?.totalWeeks ?: 20
        selectedWeek.value = (uiState.value.selectedWeek + 1).coerceAtMost(total)
    }

    fun goToThisWeek() {
        selectedWeek.value = uiState.value.semester?.let(::weekForToday) ?: 1
    }

    fun updateSemesterStartDate(startDate: LocalDate) {
        val semesterId = uiState.value.semester?.id ?: return
        if (!isValidFirstWeekStart(startDate)) {
            settingsError.value = "第一周第一天必须选择星期一"
            return
        }
        viewModelScope.launch {
            runCatching { repository.updateSemesterStartDate(semesterId, startDate) }
                .onSuccess {
                    selectedWeek.value = null
                    settingsError.value = null
                }
                .onFailure { error ->
                    settingsError.value = error.message ?: "保存首周日期失败"
                }
        }
    }

    fun dismissSettingsError() {
        settingsError.value = null
    }

    private fun weekForToday(semester: Semester): Int {
        val days = ChronoUnit.DAYS.between(semester.startDate, LocalDate.now())
        return (days / 7 + 1).toInt().coerceIn(1, semester.totalWeeks)
    }

    companion object {
        fun factory(repository: ScheduleRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    TimetableViewModel(repository) as T
            }
    }
}
