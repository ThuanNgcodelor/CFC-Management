package com.booking.system.hr;

import com.booking.system.hr.entity.HrEmployee;
import com.booking.system.hr.entity.HrEmployeeMovement;
import com.booking.system.hr.enums.HrEmploymentStatus;
import com.booking.system.hr.enums.HrMovementStatus;
import com.booking.system.hr.enums.HrMovementType;
import com.booking.system.hr.importer.HrImportActor;
import com.booking.system.hr.repository.HrAuditEventRepository;
import com.booking.system.hr.repository.HrEmployeeMovementRepository;
import com.booking.system.hr.repository.HrEmployeeRepository;
import com.booking.system.hr.repository.HrExcelImportRowRepository;
import com.booking.system.hr.repository.HrMonthlyRosterItemRepository;
import com.booking.system.hr.repository.HrMonthlyRosterRepository;
import com.booking.system.hr.repository.HrProbationCandidateRepository;
import com.booking.system.hr.service.HrEmploymentContractService;
import com.booking.system.hr.service.HrLeaveEntitlementService;
import com.booking.system.hr.service.HrWorkforceService;
import com.booking.system.hr.importer.HrImportJsonCodec;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HrWorkforceIntakeServiceTest {
    @Mock private HrEmployeeRepository employeeRepository;
    @Mock private HrEmployeeMovementRepository movementRepository;
    @Mock private HrMonthlyRosterRepository rosterRepository;
    @Mock private HrMonthlyRosterItemRepository rosterItemRepository;
    @Mock private HrExcelImportRowRepository importRowRepository;
    @Mock private HrProbationCandidateRepository probationCandidateRepository;
    @Mock private HrAuditEventRepository auditRepository;
    @Mock private HrImportJsonCodec jsonCodec;
    @Mock private EntityManager entityManager;
    @Mock private HrLeaveEntitlementService leaveEntitlementService;
    @Mock private HrEmploymentContractService employmentContractService;
    @InjectMocks private HrWorkforceService service;

    @Test
    void stoppingIntakeCancelsPendingIncreaseAndKeepsEmployeeRecord() throws Exception {
        HrEmployee employee = employee(HrEmploymentStatus.DRAFT);
        HrEmployeeMovement increase = new HrEmployeeMovement();
        increase.setId("movement-1");
        increase.setStatus(HrMovementStatus.DRAFT);
        increase.setMovementType(HrMovementType.INCREASE);
        when(employeeRepository.findDetailByIdForUpdate("employee-1")).thenReturn(Optional.of(employee));
        when(movementRepository.findByEmployeeAndStatusAndTypeForUpdate(
                "employee-1", HrMovementStatus.DRAFT, HrMovementType.INCREASE)).thenReturn(List.of(increase));
        when(jsonCodec.write(any())).thenReturn("[]");

        service.stopEmployeeIntake("employee-1", 0, "Không tiếp tục tiếp nhận", actor());

        assertThat(employee.getEmploymentStatus()).isEqualTo(HrEmploymentStatus.INTAKE_STOPPED);
        assertThat(increase.getStatus()).isEqualTo(HrMovementStatus.CANCELLED);
        assertThat(increase.getCancelledAt()).isNotNull();
        verify(movementRepository).saveAll(List.of(increase));
        verify(entityManager).flush();
    }

    @Test
    void restoringIntakeReturnsStoppedEmployeeToAwaitingIntake() throws Exception {
        HrEmployee employee = employee(HrEmploymentStatus.INTAKE_STOPPED);
        when(employeeRepository.findDetailByIdForUpdate("employee-1")).thenReturn(Optional.of(employee));
        when(jsonCodec.write(any())).thenReturn("[]");

        service.restoreEmployeeIntake("employee-1", 0, "Đã rà soát", actor());

        assertThat(employee.getEmploymentStatus()).isEqualTo(HrEmploymentStatus.DRAFT);
        verify(entityManager).flush();
    }

    private static HrEmployee employee(HrEmploymentStatus status) {
        HrEmployee employee = new HrEmployee();
        employee.setId("employee-1");
        employee.setEmploymentStatus(status);
        employee.setRowVersion(0);
        return employee;
    }

    private static HrImportActor actor() {
        return new HrImportActor("USER:manager", "HR Manager", "MANAGER");
    }
}
