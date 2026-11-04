package com.booking.system.hr.service;

import com.booking.system.hr.api.HrApiException;
import com.booking.system.hr.api.dto.HrReportDtos;
import com.booking.system.hr.entity.HrEmployeeMovement;
import com.booking.system.hr.entity.HrEmploymentContract;
import com.booking.system.hr.entity.HrMonthlyRoster;
import com.booking.system.hr.enums.HrEmploymentContractStatus;
import com.booking.system.hr.enums.HrMovementStatus;
import com.booking.system.hr.enums.HrMovementType;
import com.booking.system.hr.enums.HrWorkforceGroup;
import com.booking.system.hr.repository.HrEmployeeMovementRepository;
import com.booking.system.hr.repository.HrEmploymentContractRepository;
import com.booking.system.hr.repository.HrMonthlyRosterRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class HrReportService {
    private static final int MAX_YEAR = 2100;
    private final HrRosterProjectionService rosterProjectionService;
    private final HrMonthlyRosterRepository rosterRepository;
    private final HrEmployeeMovementRepository movementRepository;
    private final HrEmploymentContractRepository contractRepository;

    public HrReportDtos.HeadcountReport headcount(int year, HrWorkforceGroup group, String contractClass, String department) {
        validateYear(year);
        validateContractClass(contractClass);
        String departmentQuery = normalized(department);
        HrMonthlyRoster baseline = rosterRepository.findFirstByOrderByPeriodStartAsc().orElse(null);
        if (baseline == null) return new HrReportDtos.HeadcountReport(year, null, List.of(), "Nhóm theo hồ sơ hiện tại");
        LocalDate baselineFrom = baseline.getPeriodStart();
        List<HrReportDtos.HeadcountMonth> months = new ArrayList<>();
        LocalDate yearStart = LocalDate.of(year, 1, 1);
        if (yearStart.isBefore(baselineFrom)) {
            for (int month = 1; month <= 12; month++) months.add(unavailable(month, "Chưa có baseline quân số cho kỳ này."));
            return new HrReportDtos.HeadcountReport(year, baselineFrom, months, "Nhóm theo hồ sơ hiện tại; lịch sử đổi nhóm chưa được lưu.");
        }
        LocalDate lastAvailable = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).withDayOfMonth(1);
        List<HrEmployeeMovement> timeline = movementRepository.findConfirmedForProjection(
                HrMovementStatus.CONFIRMED, List.of(HrMovementType.INCREASE, HrMovementType.DECREASE, HrMovementType.REHIRE, HrMovementType.TRANSFER));
        Set<String> superseded = new HashSet<>();
        timeline.stream().filter(m -> m.getCorrectionOfMovement() != null).forEach(m -> superseded.add(m.getCorrectionOfMovement().getId()));
        List<LocalDate> periodsToProject = new ArrayList<>();
        for (int month = 1; month <= 12; month++) {
            LocalDate period = LocalDate.of(year, month, 1);
            periodsToProject.add(period);
            periodsToProject.add(period.minusMonths(1));
        }
        Map<LocalDate, List<HrRosterProjectionService.ProjectedRosterItem>> projections =
                rosterProjectionService.projectedItemsForPeriods(periodsToProject);
        for (int month = 1; month <= 12; month++) {
            LocalDate period = LocalDate.of(year, month, 1);
            if (period.isAfter(lastAvailable)) {
                months.add(unavailable(month, "Kỳ chưa đến."));
                continue;
            }
            if (period.isBefore(baselineFrom)) {
                months.add(unavailable(month, "Chưa có baseline quân số cho kỳ này."));
                continue;
            }
            LocalDate end = period.withDayOfMonth(period.lengthOfMonth());
            List<HrRosterProjectionService.ProjectedRosterItem> ending = projections.getOrDefault(period, List.of());
            Long opening = period.equals(baselineFrom) ? null : countRoster(projections.getOrDefault(period.minusMonths(1), List.of()), period.minusDays(1), group, contractClass, departmentQuery);
            long endingCount = countRoster(ending, end, group, contractClass, departmentQuery);
            List<HrEmployeeMovement> increasesThisMonth = timeline.stream().filter(m -> !superseded.contains(m.getId())
                    && !m.getEffectiveDate().isBefore(period) && !m.getEffectiveDate().isAfter(end)
                    && (m.getMovementType() == HrMovementType.INCREASE || m.getMovementType() == HrMovementType.REHIRE))
                    .filter(m -> group == null || group == m.getEmployee().getWorkforceGroup())
                    .filter(m -> departmentQuery == null || contains(m.getToDepartment() == null ? null : m.getToDepartment().getName(), departmentQuery)).toList();
            Map<String, Boolean> increaseContracts = contractMatches(increasesThisMonth.stream().map(m -> m.getEmployee().getId()).distinct().toList(), end);
            long increases = increasesThisMonth.stream().filter(m -> contractClass == null
                    || Boolean.TRUE.equals(increaseContracts.get(m.getEmployee().getId())) == contractClass.equals("FORMAL")).count();
            List<HrEmployeeMovement> transfers = timeline.stream().filter(m -> !superseded.contains(m.getId())
                    && m.getMovementType() == HrMovementType.TRANSFER && !m.getEffectiveDate().isBefore(period)
                    && !m.getEffectiveDate().isAfter(end) && (group == null || group == m.getEmployee().getWorkforceGroup())).toList();
            Map<String, Boolean> transferContracts = contractMatches(transfers.stream().map(m -> m.getEmployee().getId()).distinct().toList(), end);
            transfers = transfers.stream().filter(m -> contractClass == null
                    || Boolean.TRUE.equals(transferContracts.get(m.getEmployee().getId())) == contractClass.equals("FORMAL")).toList();
            long movedIn = departmentQuery == null ? 0 : transfers.stream().filter(m -> contains(m.getToDepartment() == null ? null : m.getToDepartment().getName(), departmentQuery)).count();
            long movedOut = departmentQuery == null ? 0 : transfers.stream().filter(m -> contains(m.getFromDepartment() == null ? null : m.getFromDepartment().getName(), departmentQuery)).count();
            List<HrEmployeeMovement> leavers = timeline.stream().filter(m -> m.getMovementType() == HrMovementType.DECREASE
                    && !m.getEffectiveDate().isBefore(period) && !m.getEffectiveDate().isAfter(end)).toList();
            leavers = removeSuperseded(leavers);
            if (group != null) leavers = leavers.stream().filter(m -> m.getEmployee().getWorkforceGroup() == group).toList();
            if (departmentQuery != null) leavers = leavers.stream().filter(m -> contains(m.getFromDepartment() == null ? null : m.getFromDepartment().getName(), departmentQuery)).toList();
            if (contractClass != null) leavers = filterContracts(leavers, contractClass, end);
            months.add(new HrReportDtos.HeadcountMonth(month, true, opening, increases, movedIn, movedOut, leavers.size(), endingCount,
                    leavers.stream().map(m -> m.getEmployee().getId()).distinct().count(), opening == null ? "Thiếu mốc đầu kỳ do đây là baseline đầu tiên." : null));
        }
        return new HrReportDtos.HeadcountReport(year, baselineFrom, months, "Nhóm theo hồ sơ hiện tại; lịch sử đổi nhóm chưa được lưu.");
    }

    public HrReportDtos.LeaverReport leavers(int year, Integer month, HrWorkforceGroup group, String contractClass,
                                              String department, String reason, int page, int size) {
        validateYear(year);
        validateContractClass(contractClass);
        if (month != null && (month < 1 || month > 12)) throw HrApiException.badRequest("REPORT_MONTH_INVALID", "Tháng báo cáo không hợp lệ.");
        int safePage = Math.max(0, page), safeSize = Math.max(1, Math.min(size, 100));
        LocalDate from = LocalDate.of(year, month == null ? 1 : month, 1);
        LocalDate to = month == null ? LocalDate.of(year, 12, 31) : from.withDayOfMonth(from.lengthOfMonth());
        List<HrEmployeeMovement> timeline = movementRepository.findConfirmedForProjection(
                HrMovementStatus.CONFIRMED, List.of(HrMovementType.INCREASE, HrMovementType.DECREASE, HrMovementType.REHIRE));
        List<HrEmployeeMovement> rows = removeSuperseded(timeline.stream()
                .filter(m -> m.getMovementType() == HrMovementType.DECREASE && !m.getEffectiveDate().isBefore(from) && !m.getEffectiveDate().isAfter(to)).toList());
        Map<String, Boolean> eventContracts = eventContractMatches(rows);
        String departmentQuery = normalized(department), reasonQuery = normalized(reason);
        List<HrEmployeeMovement> filtered = rows.stream()
                .filter(m -> group == null || group == m.getEmployee().getWorkforceGroup())
                .filter(m -> contractClass == null || Boolean.TRUE.equals(eventContracts.get(eventContractKey(m))) == contractClass.equals("FORMAL"))
                .filter(m -> departmentQuery == null || contains(m.getFromDepartment() == null ? null : m.getFromDepartment().getName(), departmentQuery))
                .filter(m -> reasonQuery == null || contains(m.getReason(), reasonQuery))
                .toList();
        int start = (int) Math.min((long) safePage * safeSize, filtered.size());
        List<HrReportDtos.LeaverRow> content = filtered.subList(start, Math.min(start + safeSize, filtered.size())).stream().map(m ->
                new HrReportDtos.LeaverRow(m.getId(), m.getEmployee().getId(), m.getEmployee().getEmployeeCode(), m.getEmployee().getFullName(),
                        m.getEffectiveDate(), m.getFromDepartment() == null ? null : m.getFromDepartment().getName(), m.getReason(), m.getDecisionNumber(),
                        m.getEmployee().getWorkforceGroup(), Boolean.TRUE.equals(eventContracts.get(eventContractKey(m))) ? "FORMAL" : "UNKNOWN"))
                .toList();
        int totalPages = filtered.isEmpty() ? 0 : (int) Math.ceil((double) filtered.size() / safeSize);
        return new HrReportDtos.LeaverReport(from, to, filtered.size(), filtered.stream().map(m -> m.getEmployee().getId()).distinct().count(),
                content, safePage, safeSize, filtered.size(), totalPages, "Nhóm theo hồ sơ hiện tại; lịch sử đổi nhóm chưa được lưu.");
    }

    public List<HrReportDtos.LeaverRow> allLeavers(int year, Integer month, HrWorkforceGroup group, String contractClass,
                                                   String department, String reason) {
        HrReportDtos.LeaverReport first = leavers(year, month, group, contractClass, department, reason, 0, 100);
        List<HrReportDtos.LeaverRow> all = new ArrayList<>(first.content());
        for (int page = 1; page < first.totalPages(); page++) all.addAll(leavers(year, month, group, contractClass, department, reason, page, 100).content());
        return all;
    }

    public HrReportDtos.MovementSourceReport movementSources(int year, int month, String type, String flow, boolean uniqueOnly,
                                                              HrWorkforceGroup group, String contractClass,
                                                              String department, String reason, int page, int size) {
        validateYear(year);
        validateContractClass(contractClass);
        if (month < 1 || month > 12) throw HrApiException.badRequest("REPORT_MONTH_INVALID", "Tháng báo cáo không hợp lệ.");
        if (type == null || !Set.of("INCREASE", "DECREASE", "TRANSFER").contains(type)) throw HrApiException.badRequest("REPORT_MOVEMENT_TYPE_INVALID", "Loại biến động báo cáo không hợp lệ.");
        if (flow != null && !Set.of("IN", "OUT").contains(flow)) throw HrApiException.badRequest("REPORT_MOVEMENT_FLOW_INVALID", "Hướng điều chuyển không hợp lệ.");
        LocalDate from = LocalDate.of(year, month, 1), to = from.withDayOfMonth(from.lengthOfMonth());
        List<HrEmployeeMovement> timeline = movementRepository.findConfirmedForProjection(HrMovementStatus.CONFIRMED,
                List.of(HrMovementType.INCREASE, HrMovementType.DECREASE, HrMovementType.REHIRE, HrMovementType.TRANSFER));
        List<HrEmployeeMovement> rows = removeSuperseded(timeline.stream().filter(m -> !m.getEffectiveDate().isBefore(from)
                && !m.getEffectiveDate().isAfter(to) && switch (type) {
                    case "INCREASE" -> m.getMovementType() == HrMovementType.INCREASE || m.getMovementType() == HrMovementType.REHIRE;
                    case "DECREASE" -> m.getMovementType() == HrMovementType.DECREASE;
                    default -> m.getMovementType() == HrMovementType.TRANSFER;
                }).toList());
        Map<String, Boolean> contracts = eventContractMatches(rows);
        String departmentQuery = normalized(department), reasonQuery = normalized(reason);
        List<HrEmployeeMovement> filtered = rows.stream()
                .filter(m -> group == null || group == m.getEmployee().getWorkforceGroup())
                .filter(m -> contractClass == null || Boolean.TRUE.equals(contracts.get(eventContractKey(m))) == contractClass.equals("FORMAL"))
                .filter(m -> departmentQuery == null || matchesMovementDepartment(m, flow, departmentQuery))
                .filter(m -> reasonQuery == null || contains(m.getReason(), reasonQuery))
                .toList();
        if (uniqueOnly) {
            Map<String, HrEmployeeMovement> latestByEmployee = new LinkedHashMap<>();
            filtered.stream().sorted(java.util.Comparator.comparing(HrEmployeeMovement::getEffectiveDate).reversed())
                    .forEach(m -> latestByEmployee.putIfAbsent(m.getEmployee().getId(), m));
            filtered = List.copyOf(latestByEmployee.values());
        }
        int safePage = Math.max(0, page), safeSize = Math.max(1, Math.min(size, 100));
        int start = (int) Math.min((long) safePage * safeSize, filtered.size());
        List<HrReportDtos.MovementSourceRow> content = filtered.subList(start, Math.min(start + safeSize, filtered.size())).stream().map(m ->
                new HrReportDtos.MovementSourceRow(m.getId(), m.getEmployee().getId(), m.getEmployee().getEmployeeCode(), m.getEmployee().getFullName(),
                        m.getEffectiveDate(), m.getMovementType().name(), m.getFromDepartment() == null ? null : m.getFromDepartment().getName(),
                        m.getToDepartment() == null ? null : m.getToDepartment().getName(), m.getReason(), m.getDecisionNumber(),
                        m.getEmployee().getWorkforceGroup(), Boolean.TRUE.equals(contracts.get(eventContractKey(m))) ? "FORMAL" : "UNKNOWN")).toList();
        int pages = filtered.isEmpty() ? 0 : (int) Math.ceil((double) filtered.size() / safeSize);
        return new HrReportDtos.MovementSourceReport(from, to, type, flow, uniqueOnly, content, safePage, safeSize, filtered.size(), pages);
    }

    private static boolean matchesMovementDepartment(HrEmployeeMovement movement, String flow, String department) {
        String from = movement.getFromDepartment() == null ? null : movement.getFromDepartment().getName();
        String to = movement.getToDepartment() == null ? null : movement.getToDepartment().getName();
        if ("IN".equals(flow)) return contains(to, department);
        if ("OUT".equals(flow)) return contains(from, department);
        return contains(from, department) || contains(to, department);
    }

    private long countRoster(List<HrRosterProjectionService.ProjectedRosterItem> items, LocalDate at, HrWorkforceGroup group, String contractClass, String department) {
        if (items.isEmpty()) return 0;
        Map<String, Boolean> contracts = contractMatches(items.stream().map(i -> i.employee().getId()).toList(), at);
        return items.stream().filter(i -> group == null || group == i.employee().getWorkforceGroup())
                .filter(i -> department == null || contains(i.departmentName(), department))
                .filter(i -> contractClass == null || Boolean.TRUE.equals(contracts.get(i.employee().getId())) == contractClass.equals("FORMAL"))
                .count();
    }

    private Map<String, Boolean> contractMatches(List<String> ids, LocalDate at) {
        if (ids.isEmpty()) return Map.of();
        Set<String> idSet = new HashSet<>(ids);
        Map<String, Boolean> result = new HashMap<>();
        for (HrEmploymentContract contract : contractRepository.findForReport(ids, HrEmploymentContractStatus.EFFECTIVE, at)) {
            if (idSet.contains(contract.getEmployee().getId()) && (contract.getEffectiveUntil() == null || !contract.getEffectiveUntil().isBefore(at))) result.put(contract.getEmployee().getId(), true);
        }
        return result;
    }

    private Map<String, Boolean> eventContractMatches(List<HrEmployeeMovement> rows) {
        Map<LocalDate, List<HrEmployeeMovement>> byDate = new HashMap<>();
        rows.forEach(row -> byDate.computeIfAbsent(row.getEffectiveDate(), ignored -> new ArrayList<>()).add(row));
        Map<String, Boolean> matches = new HashMap<>();
        byDate.forEach((date, datedRows) -> {
            List<String> ids = datedRows.stream().map(row -> row.getEmployee().getId()).distinct().toList();
            Map<String, Boolean> active = contractMatches(ids, date);
            datedRows.forEach(row -> matches.put(eventContractKey(row), Boolean.TRUE.equals(active.get(row.getEmployee().getId()))));
        });
        return matches;
    }

    private List<HrEmployeeMovement> filterContracts(List<HrEmployeeMovement> rows, String status, LocalDate at) {
        Map<String, Boolean> matches = contractMatches(rows.stream().map(m -> m.getEmployee().getId()).distinct().toList(), at);
        return rows.stream().filter(m -> Boolean.TRUE.equals(matches.get(m.getEmployee().getId())) == status.equals("FORMAL")).toList();
    }

    private static List<HrEmployeeMovement> removeSuperseded(List<HrEmployeeMovement> rows) {
        Set<String> superseded = new HashSet<>();
        rows.stream().filter(m -> m.getCorrectionOfMovement() != null).forEach(m -> superseded.add(m.getCorrectionOfMovement().getId()));
        return rows.stream().filter(m -> !superseded.contains(m.getId())).toList();
    }
    private static HrReportDtos.HeadcountMonth unavailable(int month, String note) { return new HrReportDtos.HeadcountMonth(month, false, null, 0, 0, 0, 0, 0, 0, note); }
    private static String normalized(String value) { return value == null || value.isBlank() ? null : value.trim().toLowerCase(); }
    private static boolean contains(String value, String query) { return value != null && value.toLowerCase().contains(query); }
    private static String eventContractKey(HrEmployeeMovement movement) { return movement.getEmployee().getId() + "|" + movement.getEffectiveDate(); }
    private static void validateContractClass(String value) {
        if (value != null && !value.isBlank() && !Set.of("FORMAL", "UNKNOWN").contains(value)) {
            throw HrApiException.badRequest("REPORT_CONTRACT_FILTER_INVALID", "Bộ lọc hợp đồng không hợp lệ.");
        }
    }
    private static void validateYear(int year) { if (year < 2000 || year > MAX_YEAR) throw HrApiException.badRequest("REPORT_YEAR_INVALID", "Năm báo cáo không hợp lệ."); }
}
