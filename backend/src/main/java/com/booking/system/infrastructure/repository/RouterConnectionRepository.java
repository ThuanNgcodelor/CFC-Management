package com.booking.system.infrastructure.repository;

import java.util.Optional;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

@Repository
public class RouterConnectionRepository {
    public record Stored(String baseUrl, String username, String encryptedPassword, boolean enabled, long rowVersion) {
        @Override public String toString() { return "StoredRouterConnection[REDACTED]"; }
    }
    private final JdbcTemplate jdbc;
    public RouterConnectionRepository(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional(readOnly=true)
    public Optional<Stored> find() {
        return jdbc.query("SELECT base_url,username,encrypted_password,enabled,row_version FROM network_router_connection WHERE id=1",
                (rs, row) -> new Stored(rs.getString(1), rs.getString(2), rs.getString(3), rs.getBoolean(4), rs.getLong(5)))
                .stream().findFirst();
    }

    @Transactional
    public void save(Stored value, String ip, String actorId, String actorName) {
        int changed = jdbc.update("UPDATE network_router_connection SET base_url=?,username=?,encrypted_password=?,enabled=?,row_version=row_version+1 WHERE id=1 AND row_version=?",
                value.baseUrl(), value.username(), value.encryptedPassword(), value.enabled(), value.rowVersion());
        if (changed == 0) {
            if (value.rowVersion() != 0) throw new org.springframework.dao.OptimisticLockingFailureException("Router configuration changed");
            jdbc.update("INSERT INTO network_router_connection(id,base_url,username,encrypted_password,enabled,row_version) VALUES(1,?,?,?,?,1)",
                    value.baseUrl(), value.username(), value.encryptedPassword(), value.enabled());
        }
        String deviceId = ensureRouter(ip);
        jdbc.update("INSERT INTO network_audit_events(id,actor_id,actor_name,action,device_id,management_ip) VALUES(?,?,?,?,?,?)",
                UUID.randomUUID().toString(), actorId, actorName, "ROUTER_CONNECTION_UPDATE", deviceId, ip);
    }

    private String ensureRouter(String ip) {
        var devices = jdbc.query("SELECT id,device_type FROM network_devices WHERE management_ip=?", (rs,row) -> new String[]{rs.getString(1),rs.getString(2)}, ip);
        if (!devices.isEmpty()) {
            if (!"ROUTER".equals(devices.get(0)[1])) throw new IllegalArgumentException("IP này đã thuộc thiết bị khác loại Router trong danh mục.");
            String id = devices.get(0)[0];
            jdbc.update("UPDATE network_devices SET active=TRUE WHERE id=?", id);
            jdbc.update("DELETE FROM network_device_status WHERE device_id=?", id);
            return id;
        }
        String id = UUID.randomUUID().toString();
        jdbc.update("INSERT INTO network_devices(id,name,device_type,vendor,management_ip) VALUES(?,?,'ROUTER','MikroTik',?)", id, "MikroTik Router", ip);
        return id;
    }
}
