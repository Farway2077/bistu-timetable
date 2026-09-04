package cn.edu.bistu.kebiao.domain

data class PeriodTime(
    val period: Int,
    val startsAt: String,
    val endsAt: String,
)

val BistuPeriodTimes = listOf(
    PeriodTime(1, "08:00", "08:45"),
    PeriodTime(2, "08:50", "09:35"),
    PeriodTime(3, "09:50", "10:35"),
    PeriodTime(4, "10:40", "11:25"),
    PeriodTime(5, "11:30", "12:15"),
    PeriodTime(6, "13:30", "14:15"),
    PeriodTime(7, "14:20", "15:05"),
    PeriodTime(8, "15:20", "16:05"),
    PeriodTime(9, "16:10", "16:55"),
    PeriodTime(10, "17:00", "17:45"),
    PeriodTime(11, "18:30", "19:15"),
    PeriodTime(12, "19:20", "20:05"),
    PeriodTime(13, "20:10", "20:55"),
    PeriodTime(14, "21:00", "21:45"),
)
