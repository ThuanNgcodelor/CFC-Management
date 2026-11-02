package com.booking.system.hr;

import com.booking.system.hr.entity.HrEmployee;
import com.booking.system.hr.entity.HrProductionAttendanceShift;
import com.booking.system.hr.enums.HrAttendanceImportStatus;
import com.booking.system.hr.enums.HrProductionAttendanceShiftStatus;
import com.booking.system.hr.repository.HrEmployeeRepository;
import com.booking.system.hr.repository.HrProductionAttendanceImportRepository;
import com.booking.system.hr.repository.HrProductionAttendanceShiftRepository;
import com.booking.system.hr.service.HrNightRewardEmployeeAttendanceService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HrNightRewardEmployeeAttendanceServiceTest {
    @Mock HrEmployeeRepository employeeRepository;
    @Mock HrProductionAttendanceImportRepository importRepository;
    @Mock HrProductionAttendanceShiftRepository shiftRepository;
    @InjectMocks HrNightRewardEmployeeAttendanceService service;

    @Test
    void searchesEmployeesOutsideTheSelectedRewardMonthWithABoundedPage() {
        HrEmployee employee = employee();
        when(employeeRepository.searchNightRewardEmployees(eq("%thuận%"), any()))
                .thenReturn(new PageImpl<>(List.of(employee)));

        var result = service.search("Thuận", 0);

        assertThat(result.content()).singleElement().satisfies(value -> {
            assertThat(value.employeeCode()).isEqualTo("A001");
            assertThat(value.employeeName()).isEqualTo("Nguyễn Trung Thuận");
        });
        verify(employeeRepository).searchNightRewardEmployees("%thuận%",
                PageRequest.of(0, 20, Sort.by("employeeCode")));
        assertThat(service.search("A", 0).content()).isEmpty();
    }

    @Test
    void separatesMissingImportFromMissingEmployeeAndDeduplicatesConfirmedDays() {
        when(employeeRepository.findByEmployeeCode("A001")).thenReturn(Optional.of(employee()));
        when(importRepository.findConfirmedMonthsInRange("2025-11", "2026-10", HrAttendanceImportStatus.CONFIRMED))
                .thenReturn(List.of("2026-09", "2026-10"));
        HrProductionAttendanceShift night = shift("2026-09-01", "1.5", "CN_18_5", HrProductionAttendanceShiftStatus.CONFIRMED);
        HrProductionAttendanceShift duplicate = shift("2026-09-01", "2", "CN_18_5", HrProductionAttendanceShiftStatus.CONFIRMED);
        HrProductionAttendanceShift missingPunch = shift("2026-09-02", "1", "CN_DAY", HrProductionAttendanceShiftStatus.NO_PUNCH);
        when(shiftRepository.findConfirmedEmployeeShiftsInRange(
                "A001", LocalDate.of(2025, 11, 1), LocalDate.of(2026, 10, 31),
                "2025-11", "2026-10", HrAttendanceImportStatus.CONFIRMED))
                .thenReturn(List.of(night, duplicate, missingPunch));

        var history = service.history("a001", "2026-10", 12);

        assertThat(history.months()).hasSize(12);
        assertThat(history.months().getFirst().hasConfirmedImport()).isTrue();
        assertThat(history.months().getFirst().hasEmployeeData()).isFalse();
        assertThat(history.months().get(1)).satisfies(september -> {
            assertThat(september.attendanceMonth()).isEqualTo("2026-09");
            assertThat(september.totalWorkValue()).isEqualByComparingTo("1.5");
            assertThat(september.nightShifts()).isEqualTo(1);
            assertThat(september.days()).hasSize(2);
        });
        assertThat(history.months().get(2).hasConfirmedImport()).isFalse();
        assertThat(history.months().get(2).totalWorkValue()).isNull();
    }

    @Test
    void rejectsUnboundedHistoryRanges() {
        assertThatThrownBy(() -> service.history("A001", "2026-10", 120))
                .hasMessageContaining("12 hoặc 24 tháng");
        verify(shiftRepository, never()).findConfirmedEmployeeShiftsInRange(
                any(), any(), any(), any(), any(), any());
    }

    private HrEmployee employee() {
        HrEmployee employee = new HrEmployee();
        employee.setEmployeeCode("A001");
        employee.setFullName("Nguyễn Trung Thuận");
        return employee;
    }

    private HrProductionAttendanceShift shift(String date, String work, String code,
                                               HrProductionAttendanceShiftStatus status) {
        HrProductionAttendanceShift shift = new HrProductionAttendanceShift();
        shift.setEmployeeCode("A001");
        shift.setWorkDate(LocalDate.parse(date));
        shift.setWorkValue(new BigDecimal(work));
        shift.setShiftCodeSnapshot(code);
        shift.setStatus(status);
        return shift;
    }
}
