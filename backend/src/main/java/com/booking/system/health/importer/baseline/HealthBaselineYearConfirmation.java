package com.booking.system.health.importer.baseline;

import com.booking.system.health.entity.*;
import com.booking.system.health.importer.*;
import com.booking.system.health.repository.*;
import com.booking.system.health.service.*;
import com.booking.system.hr.importer.HrImportActor;
import jakarta.persistence.*;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.*;
import static com.booking.system.health.importer.HealthImportSupport.*;

/** Explicit operator confirmation of the historical year, never of an examination date or person. */
@Service
@RequiredArgsConstructor
public class HealthBaselineYearConfirmation {
    public static final String UNVERIFIED_YEAR_ERROR = "Năm kỳ khám từ SID chưa được xác minh; cần xác nhận nguồn trước khi nhập.";
    private final HealthImportStaging staging;
    private final HealthImportRowRepository rows;
    private final HealthImportRepository batches;
    private final HealthAccess access;
    private final HealthAuditService audit;
    private final EntityManager entityManager;

    @Transactional(isolation=Isolation.READ_COMMITTED)
    public int confirm(String batchId,String reason,HrImportActor actor) {
        access.requireManager(actor);
        if(reason==null || reason.isBlank() || reason.length()>500) throw invalid("Cần lý do xác nhận năm khám, tối đa 500 ký tự.");
        var batch=staging.requireBatch(batchId);var campaign=staging.lockCampaign(batch.getCampaignId());
        entityManager.refresh(batch,LockModeType.PESSIMISTIC_WRITE);
        if(!batch.getSourceNamespace().equals("CFC_HISTORY")) throw invalid("Chỉ xác nhận năm cho nguồn baseline lịch sử đã chọn.");
        int year=campaign.getReportYear();
        if(year!=2023 && year!=2024) return 0;
        if(!campaign.getCode().equals("HISTORY_"+year) || campaign.getStatus()!=HealthTypes.CampaignStatus.OPEN) throw invalid("Đợt lịch sử không phù hợp hoặc đã khóa.");
        var changed=new ArrayList<HealthImportRow>();
        for(int page=0;page<25;page++) {
            var values=rows.findByBatchIdOrderBySourceRow(batchId,PageRequest.of(page,100));
            if(values.getTotalElements()>MAX_ROWS) throw invalid("Lô lịch sử vượt giới hạn dòng.");
            for(var row:values) if("PENDING".equals(row.getState()) && row.getPayload()!=null) {
                var source=read(row.getPayload(),HealthParsedData.Row.class);
                if(source.errors().contains(UNVERIFIED_YEAR_ERROR)) {
                    row.setPayload(confirmedPayload(source,year,reason,actor));HealthAccess.audit(row,actor,false);changed.add(row);
                }
            }
            if(!values.hasNext()) break;
        }
        if(!changed.isEmpty()) {
            rows.saveAll(changed);rows.flush();HealthAccess.audit(batch,actor,false);batches.flush();
            audit.record("CONFIRM_BASELINE_SOURCE_YEAR",batchId,actor);
        }
        return changed.size();
    }
    private static String confirmedPayload(HealthParsedData.Row source,int year,String reason,HrImportActor actor) {
        var hint=HealthWorkbookParser.sidDate(source.sid());
        if(hint==null || hint.getYear()!=year || (source.date()!=null && source.date().getYear()!=year)) throw invalid("Năm SID không khớp đợt; không tự chuyển năm hoặc tạo ngày khám.");
        if(source.corrections().size()>=20) throw invalid("Dòng đã có 20 xác nhận/đính chính; cần kiểm tra nguồn.");
        var history=new ArrayList<>(source.corrections());
        history.add(new HealthParsedData.Correction("BASELINE_SOURCE_YEAR","Năm khám","Chưa xác minh",Integer.toString(year),reason.trim(),actor.subject(),Instant.now().toString()));
        String payload=json(new HealthParsedData.Row(source.sourceRow(),source.code(),source.name(),source.birth(),source.sid(),source.date(),source.values(),
            source.errors().stream().filter(error -> !error.equals(UNVERIFIED_YEAR_ERROR)).toList(),source.warnings(),history));
        if(payload.getBytes(StandardCharsets.UTF_8).length>60000) throw invalid("Xác nhận năm vượt giới hạn lưu trữ dòng.");
        return payload;
    }
}
