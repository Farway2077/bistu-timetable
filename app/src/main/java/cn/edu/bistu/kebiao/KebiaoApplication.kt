package cn.edu.bistu.kebiao

import android.app.Application
import androidx.room.Room
import cn.edu.bistu.kebiao.data.ScheduleRepository
import cn.edu.bistu.kebiao.data.local.AppDatabase

class KebiaoApplication : Application() {
    val database: AppDatabase by lazy {
        Room.databaseBuilder(
            applicationContext,
            AppDatabase::class.java,
            "kebiao.db",
        ).build()
    }

    val repository: ScheduleRepository by lazy {
        ScheduleRepository(database.scheduleDao())
    }
}

