package com.booking.system.health.entity;

import com.booking.system.hr.entity.HrBaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "hr_health_mapping_profiles")
public class HealthMappingProfile extends HrBaseEntity {
    @Column(name = "name", nullable = false, length = 100)
    private String name;
    @Column(name = "fingerprint", nullable = false, length = 64)
    private String fingerprint;
    @Column(name = "mapping_json", nullable = false, columnDefinition = "text")
    private String mappingJson;
}
