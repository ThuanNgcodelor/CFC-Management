package com.booking.system.infrastructure.service;

import com.booking.system.infrastructure.api.InfrastructureDtos.*;
import com.booking.system.infrastructure.config.InfrastructureProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;

@Service
public class InfrastructureService {
    private record DeviceRow(String id, String name, String deviceType, String vendor, String model,
            String managementIp, String firmwareVersion, String site, String location, String notes, boolean active) {
        DeviceResponse toResponse(StatusResponse status) {
            return new DeviceResponse(id, name, deviceType, vendor, model, managementIp, firmwareVersion, site, location, notes, active, status);
        }
    }
    private final JdbcTemplate jdbc;
    private final InfrastructureProperties properties;
    private final ObjectMapper mapper;
    private final RestClient.Builder restBuilder;

    public InfrastructureService(JdbcTemplate jdbc, InfrastructureProperties properties, ObjectMapper mapper, RestClient.Builder restBuilder) {
        this.jdbc = jdbc; this.properties = properties; this.mapper = mapper; this.restBuilder = restBuilder;
    }

    @Transactional(readOnly = true)
    public PageResponse<DeviceResponse> devices(int page, int size, String query) {
        int safePage = Math.max(0, page), safeSize = Math.max(1, Math.min(size, 100));
        String term = query == null ? "" : query.trim();
        String filter = "%" + term + "%";
        long total = jdbc.queryForObject("SELECT COUNT(*) FROM network_devices WHERE active=TRUE AND (name LIKE ? OR management_ip LIKE ? OR vendor LIKE ? OR model LIKE ?)", Long.class, filter, filter, filter, filter);
        List<DeviceRow> rows = jdbc.query("SELECT id,name,device_type,vendor,model,management_ip,firmware_version,site,location,notes,active FROM network_devices WHERE active=TRUE AND (name LIKE ? OR management_ip LIKE ? OR vendor LIKE ? OR model LIKE ?) ORDER BY name LIMIT ? OFFSET ?",
                (rs, row) -> new DeviceRow(rs.getString("id"), rs.getString("name"), rs.getString("device_type"), rs.getString("vendor"), rs.getString("model"), rs.getString("management_ip"), rs.getString("firmware_version"), rs.getString("site"), rs.getString("location"), rs.getString("notes"), rs.getBoolean("active")), filter, filter, filter, filter, safeSize, (long) safePage * safeSize);
        Map<String, StatusResponse> statuses = new HashMap<>();
        if (!rows.isEmpty()) {
            String marks = String.join(",", Collections.nCopies(rows.size(), "?"));
            String[] deviceIds = rows.stream().map(DeviceRow::id).toArray(String[]::new);
            jdbc.query("SELECT device_id,status,software_version,uptime,cpu_load,free_memory_bytes,total_memory_bytes,interface_up_count,interface_down_count,checked_at,last_error FROM network_device_status WHERE device_id IN (" + marks + ")",
                    statement -> {
                        for (int index = 0; index < deviceIds.length; index++) {
                            statement.setString(index + 1, deviceIds[index]);
                        }
                    },
                    rs -> statuses.put(rs.getString("device_id"), mapStatus(rs)));
        }
        List<DeviceResponse> items = rows.stream().map(row -> row.toResponse(statuses.getOrDefault(row.id(), notConfigured()))).toList();
        return new PageResponse<>(items, safePage, safeSize, total);
    }

    @Transactional
    public DeviceResponse create(DeviceRequest request, String actorId, String actorName) {
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO network_devices(id,name,device_type,vendor,model,management_ip,firmware_version,site,location,notes) VALUES(?,?,?,?,?,?,?,?,?,?)",
                id, request.name().trim(), request.deviceType(), clean(request.vendor()), clean(request.model()), request.managementIp(), clean(request.firmwareVersion()), clean(request.site()), clean(request.location()), clean(request.notes()));
        audit(actorId, actorName, "CREATE", id, request.managementIp());
        return one(id);
    }

    @Transactional
    public DeviceResponse update(String id, DeviceRequest request, String actorId, String actorName) {
        int changed = jdbc.update("UPDATE network_devices SET name=?,device_type=?,vendor=?,model=?,management_ip=?,firmware_version=?,site=?,location=?,notes=? WHERE id=? AND active=TRUE",
                request.name().trim(), request.deviceType(), clean(request.vendor()), clean(request.model()), request.managementIp(), clean(request.firmwareVersion()), clean(request.site()), clean(request.location()), clean(request.notes()), id);
        if (changed == 0) throw new NoSuchElementException("Không tìm thấy thiết bị đang hoạt động.");
        audit(actorId, actorName, "UPDATE", id, request.managementIp());
        return one(id);
    }

    @Transactional
    public void archive(String id, String actorId, String actorName) {
        String ip = jdbc.query("SELECT management_ip FROM network_devices WHERE id=? AND active=TRUE", rs -> rs.next() ? rs.getString(1) : null, id);
        if (jdbc.update("UPDATE network_devices SET active=FALSE WHERE id=? AND active=TRUE", id) == 0) throw new NoSuchElementException("Không tìm thấy thiết bị đang hoạt động.");
        audit(actorId, actorName, "ARCHIVE", id, ip);
    }

    public StatusResponse routerStatus() {
        return jdbc.query("SELECT status,software_version,uptime,cpu_load,free_memory_bytes,total_memory_bytes,interface_up_count,interface_down_count,checked_at,last_error FROM network_device_status s JOIN network_devices d ON d.id=s.device_id WHERE d.device_type='ROUTER' AND d.active=TRUE AND d.management_ip=? ORDER BY s.checked_at DESC LIMIT 1",
                rs -> rs.next() ? new StatusResponse(rs.getString(1), rs.getString(2), rs.getString(3), (Integer) rs.getObject(4), rs.getObject(5, Long.class), rs.getObject(6, Long.class), (Integer) rs.getObject(7), (Integer) rs.getObject(8), rs.getTimestamp(9).toInstant().toString(), rs.getString(10)) : notConfigured(), configuredRouterIp());
    }

    @Scheduled(fixedDelayString = "${cfc.infrastructure.poll-interval-ms:60000}")
    public void pollRouter() {
        if (!properties.monitoringEnabled() || !routerConfigurationValid()) return;
        List<String> ids = jdbc.query("SELECT id FROM network_devices WHERE active=TRUE AND device_type='ROUTER' AND management_ip=? LIMIT 1", (rs, row) -> rs.getString(1), configuredRouterIp());
        if (ids.isEmpty()) return;
        String id = ids.get(0);
        try {
            URI uri = URI.create(properties.routerBaseUrl());
            RestClient client = restBuilder.baseUrl(uri.toString()).requestFactory(timeoutRequestFactory())
                    .defaultHeaders(headers -> headers.setBasicAuth(properties.routerUsername(), properties.routerPassword())).build();
            JsonNode resource = client.get().uri("/rest/system/resource").retrieve().body(JsonNode.class);
            JsonNode interfaces = client.get().uri("/rest/interface").retrieve().body(JsonNode.class);
            int up = 0, down = 0;
            if (interfaces != null && interfaces.isArray()) for (JsonNode item : interfaces) { if (item.path("running").asBoolean(false)) up++; else down++; }
            saveStatus(id, "ONLINE", text(resource, "version"), text(resource, "uptime"), integer(resource, "cpu-load"), number(resource, "free-memory"), number(resource, "total-memory"), up, down, null);
        } catch (Exception exception) {
            saveStatus(id, "OFFLINE", null, null, null, null, null, null, null, "Không kết nối được RouterOS qua HTTPS; kiểm tra mạng, chứng thư và tài khoản chỉ đọc.");
        }
    }

    public PageResponse<LogResponse> logs(String from, String to, String query, String deviceIp, int page, int size) {
        if (properties.lokiBaseUrl() == null || properties.lokiBaseUrl().isBlank()) return new PageResponse<>(List.of(), 0, 50, 0);
        int safeSize = Math.max(1, Math.min(size, 200));
        Instant now = Instant.now();
        Instant end = parseInstant(to, now);
        if (end.isAfter(now)) end = now;
        Instant start = parseInstant(from, end.minus(1, ChronoUnit.DAYS));
        if (start.isAfter(end) || start.isBefore(end.minus(7, ChronoUnit.DAYS))) throw new IllegalArgumentException("Khoảng tra cứu nhật ký tối đa 7 ngày.");
        String selector = "{job=\"network-syslog\"}" + (deviceIp == null || deviceIp.isBlank() ? "" : " |= \"" + escape(deviceIp.trim()) + "\"");
        if (query != null && !query.isBlank()) selector += " |= \"" + escape(query.trim().substring(0, Math.min(query.trim().length(), 120))) + "\"";
        String url = properties.lokiBaseUrl().replaceAll("/$", "") + "/loki/api/v1/query_range?query=" + encode(selector) + "&start=" + start.toEpochMilli() * 1_000_000L + "&end=" + end.toEpochMilli() * 1_000_000L + "&limit=" + safeSize + "&direction=backward";
        try {
            JsonNode root = restBuilder.baseUrl(properties.lokiBaseUrl()).requestFactory(timeoutRequestFactory()).build().get().uri(url).retrieve().body(JsonNode.class);
            List<LogResponse> entries = new ArrayList<>(); JsonNode values = root == null ? null : root.path("data").path("result");
            if (values != null && values.isArray()) for (JsonNode stream : values) { JsonNode labels = stream.path("stream"); JsonNode lines = stream.path("values"); for (JsonNode line : lines) if (entries.size() < safeSize) entries.add(new LogResponse(line.path(0).asText(), labels.path("device_ip").asText(""), labels.path("level").asText("info"), line.path(1).asText())); }
            return new PageResponse<>(entries, 0, safeSize, entries.size());
        } catch (Exception e) { throw new IllegalStateException("Không thể truy vấn kho nhật ký. Kiểm tra dịch vụ Loki."); }
    }

    private DeviceResponse one(String id) { return jdbc.queryForObject("SELECT id,name,device_type,vendor,model,management_ip,firmware_version,site,location,notes,active FROM network_devices WHERE id=?", (rs, row) -> new DeviceResponse(rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4), rs.getString(5), rs.getString(6), rs.getString(7), rs.getString(8), rs.getString(9), rs.getString(10), rs.getBoolean(11), status(id)), id); }
    private StatusResponse status(String id) { return jdbc.query("SELECT status,software_version,uptime,cpu_load,free_memory_bytes,total_memory_bytes,interface_up_count,interface_down_count,checked_at,last_error FROM network_device_status WHERE device_id=?", rs -> rs.next() ? mapStatus(rs) : notConfigured(), id); }
    private StatusResponse mapStatus(java.sql.ResultSet rs) throws java.sql.SQLException { return new StatusResponse(rs.getString(1), rs.getString(2), rs.getString(3), (Integer) rs.getObject(4), rs.getObject(5, Long.class), rs.getObject(6, Long.class), (Integer) rs.getObject(7), (Integer) rs.getObject(8), rs.getTimestamp(9).toInstant().toString(), rs.getString(10)); }
    private void saveStatus(String id, String state, String version, String uptime, Integer cpu, Long free, Long total, Integer up, Integer down, String error) { jdbc.update("INSERT INTO network_device_status(device_id,status,software_version,uptime,cpu_load,free_memory_bytes,total_memory_bytes,interface_up_count,interface_down_count,checked_at,last_error) VALUES(?,?,?,?,?,?,?,?,?,CURRENT_TIMESTAMP(6),?) ON DUPLICATE KEY UPDATE status=VALUES(status),software_version=VALUES(software_version),uptime=VALUES(uptime),cpu_load=VALUES(cpu_load),free_memory_bytes=VALUES(free_memory_bytes),total_memory_bytes=VALUES(total_memory_bytes),interface_up_count=VALUES(interface_up_count),interface_down_count=VALUES(interface_down_count),checked_at=CURRENT_TIMESTAMP(6),last_error=VALUES(last_error)", id, state, version, uptime, cpu, free, total, up, down, error); }
    private void audit(String actorId, String actorName, String action, String deviceId, String ip) { jdbc.update("INSERT INTO network_audit_events(id,actor_id,actor_name,action,device_id,management_ip) VALUES(?,?,?,?,?,?)", UUID.randomUUID().toString(), actorId, actorName, action, deviceId, ip); }
    private boolean routerConfigurationValid() { try { URI uri = URI.create(properties.routerBaseUrl()); return "https".equalsIgnoreCase(uri.getScheme()) && uri.getHost() != null && uri.getUserInfo() == null && properties.routerUsername() != null && !properties.routerUsername().isBlank() && properties.routerPassword() != null && !properties.routerPassword().isBlank(); } catch (Exception e) { return false; } }
    private org.springframework.http.client.ClientHttpRequestFactory timeoutRequestFactory() {
        var factory = new org.springframework.http.client.JdkClientHttpRequestFactory(
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build());
        factory.setReadTimeout(Duration.ofSeconds(5));
        return factory;
    }
    private String configuredRouterIp() { try { return URI.create(properties.routerBaseUrl()).getHost(); } catch (Exception e) { return ""; } }
    private StatusResponse notConfigured() { return new StatusResponse("UNKNOWN", null, null, null, null, null, null, null, null, "Chưa có số liệu giám sát."); }
    private String text(JsonNode node, String key) { return node == null || node.path(key).isMissingNode() ? null : node.path(key).asText(null); }
    private Integer integer(JsonNode node, String key) { return node != null && node.has(key) ? node.path(key).asInt() : null; }
    private Long number(JsonNode node, String key) { return node != null && node.has(key) ? node.path(key).asLong() : null; }
    private String clean(String value) { return value == null || value.isBlank() ? null : value.trim(); }
    private Instant parseInstant(String value, Instant fallback) {
        if (value == null || value.isBlank()) return fallback;
        try { return Instant.parse(value); }
        catch (java.time.format.DateTimeParseException exception) { throw new IllegalArgumentException("Thời điểm tra cứu phải theo chuẩn ISO-8601."); }
    }
    private String escape(String s) { return s.replace("\\", "\\\\").replace("\"", "\\\"").replace("\n", " ").replace("\r", " "); }
    private String encode(String value) { return java.net.URLEncoder.encode(value, java.nio.charset.StandardCharsets.UTF_8); }
}
