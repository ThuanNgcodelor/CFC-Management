CREATE TABLE network_devices (
    id VARCHAR(36) NOT NULL,
    name VARCHAR(120) NOT NULL,
    device_type VARCHAR(32) NOT NULL,
    vendor VARCHAR(80) NULL,
    model VARCHAR(120) NULL,
    management_ip VARCHAR(45) NULL,
    firmware_version VARCHAR(80) NULL,
    site VARCHAR(120) NULL,
    location VARCHAR(200) NULL,
    notes VARCHAR(1000) NULL,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_network_devices_management_ip (management_ip),
    KEY idx_network_devices_active_name (active, name),
    CONSTRAINT ck_network_devices_type CHECK (device_type IN ('ROUTER', 'SWITCH', 'ACCESS_POINT', 'SERVER', 'WAN', 'OTHER'))
);

CREATE TABLE network_device_status (
    device_id VARCHAR(36) NOT NULL,
    status VARCHAR(24) NOT NULL,
    software_version VARCHAR(80) NULL,
    uptime VARCHAR(64) NULL,
    cpu_load SMALLINT NULL,
    free_memory_bytes BIGINT NULL,
    total_memory_bytes BIGINT NULL,
    interface_up_count SMALLINT NULL,
    interface_down_count SMALLINT NULL,
    checked_at DATETIME(6) NOT NULL,
    last_error VARCHAR(240) NULL,
    PRIMARY KEY (device_id),
    CONSTRAINT fk_network_device_status_device FOREIGN KEY (device_id)
        REFERENCES network_devices (id) ON DELETE CASCADE,
    CONSTRAINT ck_network_device_status_value CHECK (status IN ('ONLINE', 'OFFLINE', 'UNKNOWN'))
);

CREATE TABLE network_audit_events (
    id VARCHAR(36) NOT NULL,
    actor_id VARCHAR(36) NOT NULL,
    actor_name VARCHAR(180) NOT NULL,
    action VARCHAR(32) NOT NULL,
    device_id VARCHAR(36) NULL,
    management_ip VARCHAR(45) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_network_audit_created (created_at),
    KEY idx_network_audit_device (device_id, created_at)
);

INSERT INTO network_devices (id, name, device_type, vendor, model, management_ip, firmware_version, site, location, notes)
SELECT '4e5df3b9-83c9-4c7f-8d11-192168001001', 'MikroTik Router chính', 'ROUTER', 'MikroTik', 'L009UiGS', '192.168.1.1', '7.16.2', 'Mạng nội bộ', NULL, 'Thông tin kiểm kê do quản trị viên cung cấp; trạng thái giám sát chưa được xác minh.'
WHERE NOT EXISTS (SELECT 1 FROM network_devices WHERE management_ip = '192.168.1.1');
