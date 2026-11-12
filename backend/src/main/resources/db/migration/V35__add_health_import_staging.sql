-- Phase 2: private import staging, profiles and result revision history.
ALTER TABLE hr_health_imports ADD COLUMN source_sheet VARCHAR(255) NOT NULL DEFAULT '';
ALTER TABLE hr_health_imports ADD COLUMN file_hash VARCHAR(64) NOT NULL DEFAULT '';
ALTER TABLE hr_health_imports ADD COLUMN source_namespace VARCHAR(100) NOT NULL DEFAULT '';
ALTER TABLE hr_health_imports ADD COLUMN mapping_json TEXT NULL;
ALTER TABLE hr_health_imports ADD COLUMN total_rows INT NOT NULL DEFAULT 0;
ALTER TABLE hr_health_results ADD COLUMN value_key VARCHAR(64) NOT NULL DEFAULT '';
ALTER TABLE hr_health_results ADD COLUMN source_label VARCHAR(500) NULL;
CREATE UNIQUE INDEX uk_health_result_value ON hr_health_results (visit_id, indicator_id, value_key);

CREATE TABLE hr_health_import_rows (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    batch_id VARCHAR(36) NOT NULL,
    source_row INT NOT NULL,
    payload TEXT NULL,
    state VARCHAR(20) NOT NULL,
    employee_id VARCHAR(36) NULL,
    resolution_reason VARCHAR(500) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_health_row_batch FOREIGN KEY (batch_id) REFERENCES hr_health_imports(id) ON DELETE RESTRICT,
    CONSTRAINT fk_health_row_employee FOREIGN KEY (employee_id) REFERENCES hr_employees(id) ON DELETE RESTRICT,
    CONSTRAINT uk_health_row_source UNIQUE (batch_id, source_row),
    CONSTRAINT ck_health_row_state CHECK (state IN ('PENDING','IMPORTED','CANCELLED'))
);
CREATE INDEX idx_health_row_batch_state ON hr_health_import_rows (batch_id, state);
CREATE TABLE hr_health_mapping_profiles (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    name VARCHAR(100) NOT NULL,
    fingerprint VARCHAR(64) NOT NULL,
    mapping_json TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_health_profile UNIQUE (fingerprint, name)
);
CREATE TABLE hr_health_result_changes (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    result_id VARCHAR(36) NOT NULL,
    batch_id VARCHAR(36) NOT NULL,
    before_payload TEXT NOT NULL,
    after_payload TEXT NOT NULL,
    reason VARCHAR(500) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_health_change_result FOREIGN KEY (result_id) REFERENCES hr_health_results(id) ON DELETE RESTRICT,
    CONSTRAINT fk_health_change_batch FOREIGN KEY (batch_id) REFERENCES hr_health_imports(id) ON DELETE RESTRICT
);
