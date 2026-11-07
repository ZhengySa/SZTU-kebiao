package com.classflow.timetable.data.local

import androidx.room.TypeConverter
import java.time.LocalDate

/**
 * Room 类型转换。
 * LocalDate 存 epochDay（跨时区稳定）；LocalTime 直接存分钟数（Int），比字符串好排序。
 */
class Converters {

    @TypeConverter
    fun toEpochDay(value: LocalDate?): Long? = value?.toEpochDay()

    @TypeConverter
    fun fromEpochDay(value: Long?): LocalDate? = value?.let(LocalDate::ofEpochDay)
}
