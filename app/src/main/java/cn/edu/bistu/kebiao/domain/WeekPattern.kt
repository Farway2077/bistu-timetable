package cn.edu.bistu.kebiao.domain

object WeekPattern {
    private val range = Regex("(\\d{1,2})\\s*[-~—至]\\s*(\\d{1,2})")
    private val number = Regex("\\d{1,2}")

    fun parse(raw: String, maxWeek: Int = 30): Set<Int> {
        if (raw.isBlank()) return emptySet()
        val normalized = raw
            .replace('（', '(')
            .replace('）', ')')
            .replace("周次", "")
            .replace("周", "")
            .replace("第", "")
        val parity = when {
            normalized.contains("单") -> 1
            normalized.contains("双") -> 0
            else -> null
        }
        val cleaned = normalized.replace(Regex("[()单双]"), "")
        val result = linkedSetOf<Int>()
        cleaned.split(Regex("[,，、;；\\s]+"))
            .filter(String::isNotBlank)
            .forEach { part ->
                val match = range.find(part)
                if (match != null) {
                    val start = match.groupValues[1].toInt()
                    val end = match.groupValues[2].toInt()
                    if (start <= end) result += start..end
                } else {
                    number.findAll(part).forEach { result += it.value.toInt() }
                }
            }
        return result
            .asSequence()
            .filter { it in 1..maxWeek }
            .filter { parity == null || it % 2 == parity }
            .toCollection(linkedSetOf())
    }

    fun format(weeks: Set<Int>): String {
        if (weeks.isEmpty()) return "未设置周次"
        val sorted = weeks.sorted()
        val oddOnly = sorted.all { it % 2 == 1 } && sorted.size > 1
        val evenOnly = sorted.all { it % 2 == 0 } && sorted.size > 1
        val parityLabel = when {
            oddOnly -> "（单周）"
            evenOnly -> "（双周）"
            else -> ""
        }
        return if (oddOnly || evenOnly) {
            "${sorted.first()}-${sorted.last()}周$parityLabel"
        } else {
            contiguousRanges(sorted).joinToString("、") { (start, end) ->
                if (start == end) "$start" else "$start-$end"
            } + "周"
        }
    }

    private fun contiguousRanges(values: List<Int>): List<Pair<Int, Int>> {
        if (values.isEmpty()) return emptyList()
        val result = mutableListOf<Pair<Int, Int>>()
        var start = values.first()
        var previous = start
        values.drop(1).forEach { current ->
            if (current != previous + 1) {
                result += start to previous
                start = current
            }
            previous = current
        }
        result += start to previous
        return result
    }
}

