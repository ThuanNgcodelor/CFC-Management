package com.booking.system.hr.api.dto;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public final class HrAttendanceSandboxDtos {
    private HrAttendanceSandboxDtos() { }

    public record Punch(String id, LocalDateTime punchedAt) { }

    public record Policy(String code, String name, boolean countsTowardNightReward) { }

    public record Day(int sourceRowNumber, String employeeCode, String employeeName,
                      LocalDate workDate, List<Punch> punches, String shiftCode,
                      LocalDateTime checkInAt, LocalDateTime checkOutAt, BigDecimal workValue,
                      String status, String explanation, boolean countsTowardNightReward) { }

    public record Preview(String attendanceMonth, int totalRows, int totalPunches,
                          List<Policy> policies, List<Day> days) { }
}
