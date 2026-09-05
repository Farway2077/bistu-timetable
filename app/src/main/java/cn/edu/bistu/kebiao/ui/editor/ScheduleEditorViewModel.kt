package cn.edu.bistu.kebiao.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cn.edu.bistu.kebiao.data.ScheduleRepository
import cn.edu.bistu.kebiao.domain.ScheduleException
import cn.edu.bistu.kebiao.domain.ScheduleExceptionKind
import cn.edu.bistu.kebiao.domain.ScheduleLessonDraft
import cn.edu.bistu.kebiao.domain.ScheduleOverride
import cn.edu.bistu.kebiao.domain.ScheduleOverrideAction
import cn.edu.bistu.kebiao.domain.ScheduledCourse
import cn.edu.bistu.kebiao.domain.Semester
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class ScheduleEditorUiState(
    val semester: Semester? = null,
    val courses: List<ScheduledCourse> = emptyList(),
    val exceptions: List<ScheduleException> = emptyList(),
    val hiddenOverrides: List<ScheduleOverride> = emptyList(),
    val isSaving: Boolean = false,
    val message: String? = null,
)

private data class EditorScheduleData(
    val semester: Semester?,
    val courses: List<ScheduledCourse>,
    val exceptions: List<ScheduleException>,
    val hiddenOverrides: List<ScheduleOverride>,
)

class ScheduleEditorViewModel(
    private val repository: ScheduleRepository,
) : ViewModel() {
    private val isSaving = MutableStateFlow(false)
    private val message = MutableStateFlow<String?>(null)
    private val editorScheduleData = combine(
        repository.activeSemester,
        repository.scheduledCourses,
        repository.scheduleExceptions,
        repository.scheduleOverrides,
    ) { semester, courses, exceptions, overrides ->
        EditorScheduleData(
            semester = semester,
            courses = courses,
            exceptions = exceptions,
            hiddenOverrides = overrides.filter { it.action == ScheduleOverrideAction.HIDE },
        )
    }

    val uiState: StateFlow<ScheduleEditorUiState> = combine(
        editorScheduleData,
        isSaving,
        message,
    ) { data, saving, currentMessage ->
        ScheduleEditorUiState(
            semester = data.semester,
            courses = data.courses,
            exceptions = data.exceptions,
            hiddenOverrides = data.hiddenOverrides,
            isSaving = saving,
            message = currentMessage,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = ScheduleEditorUiState(),
    )

    fun saveLesson(
        original: ScheduledCourse?,
        draft: ScheduleLessonDraft,
        onSaved: () -> Unit = {},
    ) {
        val semesterId = uiState.value.semester?.id ?: return
        runEdit(
            success = if (original == null) "手动课程已添加" else "课程修改已保存",
            onSuccess = onSaved,
        ) {
            repository.saveLesson(semesterId, original, draft)
        }
    }

    fun deleteLesson(scheduled: ScheduledCourse, onDeleted: () -> Unit = {}) {
        runEdit(success = "课程已从本机课表移除", onSuccess = onDeleted) {
            repository.deleteLesson(scheduled)
        }
    }

    fun restoreImportedVersion(scheduled: ScheduledCourse) {
        restoreImportedVersion(scheduled.meeting.sourceKey)
    }

    fun restoreImportedVersion(sourceKey: String) {
        runEdit(success = "已恢复教务版本") {
            repository.restoreImportedVersion(sourceKey)
        }
    }

    fun saveException(
        scheduled: ScheduledCourse,
        originalDate: LocalDate,
        kind: ScheduleExceptionKind,
        replacementDate: LocalDate? = null,
        startPeriod: Int? = null,
        endPeriod: Int? = null,
        roomOverride: String? = null,
        onSaved: () -> Unit = {},
    ) {
        runEdit(success = if (kind == ScheduleExceptionKind.CANCEL) "本次停课已记录" else "临时调课已记录", onSuccess = onSaved) {
            repository.saveException(
                scheduled = scheduled,
                originalDate = originalDate,
                kind = kind,
                replacementDate = replacementDate,
                startPeriod = startPeriod,
                endPeriod = endPeriod,
                roomOverride = roomOverride,
            )
        }
    }

    fun deleteException(id: String) {
        runEdit(success = "临时调整已恢复") {
            repository.deleteException(id)
        }
    }

    fun dismissMessage() {
        message.value = null
    }

    private fun runEdit(
        success: String,
        onSuccess: () -> Unit = {},
        block: suspend () -> Unit,
    ) {
        if (isSaving.value) return
        isSaving.value = true
        viewModelScope.launch {
            message.value = null
            try {
                block()
                message.value = success
                onSuccess()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Throwable) {
                message.value = error.message ?: "保存失败"
            } finally {
                isSaving.value = false
            }
        }
    }

    companion object {
        fun factory(repository: ScheduleRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    ScheduleEditorViewModel(repository) as T
            }
    }
}
