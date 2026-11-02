package com.booking.system.hr.service;

import com.booking.system.hr.api.HrApiException;
import com.booking.system.hr.api.dto.HrAttendanceSandboxDtos;
import com.booking.system.hr.api.dto.HrProductionAttendanceDtos;
import com.booking.system.hr.entity.HrEmployee;
import com.booking.system.hr.entity.HrEmployeeAttendancePolicy;
import com.booking.system.hr.entity.HrAttendanceExemption;
import com.booking.system.hr.enums.HrAttendancePolicyGroup;
import com.booking.system.hr.enums.HrAttendanceExemptionStatus;
import com.booking.system.hr.enums.HrWorkforceGroup;
import com.booking.system.hr.importer.HrProductionAttendanceWorkbookParser;
import com.booking.system.hr.repository.HrEmployeeRepository;
import com.booking.system.hr.repository.HrEmployeeAttendancePolicyRepository;
import com.booking.system.hr.repository.HrAttendanceExemptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashMap;
import java.util.HashSet;
import java.time.LocalDate;
import java.math.BigDecimal;
import java.util.Comparator;
import java.util.Locale;
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
    private final HrEmployeeRepository employeeRepository;
    private final HrEmployeeAttendancePolicyRepository employeePolicyRepository;
    private final HrAttendanceExemptionRepository exemptionRepository;

    public HrAttendanceSandboxDtos.Preview preview(byte[] bytes) {
        return preview(bytes, List.of());
    }

    public HrAttendanceSandboxDtos.Preview preview(byte[] bytes,
            List<HrAttendanceSandboxDtos.Decision> decisions) {
        validateFileSize(bytes == null ? 0 : bytes.length);
        if (decisions == null || decisions.size() > MAX_PREVIEW_ROWS) {
            throw HrApiException.badRequest("ATTENDANCE_SANDBOX_DECISIONS_INVALID", "Danh sách quyết định thử không hợp lệ.");
        }
        // Parsing stays outside a transaction; the existing service reads policy snapshots separately.
        var workbook = parser.parse(bytes, null);
        if (workbook.totalRows() > MAX_PREVIEW_ROWS) {
            throw HrApiException.badRequest("ATTENDANCE_SANDBOX_TOO_MANY_ROWS",
                    "Bản thử nghiệm chỉ nhận tối đa 200 ngày công mỗi file.");
        }
        List<HrProductionAttendanceDtos.ShiftPolicyResponse> configuredPolicies = attendance.shiftPolicies().stream()
                .filter(HrProductionAttendanceDtos.ShiftPolicyResponse::active)
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
        Map<String, HrEmployee> employees = (byEmployee.isEmpty() ? List.<HrEmployee>of()
                : employeeRepository.findAttendanceEmployeesByCodes(byEmployee.keySet())).stream()
                .collect(Collectors.toMap(employee -> employee.getEmployeeCode().trim().toUpperCase(Locale.ROOT),
                        Function.identity()));
        LocalDate firstDate = workbook.days().stream().map(HrProductionAttendanceWorkbookParser.ParsedDay::workDate)
                .min(LocalDate::compareTo).orElse(workbook.attendanceMonth().atDay(1));
        LocalDate lastDate = workbook.days().stream().map(HrProductionAttendanceWorkbookParser.ParsedDay::workDate)
                .max(LocalDate::compareTo).orElse(workbook.attendanceMonth().atEndOfMonth());
        Map<String, List<HrEmployeeAttendancePolicy>> employeePolicies = employees.isEmpty() ? Map.of()
                : employeePolicyRepository.findEffectiveForEmployees(
                        employees.values().stream().map(HrEmployee::getId).toList(), firstDate, lastDate).stream()
                        .collect(Collectors.groupingBy(HrEmployeeAttendancePolicy::getEmployeeId));
        Map<String, List<HrAttendanceExemption>> exemptions = byEmployee.isEmpty() ? Map.of()
                : exemptionRepository.findEffective(byEmployee.keySet().stream().toList(),
                        HrAttendanceExemptionStatus.CONFIRMED, firstDate, lastDate).stream()
                        .collect(Collectors.groupingBy(HrAttendanceExemption::getEmployeeCode));
        List<HrAttendanceSandboxDtos.Day> days = new ArrayList<>();
        Map<String, HrAttendanceSandboxDtos.Decision> decisionsByDay = new HashMap<>();
        for (var decision : decisions) {
            String key = dayKey(decision.employeeCode(), decision.workDate());
            if (decisionsByDay.putIfAbsent(key, decision) != null) {
                throw HrApiException.badRequest("ATTENDANCE_SANDBOX_DUPLICATE_DECISION", "Một ngày có nhiều quyết định thử.");
            }
        }
        Set<String> sourceKeys = workbook.days().stream()
                .map(day -> dayKey(day.employeeCode(), day.workDate())).collect(Collectors.toSet());
        if (!sourceKeys.containsAll(decisionsByDay.keySet())) {
            throw HrApiException.badRequest("ATTENDANCE_SANDBOX_UNKNOWN_DAY", "Quyết định thử không thuộc file công.");
        }
        for (var entry : byEmployee.entrySet()) {
            HrEmployee employee = employees.get(entry.getKey());
            Map<Assignment, List<HrProductionAttendanceWorkbookParser.ParsedDay>> grouped = entry.getValue().stream()
                    .collect(Collectors.groupingBy(day -> assignment(employee,
                            employee == null ? List.of() : employeePolicies.getOrDefault(employee.getId(), List.of()),
                            day.workDate()), LinkedHashMap::new, Collectors.toList()));
            for (var group : grouped.entrySet()) {
                List<HrProductionShiftMatcher.ShiftPolicy> groupPolicies = policies.stream()
                        .filter(policy -> policy.group() == group.getKey().group()).toList();
                Set<String> groupPolicyIds = groupPolicies.stream().map(HrProductionShiftMatcher.ShiftPolicy::id)
                        .collect(Collectors.toSet());
                Map<String, HrProductionAttendanceDtos.ShiftPolicyResponse> groupPoliciesById = policiesById.entrySet().stream()
                        .filter(value -> groupPolicyIds.contains(value.getKey()))
                        .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
                days.addAll(matchEmployee(group.getValue(), entry.getValue(), groupPolicies,
                        rules.stream().filter(rule -> groupPolicyIds.contains(rule.shiftPolicyId())).toList(),
                        groupPoliciesById, employee != null, decisionsByDay, group.getKey(),
                        exemptions.getOrDefault(entry.getKey(), List.of())));
            }
        }
        days.sort(Comparator.comparingInt(HrAttendanceSandboxDtos.Day::sourceRowNumber));
        List<HrAttendanceSandboxDtos.Policy> policyOptions = configuredPolicies.stream()
                .map(value -> new HrAttendanceSandboxDtos.Policy(value.id(), value.code(), value.name(),
                        value.countsTowardNightReward(), value.crossesMidnight(), value.nightAllowanceAmount(),
                        value.policyGroup().name())).toList();
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
            List<HrProductionAttendanceWorkbookParser.ParsedDay> allEmployeeDays,
            List<HrProductionShiftMatcher.ShiftPolicy> policies,
            List<HrProductionShiftMatcher.CreditRule> rules,
            Map<String, HrProductionAttendanceDtos.ShiftPolicyResponse> policiesById,
            boolean knownEmployee,
            Map<String, HrAttendanceSandboxDtos.Decision> decisionsByDay,
            Assignment assignment,
            List<HrAttendanceExemption> exemptions) {
        Map<String, HrProductionAttendanceWorkbookParser.ParsedPunch> punchesById = allEmployeeDays.stream()
                .flatMap(day -> day.punches().stream()
                        .map(punch -> Map.entry(punchId(day, punch), punch)))
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
        Map<Integer, HrAttendanceSandboxDtos.Day> resolved = new HashMap<>();
        Set<String> reserved = new HashSet<>();
        for (var source : sourceDays) {
            var decision = decisionsByDay.get(dayKey(source.employeeCode(), source.workDate()));
            if (decision != null) {
                resolved.put(source.sourceRowNumber(), manualDay(source, decision, policiesById,
                        punchesById, reserved, allEmployeeDays, assignment.group()));
            }
        }
        List<HrProductionShiftMatcher.WorkDay> workDays = sourceDays.stream()
                .filter(day -> !resolved.containsKey(day.sourceRowNumber()))
                .map(day -> new HrProductionShiftMatcher.WorkDay(day.sourceRowNumber(), day.workDate(),
                        day.punches().stream().map(punch -> new HrProductionShiftMatcher.Punch(
                                punchId(day, punch), punch.punchedAt()))
                                .filter(punch -> !reserved.contains(punch.id())).toList(),
                        exemptions.stream().anyMatch(value -> !day.workDate().isBefore(value.getValidFrom())
                                && !day.workDate().isAfter(value.getValidTo()))))
                .toList();
        List<HrProductionShiftMatcher.Punch> supplemental = sourceDays.stream()
                .filter(day -> resolved.containsKey(day.sourceRowNumber()))
                .flatMap(day -> day.punches().stream().map(punch -> new HrProductionShiftMatcher.Punch(
                        punchId(day, punch), punch.punchedAt())))
                .filter(punch -> !reserved.contains(punch.id())).toList();
        Map<Integer, HrProductionAttendanceWorkbookParser.ParsedDay> byRow = sourceDays.stream()
                .collect(Collectors.toMap(HrProductionAttendanceWorkbookParser.ParsedDay::sourceRowNumber,
                        Function.identity()));
        for (var result : matcher.match(workDays, supplemental, policies, rules)) {
            var source = byRow.get(result.sourceRowNumber());
            var policy = policiesById.get(result.shiftPolicyId());
            boolean missingEmployee = !knownEmployee && result.status().name().equals("AUTO_MATCHED");
            boolean dayOverride = "CN_DAY".equals(result.shiftCode()) && result.checkInAt() != null
                    && result.checkOutAt() != null && assignment.dayWorkValueOverride() != null;
            String explanation = dayOverride
                    ? result.explanation() + " Áp dụng mức ca ngày cố định "
                            + assignment.dayWorkValueOverride().stripTrailingZeros().toPlainString()
                            + " công theo chính sách nhân viên."
                    : result.explanation();
            resolved.put(source.sourceRowNumber(), new HrAttendanceSandboxDtos.Day(source.sourceRowNumber(), source.employeeCode(),
                    source.employeeName(), source.workDate(), visiblePunches(allEmployeeDays, source.workDate()), result.shiftCode(), result.checkInAt(),
                    result.checkOutAt(), dayOverride ? assignment.dayWorkValueOverride() : result.workValue(),
                    missingEmployee ? "NEEDS_REVIEW" : result.status().name(),
                    missingEmployee ? "Không tìm thấy mã nhân viên trong hồ sơ; " + explanation : explanation,
                    policy != null && policy.countsTowardNightReward(), result.nightAllowanceAmount(),
                    assignment.group().name()));
        }
        return sourceDays.stream().map(day -> resolved.get(day.sourceRowNumber())).toList();
    }

    private HrAttendanceSandboxDtos.Day manualDay(HrProductionAttendanceWorkbookParser.ParsedDay source,
            HrAttendanceSandboxDtos.Decision decision,
            Map<String, HrProductionAttendanceDtos.ShiftPolicyResponse> policiesById,
            Map<String, HrProductionAttendanceWorkbookParser.ParsedPunch> punchesById,
            Set<String> reserved, List<HrProductionAttendanceWorkbookParser.ParsedDay> sourceDays,
            HrAttendancePolicyGroup group) {
        boolean rejected = decision.action() == HrProductionAttendanceDtos.DecisionAction.REJECT;
        var policy = policiesById.values().stream().filter(value -> value.code().equals(decision.shiftCode()))
                .findFirst().orElse(null);
        if (decision.reason() == null || decision.reason().isBlank()
                || !rejected && (policy == null || decision.workValue() == null || decision.nightAllowanceAmount() == null
                || decision.nightAllowanceAmount().signum() < 0
                || List.of(BigDecimal.ZERO, new BigDecimal("0.5"), BigDecimal.ONE,
                        new BigDecimal("1.5"), new BigDecimal("2")).stream()
                        .noneMatch(value -> value.compareTo(decision.workValue()) == 0))) {
            throw HrApiException.badRequest("ATTENDANCE_SANDBOX_DECISION_INVALID", "Ca, công hoặc phụ cấp thử không hợp lệ.");
        }
        var inPunch = rejected ? null : selectedPunch(decision.checkInPunchId(), source, policy, true, punchesById, reserved);
        var outPunch = rejected ? null : selectedPunch(decision.checkOutPunchId(), source, policy, false, punchesById, reserved);
        if (inPunch != null && outPunch != null && !outPunch.punchedAt().isAfter(inPunch.punchedAt())) {
            throw HrApiException.badRequest("ATTENDANCE_SANDBOX_PUNCH_ORDER", "Lượt ra phải sau lượt vào.");
        }
        if (inPunch != null && outPunch != null && decision.checkInPunchId().equals(decision.checkOutPunchId())) {
            throw HrApiException.badRequest("ATTENDANCE_SANDBOX_PUNCH_REUSED", "Một dấu chấm không thể dùng hai lượt.");
        }
        if (inPunch != null) reserved.add(decision.checkInPunchId());
        if (outPunch != null) reserved.add(decision.checkOutPunchId());
        return new HrAttendanceSandboxDtos.Day(source.sourceRowNumber(), source.employeeCode(), source.employeeName(),
                source.workDate(), visiblePunches(sourceDays, source.workDate()), rejected ? null : policy.code(),
                inPunch == null ? null : inPunch.punchedAt(), outPunch == null ? null : outPunch.punchedAt(),
                rejected ? BigDecimal.ZERO : decision.workValue(), rejected ? "REJECTED" : "CONFIRMED",
                decision.reason().trim(), !rejected && policy.countsTowardNightReward(),
                rejected ? BigDecimal.ZERO : decision.nightAllowanceAmount(), group.name());
    }

    private HrProductionAttendanceWorkbookParser.ParsedPunch selectedPunch(String id,
            HrProductionAttendanceWorkbookParser.ParsedDay source,
            HrProductionAttendanceDtos.ShiftPolicyResponse policy, boolean checkIn,
            Map<String, HrProductionAttendanceWorkbookParser.ParsedPunch> punchesById, Set<String> reserved) {
        if (id == null || id.isBlank()) return null;
        var punch = punchesById.get(id);
        LocalDate expected = checkIn || !policy.crossesMidnight()
                ? source.workDate() : source.workDate().plusDays(1);
        if (punch == null || reserved.contains(id) || !punch.punchedAt().toLocalDate().equals(expected)
                || (policy.crossesMidnight() && (checkIn
                ? punch.punchedAt().toLocalTime().isBefore(java.time.LocalTime.NOON)
                : !punch.punchedAt().toLocalTime().isBefore(java.time.LocalTime.NOON)))) {
            throw HrApiException.badRequest("ATTENDANCE_SANDBOX_PUNCH_INVALID", "Dấu chấm thử không hợp lệ hoặc đã được dùng.");
        }
        return punch;
    }

    private List<HrAttendanceSandboxDtos.Punch> visiblePunches(
            List<HrProductionAttendanceWorkbookParser.ParsedDay> sourceDays, LocalDate date) {
        return sourceDays.stream().filter(day -> day.workDate().equals(date)
                        || day.workDate().equals(date.plusDays(1)))
                .flatMap(day -> day.punches().stream().map(punch -> new HrAttendanceSandboxDtos.Punch(
                        punchId(day, punch), punch.punchedAt()))).toList();
    }

    private String dayKey(String code, LocalDate date) {
        return code.trim().toUpperCase(java.util.Locale.ROOT) + ":" + date;
    }

    private Assignment assignment(HrEmployee employee, List<HrEmployeeAttendancePolicy> employeePolicies,
            LocalDate date) {
        if (employee != null) {
            for (var policy : employeePolicies) {
                if (!date.isBefore(policy.getValidFrom())
                        && (policy.getValidTo() == null || !date.isAfter(policy.getValidTo()))) {
                    return new Assignment(policy.getPolicyGroup(), policy.getDayWorkValueOverride());
                }
            }
            if (employee.getEmployment() != null && employee.getEmployment().getDepartment() != null) {
                var department = employee.getEmployment().getDepartment();
                String label = (department.getCode() + " " + department.getName()).toUpperCase(Locale.ROOT);
                if (label.contains("KCS")) return new Assignment(HrAttendancePolicyGroup.KCS, null);
            }
            if (employee.getWorkforceGroup() == HrWorkforceGroup.GENERAL_LABOR) {
                return new Assignment(HrAttendancePolicyGroup.PRODUCTION_WORKER, null);
            }
        }
        return new Assignment(HrAttendancePolicyGroup.PRODUCTION_WORKER, null);
    }

    private record Assignment(HrAttendancePolicyGroup group, BigDecimal dayWorkValueOverride) { }

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
