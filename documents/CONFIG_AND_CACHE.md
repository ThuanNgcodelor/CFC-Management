# Config And Cache Registry (BookingBase / HR)

Update this file in the same change whenever a cache, config key, or environment variable is added or changed. Never write secret values here, only names and purpose. State is from the 2026-10-16 audit; verify against code before relying on it.

## Caches

Current state: **no application cache exists** (`@EnableCaching`, `@Cacheable`, `@CacheEvict` are all absent). Redis is declared in `pom.xml` but unused.

When you add a cache, add one row:

| Cache name | Data | Provider | TTL | Max size | Evicted by (every write path) | Added in |
| --- | --- | --- | --- | --- | --- | --- |
| (none yet) | | | | | | |

Allowed candidates (see AGENTS.md section 6): department/position/working-condition catalogs (`HrManagementService.getCatalogs()`, evict on catalog create/update/delete), shift policies and work credit rules (`HrProductionAttendanceService.shiftPolicies()`, evict in `upsertShiftPolicy` and `upsertWorkCreditRule`), night reward program (`HrNightRewardProgramRepository.findEffective()`).

Never cache: DRAFT imports/shifts, unconfirmed qualifications, notifications, booking data. Service worker: do not cache dynamic booking API responses.

## Backend configuration (application.yml / application.properties)

| Key / area | Current value | Note |
| --- | --- | --- |
| JDBC `serverTimezone` | `UTC` (`application.yml:16`) | Business runs at +07:00. Do not change without a decision (see DECISIONS.md, Open) |
| `show-sql` | `true` (`application.yml:24`) | Should be `${SHOW_SQL:false}` (TECH_DEBT) |
| `spring.jpa.open-in-view` | not set (Spring default `true`) | |
| `hibernate.jdbc.batch_size`, `order_inserts`, `order_updates` | not set | Needed if batch inserts are introduced |
| Connection pool (HikariCP) | defaults (10 connections) | |
| `spring.servlet.multipart.max-file-size` | 50MB | Upload limit for Excel imports |
| Hibernate dialect | `MySQLDialect` | Tests use H2 |
| Migrations | Flyway V1 to V28 in `db/migration/` | Schema changes only via new migration |
| Secrets and credentials (JWT, DB, SMTP, VAPID) | set through config/env; names only in `application.properties.example` | Defaults currently committed (P0 in TECH_DEBT). Never write values here |

## Frontend configuration

| Area | Current state |
| --- | --- |
| Bundler | Vite 8; `vite-plugin-pwa` generates the service worker (99 precache entries, about 5.1 MB) |
| Largest chunk | `HrNightRewards` 914 kB (goal: under 500 kB) |
| HTTP client | Axios in `baseApi.js`: adds Bearer token, refreshes on 401 and retries; no central 403 handling |
| Lint | `oxlint`; baseline 0 errors, 10 warnings |

## Environment variables

List variable names (not values) that deploy relies on. Add a row when a new one is introduced.

| Variable | Purpose | Default | Where used |
| --- | --- | --- | --- |
| `SHOW_SQL` (proposed) | Toggle SQL logging | `false` | `application.yml` |
