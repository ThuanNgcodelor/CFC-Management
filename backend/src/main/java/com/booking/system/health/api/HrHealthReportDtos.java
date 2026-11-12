package com.booking.system.health.api;

import com.booking.system.health.api.HrHealthRecordDtos.*;
import com.booking.system.hr.enums.HrWorkforceGroup;
import jakarta.validation.constraints.*;
import java.util.List;

public final class HrHealthReportDtos {
    private HrHealthReportDtos() { }
    public record Request(@Size(max=36) String employeeId,@Min(1900) @Max(2200) int fromYear,
            @Min(1900) @Max(2200) int toYear,@Size(max=100) String keyword,HrWorkforceGroup group,
            @NotBlank @Pattern(regexp="ALL|WITH_DATA|WITHOUT_DATA") String data,
            @NotBlank @Pattern(regexp="codeAsc|codeDesc|nameAsc|nameDesc") String sort,
            @Size(max=6) List<@NotBlank @Size(max=36) String> visitIds) {
        @Override public String toString(){return "HealthReportRequest[redacted]";}
    }
    public record VisitRow(String employeeId,Visit visit) {
        @Override public String toString(){return "HealthReportVisit[redacted]";}
    }
    public record Count(String label,long count,long denominator,String basis) { }
    public record Summary(int people,int peopleWithData,int visits,int results,List<Count> counts,List<String> notes) { }
    public record Snapshot(Request scope,List<Person> people,List<VisitRow> visits,List<Measurement> results,
            List<ComparisonRow> comparison,Summary summary) {
        @Override public String toString(){return "HealthReportSnapshot[redacted]";}
    }
}
