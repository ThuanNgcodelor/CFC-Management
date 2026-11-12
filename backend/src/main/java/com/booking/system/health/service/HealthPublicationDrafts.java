package com.booking.system.health.service;

import com.booking.system.health.api.HrHealthPublicationDtos.*;
import com.booking.system.health.api.HrHealthRecordDtos.*;
import com.booking.system.health.entity.*;
import com.booking.system.health.repository.*;
import com.booking.system.hr.api.HrApiException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import static com.booking.system.health.importer.HealthImportSupport.*;

@Component
@RequiredArgsConstructor
public class HealthPublicationDrafts {
    public static final int MAX_VISITS=500,MAX_RESULTS=10000,MAX_SNAPSHOT_BYTES=2*1024*1024;
    private final HealthPublicationReadRepository publications;
    private final HealthSubjectRepository subjects;
    private final HealthReportRepository reports;
    public record Draft(Person person,HealthVisit entity,PublishedSnapshot body,String hash,Candidate candidate) {
        @Override public String toString(){return "HealthPublicationDraft[redacted]";}
    }
    public Map<String,Draft> load(HealthCampaign campaign,List<Person> people) {
        var ids=people.stream().map(Person::employeeId).toList();var visits=publications.visits(campaign.getId(),ids);
        if(visits.size()>MAX_VISITS)throw invalid("Mỗi lần xem trước tối đa 500 lượt. Hãy thu hẹp bộ lọc.");
        var owners=ids.isEmpty()?Map.<String,String>of():subjects.findByEmployeeIdIn(ids).stream().collect(Collectors.toMap(HealthSubject::getId,HealthSubject::getEmployeeId));
        var persons=people.stream().collect(Collectors.toMap(Person::employeeId,Function.identity()));
        var visitIds=visits.stream().map(HealthVisit::getId).toList();var measurements=reports.results(visitIds,MAX_RESULTS+1);
        if(measurements.size()>MAX_RESULTS)throw invalid("Mỗi lần xem trước tối đa 10.000 kết quả. Hãy thu hẹp bộ lọc.");
        var values=measurements.stream().collect(Collectors.groupingBy(Measurement::visitId));
        var latest=publications.latest(visitIds).stream().collect(Collectors.toMap(PublicationInfo::visitId,Function.identity()));
        var drafts=new LinkedHashMap<String,Draft>();
        for(var visit:visits){var person=persons.get(owners.get(visit.getSubjectId()));drafts.put(visit.getId(),draft(campaign,person,visit,values.getOrDefault(visit.getId(),List.of()),latest.get(visit.getId())));}
        return drafts;
    }
    private Draft draft(HealthCampaign campaign,Person source,HealthVisit visit,List<Measurement> values,PublicationInfo latest) {
        var person=new Person(source.employeeId(),source.code(),source.name(),source.birthDate(),source.currentGroup(),1L,campaign.getReportYear());
        var classifications=values.stream().filter(v->"HEALTH_CLASSIFICATION".equals(v.code())).map(Measurement::raw).filter(v->v!=null&&!v.isBlank()).distinct().toList();
        var view=new Visit(visit.getId(),campaign.getReportYear(),campaign.getName(),campaign.getProvider(),visit.getExaminedOn(),"CONFIRMED",visit.getSourceNamespace(),visit.getSourceSid(),(long)values.size(),classifications.isEmpty()?null:classifications.size()==1?classifications.getFirst():"Nhiều giá trị — cần rà soát");
        var body=new PublishedSnapshot(1,person,view,List.copyOf(values),true,null);String payload=json(body);String contentHash=hash(payload);
        var blockers=new ArrayList<String>();var warnings=new ArrayList<String>();
        if(clean(person.code()).isBlank()||clean(person.name()).isBlank())blockers.add("Thiếu mã hoặc tên nhân viên đã liên kết.");
        if(values.isEmpty())blockers.add("Chưa có kết quả đã nhập.");
        if(values.stream().anyMatch(v->clean(v.raw()).isBlank()))blockers.add("Có kết quả trống; cần kiểm tra nguồn trước khi công bố.");
        if(visit.getExaminedOn()!=null&&visit.getExaminedOn().getYear()!=campaign.getReportYear())blockers.add("Ngày khám không thuộc năm của đợt; cần xác minh nguồn.");
        if(values.size()>1000||payload.getBytes(StandardCharsets.UTF_8).length>MAX_SNAPSHOT_BYTES)blockers.add("Vượt 1.000 kết quả hoặc 2 MiB cho một phiên bản; cần chia đúng lượt khám.");
        if(classifications.size()>1)blockers.add("Phân loại có nhiều giá trị mâu thuẫn.");
        if(classifications.stream().anyMatch(v->com.booking.system.health.importer.baseline.HealthClassification.normalize(v)==null))blockers.add("Phân loại chưa thuộc I–V; cần rà soát/đính chính nguồn.");
        warnings.add("Chưa xác nhận đủ gói khám bắt buộc; bản công bố được gắn nhãn dữ liệu chưa đầy đủ.");
        if(visit.getExaminedOn()==null)warnings.add("Chưa có ngày khám; chỉ hiển thị năm đã xác nhận.");
        if(classifications.isEmpty())warnings.add("Chưa có phân loại sức khỏe theo nguồn.");
        if(values.stream().anyMatch(v->clean(v.method()).isBlank()||clean(v.reference()).isBlank()))warnings.add("Có kết quả thiếu phương pháp hoặc tham chiếu; giữ nguyên nguồn.");
        boolean reviewed="CONFIRMED".equals(visit.getStatus())&&contentHash.equals(visit.getReviewedHash())&&visit.isAcknowledgedIncomplete();
        String token=hash(contentHash+"|"+visit.getRowVersion()+"|"+campaign.getRowVersion()+"|"+(latest==null?"none":latest.id()+":"+latest.rowVersion()+":"+latest.status()));
        String state=latest==null?"UNPUBLISHED":"REVOKED".equals(latest.status())?"REVOKED":contentHash.equals(latest.contentHash())?"PUBLISHED":"CHANGED";
        return new Draft(person,visit,body,contentHash,new Candidate(visit.getId(),visit.getSourceSid(),visit.getRowVersion(),token,state,values.size(),reviewed,reviewed&&blockers.isEmpty(),List.copyOf(blockers),List.copyOf(warnings),latest));
    }
    public static Campaign campaign(HealthCampaign c){return new Campaign(c.getId(),c.getName(),c.getReportYear(),c.getStatus().name(),c.getRowVersion());}
    private static HrApiException invalid(String message){return HrApiException.badRequest("HEALTH_PUBLICATION_LIMIT",message);}
}
