package cn.edu.bistu.kebiao.data.local

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        SemesterEntity::class,
        CourseEntity::class,
        MeetingEntity::class,
        ScheduleOverrideEntity::class,
        ScheduleExceptionEntity::class,
        ImportSnapshotEntity::class,
        StudyTaskEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun scheduleDao(): ScheduleDao
}
