package com.booking.system.infrastructure.api;

import com.booking.system.dto.ApiResponse;
import com.booking.system.infrastructure.api.InfrastructureDtos.*;
import com.booking.system.infrastructure.service.InfrastructureService;
import com.booking.system.entity.User;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/infrastructure")
public class InfrastructureController {
    private final InfrastructureService service;
    public InfrastructureController(InfrastructureService service) { this.service = service; }

    @GetMapping("/devices")
    public ApiResponse<PageResponse<DeviceResponse>> devices(@RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "50") int size, @RequestParam(required = false) String q) {
        return ApiResponse.success(service.devices(page, size, q), "Danh sách thiết bị hạ tầng");
    }
    @PostMapping("/devices")
    public ApiResponse<DeviceResponse> create(@Valid @RequestBody DeviceRequest request, @AuthenticationPrincipal User actor) {
        return ApiResponse.success(service.create(request, actor.getId(), actor.getEmail()), "Đã thêm thiết bị");
    }
    @PutMapping("/devices/{id}")
    public ApiResponse<DeviceResponse> update(@PathVariable String id, @Valid @RequestBody DeviceRequest request, @AuthenticationPrincipal User actor) {
        return ApiResponse.success(service.update(id, request, actor.getId(), actor.getEmail()), "Đã cập nhật thiết bị");
    }
    @DeleteMapping("/devices/{id}")
    public ApiResponse<Void> archive(@PathVariable String id, @AuthenticationPrincipal User actor) { service.archive(id, actor.getId(), actor.getEmail()); return ApiResponse.success(null, "Đã ẩn thiết bị"); }
    @GetMapping("/router/status")
    public ApiResponse<StatusResponse> routerStatus() { return ApiResponse.success(service.routerStatus(), "Trạng thái giám sát RouterOS"); }
    @GetMapping("/logs")
    public ApiResponse<PageResponse<LogResponse>> logs(@RequestParam(required = false) String from,
            @RequestParam(required = false) String to, @RequestParam(required = false) String q,
            @RequestParam(required = false) String deviceIp, @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "100") int size) {
        return ApiResponse.success(service.logs(from, to, q, deviceIp, page, size), "Nhật ký hạ tầng");
    }
}
