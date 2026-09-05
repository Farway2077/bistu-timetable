package cn.edu.bistu.kebiao.data.local

import org.json.JSONArray
import org.json.JSONObject

internal data class ImportSnapshotContents(
    val semester: SemesterEntity,
    val courses: List<CourseEntity>,
    val meetings: List<MeetingEntity>,
)

internal object ImportSnapshotCodec {
    private const val VERSION = 1

    fun encode(
        semester: SemesterEntity,
        rows: List<MeetingRow>,
        createdAt: Long,
    ): ImportSnapshotEntity {
        val root = JSONObject()
            .put("version", VERSION)
            .put(
                "semester",
                JSONObject()
                    .put("id", semester.id)
                    .put("name", semester.name)
                    .put("startDate", semester.startDate)
                    .put("totalWeeks", semester.totalWeeks)
                    .put("updatedAt", semester.updatedAt),
            )
        val lessons = JSONArray()
        rows.forEach { row ->
            lessons.put(
                JSONObject()
                    .put("courseId", row.courseId)
                    .put("courseName", row.courseName)
                    .put("teacher", row.teacher)
                    .put("colorIndex", row.colorIndex)
                    .put("courseSource", row.courseSource)
                    .put("meetingId", row.meetingId)
                    .put("weekday", row.weekday)
                    .put("startPeriod", row.startPeriod)
                    .put("endPeriod", row.endPeriod)
                    .put("room", row.room)
                    .put("weeksCsv", row.weeksCsv)
                    .put("sourceKey", row.sourceKey),
            )
        }
        root.put("lessons", lessons)
        return ImportSnapshotEntity(
            semesterId = semester.id,
            payload = root.toString(),
            createdAt = createdAt,
        )
    }

    fun decode(snapshot: ImportSnapshotEntity): ImportSnapshotContents {
        val root = JSONObject(snapshot.payload)
        require(root.optInt("version") == VERSION) { "不支持的课表快照版本" }
        val semesterJson = root.getJSONObject("semester")
        val semester = SemesterEntity(
            id = semesterJson.getString("id"),
            name = semesterJson.getString("name"),
            startDate = semesterJson.getString("startDate"),
            totalWeeks = semesterJson.getInt("totalWeeks"),
            updatedAt = semesterJson.getLong("updatedAt"),
        )
        val lessons = root.getJSONArray("lessons")
        val courses = linkedMapOf<String, CourseEntity>()
        val meetings = buildList {
            for (index in 0 until lessons.length()) {
                val item = lessons.getJSONObject(index)
                val courseId = item.getString("courseId")
                courses.putIfAbsent(
                    courseId,
                    CourseEntity(
                        id = courseId,
                        semesterId = semester.id,
                        name = item.getString("courseName"),
                        teacher = item.getString("teacher"),
                        colorIndex = item.getInt("colorIndex"),
                        source = item.optString("courseSource", "IMPORTED"),
                    ),
                )
                add(
                    MeetingEntity(
                        id = item.getString("meetingId"),
                        courseId = courseId,
                        weekday = item.getInt("weekday"),
                        startPeriod = item.getInt("startPeriod"),
                        endPeriod = item.getInt("endPeriod"),
                        room = item.getString("room"),
                        weeksCsv = item.getString("weeksCsv"),
                        sourceKey = item.optString("sourceKey", item.getString("meetingId")),
                    ),
                )
            }
        }
        return ImportSnapshotContents(semester, courses.values.toList(), meetings)
    }
}
