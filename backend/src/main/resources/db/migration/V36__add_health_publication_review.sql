-- Phase 6: immutable release content and an explicit review of the current editing version.
ALTER TABLE hr_health_visits ADD COLUMN reviewed_hash VARCHAR(64) NULL;
ALTER TABLE hr_health_visits ADD COLUMN review_reason VARCHAR(500) NULL;
ALTER TABLE hr_health_visits ADD COLUMN acknowledged_incomplete BOOLEAN NOT NULL DEFAULT FALSE;
ALTER TABLE hr_health_publications MODIFY COLUMN snapshot LONGTEXT NOT NULL;
ALTER TABLE hr_health_publications ADD COLUMN content_hash VARCHAR(64) NULL;
ALTER TABLE hr_health_publications ADD COLUMN publish_reason VARCHAR(500) NULL;
ALTER TABLE hr_health_publications ADD COLUMN revoke_reason VARCHAR(500) NULL;
ALTER TABLE hr_health_audit_events ADD COLUMN reason VARCHAR(500) NULL;
