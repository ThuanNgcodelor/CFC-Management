package com.booking.system.infrastructure.service;

import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class NetworkObservationPersistence {
    private final JdbcTemplate jdbc;
    public NetworkObservationPersistence(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    @Transactional
    public int persist(String routerId, List<NetworkOperationsService.Observed> rows) {
        jdbc.batchUpdate("INSERT INTO network_ip_observations(id,router_device_id,source,ip_address,mac_address,host_name,interface_name,lease_status) VALUES(?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE host_name=VALUES(host_name),interface_name=VALUES(interface_name),lease_status=VALUES(lease_status),last_seen_at=CURRENT_TIMESTAMP(6)", rows, 300,
                (statement, row) -> { statement.setString(1, UUID.randomUUID().toString()); statement.setString(2, routerId); statement.setString(3, row.source()); statement.setString(4, row.ip()); statement.setString(5, row.mac()); statement.setString(6, row.host()); statement.setString(7, row.interfaceName()); statement.setString(8, row.status()); });
        return rows.size();
    }
}
