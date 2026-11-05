package com.booking.system.infrastructure.api;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
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
    public record IpObservation(String ipAddress, String macAddress, String hostName, String interfaceName,
            String source, String status, String firstSeenAt, String lastSeenAt) {}
    public record IpConflict(String ipAddress, java.util.List<String> macAddresses, java.util.List<String> sources,
            String reviewStatus, String note, String lastSeenAt) {}
    public record IpReviewRequest(@NotBlank @Pattern(regexp = "CONFIRMED|NOT_CONFLICT|RESOLVED|OPEN") String status,
            @Size(max = 1000) String note) {}
    public record TopologyLinkRequest(@NotBlank String sourceDeviceId, @Size(max = 120) String sourcePort,
            @NotBlank String targetDeviceId, @Size(max = 120) String targetPort,
            @Pattern(regexp = "ETHERNET|FIBER|WIRELESS|OTHER") String linkType,
            @Min(1) @Max(4094) Integer vlanId, @Pattern(regexp = "UNVERIFIED|MANUAL_VERIFIED") String verificationStatus,
            @Size(max = 1000) String notes) {}
    public record TopologyLink(String id, String sourceDeviceId, String sourceName, String sourcePort,
            String targetDeviceId, String targetName, String targetPort, String linkType, Integer vlanId,
            String verificationStatus, String notes, String createdBy) {}
    public record AssetRequest(@NotBlank @Size(max = 80) String assetTag,
            @NotBlank @Pattern(regexp = "WORKSTATION|PRINTER|PHONE|SERVER|CAMERA|OTHER") String assetType,
            @NotBlank @Size(max = 180) String name, @Pattern(regexp = "(?i)([0-9a-f]{2}:){5}[0-9a-f]{2}|^$") String macAddress,
            @Pattern(regexp = "^$|(?:(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)\\.){3}(?:25[0-5]|2[0-4]\\d|1?\\d?\\d)") String ipAddress,
            @Size(max = 180) String department, @Size(max = 200) String location,
            @Size(max = 180) String responsiblePerson,
            @Pattern(regexp = "ACTIVE|STORED|RETIRED|SHARED|BYOD") String status,
            @Size(max = 1000) String notes) {}
    public record Asset(String id, String assetTag, String assetType, String name, String macAddress,
            String ipAddress, String department, String location, String responsiblePerson, String status, String notes) {}
    public record AssetAssignment(String id, String department, String location, String responsiblePerson,
            String validFrom, String validTo, String changedBy) {}
    public record PageResponse<T>(java.util.List<T> content, int page, int size, long totalElements) {}
}
