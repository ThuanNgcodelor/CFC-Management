package com.booking.system.health.service;

import com.booking.system.health.api.HrHealthPublicationDtos.*;
import com.booking.system.health.entity.HealthTypes.CampaignStatus;
import com.booking.system.health.repository.HealthCampaignRepository;
import com.booking.system.hr.api.HrApiException;
import com.booking.system.hr.importer.HrImportActor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class HealthCampaignStateService {
    private final HealthAccess access;
    private final HealthCampaignRepository campaigns;
    private final HealthAuditService audit;
    @Transactional
    public Campaign set(String id,CampaignStateRequest request,HrImportActor actor) {
        access.requireManager(actor);var value=campaigns.lockById(id).orElseThrow(()->HrApiException.notFound("HEALTH_CAMPAIGN_NOT_FOUND","Không tìm thấy đợt khám."));
        var target=request.closed()?CampaignStatus.CLOSED:CampaignStatus.OPEN;
        if(value.getStatus()==target)return HealthPublicationDrafts.campaign(value);
        HealthAccess.checkVersion(value,request.rowVersion());value.setStatus(target);HealthAccess.audit(value,actor,false);campaigns.flush();
        audit.record(request.closed()?"CLOSE_HEALTH_CAMPAIGN":"REOPEN_HEALTH_CAMPAIGN",id,actor,request.reason().trim());return HealthPublicationDrafts.campaign(value);
    }
}
