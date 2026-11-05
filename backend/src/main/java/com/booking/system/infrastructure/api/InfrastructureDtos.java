package com.booking.system.infrastructure.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public final class InfrastructureDtos {
    private InfrastructureDtos() {}

    public record DeviceRequest(
            @NotBlank @Size(max = 120) String name,
            @NotBlank @Pattern(regexp = "ROUTER|SWITCH|ACCESS_POINT|SERVER|WAN|OTHER") String deviceType,
            @Size(max = 80) String vendor,
            @Size(max = 120) String model,
            @NotBlank @Pattern(regexp = "(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)") String managementIp,
            @Size(max = 80) String firmwareVersion,
            @Size(max = 120) String site,
            @Size(max = 180) String location,
            @Size(max = 1000) String notes) {}

    public record DeviceResponse(String id, String name, String deviceType, String vendor, String model,
            String managementIp, String firmwareVersion, String site, String location, String notes,
            boolean active, StatusResponse status) {}

    public record StatusResponse(String status, String softwareVersion, String uptime, Integer cpuLoad,
            Long freeMemory, Long totalMemory, Integer interfacesUp, Integer interfacesDown,
            String checkedAt, String lastError) {}

    public record LogResponse(String timestamp, String deviceIp, String severity, String message) {}
    public record PageResponse<T>(java.util.List<T> content, int page, int size, long totalElements) {}
}
