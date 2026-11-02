package com.booking.system.hr.service;

import com.booking.system.hr.api.dto.HrProductionAttendanceDtos;
import com.booking.system.hr.enums.HrAttendancePolicyGroup;
import com.booking.system.hr.importer.HrProductionAttendanceWorkbookParser;
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
        when(attendance.shiftPolicies()).thenReturn(List.of(new HrProductionAttendanceDtos.ShiftPolicyResponse(
                "day", "CN_DAY", "Ca ngày", HrAttendancePolicyGroup.PRODUCTION_WORKER,
                LocalTime.of(6, 0), LocalTime.of(17, 0), LocalTime.of(5, 0), LocalTime.of(8, 0),
                LocalTime.of(16, 0), LocalTime.of(18, 0), false, BigDecimal.ZERO, false,
                10, true, LocalDate.of(2026, 1, 1), null, 0)));
        when(attendance.workCreditRules()).thenReturn(List.of(new HrProductionAttendanceDtos.WorkCreditRuleResponse(
                "full", "day", "Đủ công", LocalTime.of(16, 0), LocalTime.of(18, 0),
                BigDecimal.ONE, 10, true, LocalDate.of(2026, 1, 1), null, 0)));
        var service = new HrAttendanceSandboxService(new HrProductionAttendanceWorkbookParser(),
                new HrProductionShiftMatcher(), attendance);

        var preview = service.preview(workbook());

        assertThat(preview.attendanceMonth()).isEqualTo("2026-09");
        assertThat(preview.totalRows()).isEqualTo(2);
        assertThat(preview.totalPunches()).isEqualTo(5);
        assertThat(preview.days()).extracting(day -> day.status())
                .containsExactly("NEEDS_REVIEW", "AUTO_MATCHED");
        assertThat(preview.days().get(0).checkInAt()).isEqualTo(LocalDateTime.parse("2026-09-01T05:55:00"));
        assertThat(preview.days().get(0).checkOutAt()).isEqualTo(LocalDateTime.parse("2026-09-01T17:16:00"));
        verify(attendance).shiftPolicies();
        verify(attendance).workCreditRules();
        verifyNoMoreInteractions(attendance);
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
