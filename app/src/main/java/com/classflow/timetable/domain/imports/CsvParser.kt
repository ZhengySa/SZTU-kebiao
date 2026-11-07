package com.classflow.timetable.domain.imports

import com.classflow.timetable.domain.model.Semester

/**
 * CSV 模板导入。
 *
 * 表头认中英文别名（不区分大小写、允许有空格），列顺序随意：
 * ```
 * 课程名,教师,地点,星期,开始节,结束节,周次,备注
 * 高等数学,张老师,教三-201,周一,1,2,1-16,
 * 大学英语,李老师,外语楼-305,周三,3,4,1-16双,
 * ```
 *
 * 必填：课程名、星期、开始节、结束节。周次为空视为「每周」。
 * 解析失败的行不会中断整个导入，会记一条警告并跳过。
 */
object CsvParser {

    private val aliases = mapOf(
        "name" to listOf("课程名", "课程", "名称", "课程名称", "course", "name"),
        "teacher" to listOf("教师", "老师", "任课教师", "teacher"),
        "location" to listOf("地点", "教室", "上课地点", "location", "room"),
        "day" to listOf("星期", "周几", "上课星期", "day", "weekday"),
        "startSlot" to listOf("开始节", "起始节", "开始节次", "start", "startsection"),
        "endSlot" to listOf("结束节", "结束节次", "end", "endsection"),
        "weeks" to listOf("周次", "周数", "上课周次", "weeks"),
        "note" to listOf("备注", "note", "remark"),
    )

    fun parse(text: String, semester: Semester): ParseResult {
        val warnings = mutableListOf<String>()
        val rows = text.replace("\r\n", "\n")
            .replace('\r', '\n')
            .split('\n')
            .filter { it.isNotBlank() }

        if (rows.size < 2) {
            return ParseResult(
                courses = emptyList(),
                warnings = listOf("CSV 内容太少，至少需要一行表头和一行课程数据"),
                skippedItems = 0,
            )
        }

        val header = splitCsvLine(rows.first()).map { it.trim().lowercase() }
        val index = resolveColumns(header, warnings)
        if (index["name"] == null || index["day"] == null ||
            index["startSlot"] == null || index["endSlot"] == null
        ) {
            return ParseResult(
                courses = emptyList(),
                warnings = listOf(
                    "表头不完整。需要至少包含：课程名、星期、开始节、结束节。" +
                        "当前表头：${rows.first()}",
                ),
                skippedItems = 0,
            )
        }

        val courses = linkedMapOf<String, MutableList<ImportedSession>>()
        val teachers = mutableMapOf<String, String?>()
        val locations = mutableMapOf<String, String?>()
        val notes = mutableMapOf<String, String?>()
        var skipped = 0

        rows.drop(1).forEachIndexed { offset, line ->
            val cells = splitCsvLine(line)
            fun cell(key: String): String? {
                val i = index[key] ?: return null
                return cells.getOrNull(i)?.trim()?.takeIf { it.isNotEmpty() }
            }

            val name = cell("name")
            if (name == null) {
                skipped++
                warnings += "第 ${offset + 2} 行缺少课程名，已跳过"
                return@forEachIndexed
            }

            val day = ImportSupport.parseDayOfWeek(cell("day").orEmpty())
            val startSlot = cell("startSlot")?.toIntOrNull()
            val endSlot = cell("endSlot")?.toIntOrNull()

            if (day == null || startSlot == null || endSlot == null) {
                skipped++
                warnings += "第 ${offset + 2} 行「$name」的星期/节次无法识别，已跳过"
                return@forEachIndexed
            }
            if (startSlot < 1 || endSlot < startSlot) {
                skipped++
                warnings += "第 ${offset + 2} 行「$name」的节次范围不合法（$startSlot-$endSlot），已跳过"
                return@forEachIndexed
            }

            val weeks = ImportSupport.parseWeekExpression(
                cell("weeks").orEmpty(),
                semester.totalWeeks,
            )

            courses.getOrPut(name) { mutableListOf() } += ImportedSession(
                dayOfWeek = day,
                startSlot = startSlot,
                endSlot = endSlot,
                weeks = weeks,
            )
            teachers.putIfAbsent(name, cell("teacher"))
            locations.putIfAbsent(name, cell("location"))
            notes.putIfAbsent(name, cell("note"))
        }

        val result = courses.map { (name, sessions) ->
            ImportedCourse(
                name = name,
                teacher = teachers[name],
                location = locations[name],
                note = notes[name],
                sessions = sessions.distinctBy { listOf(it.dayOfWeek, it.startSlot, it.endSlot, it.weeks) },
            )
        }

        return ParseResult(courses = result, warnings = warnings, skippedItems = skipped)
    }

    private fun resolveColumns(header: List<String>, warnings: MutableList<String>): Map<String, Int> {
        val result = mutableMapOf<String, Int>()
        aliases.forEach { (key, names) ->
            val found = header.indexOfFirst { raw ->
                names.any { it.equals(raw, ignoreCase = true) || it.equals(raw.replace(" ", ""), ignoreCase = true) }
            }
            if (found >= 0) result[key] = found
        }
        if (result["weeks"] == null) {
            warnings += "没有找到「周次」列，按每周上课处理"
        }
        return result
    }

    /** 支持双引号包裹、字段内逗号与转义双引号的最小 CSV 解析。 */
    private fun splitCsvLine(line: String): List<String> {
        val cells = mutableListOf<String>()
        val builder = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val ch = line[i]
            when {
                ch == '"' -> {
                    if (inQuotes && i + 1 < line.length && line[i + 1] == '"') {
                        builder.append('"')
                        i++
                    } else {
                        inQuotes = !inQuotes
                    }
                }
                ch == ',' && !inQuotes -> {
                    cells += builder.toString()
                    builder.clear()
                }
                else -> builder.append(ch)
            }
            i++
        }
        cells += builder.toString()
        return cells
    }
}
