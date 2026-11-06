package com.booking.system.infrastructure.service;

import com.booking.system.infrastructure.api.InfrastructureDtos.StatusResponse;
import com.booking.system.infrastructure.service.RouterConnectionService.Connection;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class RouterOsClient {
    private final ObjectMapper mapper = new ObjectMapper();

    public JsonNode read(Connection connection, String path) {
        if (!connection.enabled() || connection.password() == null || connection.password().isEmpty()) {
            throw new IllegalArgumentException("Chưa bật hoặc chưa cấu hình kết nối MikroTik.");
        }
        if (!java.util.Set.of("/rest/system/resource", "/rest/interface", "/rest/ip/dhcp-server/lease", "/rest/ip/arp").contains(path)) {
            throw new IllegalArgumentException("Tài nguyên RouterOS không được hỗ trợ.");
        }
        // Keep JVM certificate validation and never forward Basic credentials through redirects.
        var factory = new JdkClientHttpRequestFactory(HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(3)).followRedirects(HttpClient.Redirect.NEVER).build());
        factory.setReadTimeout(Duration.ofSeconds(8));
        try {
            String body = RestClient.builder().baseUrl(connection.baseUrl()).requestFactory(factory)
                    .defaultHeaders(headers -> headers.setBasicAuth(connection.username(), connection.password()))
                    .build().get().uri(path).retrieve().body(String.class);
            JsonNode result = mapper.readTree(body);
            if (result == null || (!result.isArray() && !result.isObject()) || result.has("error")) {
                throw new IllegalArgumentException("Phản hồi RouterOS không hợp lệ.");
            }
            return result;
        } catch (Exception exception) {
            throw new IllegalArgumentException(failureMessage(exception));
        }
    }

    public StatusResponse health(Connection connection) {
        return parseHealth(read(connection, "/rest/system/resource"), read(connection, "/rest/interface"));
    }

    static StatusResponse parseHealth(JsonNode resource, JsonNode interfaces) {
        JsonNode item = resource.isArray() ? resource.path(0) : resource;
        if (!item.isObject() || !item.hasNonNull("uptime") || !item.hasNonNull("version") || !interfaces.isArray()) {
            throw new IllegalArgumentException("RouterOS trả về dữ liệu tài nguyên/interface không đúng định dạng.");
        }
        int up = 0;
        for (JsonNode networkInterface : interfaces) if (networkInterface.path("running").asBoolean(false)) up++;
        return new StatusResponse("ONLINE", item.path("version").asText(), item.path("uptime").asText(),
                item.has("cpu-load") ? item.path("cpu-load").asInt() : null,
                item.has("free-memory") ? item.path("free-memory").asLong() : null,
                item.has("total-memory") ? item.path("total-memory").asLong() : null,
                up, interfaces.size() - up, Instant.now().toString(), null);
    }

    static String failureMessage(Exception exception) {
        for (Throwable cause = exception; cause != null; cause = cause.getCause()) {
            if (cause instanceof RestClientResponseException response) {
                if (response.getStatusCode().value() == 401) return "MikroTik từ chối tài khoản hoặc mật khẩu (401).";
                if (response.getStatusCode().value() == 403) return "Tài khoản MikroTik chưa có quyền đọc REST API (403).";
                if (response.getStatusCode().value() == 404) return "Không tìm thấy REST API; kiểm tra RouterOS 7 và dịch vụ www-ssl.";
            }
            if (cause instanceof javax.net.ssl.SSLException) return "Chứng thư HTTPS MikroTik chưa được JVM tin cậy hoặc không khớp IP. Cần cấu hình chứng thư/truststore trên Fedora.";
            if (cause instanceof java.net.http.HttpTimeoutException || cause instanceof java.net.SocketTimeoutException) return "Kết nối MikroTik quá thời gian; kiểm tra đường mạng và firewall từ server.";
        }
        return "Không đọc được RouterOS; kiểm tra IP, cổng HTTPS, www-ssl và đường mạng từ server đến MikroTik.";
    }
}
