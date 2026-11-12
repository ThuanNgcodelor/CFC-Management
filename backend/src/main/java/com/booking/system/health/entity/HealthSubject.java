package com.booking.system.health.entity;

import com.booking.system.hr.entity.HrBaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "hr_health_subjects")
public class HealthSubject extends HrBaseEntity {
    @Column(name = "employee_id", length = 36, nullable = false)
    private String employeeId;
}
