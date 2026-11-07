package com.classflow.timetable.domain.model

import androidx.annotation.DrawableRes
import com.classflow.timetable.R

/**
 * 中德智能制造学院楼层数据（C-3 栋 / D3）。
 *
 * 来源：学院发布的《中德智能制造学院楼层分布图》（2026 年 10 月版，15 页 PDF）。
 * 平面图本身是从 PDF 里按页裁出来的位图，放在 `res/drawable/map_zd_*.webp`；
 * 这里的房间清单是同一份 PDF 的文字层，用来做「搜房间号」。
 *
 * 为什么楼层不能写 `1..18`：
 * 学院只占 **1~6、8~10、12、13、17、18** 这 13 层，7/11/14/15/16 是别人的楼层。
 * 用区间遍历会在那些楼层上显示一张空白图，用户只会以为图加载失败。
 */
object ZhongdeFloors {

    data class Room(val no: String, val name: String)

    data class Floor(
        /** 楼层号，同时是 UI 上的标签。 */
        val floor: Int,
        /** 这一层整体是什么用途，给楼层按钮下面那行小字用。 */
        val summary: String,
        @param:DrawableRes val image: Int,
        val rooms: List<Room>,
    )

    val all: List<Floor> = listOf(
        Floor(
            floor = 1,
            summary = "实训楼层",
            image = R.drawable.map_zd_1,
            rooms = listOf(
                Room("101", "工业机器人实验室"),
                Room("102", "大族激光-深圳技术大学激光智能制造联合实验室"),
                Room("103", "工程实践与创新能力工作室"),
                Room("103A", "实验室"),
                Room("104", "实验室"),
                Room("105", "数字化制造训练中心（飞秒激光加工实验室）"),
                Room("106", "中德啤酒酿造过程控制实验室"),
                Room("107", "储物间"),
                Room("108", "特种制造实训室"),
                Room("109", "学生创新实验室"),
                Room("110", "钳工实训室"),
                Room("111", "实训室"),
                Room("112", "超精密铣削加工实训室-钳工实训室"),
                Room("113", "数控铣削实训室"),
                Room("113A", "实训室"),
                Room("115", "传统制造实训室"),
                Room("116", "油料间"),
            ),
        ),
        Floor(
            floor = 2,
            summary = "先进制造中心",
            image = R.drawable.map_zd_2,
            rooms = listOf(
                Room("201", "数字化制造训练中心（增材制造实验室）"),
                Room("202", "先进制造示范中心"),
                Room("203", "教室"),
                Room("203A", "教室"),
                Room("205", "先进制造示范中心"),
            ),
        ),
        Floor(
            floor = 3,
            summary = "专业机房与实验室",
            image = R.drawable.map_zd_3,
            rooms = listOf(
                Room("301", "CAD/CAE/CAM 实验室 I"),
                Room("302", "自动化专业机房"),
                Room("303", "电子专业机房"),
                Room("303A", "力学实验室"),
                Room("305", "教室"),
                Room("306", "教室"),
                Room("307", "教室"),
                Room("308", "教室"),
                Room("309", "教室"),
                Room("310", "教室"),
                Room("311", "教室"),
                Room("312", "教室"),
                Room("313", "教室"),
                Room("313A", "机械原理实验室"),
                Room("315", "机械设计实验室"),
                Room("316", "光电检测实验室"),
                Room("317", "精密运动控制实验室"),
                Room("318", "电子综合实验室"),
                Room("320", "光学基础实验室"),
                Room("321", "虚拟仪器与检测技术实验室"),
            ),
        ),
        Floor(
            floor = 4,
            summary = "电学基础实验中心",
            image = R.drawable.map_zd_4,
            rooms = listOf(
                Room("401", "制造与检测实验室"),
                Room("402", "CAD/CAE/CAM 实验室 II"),
                Room("403", "工程图学室"),
                Room("403A", "热流体实验室"),
                Room("405", "智能机器人实验室"),
                Room("406", "职压疏语坊"),
                Room("407", "教室"),
                Room("408", "电学基础实验中心"),
                Room("409", "电学基础实验中心"),
                Room("410", "电学基础实验中心"),
                Room("411", "电学基础实验中心"),
                Room("412", "电学基础实验中心"),
                Room("413", "电学基础实验中心"),
                Room("413A", "电学基础实验中心"),
                Room("415", "电学基础实验中心"),
                Room("416", "电学基础实验中心"),
                Room("417", "电学基础实验中心"),
                Room("418", "电学基础实验中心"),
                Room("419", "嵌入式系统实验室"),
                Room("420", "信号处理实验室"),
                Room("421", "控制系统仿真与快速原型实验室"),
                Room("422", "激光原理与技术实验室"),
                Room("423", "控制理论与应用实验室"),
                Room("423A", "电力电子与电机实验室"),
                Room("425", "PLC 实验室"),
                Room("426", "工程材料实验室"),
            ),
        ),
        Floor(
            floor = 5,
            summary = "学院党政办公",
            image = R.drawable.map_zd_5,
            rooms = listOf(
                Room("501", "档案室"),
                Room("502", "党团活动室"),
                Room("503-1", "教务办公室"),
                Room("503-2", "行政办公室"),
                Room("503A", "学生工作办公室"),
                Room("505", "外事专员（副院长）"),
                Room("506", "校企合作专员办公室"),
                Room("507", "三电教研室主任办公室"),
                Room("508", "机械工程系主任办公室"),
                Room("509", "院长办公室"),
                Room("510", "教学副院长办公室"),
                Room("511", "网络面试间"),
                Room("512", "团委办公室"),
                Room("513", "学生活动室"),
                Room("513A", "党总支书记办公室"),
                Room("515", "党总支副书记办公室"),
            ),
        ),
        Floor(
            floor = 6,
            summary = "创新实践实验室",
            image = R.drawable.map_zd_6,
            rooms = listOf(
                Room("601", "电子技能与创新实验室 I"),
                Room("602", "自动化创新实践实验室 I"),
                Room("603", "自动化创新实践实验室 II"),
                Room("603A", "电子技能与创新实验室 II"),
                Room("605", "认知实践实验室"),
                Room("606", "机械创新实践实验室"),
            ),
        ),
        Floor(
            floor = 8,
            summary = "激光与智能制造",
            image = R.drawable.map_zd_8,
            rooms = listOf(
                Room("801", "激光微纳加工实验室"),
                Room("802", "精密成型与 3D 打印实验室 II"),
                Room("803", "激光微纳加工实验室-智能制造及数字孪生技术实验室 I"),
                Room("803A", "实验室"),
                Room("805", "智能传感与检测实验室 I"),
                Room("806", "实验室"),
            ),
        ),
        Floor(
            floor = 9,
            summary = "智能可穿戴与无人系统",
            image = R.drawable.map_zd_9,
            rooms = listOf(
                Room("901", "智能可穿戴设备及应用实验室"),
                Room("902", "智能可穿戴设备及应用实验室"),
                Room("903", "智慧无人系统实验室"),
                Room("903A", "电路与系统实验室 I"),
                Room("905", "电路与系统实验室 II"),
            ),
        ),
        Floor(
            floor = 10,
            summary = "现代设计与数字孪生",
            image = R.drawable.map_zd_10,
            rooms = listOf(
                Room("1001", "现代设计理论及应用实验室 I"),
                Room("1002", "现代设计理论及应用实验室 II"),
                Room("1003", "智能制造及数字孪生技术实验室 II"),
                Room("1003A", "机器人技术及应用实验室"),
            ),
        ),
        Floor(
            floor = 12,
            summary = "驱动与智能控制",
            image = R.drawable.map_zd_12,
            rooms = listOf(
                Room("1201", "驱动与伺服技术实验室 I"),
                Room("1202", "驱动与伺服技术实验室 II"),
                Room("1203", "智能传感与检测实验室 II"),
                Room("1203A", "智能控制与算法实验室"),
            ),
        ),
        Floor(
            floor = 13,
            summary = "教师办公室",
            image = R.drawable.map_zd_13,
            rooms = listOf(
                Room("1301", "教师办公室"), Room("1302", "教师办公室"),
                Room("1303", "教师办公室"), Room("1303A", "教师办公室"),
                Room("1305", "教师办公室"), Room("1306", "教师办公室"),
                Room("1307", "教师办公室"), Room("1308", "教师办公室"),
                Room("1309", "教师办公室"), Room("1310", "教师办公室"),
                Room("1311", "教师办公室"), Room("1312", "教师办公室"),
                Room("1313", "教师办公室"), Room("1313A", "教师办公室"),
                Room("1315", "教师办公室"), Room("1316", "教师办公室"),
            ),
        ),
        Floor(
            floor = 17,
            summary = "教师办公室",
            image = R.drawable.map_zd_17,
            rooms = listOf(
                Room("1701", "教师办公室"), Room("1702", "教师办公室"),
                Room("1703", "教师办公室"), Room("1703A", "教师办公室"),
                Room("1705", "教师办公室"), Room("1706", "教师办公室"),
                Room("1707", "教师办公室"), Room("1708", "教师办公室"),
                Room("1709", "教师办公室"), Room("1710", "教师办公室"),
                Room("1711", "教师办公室"), Room("1712", "教师办公室"),
                Room("1713", "教师办公室"), Room("1713A", "教师办公室"),
                Room("1715", "教师办公室"),
            ),
        ),
        Floor(
            floor = 18,
            summary = "教师办公室",
            image = R.drawable.map_zd_18,
            rooms = listOf(
                Room("1801", "教师办公室"), Room("1802", "教师办公室"),
                Room("1803", "教师办公室"), Room("1803A", "教师办公室"),
                Room("1805", "教师办公室"), Room("1806", "教师办公室"),
                Room("1807", "教师办公室"), Room("1808", "教师办公室"),
                Room("1809", "教师办公室"), Room("1810", "教师办公室"),
                Room("1811", "教师办公室"), Room("1812", "教师办公室"),
                Room("1813", "教师办公室"), Room("1813A", "教师办公室"),
                Room("1815", "教师办公室"), Room("1816", "教师办公室"),
                Room("1817", "教师办公室"), Room("1818", "教师办公室"),
                Room("1819", "教师办公室"), Room("1820", "教师办公室"),
            ),
        ),
    )

    /**
     * 按房间号找楼层，给「搜 C-3 305 在哪层」用。
     *
     * 只做前缀匹配而不是模糊搜索：房间号是个封闭集合，模糊匹配会把
     * 「305」匹配到「1305」，那是最糟的错 —— 用户会真的跑去 13 楼。
     * 所以先比完全相等，再做「以它开头」。
     */
    fun find(room: String): List<Pair<Floor, Room>> {
        val q = room.trim().uppercase()
        if (q.isEmpty()) return emptyList()
        val exact = mutableListOf<Pair<Floor, Room>>()
        val prefix = mutableListOf<Pair<Floor, Room>>()
        all.forEach { f ->
            f.rooms.forEach { r ->
                val no = r.no.uppercase()
                when {
                    no == q -> exact += f to r
                    no.startsWith(q) -> prefix += f to r
                }
            }
        }
        return if (exact.isNotEmpty()) exact else prefix
    }

    /** 楼层号列表，供 UI 做横向滚动的选择器。 */
    val floors: List<Int> = all.map { it.floor }

    fun of(floor: Int): Floor = all.first { it.floor == floor }
}
