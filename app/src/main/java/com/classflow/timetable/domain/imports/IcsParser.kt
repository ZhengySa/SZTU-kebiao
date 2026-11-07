package com.classflow.timetable.domain.imports

import com.classflow.timetable.core.time.WeekMath
import com.classflow.timetable.domain.model.Semester
import com.classflow.timetable.domain.model.TimeSlot
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.OffsetDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter

/**
 * ICS（iCalendar / RFC 5545）解析器。
 *
 * 支持范围（够用且不出错的最小集合）：
 * - VEVENT + SUMMARY / LOCATION / DESCRIPTION / DTSTART / DTEND
 * - RRULE 的 FREQ=WEEKLY + INTERVAL / COUNT / UNTIL / BYDAY
 * - 时区：`...Z`（UTC）与本地时间两种写法
 *
 * 明确不支持（会跳过并给出警告，而不是瞎猜）：
 * - 全天事件（VALUE=DATE，没有具体时间，无法映射到节次）
 * - EXDATE 例外日、RDATE 附加日
 * - FREQ=DAILY / MONTHLY 等非周重复
 */
object IcsParser {

    fun parse(text: String, semester: Semester, slots: List<TimeSlot>): ParseResult {
        val warnings = mutableListOf<String>()
        var skipped = 0

        val events = splitEvents(unfold(text))
        if (events.isEmpty()) {
            return ParseResult(
                courses = emptyList(),
                warnings = listOf("文件里没有找到任何日程（VEVENT），请确认选的是课表 ICS 文件"),
                skippedItems = 0,
            )
        }

        val byName = linkedMapOf<String, MutableList<ImportedSession>>()
        val meta = mutableMapOf<String, Pair<String?, String?>>() // name -> (teacher, location)

        events.forEachIndexed { index, block ->
            val props = parseProperties(block)
            val summary = props["SUMMARY"]?.value?.trim().orEmpty()
            if (summary.isBlank()) {
                skipped++
                return@forEachIndexed
            }

            val dtStartRaw = props["DTSTART"]?.value
            val start = dtStartRaw?.let { parseDateTime(it) }
            if (start == null) {
                skipped++
                warnings += "「$summary」没有可用的开始时间（可能是全天事件），已跳过"
                return@forEachIndexed
            }

            val end = props["DTEND"]?.value?.let { parseDateTime(it) }
                ?: start.plusMinutes(DEFAULT_LESSON_MINUTES)

            val slotRange = ImportSupport.mapToSlots(start.toLocalTime(), end.toLocalTime(), slots)
            if (slotRange == null) {
                skipped++
                warnings += "「$summary」的上课时间无法对应到任何节次，已跳过"
                return@forEachIndexed
            }

            val rule = props["RRULE"]?.value?.let { parseRecurrence(it) }
            val weeks = resolveWeeks(rule, start.toLocalDate(), semester)

            if (weeks.isEmpty()) {
                skipped++
                warnings += "「$summary」的周次不在本学期范围内，已跳过"
                return@forEachIndexed
            }

            val days = rule?.byDay?.takeIf { it.isNotEmpty() } ?: listOf(start.dayOfWeek.value)
            val description = props["DESCRIPTION"]?.value
            val teacher = extractTeacher(description)
            val location = props["LOCATION"]?.value?.trim()?.takeIf { it.isNotBlank() }

            val sessions = byName.getOrPut(summary) { mutableListOf() }
            days.forEach { day ->
                sessions += ImportedSession(
                    dayOfWeek = day,
                    startSlot = slotRange.first,
                    endSlot = slotRange.second,
                    weeks = weeks,
                )
            }
            meta[summary] = teacher to location
            if (index == 0 && description != null && description.length > 60) {
                warnings += "描述较长，仅用于提取教师名，未写入备注"
            }
        }

        val courses = byName.map { (name, sessions) ->
            val (teacher, location) = meta[name] ?: (null to null)
            ImportedCourse(
                name = name,
                teacher = teacher,
                location = location,
                sessions = sessions.distinctBy { listOf(it.dayOfWeek, it.startSlot, it.endSlot, it.weeks) },
            )
        }

        return ParseResult(courses = courses, warnings = warnings, skippedItems = skipped)
    }

    // ---------- 文本拆分 ----------

    /** ICS 规定超过 75 字节的行要折行，折行以空格或制表符开头。 */
    private fun unfold(raw: String): List<String> {
        val normalized = raw.replace("\r\n", "\n").replace('\r', '\n')
        val result = mutableListOf<String>()
        var current: StringBuilder? = null

        normalized.split('\n').forEach { line ->
            if (line.startsWith(" ") || line.startsWith("\t")) {
                current?.append(line.substring(1))
            } else {
                current?.let { result += it.toString() }
                current = StringBuilder(line)
            }
        }
        current?.let { result += it.toString() }
        return result
    }

    private fun splitEvents(lines: List<String>): List<List<String>> {
        val events = mutableListOf<List<String>>()
        var buffer: MutableList<String>? = null
        lines.forEach { line ->
            when {
                line.trim().equals("BEGIN:VEVENT", ignoreCase = true) -> buffer = mutableListOf()
                line.trim().equals("END:VEVENT", ignoreCase = true) -> {
                    buffer?.let { events += it }
                    buffer = null
                }
                buffer != null -> buffer!!.add(line)
            }
        }
        return events
    }

    private data class Property(val value: String, val params: Map<String, String>)

    /** `DTSTART;TZID=Asia/Shanghai:20230904T080000` → DTSTART -> (20230904T080000, {TZID=...}) */
    private fun parseProperties(block: List<String>): Map<String, Property> {
        val map = mutableMapOf<String, Property>()
        block.forEach { line ->
            val colon = line.indexOf(':')
            if (colon <= 0) return@forEach
            val head = line.substring(0, colon)
            val value = line.substring(colon + 1)
            val parts = head.split(';')
            val name = parts.first().uppercase()
            val params = parts.drop(1).mapNotNull { param ->
                val eq = param.indexOf('=')
                if (eq <= 0) null else param.substring(0, eq).uppercase() to param.substring(eq + 1)
            }.toMap()
            // 同一个属性只取第一条，够用（我们不处理重复 DTSTART）
            map.putIfAbsent(name, Property(value, params))
        }
        return map
    }

    // ---------- 时间 ----------

    private const val DEFAULT_LESSON_MINUTES = 95L

    private val compactSeconds: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")
    private val compactMinutes: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmm")
    private val utcFormatter: DateTimeFormatter = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmssX")

    private fun parseDateTime(raw: String): LocalDateTime? {
        val value = raw.trim()
        if (value.length == 8) return null // 全天事件：只有日期没有时间
        return runCatching {
            when {
                value.endsWith("Z") -> OffsetDateTime.parse(value, utcFormatter)
                    .atZoneSameInstant(ZoneId.systemDefault())
                    .toLocalDateTime()

                value.length >= 15 -> LocalDateTime.parse(value.take(15), compactSeconds)
                value.length == 13 -> LocalDateTime.parse(value, compactMinutes)
                else -> null
            }
        }.getOrNull()
    }

    // ---------- 重复规则 ----------

    private data class Recurrence(
        val freq: String,
        val interval: Int,
        val count: Int?,
        val until: LocalDate?,
        val byDay: List<Int>,
    )

    private fun parseRecurrence(raw: String): Recurrence? {
        if (raw.isBlank()) return null
        val map = raw.split(';')
            .mapNotNull { part ->
                val eq = part.indexOf('=')
                if (eq <= 0) null else part.substring(0, eq).uppercase() to part.substring(eq + 1)
            }
            .toMap()

        val freq = map["FREQ"]?.uppercase() ?: return null
        val interval = map["INTERVAL"]?.toIntOrNull()?.coerceAtLeast(1) ?: 1
        val count = map["COUNT"]?.toIntOrNull()
        val until = map["UNTIL"]?.let { parseDateTime(it)?.toLocalDate() }
        val byDay = map["BYDAY"].orEmpty()
            .split(',')
            .mapNotNull { ImportSupport.parseDayOfWeek(it.trim()) }

        return Recurrence(freq, interval, count, until, byDay)
    }

    /** 把重复规则换算成「本学期的第几周上课」。 */
    private fun resolveWeeks(rule: Recurrence?, firstDate: LocalDate, semester: Semester): Set<Int> {
        val totalWeeks = semester.totalWeeks
        val firstWeek = WeekMath.weekOf(semester.startDate, firstDate)

        if (rule == null || rule.freq != "WEEKLY") {
            return if (firstWeek in 1..totalWeeks) setOf(firstWeek) else emptySet()
        }

        val lastWeek = when {
            rule.count != null -> firstWeek + (rule.count - 1) * rule.interval
            rule.until != null -> WeekMath.weekOf(semester.startDate, rule.until)
            else -> totalWeeks
        }

        return ImportSupport.weeksInRange(
            from = maxOf(firstWeek, 1),
            to = minOf(lastWeek, totalWeeks),
            totalWeeks = totalWeeks,
            step = rule.interval,
        )
    }

    private fun extractTeacher(description: String?): String? {
        val text = description?.trim().orEmpty()
        if (text.isEmpty()) return null
        val match = Regex("(?:教师|老师|授课教师)\\s*[:：]\\s*([^\\n;；,，]{1,20})").find(text)
        if (match != null) return match.groupValues[1].trim()
        return text.takeIf { it.length <= 12 && !it.contains('\n') }
    }
}
