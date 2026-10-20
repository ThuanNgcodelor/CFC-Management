# BookingBase Agent Instructions

Read this file before changing code in this repository. Rules are written in English; business descriptions stay in Vietnamese.

## 1. Active Product Scope

- Since 2026-07-27, only the HR management domain is under active product development.
- Booking room/car and Booking approval are legacy/frozen: do not start Booking Phase 6–10 or expand Booking unless the user explicitly reopens that scope.
- Do not delete legacy Booking code, tables, routes, or shared Auth/Notification/PWA infrastructure as part of HR work.
- Canonical roadmap and system master context: `documents/PROJECT_MASTER_CONTEXT.md`. Read it first.
- Known technical debt lives in `documents/TECH_DEBT.md`. Read it only when the task touches a listed item; do not fix unrelated items.
- Past decisions live in `documents/DECISIONS.md`; caches and configuration live in `documents/CONFIG_AND_CACHE.md`. Read them before touching caching, configuration, dependencies, or anything a past decision covers.

## 2. Source Of Truth

1. Current code and config.
2. Current schema: entities + Flyway migrations (`db/migration/`, V1 to V28 at the time of writing). `clean_database_master.sql` does not exist.
3. Master architecture and business flow: `documents/PROJECT_MASTER_CONTEXT.md`.

## 3. Stack And Commands

| Layer | Stack |
| --- | --- |
| Backend | Java 21, Spring Boot 4.0.0, Spring Security + JJWT 0.12.5, WebSocket (STOMP), MySQL + Flyway, Apache POI 5.4.1, PDFBox 3.0.4, Lombok, Bean Validation. Tests use H2. |
| Frontend | React 19, Vite 8, react-router-dom 7, Tailwind 4, Axios, lucide-react, react-hot-toast, xlsx / xlsx-js-style, vite-plugin-pwa, oxlint. No Redux/Zustand; state is React Context + hooks. |

Do not use APIs from other major versions of these libraries.

Commands (run from the matching directory):

| Task | Linux / macOS | Windows (PowerShell) |
| --- | --- | --- |
| Frontend build | `npm run build` | `npm.cmd run build` |
| Frontend lint | `npm run lint` | `npm.cmd run lint` |
| Backend test | `./mvnw test` | `.\mvnw.cmd test` |

- Backend commands need `JAVA_HOME` set. If Java is missing, say so; never claim tests passed when they did not run.
- If Maven needs network to download dependencies, ask for approval rather than skipping tests.

## 4. Hard Rules

- Do not refactor outside the selected task.
- Do not change business flow just to match older design docs.
- Do not change login/profile behavior unless the task explicitly says so.
- Do not expose or log secrets. Do not commit real JWT secrets, SMTP passwords, VAPID private keys, DB passwords, OTPs, or Authorization headers.
- Do not move mail or push into the booking transaction.
- Do not cache dynamic booking APIs in the service worker without an invalidation design.
- Do not add Redis/Kafka/queue just because it sounds like a best practice. (`spring-boot-starter-data-redis` is declared but unused; do not build on it without an explicit decision.)
- Do not remove backward compatibility unless the task includes migration and tests.
- If unsure about payroll, reward, or attendance business rules, ask. Do not guess.

## 5. Code Quality Rules

### Language and naming
- Identifiers (class, method, variable, DB column): English. `camelCase` in code, `snake_case` in DB.
- User-facing text (UI, toast, exception message): Vietnamese with diacritics.
- Comments explain why, not what. Business-rule comments may be Vietnamese. Remove dead code; never leave commented-out code.
- Replace magic numbers/strings with enums, constants, or config. Business thresholds and amounts belong in DB/config, not hard-coded.

### Layering and size
- Controller: validate and map DTOs only. Business logic goes in Service. Data access goes in Repository.
- API contract: DTOs as Java `record`s grouped in `Hr<Domain>Dtos`, wrapped in `ApiResponse<T>`. URLs under `/api/v1/hr/...`.
- Prefer small units: methods around 40 lines or less. These limits apply to NEW code: a Service should stay under ~800 lines and a component under ~400 lines / 15 `useState`.
- Some files are already over the limit (`HrProductionAttendanceService` ~1,230 lines, `HrProductionAttendance.jsx` 41 `useState`, `HrMovements.jsx` 840 lines, `HrPayroll.jsx`). When adding a feature there, put it in a new sub-component, hook, or service class; do not add more `useState` or methods to the old file. Do not rewrite the whole old file as part of a small task.
- Reuse before writing new: search for an existing helper, hook, or component first.

### Error handling and validation
- Never swallow exceptions: no `catch (Exception ignored) {}`. At minimum `log.warn` with context (row, id), never secrets.
- Every `@RequestBody` on POST/PUT must use `@Valid`; put constraints on the DTO.
- Every new controller under `com.booking.system.hr.api` must be covered by `HrApiExceptionHandler` (check its `assignableTypes`), so errors return `ApiResponse.error()` JSON.

## 6. Backend Performance Rules

- Keep transactions short. Parse Excel (POI), hash files, and do other I/O OUTSIDE `@Transactional`; open a transaction only when writing via repositories. Use `@Transactional(readOnly = true)` for read-only methods.
- Upload pattern: parse and hash first (no transaction), then call a separate `@Transactional` method that persists the parsed result. Put that method in another bean or use `TransactionTemplate`; a same-class call bypasses the Spring proxy and the transaction will not apply.
- No `.save()` inside loops over rows or employees. Collect and call `saveAll(...)`, or use batch queries (`findByXIn`). No repository calls inside per-employee loops (N+1).
- Fetch strategy: keep `LAZY`; use `@EntityGraph` / `JOIN FETCH` / DTO projection when a list needs related data. Check SQL log for N+1.
- Every list endpoint must paginate or filter by range, and cap `size` (suggested max 200). Do not return unbounded lists.
- Index changes go through a Flyway migration and are verified with `EXPLAIN` (paste the result in the PR).
- Config: `show-sql` must come from an env variable and default to `false`. Do not change `serverTimezone` or timezone handling without an explicit task (night shifts crossing 0h depend on it).

### Caching policy
- Default: no cache. Add caching only after a slow or hot read is measured.
- If needed: Spring Cache + Caffeine (in-process) first. Each cache needs a name, TTL, max size, and a `@CacheEvict` on every write path (create, update, delete, finalize, reopen).
- Allowed candidates (read-heavy, rarely changed): department/position/working-condition catalogs, shift policies, work credit rules, night reward program.
- Never cache: DRAFT imports/shifts, unconfirmed qualifications, notifications, booking data.
- Use `rowVersion` as ETag when adding HTTP caching to read APIs. Redis only if the app runs multiple instances and the task says so.
- Register every cache in `documents/CONFIG_AND_CACHE.md` (name, data, TTL, max size, evict points) in the same change.

## 7. Frontend Performance And Structure Rules

- Server state: new screens use TanStack Query (cache, dedupe, cancellation). Existing screens migrate only when the task touches them; do not mass-migrate.
- Existing sequence guards (`shiftsSeq`, `summariesSeq` with `useRef`) in `HrProductionAttendance` / `HrNightRewards` must be kept until that screen is migrated to Query. When adding a fetch to one of these screens, follow the same sequence-guard pattern; do not mix in `AbortController` there.
- In new screens and new files, cancel stale requests with TanStack Query or `AbortController`, not `useRef` sequences.
- Heavy libraries: never statically import `xlsx` or `xlsx-js-style` at module top. Use `await import(...)` when the user triggers the feature. A new chunk must not exceed 500 kB; `HrNightRewards` is currently 914 kB because of a static import (see TECH_DEBT).
- Memoization: use `useMemo` / `useCallback` / `React.memo` only with a reason (large lists, context values, props passed to memoized children). Do not wrap trivial values. Do not recompute large filters/maps inline in JSX.
- Large lists: more than ~200 rows must use `@tanstack/react-virtual` or server pagination. Search/filter inputs must be debounced.
- Shared UI: use `HrLoading`, `HrError`, `HrEmpty`, `HrPagination`, `HrDrawer` from `HrUi.jsx`. Do not hand-write inline loading/error states.
- Keep local loading states (`shiftsLoading`, `summariesLoading`, `working`, `refreshing`); do not unmount the whole page on mutate or reload.
- Measure before optimizing: note bundle size or Profiler result before/after in the PR.

## 8. Definition Of Done

- `npm run build` passes; `npm run lint` has 0 errors and no new warnings (baseline: 10).
- Backend tests pass (`./mvnw test`). If they could not run, state the reason explicitly.
- Business logic changes include tests. Required when touching `HrNightRewardService` or `HrProductionShiftMatcher`: threshold of 10 night shifts, FAILED month does not wipe accrued months, reopen moves qualifications to `STALE`, shifts crossing 0h.
- No secrets, no new `catch` that swallows errors, no new unbounded list endpoint, no `.save()` in a loop.
- Documentation is part of the change (see "Documentation Maintenance" below). A change without its matching doc update is not done.
- Workflow: read relevant files, state a short plan, make the smallest change, run checks, report what changed and what was not verified.

## 8b. Documentation Maintenance

The AI does not remember previous sessions. Context lives in these files, so keep them current in the same change:

| If you... | Update |
| --- | --- |
| Add or change a cache, config key, env variable, or timeout/pool/batch setting | `documents/CONFIG_AND_CACHE.md` (one row per cache or setting, including where it is evicted) |
| Change a business flow or architecture | `documents/PROJECT_MASTER_CONTEXT.md` |
| Make a technical decision (choose/remove a library or dependency, change a rule, pick an approach) | Append an entry to `documents/DECISIONS.md` (date, decision, reason) |
| Finish a technical-debt item | Delete it from `documents/TECH_DEBT.md` |
| Find a new problem you are not fixing now | Add it to `documents/TECH_DEBT.md` |
| Change a command, stack version, or rule in this file | Update this file |

- Before proposing a change to a decision already in `DECISIONS.md` (for example adding Redis), say so and ask the user. Do not silently reverse it.
- Do not copy into docs what can be read from code (file lists, repository names). Document flows, invariants, and reasons.
- Never put secrets in any document.

## 9. Active HR Domain Map

HR has more than the two modules detailed below. Other running modules (read their service before changing them):

| Module | Main service | Note |
| --- | --- | --- |
| Chấm công hành chính | `HrAttendanceService` | ~810 lines |
| Nhân sự và biến động | `HrWorkforceService` | ~950 lines |
| Thử việc | `HrProbationService` | ~935 lines |
| Lương | `HrPayrollService`, `HrPayrollCampaignService` | PDF payslip via PDFBox |
| Nâng lương | `HrSalaryRaiseService` | ~690 lines |
| OCR hồ sơ | `HrOcrService` | |
| Telegram bot | `HrTelegramService` | |
| Import baseline | `HrBaselineWorkbookParser`, `HrBaselineImportPersistence` | |
| Xuất Excel | `HrExcelExportService` | |

Backend root: `backend/src/main/java/com/booking/system/hr/` (`api/`, `api/dto/`, `service/`, `importer/`, `entity/`, `repository/`). Frontend: `frontend/src/` (`api/`, `components/{admin,hr,ui}/`, `contexts/`, `hooks/`, `layouts/`, `pages/{admin,hr}/`, `styles/`, `utils/`). Entities extend `HrBaseEntity` (has `rowVersion`).

## 10. Production Attendance And Night Rewards (detailed domain)

Key files: `HrProductionAttendanceController/Service`, `HrProductionShiftMatcher`, `HrProductionAttendanceReportService`, `HrProductionAttendanceWorkbookParser`, `HrNightRewardController/Service`; frontend `HrProductionAttendance.jsx`, `HrNightRewards.jsx`, `HrNightRewardReconcileModal.jsx`, `nightRewardReconcile.js`, `hrProductionAttendanceApi.js`, `hrNightRewardApi.js`.

### Production Attendance (Chấm công ca sản xuất) invariants
- **Anti-duplicate**: the parser blocks duplicate `(employeeCode, workDate)` inside one workbook. `upload` checks SHA256 to block identical re-uploads and blocks cross-file collision (two files in the same month with the same employee-day).
- **Matching** (`HrProductionShiftMatcher`): pairs In/Out punches; detects night shifts crossing midnight (18:00 to 06:00 next day); applies rounding and work-credit rules (0, 0.5, 1.0, 1.5, 2.0); statuses `AUTO_MATCHED`, `NEEDS_REVIEW`, `NO_PUNCH`.
- **Manual review**: HR can override shift, time, credit rule, and record incidents. Every action is logged in `HrAttendanceShiftAdjustment` / `HrAttendanceIncident`.
- Query endpoints (`shifts`, `employeeReviewSummaries`, `punches`) return `HrPageResponse.empty()` when `importId` is missing/deleted, instead of 404, to avoid frontend cascade errors.
- Preserve optimistic locking (`rowVersion`) on shifts, credit rules, exemptions, and imports.
- Maintain audit events for import, calculate, shift decision, bulk confirm, delete, and reopen.

### Night Rewards (Thưởng gắn bó ca đêm) invariants
- Count only shifts in `CONFIRMED` imports with `countsTowardNightReward = true`. Threshold: at least 10 night shifts per month (stored in `HrNightRewardProgram.qualifyingNightThreshold`).
- Company exceptions (`HrNightRewardException`): HR creates, manager approves (`APPROVED`), approved exceptions add credited shifts.
- Each qualified month adds 1 to the cycle. A `FAILED` month must NOT wipe previously accrued months. At 12 months: draft periodic entitlement. At 24 months: draft loyalty entitlement.
- `finalize` is blocked if any import of the month is unconfirmed or duplicate employee-days exist across files.
- `reopen` moves qualifications to `STALE` (needs recompute) without losing history or cycle data.
- Excel reconciliation (`HrNightRewardReconcileModal`, `nightRewardReconcile.js`) is client-side only: green = match, red = shift count differs, yellow = employee code not found. It must never write to the database.
- Known debt: cycle months (12/24) and reward amounts are hard-coded in `HrNightRewardService` (see TECH_DEBT). Do not change amounts or cycles without explicit approval.

## 11. Security Checklist

Before changing booking, approval, cancel, dashboard, or admin code:
- Requester, approver, and canceller must come from the authenticated principal, never from body fields (`requesterId`, `approverId`, `cancellerId`).
- Dashboard user data must come from the authenticated principal or be admin-authorized.
- Admin APIs return 403 for non-admin users. Protected APIs return 401 without a token.
- CORS allows configured domains only. WebSocket/STOMP CONNECT must validate JWT.

## 12. Legacy Areas (read only when the task mentions them)

### Booking (frozen)
- Preserve `startTime < endTime` validation and overlap logic: existing start < new end and existing end > new start.
- Preserve locking on room/vehicle before the overlap check unless replaced by a proven equivalent.
- Blocking statuses stay `PENDING` and `APPROVED`. Test concurrent overlap if transaction/locking changes.

### Notification
- Database notification is the source of truth; WebSocket is realtime delivery only; email is an independent fallback; Web Push goes only to active subscriptions.
- Keep event handling after commit. Mail/push failure must not roll back booking.
- Avoid duplicates by source type/source id. Do not retry permanent push failures (403, 404, 410). Log source type/id and recipient id, never secrets.

### Calendar and routing (frontend)
- Keep range-based fetch for month/week/day; test boundary events; add a stale-request guard if changing fetch; avoid large all-booking loads; test mobile portrait/landscape.
- Do not break deep links from email or push. Keep protected-route redirect behavior.

### PWA
- Verify manifest and maskable icons. Do not cache dynamic booking API responses without invalidation.
- Test notification click (app open and closed), Android install + push, iOS Add to Home Screen + push, and safe-area CSS.

## 13. Docker / Deploy Checklist

- Keep DB data persistent. Add or preserve healthchecks and restart policy.
- Use env variables for JWT, mail, VAPID, CORS, frontend URL, DB, timezone, `SHOW_SQL`.
- Ensure SPA refresh on nested React routes does not 404.
- Verify the Cloudflare tunnel points to the correct frontend/backend ports and HTTPS secure context for PWA push.

## 14. Current Known Baseline

- Frontend build passes; lint passes with 0 errors and 10 warnings (`oxlint`). Main bundles: `HrNightRewards` 914 kB (over the 500 kB goal), precache about 5.1 MB.
- Backend tests: not verified in the last audit (no `JAVA_HOME`). Do not assume they pass.
- Frontend has 3 small test files and no test runner configured.
- Main P0 risks: client-trusted identity and committed secret defaults.
- Rules currently violated by existing code (fix only when the task touches them): long transactions around Excel parsing in `upload()`; `.save()` in loops; `HrApiExceptionHandler` missing some controllers; `show-sql: true`; no debounce/virtualization on large HR tables.
