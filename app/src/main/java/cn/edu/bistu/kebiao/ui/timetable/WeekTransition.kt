package cn.edu.bistu.kebiao.ui.timetable

fun pageIndexForWeek(week: Int, totalWeeks: Int): Int {
    val safeTotalWeeks = totalWeeks.coerceAtLeast(1)
    return week.coerceIn(1, safeTotalWeeks) - 1
}

fun weekForPageIndex(pageIndex: Int, totalWeeks: Int): Int {
    val safeTotalWeeks = totalWeeks.coerceAtLeast(1)
    return (pageIndex + 1).coerceIn(1, safeTotalWeeks)
}
