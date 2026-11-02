package com.booking.system.hr.service;

import com.booking.system.hr.api.dto.HrProductionAttendanceDtos;
import com.booking.system.hr.api.dto.HrAttendanceSandboxDtos;
import com.booking.system.hr.enums.HrAttendancePolicyGroup;
import com.booking.system.hr.importer.HrProductionAttendanceWorkbookParser;
import com.booking.system.hr.repository.HrEmployeeRepository;
import com.booking.system.hr.repository.HrEmployeeAttendancePolicyRepository;
import com.booking.system.hr.repository.HrAttendanceExemptionRepository;
import com.booking.system.hr.entity.HrEmployee;
import com.booking.system.hr.entity.HrAttendanceExemption;
import com.booking.system.hr.enums.HrAttendanceExemptionStatus;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class HrAttendanceSandboxServiceTest {
    @Test
    void previewsThreePunchReviewWithoutCreatingAnImport() throws Exception {
        var attendance = mock(HrProductionAttendanceService.class);
        var employees = mock(HrEmployeeRepository.class);
        var known = mock(HrEmployee.class);
        when(known.getEmployeeCode()).thenReturn("C743");
        when(employees.findAttendanceEmployeesByCodes(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of(known));
        when(attendance.shiftPolicies()).thenReturn(List.of(new HrProductionAttendanceDtos.ShiftPolicyResponse(
                "day", "CN_DAY", "Ca ngày", HrAttendancePolicyGroup.PRODUCTION_WORKER,
                LocalTime.of(6, 0), LocalTime.of(17, 0), LocalTime.of(5, 0), LocalTime.of(8, 0),
                LocalTime.of(16, 0), LocalTime.of(18, 0), false, BigDecimal.ZERO, false,
                10, true, LocalDate.of(2026, 1, 1), null, 0)));
        when(attendance.workCreditRules()).thenReturn(List.of(new HrProductionAttendanceDtos.WorkCreditRuleResponse(
                "full", "day", "Đủ công", LocalTime.of(16, 0), LocalTime.of(18, 0),
                BigDecimal.ONE, 10, true, LocalDate.of(2026, 1, 1), null, 0)));
        var service = new HrAttendanceSandboxService(new HrProductionAttendanceWorkbookParser(),
                new HrProductionShiftMatcher(), attendance, employees,
                mock(HrEmployeeAttendancePolicyRepository.class), mock(HrAttendanceExemptionRepository.class));

        var preview = service.preview(workbook());

        assertThat(preview.attendanceMonth()).isEqualTo("2026-09");
        assertThat(preview.totalRows()).isEqualTo(2);
        assertThat(preview.totalPunches()).isEqualTo(5);
        assertThat(preview.days()).extracting(day -> day.status())
                .containsExactly("NEEDS_REVIEW", "NEEDS_REVIEW");
        assertThat(preview.days().get(1).explanation()).contains("Không tìm thấy mã nhân viên");
        assertThat(preview.days().get(0).checkInAt()).isEqualTo(LocalDateTime.parse("2026-09-01T05:55:00"));
        assertThat(preview.days().get(0).checkOutAt()).isEqualTo(LocalDateTime.parse("2026-09-01T17:16:00"));
        verify(attendance).shiftPolicies();
        verify(attendance).workCreditRules();
        verifyNoMoreInteractions(attendance);
    }

    @Test
    void rematchesAfterDraftDecisionWithoutPersistingAnything() throws Exception {
        var attendance = mock(HrProductionAttendanceService.class);
        var employees = mock(HrEmployeeRepository.class);
        when(employees.findAttendanceEmployeesByCodes(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of());
        when(attendance.shiftPolicies()).thenReturn(List.of(new HrProductionAttendanceDtos.ShiftPolicyResponse(
                "day", "CN_DAY", "Ca ngày", HrAttendancePolicyGroup.PRODUCTION_WORKER,
                LocalTime.of(6, 0), LocalTime.of(17, 0), LocalTime.of(5, 0), LocalTime.of(8, 0),
                LocalTime.of(16, 0), LocalTime.of(18, 0), false, BigDecimal.ZERO, false,
                10, true, LocalDate.of(2026, 1, 1), null, 0)));
        when(attendance.workCreditRules()).thenReturn(List.of(new HrProductionAttendanceDtos.WorkCreditRuleResponse(
                "full", "day", "Đủ công", LocalTime.of(16, 0), LocalTime.of(18, 0),
                BigDecimal.ONE, 10, true, LocalDate.of(2026, 1, 1), null, 0)));
        var service = new HrAttendanceSandboxService(new HrProductionAttendanceWorkbookParser(),
                new HrProductionShiftMatcher(), attendance, employees,
                mock(HrEmployeeAttendancePolicyRepository.class), mock(HrAttendanceExemptionRepository.class));
        byte[] file = workbook();
        var first = service.preview(file);
        var a001 = first.days().stream().filter(day -> day.employeeCode().equals("A001")).findFirst().orElseThrow();
        var decision = new HrAttendanceSandboxDtos.Decision("A001", a001.workDate(),
                HrProductionAttendanceDtos.DecisionAction.CONFIRM, "CN_DAY",
                a001.punches().get(0).id(), a001.punches().get(1).id(),
                BigDecimal.ONE, BigDecimal.ZERO, "Kiểm tra thủ công");

        var second = service.preview(file, List.of(decision));

        assertThat(second.days()).hasSize(2);
        assertThat(second.days().stream().filter(day -> day.employeeCode().equals("A001")).findFirst().orElseThrow().status())
                .isEqualTo("CONFIRMED");
        assertThat(second.days().stream().filter(day -> day.employeeCode().equals("C743")).findFirst().orElseThrow().status())
                .isEqualTo("NEEDS_REVIEW");
        verify(employees, org.mockito.Mockito.times(2)).findAttendanceEmployeesByCodes(org.mockito.ArgumentMatchers.anyCollection());
        verifyNoMoreInteractions(employees);
    }

    @Test
    void confirmedExemptionUsesTheSameExcludedStatusAsProduction() throws Exception {
        var attendance = mock(HrProductionAttendanceService.class);
        var employees = mock(HrEmployeeRepository.class);
        var exemptions = mock(HrAttendanceExemptionRepository.class);
        var known = mock(HrEmployee.class);
        when(known.getEmployeeCode()).thenReturn("C743");
        when(known.getId()).thenReturn("employee-c743");
        when(employees.findAttendanceEmployeesByCodes(org.mockito.ArgumentMatchers.anyCollection()))
                .thenReturn(List.of(known));
        var exemption = new HrAttendanceExemption();
        exemption.setEmployeeCode("C743");
        exemption.setValidFrom(LocalDate.of(2026, 9, 1));
        exemption.setValidTo(LocalDate.of(2026, 9, 1));
        exemption.setStatus(HrAttendanceExemptionStatus.CONFIRMED);
        when(exemptions.findEffective(org.mockito.ArgumentMatchers.anyList(),
                org.mockito.ArgumentMatchers.eq(HrAttendanceExemptionStatus.CONFIRMED),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any()))
                .thenReturn(List.of(exemption));
        when(attendance.shiftPolicies()).thenReturn(List.of(new HrProductionAttendanceDtos.ShiftPolicyResponse(
                "day", "CN_DAY", "Ca ngày", HrAttendancePolicyGroup.PRODUCTION_WORKER,
                LocalTime.of(6, 0), LocalTime.of(17, 0), LocalTime.of(5, 0), LocalTime.of(8, 0),
                LocalTime.of(16, 0), LocalTime.of(18, 0), false, BigDecimal.ZERO, false,
                10, true, LocalDate.of(2026, 1, 1), null, 0)));
        when(attendance.workCreditRules()).thenReturn(List.of());
        var service = new HrAttendanceSandboxService(new HrProductionAttendanceWorkbookParser(),
                new HrProductionShiftMatcher(), attendance, employees,
                mock(HrEmployeeAttendancePolicyRepository.class), exemptions);

        var preview = service.preview(workbook());

        assertThat(preview.days().stream().filter(day -> day.employeeCode().equals("C743")).findFirst().orElseThrow().status())
                .isEqualTo("EXCLUDED");
        assertThat(preview.days().stream().filter(day -> day.employeeCode().equals("A001")).findFirst().orElseThrow().status())
                .isEqualTo("NEEDS_REVIEW");
    }

    private byte[] workbook() throws Exception {
        try (var book = new XSSFWorkbook(); var bytes = new ByteArrayOutputStream()) {
            Sheet sheet = book.createSheet("Giờ chấm công");
            Row header = sheet.createRow(1);
            header.createCell(1).setCellValue("Mã nhân viên");
            header.createCell(2).setCellValue("Tên nhân viên");
            header.createCell(4).setCellValue("Ngày");
            header.createCell(6).setCellValue("Lần chấm 1");
            header.createCell(7).setCellValue("Lần chấm 2");
            header.createCell(8).setCellValue("Lần chấm 3");
            addDay(sheet.createRow(2), "C743", "Phùng Ngọc Ẩn", "05:55", "17:13", "17:16");
            addDay(sheet.createRow(3), "A001", "Nguyễn Trung Thuận", "06:00", "17:00", null);
            book.write(bytes);
            return bytes.toByteArray();
        }
    }

    private void addDay(Row row, String code, String name, String first, String second, String third) {
        row.createCell(1).setCellValue(code);
        row.createCell(2).setCellValue(name);
        row.createCell(4).setCellValue("2026-09-01");
        row.createCell(6).setCellValue(first);
        row.createCell(7).setCellValue(second);
        if (third != null) row.createCell(8).setCellValue(third);
    }
}
