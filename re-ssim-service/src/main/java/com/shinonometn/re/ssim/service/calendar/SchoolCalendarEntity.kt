package com.shinonometn.re.ssim.service.calendar

import com.shinonometn.re.ssim.service.caterpillar.common.SchoolCalendar
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.beans.Transient
import java.io.Serializable
import java.time.ZoneId
import java.util.*

@Document
class SchoolCalendarEntity : Serializable {

    @Id
    var id: String? = null

    var term: String? = null
    var termName: String? = null
    var startDate: Date? = null
    var endDate: Date? = null
    var createTime: Date? = null

    @Transient
    fun fromSchoolCalendar(schoolCalendar: SchoolCalendar) {
        this.term = schoolCalendar.name
        this.startDate = Date.from(schoolCalendar.startDate.atZone(ZoneId.systemDefault()).toInstant())
        this.endDate = Date.from(schoolCalendar.endDate.atZone(ZoneId.systemDefault()).toInstant())
    }
}
