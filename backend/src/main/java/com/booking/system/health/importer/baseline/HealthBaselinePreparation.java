package com.booking.system.health.importer.baseline;

import com.booking.system.health.api.HrHealthImportDtos.*;
import com.booking.system.health.importer.*;
import com.booking.system.health.repository.*;
import com.booking.system.health.service.HealthAccess;
import com.booking.system.hr.importer.HrImportActor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import java.util.*;
import java.util.stream.Collectors;
import static com.booking.system.health.importer.HealthImportSupport.*;
import static com.booking.system.health.importer.baseline.HealthBaselineData.*;

@Service
@RequiredArgsConstructor
public class HealthBaselinePreparation {
    private final HealthBaselineCatalog catalog;
    private final HealthImportStaging staging;
    private final HealthImportRepository batches;
    private final HealthEmployeeRepository employees;
    private final HealthAccess access;
    public record Prepared(Report report,List<Batch> batches,int unassignedYearRows,Policy policy,List<String> primaryBatchIds) { }

    public List<Identity> identities() {
        access.requireEnabled();var identities=new ArrayList<Identity>();
        for(int page=0;page<100;page++) {
            var result=employees.findAll(PageRequest.of(page,200));
            result.forEach(e -> identities.add(new Identity(e.getId(),e.getEmployeeCode(),e.getFullName(),e.getDateOfBirth()==null ? "" : e.getDateOfBirth().toString())));
            if(!result.hasNext()) return List.copyOf(identities);
        }
        throw invalid("Danh sách nhân sự vượt 20.000 người; cần giới hạn phạm vi baseline.");
    }
    // Reading workbooks and generating the report happen before the catalog/staging transaction beans.
    public Prepared prepare(Dataset dataset,Report report,String namespace,Policy policy,boolean sidYearsConfirmed,HrImportActor actor) {
        access.requireManager(actor);
        if(namespace==null || namespace.isBlank() || namespace.length()>100 || policy==null) throw invalid("Cần mã nguồn baseline và chính sách đối chiếu rõ ràng.");
        var targets=catalog.prepare(dataset.indicatorLabels(),actor);var created=new ArrayList<Batch>();var primaryIds=new ArrayList<String>();int unknown=0;
        var sources=new ArrayList<>(dataset.sources());
        if(policy==Policy.TOTAL_FIRST) sources.sort(Comparator.comparingInt(s -> normalized(s.sheet()).equals("tong hop") ? 0 : s.kind()==Kind.RESULTS_2025 ? 1 : 2));
        for(var source:sources) if(!source.taskList()) {
            var groups=source.rows().stream().collect(Collectors.groupingBy(row -> HealthBaselineAnalyzer.suggestedYear(source.kind(),row),TreeMap::new,Collectors.toList()));
            for(var group:groups.entrySet()) {
                Integer year=group.getKey();String campaignId=targets.campaigns().get(year);
                if(campaignId==null) {unknown+=group.getValue().size();continue;}
                String sourceHash=hash(source.fileHash()+"|"+source.sheet());
                var previous=batches.findByCampaignIdAndSourceHash(campaignId,sourceHash);
                if(previous.isPresent()) {created.add(staging.summary(previous.get()));if(primary(source)) primaryIds.add(previous.get().getId());continue;}
                var mapping=source.mapping().entrySet().stream().collect(Collectors.toMap(Map.Entry::getKey,e -> remap(e.getValue(),targets.indicators())));
                var request=new StageRequest(campaignId,source.sheet(),source.headerRow(),1,namespace,false,null,mapping,null);
                var rows=group.getValue().stream().map(row -> remap(row,targets.indicators(),source.kind(),year,sidYearsConfirmed)).toList();
                String fingerprint=hash(json(source.mapping()));
                var batch=staging.stage(new HealthParsedData.Parsed(source.fileHash(),fingerprint,request,rows),actor);created.add(batch);if(primary(source)) primaryIds.add(batch.id());
            }
        }
        return new Prepared(report,List.copyOf(created),unknown,policy,List.copyOf(primaryIds));
    }
    private static boolean primary(Source source) {return normalized(source.sheet()).equals("tong hop") || source.kind()==Kind.RESULTS_2025;}
    private static Mapping remap(Mapping mapping,Map<String,String> targets) {
        return new Mapping(mapping.field(),mapping.indicatorId()==null ? null : targets.get(mapping.indicatorId()),mapping.unit(),mapping.method());
    }
    private static HealthParsedData.Row remap(HealthParsedData.Row row,Map<String,String> targets,Kind kind,int year,boolean yearsConfirmed) {
        var errors=new ArrayList<>(row.errors());var warnings=new ArrayList<>(row.warnings());
        if(kind==Kind.HISTORY_2023_2024 && !yearsConfirmed) errors.add(HealthBaselineYearConfirmation.UNVERIFIED_YEAR_ERROR);
        if((kind==Kind.HISTORY_2023_2024 && year!=2023 && year!=2024)||(kind==Kind.RESULTS_2025 && year!=2025)||(kind==Kind.SUPPLEMENT_2024 && year!=2024)) errors.add("Năm gợi ý không thuộc phạm vi workbook lịch sử đã chọn.");
        var values=row.values().stream().map(value -> {
            if(value.label().isBlank()) errors.add("Cột "+value.column()+" chưa có tiêu đề; cần ánh xạ lại trước khi nhập.");
            if(HealthClassification.isClassification(value.label())) {
                String normalized=HealthClassification.normalize(value.rawValue());
                if(normalized!=null && !normalized.equals(value.rawValue())) warnings.add("Phân loại hiển thị chuẩn: "+normalized+"; vẫn giữ giá trị gốc.");
            }
            return new HealthParsedData.Value(targets.get(value.indicatorId()),value.rawValue(),value.reference(),value.unit(),value.method(),value.column(),value.label());
        }).toList();
        return new HealthParsedData.Row(row.sourceRow(),row.code(),row.name(),row.birth(),row.sid(),row.date(),values,errors,warnings);
    }
}
