package com.booking.system.hr.api;

import com.booking.system.dto.ApiResponse;
import com.booking.system.entity.User;
import com.booking.system.hr.api.dto.HrAttendanceSandboxDtos;
import com.booking.system.hr.service.HrAttendanceSandboxService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.MediaType;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import jakarta.validation.Valid;

import java.io.IOException;

@RestController
@RequiredArgsConstructor
@ConditionalOnProperty(prefix = "app.hr.attendance.production", name = "enabled", havingValue = "true", matchIfMissing = true)
@RequestMapping("/api/v1/hr/attendance/production/sandbox")
public class HrAttendanceSandboxController {
    private final HrAttendanceSandboxService service;
    private final HrActorResolver actors;

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<HrAttendanceSandboxDtos.Preview> preview(@RequestPart("file") MultipartFile file,
                                                                  @AuthenticationPrincipal User principal) throws IOException {
        actors.fromPrincipal(principal);
        service.validateFileSize(file.getSize());
        return ApiResponse.success(service.preview(file.getBytes()), "Đã tính thử chấm công, không lưu dữ liệu.");
    }

    @PostMapping(value = "/rematch", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<HrAttendanceSandboxDtos.Preview> rematch(@RequestPart("file") MultipartFile file,
            @Valid @RequestPart("request") HrAttendanceSandboxDtos.RematchRequest request,
            @AuthenticationPrincipal User principal) throws IOException {
        actors.fromPrincipal(principal);
        service.validateFileSize(file.getSize());
        return ApiResponse.success(service.preview(file.getBytes(), request.decisions()),
                "Đã tính lại chuỗi ca thử, không lưu dữ liệu.");
    }
}
