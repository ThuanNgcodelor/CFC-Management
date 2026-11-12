package com.booking.system.health.entity;

import com.booking.system.hr.entity.HrBaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;
import java.time.LocalDate;
import com.booking.system.health.entity.HealthTypes.*;

@Getter
@Setter
@Entity
@Table(name = "hr_health_campaigns")
public class HealthCampaign extends HrBaseEntity {
    @Column(name = "code", length = 40, nullable = false)
    private String code;
    @Column(name = "name", length = 255, nullable = false)
    private String name;
    @Column(name = "report_year", nullable = false)
    private Integer reportYear;
    @Column(name = "provider", length = 255, nullable = false)
    private String provider;
    @Column(name = "starts_on")
    private LocalDate startsOn;
    @Column(name = "ends_on")
    private LocalDate endsOn;
    @Enumerated(EnumType.STRING)
    @Column(name = "workforce_group", length = 20, nullable = false)
    private WorkforceGroup workforceGroup;
    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 20, nullable = false)
    private CampaignStatus status;
}
