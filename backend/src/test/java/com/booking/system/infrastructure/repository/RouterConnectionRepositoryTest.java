package com.booking.system.infrastructure.repository;

import com.booking.system.infrastructure.repository.RouterConnectionRepository.Stored;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.support.TransactionTemplate;
import static org.assertj.core.api.Assertions.*;

class RouterConnectionRepositoryTest {
    JdbcTemplate jdbc;
    RouterConnectionRepository repository;
    TransactionTemplate transaction;
    org.springframework.jdbc.datasource.SingleConnectionDataSource ds;
    @BeforeEach void setup() {
        ds = new org.springframework.jdbc.datasource.SingleConnectionDataSource("jdbc:h2:mem:router-" + UUID.randomUUID() + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1", "sa", "", true);
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V31__create_infrastructure_monitoring.sql"),
                new ClassPathResource("db/migration/V33__add_router_web_connection.sql")).execute(ds);
        jdbc = new JdbcTemplate(ds); repository = new RouterConnectionRepository(jdbc);
        transaction = new TransactionTemplate(new DataSourceTransactionManager(ds));
    }
    @AfterEach void close() { ds.destroy(); }
    @Test void savesReactivatesRouterAndRejectsStaleRevision() {
        jdbc.update("UPDATE network_devices SET active=FALSE WHERE management_ip='192.168.1.1'");
        save(new Stored("https://192.168.1.1", "reader", "ciphertext", true, 0), "192.168.1.1");
        assertThat(repository.find().orElseThrow().rowVersion()).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM network_devices WHERE active=TRUE AND management_ip='192.168.1.1'", Integer.class)).isEqualTo(1);
        assertThatThrownBy(() -> save(new Stored("https://192.168.1.1", "reader", "stale", true, 0), "192.168.1.1"))
                .isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(repository.find().orElseThrow().encryptedPassword()).isEqualTo("ciphertext");
        save(new Stored("https://192.168.1.1", "reader", "new-ciphertext", false, 1), "192.168.1.1");
        assertThat(repository.find().orElseThrow().rowVersion()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM network_audit_events", Integer.class)).isEqualTo(2);
    }
    @Test void createsMissingCatalogEntryWithoutTouchingExistingRouter() {
        save(new Stored("https://192.168.1.2", "reader", "ciphertext", true, 0), "192.168.1.2");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM network_devices WHERE active=TRUE AND device_type='ROUTER'", Integer.class)).isEqualTo(2);
        assertThat(repository.find().orElseThrow().baseUrl()).isEqualTo("https://192.168.1.2");
    }
    @Test void conflictingDeviceTypeRollsBackConfigurationAndAudit() {
        jdbc.update("UPDATE network_devices SET device_type='SWITCH' WHERE management_ip='192.168.1.1'");
        assertThatThrownBy(() -> save(new Stored("https://192.168.1.1", "reader", "ciphertext", true, 0), "192.168.1.1"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(repository.find()).isEmpty();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM network_audit_events", Integer.class)).isZero();
    }
    void save(Stored value, String ip) { transaction.executeWithoutResult(status -> repository.save(value, ip, "admin", "Admin")); }
}
