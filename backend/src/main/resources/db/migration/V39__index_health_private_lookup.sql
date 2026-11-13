-- A private code resolves exactly one grant without requiring public identity fields.
CREATE UNIQUE INDEX uk_health_access_secret_hash ON hr_health_access_grants(secret_hash);
