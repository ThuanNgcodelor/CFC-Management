-- Phase 1: additive health foundation; no employee data, publication or access credentials are seeded.

CREATE TABLE hr_health_campaigns (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(255) NOT NULL,
    report_year INT NOT NULL,
    provider VARCHAR(255) NOT NULL,
    starts_on DATE NULL,
    ends_on DATE NULL,
    workforce_group VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_health_campaign_code UNIQUE (code),
    CONSTRAINT ck_health_campaign_year CHECK (report_year BETWEEN 1900 AND 2200),
    CONSTRAINT ck_health_campaign_group CHECK (workforce_group IN ('INDIRECT','DIRECT','BOTH')),
    CONSTRAINT ck_health_campaign_status CHECK (status IN ('OPEN','CLOSED')),
    CONSTRAINT ck_health_campaign_dates CHECK (ends_on IS NULL OR starts_on IS NULL OR ends_on >= starts_on)
);

CREATE TABLE hr_health_indicators (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    code VARCHAR(40) NOT NULL,
    name VARCHAR(255) NOT NULL,
    category VARCHAR(100) NOT NULL,
    value_type VARCHAR(20) NOT NULL,
    status VARCHAR(20) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_health_indicator_code UNIQUE (code),
    CONSTRAINT ck_health_indicator_type CHECK (value_type IN ('NUMBER','TEXT','CODED','COMPOSITE')),
    CONSTRAINT ck_health_indicator_status CHECK (status IN ('ACTIVE','INACTIVE'))
);

CREATE TABLE hr_health_indicator_aliases (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    indicator_id VARCHAR(36) NOT NULL,
    name VARCHAR(255) NOT NULL,
    normalized_name VARCHAR(255) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_health_alias_name UNIQUE (normalized_name),
    CONSTRAINT fk_health_alias_indicator FOREIGN KEY (indicator_id) REFERENCES hr_health_indicators(id) ON DELETE RESTRICT
);

CREATE TABLE hr_health_subjects (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    employee_id VARCHAR(36) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT uk_health_subject_employee UNIQUE (employee_id),
    CONSTRAINT fk_health_subject_employee FOREIGN KEY (employee_id) REFERENCES hr_employees(id) ON DELETE RESTRICT
);

CREATE TABLE hr_health_imports (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    campaign_id VARCHAR(36) NOT NULL,
    source_hash VARCHAR(64) NOT NULL,
    status VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_health_import_campaign FOREIGN KEY (campaign_id) REFERENCES hr_health_campaigns(id) ON DELETE RESTRICT,
    CONSTRAINT uk_health_import_hash UNIQUE (campaign_id, source_hash),
    CONSTRAINT ck_health_import_status CHECK (status IN ('REVIEW','PARTIAL','IMPORTED','CANCELLED'))
);

CREATE TABLE hr_health_visits (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    subject_id VARCHAR(36) NOT NULL,
    campaign_id VARCHAR(36) NOT NULL,
    source_namespace VARCHAR(100) NOT NULL,
    source_sid VARCHAR(100) NULL,
    examined_on DATE NULL,
    status VARCHAR(30) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_health_visit_subject FOREIGN KEY (subject_id) REFERENCES hr_health_subjects(id) ON DELETE RESTRICT,
    CONSTRAINT fk_health_visit_campaign FOREIGN KEY (campaign_id) REFERENCES hr_health_campaigns(id) ON DELETE RESTRICT,
    CONSTRAINT uk_health_visit_source UNIQUE (source_namespace, source_sid),
    CONSTRAINT ck_health_visit_status CHECK (status IN ('REVIEW','CONFIRMED'))
);

CREATE TABLE hr_health_results (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    visit_id VARCHAR(36) NOT NULL,
    indicator_id VARCHAR(36) NOT NULL,
    import_id VARCHAR(36) NULL,
    raw_value TEXT NOT NULL,
    unit VARCHAR(100) NULL,
    method VARCHAR(255) NULL,
    reference_text VARCHAR(500) NULL,
    source_sheet VARCHAR(255) NULL,
    source_row INT NULL,
    source_column VARCHAR(10) NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_health_result_visit FOREIGN KEY (visit_id) REFERENCES hr_health_visits(id) ON DELETE RESTRICT,
    CONSTRAINT fk_health_result_indicator FOREIGN KEY (indicator_id) REFERENCES hr_health_indicators(id) ON DELETE RESTRICT,
    CONSTRAINT fk_health_result_import FOREIGN KEY (import_id) REFERENCES hr_health_imports(id) ON DELETE RESTRICT,
    CONSTRAINT ck_health_result_row CHECK (source_row IS NULL OR source_row > 0)
);

CREATE TABLE hr_health_publications (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    visit_id VARCHAR(36) NOT NULL,
    revision INT NOT NULL,
    status VARCHAR(30) NOT NULL,
    snapshot TEXT NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0,
    CONSTRAINT fk_health_publication_visit FOREIGN KEY (visit_id) REFERENCES hr_health_visits(id) ON DELETE RESTRICT,
    CONSTRAINT uk_health_publication_revision UNIQUE (visit_id, revision),
    CONSTRAINT ck_health_publication_revision CHECK (revision > 0),
    CONSTRAINT ck_health_publication_status CHECK (status IN ('PUBLISHED','REVOKED'))
);

CREATE TABLE hr_health_audit_events (
    id VARCHAR(36) NOT NULL PRIMARY KEY,
    action VARCHAR(64) NOT NULL,
    entity_id VARCHAR(36) NOT NULL,
    actor_role VARCHAR(32) NOT NULL,
    created_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    updated_at DATETIME(6) NOT NULL DEFAULT CURRENT_TIMESTAMP(6),
    created_by_actor VARCHAR(320) NOT NULL,
    updated_by_actor VARCHAR(320) NOT NULL,
    row_version BIGINT NOT NULL DEFAULT 0
);

CREATE INDEX idx_health_campaign_year_code ON hr_health_campaigns (report_year, code, id);

CREATE INDEX idx_health_campaign_group_year ON hr_health_campaigns (workforce_group, report_year, code, id);

CREATE INDEX idx_health_alias_indicator ON hr_health_indicator_aliases (indicator_id);
