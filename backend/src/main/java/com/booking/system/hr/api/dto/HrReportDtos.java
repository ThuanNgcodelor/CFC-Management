package com.booking.system.hr.api.dto;

import com.booking.system.hr.enums.HrWorkforceGroup;

import java.time.LocalDate;
import java.util.List;

public final class HrReportDtos {
    private HrReportDtos() {}

    public record HeadcountMonth(
            int month, boolean available, Long opening, long increases, long movedIn, long movedOut, long decreases,
            long ending, long uniqueLeavers, String note
    ) {}

    public record HeadcountReport(int year, LocalDate baselineFrom, List<HeadcountMonth> months,
                                  String workforceGroupBasis) {}

    public record LeaverRow(String movementId, String employeeId, String employeeCode, String fullName,
                            LocalDate effectiveDate, String departmentName, String reason,
                            String decisionNumber, HrWorkforceGroup currentWorkforceGroup,
                            String contractClassification) {}

    public record LeaverReport(LocalDate from, LocalDate to, long eventCount, long uniqueEmployeeCount,
                               List<LeaverRow> content, int page, int size, long totalElements,
                               int totalPages, String workforceGroupBasis) {}

    public record MovementSourceRow(String movementId, String employeeId, String employeeCode, String fullName,
                                    LocalDate effectiveDate, String movementType, String fromDepartment,
                                    String toDepartment, String reason, String decisionNumber,
                                    HrWorkforceGroup currentWorkforceGroup, String contractClassification) {}

    public record MovementSourceReport(LocalDate from, LocalDate to, String type, String flow, boolean uniqueOnly,
                                       List<MovementSourceRow> content, int page, int size,
                                       long totalElements, int totalPages) {}
}
