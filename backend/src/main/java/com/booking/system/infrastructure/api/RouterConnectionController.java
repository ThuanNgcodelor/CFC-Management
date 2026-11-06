package com.booking.system.infrastructure.api;

import com.booking.system.dto.ApiResponse;
import com.booking.system.entity.User;
import com.booking.system.infrastructure.api.RouterConnectionDtos.*;
import com.booking.system.infrastructure.service.*;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/infrastructure/router")
public class RouterConnectionController {
    private final RouterConnectionService connections;
    private final RouterOsClient client;
    private final InfrastructureService infrastructure;

    public RouterConnectionController(RouterConnectionService connections, RouterOsClient client, InfrastructureService infrastructure) {
        this.connections = connections; this.client = client; this.infrastructure = infrastructure;
    }

    @GetMapping("/connection")
    public ApiResponse<Response> connection() { return ApiResponse.success(connections.metadata(), "Cấu hình kết nối MikroTik"); }

    @PutMapping("/connection")
    public ApiResponse<Response> save(@Valid @RequestBody Request request, @AuthenticationPrincipal User actor) {
        return ApiResponse.success(connections.save(request, actor.getId(), actor.getEmail()), "Đã lưu cấu hình MikroTik");
    }

    @PostMapping("/connection/test")
    public ApiResponse<TestResponse> test(@Valid @RequestBody Request request) {
        var connection = connections.candidate(request);
        // Testing is explicit and does not persist credentials or change the monitoring toggle.
        try {
            var enabled = new RouterConnectionService.Connection(connection.baseUrl(), connection.username(), connection.password(), true);
            return ApiResponse.success(new TestResponse(true, "Kết nối thành công, đã đọc tài nguyên và interface.", client.health(enabled)), "Kết quả kiểm tra MikroTik");
        } catch (IllegalArgumentException exception) {
            return ApiResponse.success(new TestResponse(false, exception.getMessage(), null), "Kết quả kiểm tra MikroTik");
        }
    }

    @PostMapping("/refresh")
    public ApiResponse<InfrastructureDtos.StatusResponse> refresh() {
        infrastructure.pollRouter();
        return ApiResponse.success(infrastructure.routerStatus(), "Đã kiểm tra giám sát MikroTik");
    }
}
