-- Dev-only personal access. No credentials or identities are seeded.
CREATE TABLE hr_health_access_grants (
    employee_id VARCHAR(36) NOT NULL PRIMARY KEY,
    secret_hash VARCHAR(64) NOT NULL,
    generation VARCHAR(36) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    revoked BOOLEAN NOT NULL DEFAULT FALSE,
    updated_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_health_access_employee FOREIGN KEY (employee_id) REFERENCES hr_employees(id) ON DELETE CASCADE
);
CREATE TABLE hr_health_access_sessions (
    token_hash VARCHAR(64) NOT NULL PRIMARY KEY,
    employee_id VARCHAR(36) NOT NULL,
    generation VARCHAR(36) NOT NULL,
    expires_at DATETIME(6) NOT NULL,
    CONSTRAINT fk_health_session_employee FOREIGN KEY (employee_id) REFERENCES hr_health_access_grants(employee_id) ON DELETE CASCADE
);
CREATE INDEX idx_health_access_session_expiry ON hr_health_access_sessions(expires_at);
-- Personal history filters must use the frozen publication year, not mutable campaign metadata.
ALTER TABLE hr_health_publications ADD COLUMN report_year INT NULL;
