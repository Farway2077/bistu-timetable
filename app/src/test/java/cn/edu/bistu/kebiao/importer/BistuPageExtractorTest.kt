package cn.edu.bistu.kebiao.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BistuPageExtractorTest {
    @Test
    fun decodesStructuredScheduleReturnedByJavascript() {
        val raw = "\"{\\\"title\\\":\\\"我的课表\\\",\\\"url\\\":\\\"https://jwxt.bistu.edu.cn/jwapp/sys/homeapp/home/index.html\\\",\\\"bodyText\\\":\\\"2026-2027学年第一学期\\\",\\\"tables\\\":[],\\\"cards\\\":[],\\\"lessons\\\":[{\\\"courseName\\\":\\\"数据结构\\\",\\\"teacher\\\":\\\"王老师\\\",\\\"room\\\":\\\"教一205\\\",\\\"weekday\\\":3,\\\"startPeriod\\\":5,\\\"endPeriod\\\":6,\\\"weekText\\\":\\\"1-16周\\\",\\\"rawText\\\":\\\"学生组：计科24级\\\"}],\\\"extractionWarnings\\\":[]}\""

        val page = BistuPageExtractor.decodeJavascriptResult(raw)

        assertEquals("我的课表", page.title)
        assertEquals(1, page.lessons.size)
        assertEquals("数据结构", page.lessons.single().courseName)
        assertEquals(3, page.lessons.single().weekday)
        assertEquals(5, page.lessons.single().startPeriod)
    }

    @Test
    fun scriptUsesTermApiAndSevenColumnGridFallback() {
        assertTrue(BistuPageExtractor.script.contains("student/getMyScheduleDetail.do"))
        assertTrue(BistuPageExtractor.script.contains("form.set('type', 'term')"))
        assertTrue(BistuPageExtractor.script.contains("kbappTimetableDayColumnRoot"))
        assertTrue(BistuPageExtractor.script.contains("sectionBox"))
    }

    @Test
    fun scriptUsesTheSemesterSelectedOnTheCurrentPage() {
        assertTrue(BistuPageExtractor.script.contains("selectedOptions"))
        assertTrue(BistuPageExtractor.script.contains("aria-selected=\"true\""))
        assertTrue(BistuPageExtractor.script.contains("resolveSelectedTerm"))
    }
}
