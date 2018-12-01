package com.shinonometn.re.ssim.application.controller.course

import com.shinonometn.re.ssim.commons.CacheKeys
import com.shinonometn.re.ssim.service.courses.CourseInfoService
import com.shinonometn.re.ssim.service.courses.plugin.structure.TermMeta
import org.springframework.beans.factory.annotation.Autowired
import org.springframework.cache.annotation.Cacheable
import org.springframework.data.mongodb.core.aggregation.Aggregation.*
import org.springframework.data.mongodb.core.query.Criteria
import org.springframework.web.bind.annotation.*

@RestController
@RequestMapping("/api/term")
open class TermInfoController @Autowired
constructor(private val courseInfoService: CourseInfoService) {

    /**
     *
     * List all terms
     *
     */
    @GetMapping
    open fun list(): Map<String, TermMeta> = courseInfoService.termList()

    /**
     *
     * List term courses
     *
     */
    @GetMapping("/{name}", params = ["course"])
    @ResponseBody
    @Cacheable(CacheKeys.TERM_COURSE_LIST)
    open fun listTermCourse(@PathVariable("name") termName: String): Any =
            courseInfoService.queryTermCourse(termName)

    /**
     *
     * List all teacher presented in term
     *
     */
    @GetMapping("/{name}", params = ["teacher"])
    @ResponseBody
    @Cacheable(CacheKeys.TERM_TEACHER_LIST)
    open fun listTermTeachers(@PathVariable("name") termName: String): Any? =
            courseInfoService.queryTermTeachers(termName)

    /**
     *
     * List all term class
     *
     */
    @GetMapping("/{name}", params = ["class"])
    @ResponseBody
    @Cacheable(CacheKeys.TERM_CLASS_LIST)
    open fun listTermClasses(@PathVariable("name") termName: String): Any? =
            courseInfoService.queryTermClasses(termName)

    /**
     *
     * Show term week range
     *
     */
    @GetMapping("/{name}", params = ["weekRange"])
    @ResponseBody
    @Cacheable(CacheKeys.TERM_WEEK_RANGE)
    open fun showTermWeekRange(@PathVariable("name") termName: String): Any? =
            courseInfoService.queryTermWeekRange(termName)

    /**
     *
     * Get all class types of term
     *
     */
    @GetMapping("/{name}", params = ["classType"])
    @ResponseBody
    @Cacheable(CacheKeys.TERM_CLASS_TYPE)
    open fun listTermClassTypes(@PathVariable("name") termName: String): Any? =
            courseInfoService.queryTermCourseTypes(termName)

    /**
     *
     * List all classrooms that used in term
     *
     */
    @GetMapping("/{name}", params = ["classroom"])
    @ResponseBody
    @Cacheable(CacheKeys.TERM_CLASSROOM)
    open fun listTermClassrooms(@PathVariable("name") termName: String): Any? =
            courseInfoService.queryTermClassrooms(termName)
}