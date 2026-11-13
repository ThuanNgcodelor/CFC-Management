package com.booking.system.health.entity;

import com.booking.system.hr.entity.HrBaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "hr_health_indicator_aliases")
public class HealthIndicatorAlias extends HrBaseEntity {
    @Column(name = "indicator_id", length = 36, nullable = false)
    private String indicatorId;
    @Column(name = "name", length = 255, nullable = false)
    private String name;
    @Column(name = "normalized_name", length = 255, nullable = false)
    private String normalizedName;
}
