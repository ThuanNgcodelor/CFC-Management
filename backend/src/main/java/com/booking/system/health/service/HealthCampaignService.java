package com.booking.system.health.service;

import com.booking.system.health.api.HrHealthDtos.*;
import com.booking.system.health.entity.HealthCampaign;
import com.booking.system.health.entity.HealthTypes.*;
import com.booking.system.health.repository.HealthCampaignRepository;
import com.booking.system.hr.api.HrApiException;
import com.booking.system.hr.api.dto.HrPageResponse;
import com.booking.system.hr.importer.HrImportActor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HealthCampaignService {
    private final HealthCampaignRepository campaigns;
    private final HealthAuditService audit;
    private final HealthAccess access;

    @Transactional(readOnly = true)
    public HrPageResponse<CampaignResponse> list(String keyword, Integer year, WorkforceGroup group, int page, int size) {
        access.requireEnabled();
        var spec = HealthQueries.<HealthCampaign>keyword(keyword);
        if (year != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("reportYear"), year));
        if (group != null) spec = spec.and((root, query, cb) -> cb.equal(root.get("workforceGroup"), group));
        return HrPageResponse.from(campaigns.findAll(spec, HealthQueries.page(page, size, true)), HealthCampaignService::response);
    }
    @Transactional
    public CampaignResponse create(CampaignRequest request, HrImportActor actor) {
        access.requireManager(actor);
        if (campaigns.existsByCode(HealthQueries.code(request.code()))) throw HrApiException.conflict("HEALTH_CODE_EXISTS", "Mã đợt khám đã tồn tại.");
        HealthCampaign entity = new HealthCampaign();
        entity.setCode(HealthQueries.code(request.code()));
        entity.setStatus(CampaignStatus.OPEN);
        apply(entity, request);
        HealthAccess.audit(entity, actor, true);
        campaigns.saveAndFlush(entity);
        audit.record("CREATE_CAMPAIGN", entity.getId(), actor);
        return response(entity);
    }
    @Transactional
    public CampaignResponse update(String id, CampaignRequest request, HrImportActor actor) {
        access.requireManager(actor);
        HealthCampaign entity = campaigns.findById(id).orElseThrow(() -> HrApiException.notFound("HEALTH_CAMPAIGN_NOT_FOUND", "Không tìm thấy đợt khám."));
        HealthAccess.checkVersion(entity, request.rowVersion());
        if (!entity.getCode().equals(HealthQueries.code(request.code()))) throw HrApiException.badRequest("HEALTH_CODE_IMMUTABLE", "Mã đợt khám không thay đổi sau khi tạo.");
        if (entity.getStatus() != CampaignStatus.OPEN) throw HrApiException.conflict("HEALTH_CAMPAIGN_CLOSED", "Đợt khám đã khóa.");
        apply(entity, request);
        HealthAccess.audit(entity, actor, false);
        campaigns.flush();
        audit.record("UPDATE_CAMPAIGN", id, actor);
        return response(entity);
    }
    private static void apply(HealthCampaign entity, CampaignRequest request) {
        if (request.startsOn() != null && request.endsOn() != null && request.endsOn().isBefore(request.startsOn())) {
            throw HrApiException.badRequest("HEALTH_DATE_INVALID", "Ngày kết thúc phải từ ngày bắt đầu trở đi.");
        }
        entity.setName(request.name().trim()); entity.setReportYear(request.reportYear());
        entity.setProvider(request.provider().trim()); entity.setStartsOn(request.startsOn());
        entity.setEndsOn(request.endsOn()); entity.setWorkforceGroup(request.workforceGroup());
    }
    private static CampaignResponse response(HealthCampaign value) {
        return new CampaignResponse(value.getId(), value.getCode(), value.getName(), value.getReportYear(), value.getProvider(),
                value.getStartsOn(), value.getEndsOn(), value.getWorkforceGroup(), value.getStatus(), value.getRowVersion());
    }
}
