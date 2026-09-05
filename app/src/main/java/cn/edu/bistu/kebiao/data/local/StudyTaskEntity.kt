package cn.edu.bistu.kebiao.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey
import cn.edu.bistu.kebiao.domain.StudyTask
import cn.edu.bistu.kebiao.domain.StudyTaskKind
import java.time.LocalDateTime

// Independent of imported course IDs: syncing or undoing a timetable preserves tasks.
@Entity(tableName = "study_tasks")
data class StudyTaskEntity(
    @PrimaryKey val id: String,
    val title: String,
    val kind: String,
    val courseName: String,
    val dueAt: String,
    val notes: String,
    val completed: Boolean,
) {
    fun toDomain() = StudyTask(
        id, title, StudyTaskKind.valueOf(kind), courseName, LocalDateTime.parse(dueAt), notes, completed,
    )
}

fun StudyTask.toEntity() = StudyTaskEntity(id, title, kind.name, courseName, dueAt.toString(), notes, completed)
