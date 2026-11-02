package com.booking.system.hr.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public final class HrAttendanceSandboxDtos {
    private HrAttendanceSandboxDtos() { }

    public record Punch(String id, LocalDateTime punchedAt) { }

    public record Policy(String id, String code, String name, boolean countsTowardNightReward,
                         boolean crossesMidnight, BigDecimal nightAllowanceAmount, String policyGroup) { }

    public record Day(int sourceRowNumber, String employeeCode, String employeeName,
                      LocalDate workDate, List<Punch> punches, String shiftCode,
                      LocalDateTime checkInAt, LocalDateTime checkOutAt, BigDecimal workValue,
                      String status, String explanation, boolean countsTowardNightReward,
                      BigDecimal nightAllowanceAmount, String policyGroup) { }

    public record Preview(String attendanceMonth, int totalRows, int totalPunches,
                          List<Policy> policies, List<Day> days) { }

    public record Decision(@NotBlank String employeeCode, @NotNull LocalDate workDate,
                           @NotNull HrProductionAttendanceDtos.DecisionAction action,
                           String shiftCode, String checkInPunchId, String checkOutPunchId,
                           BigDecimal workValue, BigDecimal nightAllowanceAmount, @NotBlank String reason) { }

    public record RematchRequest(List<@Valid Decision> decisions) { }
}
