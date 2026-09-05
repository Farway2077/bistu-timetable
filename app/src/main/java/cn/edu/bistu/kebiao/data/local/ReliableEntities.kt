package cn.edu.bistu.kebiao.data.local

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "schedule_overrides",
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
data class ScheduleOverrideEntity(
    @PrimaryKey @ColumnInfo(name = "source_key") val sourceKey: String,
    @ColumnInfo(name = "semester_id") val semesterId: String,
    val action: String,
    @ColumnInfo(name = "course_name") val courseName: String,
    val teacher: String,
    @ColumnInfo(name = "color_index") val colorIndex: Int,
    val weekday: Int,
    @ColumnInfo(name = "start_period") val startPeriod: Int,
    @ColumnInfo(name = "end_period") val endPeriod: Int,
    val room: String,
    @ColumnInfo(name = "weeks_csv") val weeksCsv: String,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "schedule_exceptions",
    foreignKeys = [
        ForeignKey(
            entity = SemesterEntity::class,
            parentColumns = ["id"],
            childColumns = ["semester_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("semester_id"),
        Index(value = ["source_key", "original_date"], unique = true),
    ],
)
data class ScheduleExceptionEntity(
    @PrimaryKey val id: String,
    @ColumnInfo(name = "semester_id") val semesterId: String,
    @ColumnInfo(name = "source_key") val sourceKey: String,
    @ColumnInfo(name = "original_date") val originalDate: String,
    val kind: String,
    @ColumnInfo(name = "replacement_date") val replacementDate: String?,
    @ColumnInfo(name = "start_period") val startPeriod: Int?,
    @ColumnInfo(name = "end_period") val endPeriod: Int?,
    @ColumnInfo(name = "room_override") val roomOverride: String?,
    @ColumnInfo(name = "updated_at") val updatedAt: Long,
)

@Entity(
    tableName = "import_snapshots",
    foreignKeys = [
        ForeignKey(
            entity = SemesterEntity::class,
            parentColumns = ["id"],
            childColumns = ["semester_id"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
)
data class ImportSnapshotEntity(
    @PrimaryKey @ColumnInfo(name = "semester_id") val semesterId: String,
    val payload: String,
    @ColumnInfo(name = "created_at") val createdAt: Long,
)
