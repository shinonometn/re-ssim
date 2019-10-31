package com.shinonometn.re.ssim.data.kingo.application.entity

import com.fasterxml.jackson.annotation.JsonIgnoreProperties
import com.shinonometn.re.ssim.data.kingo.application.base.kingo.pojo.Lesson
import org.springframework.data.annotation.Id
import org.springframework.data.mongodb.core.mapping.Document
import java.io.Serializable

@Document
@JsonIgnoreProperties(ignoreUnknown = true)
open class CourseEntity : Serializable {

    @Id
    var id: String? = null
    var term: String? = null//学期
    var termCode: String? = null
    var name: String? = null//课程名称
    var code: String? = null//课程代号
    var unit: String? = null//承担单位
    var timeSpend: Double = 0.toDouble()//学时
    var point: Double? = null//学分
    var lessons: List<Lesson>? = null//课堂

    var batchId: String? = null // Import batch
}