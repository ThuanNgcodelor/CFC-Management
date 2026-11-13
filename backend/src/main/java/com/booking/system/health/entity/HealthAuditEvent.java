package com.booking.system.health.entity;

import com.booking.system.hr.entity.HrBaseEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "hr_health_audit_events")
public class HealthAuditEvent extends HrBaseEntity {
    @Column(nullable = false, length = 64) private String action;
    @Column(name = "entity_id", nullable = false, length = 36) private String entityId;
    @Column(name = "actor_role", nullable = false, length = 32) private String actorRole;
    @Column(length=500) private String reason;
}
