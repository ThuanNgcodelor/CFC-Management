package com.booking.system.health.entity;

import com.booking.system.hr.entity.HrBaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;

@Getter
@Setter
@Entity
@Table(name = "hr_health_visits")
public class HealthVisit extends HrBaseEntity {
    @Column(name = "subject_id", length = 36, nullable = false)
    private String subjectId;
    @Column(name = "campaign_id", length = 36, nullable = false)
    private String campaignId;
    @Column(name = "source_namespace", length = 100, nullable = false)
    private String sourceNamespace;
    @Column(name = "source_sid", length = 100)
    private String sourceSid;
    @Column(name = "examined_on")
    private LocalDate examinedOn;
    @Column(name = "status", length = 30, nullable = false)
    private String status;
    @Column(name="reviewed_hash",length=64)
    private String reviewedHash;
    @Column(name="review_reason",length=500)
    private String reviewReason;
    @Column(name="acknowledged_incomplete",nullable=false)
    private boolean acknowledgedIncomplete;
}
