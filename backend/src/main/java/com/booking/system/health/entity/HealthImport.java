package com.booking.system.health.entity;

import com.booking.system.hr.entity.HrBaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "hr_health_imports")
public class HealthImport extends HrBaseEntity {
    @Column(name = "campaign_id", length = 36, nullable = false)
    private String campaignId;
    @Column(name = "source_hash", length = 64, nullable = false)
    private String sourceHash;
    @Column(name = "status", length = 30, nullable = false)
    private String status;
    @Column(name = "source_sheet", nullable = false, length = 255)
    private String sourceSheet;
    @Column(name = "file_hash", nullable = false, length = 64)
    private String fileHash;
    @Column(name = "source_namespace", nullable = false, length = 100)
    private String sourceNamespace;
    @Column(name = "mapping_json", columnDefinition = "text")
    private String mappingJson;
    @Column(name = "total_rows", nullable = false)
    private int totalRows;
}
