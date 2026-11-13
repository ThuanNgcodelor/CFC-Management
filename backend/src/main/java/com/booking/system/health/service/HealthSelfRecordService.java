package com.booking.system.health.service;

import com.booking.system.health.api.HealthSelfDtos.Detail;
import com.booking.system.health.api.HealthSelfDtos.Profile;
import com.booking.system.health.api.HealthSelfDtos.Release;
import com.booking.system.health.api.HrHealthRecordDtos.*;
import com.booking.system.health.api.HrHealthReportDtos.*;
import com.booking.system.health.repository.HealthPublicationRepository;
import com.booking.system.hr.api.HrApiException;
import com.booking.system.hr.api.dto.HrPageResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.io.IOException;

/** Owner is supplied by the verified personal session, never by request parameters. */
@Service
@lombok.extern.slf4j.Slf4j
@RequiredArgsConstructor
public class HealthSelfRecordService {
    private final HealthPublishedContentReader contents;
    private final HealthPublicationRepository publications;
    private final HealthRecordComparison comparison;
    private final java.util.concurrent.Semaphore pdfSlot = new java.util.concurrent.Semaphore(1);

    @Transactional(readOnly=true)
    public Profile profile(String owner, int from, int to, int page, int size) {
        page(page,size); range(from,to);
        var rows = publications.currentPage(owner,from,to,PageRequest.of(page,size));
        var verified = rows.getContent().stream().map(v -> contents.validate(owner,v)).toList();
        var person = verified.isEmpty() ? null : verified.getFirst().snapshot().person();
        return new Profile(person == null ? null : person.name(),person == null ? null : person.code(),person == null ? null : person.birthDate(),
            HrPageResponse.from(new PageImpl<>(verified.stream().map(HealthSelfRecordService::release).toList(),rows.getPageable(),rows.getTotalElements()),v->v));
    }
    @Transactional(readOnly=true)
    public Detail detail(String owner,String id,String keyword,int page,int size) {
        page(page,size);var current=contents.current(owner,id);
        var values=filtered(current.snapshot().results(),keyword).stream().map(HealthSelfRecordService::privateFieldsRemoved).toList();
        return new Detail(release(current),slice(values,page,size));
    }
    @Transactional(readOnly=true)
    public Comparison compare(String owner,List<String> ids,String keyword,int page,int size) {
        page(page,size);
        if(ids==null||ids.size()<2||ids.size()>6||new HashSet<>(ids).size()!=ids.size())throw HrApiException.badRequest("HEALTH_SELF_COMPARE","Chọn 2–6 lượt công bố khác nhau của bạn.");
        var released=ids.stream().map(id->contents.current(owner,id)).sorted(Comparator.comparing((HealthPublishedReader.Content c)->c.snapshot().visit().year()).thenComparing(c->c.snapshot().visit().examinedOn(),Comparator.nullsLast(Comparator.naturalOrder())).thenComparing(c->c.info().id())).toList();
        var visits=released.stream().map(c->publicVisit(c.snapshot().visit())).toList();
        var values=filtered(released.stream().flatMap(c->c.snapshot().results().stream()).toList(),keyword).stream().map(HealthSelfRecordService::privateFieldsRemoved).toList();
        var keys=new TreeMap<String,IndicatorKey>();
        values.forEach(v->keys.putIfAbsent(v.key(),new IndicatorKey(v.key(),v.indicatorId(),v.code(),v.label(),v.category(),v.type(),v.unit(),v.method())));
        var selected=slice(new ArrayList<>(keys.values()),page,size);
        var result=selected.content().stream().map(k->comparison.row(k,visits,values)).toList();
        return new Comparison(visits,HrPageResponse.from(new PageImpl<>(result,PageRequest.of(page,size),keys.size()),v->v));
    }
    public byte[] pdf(String owner,String id) {
        var current=contents.current(owner,id);
        if(!pdfSlot.tryAcquire())throw HrApiException.conflict("HEALTH_SELF_PDF_BUSY","Đang tạo phiếu; vui lòng thử lại.");
        try {
            var body=current.snapshot();var visit=publicVisit(body.visit());var request=new Request(owner,visit.year(),visit.year(),null,null,"ALL","codeAsc",List.of(visit.id()));
            var people=List.of(body.person());var visits=List.of(new VisitRow(owner,visit));
            var values=body.results().stream().map(HealthSelfRecordService::privateFieldsRemoved).toList();
            var summary=new Summary(1,1,1,values.size(),List.of(),List.of("Bản tổng hợp đã công bố; không thay phiếu bệnh viện.",body.incomplete()?"Kết quả có thể chưa đầy đủ.":""));
            byte[] bytes=HealthReportPdf.render(new Snapshot(request,people,visits,values,List.of(),summary),"SỔ SỨC KHỎE CÁ NHÂN","Phiên bản "+current.info().revision());
            contents.current(owner,id);return bytes;
        } catch(IOException exception) {
            log.warn("Personal health PDF failed: type={}", exception.getClass().getSimpleName());
            throw HrApiException.badRequest("HEALTH_SELF_PDF_FAILED","Chưa tạo được phiếu, vui lòng thử lại.");
        } finally { pdfSlot.release(); }
    }
    private static Release release(HealthPublishedReader.Content content) {
        var v=content.snapshot().visit();return new Release(content.info().id(),content.info().revision(),v.year(),v.campaign(),v.provider(),v.examinedOn(),v.classification(),content.snapshot().incomplete(),content.info().createdAt());
    }
    private static Visit publicVisit(Visit v) {
        return new Visit(v.id(),v.year(),v.campaign(),v.provider(),v.examinedOn(),"PUBLISHED",null,v.sid(),v.resultCount(),v.classification());
    }
    private static Measurement privateFieldsRemoved(Measurement v) {
        return new Measurement(v.visitId(),v.key(),v.indicatorId(),v.code(),v.label(),v.category(),v.type(),v.raw(),v.unit(),v.method(),v.reference(),null,null,null,null);
    }
    private static List<Measurement> filtered(List<Measurement> values,String keyword) {
        if(keyword!=null&&keyword.length()>100)throw HrApiException.badRequest("HEALTH_SELF_SEARCH","Từ khóa tối đa 100 ký tự.");
        String text=keyword==null?"":keyword.strip().toLowerCase(Locale.ROOT);
        return values.stream().filter(v->v.label().toLowerCase(Locale.ROOT).contains(text)||v.code().toLowerCase(Locale.ROOT).contains(text)).toList();
    }
    private static <T> HrPageResponse<T> slice(List<T> values,int page,int size) {
        int start=Math.min(values.size(),page*size),end=Math.min(values.size(),start+size);
        return HrPageResponse.from(new PageImpl<>(values.subList(start,end),PageRequest.of(page,size),values.size()),v->v);
    }
    private static void page(int page,int size) {
        if(page<0||page>10000||size<1||size>20)throw HrApiException.badRequest("HEALTH_SELF_PAGE","Phân trang không hợp lệ.");
    }
    private static void range(int from,int to) {
        if(from<1900||to>2200||from>to||to-from>9)throw HrApiException.badRequest("HEALTH_SELF_YEARS","Chọn khoảng tối đa 10 năm.");
    }
}
