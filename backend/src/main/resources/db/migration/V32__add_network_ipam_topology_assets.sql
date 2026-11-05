CREATE TABLE network_ip_observations (
    id VARCHAR(36) NOT NULL,
    router_device_id VARCHAR(36) NOT NULL,
    source VARCHAR(16) NOT NULL,
    ip_address VARCHAR(45) NOT NULL,
    mac_address VARCHAR(17) NULL,
    host_name VARCHAR(180) NULL,
    interface_name VARCHAR(120) NULL,
    lease_status VARCHAR(32) NULL,
    first_seen_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    last_seen_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_network_ip_observation (router_device_id, source, ip_address, mac_address),
    KEY idx_network_ip_observation_ip_seen (ip_address, last_seen_at),
    CONSTRAINT fk_network_ip_observation_router FOREIGN KEY (router_device_id) REFERENCES network_devices(id)
);

CREATE TABLE network_ip_reviews (
    ip_address VARCHAR(45) NOT NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'OPEN',
    note VARCHAR(1000) NULL,
    reviewed_by VARCHAR(180) NULL,
    reviewed_at DATETIME(6) NULL,
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (ip_address),
    CONSTRAINT ck_network_ip_review_status CHECK (status IN ('OPEN', 'CONFIRMED', 'NOT_CONFLICT', 'RESOLVED'))
);

CREATE TABLE network_topology_links (
    id VARCHAR(36) NOT NULL,
    source_device_id VARCHAR(36) NOT NULL,
    source_port VARCHAR(120) NULL,
    target_device_id VARCHAR(36) NOT NULL,
    target_port VARCHAR(120) NULL,
    link_type VARCHAR(24) NOT NULL DEFAULT 'ETHERNET',
    vlan_id SMALLINT NULL,
    verification_status VARCHAR(24) NOT NULL DEFAULT 'UNVERIFIED',
    notes VARCHAR(1000) NULL,
    created_by VARCHAR(180) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    KEY idx_network_topology_source (source_device_id),
    KEY idx_network_topology_target (target_device_id),
    CONSTRAINT fk_network_topology_source FOREIGN KEY (source_device_id) REFERENCES network_devices(id),
    CONSTRAINT fk_network_topology_target FOREIGN KEY (target_device_id) REFERENCES network_devices(id),
    CONSTRAINT ck_network_topology_status CHECK (verification_status IN ('UNVERIFIED', 'MANUAL_VERIFIED', 'AUTO_DISCOVERED'))
);

CREATE TABLE network_assets (
    id VARCHAR(36) NOT NULL,
    asset_tag VARCHAR(80) NOT NULL,
    asset_type VARCHAR(32) NOT NULL,
    name VARCHAR(180) NOT NULL,
    mac_address VARCHAR(17) NULL,
    ip_address VARCHAR(45) NULL,
    department VARCHAR(180) NULL,
    location VARCHAR(200) NULL,
    responsible_person VARCHAR(180) NULL,
    status VARCHAR(24) NOT NULL DEFAULT 'ACTIVE',
    notes VARCHAR(1000) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6) ON UPDATE CURRENT_TIMESTAMP(6),
    PRIMARY KEY (id),
    UNIQUE KEY uk_network_assets_tag (asset_tag),
    KEY idx_network_assets_mac (mac_address),
    KEY idx_network_assets_ip (ip_address),
    CONSTRAINT ck_network_assets_type CHECK (asset_type IN ('WORKSTATION', 'PRINTER', 'PHONE', 'SERVER', 'CAMERA', 'OTHER')),
    CONSTRAINT ck_network_assets_status CHECK (status IN ('ACTIVE', 'STORED', 'RETIRED', 'SHARED', 'BYOD'))
);

CREATE TABLE network_asset_assignments (
    id VARCHAR(36) NOT NULL,
    asset_id VARCHAR(36) NOT NULL,
    department VARCHAR(180) NULL,
    location VARCHAR(200) NULL,
    responsible_person VARCHAR(180) NULL,
    valid_from DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    valid_to DATETIME(6) NULL,
    changed_by VARCHAR(180) NOT NULL,
    PRIMARY KEY (id),
    KEY idx_network_asset_assignment_asset (asset_id, valid_from),
    CONSTRAINT fk_network_asset_assignment_asset FOREIGN KEY (asset_id) REFERENCES network_assets(id)
);
