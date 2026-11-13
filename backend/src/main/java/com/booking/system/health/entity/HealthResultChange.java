package com.booking.system.health.entity;

import com.booking.system.hr.entity.HrBaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "hr_health_result_changes")
public class HealthResultChange extends HrBaseEntity {
    @Column(name = "result_id", nullable = false, length = 36)
    private String resultId;
    @Column(name = "batch_id", nullable = false, length = 36)
    private String batchId;
    @Column(name = "before_payload", nullable = false, columnDefinition = "text")
    private String beforePayload;
    @Column(name = "after_payload", nullable = false, columnDefinition = "text")
    private String afterPayload;
    @Column(name = "reason", nullable = false, length = 500)
    private String reason;
}
