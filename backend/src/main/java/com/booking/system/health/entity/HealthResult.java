package com.booking.system.health.entity;

import com.booking.system.hr.entity.HrBaseEntity;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
@Entity
@Table(name = "hr_health_results")
public class HealthResult extends HrBaseEntity {
    @Column(name = "visit_id", length = 36, nullable = false)
    private String visitId;
    @Column(name = "indicator_id", length = 36, nullable = false)
    private String indicatorId;
    @Column(name = "import_id", length = 36)
    private String importId;
    @Column(name = "raw_value", columnDefinition = "text", nullable = false)
    private String rawValue;
    @Column(name = "unit", length = 100)
    private String unit;
    @Column(name = "method", length = 255)
    private String method;
    @Column(name = "reference_text", length = 500)
    private String referenceText;
    @Column(name = "source_sheet", length = 255)
    private String sourceSheet;
    @Column(name = "source_row")
    private Integer sourceRow;
    @Column(name = "source_column", length = 10)
    private String sourceColumn;
    @Column(name = "value_key", nullable = false, length = 64)
    private String valueKey;
    @Column(name = "source_label", length = 500)
    private String sourceLabel;
}
