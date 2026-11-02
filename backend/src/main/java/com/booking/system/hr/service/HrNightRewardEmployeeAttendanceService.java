package com.booking.system.hr.service;

import com.booking.system.hr.api.HrApiException;
import com.booking.system.hr.api.dto.HrNightRewardDtos;
import com.booking.system.hr.api.dto.HrPageResponse;
import com.booking.system.hr.entity.HrEmployee;
import com.booking.system.hr.entity.HrProductionAttendanceShift;
import com.booking.system.hr.enums.HrAttendanceImportStatus;
import com.booking.system.hr.enums.HrProductionAttendanceShiftStatus;
import com.booking.system.hr.repository.HrEmployeeRepository;
import com.booking.system.hr.repository.HrProductionAttendanceImportRepository;
import com.booking.system.hr.repository.HrProductionAttendanceShiftRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class HrNightRewardEmployeeAttendanceService {
    private static final int SEARCH_PAGE_SIZE = 20;
    private final HrEmployeeRepository employeeRepository;
    private final HrProductionAttendanceImportRepository importRepository;
    private final HrProductionAttendanceShiftRepository shiftRepository;

    @Transactional(readOnly = true)
    public HrPageResponse<HrNightRewardDtos.EmployeeLookupResponse> search(String keyword, int page) {
        String normalized = keyword == null ? "" : keyword.trim().replace("%", "").replace("_", "");
        if (normalized.length() < 2) return HrPageResponse.empty();
        var matches = employeeRepository.searchNightRewardEmployees(
                "%" + normalized.toLowerCase(Locale.ROOT) + "%",
                PageRequest.of(Math.max(0, page), SEARCH_PAGE_SIZE, Sort.by("employeeCode")));
        return HrPageResponse.from(matches, employee -> new HrNightRewardDtos.EmployeeLookupResponse(
                employee.getEmployeeCode(), employee.getFullName()));
    }

    @Transactional(readOnly = true)
    public HrNightRewardDtos.EmployeeAttendanceHistoryResponse history(String employeeCode, String throughValue, int months) {
        if (months != 12 && months != 24) {
            throw HrApiException.badRequest("NIGHT_REWARD_HISTORY_RANGE_INVALID", "Chỉ xem được 12 hoặc 24 tháng.");
        }
        YearMonth through = parseMonth(throughValue);
        YearMonth from = through.minusMonths(months - 1L);
        String code = employeeCode == null ? "" : employeeCode.trim().toUpperCase(Locale.ROOT);
        HrEmployee employee = employeeRepository.findByEmployeeCode(code).orElseThrow(() ->
                HrApiException.notFound("HR_EMPLOYEE_NOT_FOUND", "Không tìm thấy mã nhân viên."));

        Set<String> confirmedMonths = Set.copyOf(importRepository.findConfirmedMonthsInRange(
                from.toString(), through.toString(), HrAttendanceImportStatus.CONFIRMED));
        List<HrProductionAttendanceShift> shifts = shiftRepository.findConfirmedEmployeeShiftsInRange(
                code, from.atDay(1), through.atEndOfMonth(), from.toString(), through.toString(),
                HrAttendanceImportStatus.CONFIRMED);
        Map<LocalDate, HrProductionAttendanceShift> uniqueDays = new LinkedHashMap<>();
        shifts.forEach(shift -> uniqueDays.putIfAbsent(shift.getWorkDate(), shift));
        Map<String, List<HrProductionAttendanceShift>> byMonth = new LinkedHashMap<>();
        uniqueDays.values().forEach(shift -> byMonth.computeIfAbsent(
                YearMonth.from(shift.getWorkDate()).toString(), ignored -> new ArrayList<>()).add(shift));

        List<HrNightRewardDtos.AttendanceMonthResponse> rows = new ArrayList<>(months);
        for (int index = 0; index < months; index++) {
            String month = through.minusMonths(index).toString();
            List<HrProductionAttendanceShift> monthShifts = byMonth.getOrDefault(month, List.of());
            BigDecimal work = monthShifts.stream().map(HrNightRewardEmployeeAttendanceService::officialWorkValue)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            int nightShifts = (int) monthShifts.stream().filter(HrNightRewardEmployeeAttendanceService::isNightShift).count();
            List<HrNightRewardDtos.AttendanceDayResponse> days = monthShifts.stream().map(shift ->
                    new HrNightRewardDtos.AttendanceDayResponse(shift.getWorkDate(), officialWorkValue(shift),
                            shift.getShiftCodeSnapshot(), shift.getStatus())).toList();
            rows.add(new HrNightRewardDtos.AttendanceMonthResponse(month, confirmedMonths.contains(month),
                    !monthShifts.isEmpty(), monthShifts.isEmpty() ? null : work, nightShifts, days));
        }
        return new HrNightRewardDtos.EmployeeAttendanceHistoryResponse(code, employee.getFullName(),
                through.toString(), List.copyOf(rows));
    }

    private static BigDecimal officialWorkValue(HrProductionAttendanceShift shift) {
        return shift.getStatus() == HrProductionAttendanceShiftStatus.CONFIRMED && shift.getWorkValue() != null
                ? shift.getWorkValue() : BigDecimal.ZERO;
    }

    private static boolean isNightShift(HrProductionAttendanceShift shift) {
        return officialWorkValue(shift).signum() > 0 &&
                ((shift.getNightAllowanceAmount() != null && shift.getNightAllowanceAmount().signum() > 0)
                        || "CN_18_5".equals(shift.getShiftCodeSnapshot()));
    }

    private static YearMonth parseMonth(String value) {
        try {
            return YearMonth.parse(value);
        } catch (RuntimeException ex) {
            throw HrApiException.badRequest("NIGHT_REWARD_MONTH_INVALID", "Tháng không hợp lệ, cần định dạng YYYY-MM.");
        }
    }
}
