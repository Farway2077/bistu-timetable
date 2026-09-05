package cn.edu.bistu.kebiao.ui.study

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import cn.edu.bistu.kebiao.data.ScheduleRepository
import cn.edu.bistu.kebiao.domain.ScheduleException
import cn.edu.bistu.kebiao.domain.ScheduledCourse
import cn.edu.bistu.kebiao.domain.Semester
import cn.edu.bistu.kebiao.domain.StudyTask
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class StudyToolsState(
    val loading: Boolean = true,
    val semester: Semester? = null,
    val courses: List<ScheduledCourse> = emptyList(),
    val exceptions: List<ScheduleException> = emptyList(),
    val tasks: List<StudyTask> = emptyList(),
)

class StudyToolsViewModel(private val repository: ScheduleRepository) : ViewModel() {
    val state = combine(
        repository.activeSemester,
        repository.scheduledCourses,
        repository.scheduleExceptions,
        repository.studyTasks,
    ) { semester, courses, exceptions, tasks ->
        StudyToolsState(false, semester, courses, exceptions, tasks)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), StudyToolsState())

    val busy = MutableStateFlow(false)
    val error = MutableStateFlow<String?>(null)

    fun save(task: StudyTask, onSaved: () -> Unit) = write {
        repository.saveStudyTask(task)
        onSaved()
    }

    fun complete(task: StudyTask) = write {
        repository.setStudyTaskCompleted(task.id, !task.completed)
    }

    fun delete(id: String, onDeleted: () -> Unit) = write {
        repository.deleteStudyTask(id)
        onDeleted()
    }

    private fun write(action: suspend () -> Unit) {
        if (busy.value) return
        busy.value = true
        error.value = null
        viewModelScope.launch {
            try {
                action()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (failure: Exception) {
                error.value = failure.message ?: "保存失败，请重试"
            } finally {
                busy.value = false
            }
        }
    }

    companion object {
        fun factory(repository: ScheduleRepository): ViewModelProvider.Factory =
            object : ViewModelProvider.Factory {
                @Suppress("UNCHECKED_CAST")
                override fun <T : ViewModel> create(modelClass: Class<T>): T =
                    StudyToolsViewModel(repository) as T
            }
    }
}
