package com.booking.system.health.importer.baseline;

import com.booking.system.health.api.HrHealthImportDtos.*;
import com.booking.system.health.importer.HealthParsedData;
import java.util.List;
import java.util.Map;

public final class HealthBaselineData {
    private HealthBaselineData() { }
    public enum Kind { HISTORY_2023_2024, SUPPLEMENT_2024, RESULTS_2025 }
    public enum Policy { STAGE_ALL, TOTAL_FIRST }
    public record WorkbookInput(Kind kind, String label, byte[] content) {
        @Override public String toString() { return "HealthBaselineWorkbook[redacted]"; }
    }
    public record Source(String key, Kind kind, String fileLabel, String fileHash, String sheet, boolean hidden,
                         int headerRow, boolean taskList, Map<Integer,Mapping> mapping, List<HealthParsedData.Row> rows,
                         List<Map<String,String>> tasks) {
        @Override public String toString() { return "HealthBaselineSource[redacted]"; }
    }
    public record Dataset(List<Source> sources, Map<String,String> indicatorLabels) { }
    public record Identity(String id, String code, String name, String birth) {
        @Override public String toString() { return "HealthBaselineIdentity[redacted]"; }
    }
    public record Finding(String kind, String sourceKey, int sourceRow, String sid, String employeeCode,
                          String message, List<String> candidateEmployeeIds) {
        @Override public String toString() { return "HealthBaselineFinding[redacted]"; }
    }
    public record SheetCount(String sourceKey, String fileLabel, String sheet, boolean hidden, int rows,
                             int tasks, int withEmployeeCode, Map<Integer,Long> suggestedYears) { }
    public record Report(List<SheetCount> sheets, int sourceRows, int distinctSids, int taskRows,
                         int matchedCodeRows, int matchedPeople, int suggestedOnlyRows, int unmatchedRows, int sourceNameConflicts,
                         int sourceValueConflicts, Map<String,Long> findingCounts, List<Finding> findings) { }
}
