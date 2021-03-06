package com.shinonometn.re.ssim.service.caterpillar;

import com.shinonometn.re.ssim.commons.BusinessException;
import com.shinonometn.re.ssim.commons.CacheKeys;
import com.shinonometn.re.ssim.commons.JSON;
import com.shinonometn.re.ssim.commons.file.fundation.FileContext;
import com.shinonometn.re.ssim.service.caterpillar.commons.CaptureTaskStage;
import com.shinonometn.re.ssim.service.caterpillar.entity.CaptureTask;
import com.shinonometn.re.ssim.service.caterpillar.entity.CaptureTaskDetails;
import com.shinonometn.re.ssim.service.caterpillar.entity.CaterpillarSetting;
import com.shinonometn.re.ssim.service.caterpillar.kingo.KingoUrls;
import com.shinonometn.re.ssim.service.caterpillar.kingo.capture.*;
import com.shinonometn.re.ssim.service.caterpillar.kingo.pojo.Course;
import com.shinonometn.re.ssim.service.caterpillar.plugin.CaterpillarMonitorStore;
import com.shinonometn.re.ssim.service.caterpillar.repository.CaptureTaskRepository;
import com.shinonometn.re.ssim.service.courses.CourseInfoService;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.core.task.TaskExecutor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import us.codecraft.webmagic.Request;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Spider;
import us.codecraft.webmagic.model.HttpRequestBody;
import us.codecraft.webmagic.utils.HttpConstant;

import java.io.File;
import java.io.FileOutputStream;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
public class CaterpillarTaskService {

    private final Logger logger = LoggerFactory.getLogger(CaterpillarTaskService.class);

    private final CourseInfoService courseInfoService;
    private final CaterpillarFileManageService fileManageService;
    private final SpiderMonitor spiderMonitor;
    private final TaskExecutor taskExecutor;

    private final CaterpillarMonitorStore caterpillarMonitorStore;

    private final CaptureTaskRepository captureTaskRepository;

    public CaterpillarTaskService(CourseInfoService courseInfoService, CaterpillarFileManageService fileManageService,
                                  SpiderMonitor spiderMonitor,
                                  TaskExecutor taskExecutor,
                                  CaterpillarMonitorStore caterpillarMonitorStore,
                                  CaptureTaskRepository captureTaskRepository) {
        this.courseInfoService = courseInfoService;

        this.fileManageService = fileManageService;
        this.spiderMonitor = spiderMonitor;
        this.taskExecutor = taskExecutor;
        this.caterpillarMonitorStore = caterpillarMonitorStore;
        this.captureTaskRepository = captureTaskRepository;
    }

    /**
     * Get all school terms
     * <p>
     * if cache not found, load from remote and cache it
     *
     * @return a map, term code as key, term name as value
     */
    @Cacheable(CacheKeys.CAPTURE_TERM_LIST)
    @NotNull
    public Map<String, String> getTermList() {

        final Map<String, String> capturedResult = new HashMap<>();

        Spider.create(new TermListPageProcessor(CaterpillarSetting.Companion.createDefaultSite()))
                .addUrl(KingoUrls.classInfoQueryPage)
                .addPipeline((r, t) -> capturedResult.putAll(TermListPageProcessor.getTerms(r)))
                .run();

        logger.debug("Cache not found, returning remote data.");

        return capturedResult;
    }

    /**
     * Individual cache and re-capture list from remote
     *
     * @return see getTermList()
     */
    @CachePut(CacheKeys.CAPTURE_TERM_LIST)
    public Map<String, String> reloadAndGetTermList() {
        return getTermList();
    }

    /**
     * Get all tasks
     *
     * @return task list
     */
    public Page<CaptureTaskDetails> list(Pageable pageable) {
        return captureTaskRepository.findAll(pageable).map(this::getTaskDetails);
    }

    /*
     *
     *
     * Task management
     *
     *
     *
     *
     * */

    /**
     * Create a termName capture task with code
     *
     * @param termCode termName code
     * @return created task
     */
    public CaptureTask create(String termCode) {
        CaptureTask captureTask = new CaptureTask();

        captureTask.setCreateDate(new Date());
        captureTask.setTermCode(termCode);
        captureTask.setTermName(getTermList().get(termCode));

        captureTask.setStage(CaptureTaskStage.NONE);
        captureTask.setStageReport("task_created");

        return captureTaskRepository.save(captureTask);
    }

    /**
     * Stop a task
     *
     * @param taskId task id
     * @return task dto
     */
    public CaptureTaskDetails stop(String taskId) {
        CaptureTask captureTask = captureTaskRepository.findById(taskId).orElse(null);
        if (captureTask == null) return null;

        SpiderStatus spiderStatus = spiderMonitor.getSpiderStatus().get(taskId);
        if (spiderStatus == null) throw new BusinessException("task_have_not_initialized");
        spiderStatus.stop();

        changeCaptureTaskStatus(captureTask, null, "task_has_been_stopped");

        return captureTaskRepository.findById(taskId).map(this::getTaskDetails).orElse(null);
    }

    /**
     * Resume a stopped task
     *
     * @param taskId task id
     * @return dto
     */
    public CaptureTaskDetails resume(String taskId) {
        CaptureTaskDetails captureTaskDetails = captureTaskRepository.findById(taskId).map(this::getTaskDetails).orElse(null);
        if (captureTaskDetails == null) return null;

        SpiderStatus spiderStatus = captureTaskDetails.getRunningTaskStatus();
        if (spiderStatus == null) throw new BusinessException("task_have_not_initialized");
        if (spiderStatus.equals(Spider.Status.Running.name())) throw new BusinessException("spider_running");

        spiderStatus.start();
        changeCaptureTaskStatus(captureTaskDetails.getTaskInfo(), null, "task_resumed");

        return captureTaskDetails;
    }

    /**
     * Start a capture task by task id
     * <p>
     * It will capture all subject info to a temporal folder
     *
     * @param taskId task id
     * @return dto
     */
    public CaptureTaskDetails start(String taskId, CaterpillarSetting caterpillarSetting) {

        CaptureTaskDetails captureTaskDetails = captureTaskRepository
                .findById(taskId)
                .map(this::getTaskDetails)
                .orElseThrow(() -> new BusinessException("task_not_exists"));

        if (captureTaskDetails.getRunningTaskStatus() != null) throw new BusinessException("task_thread_exists");

        CaptureTask captureTask = captureTaskDetails.getTaskInfo();

        changeCaptureTaskStatus(captureTask, CaptureTaskStage.INITIALIZE, "task_initialing");
        caterpillarMonitorStore.increaseCaptureTaskCount();

        taskExecutor.execute(() -> {

            changeCaptureTaskStatus(captureTask, null, "login_to_kingo");

            try {
                Site site = doLogin(caterpillarSetting);

                FileContext dataFolder = fileManageService.contextOf(taskId);
                if (!dataFolder.exists() && !dataFolder.getFile().mkdirs())
                    throw new BusinessException("Could not create work directory for task");

                Spider spider = Spider.create(new CourseDetailsPageProcessor(site))
                        .addPipeline((resultItems, task) -> {
                            try {
                                Course course = CourseDetailsPageProcessor.getSubject(resultItems);
                                JSON.write(new FileOutputStream(new File(dataFolder.getFile(), Objects.requireNonNull(course.getCode()))), course);
                            } catch (Exception e) {
                                changeCaptureTaskStatus(captureTask, null, "failed:" + e.getMessage());
                                throw new RuntimeException(e);
                            }
                        })
                        .setUUID(taskId)
                        .thread(caterpillarSetting.getThreads());

                spider.startRequest(fetchTermCourseList(site, captureTaskDetails.getTaskInfo().getTermCode())
                        .stream()
                        .map(id -> createSubjectRequest(site, captureTaskDetails.getTaskInfo().getTermCode(), id))
                        .collect(Collectors.toList()));

                spiderMonitor.register(spider);

                changeCaptureTaskStatus(captureTask, CaptureTaskStage.CAPTURE, "downloading");
                spider.run();
                changeCaptureTaskStatus(captureTask, CaptureTaskStage.STOPPED, "stopped");

            } catch (BusinessException e) {
                changeCaptureTaskStatus(captureTask, null, "failed:" + e.getMessage());
            } finally {
                caterpillarMonitorStore.decreaseCaptureTaskCount();
            }
        });

        return captureTaskDetails;
    }


    /**
     * Delete a not running task
     *
     * @param id task id
     */
    public void delete(String id) {
        Map<String, SpiderStatus> spiderStatusMap = spiderMonitor.getSpiderStatus();

        if (spiderStatusMap.containsKey(id)) {

            SpiderStatus spiderStatus = spiderStatusMap.get(id);
            if (spiderStatus.getStatus().equals(Spider.Status.Running.name()))
                throw new BusinessException("spider_running");

            spiderStatusMap.remove(id);
        }

        captureTaskRepository.deleteById(id);
    }

    /**
     * Check if caterpillar setting valid
     *
     * @param caterpillarSetting settings
     * @return result
     */
    public boolean validateSettings(CaterpillarSetting caterpillarSetting) {
        return doLogin(caterpillarSetting) != null;
    }

    /*

        Status

    */

    /**
     * Get importing task count
     *
     * @return int
     */
    public Integer getImportingTaskCount() {
        return caterpillarMonitorStore.getImportTaskCount();
    }

    /**
     * Get running spider counts
     *
     * @return long
     */
    public long getCapturingTaskCount() {
        return spiderMonitor.getSpiderStatus().values().stream().filter(i -> i.getStatus().equals("Running")).count();
    }

    /**
     * Get a task dto by id
     *
     * @param id id
     * @return dto
     */
    @Nullable
    public CaptureTaskDetails queryTask(@NotNull String id) {
        return captureTaskRepository.findById(id).map(this::getTaskDetails).orElse(null);
    }

    /*

      Private procedure

     */

    private CaptureTaskDetails getTaskDetails(CaptureTask captureTask) {
        CaptureTaskDetails captureTaskDetails = new CaptureTaskDetails();
        captureTaskDetails.setTaskInfo(captureTask);
        captureTaskDetails.setRunningTaskStatus(spiderMonitor.getSpiderStatus().get(captureTask.getId()));
        return captureTaskDetails;
    }

    private Collection<String> fetchTermCourseList(Site site, String termCode) {
        Map<String, String> termList = new HashMap<>();

        Spider.create(new CoursesListPageProcessor(site))
                .addUrl(KingoUrls.subjectListQueryPath + termCode)
                .addPipeline((resultItems, task) -> termList.putAll(CoursesListPageProcessor.getCourseList(resultItems)))
                .run();

        logger.debug("Fetched remote course list of term {}.", termCode);

        return termList.keySet();
    }

    private File getTempDir(String taskId) {
        File file = new File("./_temp/" + taskId);
        if (!file.exists()) if (!file.mkdirs()) throw new IllegalStateException("create_temp_folder_failed");
        File[] files = file.listFiles();
        if (files != null) Stream.of(files).forEach(File::delete);
        return file;
    }

    private Site doLogin(CaterpillarSetting caterpillarSetting) {

        Site site = caterpillarSetting.createSite();

        String username = caterpillarSetting.getUsername();
        String password = caterpillarSetting.getPassword();
        String role = caterpillarSetting.getRole();


        Map<String, Object> items = new HashMap<>();

        Spider.create(new LoginPreparePageProcessor(username, password, role, site))
                .addUrl(KingoUrls.loginPageAddress)
                .addPipeline((r, t) -> items.putAll(r.getAll()))
                .run();

        AtomicBoolean loginResult = new AtomicBoolean(false);

        if (!LoginPreparePageProcessor.getIsReady(items)) throw new IllegalStateException("could_not_get_login_form");

        Map<String, String> cookies = LoginPreparePageProcessor.getCookie(items);
        if (cookies != null) site.addCookie("ASP.NET_SessionId", cookies.get("ASP.NET_SessionId"));

        Map<String, Object> formFields = LoginPreparePageProcessor.getFormFields(items);

        Request loginRequest = new Request(KingoUrls.loginPageAddress);
        loginRequest.setMethod(HttpConstant.Method.POST);
        loginRequest.addHeader("Content-Type", "application/x-www-form-urlencoded");
        loginRequest.addHeader("Referer", KingoUrls.loginPageAddress);
        loginRequest.setRequestBody(HttpRequestBody.form(formFields, Objects.requireNonNull(caterpillarSetting.getEncoding())));


        Spider.create(new LoginExecutePageProcessor(site))
                .addRequest(loginRequest)
                .addPipeline((resultItems, task) -> loginResult.set(LoginExecutePageProcessor.getIsLogin(resultItems)))
                .run();

        logger.debug("Login to kingo {}.", (loginResult.get() ? "successful" : "failed"));
        if (!loginResult.get()) throw new BusinessException("login_to_kingo_failed");

        return site;

    }

    private Request createSubjectRequest(Site site, String termCode, String subjectCode) {
        Map<String, Object> form = new HashMap<>();
        form.put("gs", "2");
        form.put("txt_yzm", "");
        form.put("Sel_XNXQ", termCode);
        form.put("Sel_KC", subjectCode);

        Request request = new Request(KingoUrls.subjectQueryPage);
        request.setMethod(HttpConstant.Method.POST);
        request.setRequestBody(HttpRequestBody.form(form, site.getCharset()));
        request.addHeader("Referer", KingoUrls.classInfoQueryPage);

        return request;

    }

    public CaptureTask changeCaptureTaskStatus(CaptureTask captureTask, CaptureTaskStage status, String description) {
        if (status != null) captureTask.setStage(status);
        if (description != null) captureTask.setStageReport(description);
        return captureTaskRepository.save(captureTask);
    }
}
