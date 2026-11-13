package com.booking.system.health.service;

import com.booking.system.health.api.HrHealthPublicationDtos.*;
import com.booking.system.health.api.HrHealthRecordDtos.Measurement;
import com.booking.system.health.repository.*;
import com.booking.system.hr.api.HrApiException;
import com.booking.system.hr.api.dto.HrPageResponse;
import com.booking.system.hr.importer.HrImportActor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class HealthPublishedReader {
    private final HealthAccess access;
    private final HealthPublishedContentReader contents;
    private final HealthPublicationReadRepository reads;
    private final HealthRecordRepository records;
    public record Content(PublicationInfo info,PublishedSnapshot snapshot) {
        @Override public String toString(){return "HealthPublishedContent[redacted]";}
    }
    public HrPageResponse<PublicationInfo> history(String employee,String visit,int page,int size,HrImportActor actor) {
        access.requireManager(actor);page(page,size);
        if(records.ownedVisits(employee,List.of(visit)).isEmpty())throw missing();
        return HrPageResponse.from(reads.history(employee,visit,PageRequest.of(page,size)),v->v);
    }
    public Content current(String employee,String id,HrImportActor actor) {
        access.requireManager(actor);return contents.current(employee,id);
    }
    public PublishedResults results(String employee,String id,String keyword,int page,int size,HrImportActor actor) {
        page(page,size);if(keyword!=null&&keyword.length()>100)throw missing();var content=current(employee,id,actor);String text=keyword==null?"":keyword.toLowerCase(Locale.ROOT).strip();
        var values=content.snapshot().results().stream().filter(v->v.label().toLowerCase(Locale.ROOT).contains(text)||v.code().toLowerCase(Locale.ROOT).contains(text)).toList();
        int start=Math.min(values.size(),page*size),end=Math.min(values.size(),start+size);
        var paged=HrPageResponse.from(new org.springframework.data.domain.PageImpl<Measurement>(values.subList(start,end),PageRequest.of(page,size),values.size()),v->v);
        return new PublishedResults(content.info(),content.snapshot().person(),content.snapshot().visit(),paged);
    }
    public void checkCurrent(String employee,String id,HrImportActor actor){current(employee,id,actor);}
    private static void page(int page,int size){if(page<0||page>10000||size<1||size>100)throw HrApiException.badRequest("HEALTH_PUBLICATION_PAGE","Phân trang công bố không hợp lệ.");}
    private static HrApiException missing(){return HrApiException.notFound("HEALTH_PUBLICATION_UNAVAILABLE","Phiên bản không còn được phép hiển thị hoặc không thuộc người được chọn.");}
}
