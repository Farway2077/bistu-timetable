package cn.edu.bistu.kebiao.ui.timetable

import android.content.Context

enum class CourseCardTextSize(
    val scale: Float,
    val label: String,
) {
    SMALL(0.85f, "小"),
    STANDARD(1f, "标准"),
    LARGE(1.15f, "大"),
    EXTRA_LARGE(1.3f, "特大"),
    ;

    companion object {
        fun fromStorage(value: String?): CourseCardTextSize =
            entries.firstOrNull { it.name == value } ?: STANDARD
    }
}

class TimetableDisplayPreferences(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences(
        PREFERENCES_NAME,
        Context.MODE_PRIVATE,
    )

    fun getCourseCardTextSize(): CourseCardTextSize =
        CourseCardTextSize.fromStorage(preferences.getString(COURSE_CARD_TEXT_SIZE_KEY, null))

    fun setCourseCardTextSize(value: CourseCardTextSize) {
        preferences.edit().putString(COURSE_CARD_TEXT_SIZE_KEY, value.name).apply()
    }

    private companion object {
        const val PREFERENCES_NAME = "timetable_display"
        const val COURSE_CARD_TEXT_SIZE_KEY = "course_card_text_size"
    }
}
