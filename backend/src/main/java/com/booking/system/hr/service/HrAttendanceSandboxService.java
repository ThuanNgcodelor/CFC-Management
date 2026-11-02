package com.booking.system.hr.service;

import com.booking.system.hr.api.HrApiException;
import com.booking.system.hr.api.dto.HrAttendanceSandboxDtos;
import com.booking.system.hr.api.dto.HrProductionAttendanceDtos;
import com.booking.system.hr.enums.HrAttendancePolicyGroup;
import com.booking.system.hr.importer.HrProductionAttendanceWorkbookParser;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HrAttendanceSandboxService {
    private static final int MAX_FILE_BYTES = 15 * 1024 * 1024;
    private static final int MAX_PREVIEW_ROWS = 200;

    private final HrProductionAttendanceWorkbookParser parser;
    private final HrProductionShiftMatcher matcher;
    private final HrProductionAttendanceService attendance;

    public HrAttendanceSandboxDtos.Preview preview(byte[] bytes) {
        validateFileSize(bytes == null ? 0 : bytes.length);
        // Parsing stays outside a transaction; the existing service reads policy snapshots separately.
        var workbook = parser.parse(bytes, null);
        if (workbook.totalRows() > MAX_PREVIEW_ROWS) {
            throw HrApiException.badRequest("ATTENDANCE_SANDBOX_TOO_MANY_ROWS",
                    "Bản thử nghiệm chỉ nhận tối đa 200 ngày công mỗi file.");
        }
        List<HrProductionAttendanceDtos.ShiftPolicyResponse> configuredPolicies = attendance.shiftPolicies().stream()
                .filter(value -> value.active() && value.policyGroup() == HrAttendancePolicyGroup.PRODUCTION_WORKER)
                .toList();
        if (configuredPolicies.isEmpty()) {
            throw HrApiException.badRequest("ATTENDANCE_SANDBOX_NO_POLICY", "Chưa có cấu hình ca công nhân để tính thử.");
        }
        List<HrProductionShiftMatcher.ShiftPolicy> policies = configuredPolicies.stream()
                .map(this::policy).toList();
        Set<String> policyIds = configuredPolicies.stream().map(HrProductionAttendanceDtos.ShiftPolicyResponse::id)
                .collect(Collectors.toSet());
        List<HrProductionShiftMatcher.CreditRule> rules = attendance.workCreditRules().stream()
                .filter(value -> value.active() && policyIds.contains(value.shiftPolicyId()))
                .map(this::rule).toList();
        Map<String, HrProductionAttendanceDtos.ShiftPolicyResponse> policiesById = configuredPolicies.stream()
                .collect(Collectors.toMap(HrProductionAttendanceDtos.ShiftPolicyResponse::id, Function.identity()));
        Map<String, List<HrProductionAttendanceWorkbookParser.ParsedDay>> byEmployee = workbook.days().stream()
                .collect(Collectors.groupingBy(HrProductionAttendanceWorkbookParser.ParsedDay::employeeCode,
                        LinkedHashMap::new, Collectors.toList()));
        List<HrAttendanceSandboxDtos.Day> days = new ArrayList<>();
        for (List<HrProductionAttendanceWorkbookParser.ParsedDay> employeeDays : byEmployee.values()) {
            days.addAll(matchEmployee(employeeDays, policies, rules, policiesById));
        }
        List<HrAttendanceSandboxDtos.Policy> policyOptions = configuredPolicies.stream()
                .map(value -> new HrAttendanceSandboxDtos.Policy(value.code(), value.name(),
                        value.countsTowardNightReward())).toList();
        return new HrAttendanceSandboxDtos.Preview(workbook.attendanceMonth().toString(), workbook.totalRows(),
                workbook.totalPunches(), policyOptions, List.copyOf(days));
    }

    public void validateFileSize(long bytes) {
        if (bytes <= 0 || bytes > MAX_FILE_BYTES) {
            throw HrApiException.badRequest("ATTENDANCE_SANDBOX_FILE_SIZE", "Chọn file công không rỗng và tối đa 15 MB.");
        }
    }

    private List<HrAttendanceSandboxDtos.Day> matchEmployee(
            List<HrProductionAttendanceWorkbookParser.ParsedDay> sourceDays,
            List<HrProductionShiftMatcher.ShiftPolicy> policies,
            List<HrProductionShiftMatcher.CreditRule> rules,
            Map<String, HrProductionAttendanceDtos.ShiftPolicyResponse> policiesById) {
        List<HrProductionShiftMatcher.WorkDay> workDays = sourceDays.stream()
                .map(day -> new HrProductionShiftMatcher.WorkDay(day.sourceRowNumber(), day.workDate(),
                        day.punches().stream().map(punch -> new HrProductionShiftMatcher.Punch(
                                punchId(day, punch), punch.punchedAt())).toList(), false))
                .toList();
        Map<Integer, HrProductionAttendanceWorkbookParser.ParsedDay> byRow = sourceDays.stream()
                .collect(Collectors.toMap(HrProductionAttendanceWorkbookParser.ParsedDay::sourceRowNumber,
                        Function.identity()));
        return matcher.match(workDays, policies, rules).stream().map(result -> {
            var source = byRow.get(result.sourceRowNumber());
            var policy = policiesById.get(result.shiftPolicyId());
            List<HrAttendanceSandboxDtos.Punch> punches = sourceDays.stream()
                    .filter(day -> day.workDate().equals(source.workDate())
                            || day.workDate().equals(source.workDate().plusDays(1)))
                    .flatMap(day -> day.punches().stream()
                            .map(punch -> new HrAttendanceSandboxDtos.Punch(punchId(day, punch), punch.punchedAt())))
                    .toList();
            return new HrAttendanceSandboxDtos.Day(source.sourceRowNumber(), source.employeeCode(),
                    source.employeeName(), source.workDate(), punches, result.shiftCode(), result.checkInAt(),
                    result.checkOutAt(), result.workValue(), result.status().name(), result.explanation(),
                    policy != null && policy.countsTowardNightReward());
        }).toList();
    }

    private String punchId(HrProductionAttendanceWorkbookParser.ParsedDay day,
                           HrProductionAttendanceWorkbookParser.ParsedPunch punch) {
        return day.sourceRowNumber() + ":" + punch.sourceColumn();
    }

    private HrProductionShiftMatcher.ShiftPolicy policy(HrProductionAttendanceDtos.ShiftPolicyResponse value) {
        return new HrProductionShiftMatcher.ShiftPolicy(value.id(), value.code(), value.policyGroup(),
                value.standardStart(), value.standardEnd(), value.checkInFrom(), value.checkInUntil(),
                value.checkOutFrom(), value.checkOutUntil(), value.crossesMidnight(),
                value.nightAllowanceAmount(), value.priority(), value.validFrom(), value.validTo());
    }

    private HrProductionShiftMatcher.CreditRule rule(HrProductionAttendanceDtos.WorkCreditRuleResponse value) {
        return new HrProductionShiftMatcher.CreditRule(value.id(), value.shiftPolicyId(), value.checkOutFrom(),
                value.checkOutUntil(), value.workValue(), value.priority(), value.validFrom(), value.validTo());
    }
}
