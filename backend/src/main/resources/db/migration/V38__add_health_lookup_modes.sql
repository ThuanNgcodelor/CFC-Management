-- Existing grants keep requiring the private access code until a manager explicitly changes policy.
ALTER TABLE hr_health_access_grants ADD COLUMN allow_identity_only BOOLEAN NOT NULL DEFAULT FALSE;
