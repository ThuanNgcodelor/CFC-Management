package com.booking.system.health.api;

import jakarta.validation.Valid;
import jakarta.validation.constraints.*;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public final class HrHealthImportDtos {
    private HrHealthImportDtos() { }
    public enum Field { IGNORE, EMPLOYEE_CODE, NAME, BIRTH, SID, EXAM_DATE, RESULT, REFERENCE }
    public enum Choice { KEEP, REPLACE }
    public record Column(int index, String letter, String name, Field suggested, String indicatorId) {
        public Column(int index, String letter, String name, Field suggested) { this(index,letter,name,suggested,null); }
    }
    public record SheetInfo(String name, boolean hidden, int headerRow, int dataRows, boolean resultSheet, String fingerprint, List<Column> columns) { }
    public record Inspection(String fileHash, List<SheetInfo> sheets) { }
    public record Mapping(@NotNull Field field, @Size(max=36) String indicatorId,
                          @Size(max=100) String unit, @Size(max=255) String method) { }
    public record StageRequest(@NotBlank @Size(max=36) String campaignId, @NotBlank @Size(max=255) String sheet,
            @Min(1) @Max(100) int headerRow, @Min(1) @Max(3) int headerRows,
            @NotBlank @Size(max=100) String sourceNamespace, boolean dateFromSidConfirmed,
            LocalDate defaultDate, boolean noSidDateKeyConfirmed, @NotNull @Size(min=1,max=256) Map<@NotNull @PositiveOrZero Integer,@NotNull @Valid Mapping> columns,
            @Size(max=100) String profileName) {
        public StageRequest(String campaignId, String sheet, int headerRow, int headerRows, String sourceNamespace,
                boolean dateFromSidConfirmed, LocalDate defaultDate, Map<Integer,Mapping> columns, String profileName) {
            this(campaignId,sheet,headerRow,headerRows,sourceNamespace,dateFromSidConfirmed,defaultDate,false,columns,profileName);
        }
    }
    public record Batch(String id, String campaignId, String sheet, String status, int totalRows,
                        long importedRows, long pendingRows, long rowVersion) { }
    public record Profile(String id, String name, String fingerprint, String mappingJson, long rowVersion) { }
    public record ResolveRequest(@NotBlank @Size(max=36) String employeeId,
                                 @NotBlank @Size(max=500) String reason, @NotNull @PositiveOrZero Long rowVersion) { }
    public record EmployeeChoice(String id, String code, String name, LocalDate birthDate) {
        @Override public String toString() { return "HealthEmployee[redacted]"; }
    }
    public record Cell(String key, String indicatorId, String label, String incoming, String existing,
                       String incomingReference, String existingReference, String unit, String method, String action) {
        @Override public String toString() { return "HealthCell[redacted]"; }
    }
    public record PreviewRow(String id, int sourceRow, String name, String code, String sid, LocalDate examinedOn,
             String state, String employeeId, List<EmployeeChoice> candidates, List<String> errors, List<String> warnings,
             List<Cell> cells, String previewToken, long rowVersion, List<SourceCorrection> corrections,String sourceBirth) {
        public PreviewRow(String id,int sourceRow,String name,String code,String sid,LocalDate examinedOn,String state,String employeeId,
                List<EmployeeChoice> candidates,List<String> errors,List<String> warnings,List<Cell> cells,String previewToken,long rowVersion,List<SourceCorrection> corrections) {
            this(id,sourceRow,name,code,sid,examinedOn,state,employeeId,candidates,errors,warnings,cells,previewToken,rowVersion,corrections,"");
        }
        public PreviewRow(String id,int sourceRow,String name,String code,String sid,LocalDate examinedOn,String state,String employeeId,
                List<EmployeeChoice> candidates,List<String> errors,List<String> warnings,List<Cell> cells,String previewToken,long rowVersion) {
            this(id,sourceRow,name,code,sid,examinedOn,state,employeeId,candidates,errors,warnings,cells,previewToken,rowVersion,List.of(),"");
        }
        @Override public String toString() { return "HealthPreviewRow[redacted]"; }
    }
    public record SourceCorrection(String label,String original,String corrected,String reason,String at) {
        @Override public String toString() { return "HealthSourceCorrection[redacted]"; }
    }
    public record CorrectClassificationRequest(@NotBlank @Size(max=64) String key,@NotBlank @Size(max=8) String value,
        @NotBlank @Size(max=500) String reason,@NotNull @PositiveOrZero Long rowVersion) { }
    public record RowDecision(@NotBlank @Size(max=36) String rowId, @NotBlank @Size(max=64) String previewToken,
                              @NotNull @Size(max=256) Map<@NotBlank @Size(max=64) String,@NotNull Choice> choices, @Size(max=500) String reason) { }
    public record ConfirmRequest(@NotNull @Size(min=1,max=100) List<@NotNull @Valid RowDecision> rows) { }
    public record VersionRequest(@NotNull @PositiveOrZero Long rowVersion) { }
    public record ConfirmResult(Batch batch, int importedRows, int skippedRows) { }
}
