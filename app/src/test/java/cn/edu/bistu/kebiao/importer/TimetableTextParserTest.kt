package cn.edu.bistu.kebiao.importer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class TimetableTextParserTest {
    private val parser = TimetableTextParser()

    @Test
    fun parsesCardShapedCourse() {
        val page = ExtractedPage(
            title = "我的课表",
            url = "https://jwxt.bistu.edu.cn/jwapp/#/schedule",
            bodyText = "2026-2027学年 第一学期 第3周",
            cards = listOf(
                """
                课程名称：高等数学
                教师：张老师
                地点：教二301
                星期一 第1-2节
                1-16周
                """.trimIndent(),
            ),
        )

        val result = parser.parse(page)

        assertEquals(1, result.lessons.size)
        assertEquals("高等数学", result.lessons.single().courseName)
        assertEquals("张老师", result.lessons.single().teacher)
        assertEquals("教二301", result.lessons.single().room)
        assertEquals(1, result.lessons.single().weekday)
        assertEquals(1, result.lessons.single().startPeriod)
        assertEquals(2, result.lessons.single().endPeriod)
        assertEquals((1..16).toSet(), result.lessons.single().weeks)
        assertEquals(3, result.currentWeek)
        assertEquals("2026-2027学年第一学期", result.semesterName)
    }

    @Test
    fun parsesTableUsingHeaderAndRowPeriod() {
        val page = ExtractedPage(
            title = "我的课表",
            url = "https://jwxt.bistu.edu.cn/jwapp/#/wdkb",
            bodyText = "课表",
            tables = listOf(
                ExtractedTable(
                    headers = listOf("节次", "星期一", "星期二"),
                    rows = listOf(
                        listOf(
                            "第3-4节",
                            "大学物理\n地点：实验楼204\n教师：李老师\n2-16周（双）",
                            "",
                        ),
                    ),
                ),
            ),
        )

        val lesson = parser.parse(page).lessons.single()

        assertEquals("大学物理", lesson.courseName)
        assertEquals(1, lesson.weekday)
        assertEquals(3, lesson.startPeriod)
        assertEquals(4, lesson.endPeriod)
        assertEquals(setOf(2, 4, 6, 8, 10, 12, 14, 16), lesson.weeks)
    }

    @Test
    fun reportsLoginPageWithoutInventingCourses() {
        val result = parser.parse(
            ExtractedPage(
                title = "统一认证",
                url = "https://wxjw.bistu.edu.cn/authserver/login",
                bodyText = "账号登录 学号 密码 登录",
            ),
        )

        assertTrue(result.lessons.isEmpty())
        assertTrue(result.warnings.single().contains("登录页"))
    }

    @Test
    fun prefersStructuredCourseNameOverStudentGroupLabel() {
        val result = parser.parse(
            ExtractedPage(
                title = "我的课表",
                url = "https://jwxt.bistu.edu.cn/jwapp/#/wdkb",
                bodyText = "2026-2027学年 第一学期",
                lessons = listOf(
                    ExtractedLesson(
                        courseName = "数据结构",
                        teacher = "王老师",
                        room = "教一205",
                        weekday = 3,
                        startPeriod = 5,
                        endPeriod = 6,
                        weekText = "1-16周",
                        rawText = "学生组：计科24级\n课程信息\n数据结构",
                    ),
                ),
            ),
        )

        assertEquals(1, result.lessons.size)
        assertEquals("数据结构", result.lessons.single().courseName)
        assertEquals(3, result.lessons.single().weekday)
        assertEquals(5, result.lessons.single().startPeriod)
        assertEquals((1..16).toSet(), result.lessons.single().weeks)
    }

    @Test
    fun doesNotUseStudentGroupAsCardCourseName() {
        val result = parser.parse(
            ExtractedPage(
                title = "我的课表",
                url = "https://jwxt.bistu.edu.cn/jwapp/#/wdkb",
                bodyText = "课表",
                cards = listOf(
                    """
                    学生组
                    操作系统
                    教师：赵老师
                    地点：教三402
                    星期四 第7-8节
                    3周
                    """.trimIndent(),
                ),
            ),
        )

        assertEquals("操作系统", result.lessons.single().courseName)
        assertEquals(setOf(3), result.lessons.single().weeks)
    }

    @Test
    fun parsesUnlabelledBistuRoomCode() {
        val result = parser.parse(
            ExtractedPage(
                title = "我的课表",
                url = "https://jwxt.bistu.edu.cn/jwapp/#/wdkb",
                bodyText = "2026-2027学年 第一学期",
                lessons = listOf(
                    ExtractedLesson(
                        courseName = "软件工程",
                        weekday = 2,
                        startPeriod = 3,
                        endPeriod = 4,
                        weekText = "1-16周",
                        rawText = "上课信息\nXXD-407\n教师：周老师",
                    ),
                ),
            ),
        )

        assertEquals("XXD-407", result.lessons.single().room)
    }
}
