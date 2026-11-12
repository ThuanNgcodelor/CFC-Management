package com.booking.system.health.service;

import com.booking.system.health.api.HrHealthPublicationDtos.*;
import com.booking.system.health.entity.*;
import com.booking.system.health.repository.*;
import com.booking.system.hr.api.HrApiException;
import com.booking.system.hr.importer.HrImportActor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;
import java.util.*;
import static com.booking.system.health.importer.HealthImportSupport.*;

@Service
@RequiredArgsConstructor
public class HealthPublicationWriter {
    private final HealthAccess access;
    private final HealthCampaignRepository campaigns;
    private final HealthEmployeeRepository employees;
    private final HealthRecordRepository records;
    private final HealthPublicationReadRepository reads;
    private final HealthPublicationRepository publications;
    private final HealthVisitRepository visits;
    private final HealthPublicationDrafts drafts;
    private final HealthAuditService audit;

    /** One person's selected visits commit together; other people use independent transactions. */
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public Outcome apply(String campaignId,PersonDecision person,String reason,boolean acknowledge,String action,HrImportActor actor) {
        access.requireManager(actor);var campaign=campaigns.lockById(campaignId).orElseThrow(()->invalid("Không tìm thấy đợt khám."));
        if(action.equals("REVIEW")&&campaign.getStatus()!=HealthTypes.CampaignStatus.OPEN)throw invalid("Đợt đã khóa; mở khóa trước khi rà soát.");
        employees.lockEmployees(List.of(person.employeeId()));
        var ids=person.visits().stream().map(Decision::visitId).toList();if(new HashSet<>(ids).size()!=ids.size())throw invalid("Không chọn lặp lượt khám.");
        var owned=records.ownedVisits(person.employeeId(),ids);if(owned.size()!=ids.size())throw invalid("Lượt khám không thuộc người được chọn.");
        var locked=reads.lockVisits(ids);if(locked.stream().anyMatch(v->!v.getCampaignId().equals(campaignId)))throw invalid("Lượt khám không thuộc đợt đã chọn.");
        var people=List.of(records.person(person.employeeId()).orElseThrow(()->invalid("Không tìm thấy nhân viên.")));
        var before=drafts.load(campaign,people);var indicatorIds=before.values().stream().filter(d->ids.contains(d.entity().getId())).flatMap(d->d.body().results().stream()).map(v->v.indicatorId()).distinct().toList();
        reads.lockIndicators(indicatorIds);var current=drafts.load(campaign,people);
        int affected=applyDecisions(person,current,reason.trim(),acknowledge,action,actor);
        return new Outcome(person.employeeId(),affected==0?"SKIPPED":"SUCCESS",affected,affected==0?"Không có thay đổi; phiên bản hiện tại đã đúng thao tác.":"Đã xử lý toàn bộ lượt được chọn của người này.");
    }
    private int applyDecisions(PersonDecision person,Map<String,HealthPublicationDrafts.Draft> current,String reason,boolean acknowledge,String action,HrImportActor actor) {
        var changedVisits=new ArrayList<HealthVisit>();var releases=new ArrayList<HealthPublication>();
        for(var decision:person.visits()) {
            var draft=current.get(decision.visitId());if(draft==null)throw invalid("Lượt không còn trong đợt.");var candidate=draft.candidate();
            boolean already=action.equals("REVIEW")?candidate.reviewed():action.equals("REVOKE")?candidate.latest()!=null&&candidate.latest().status().equals("REVOKED"):candidate.latest()!=null&&candidate.state().equals("PUBLISHED")&&candidate.reviewed();
            if(already)continue;
            if(!decision.token().equals(candidate.token()))throw HrApiException.conflict("HEALTH_PUBLICATION_STALE","Dữ liệu hoặc phiên bản đã thay đổi sau xem trước. Hãy xem trước lại.");
            if(action.equals("REVOKE")) {
                if(candidate.latest()==null)throw invalid("Lượt chưa có phiên bản để thu hồi.");
                var release=publications.findById(candidate.latest().id()).orElseThrow();release.setStatus("REVOKED");release.setRevokeReason(reason);HealthAccess.audit(release,actor,false);releases.add(release);continue;
            }
            if(!candidate.blockers().isEmpty())throw invalid("Lượt còn lỗi chặn: "+String.join(" ",candidate.blockers()));
            if(action.equals("REVIEW")) {
                if(!acknowledge)throw invalid("Cần xác nhận nhãn dữ liệu chưa đầy đủ trước khi duyệt rà soát.");
                var visit=draft.entity();visit.setStatus("CONFIRMED");visit.setReviewedHash(draft.hash());visit.setReviewReason(reason);visit.setAcknowledgedIncomplete(true);HealthAccess.audit(visit,actor,false);changedVisits.add(visit);
            } else {
                if(!candidate.reviewed())throw invalid("Kết quả chưa được rà soát hoặc đã thay đổi sau rà soát.");
                var body=draft.body();var snapshot=new PublishedSnapshot(1,body.person(),body.visit(),body.results(),true,draft.entity().getReviewReason());
                String payload=json(snapshot);if(payload.getBytes(java.nio.charset.StandardCharsets.UTF_8).length>HealthPublicationDrafts.MAX_SNAPSHOT_BYTES)throw invalid("Phiên bản vượt 2 MiB sau khi thêm thông tin rà soát.");
                var release=new HealthPublication();release.setVisitId(decision.visitId());release.setRevision(candidate.latest()==null?1:candidate.latest().revision()+1);release.setStatus("PUBLISHED");release.setContentHash(draft.hash());release.setSnapshot(payload);release.setReportYear(body.visit().year());release.setPublishReason(reason);HealthAccess.audit(release,actor,true);releases.add(release);
            }
        }
        visits.saveAll(changedVisits);publications.saveAll(releases);visits.flush();publications.flush();
        audit.recordAll("REVIEW_VISIT",changedVisits.stream().map(HealthVisit::getId).toList(),actor,reason);
        audit.recordAll(action.equals("REVOKE")?"REVOKE_PUBLICATION":"PUBLISH_VISIT",releases.stream().map(HealthPublication::getId).toList(),actor,reason);
        return changedVisits.size()+releases.size();
    }
    @Transactional(isolation=Isolation.READ_COMMITTED)
    public PublicationInfo revoke(String id,RevokeRequest request,HrImportActor actor) {
        access.requireManager(actor);var initial=publications.findById(id).orElseThrow(()->invalid("Không tìm thấy phiên bản."));
        var visit=visits.findById(initial.getVisitId()).orElseThrow(()->invalid("Không tìm thấy lượt."));campaigns.lockById(visit.getCampaignId());employees.lockEmployees(List.of(request.employeeId()));
        if(records.ownedVisits(request.employeeId(),List.of(visit.getId())).isEmpty())throw invalid("Phiên bản không thuộc nhân viên.");
        reads.lockVisits(List.of(visit.getId()));var latest=reads.latest(List.of(visit.getId())).getFirst();
        if(!latest.id().equals(id))throw HrApiException.conflict("HEALTH_PUBLICATION_STALE","Chỉ thu hồi phiên bản mới nhất; hãy tải lại.");
        if(latest.status().equals("REVOKED"))return latest;
        if(latest.rowVersion()!=request.rowVersion())throw HrApiException.conflict("HEALTH_PUBLICATION_STALE","Phiên bản đã thay đổi; hãy tải lại.");
        // Reload after taking the aggregate lock; the entity may have been read before waiting.
        var release=publications.findById(id).orElseThrow();release.setStatus("REVOKED");release.setRevokeReason(request.reason().trim());HealthAccess.audit(release,actor,false);publications.saveAndFlush(release);audit.record("REVOKE_PUBLICATION",id,actor,request.reason().trim());
        return reads.latest(List.of(visit.getId())).getFirst();
    }
    private static HrApiException invalid(String message){return HrApiException.badRequest("HEALTH_PUBLICATION_BLOCKED",message);}
}
