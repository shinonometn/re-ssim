package com.shinonometn.re.ssim.application.controller.management

import com.shinonometn.re.ssim.application.configuration.preparation.endpoint.scanning.ApiDescription
import com.shinonometn.re.ssim.application.security.WebSubjectUtils
import com.shinonometn.re.ssim.commons.BusinessException
import com.shinonometn.re.ssim.service.caterpillar.CaterpillarDataService
import com.shinonometn.re.ssim.service.caterpillar.CaterpillarFileManageService
import com.shinonometn.re.ssim.service.caterpillar.CaterpillarTaskService
import com.shinonometn.re.ssim.service.caterpillar.ImportTaskService
import com.shinonometn.re.ssim.service.caterpillar.entity.CaptureTask
import com.shinonometn.re.ssim.service.caterpillar.entity.CaptureTaskDetails
import com.shinonometn.re.ssim.service.caterpillar.entity.ImportTask
import com.shiononometn.commons.web.RexModel
import org.apache.commons.io.FileUtils
import org.apache.shiro.authz.annotation.RequiresPermissions
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.web.PageableDefault
import org.springframework.web.bind.annotation.*
import javax.servlet.http.HttpSession

@RestController
@RequestMapping("/caterpillar/task")
open class CaterpillarTaskManageAPI(private val caterpillarTaskService: CaterpillarTaskService,
                                    private val caterpillarDataService: CaterpillarDataService,
                                    private val dataImportTaskService: ImportTaskService,
                                    private val caterpillarFileManageService: CaterpillarFileManageService) {

    /**
     *
     * Get term list
     *
     */
    @GetMapping("/term")
    @ApiDescription(title = "Get school term list", description = "Get school terms, if not being cached, fetch from remote.")
    @RequiresPermissions("term_list:read")
    open fun termList(): Map<String, Any> = caterpillarTaskService.termList

    /**
     *
     * Clear cache and get term list
     *
     */
    @GetMapping("/term", params = ["refresh"])
    @ApiDescription(title = "Refresh school term list", description = "Get school terms from remote and evict the cache")
    @RequiresPermissions("term_list:refresh")
    open fun termListRefresh(): Map<String, Any> = caterpillarTaskService.reloadAndGetTermList()

    /**
     *
     * List all tasks
     *
     */
    @GetMapping("/capture")
    @ApiDescription(title = "List all capture tasks", description = "List all capture tasks. If task is running, show progress")
    @RequiresPermissions("task:list")
    open fun taskList(@PageableDefault pageable: Pageable): Page<CaptureTaskDetails> {
        return caterpillarTaskService.list(pageable)
    }

    /**
     *
     * create capture task
     *
     */
    @PostMapping("/capture")
    @ApiDescription(title = "Create a task", description = "Create a capture task.")
    @RequiresPermissions("task:create")
    open fun create(@RequestParam("termCode") termCode: String): CaptureTask {

        val termList = caterpillarTaskService.termList
        if (!termList.containsKey(termCode)) throw BusinessException("term_unknown");
        return caterpillarTaskService.create(termCode)

    }


    /**
     *
     * Start a task
     *
     */
    @PostMapping("/capture/{id}", params = ["start"])
    @ApiDescription(title = "Start a capture task", description = "Start a capture task.")
    @RequiresPermissions("task:start")
    open fun start(@PathVariable("id") id: String, @RequestParam("profile") profileName: String, session: HttpSession): Any {

        val username = WebSubjectUtils.currentUser().username;

        val caterpillarSettings = caterpillarDataService.findProfile(username!!, profileName).orElseThrow { BusinessException("profile_not_found") }

        return caterpillarTaskService.start(id, caterpillarSettings)

    }

    /**
     *
     * Stop a capture task
     *
     */
    @PostMapping("/capture/{id}", params = ["stop"])
    @ApiDescription(title = "Stop a running task", description = "Stop a running capture task.")
    @RequiresPermissions("task:stop")
    open fun stop(@PathVariable("id") id: String) {

        HashMap<String, Any>().apply {
            val dto = caterpillarTaskService.stop(id)
            if (dto == null) {
                this["error"] = "task_operate_failed"
                this["message"] = "task_not_registered"
            } else {
                this["message"] = "success"
                this["data"] = dto
            }
        }
    }

    /**
     *
     * Resume task
     *
     */
    @PostMapping("/capture/{id}", params = ["resume"])
    @ApiDescription(title = "Resume a capture task", description = "Resume a capture task.")
    @RequiresPermissions("task:restart")
    open fun resume(@PathVariable("id") id: String) =
            HashMap<String, Any>().apply {
                this["message"] = "success"
                this["data"] = caterpillarTaskService.resume(id)
            }

    /**
     *
     * Delete a task
     *
     */
    @DeleteMapping("/capture/{id}")
    @ResponseBody
    @ApiDescription(title = "Capture Tasks", description = "Delete a capture task.")
    @RequiresPermissions("task:delete")
    open fun delete(@PathVariable("id") id: String): RexModel<Any>? {
        caterpillarTaskService.delete(id)

        if(!dataImportTaskService.isCaptureTaskRelated(id)){
            FileUtils.deleteDirectory(caterpillarFileManageService.contextOf(id).file)
        }

        return RexModel.success<Any>()
    }

    /**
     *
     * Get current running task counts
     *
     */
    @GetMapping("/dashes")
    @ApiDescription(title = "Get task status", description = "Get task status.")
    @RequiresPermissions("dash.task:get")
    fun dashboard(): Map<String, String> = caterpillarTaskService.dashBoard()


    /**
     *
     * Start import task data to DB
     *
     */
    @PostMapping("/import/{id}")
    @ApiDescription(title = "Import task data", description = "Import a finshed capture task.")
    @RequiresPermissions("import:start")
    open fun import(@PathVariable("id") id: String): CaptureTask {

        val captureTaskDetails = caterpillarTaskService.queryTask(id) ?: throw BusinessException("task_not_found")

        if (captureTaskDetails.taskInfo.finished) throw BusinessException("task_finished")

        if (captureTaskDetails.runningTaskStatus != null && captureTaskDetails.runningTaskStatus!!.status == "Running")
            throw BusinessException("spider_running")

        return dataImportTaskService.start(captureTaskDetails.taskInfo.id)
    }

    @GetMapping("/import")
    @ApiDescription(title = "Get import tasks", description = "Get all import tasks")
    @RequiresPermissions("import:list")
    open fun listImportings(@PageableDefault pageable: Pageable) : Page<ImportTask> {
        return dataImportTaskService.list(pageable)
    }
}
