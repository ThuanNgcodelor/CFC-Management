# Attendance search regression

These development-only fixtures import the actual production page and shared hooks. They are not part of the application entry point or production build. No real employee data or credentials are used.

1. From the repository root run `npm --prefix frontend run dev -- --host 127.0.0.1` (port 5173).
2. Run Playwright MCP `browser_run_code_unsafe` with `filename` pointing to the absolute path of `frontend/tests/attendance-search.playwright.js`. The file is an async function expression, as required by that MCP tool, rather than a Node CLI program.
3. Expect `passed: 13`. The runner opens `/tests/attendance-search.html`, intercepts every `/api/v1/**` call, requires GET, and uses synthetic responses. Responses are held and released in a chosen order. Interceptors are removed and the browser is returned to about:blank on completion/failure.

Coverage: stable URL writer and old callbacks; patches in the same tick; delayed debounce acknowledgements; Back/Forward with a pending search; unmounted callbacks; request counts for C628 and both search inputs; old imports after visibility refresh; stale shift filters and file selection; employee pagination; import deep links; old month imports and summary.

No browser-test dependency was added. These checks currently run via MCP, not automatically in GitHub Actions. Existing pure utility/API tests can be run with `node --test frontend/src/utils/*.test.js frontend/src/api/*.test.js`. CI still runs the normal frontend lint/build and backend tests.

## Health Phase8 HTTP acceptance

`health-phase8-http.playwright.js` uses the real `/health-lookup` route on loopback5196 and the test-source fake HTTP backend on18086. Unlike the earlier UI harnesses it does not intercept API responses. Restart the isolated fixture before every run: the runner deliberately revokes a publication and exercises the limiter. Fourteen groups passed locally. See [Phase8 setup and safety boundaries](../../documents/EMPLOYEE_HEALTH_PHASE8_DEV.md). Never point this runner at production.

## Health readiness2026

Start `python3 scripts/health/run_dev_preview.py --maven <mvn-path> --java-home <jdk21-path>` from repo root. The script runs6 synthetic import workflow tests, generates5 fake workbooks and starts real health APIs on18086 plus Vite5196. Run `health-phase9.playwright.js` with MCP for8 end-to-end groups and desktop/mobile screenshots; `health-mapping-review.playwright.js` uses the existing5195 Phase2 UI fixture for6 mapping checks. The shell user is synthetic; health requests in Phase9 are not mocked. See [Phase9 handoff](../../documents/EMPLOYEE_HEALTH_PHASE9_DEV.md). Legacy Phase8 HTTP runner requires its original fresh fixture, not the2026 fixture. No browser tests automatically run in GitHub Actions.

## Health packaged history HTTP acceptance

`health-history.playwright.js` runs9 groups against Vite5198 and the test-source `HealthHistoryPreview` backend on127.0.0.1:18088. Health APIs are real; the shell user and bundled data are synthetic. Start a fresh preview for every run (it deliberately installs its fixture key). The32zero-byte fixture key is test-only and must never replace the real private key. Covers locked/wrong-key, unlock/import, restart-view/retry dedupe, review navigation, mobile and HR_STAFF403. Screenshots contain synthetic data only. See [history handoff](../../documents/EMPLOYEE_HEALTH_AUTO_IMPORT.md).

## Leadership payroll

`payroll-leadership.playwright.js` runs7fixture groups on loopbackVite5199 at `/tests/payroll-leadership.html`. Mocked payroll APIs never send Telegram or use production. Covers separate upload type/history label, amount details, INVALID selection/test-send blocking and390px layout. Backend tests generate synthetic workbooks; optional `LEADERSHIP_PAYROLL_SOURCE` reads a private local workbook without persisting or logging values. [Handoff](../../documents/HR_LEADERSHIP_PAYROLL_IMPORT.md).
