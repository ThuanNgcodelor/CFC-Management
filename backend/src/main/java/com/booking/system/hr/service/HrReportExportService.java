package com.booking.system.hr.service;

import com.booking.system.hr.api.dto.HrReportDtos;
import com.booking.system.hr.enums.HrWorkforceGroup;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;

@Service
public class HrReportExportService {
    public byte[] exportLeavers(int year, Integer month, HrWorkforceGroup group, String contractClass,
                                String department, String reason, List<HrReportDtos.LeaverRow> rows) {
        try (XSSFWorkbook workbook = new XSSFWorkbook(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            Sheet sheet = workbook.createSheet("Danh sach nghi viec");
            Row title = sheet.createRow(0);
            title.createCell(0).setCellValue("Báo cáo nghỉ việc năm " + year);
            Row generated = sheet.createRow(1);
            generated.createCell(0).setCellValue("Xuất lúc");
            generated.createCell(1).setCellValue(LocalDateTime.now().toString());
            Row source = sheet.createRow(2);
            source.createCell(0).setCellValue("Nguồn: biến động Giảm đã xác nhận trong CFCBase");
            source.createCell(1).setCellValue("Bộ lọc: tháng=" + (month == null ? "cả năm" : month)
                    + "; nhóm=" + label(group) + "; hợp đồng=" + label(contractClass)
                    + "; đơn vị=" + label(department) + "; lý do=" + label(reason));
            String[] headers = {"Mã nhân viên", "Họ và tên", "Ngày nghỉ hiệu lực", "Phòng/xí nghiệp", "Lý do", "Số quyết định", "Nhóm hiện tại", "Hợp đồng tại ngày nghỉ"};
            Row header = sheet.createRow(3);
            for (int i = 0; i < headers.length; i++) header.createCell(i).setCellValue(headers[i]);
            int index = 4;
            for (HrReportDtos.LeaverRow value : rows) {
                Row row = sheet.createRow(index++);
                row.createCell(0).setCellValue(value.employeeCode());
                row.createCell(1).setCellValue(value.fullName());
                row.createCell(2).setCellValue(value.effectiveDate().toString());
                row.createCell(3).setCellValue(value.departmentName() == null ? "" : value.departmentName());
                row.createCell(4).setCellValue(value.reason() == null ? "" : value.reason());
                row.createCell(5).setCellValue(value.decisionNumber() == null ? "" : value.decisionNumber());
                row.createCell(6).setCellValue(workforceGroupLabel(value.currentWorkforceGroup().name()));
                row.createCell(7).setCellValue("FORMAL".equals(value.contractClassification())
                        ? "Có HĐLĐ hiệu lực" : "Chưa ghi nhận HĐLĐ hiệu lực");
            }
            for (int i = 0; i < headers.length; i++) sheet.setColumnWidth(i, Math.min(12000, Math.max(3500, headers[i].length() * 450)));
            workbook.write(output);
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException("Không thể tạo file báo cáo nghỉ việc.", exception);
        }
    }

    private static String label(Object value) { return value == null || value.toString().isBlank() ? "tất cả" : value.toString(); }
    private static String workforceGroupLabel(String group) {
        return switch (group) {
            case "OFFICE" -> "Văn phòng";
            case "GENERAL_LABOR" -> "Lao động phổ thông";
            default -> "Chưa phân loại";
        };
    }
}
