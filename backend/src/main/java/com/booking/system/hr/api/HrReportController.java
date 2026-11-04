package com.booking.system.hr.api;

import com.booking.system.dto.ApiResponse;
import com.booking.system.hr.api.dto.HrReportDtos;
import com.booking.system.hr.enums.HrWorkforceGroup;
import com.booking.system.hr.service.HrReportExportService;
import com.booking.system.hr.service.HrReportService;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/v1/hr/reports")
public class HrReportController {
    private final HrReportService reportService;
    private final HrReportExportService exportService;

    public HrReportController(HrReportService reportService, HrReportExportService exportService) {
        this.reportService = reportService;
        this.exportService = exportService;
    }

    @GetMapping("/headcount")
    public ResponseEntity<ApiResponse<HrReportDtos.HeadcountReport>> headcount(
            @RequestParam int year, @RequestParam(required = false) HrWorkforceGroup group,
            @RequestParam(required = false) String contractClass, @RequestParam(required = false) String department) {
        return ResponseEntity.ok(ApiResponse.success(reportService.headcount(year, group, contractClass, department), "Lấy báo cáo quân số thành công"));
    }

    @GetMapping("/leavers")
    public ResponseEntity<ApiResponse<HrReportDtos.LeaverReport>> leavers(
            @RequestParam int year, @RequestParam(required = false) Integer month,
            @RequestParam(required = false) HrWorkforceGroup group, @RequestParam(required = false) String contractClass,
            @RequestParam(required = false) String department, @RequestParam(required = false) String reason,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(reportService.leavers(year, month, group, contractClass, department, reason, page, size), "Lấy danh sách nghỉ việc thành công"));
    }

    @GetMapping("/movement-sources")
    public ResponseEntity<ApiResponse<HrReportDtos.MovementSourceReport>> movementSources(
            @RequestParam int year, @RequestParam int month, @RequestParam String type,
            @RequestParam(required = false) String flow, @RequestParam(required = false) HrWorkforceGroup group,
            @RequestParam(required = false) String contractClass, @RequestParam(required = false) String department,
            @RequestParam(required = false) String reason, @RequestParam(defaultValue = "false") boolean uniqueOnly,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ResponseEntity.ok(ApiResponse.success(reportService.movementSources(year, month, type, flow, uniqueOnly,
                group, contractClass, department, reason, page, size), "Lấy biến động nguồn thành công"));
    }

    @GetMapping("/leavers/export")
    public ResponseEntity<byte[]> exportLeavers(
            @RequestParam int year, @RequestParam(required = false) Integer month,
            @RequestParam(required = false) HrWorkforceGroup group, @RequestParam(required = false) String contractClass,
            @RequestParam(required = false) String department, @RequestParam(required = false) String reason) {
        byte[] bytes = exportService.exportLeavers(year, month, group, contractClass, department, reason,
                reportService.allLeavers(year, month, group, contractClass, department, reason));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename("bao-cao-nghi-viec-" + year + ".xlsx", StandardCharsets.UTF_8).build().toString()).body(bytes);
    }
}
