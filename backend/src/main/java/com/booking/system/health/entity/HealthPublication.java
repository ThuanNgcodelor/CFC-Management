package com.booking.system.health.entity;

import com.booking.system.hr.entity.HrBaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "hr_health_publications")
public class HealthPublication extends HrBaseEntity {
    @Column(name = "visit_id", length = 36, nullable = false)
    private String visitId;
    @Column(name = "revision", nullable = false)
    private Integer revision;
    @Column(name = "status", length = 30, nullable = false)
    private String status;
    @Column(name = "snapshot", columnDefinition = "longtext", nullable = false)
    private String snapshot;
    @Column(name="report_year")
    private Integer reportYear;
    @Column(name="content_hash",length=64)
    private String contentHash;
    @Column(name="publish_reason",length=500)
    private String publishReason;
    @Column(name="revoke_reason",length=500)
    private String revokeReason;
}
