/* eslint-disable no-unused-expressions -- Playwright MCP evaluates this file as an async function expression. */
async (page) => {
  const base = 'http://127.0.0.1:5173/tests/attendance-search.html';
  const checks = [];
  const calls = [];
  const held = [];
  let hold = () => false;
  const ensure = (condition, message) => { if (!condition) throw new Error(message); };
  const routeState = () => page.getByTestId('route').textContent();
  const waitParam = (key, value) => page.waitForFunction(({ key, value }) => {
    const route = document.querySelector('[data-testid="route"]')?.textContent || '';
    return new URL(route, 'https://test.invalid').searchParams.get(key) === value;
  }, { key, value });
  const release = async () => { const waiting = held.splice(0); waiting.forEach((resume) => resume()); await page.waitForTimeout(180); };
  const paged = (content, size = 20) => ({ content, page: 0, size, totalPages: content.length ? 1 : 0, totalElements: content.length });
  const person = { employeeCode: 'C628', employeeName: 'Nhân viên mẫu', policyGroup: 'PRODUCTION_WORKER', days: 31,
    totalDays: 31, reviewShifts: 1, autoMatchedShifts: 1, noPunchDays: 0, confirmedShifts: 0, proposedWorkValue: 1.5,
    nightShifts: 0, overtimeShifts: 0, nightAndOvertimeShifts: 0 };
  const shift = (old = false) => ({ id: old ? 'old-shift' : 'current-shift', employeeCode: old ? 'OLD' : 'C628',
    employeeName: old ? 'Phản hồi cũ' : 'Nhân viên mẫu', workDate: '2026-10-01', shiftCode: 'CN_DAY',
    status: old ? 'AUTO_MATCHED' : 'NEEDS_REVIEW', workValue: 1.5, nightAllowanceAmount: 0 });

  await page.route('**/api/v1/**', async (requestRoute) => {
    const request = requestRoute.request();
    const url = new URL(request.url());
    ensure(request.method() === 'GET', 'Regression tests must never write to a backend');
    const path = url.pathname;
    const params = Object.fromEntries(url.searchParams);
    calls.push({ path, params });
    const item = { path, params };
    const shouldHold = hold(item);
    if (shouldHold) await new Promise((resolve) => held.push(resolve));
    let data;
    if (path.endsWith('/imports')) {
      const month = params.month || '2026-10';
      const first = { id: month === '2026-11' ? 'import-nov' : 'import-oct', sourceFileName: `Mẫu ${month}.xlsx`,
        attendanceMonth: month, status: 'PREVIEWED', reviewShifts: 1, autoMatchedShifts: 1, totalRows: 31,
        totalPunches: 60, processingVersion: 1, noPunchRows: 0 };
      data = paged(month === '2026-10' ? [first, { ...first, id: 'import-oct-2', sourceFileName: 'File thứ hai.xlsx' }] : [first], 100);
    } else if (path.endsWith('/employee-summaries')) {
      data = { ...paged([person]), page: Number(params.page || 0), totalPages: 3, totalElements: 60 };
    } else if (path.endsWith('/shifts')) {
      data = { ...paged([shift(params.status === 'AUTO_MATCHED' || shouldHold)], 50), page: Number(params.page || 0), totalPages: 3, totalElements: 120 };
    } else if (path.endsWith('/summary')) {
      data = { totalEmployees: params.month === '2026-11' ? 222 : 111, totalWorkValue: 1.5 };
    } else if (path.endsWith('/incidents')) {
      data = paged([]);
    } else if (path.endsWith('/shift-policies') || path.endsWith('/work-credit-rules')) {
      data = [];
    } else {
      throw new Error(`Unexpected API request in isolated test: ${path}`);
    }
    await requestRoute.fulfill({ status: 200, contentType: 'application/json', body: JSON.stringify({ status: 200, data }) });
  });
  try {
    await page.goto(`${base}?scenario=probe`);
    await page.getByRole('textbox', { name: 'Probe search' }).waitFor();
    await page.evaluate(() => {
      const write = window.__listProbe.write;
      write({ employeeKeyword: 'C628' });
      write({ view: 'ALL' }, { resetPage: false });
    });
    await waitParam('view', 'ALL');
    ensure((await routeState()).includes('employeeKeyword=C628'), 'Same-tick URL patches lost a search value');
    ensure(await page.evaluate(() => window.__listProbe.stable), 'URL writer identity changed on navigation');
    await page.evaluate(() => window.__listProbe.firstWriter({ page: 2 }, { resetPage: false }));
    await waitParam('page', '2');
    ensure((await routeState()).includes('employeeKeyword=C628'), 'Old writer erased current filters');
    checks.push('Stable URL writer, same-tick patches, old callback preserves current filters');

    await page.evaluate(() => window.__listProbe.write({ employeeKeyword: '' }, { replace: true }));
    await page.waitForTimeout(100);
    await page.getByRole('textbox', { name: 'Probe search' }).pressSequentially('C628', { delay: 420 });
    await page.waitForTimeout(700);
    ensure(await page.getByRole('textbox', { name: 'Probe search' }).inputValue() === 'C628', 'Delayed URL acknowledgement overwrote a newer draft');
    await waitParam('employeeKeyword', 'C628');
    checks.push('Delayed acknowledgements keep the newest C628 input');

    await page.evaluate(() => window.__attendanceNavigate('/probe?employeeKeyword=back'));
    await waitParam('employeeKeyword', 'back');
    await page.evaluate(() => window.__attendanceNavigate('/probe?employeeKeyword=forward'));
    await waitParam('employeeKeyword', 'forward');
    await page.getByRole('textbox', { name: 'Probe search' }).fill('uncommitted');
    await page.evaluate(() => window.__attendanceNavigate(-1));
    await waitParam('employeeKeyword', 'back');
    await page.waitForTimeout(700);
    ensure(await page.getByRole('textbox', { name: 'Probe search' }).inputValue() === 'back', 'Back did not cancel a pending search');
    await page.evaluate(() => window.__attendanceNavigate(1));
    await waitParam('employeeKeyword', 'forward');
    ensure(await page.getByRole('textbox', { name: 'Probe search' }).inputValue() === 'forward', 'Forward did not restore the search');
    await page.evaluate(() => { window.__oldListWriter = window.__listProbe.write; window.__attendanceNavigate('/manager/hr/attendance/production?month=2026-10&importId=import-oct&view=ALL'); });
    await page.getByPlaceholder('Tìm mã hoặc tên nhân viên', { exact: true }).waitFor();
    await page.evaluate(() => window.__oldListWriter({ employeeKeyword: 'obsolete' }));
    ensure(!(await routeState()).includes('obsolete'), 'Unmounted screen wrote into the current route');
    checks.push('Back/Forward restore state and cancel drafts; unmounted callbacks cannot navigate');

    await page.waitForTimeout(200);
    calls.length = 0;
    const search = page.getByPlaceholder('Tìm mã hoặc tên nhân viên', { exact: true });
    await search.pressSequentially('C628', { delay: 420 });
    await waitParam('employeeKeyword', 'C628');
    await page.waitForTimeout(200);
    ensure(calls.length === 4 && calls.every((call) => call.path.endsWith('/employee-summaries')), `Search fetched unrelated data: ${JSON.stringify(calls)}`);
    checks.push('Typing C628 with four debounce commits sends four summary requests, no base/shift requests');

    await search.fill('');
    await page.waitForTimeout(500);
    calls.length = 0;
    await search.fill('C628');
    await waitParam('employeeKeyword', 'C628');
    await page.waitForTimeout(150);
    ensure(calls.length === 1 && calls[0].path.endsWith('/employee-summaries'), 'Single search must issue exactly one relevant request');
    checks.push('Single fill: one request instead of the seven measured before the fix');

    await search.fill('');
    await page.waitForTimeout(500);
    let firstImport = true;
    hold = (call) => call.path.endsWith('/imports') && firstImport && (firstImport = false, true);
    await page.evaluate(() => document.dispatchEvent(new Event('visibilitychange')));
    await page.waitForTimeout(100);
    ensure(held.length === 1, 'Visibility refresh import was not held');
    await search.pressSequentially('C628', { delay: 420 });
    await waitParam('employeeKeyword', 'C628');
    await release();
    ensure(await search.inputValue() === 'C628', 'Old imports response rewound the C628 input');
    await waitParam('employeeKeyword', 'C628');
    checks.push('Delayed imports/visibility response cannot rewind search');

    hold = (call) => call.path.endsWith('/shifts') && call.params.status === 'AUTO_MATCHED';
    calls.length = 0;
    await page.getByRole('button', { name: /^Sẵn sàng \(/ }).click();
    await page.waitForTimeout(80);
    await page.getByRole('button', { name: 'Tất cả', exact: true }).click();
    await waitParam('view', 'ALL');
    await release();
    await waitParam('view', 'ALL');
    ensure(!(await page.locator('body').innerText()).includes('Phản hồi cũ'), 'Stale shifts overwrote the newer filter result');
    ensure(calls.length === 2 && calls.every((call) => call.path.endsWith('/shifts')), 'Changing shift filters refetched unrelated data');
    checks.push('ALL stays selected after older AUTO_MATCHED response; only shift requests fire');

    hold = () => false;
    calls.length = 0;
    await page.getByPlaceholder('Tìm mã nhân viên', { exact: true }).fill('C628');
    await waitParam('employeeCode', 'C628');
    await page.waitForTimeout(150);
    ensure(calls.length === 1 && calls[0].path.endsWith('/shifts'), 'Detail code search fetched unrelated data');
    checks.push('Detail employee search sends only its shift request');

    await search.fill('');
    await page.getByPlaceholder('Tìm mã nhân viên', { exact: true }).fill('');
    await page.waitForTimeout(500);
    calls.length = 0;
    await search.fill('C628');
    await page.getByPlaceholder('Tìm mã nhân viên', { exact: true }).fill('B129');
    await waitParam('employeeKeyword', 'C628');
    await waitParam('employeeCode', 'B129');
    await page.waitForTimeout(150);
    ensure(calls.length === 2, 'Two search fields lost or duplicated commits');
    checks.push('Two pending search fields preserve both URL patches');

    calls.length = 0;
    const overview = page.getByRole('heading', { name: 'Tổng quan theo nhân viên', exact: true }).locator('xpath=ancestor::section[1]');
    await overview.getByRole('button', { name: 'Sau', exact: true }).click();
    await waitParam('employeePage', '1');
    await page.waitForTimeout(100);
    ensure(calls.length === 1 && calls[0].path.endsWith('/employee-summaries') && calls[0].params.keyword === 'C628', 'Summary paging lost search or fetched unrelated data');
    ensure((await routeState()).includes('employeeCode=B129'), 'Summary paging erased detail filters');
    checks.push('Employee pagination preserves both search filters and fetches only its page');

    hold = (call) => call.path.endsWith('/shifts') && call.params.importId === 'import-oct';
    await page.getByRole('button', { name: /^Sẵn sàng \(/ }).click();
    await page.waitForTimeout(80);
    await page.getByRole('button', { name: /^File thứ hai.xlsx/ }).click();
    await waitParam('importId', 'import-oct-2');
    await release();
    await waitParam('importId', 'import-oct-2');
    ensure(!(await page.locator('body').innerText()).includes('Phản hồi cũ'), 'Old file shifts overwrote the selected file');
    checks.push('Selecting another file ignores old-file requests');

    hold = () => false;
    await page.evaluate(() => window.__attendanceNavigate('/manager/hr/attendance/production?month=2026-10&importId=import-oct&view=ALL&employeeCode=C628&employeePage=1'));
    await waitParam('importId', 'import-oct');
    await page.waitForTimeout(180);
    await waitParam('view', 'ALL');
    await waitParam('employeeCode', 'C628');
    await waitParam('employeePage', '1');
    checks.push('Import deep link retains view, person and page instead of reinitializing filters');

    hold = (call) => (call.path.endsWith('/imports') || call.path.endsWith('/summary')) && call.params.month === '2026-10';
    await page.getByRole('button', { name: 'Tải lại', exact: true }).click();
    await page.waitForTimeout(100);
    ensure(held.length === 2, 'Old month responses were not held');
    await page.locator('input[type="month"]').fill('2026-11');
    await waitParam('importId', 'import-nov');
    await page.waitForTimeout(150);
    await release();
    ensure(await page.locator('input[type="month"]').inputValue() === '2026-11', 'Old month response changed the selected month');
    await waitParam('importId', 'import-nov');
    ensure((await page.getByText('Tổng nhân viên', { exact: true }).locator('..').innerText()).includes('222'), 'Old month summary overwrote the newer month');
    ensure(!(await page.locator('body').innerText()).includes('Mẫu 2026-10.xlsx'), 'Old month imports remained visible');
    checks.push('Old import and base responses cannot overwrite a newer month');
    return { passed: checks.length, checks };
  } finally {
    held.splice(0).forEach((resume) => resume());
    await page.unrouteAll({ behavior: 'wait' });
    await page.goto('about:blank');
  }
}
