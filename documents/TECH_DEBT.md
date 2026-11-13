# Technical Debt (BookingBase / HR)

Source: codebase audit on 2026-10-16. Read this file only when a task touches an item below. Do not fix unrelated items while working on another task. Line numbers may have moved; search by file and symbol.

## Group 1: Do first (small, low risk)

| # | Issue | Where | Fix |
| --- | --- | --- | --- |
| 1 | Controllers missing from exception advice: errors return Spring default JSON instead of `ApiResponse.error()` | `HrApiExceptionHandler.java` `assignableTypes`; missing `HrNightRewardController`, `HrAttendanceController`, `HrLeaveSyncController`, `HrEmployeeDocumentController`, `HrDocumentTemplateController`, `HrWordEditorController` | Remove `assignableTypes` (apply to the whole `hr.api` package) or add all controllers |
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
| 15 | Some HR search/list screens still lack debounce or virtualization; production attendance search debounce/race fixed locally 09/11, existing shift sequence guards retained | `HrProductionAttendance.jsx`, `HrNightRewards.jsx`, HR search inputs | `useDebounce`; `@tanstack/react-virtual`; `AbortController` or TanStack Query |
| 15b | Administrative attendance reload/preview/summary reads have no stale-response guard when selections change | `HrAttendance.jsx` reload/openPreview/loadSummary | Source audit only, not reproduced with production delay; add context guards in a separate scoped fix. See ATTENDANCE_SEARCH_AUDIT.md. |
| 16 | Unpaginated list endpoints (17 found) | `HrProductionAttendanceController` `/exemptions` and `/shifts/{shiftId}/punches`, `HrNightRewardController` `/exceptions`, `HrEmployeeDocumentController`, `HrLeaveSyncController` | Add pagination or range filter and a max `size` |
| 17 | 6 POST/PUT endpoints without `@Valid` | `HrOcrController:38`, `HrWordEditorController:18, 31`, others | Add `@Valid` and DTO constraints |

| 17b | Two attendance tests depend on missing local `CongXn.xlsx`; full suite cannot pass on this checkout | `HrProductionAttendanceServiceTest`, `HrProductionShiftMatcherTest` | Supply a sanitized, versioned fixture or generate representative workbook data; do not commit real employee data |
| 17c | Styled Excel library is loaded on demand but its `xlsx.min` chunk is still about 863 kB, above the 500 kB target | `utils/nightRewardReconcile.js` parse/export | Evaluate a smaller reader and a styled export path that preserves the current workbook formatting |
| 17d | Master Context contains 8 relative links to documents absent from this checkout, found during the 2026-11-04 plan review | `documents/PROJECT_MASTER_CONTEXT.md`: older phase guides, Attendance plans/source docs, salary-raise plan and Telegram guide | Locate the canonical documents or correct/remove outdated links in a documentation maintenance task; new reporting-plan links were checked separately |
| 17e | Pending employee records with draft movements cannot be deleted, and the generic reference error provides no resolution workflow | `HrWorkforceService.deleteDraftEmployee`, employee detail/delete UI | Add precise reference diagnostics and an approved stop-reception/archive workflow that preserves audit and confirmed history; planned in `HR_REPORTING_UX_PLAN.md`, not implemented |
| 17f | Full backend suite has a reproducible unused Mockito stub in `previewLoadsQualificationAndEntitlementDetailsInBatches` | `HrNightRewardServiceTest.setUp`, entitlement repository stub at line 122 in the 04/11/2026 checkout | Scope the stub to tests that consume it; reproduced in isolation during HR UX Phase 0–3. Outside this change's business scope; full suite is not green even with the two attendance fixtures supplied. |
| 17g | Workforce report projection and correction filtering load the full confirmed movement timeline before filtering | `HrReportService`, `HrRosterProjectionService.projectedItemsForPeriods` | Add date-bounded movement queries plus a correction lookup that still removes replacements outside the requested year; measure on MySQL before sign-off. |

| 17i | Health staging/revision retention và MySQL chưa nghiệm thu | `health` import Phase 2 | Chốt TTL/purge/backup trước dữ liệu thật trên server; chạy V34/V35 và EXPLAIN MySQL dev. Phase 3 đã được phép nhập thật vào H2 dev riêng, có backup/restore nhưng /tmp không là lưu trữ dài hạn. Cancel xóa pending payload; không tự purge imported/history. Xem EMPLOYEE_HEALTH_PHASE3_DEV.md. |

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

- Health Phase 5 trước rollout: nghiệm thu Excel/LibreOffice desktop và MySQL/EXPLAIN, duyệt mẫu xuất; bổ sung snapshot đơn vị lịch sử khi có nguồn. Spool export private được dọn khi close nhưng kill/mất điện có thể để lại cfc-health-export-*; cần quy trình dọn khi app dừng, không áp dụng purge rộng có thể xóa export còn chạy. Xem EMPLOYEE_HEALTH_PHASE5_DEV.md.

- Health Phase 6 trước rollout: nghiệm thu V36, snapshot LONGTEXT/locking và truy vấn latest revision trên MySQL thật; chạy EXPLAIN với dữ liệu lớn trước quyết định thêm index. Cần backup/retention cho snapshot công bố, rà soát baseline thật trước phát hành. Phase 7 đã được duyệt mã tra cứu riêng và có cổng dev; chưa công bố thật hoặc mở production. Xem EMPLOYEE_HEALTH_PHASE6_DEV.md.

- Health Phase 7 trước rollout: nghiệm thu V37/backfill frozen year và locking/EXPLAIN trên MySQL; kiểm tra remoteAddr thực sau Cloudflare/reverse proxy vì limiter chỉ RAM một instance (20/IP và10/mã trong15phút), restart reset và cùng proxy có thể dùng chung hạn mức. Chốt backup/retention cho grant/session và giám sát an toàn không log request body/token; không tự bật cổng hoặc thêm Redis. Xem EMPLOYEE_HEALTH_PHASE7_DEV.md.
