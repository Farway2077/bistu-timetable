package cn.edu.bistu.kebiao.importer

data class ExtractedPage(
    val title: String,
    val url: String,
    val bodyText: String,
    val tables: List<ExtractedTable> = emptyList(),
    val cards: List<String> = emptyList(),
    val lessons: List<ExtractedLesson> = emptyList(),
    val extractionWarnings: List<String> = emptyList(),
)

data class ExtractedLesson(
    val courseName: String,
    val teacher: String = "",
    val room: String = "",
    val weekday: Int? = null,
    val startPeriod: Int? = null,
    val endPeriod: Int? = null,
    val weekText: String = "",
    val rawText: String = "",
)

data class ExtractedTable(
    val headers: List<String>,
    val rows: List<List<String>>,
)

data class ParseOutcome(
    val lessons: List<ParsedLesson>,
    val semesterName: String?,
    val currentWeek: Int?,
    val warnings: List<String>,
)

data class ParsedLesson(
    val courseName: String,
    val teacher: String,
    val room: String,
    val weekday: Int,
    val startPeriod: Int,
    val endPeriod: Int,
    val weeks: Set<Int>,
)
