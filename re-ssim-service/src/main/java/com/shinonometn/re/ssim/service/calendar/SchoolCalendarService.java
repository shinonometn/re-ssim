package com.shinonometn.re.ssim.service.calendar;

import com.shinonometn.re.ssim.commons.BusinessException;
import com.shinonometn.re.ssim.service.caterpillar.common.SchoolCalendar;
import com.shinonometn.re.ssim.service.caterpillar.kingo.KingoSchoolCalendar;
import com.shinonometn.re.ssim.service.caterpillar.kingo.capture.CalendarPageProcessor;
import com.shinonometn.re.ssim.service.terms.TermInfoEntity;
import org.jetbrains.annotations.Nullable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import us.codecraft.webmagic.Request;
import us.codecraft.webmagic.Site;
import us.codecraft.webmagic.Spider;
import us.codecraft.webmagic.model.HttpRequestBody;
import us.codecraft.webmagic.utils.HttpConstant;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

import static com.shinonometn.re.ssim.service.caterpillar.kingo.KingoUrls.calendarPage;

@Service
public class SchoolCalendarService {

    private final SchoolCalendarEntityRepository schoolCalendarEntityRepository;
    private final SchoolCalendarStore schoolCalendarStore;

    public SchoolCalendarService(SchoolCalendarEntityRepository schoolCalendarEntityRepository, SchoolCalendarStore schoolCalendarStore) {
        this.schoolCalendarEntityRepository = schoolCalendarEntityRepository;
        this.schoolCalendarStore = schoolCalendarStore;

        // let school date store can get current school date
        schoolCalendarStore.setSchoolDateSupplier(() -> {
            SchoolCalendar s = getCurrentCalendarInfo();
            return s == null ? null : s.getFromDateTime(LocalDateTime.now());
        });
    }

    public Page<SchoolCalendarEntity> list(Pageable pageable) {
        return schoolCalendarEntityRepository.findAll(pageable);
    }

    public SchoolCalendarEntity find(String id) {
        return schoolCalendarEntityRepository.findById(id).orElse(null);
    }

    public SchoolCalendarEntity save(SchoolCalendarEntity schoolCalendarEntity) {
        return schoolCalendarEntityRepository.save(schoolCalendarEntity);
    }

    public void delete(String id) {
        schoolCalendarEntityRepository.deleteById(id);
    }

    public void pull(TermInfoEntity entity) {
        String termCode = entity.getCode();
        if (termCode == null) throw new BusinessException("term_code_unknown");

        Request calendarQueryRequest = new Request(calendarPage);
        calendarQueryRequest.setMethod(HttpConstant.Method.POST);
        Map<String, Object> requestContent = new HashMap<>();
        requestContent.put("sel_xnxq", termCode);
        calendarQueryRequest.addHeader("Content-Type", "application/x-www-form-urlencoded; charset=GBK");
        calendarQueryRequest.setRequestBody(HttpRequestBody.form(requestContent, "gbk"));

        Spider.create(new CalendarPageProcessor(site))
                .addRequest(calendarQueryRequest)
                .addPipeline((r, t) -> schoolCalendarEntityRepository.saveAll(CalendarPageProcessor
                        .getCalendarRangeInfo(r)
                        .stream()
                        .map(e -> {
                            SchoolCalendarEntity calendarEntity = schoolCalendarEntityRepository.findByTermName(e.termName)
                                    .orElse(new SchoolCalendarEntity());

                            calendarEntity.setTermName(e.termName);
                            calendarEntity.setStartDate(e.startDate);
                            calendarEntity.setEndDate(e.endDate);
                            calendarEntity.setCreateTime(new Date());

                            return calendarEntity;
                        }).collect(Collectors.toList())))
                .run();
    }

    private Site site = Site.me()
            .setDomain("jwgl.lnc.edu.cn")
            .setTimeOut(10000)
            .setRetryTimes(81)
            .setSleepTime(500)
            .setCharset("GBK")
            .setUserAgent("Mozilla/5.0 (Macintosh; Intel Mac OS X 10_12_3) AppleWebKit/602.4.8 (KHTML, like Gecko) Version/10.0.3 Safari/602.4.8")
            .addHeader("Referer", calendarPage);

    public Optional<SchoolCalendarEntity> findByTermCode(@Nullable String term) {
        return schoolCalendarEntityRepository.findByTerm(term);
    }

    public boolean existsByTermName(@Nullable String name) {
        return schoolCalendarEntityRepository.existsByTermName(name);
    }

    public Optional<SchoolCalendarEntity> findById(String id) {
        return schoolCalendarEntityRepository.findById(id);
    }


    public SchoolCalendar getCurrentCalendarInfo() {

        List<SchoolCalendarEntity> queryResult = schoolCalendarEntityRepository.findAll(PageRequest.of(0, 2, Sort.Direction.DESC, "endDate")).getContent();
        if (queryResult.size() == 0) return null;
        SchoolCalendarEntity latestCalendar = queryResult.get(0);
        if (new Date().compareTo(latestCalendar.getStartDate()) < 0) latestCalendar = queryResult.get(1);
        if (latestCalendar == null) return null;

        return new KingoSchoolCalendar(latestCalendar.getTermName(), latestCalendar.getStartDate(), latestCalendar.getEndDate());
    }
}
