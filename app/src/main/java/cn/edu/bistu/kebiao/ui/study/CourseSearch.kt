package cn.edu.bistu.kebiao.ui.study

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import cn.edu.bistu.kebiao.domain.searchSemesterCourses

@Composable
fun CourseSearch(state: StudyToolsState, onAddTask: (String) -> Unit) {
    var query by rememberSaveable { mutableStateOf("") }
    val results = remember(state.semester, state.courses, state.exceptions, query) {
        state.semester?.let { searchSemesterCourses(it, state.courses, state.exceptions, query) }.orEmpty()
    }
    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("在整个学期里找一门课", style = MaterialTheme.typography.titleMedium)
            Text("按课程名、老师或教室查询，结果已计入停课与临时调课。",
                style = MaterialTheme.typography.bodyMedium)
        }
        item {
            OutlinedTextField(
                value = query, onValueChange = { query = it }, singleLine = true,
                label = { Text("课程名 / 老师 / 教室") },
                trailingIcon = { if (query.isNotEmpty()) TextButton(onClick = { query = "" }) { Text("清除") } },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        item {
            Text(when {
                state.semester == null -> "先导入课表，再搜索课程。"
                query.isBlank() -> "例如：高等数学、老师姓名或 XXD-407。可用空格组合关键词。"
                results.isEmpty() -> "没有匹配的课程，试试更短的关键词。"
                else -> "共找到 ${results.size} 次上课安排"
            })
        }
        items(results, key = { "${it.date}/${it.scheduled.meeting.id}" }) { result ->
            val scheduled = result.scheduled
            Card(Modifier.fillMaxWidth()) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(scheduled.course.name, style = MaterialTheme.typography.titleMedium)
                    Text("${result.date} · 周${listOf("一", "二", "三", "四", "五", "六", "日")[result.date.dayOfWeek.value - 1]}")
                    Text("第 ${scheduled.meeting.startPeriod}–${scheduled.meeting.endPeriod} 节 · ${scheduled.meeting.room.ifBlank { "教室待定" }}")
                    if (scheduled.course.teacher.isNotBlank()) Text(scheduled.course.teacher)
                    if (scheduled.isDateException) Text("临时调课", color = MaterialTheme.colorScheme.primary)
                    TextButton(onClick = { onAddTask(scheduled.course.name) }) { Text("为这门课记待办") }
                }
            }
        }
    }
}
