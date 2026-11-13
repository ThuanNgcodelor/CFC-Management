package com.booking.system.health.entity;

import com.booking.system.hr.entity.HrBaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "hr_health_import_rows")
public class HealthImportRow extends HrBaseEntity {
    @Column(name = "batch_id", nullable = false, length = 36)
    private String batchId;
    @Column(name = "source_row", nullable = false)
    private int sourceRow;
    @Column(name = "payload", columnDefinition = "text")
    private String payload;
    @Column(name = "state", nullable = false, length = 20)
    private String state;
    @Column(name = "employee_id", length = 36)
    private String employeeId;
    @Column(name = "resolution_reason", length = 500)
    private String resolutionReason;
}
