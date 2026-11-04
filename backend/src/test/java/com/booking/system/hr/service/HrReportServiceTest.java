package com.booking.system.hr.service;

import com.booking.system.hr.entity.HrEmployee;
import com.booking.system.hr.entity.HrEmployeeMovement;
import com.booking.system.hr.entity.HrMonthlyRoster;
import com.booking.system.hr.enums.HrEmploymentContractStatus;
import com.booking.system.hr.enums.HrMovementStatus;
import com.booking.system.hr.enums.HrMovementType;
import com.booking.system.hr.enums.HrWorkforceGroup;
import com.booking.system.hr.repository.HrEmployeeMovementRepository;
import com.booking.system.hr.repository.HrEmploymentContractRepository;
import com.booking.system.hr.repository.HrMonthlyRosterRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HrReportServiceTest {
    @Mock HrRosterProjectionService projectionService;
    @Mock HrMonthlyRosterRepository rosterRepository;
    @Mock HrEmployeeMovementRepository movementRepository;
    @Mock HrEmploymentContractRepository contractRepository;

    @Test
    void reportsMonthsBeforeBaselineAsUnavailableInsteadOfZero() {
        when(rosterRepository.findFirstByOrderByPeriodStartAsc()).thenReturn(Optional.empty());
        HrReportService service = service();

        var report = service.headcount(2026, null, null, null);

        assertThat(report.baselineFrom()).isNull();
        assertThat(report.months()).hasSize(0);
    }

    @Test
    void correctionReplacesTheOriginalMovementInMonthlyCounts() {
        int year = LocalDate.now(ZoneId.of("Asia/Ho_Chi_Minh")).getYear();
        HrMonthlyRoster baseline = new HrMonthlyRoster();
        baseline.setPeriodStart(LocalDate.of(year, 1, 1));
        when(rosterRepository.findFirstByOrderByPeriodStartAsc()).thenReturn(Optional.of(baseline));
        when(projectionService.projectedItemsForPeriods(anyList())).thenReturn(Map.of());
        when(movementRepository.findConfirmedForProjection(eq(HrMovementStatus.CONFIRMED), anyCollection()))
                .thenReturn(List.of(movement("original", HrMovementType.INCREASE, LocalDate.of(year, 1, 12), null),
                        movement("correction", HrMovementType.REHIRE, LocalDate.of(year, 2, 2), movement("original", HrMovementType.INCREASE, LocalDate.of(year, 1, 12), null))));
        when(contractRepository.findForReport(anyList(), eq(HrEmploymentContractStatus.EFFECTIVE), any(LocalDate.class))).thenReturn(List.of());

        var report = service().headcount(year, null, null, null);

        assertThat(report.months().get(0).increases()).isZero();
        assertThat(report.months().get(1).increases()).isEqualTo(1);
        var sources = service().movementSources(year, 2, "INCREASE", null, true,
                null, null, null, null, 0, 20);
        assertThat(sources.totalElements()).isEqualTo(1);
        assertThat(sources.content()).singleElement().satisfies(row -> assertThat(row.movementType()).isEqualTo("REHIRE"));
    }

    private HrReportService service() {
        return new HrReportService(projectionService, rosterRepository, movementRepository, contractRepository);
    }

    private static HrEmployeeMovement movement(String id, HrMovementType type, LocalDate date, HrEmployeeMovement correction) {
        HrEmployee employee = new HrEmployee();
        employee.setId("employee-1");
        employee.setEmployeeCode("A001");
        employee.setFullName("Nhân viên thử nghiệm");
        employee.setWorkforceGroup(HrWorkforceGroup.GENERAL_LABOR);
        HrEmployeeMovement movement = new HrEmployeeMovement();
        movement.setId(id);
        movement.setEmployee(employee);
        movement.setMovementType(type);
        movement.setStatus(HrMovementStatus.CONFIRMED);
        movement.setEffectiveDate(date);
        movement.setCorrectionOfMovement(correction);
        return movement;
    }
}
