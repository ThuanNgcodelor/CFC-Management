package com.booking.system.health.service;

import com.booking.system.health.api.HrHealthRecordDtos.*;
import com.booking.system.health.repository.HealthRecordRepository;
import com.booking.system.hr.api.HrApiException;
import com.booking.system.hr.api.dto.HrPageResponse;
import com.booking.system.hr.enums.HrWorkforceGroup;
import com.booking.system.hr.importer.HrImportActor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import static com.booking.system.health.importer.HealthImportSupport.invalid;

@Service
@RequiredArgsConstructor
@Transactional(readOnly=true)
public class HealthRecordService {
    private final HealthRecordRepository records;
    private final HealthAccess access;
    private final HealthRecordComparison comparison;
    private static final Map<String,String> SORT=Map.of("codeAsc","e.employeeCode asc","codeDesc","e.employeeCode desc","nameAsc","e.fullName asc","nameDesc","e.fullName desc");

    public HrPageResponse<Person> people(String keyword,Integer year,HrWorkforceGroup group,String data,String sort,int page,int size,HrImportActor actor) {
        access.requireManager(actor);checkPage(page,size);if(year!=null)checkYear(year);
        if(!Set.of("ALL","WITH_DATA","WITHOUT_DATA").contains(data) || !SORT.containsKey(sort)) throw invalid("Bộ lọc/sắp xếp hồ sơ sức khỏe không hợp lệ.");
        return HrPageResponse.from(records.people(pattern(keyword),year,group,data,SORT.get(sort),PageRequest.of(page,size)),value->value);
    }
    public Profile timeline(String employeeId,int from,int to,int page,int size,HrImportActor actor) {
        access.requireManager(actor);checkPage(page,size);checkYear(from);checkYear(to);
        if(to<from || to-from>9)throw invalid("Chọn khoảng tối đa 10 năm, từ năm không lớn hơn đến năm.");
        var person=person(employeeId);var counts=new HashMap<Integer,Long>();records.years(employeeId,from,to).forEach(y->counts.put(y.year(),y.visits()));
        var years=new ArrayList<YearCount>();for(int year=from;year<=to;year++)years.add(new YearCount(year,counts.getOrDefault(year,0L)));
        var visits=records.timeline(employeeId,from,to,PageRequest.of(page,size));var classifications=classifications(visits.map(Visit::id).getContent());
        return new Profile(person,HrPageResponse.from(visits,v->classified(v,classifications.get(v.id()))),List.copyOf(years));
    }
    public Results results(String employeeId,String visitId,String keyword,int page,int size,HrImportActor actor) {
        access.requireManager(actor);checkPage(page,size);var visit=owned(employeeId,List.of(visitId)).getFirst();
        return new Results(visit,HrPageResponse.from(records.results(visitId,pattern(keyword),PageRequest.of(page,size)),v->v));
    }
    public Comparison compare(String employeeId,CompareRequest request,HrImportActor actor) {
        access.requireManager(actor);checkPage(request.page(),request.size());var ids=request.visitIds();
        if(ids==null || ids.size()<2 || ids.size()>6 || new HashSet<>(ids).size()!=ids.size()) throw invalid("Chọn 2–6 lượt khám khác nhau của cùng một người.");
        var visits=owned(employeeId,ids);var keys=records.comparisonKeys(ids,pattern(request.keyword()),PageRequest.of(request.page(),request.size()));
        var values=records.comparisonValues(ids,keys.map(IndicatorKey::key).getContent());
        return new Comparison(visits,HrPageResponse.from(keys,key->comparison.row(key,visits,values)));
    }
    private Person person(String id){return records.person(id).orElseThrow(HealthRecordService::missing);}
    private List<Visit> owned(String employeeId,List<String> ids){var values=records.ownedVisits(employeeId,ids);if(values.size()!=ids.size())throw missing();return values;}
    private Map<String,String> classifications(List<String> ids){
        var values=new HashMap<String,Set<String>>();records.classifications(ids).forEach(c->values.computeIfAbsent(c.visitId(),key->new HashSet<>()).add(c.raw()));
        var result=new HashMap<String,String>();values.forEach((key,items)->result.put(key,items.size()==1?items.iterator().next():"Nhiều giá trị — cần rà soát"));return result;
    }
    private static Visit classified(Visit v,String classification){return new Visit(v.id(),v.year(),v.campaign(),v.provider(),v.examinedOn(),v.status(),v.namespace(),v.sid(),v.resultCount(),classification);}
    private static HrApiException missing(){return HrApiException.notFound("HEALTH_RECORD_NOT_FOUND","Không tìm thấy hồ sơ hoặc lượt khám trong phạm vi người đã chọn.");}
    public static String pattern(String keyword){String value=keyword==null?"":keyword.strip();if(value.length()>100)throw invalid("Từ khóa tối đa 100 ký tự.");return "%"+value.toLowerCase(Locale.ROOT).replace("!","!!").replace("%","!%").replace("_","!_")+"%";}
    private static void checkPage(int page,int size){if(page<0||page>10000||size<1||size>100)throw invalid("Trang không hợp lệ; tối đa 100 dòng mỗi trang.");}
    private static void checkYear(int year){if(year<1900||year>2200)throw invalid("Năm phải từ 1900 đến 2200.");}
}
