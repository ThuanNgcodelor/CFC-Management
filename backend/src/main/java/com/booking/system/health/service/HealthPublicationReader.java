package com.booking.system.health.service;

import com.booking.system.health.api.HrHealthPublicationDtos.*;
import com.booking.system.health.api.HrHealthRecordDtos.Person;
import com.booking.system.health.entity.HealthCampaign;
import com.booking.system.health.repository.*;
import com.booking.system.hr.api.HrApiException;
import com.booking.system.hr.api.dto.HrPageResponse;
import com.booking.system.hr.importer.HrImportActor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
public class HealthPublicationReader {
    private final HealthAccess access;
    private final HealthCampaignRepository campaigns;
    private final HealthPublicationReadRepository publications;
    private final HealthPublicationDrafts drafts;
    public HrPageResponse<PersonPreview> candidates(Scope scope,int page,int size,HrImportActor actor) {
        access.requireManager(actor);validate(scope);if(page<0||page>10000||size<1||size>20)throw invalid("Phân trang công bố từ 1 đến 20 người.");
        var campaign=campaign(scope.campaignId());var people=publications.people(scope,HealthRecordService.pattern(scope.keyword()),PageRequest.of(page,size));
        var values=rows(campaign,people.getContent());var byId=new HashMap<String,PersonPreview>();values.forEach(v->byId.put(v.employeeId(),v));
        return HrPageResponse.from(people,p->byId.get(p.employeeId()));
    }
    public Preview preview(Scope scope,HrImportActor actor) {
        access.requireManager(actor);validate(scope);var campaign=campaign(scope.campaignId());
        var people=publications.people(scope,HealthRecordService.pattern(scope.keyword()),PageRequest.of(0,201));
        if(people.getTotalElements()>200)throw invalid("Xem trước tối đa 200 người; hãy thu hẹp bộ lọc.");
        if(scope.employeeIds()!=null&&people.getTotalElements()!=new HashSet<>(scope.employeeIds()).size())throw invalid("Có người không còn trong đợt/bộ lọc. Hãy tải lại danh sách.");
        var rows=rows(campaign,people.getContent());var visits=rows.stream().flatMap(v->v.visits().stream()).toList();int ready=(int)visits.stream().filter(Candidate::publishable).count();
        return new Preview(HealthPublicationDrafts.campaign(campaign),rows.size(),visits.size(),ready,visits.size()-ready,rows);
    }
    private List<PersonPreview> rows(HealthCampaign campaign,List<Person> people) {
        var loaded=drafts.load(campaign,people);return people.stream().map(p->new PersonPreview(p.employeeId(),p.code(),p.name(),loaded.values().stream().filter(d->d.person().employeeId().equals(p.employeeId())).map(HealthPublicationDrafts.Draft::candidate).toList())).toList();
    }
    private HealthCampaign campaign(String id){return campaigns.findById(id).orElseThrow(()->invalid("Không tìm thấy đợt khám."));}
    private static void validate(Scope scope){if(scope.employeeIds()!=null&&new HashSet<>(scope.employeeIds()).size()!=scope.employeeIds().size())throw invalid("Không chọn lặp nhân viên.");}
    private static HrApiException invalid(String message){return HrApiException.badRequest("HEALTH_PUBLICATION_SCOPE_INVALID",message);}
}
