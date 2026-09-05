package cn.edu.bistu.kebiao.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(tableName = "semesters")
data class SemesterEntity(
    @PrimaryKey val id: String,
    val name: String,
    @ColumnInfo(name = "start_date") val startDate: String,
    @ColumnInfo(name = "total_weeks") val totalWeeks: Int,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "courses",
    foreignKeys = [
        ForeignKey(
            entity = SemesterEntity::class,
            parentColumns = ["id"],
            childColumns = ["semester_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("semester_id")],
)
data class CourseEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "semester_id") val semesterId: String,
    val name: String,
    val teacher: String,
    @ColumnInfo(name = "color_index") val colorIndex: Int,
    @ColumnInfo(defaultValue = "'IMPORTED'") val source: String = "IMPORTED",
)

@Entity(
    tableName = "meetings",
    foreignKeys = [
        ForeignKey(
            entity = CourseEntity::class,
            parentColumns = ["id"],
            childColumns = ["course_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [Index("course_id")],
)
data class MeetingEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "course_id") val courseId: String,
    val weekday: Int,
    @ColumnInfo(name = "start_period") val startPeriod: Int,
    @ColumnInfo(name = "end_period") val endPeriod: Int,
    val room: String,
    @ColumnInfo(name = "weeks_csv") val weeksCsv: String,
    @ColumnInfo(name = "source_key", defaultValue = "''") val sourceKey: String = "",
)

data class MeetingRow(
    @ColumnInfo(name = "semester_id") val semesterId: String,
    @ColumnInfo(name = "semester_name") val semesterName: String,
    @ColumnInfo(name = "start_date") val startDate: String,
    @ColumnInfo(name = "total_weeks") val totalWeeks: Int,
    @ColumnInfo(name = "course_id") val courseId: String,
    @ColumnInfo(name = "course_name") val courseName: String,
    val teacher: String,
    @ColumnInfo(name = "color_index") val colorIndex: Int,
    @ColumnInfo(name = "course_source") val courseSource: String,
    @ColumnInfo(name = "meeting_id") val meetingId: String,
    val weekday: Int,
    @ColumnInfo(name = "start_period") val startPeriod: Int,
    @ColumnInfo(name = "end_period") val endPeriod: Int,
    val room: String,
    @ColumnInfo(name = "weeks_csv") val weeksCsv: String,
    @ColumnInfo(name = "source_key") val sourceKey: String,
)

