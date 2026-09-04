package cn.edu.bistu.kebiao.importer

import cn.edu.bistu.kebiao.domain.WeekPattern

class TimetableTextParser {
    private val semesterRegex = Regex("(20\\d{2})\\s*[-—]\\s*(20\\d{2})\\s*学年\\s*第?([一二12])\\s*学期")
    private val currentWeekRegex = Regex("第\\s*(\\d{1,2})\\s*周")
    private val weekdayRegex = Regex(
        "(?:星期|周|xq|weekday|day)\\s*[:=_-]?\\s*([一二三四五六日天1-7])",
        RegexOption.IGNORE_CASE,
    )
    private val periodRegex = Regex("第?\\s*(\\d{1,2})\\s*[-~—至]\\s*(\\d{1,2})\\s*节")
    private val singlePeriodRegex = Regex("第\\s*(\\d{1,2})\\s*节")
    private val weeksRegex = Regex(
        "(?:第?\\s*)?(?:\\d{1,2}\\s*[-~—至]\\s*\\d{1,2}|\\d{1,2}(?:\\s*[,，、]\\s*\\d{1,2})+|\\d{1,2})\\s*周(?:\\s*[（(][单双][）)])?",
    )
    private val teacherRegex = Regex("(?:教师|老师|任课教师)\\s*[:：]?\\s*([^\\n，,；;]+)")
    private val roomRegex = Regex("(?:地点|教室|上课地点)\\s*[:：]?\\s*([^\\n，,；;]+)")
    private val roomCodeRegex = Regex(
        "(?<![A-Za-z0-9])([A-Za-z]{2,8}\\s*[-－—]\\s*[A-Za-z]?\\d{2,4})(?![A-Za-z0-9])",
        RegexOption.IGNORE_CASE,
    )
    private val courseNameRegex = Regex("(?:课程名称|课程)\\s*[:：]\\s*([^\\n]+)")

    fun parse(page: ExtractedPage): ParseOutcome {
        val warnings = page.extractionWarnings.toMutableList()
        val candidates = mutableListOf<ParsedLesson>()
        val structuredCountBefore = candidates.size
        page.lessons.forEach { lesson -> parseStructuredLesson(lesson)?.let(candidates::add) }
        if (page.lessons.isNotEmpty() && candidates.size - structuredCountBefore < page.lessons.size) {
            warnings += "部分课程缺少有效的星期、节次或周次，已跳过。"
        }
        page.tables.forEach { candidates += parseTable(it) }
        page.cards.forEach { text -> parseBlock(text)?.let(candidates::add) }
        if (candidates.isEmpty()) {
            page.bodyText.split(Regex("\\n\\s*\\n"))
                .filter { it.contains("周") && (it.contains("节") || weekdayRegex.containsMatchIn(it)) }
                .forEach { text -> parseBlock(text)?.let(candidates::add) }
        }

        val deduplicated = candidates
            .filter { it.courseName.isNotBlank() && it.weeks.isNotEmpty() }
            .distinctBy {
                listOf(
                    it.courseName,
                    it.teacher,
                    it.room,
                    it.weekday,
                    it.startPeriod,
                    it.endPeriod,
                    it.weeks.sorted(),
                )
            }
        if (deduplicated.isEmpty()) {
            warnings += if (page.bodyText.contains("密码") || page.url.contains("authserver")) {
                "当前仍是登录页。请完成登录，并在教务系统中打开“我的课表”。"
            } else {
                "当前页面没有识别到包含星期、节次和周次的课程，请先打开“我的课表”。"
            }
        }
        if (deduplicated.size < candidates.size) warnings += "已自动合并重复课程记录。"
        return ParseOutcome(
            lessons = deduplicated,
            semesterName = semesterRegex.find(page.bodyText)?.let { match ->
                val term = if (match.groupValues[3] in listOf("一", "1")) "第一学期" else "第二学期"
                "${match.groupValues[1]}-${match.groupValues[2]}学年$term"
            },
            currentWeek = currentWeekRegex.find(page.bodyText)?.groupValues?.get(1)?.toIntOrNull(),
            warnings = warnings,
        )
    }

    private fun parseStructuredLesson(lesson: ExtractedLesson): ParsedLesson? {
        val weekday = lesson.weekday?.takeIf { it in 1..7 } ?: return null
        val startPeriod = lesson.startPeriod?.takeIf { it in 1..20 } ?: return null
        val endPeriod = lesson.endPeriod?.takeIf { it in startPeriod..20 } ?: return null
        val weekText = lesson.weekText.ifBlank {
            weeksRegex.find(lesson.rawText)?.value.orEmpty()
        }
        val weeks = WeekPattern.parse(weekText)
        if (weeks.isEmpty()) return null

        val name = lesson.courseName.trim().takeUnless(::isCourseLabel)
            ?: parseBlock(
                raw = listOf(lesson.rawText, weekText).filter(String::isNotBlank).joinToString("\n"),
                knownWeekday = weekday,
                knownPeriod = startPeriod to endPeriod,
            )?.courseName
            ?: return null
        val teacher = lesson.teacher.trim().ifBlank {
            teacherRegex.find(lesson.rawText)?.groupValues?.get(1)?.trim().orEmpty()
        }
        val room = lesson.room.trim().ifBlank { extractRoom(lesson.rawText) }
        return ParsedLesson(
            courseName = name.take(80),
            teacher = teacher.take(40),
            room = room.take(60),
            weekday = weekday,
            startPeriod = startPeriod,
            endPeriod = endPeriod,
            weeks = weeks,
        )
    }

    private fun parseTable(table: ExtractedTable): List<ParsedLesson> {
        val headerRow = when {
            table.headers.any(::looksLikeWeekday) -> table.headers
            table.rows.firstOrNull()?.any(::looksLikeWeekday) == true -> table.rows.first()
            else -> emptyList()
        }
        if (headerRow.isEmpty()) return emptyList()
        val dayByColumn = headerRow.mapIndexedNotNull { index, text ->
            parseWeekday(text)?.let { index to it }
        }.toMap()
        val dataRows = if (table.rows.firstOrNull() == headerRow) table.rows.drop(1) else table.rows
        return buildList {
            dataRows.forEachIndexed { rowIndex, row ->
                val rowPeriod = row.firstOrNull()?.let(::parsePeriod)
                    ?: ((rowIndex + 1) to (rowIndex + 1))
                dayByColumn.forEach { (columnIndex, weekday) ->
                    val text = row.getOrNull(columnIndex).orEmpty()
                    splitCell(text).forEach { block ->
                        parseBlock(block, weekday, rowPeriod)?.let(::add)
                    }
                }
            }
        }
    }

    private fun parseBlock(
        raw: String,
        knownWeekday: Int? = null,
        knownPeriod: Pair<Int, Int>? = null,
    ): ParsedLesson? {
        val text = raw.replace("\\r", "").trim()
        if (text.length < 3) return null
        val weekday = knownWeekday ?: parseWeekday(text) ?: return null
        val period = parsePeriod(text) ?: knownPeriod ?: return null
        val weekText = weeksRegex.find(text)?.value ?: return null
        val weeks = WeekPattern.parse(weekText)
        if (weeks.isEmpty()) return null
        val lines = text.lines().map(String::trim).filter(String::isNotBlank)
        val teacher = teacherRegex.find(text)?.groupValues?.get(1)?.trim().orEmpty()
        val room = extractRoom(text)
        val explicitName = courseNameRegex.find(text)?.groupValues?.get(1)?.trim()
        val name = explicitName ?: lines.firstOrNull { line ->
            !isMetadataLine(line) && line != room && line != teacher
        }.orEmpty()
        if (name.isBlank()) return null
        return ParsedLesson(
            courseName = name.take(80),
            teacher = teacher.take(40),
            room = room.take(60),
            weekday = weekday,
            startPeriod = period.first,
            endPeriod = period.second,
            weeks = weeks,
        )
    }

    private fun splitCell(text: String): List<String> = text
        .split(Regex("\\n{2,}|(?=课程(?:名称)?\\s*[:：])"))
        .map(String::trim)
        .filter(String::isNotBlank)
        .ifEmpty { listOf(text) }

    private fun looksLikeWeekday(text: String): Boolean = parseWeekday(text) != null

    private fun parseWeekday(text: String): Int? {
        val token = weekdayRegex.find(text)?.groupValues?.get(1)
            ?: text.trim().takeIf { it in listOf("一", "二", "三", "四", "五", "六", "日", "天") }
            ?: return null
        return when (token) {
            "一", "1" -> 1
            "二", "2" -> 2
            "三", "3" -> 3
            "四", "4" -> 4
            "五", "5" -> 5
            "六", "6" -> 6
            "日", "天", "7" -> 7
            else -> null
        }
    }

    private fun parsePeriod(text: String): Pair<Int, Int>? {
        periodRegex.find(text)?.let { match ->
            return match.groupValues[1].toInt() to match.groupValues[2].toInt()
        }
        singlePeriodRegex.find(text)?.let { match ->
            val value = match.groupValues[1].toInt()
            return value to value
        }
        return null
    }

    private fun extractRoom(text: String): String =
        roomRegex.find(text)?.groupValues?.get(1)?.trim()
            ?: roomCodeRegex.find(text)?.groupValues?.get(1)?.replace(Regex("\\s+"), "")
            ?: text.lines().firstOrNull(::looksLikeRoom)?.trim().orEmpty()

    private fun looksLikeRoom(line: String): Boolean =
        line.contains("教室") || line.contains("校区") || roomCodeRegex.containsMatchIn(line) ||
            Regex("(?:教|实|文|理|机)[A-Za-z]?\\d{2,4}").containsMatchIn(line)

    private fun isMetadataLine(line: String): Boolean =
        weekdayRegex.containsMatchIn(line) || periodRegex.containsMatchIn(line) ||
            weeksRegex.containsMatchIn(line) || line.startsWith("教师") ||
            line.startsWith("老师") || line.startsWith("地点") ||
            line.startsWith("教室") || isCourseLabel(line) || line.length <= 1

    private fun isCourseLabel(line: String): Boolean {
        val normalized = line.trim().substringBefore('：').substringBefore(':').trim()
        return normalized in setOf(
            "学生组",
            "课程信息",
            "上课信息",
            "教学班",
            "班级",
            "课程",
            "课程名称",
        )
    }
}
