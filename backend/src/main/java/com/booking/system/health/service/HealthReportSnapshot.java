package com.booking.system.health.service;

import com.booking.system.health.api.HrHealthRecordDtos.*;
import com.booking.system.health.api.HrHealthReportDtos.*;
import com.booking.system.health.repository.*;
import com.booking.system.hr.api.HrApiException;
import com.booking.system.hr.importer.HrImportActor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.nio.charset.StandardCharsets;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HealthReportSnapshot {
    public static final int MAX_PEOPLE=500,MAX_VISITS=2000,MAX_RESULTS=20000,MAX_TEXT_BYTES=16*1024*1024;
    public static final int MAX_COMPARISON_CELLS=60000;
    private final HealthAccess access;
    private final HealthReportRepository reports;
    private final HealthRecordRepository records;
    private final HealthRecordComparison comparison;

    @Transactional(readOnly=true,isolation=Isolation.REPEATABLE_READ)
    public Snapshot read(Request scope,HrImportActor actor) {
        access.requireManager(actor);validate(scope);
        if(scope.employeeId()!=null&&records.person(scope.employeeId()).isEmpty())throw HrApiException.notFound("HEALTH_PERSON_NOT_FOUND","Không tìm thấy hồ sơ nhân sự.");
        var people=reports.people(scope,HealthRecordService.pattern(scope.keyword()),MAX_PEOPLE+1);limit(people.size(),MAX_PEOPLE);
        var visits=reports.visits(people.stream().map(Person::employeeId).toList(),scope,MAX_VISITS+1);limit(visits.size(),MAX_VISITS);
        if(scope.visitIds()!=null&&!scope.visitIds().isEmpty()&&visits.size()!=scope.visitIds().size())throw HrApiException.notFound("HEALTH_VISIT_NOT_FOUND","Lượt khám không thuộc người hoặc khoảng năm đã chọn.");
        var results=reports.results(visits.stream().map(v->v.visit().id()).toList(),MAX_RESULTS+1);limit(results.size(),MAX_RESULTS);checkText(results);
        visits=classified(visits,results);
        return new Snapshot(scope,List.copyOf(people),visits,results,compare(scope,visits,results),HealthReportStatistics.summarize(scope,people,visits,results));
    }
    private static void validate(Request scope) {
        if(scope.fromYear()<1900||scope.toYear()>2200||scope.toYear()<scope.fromYear()||scope.toYear()-scope.fromYear()>9)throw invalid("Chọn khoảng tối đa 10 năm từ 1900 đến 2200.");
        if(!Set.of("ALL","WITH_DATA","WITHOUT_DATA").contains(scope.data())||!Set.of("codeAsc","codeDesc","nameAsc","nameDesc").contains(scope.sort()))throw invalid("Bộ lọc báo cáo không hợp lệ.");
        if(scope.employeeId()!=null&&scope.employeeId().isBlank())throw invalid("Mã hồ sơ không hợp lệ.");
        var ids=scope.visitIds();
        if(ids!=null&&!ids.isEmpty()&&(scope.employeeId()==null||ids.size()>6||new HashSet<>(ids).size()!=ids.size()))throw invalid("Chỉ chọn tối đa 6 lượt khác nhau của một người.");
    }
    private List<ComparisonRow> compare(Request scope,List<VisitRow> visits,List<Measurement> values) {
        var visitsByPerson=visits.stream().collect(Collectors.groupingBy(VisitRow::employeeId,LinkedHashMap::new,Collectors.toList()));
        var valuesByVisit=values.stream().collect(Collectors.groupingBy(Measurement::visitId));
        var rows=new ArrayList<ComparisonRow>();long cells=0;
        for(var person:visitsByPerson.values()) {
            if(person.size()<2||person.size()>6)continue;
            var keys=new LinkedHashMap<String,IndicatorKey>();var grouped=new HashMap<String,List<Measurement>>();
            for(var visit:person)for(var v:valuesByVisit.getOrDefault(visit.visit().id(),List.of())){keys.putIfAbsent(v.key(),new IndicatorKey(v.key(),v.indicatorId(),v.code(),v.label(),v.category(),v.type(),v.unit(),v.method()));grouped.computeIfAbsent(v.key(),key->new ArrayList<>()).add(v);}
            cells+=(long)keys.size()*person.size();if(cells>MAX_COMPARISON_CELLS)throw invalid("Vượt 60.000 ô so sánh. Hãy thu hẹp khoảng năm hoặc nhóm.");
            var ordered=person.stream().map(VisitRow::visit).toList();
            keys.values().forEach(key->rows.add(comparison.row(key,ordered,grouped.get(key.key()))));
        }
        return List.copyOf(rows);
    }
    private static List<VisitRow> classified(List<VisitRow> visits,List<Measurement> values) {
        var classes=values.stream().filter(v->"HEALTH_CLASSIFICATION".equals(v.code())&&v.raw()!=null&&!v.raw().isBlank()).collect(Collectors.groupingBy(Measurement::visitId,Collectors.mapping(Measurement::raw,Collectors.toSet())));
        return visits.stream().map(row->{var v=row.visit();var items=classes.getOrDefault(v.id(),Set.of());String label=items.isEmpty()?null:items.size()==1?items.iterator().next():"Nhiều giá trị — cần rà soát";
            return new VisitRow(row.employeeId(),new Visit(v.id(),v.year(),v.campaign(),v.provider(),v.examinedOn(),v.status(),v.namespace(),v.sid(),v.resultCount(),label));}).toList();
    }
    private static void checkText(List<Measurement> values) {
        long bytes=0;
        for(var v:values)for(String text:new String[]{v.raw(),v.label(),v.reference(),v.unit(),v.method(),v.sourceLabel(),v.sourceSheet()})if(text!=null){
            if(text.length()>32767)throw invalid("Có ô nguồn dài hơn giới hạn Excel; hãy thu hẹp phạm vi hoặc rà soát nguồn, không tự cắt kết quả.");
            bytes+=text.getBytes(StandardCharsets.UTF_8).length;if(bytes>MAX_TEXT_BYTES)throw invalid("Nội dung quá lớn cho một lần xuất. Hãy thu hẹp khoảng năm hoặc nhóm.");
        }
    }
    private static void limit(int size,int max){if(size>max)throw invalid("Vượt giới hạn xuất "+MAX_PEOPLE+" người / "+MAX_VISITS+" lượt / "+MAX_RESULTS+" kết quả. Hãy thu hẹp bộ lọc.");}
    private static HrApiException invalid(String message){return HrApiException.badRequest("HEALTH_REPORT_SCOPE_INVALID",message);}
}
