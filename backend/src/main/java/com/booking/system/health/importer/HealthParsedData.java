package com.booking.system.health.importer;

import com.booking.system.health.api.HrHealthImportDtos.StageRequest;
import java.time.LocalDate;
import java.util.List;

public final class HealthParsedData {
    private HealthParsedData() { }
    public record Value(String indicatorId, String rawValue, String reference, String unit, String method, String column, String label) {
        @Override public String toString() { return "HealthSourceValue[redacted]"; }
    }
    public record Row(int sourceRow, String code, String name, String birth, String sid, LocalDate date,
                      List<Value> values, List<String> errors, List<String> warnings, List<Correction> corrections) {
        public Row { corrections=corrections==null ? List.of() : List.copyOf(corrections); }
        public Row(int sourceRow,String code,String name,String birth,String sid,LocalDate date,List<Value> values,List<String> errors,List<String> warnings) {
            this(sourceRow,code,name,birth,sid,date,values,errors,warnings,List.of());
        }
        @Override public String toString() { return "HealthSourceRow[redacted]"; }
    }
    public record Correction(String key,String label,String original,String corrected,String reason,String actor,String at) {
        @Override public String toString() { return "HealthSourceCorrection[redacted]"; }
    }
    public record Parsed(String fileHash, String fingerprint, StageRequest request, List<Row> rows) { }
}
