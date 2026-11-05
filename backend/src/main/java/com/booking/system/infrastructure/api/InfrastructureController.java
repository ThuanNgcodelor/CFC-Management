package com.booking.system.infrastructure.api;

import com.booking.system.dto.ApiResponse;
import com.booking.system.infrastructure.api.InfrastructureDtos.*;
import com.booking.system.infrastructure.service.InfrastructureService;
import com.booking.system.infrastructure.service.NetworkOperationsService;
import com.booking.system.entity.User;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/infrastructure")
public class InfrastructureController {
    private final InfrastructureService service;
    private final NetworkOperationsService operations;
    public InfrastructureController(InfrastructureService service, NetworkOperationsService operations) { this.service = service; this.operations = operations; }

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

    @PostMapping("/ipam/sync")
    public ApiResponse<Integer> syncIpam() { return ApiResponse.success(operations.syncIpam(), "Đã cập nhật quan sát DHCP/ARP"); }
    @GetMapping("/ipam/observations")
    public ApiResponse<java.util.List<IpObservation>> observations(@RequestParam(required = false) String q) { return ApiResponse.success(operations.observations(q), "Quan sát IP gần đây"); }
    @GetMapping("/ipam/conflicts")
    public ApiResponse<java.util.List<IpConflict>> conflicts() { return ApiResponse.success(operations.conflicts(), "Ứng viên xung đột IP"); }
    @PutMapping("/ipam/conflicts/{ip}")
    public ApiResponse<Void> reviewIp(@PathVariable String ip, @Valid @RequestBody IpReviewRequest request, @AuthenticationPrincipal User actor) { operations.reviewIp(ip, request, actor.getEmail()); return ApiResponse.success(null, "Đã cập nhật xác minh IP"); }

    @GetMapping("/topology/links")
    public ApiResponse<java.util.List<TopologyLink>> topology() { return ApiResponse.success(operations.topology(), "Các liên kết topology"); }
    @PostMapping("/topology/links")
    public ApiResponse<TopologyLink> addLink(@Valid @RequestBody TopologyLinkRequest request, @AuthenticationPrincipal User actor) { return ApiResponse.success(operations.addLink(request, actor.getEmail()), "Đã thêm liên kết thủ công"); }
    @PutMapping("/topology/links/{id}/verification")
    public ApiResponse<Void> verifyLink(@PathVariable String id, @RequestParam String status) { operations.verifyLink(id, status); return ApiResponse.success(null, "Đã cập nhật trạng thái xác minh"); }

    @GetMapping("/assets")
    public ApiResponse<java.util.List<Asset>> assets(@RequestParam(required = false) String q) { return ApiResponse.success(operations.assets(q), "Danh mục tài sản mạng"); }
    @PostMapping("/assets")
    public ApiResponse<Asset> addAsset(@Valid @RequestBody AssetRequest request, @AuthenticationPrincipal User actor) { return ApiResponse.success(operations.saveAsset(null, request, actor.getEmail()), "Đã thêm tài sản"); }
    @PutMapping("/assets/{id}")
    public ApiResponse<Asset> updateAsset(@PathVariable String id, @Valid @RequestBody AssetRequest request, @AuthenticationPrincipal User actor) { return ApiResponse.success(operations.saveAsset(id, request, actor.getEmail()), "Đã cập nhật tài sản"); }
    @GetMapping("/assets/{id}/history")
    public ApiResponse<java.util.List<AssetAssignment>> assetHistory(@PathVariable String id) { return ApiResponse.success(operations.assetHistory(id), "Lịch sử phân công tài sản"); }
}
