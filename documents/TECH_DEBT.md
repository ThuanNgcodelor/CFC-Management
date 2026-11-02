# Technical Debt (BookingBase / HR)

Source: codebase audit on 2026-10-16. Read this file only when a task touches an item below. Do not fix unrelated items while working on another task. Line numbers may have moved; search by file and symbol.

## Group 1: Do first (small, low risk)

| # | Issue | Where | Fix |
| --- | --- | --- | --- |
| 1 | Controllers missing from exception advice: errors return Spring default JSON instead of `ApiResponse.error()` | `HrApiExceptionHandler.java` `assignableTypes`; missing `HrNightRewardController`, `HrAttendanceController`, `HrLeaveSyncController`, `HrEmployeeDocumentController`, `HrDocumentTemplateController`, `HrWordEditorController` | Remove `assignableTypes` (apply to the whole `hr.api` package) or add all controllers |
| 2 | Static import of heavy lib bloats `HrNightRewards` chunk to 914 kB | `utils/nightRewardReconcile.js:18` | `await import('xlsx-js-style')` when the reconcile modal opens |
| 3 | `show-sql: true` | `application.yml` | `${SHOW_SQL:false}` |
| 4 | Missing index for night-shift scan | `hr_attendance_shifts` | New migration: index `(import_id, counts_toward_night_reward)` |
| 5 | Missing index for ledger lookup | `hr_night_reward_ledger` | New migration: index `(qualification_id, track_code, entry_type)` |
| 6 | Missing index for cross-file duplicate check | `hr_attendance_source_days` | New migration: index `(employee_code, work_date)` |
| 7 | `.save()` in loops | `HrAttendanceService.java:222`, `HrProductionAttendanceService.java:727`, `HrPayrollService.java:73` | Collect into a list and `saveAll(...)` |
| 8 | Swallowed exceptions when parsing date/time | `HrAttendanceService.java:683, 693` (11 empty catches in total) | `log.warn` with row context |

## Group 2: Do next

| # | Issue | Where | Fix |
| --- | --- | --- | --- |
| 9 | N+1 queries when finalizing night rewards (SELECT + INSERT per employee per track) | `HrNightRewardService.java:420-436` | `findByQualificationIdIn(...)` + `saveAll(...)` |
| 10 | Long transaction around Excel parsing and SHA256 | `HrProductionAttendanceService.upload` (:219), `HrAttendanceService` (:171), `HrPayrollService` (:39) | Parse outside the transaction; open `@Transactional` only for persistence |
| 11 | Per-file `existsByImportIdAndEmployeeCodeIn` loop | `HrProductionAttendanceService.java:302` | One query across active imports of the month |
| 12 | `existsActiveAfter` called per row | `HrSalaryRaiseService.java:337` | Batch query |
| 13 | Entitlement generation at 12/24 months still lacks direct tests | `HrNightRewardServiceTest.java` | Threshold 9/10, accrued months after NOT_QUALIFIED (FAILED), ADMIN/HR_STAFF finalize and reopen to STALE covered on 2026-11-02; add entitlement boundary tests |
| 14 | God components: `HrProductionAttendance.jsx` (41 `useState` + 8 `useEffect`, 641 lines), `HrMovements.jsx` (840 lines, 19 `useState`), `HrPayroll.jsx` (32 hooks) | those files | Split drawers/forms into child components; `useReducer` for the adjustment form |
| 14b | Large shift list filtered inline in JSX on every render | `HrProductionAttendance.jsx:490-530` | Move the filter into `useMemo` (or into a child component) |
| 14c | No central handling of HTTP 403 (only 401 refresh) | `baseApi.js` | Add a response interceptor for 403 with a Vietnamese toast |
| 15 | No debounce, no virtualization on large tables; two shift screens use `useRef` sequence guards instead of request cancellation | `HrProductionAttendance.jsx`, `HrNightRewards.jsx`, HR search inputs | `useDebounce`; `@tanstack/react-virtual`; `AbortController` or TanStack Query |
| 16 | Unpaginated list endpoints (17 found) | `HrProductionAttendanceController` `/exemptions` and `/shifts/{shiftId}/punches`, `HrNightRewardController` `/exceptions`, `HrEmployeeDocumentController`, `HrLeaveSyncController` | Add pagination or range filter and a max `size` |
| 17 | 6 POST/PUT endpoints without `@Valid` | `HrOcrController:38`, `HrWordEditorController:18, 31`, others | Add `@Valid` and DTO constraints |

| 17b | Two attendance tests depend on missing local `CongXn.xlsx`; full suite cannot pass on this checkout | `HrProductionAttendanceServiceTest`, `HrProductionShiftMatcherTest` | Supply a sanitized, versioned fixture or generate representative workbook data; do not commit real employee data |

## Group 3: Needs an owner decision (do not change on your own)

| # | Issue | Why it needs a decision |
| --- | --- | --- |
| 18 | Timezone: JDBC uses `serverTimezone=UTC` while the business runs in Vietnam (+07:00) | Wrong change can shift already-finalized shift dates; verify against real data first |
| 19 | Hard-coded cycle months (12/24) and reward amounts (500k, 1M, 6M, 24M) at `HrNightRewardService.java:32-37` | Moving them into `HrNightRewardProgram` changes how money rules are configured |
| 20 | `spring-boot-starter-data-redis` is declared but unused | Remove it and use Caffeine, or keep it if multiple instances are planned |
| 21 | Committed secret defaults and client-trusted identity (P0 in AGENTS.md) | Needs a dedicated security task |

## Not verified in the audit

- Backend `./mvnw test` could not run (no `JAVA_HOME`).
- No `EXPLAIN ANALYZE` on production-size data for the cross-file duplicate query.
