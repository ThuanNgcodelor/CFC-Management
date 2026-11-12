package com.booking.system.health.importer;

import com.booking.system.health.api.HrHealthImportDtos.*;
import com.booking.system.health.entity.*;
import com.booking.system.health.repository.*;
import com.booking.system.health.service.*;
import com.booking.system.hr.api.HrApiException;
import com.booking.system.hr.api.dto.HrPageResponse;
import com.booking.system.hr.importer.HrImportActor;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.util.*;
import java.util.stream.Collectors;
import static com.booking.system.health.importer.HealthImportSupport.*;

@Service
@RequiredArgsConstructor
public class HealthImportStaging {
    private final HealthCampaignRepository campaigns;
    private final HealthImportRepository batches;
    private final HealthImportRowRepository rows;
    private final HealthMappingRepository profiles;
    private final HealthAuditService audit;
    private final HealthAccess access;

    @Transactional
    public Batch stage(HealthParsedData.Parsed parsed, HrImportActor actor) {
        access.requireManager(actor);
        var request = parsed.request(); var campaign = lockCampaign(request.campaignId());
        if (campaign.getStatus()!=HealthTypes.CampaignStatus.OPEN) throw invalid("Đợt khám đã khóa.");
        String sourceHash = hash(parsed.fileHash()+"|"+request.sheet());
        if (batches.findByCampaignIdAndSourceHash(campaign.getId(),sourceHash).isPresent()) throw HrApiException.conflict("HEALTH_FILE_DUPLICATE", "File/sheet này đã được đưa vào đợt khám. Hãy tiếp tục lô đang có.");
        String mapping = json(request);
        if (mapping.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>60000) throw invalid("Cấu hình ánh xạ quá lớn.");
        var batch = new HealthImport(); batch.setCampaignId(campaign.getId()); batch.setSourceHash(sourceHash);
        batch.setSourceSheet(request.sheet()); batch.setSourceNamespace(request.sourceNamespace().trim().toUpperCase(Locale.ROOT));
        batch.setFileHash(parsed.fileHash()); batch.setMappingJson(mapping); batch.setTotalRows(parsed.rows().size()); batch.setStatus("REVIEW");
        HealthAccess.audit(batch,actor,true); batches.saveAndFlush(batch);
        var duplicates = parsed.rows().stream().filter(r -> !r.sid().isBlank()).collect(Collectors.groupingBy(HealthParsedData.Row::sid, Collectors.counting()));
        var staged = parsed.rows().stream().map(source -> staged(batch,source,duplicates,actor)).toList();
        rows.saveAll(staged); rows.flush();
        if (request.profileName()!=null && !request.profileName().isBlank()) {
            var profile = new HealthMappingProfile(); profile.setName(request.profileName().trim());
            profile.setFingerprint(parsed.fingerprint()); profile.setMappingJson(mapping); HealthAccess.audit(profile,actor,true); profiles.save(profile);
        }
        audit.record("STAGE_IMPORT",batch.getId(),actor);
        return summary(batch);
    }
    private HealthImportRow staged(HealthImport batch, HealthParsedData.Row source, Map<String,Long> duplicates, HrImportActor actor) {
        var errors = new ArrayList<>(source.errors());
        if (duplicates.getOrDefault(source.sid(),0L)>1) errors.add("SID lặp trong cùng sheet; cần sửa file hoặc chọn nguồn đúng.");
        var payload = json(new HealthParsedData.Row(source.sourceRow(),source.code(),source.name(),source.birth(),source.sid(),source.date(),source.values(),errors,source.warnings()));
        if (payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>60000) throw invalid("Dòng "+source.sourceRow()+" có dữ liệu quá lớn; hãy tách file.");
        var row = new HealthImportRow(); row.setBatchId(batch.getId()); row.setSourceRow(source.sourceRow()); row.setPayload(payload);
        row.setState("PENDING"); HealthAccess.audit(row,actor,true); return row;
    }
    public HealthCampaign lockCampaign(String id) {
        return campaigns.lockById(id).orElseThrow(() -> HrApiException.notFound("HEALTH_CAMPAIGN_NOT_FOUND","Không tìm thấy đợt khám."));
    }
    public HealthImport requireBatch(String id) {
        return batches.findById(id).orElseThrow(() -> HrApiException.notFound("HEALTH_IMPORT_NOT_FOUND","Không tìm thấy lô nhập sức khỏe."));
    }
    public Batch summary(HealthImport value) {
        return new Batch(value.getId(),value.getCampaignId(),value.getSourceSheet(),value.getStatus(),value.getTotalRows(),
                rows.countByBatchIdAndState(value.getId(),"IMPORTED"),rows.countByBatchIdAndState(value.getId(),"PENDING"),value.getRowVersion());
    }
    @Transactional(readOnly=true)
    public HrPageResponse<Batch> list(String campaignId, int page, int size) {
        access.requireEnabled(); checkPage(page,size);
        // Counts are grouped once instead of querying for every batch.
        var result = batches.findByCampaignIdOrderByCreatedAtDesc(campaignId,PageRequest.of(page,size));
        var ids = result.map(HealthImport::getId).getContent();
        var counts = ids.isEmpty() ? List.<HealthImportRowRepository.Counts>of() : rows.countStates(ids);
        return HrPageResponse.from(result,b -> new Batch(b.getId(),b.getCampaignId(),b.getSourceSheet(),b.getStatus(),b.getTotalRows(),
                count(counts,b.getId(),"IMPORTED"),count(counts,b.getId(),"PENDING"),b.getRowVersion()));
    }
    private static long count(List<HealthImportRowRepository.Counts> counts, String id, String state) {
        return counts.stream().filter(c -> c.getBatchId().equals(id) && c.getState().equals(state)).mapToLong(HealthImportRowRepository.Counts::getTotal).sum();
    }
    public static void checkPage(int page, int size) {
        if (page<0 || page>10000 || size<1 || size>100) throw invalid("Trang không hợp lệ; tối đa 100 dòng mỗi trang.");
    }
}
