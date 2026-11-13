package com.booking.system.health.entity;

import com.booking.system.hr.entity.HrBaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import com.booking.system.health.entity.HealthTypes.*;

@Getter
@Setter
@Entity
@Table(name = "hr_health_indicators")
public class HealthIndicator extends HrBaseEntity {
    @Column(name = "code", length = 40, nullable = false)
    private String code;
    @Column(name = "name", length = 255, nullable = false)
    private String name;
    @Column(name = "category", length = 100, nullable = false)
    private String category;
    @Enumerated(EnumType.STRING)
    @Column(name = "value_type", length = 20, nullable = false)
    private IndicatorType valueType;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private IndicatorStatus status;
}
