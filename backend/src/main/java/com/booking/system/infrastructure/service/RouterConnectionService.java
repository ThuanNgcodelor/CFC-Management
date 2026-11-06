package com.booking.system.infrastructure.service;

import com.booking.system.infrastructure.api.RouterConnectionDtos.*;
import com.booking.system.infrastructure.config.InfrastructureProperties;
import com.booking.system.infrastructure.repository.RouterConnectionRepository;
import com.booking.system.infrastructure.repository.RouterConnectionRepository.Stored;
import java.net.URI;
import org.springframework.stereotype.Service;

@Service
public class RouterConnectionService {
    public record Connection(String baseUrl, String username, String password, boolean enabled) {
        public String ip() { return URI.create(baseUrl).getHost(); }
        @Override public String toString() { return "RouterConnection[REDACTED]"; }
    }
    private final RouterConnectionRepository repository;
    private final RouterCredentialCipher cipher;
    private final InfrastructureProperties properties;

    public RouterConnectionService(RouterConnectionRepository repository, RouterCredentialCipher cipher, InfrastructureProperties properties) {
        this.repository = repository; this.cipher = cipher; this.properties = properties;
    }

    public Response metadata() {
        return describe(repository.find());
    }

    private Response describe(java.util.Optional<Stored> saved) {
        if (saved.isPresent()) {
            Stored s = saved.get();
            return new Response(s.baseUrl(), s.username(), s.enabled(), true, s.rowVersion(), "WEB");
        }
        return new Response(properties.routerBaseUrl() == null ? "https://192.168.1.1" : properties.routerBaseUrl(),
                properties.routerUsername() == null ? "" : properties.routerUsername(), properties.monitoringEnabled(),
                hasPassword(properties.routerPassword()), 0, hasPassword(properties.routerPassword()) ? "ENV" : "NONE");
    }

    public Connection effective() {
        var saved = repository.find();
        if (saved.isPresent()) {
            Stored s = saved.get();
            return new Connection(s.baseUrl(), s.username(), s.enabled() ? cipher.decrypt(s.encryptedPassword()) : "", s.enabled());
        }
        return new Connection(properties.routerBaseUrl(), properties.routerUsername(), properties.routerPassword(), properties.monitoringEnabled());
    }

    public Response save(Request request, String actorId, String actorName) {
        Connection connection = candidate(request);
        repository.save(new Stored(connection.baseUrl(), connection.username(), cipher.encrypt(connection.password()),
                connection.enabled(), request.rowVersion()), connection.ip(), actorId, actorName);
        return metadata();
    }

    public Connection candidate(Request request) {
        String url = normalizeUrl(request.baseUrl());
        String username = request.username().trim();
        if (username.isBlank() || username.contains(":")) throw new IllegalArgumentException("Tên đăng nhập MikroTik không hợp lệ.");
        var snapshot = repository.find();
        Response current = describe(snapshot);
        if (current.rowVersion() != request.rowVersion()) throw new org.springframework.dao.OptimisticLockingFailureException("Router configuration changed");
        String password = request.password();
        if (!hasPassword(password)) {
            if (!url.equals(current.baseUrl()) || !username.equals(current.username()) || !current.passwordConfigured()) {
                throw new IllegalArgumentException("Hãy nhập mật khẩu MikroTik khi thiết lập mới hoặc đổi địa chỉ/tài khoản.");
            }
            password = snapshot.isPresent() ? cipher.decrypt(snapshot.get().encryptedPassword()) : properties.routerPassword();
        }
        return new Connection(url, username, password, request.enabled());
    }

    public static String normalizeUrl(String value) {
        try {
            URI uri = URI.create(value.trim());
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme()) || host == null || uri.getUserInfo() != null
                    || uri.getRawQuery() != null || uri.getRawFragment() != null
                    || !(uri.getPath().isEmpty() || "/".equals(uri.getPath()))
                    || uri.getPort() == 0 || uri.getPort() > 65535 || !privateIpv4(host)) throw new IllegalArgumentException();
            return "https://" + host + (uri.getPort() == -1 ? "" : ":" + uri.getPort());
        } catch (Exception exception) {
            throw new IllegalArgumentException("Địa chỉ phải là HTTPS với IP nội bộ, ví dụ https://192.168.1.1; không kèm đường dẫn hay tài khoản.");
        }
    }

    private static boolean privateIpv4(String host) {
        if (!host.matches("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}")) return false;
        String[] parts = host.split("\\.");
        int[] numbers = new int[4];
        for (int i=0; i<4; i++) {
            numbers[i] = Integer.parseInt(parts[i]);
            if (numbers[i] > 255 || (parts[i].length() > 1 && parts[i].startsWith("0"))) return false;
        }
        return numbers[0] == 10 || (numbers[0] == 172 && numbers[1] >= 16 && numbers[1] <= 31)
                || (numbers[0] == 192 && numbers[1] == 168);
    }
    private static boolean hasPassword(String value) { return value != null && !value.isEmpty(); }
}
