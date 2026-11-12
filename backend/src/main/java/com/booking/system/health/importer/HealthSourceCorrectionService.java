package com.booking.system.health.importer;

import com.booking.system.health.api.HrHealthImportDtos.*;
import com.booking.system.health.entity.HealthTypes;
import com.booking.system.health.importer.baseline.HealthClassification;
import com.booking.system.health.repository.*;
import com.booking.system.health.service.*;
import com.booking.system.hr.importer.HrImportActor;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;
import static com.booking.system.health.importer.HealthImportSupport.*;

@Service
@RequiredArgsConstructor
public class HealthSourceCorrectionService {
    private final HealthImportStaging staging;
    private final HealthImportRowRepository rows;
    private final HealthImportRepository batches;
    private final EntityManager entityManager;
    private final HealthAccess access;
    private final HealthAuditService audit;

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Batch correct(String batchId,String rowId,CorrectClassificationRequest request,HrImportActor actor) {
        access.requireManager(actor);var batch=staging.requireBatch(batchId);var campaign=staging.lockCampaign(batch.getCampaignId());
        entityManager.refresh(batch,LockModeType.PESSIMISTIC_WRITE);
        if(campaign.getStatus()!=HealthTypes.CampaignStatus.OPEN) throw invalid("Đợt khám đã khóa.");
        var row=rows.findById(rowId).orElseThrow(() -> invalid("Không tìm thấy dòng cần đính chính."));
        if(!row.getBatchId().equals(batchId)||!row.getState().equals("PENDING")||row.getPayload()==null) throw invalid("Chỉ đính chính phân loại của dòng đang chờ trong lô này.");
        HealthAccess.checkVersion(row,request.rowVersion());
        if(request.reason()==null || request.reason().isBlank()) throw invalid("Nhập lý do đính chính nguồn.");
        String corrected=HealthClassification.normalize(request.value());
        if(corrected==null) throw invalid("Phân loại đính chính phải từ I đến V.");
        var source=read(row.getPayload(),HealthParsedData.Row.class);var history=new ArrayList<>(source.corrections());
        if(history.size()>=20) throw invalid("Dòng đã có 20 đính chính; cần kiểm tra lại nguồn gốc.");
        var original=source.values().stream().filter(v -> valueKey(v.indicatorId(),v.unit(),v.method()).equals(request.key())).findFirst().orElseThrow(() -> invalid("Không tìm thấy ô phân loại."));
        if(!HealthClassification.isClassification(original.label())) throw invalid("Thao tác này chỉ đính chính phân loại sức khỏe.");
        history.add(new HealthParsedData.Correction(request.key(),original.label(),original.rawValue(),corrected,request.reason().trim(),actor.subject(),java.time.Instant.now().toString()));
        var values=source.values().stream().map(v -> v==original ? new HealthParsedData.Value(v.indicatorId(),corrected,v.reference(),v.unit(),v.method(),v.column(),v.label()) : v).toList();
        String payload=json(new HealthParsedData.Row(source.sourceRow(),source.code(),source.name(),source.birth(),source.sid(),source.date(),values,source.errors(),source.warnings(),history));
        if(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>60000) throw invalid("Đính chính vượt giới hạn lưu trữ dòng; cần kiểm tra nguồn.");
        row.setPayload(payload);HealthAccess.audit(row,actor,false);rows.flush();
        HealthAccess.audit(batch,actor,false);batches.flush();audit.record("CORRECT_SOURCE_CLASSIFICATION",batchId,actor);
        return staging.summary(batch);
    }
}
