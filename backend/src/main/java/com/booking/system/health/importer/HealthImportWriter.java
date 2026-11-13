package com.booking.system.health.importer;

import com.booking.system.health.api.HrHealthImportDtos.*;
import com.booking.system.health.entity.*;
import com.booking.system.health.repository.*;
import com.booking.system.health.service.*;
import com.booking.system.hr.api.HrApiException;
import com.booking.system.hr.importer.HrImportActor;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;
import static com.booking.system.health.importer.HealthImportSupport.*;

@Service
@RequiredArgsConstructor
public class HealthImportWriter {
    private final HealthImportStaging staging;
    private final HealthImportRepository batches;
    private final HealthImportRowRepository rows;
    private final HealthSubjectRepository subjects;
    private final HealthVisitRepository visits;
    private final HealthResultRepository results;
    private final HealthResultChangeRepository changes;
    private final HealthEmployeeRepository employees;
    private final HealthImportContext loader;
    private final HealthImportPreview preview;
    private final HealthAuditService audit;
    private final HealthAccess access;
    private final EntityManager entityManager;

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public ConfirmResult confirm(String id, ConfirmRequest request, HrImportActor actor) {
        access.requireManager(actor);
        HealthImport batch = locked(id); var campaign = staging.lockCampaign(batch.getCampaignId());
        if (campaign.getStatus()!=HealthTypes.CampaignStatus.OPEN) throw invalid("Đợt khám đã khóa.");
        var decisions = request.rows();
        var ids = decisions.stream().map(RowDecision::rowId).collect(Collectors.toSet());
        if (ids.size()!=decisions.size()) throw invalid("Không chọn lặp dòng xác nhận.");
        var selected = rows.findByBatchIdAndIdIn(id,ids);
        if (selected.size()!=ids.size()) throw invalid("Dòng không thuộc lô nhập đã chọn.");
        var beforeLock = loader.load(batch,selected);
        var employeeIds = selected.stream().filter(r -> r.getPayload()!=null)
                .map(r -> HealthImportContext.match(r,read(r.getPayload(),HealthParsedData.Row.class),beforeLock.employees())).filter(Objects::nonNull).collect(Collectors.toSet());
        if (!employeeIds.isEmpty()) employees.lockEmployees(employeeIds);
        var context = loader.load(batch,selected);
        var byId = decisions.stream().collect(Collectors.toMap(RowDecision::rowId,Function.identity()));
        var valid = validate(batch,selected,byId,context,campaign.getReportYear());
        persist(batch,valid,byId,context,actor);
        updateStatus(batch,actor);
        audit.record("CONFIRM_IMPORT_ROWS",id,actor);
        return new ConfirmResult(staging.summary(batch),valid.size(),selected.size()-valid.size());
    }
    private List<HealthImportRow> validate(HealthImport batch, List<HealthImportRow> selected, Map<String,RowDecision> decisions,
                                          HealthImportContext.State context, int year) {
        if ("CANCELLED".equals(batch.getStatus())) throw invalid("Lô nhập đã hủy.");
        var keys = new HashSet<String>(); var pending = new ArrayList<HealthImportRow>(); int cells=0;
        for (var row : selected) {
            if ("IMPORTED".equals(row.getState())) continue;
            if (!"PENDING".equals(row.getState()) || row.getPayload()==null) throw invalid("Dòng không còn chờ xác nhận.");
            var rendered = preview.row(batch,row,context,year); var decision = decisions.get(row.getId());
            if (!rendered.errors().isEmpty()) throw invalid("Dòng "+row.getSourceRow()+" còn lỗi; hãy kiểm tra trước khi xác nhận.");
            if (!rendered.previewToken().equals(decision.previewToken())) throw HrApiException.conflict("HEALTH_PREVIEW_STALE","Dữ liệu đã thay đổi sau xem trước. Vui lòng tải lại và chọn lại quyết định.");
            var source = read(row.getPayload(),HealthParsedData.Row.class);
            if (!keys.add(HealthImportContext.visitKey(source,rendered.employeeId(),batch.getCampaignId()))) throw invalid("Chỉ chọn một dòng cho cùng lượt khám trong mỗi lần xác nhận.");
            validateChoices(rendered,decision);
            cells += rendered.cells().size(); if (cells>10000) throw invalid("Mỗi lần xác nhận tối đa 10.000 kết quả; hãy chọn ít dòng hơn.");
            pending.add(row);
        }
        return pending;
    }
    private static void validateChoices(PreviewRow row, RowDecision decision) {
        var conflicts = row.cells().stream().filter(c -> c.action().equals("CONFLICT")).map(Cell::key).collect(Collectors.toSet());
        if (!decision.choices().keySet().equals(conflicts) || decision.choices().values().stream().anyMatch(Objects::isNull)) throw invalid("Chọn giữ hoặc thay cho từng ô chênh lệch, không gửi quyết định ngoài phạm vi.");
        if (decision.choices().containsValue(Choice.REPLACE) && (decision.reason()==null || decision.reason().isBlank())) throw invalid("Nhập lý do khi thay kết quả đang lưu.");
    }
    private void persist(HealthImport batch, List<HealthImportRow> selected, Map<String,RowDecision> decisions,
                         HealthImportContext.State context, HrImportActor actor) {
        var subjectMap = new HashMap<>(context.subjects()); var newSubjects = new ArrayList<HealthSubject>();
        for (var row : selected) {
            String employeeId = HealthImportContext.match(row,read(row.getPayload(),HealthParsedData.Row.class),context.employees());
            if (!subjectMap.containsKey(employeeId)) {
                var subject = new HealthSubject(); subject.setEmployeeId(employeeId); HealthAccess.audit(subject,actor,true);
                subjectMap.put(employeeId,subject); newSubjects.add(subject);
            }
        }
        subjects.saveAll(newSubjects); subjects.flush();
        var visitMap = new HashMap<>(context.visits()); var changedVisits = new ArrayList<HealthVisit>();
        for (var row : selected) {
            var source = read(row.getPayload(),HealthParsedData.Row.class);
            String employeeId = HealthImportContext.match(row,source,context.employees());
            String key = HealthImportContext.visitKey(source,employeeId,batch.getCampaignId());
            if (!visitMap.containsKey(key)) {
                var visit = new HealthVisit(); visit.setCampaignId(batch.getCampaignId()); visit.setSubjectId(subjectMap.get(employeeId).getId());
                visit.setSourceNamespace(batch.getSourceNamespace()); visit.setSourceSid(key); visit.setExaminedOn(source.date()); visit.setStatus("REVIEW");
                HealthAccess.audit(visit,actor,true); visitMap.put(key,visit); changedVisits.add(visit);
            }
        }
        visits.saveAll(changedVisits); visits.flush();
        writeResults(batch,selected,decisions,context,visitMap,actor);
    }
    private void writeResults(HealthImport batch, List<HealthImportRow> selected, Map<String,RowDecision> decisions,
                              HealthImportContext.State context, Map<String,HealthVisit> visitMap, HrImportActor actor) {
        var writes = new ArrayList<HealthResult>(); var revisions = new ArrayList<HealthResultChange>();
        var changedVisits = new HashSet<HealthVisit>();
        for (var row : selected) {
            var source = read(row.getPayload(),HealthParsedData.Row.class);
            String employeeId = HealthImportContext.match(row,source,context.employees());
            var visit = visitMap.get(HealthImportContext.visitKey(source,employeeId,batch.getCampaignId()));
            var decision = decisions.get(row.getId());
            for (var value : source.values()) {
                String key = valueKey(value.indicatorId(),value.unit(),value.method());
                var existing = context.results().get(visit.getId()+":"+key);
                if (existing!=null && decision.choices().get(key)!=Choice.REPLACE) continue;
                var result = existing==null ? new HealthResult() : existing;
                var incoming = existing!=null && clean(value.reference()).isBlank()
                        ? new HealthParsedData.Value(value.indicatorId(),value.rawValue(),clean(existing.getReferenceText()),value.unit(),value.method(),value.column(),value.label()) : value;
                if (existing!=null) revisions.add(revision(existing,incoming,batch.getId(),decision.reason(),actor));
                apply(result,incoming,batch,visit,row,actor,existing==null); writes.add(result); changedVisits.add(visit);
            }
            row.setState("IMPORTED"); row.setEmployeeId(employeeId); HealthAccess.audit(row,actor,false);
        }
        results.saveAll(writes); results.flush(); changes.saveAll(revisions);
        changedVisits.forEach(visit -> { visit.setStatus("REVIEW"); HealthAccess.audit(visit,actor,false); });
        visits.saveAll(changedVisits); rows.saveAll(selected); rows.flush();
    }
    private static HealthResultChange revision(HealthResult existing, HealthParsedData.Value value, String batchId, String reason, HrImportActor actor) {
        var change = new HealthResultChange(); change.setResultId(existing.getId()); change.setBatchId(batchId); change.setReason(reason.trim());
        change.setBeforePayload(json(Arrays.asList(existing.getRawValue(),existing.getReferenceText(),existing.getUnit(),existing.getMethod(),
                existing.getImportId(),existing.getSourceSheet(),existing.getSourceRow(),existing.getSourceColumn(),existing.getSourceLabel())));
        change.setAfterPayload(json(value)); HealthAccess.audit(change,actor,true); return change;
    }
    private static void apply(HealthResult result, HealthParsedData.Value value, HealthImport batch, HealthVisit visit, HealthImportRow row, HrImportActor actor, boolean creating) {
        result.setVisitId(visit.getId()); result.setIndicatorId(value.indicatorId()); result.setImportId(batch.getId());
        result.setValueKey(valueKey(value.indicatorId(),value.unit(),value.method())); result.setRawValue(value.rawValue());
        result.setUnit(value.unit()); result.setMethod(value.method()); result.setReferenceText(value.reference());
        result.setSourceSheet(batch.getSourceSheet()); result.setSourceRow(row.getSourceRow()); result.setSourceColumn(value.column()); result.setSourceLabel(value.label());
        HealthAccess.audit(result,actor,creating);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Batch resolve(String batchId, String rowId, ResolveRequest request, HrImportActor actor) {
        access.requireManager(actor); var batch = locked(batchId);
        var row = rows.findById(rowId).orElseThrow(() -> invalid("Không tìm thấy dòng cần ghép."));
        if (!row.getBatchId().equals(batchId) || !row.getState().equals("PENDING")) throw invalid("Chỉ ghép nhân sự cho dòng đang chờ trong lô này.");
        HealthAccess.checkVersion(row,request.rowVersion());
        if (!employees.existsById(request.employeeId())) throw invalid("Không tìm thấy hồ sơ nhân sự được chọn.");
        row.setEmployeeId(request.employeeId()); row.setResolutionReason(request.reason().trim()); HealthAccess.audit(row,actor,false); rows.flush();
        HealthAccess.audit(batch,actor,false); batches.flush(); audit.record("RESOLVE_IMPORT_PERSON",batchId,actor); return staging.summary(batch);
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Batch cancel(String id, VersionRequest request, HrImportActor actor) {
        access.requireManager(actor); var batch = locked(id); HealthAccess.checkVersion(batch,request.rowVersion());
        rows.cancelPending(id); entityManager.flush();
        if (rows.countByBatchIdAndState(id,"IMPORTED")==0) {
            batch.setStatus("CANCELLED"); batch.setSourceHash(hash(batch.getSourceHash()+"|CANCELLED|"+batch.getId()));
        } else batch.setStatus("IMPORTED");
        HealthAccess.audit(batch,actor,false); batches.flush(); audit.record("CANCEL_PENDING_IMPORT_ROWS",id,actor); return staging.summary(batch);
    }
    private HealthImport locked(String id) {
        var batch = staging.requireBatch(id); staging.lockCampaign(batch.getCampaignId());
        entityManager.refresh(batch,LockModeType.PESSIMISTIC_WRITE); return batch;
    }
    private void updateStatus(HealthImport batch, HrImportActor actor) {
        long pending=rows.countByBatchIdAndState(batch.getId(),"PENDING"), imported=rows.countByBatchIdAndState(batch.getId(),"IMPORTED");
        batch.setStatus(pending==0 ? "IMPORTED" : imported>0 ? "PARTIAL" : "REVIEW");
        HealthAccess.audit(batch,actor,false); batches.flush();
    }
}
