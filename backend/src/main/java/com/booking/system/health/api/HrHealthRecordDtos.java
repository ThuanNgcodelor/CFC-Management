package com.booking.system.health.api;

import com.booking.system.health.entity.HealthTypes.IndicatorType;
import com.booking.system.hr.api.dto.HrPageResponse;
import com.booking.system.hr.enums.HrWorkforceGroup;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

public final class HrHealthRecordDtos {
    private HrHealthRecordDtos() { }
    public record Person(String employeeId,String code,String name,LocalDate birthDate,HrWorkforceGroup currentGroup,Long visitCount,Integer lastYear) {
        @Override public String toString(){return "HealthPerson[redacted]";}
    }
    public record Visit(String id,Integer year,String campaign,String provider,LocalDate examinedOn,String status,String namespace,String sid,Long resultCount,String classification) {
        public Visit(String id,Integer year,String campaign,String provider,LocalDate examinedOn,String status,String namespace,String sid,Long resultCount){this(id,year,campaign,provider,examinedOn,status,namespace,sid,resultCount,null);}
        @Override public String toString(){return "HealthVisit[redacted]";}
    }
    public record Classification(String visitId,String raw) {
        @Override public String toString(){return "HealthClassification[redacted]";}
    }
    public record YearCount(Integer year,Long visits) { }
    public record Profile(Person person,HrPageResponse<Visit> visits,List<YearCount> years) {
        @Override public String toString(){return "HealthProfile[redacted]";}
    }
    public record Measurement(String visitId,String key,String indicatorId,String code,String label,String category,IndicatorType type,
            String raw,String unit,String method,String reference,String sourceSheet,Integer sourceRow,String sourceColumn,String sourceLabel) {
        @Override public String toString(){return "HealthMeasurement[redacted]";}
    }
    public record Results(Visit visit,HrPageResponse<Measurement> results) {
        @Override public String toString(){return "HealthResults[redacted]";}
    }
    public record IndicatorKey(String key,String indicatorId,String code,String label,String category,IndicatorType type,String unit,String method) { }
    public record CompareRequest(@NotNull @Size(min=2,max=6) List<@NotBlank @Size(max=36) String> visitIds,
            @Size(max=100) String keyword,@Min(0) @Max(10000) int page,@Min(1) @Max(100) int size) { }
    public record ComparisonCell(String visitId,boolean missing,String raw,String reference,String sourceSheet,Integer sourceRow,String sourceColumn,BigDecimal numeric) {
        @Override public String toString(){return "HealthComparisonCell[redacted]";}
    }
    public record ComparisonRow(IndicatorKey indicator,List<ComparisonCell> cells,boolean numericComparable,BigDecimal difference,String note) {
        @Override public String toString(){return "HealthComparisonRow[redacted]";}
    }
    public record Comparison(List<Visit> visits,HrPageResponse<ComparisonRow> rows) {
        @Override public String toString(){return "HealthComparison[redacted]";}
    }
}
