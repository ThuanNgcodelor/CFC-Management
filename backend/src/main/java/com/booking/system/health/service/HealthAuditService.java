package com.booking.system.health.service;

import com.booking.system.health.entity.HealthAuditEvent;
import com.booking.system.health.repository.HealthAuditRepository;
import com.booking.system.hr.importer.HrImportActor;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class HealthAuditService {
    private final HealthAuditRepository events;
    public void record(String action, String entityId, HrImportActor actor) {
        record(action,entityId,actor,null);
    }
    public void record(String action,String entityId,HrImportActor actor,String reason) {
        // Health metadata stays outside the general personnel audit feed.
        HealthAuditEvent event = new HealthAuditEvent();
        event.setAction(action);
        event.setEntityId(entityId);
        event.setActorRole(actor.role());
        event.setReason(reason);
        HealthAccess.audit(event, actor, true);
        events.save(event);
    }
    public void recordAll(String action,java.util.Collection<String> ids,HrImportActor actor,String reason) {
        var batch=ids.stream().map(id->{var event=new HealthAuditEvent();event.setAction(action);event.setEntityId(id);event.setActorRole(actor.role());event.setReason(reason);HealthAccess.audit(event,actor,true);return event;}).toList();
        events.saveAll(batch);
    }
}
