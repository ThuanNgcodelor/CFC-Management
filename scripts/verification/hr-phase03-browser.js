async (originalPage) => {
  const context = await originalPage.context().browser().newContext({ viewport: { width: 1440, height: 1000 }, serviceWorkers: 'block' });
  context.setDefaultTimeout(8000);
  const page = await context.newPage();
  const results = [];
  const errors = [];
  const requests = [];
  const navigation = [];
  let probationCount = 7;
  const base = 'http://127.0.0.1:5175';
  const user = { id: 'demo-manager', fullName: 'Người kiểm thử giả lập', email: 'demo@example.test', role: 'MANAGER', status: 'ACTIVE' };
  const unit = { id: 'demo-unit', code: 'DEMO', name: 'Đơn vị giả lập', status: 'ACTIVE' };
  const position = { id: 'demo-position', code: 'CN', name: 'Công nhân', status: 'ACTIVE' };
  let rows = Array.from({ length: 65 }, (_, i) => ({
    id: `demo-${i}`, employeeCode: `DEMO${String(i).padStart(3, '0')}`, fullName: `Nhân sự giả lập ${i}`,
    employmentStatus: i % 3 === 0 ? 'DRAFT' : 'ACTIVE', departmentName: unit.name, positionName: position.name,
    departmentId: unit.id, positionId: position.id, hireDate: '2026-09-01', rowVersion: 0,
  }));
  const detail = (row) => ({ id: row.id, employmentStatus: row.employmentStatus, rowVersion: row.rowVersion, workforceGroup: 'GENERAL_LABOR',
    personal: { employeeCode: row.employeeCode, fullName: row.fullName, gender: 'MALE', dateOfBirth: '1999-01-01' },
    employment: { department: unit, position, hireDate: row.hireDate, baseSalary: 1000000, allowance: 0 }, contact: {}, insurance: {}, identity: {},
  });
  const candidates = Array.from({ length: 45 }, (_, i) => ({
    id: `candidate-${i}`, candidateCode: `TV-DEMO${i}`, fullName: `Ứng viên giả lập ${i}`, gender: 'MALE',
    status: i < 7 ? 'IN_PROBATION' : 'CONVERTED', convertedEmployeeId: i < 7 ? null : 'demo-1',
    department: unit, position, probationStartDate: '2026-10-01', probationEndDate: '2026-11-30',
    rowVersion: 0, updatedAt: '2026-11-01T00:00:00', baseSalary: 1000000, latestContract: null,
  }));
  const templates = [{ id: 'template-1', code: 'DEMO', name: 'Mẫu giả lập', jobTitle: 'Nhân viên', status: 'ACTIVE', sortOrder: 1, baseSalary: 1000000 }];
  const rosters = Array.from({ length: 25 }, (_, i) => ({ id: `roster-${i}`, periodStart: `2026-${String(12 - i % 12).padStart(2, '0')}-01`, status: 'CLOSED', itemCount: 65 }));
  const moves = rows.map((row, i) => ({ id: `movement-${i}`, employeeId: row.id, employeeCode: row.employeeCode,
    employeeName: row.fullName, movementType: i % 2 === 0 ? 'INCREASE' : 'DECREASE', status: 'CONFIRMED', effectiveDate: '2026-09-01',
    createdAt: '2026-09-01T00:00:00', confirmedAt: '2026-09-01T01:00:00', confirmedByActor: 'Người kiểm thử',
    fromEmployeeStatus: 'DRAFT', toEmployeeStatus: 'ACTIVE', toDepartment: unit.name, rowVersion: 0,
  }));
  function paged(items, params) {
    const number = Number(params.get('page') || 0), size = Number(params.get('size') || 20);
    return { content: items.slice(number * size, (number + 1) * size), number, size, totalElements: items.length, totalPages: Math.ceil(items.length / size) };
  }
  const ok = (condition, label) => { if (!condition) throw new Error(label); results.push(label); };
  page.on('pageerror', (error) => errors.push(error.message));
  page.on('framenavigated', (frame) => { if (frame === page.mainFrame()) navigation.push(frame.url()); });
  await context.addCookies([{ name: 'accessToken', value: 'demo-local-fixture', url: base }, { name: 'user', value: encodeURIComponent(JSON.stringify(user)), url: base }]);
  await context.route('**/*', async (route) => {
    const url = new URL(route.request().url());
    if (url.origin !== base && !url.pathname.includes('/api/v1/')) return route.abort();
    if (!url.pathname.includes('/api/v1/')) return route.continue();
    const path = url.pathname.split('/api/v1')[1], params = url.searchParams;
    requests.push({ path, method: route.request().method(), params: Object.fromEntries(params),
      body: route.request().method() === 'POST' ? route.request().postDataJSON() : null });
    let data;
    if (route.request().method() === 'OPTIONS') return route.fulfill({ status: 204, headers: { 'Access-Control-Allow-Origin': base, 'Access-Control-Allow-Headers': '*', 'Access-Control-Allow-Methods': '*' } });
    if (path === '/users/me') data = user;
    else if (path === '/hr/overview') data = { totalEmployees: 65, activeEmployees: 43, draftEmployees: 22, inactiveEmployees: 0, probationEmployees: probationCount };
    else if (path.startsWith('/hr/catalogs/')) data = paged(path.endsWith('/departments') ? [unit] : path.endsWith('/positions') ? [position] : [], params);
    else if (path === '/hr/employees') {
      let filtered = rows.filter((r) => !params.get('status') || r.employmentStatus === params.get('status'));
      if (params.get('keyword')) filtered = filtered.filter((r) => `${r.employeeCode} ${r.fullName}`.toLowerCase().includes(params.get('keyword').toLowerCase()));
      const [field, direction] = (params.get('sort') || 'employeeCode,asc').split(',');
      filtered.sort((a, b) => String(a[field]).localeCompare(String(b[field])) * (direction === 'asc' ? 1 : -1));
      data = paged(filtered, params);
    } else if (/^\/hr\/employees\/[^/]+$/.test(path)) {
      const row = rows.find((r) => r.id === path.split('/').at(-1));
      if (route.request().method() === 'PATCH') {
        const payload = route.request().postDataJSON();
        row.fullName = payload.personal.fullName;
        row.rowVersion++;
      }
      data = row && detail(row);
    } else if (path.endsWith('/documents')) data = [];
    else if (path.includes('/employees/') && path.includes('/leave')) data = { calculatedDays: 12, finalDays: 12, year: 2026 };
    else if (path === '/hr/probation/candidates') data = paged(candidates.filter((c) => !params.get('status') || c.status === params.get('status')), params);
    else if (path.startsWith('/hr/probation/candidates/')) data = candidates.find((c) => c.id === path.split('/').at(-1));
    else if (path === '/hr/probation/job-templates') data = paged(templates, params);
    else if (path.startsWith('/hr/probation/job-templates/')) data = templates[0];
    else if (path === '/hr/rosters') data = paged(rosters, params);
    else if (path === '/hr/rosters/reconciliation') data = { activeEmployeeCount: 65, rosterEmployeeCount: 65, missingEmployees: [], unexpectedEmployees: [] };
    else if (/^\/hr\/rosters\/[^/]+$/.test(path)) data = rosters.find((r) => r.id === path.split('/').at(-1));
    else if (path.startsWith('/hr/rosters/') && path.endsWith('/items')) data = paged(rows.map((r, i) => ({ ...r, displayOrder: i + 1, leaveDays: 12 })), params);
    else if (path === '/hr/movements' && route.request().method() === 'POST') {
      const payload = route.request().postDataJSON();
      const row = rows.find((r) => r.id === payload.employeeId);
      data = { ...moves[0], ...payload, id: 'movement-created', employeeCode: row.employeeCode, employeeName: row.fullName, status: 'DRAFT' };
      moves.push(data);
    } else if (path === '/hr/movements') {
      let filtered = moves.filter((m) => (!params.get('keyword') || `${m.employeeCode} ${m.employeeName}`.toLowerCase().includes(params.get('keyword').toLowerCase())) && (!params.get('type') || m.movementType === params.get('type')));
      const [field, direction] = (params.get('sort') || 'effectiveDate,desc').split(',');
      filtered.sort((a, b) => String(a[field] || '').localeCompare(String(b[field] || '')) * (direction === 'asc' ? 1 : -1));
      data = paged(filtered, params);
    } else data = paged([], params);
    return route.fulfill({ status: 200, contentType: 'application/json', headers: { 'Access-Control-Allow-Origin': base }, body: JSON.stringify({ status: 200, data }) });
  });
  const main = page.locator('main');
  const click = (name, exact = true) => main.getByRole('button', { name, exact }).filter({ visible: true }).first().click();
  const loadedList = async () => page.waitForFunction(() => /kết quả.*Trang/.test(document.querySelector('main')?.innerText || ''));
  try {
    await page.goto(`${base}/manager/hr`);
    await main.getByText('Đang thử việc', { exact: true }).waitFor();
    const trial = main.getByRole('link').filter({ hasText: 'Đang thử việc' });
    ok((await trial.innerText()).includes('7'), 'Dashboard thử việc = API count 7');
    await trial.click();
    await loadedList();
    ok(await main.getByRole('combobox').first().inputValue() === 'IN_PROBATION', 'Dashboard opens applied IN_PROBATION filter');
    ok((await main.innerText()).includes('7 kết quả'), 'Probation count and destination list agree');

    const employeeUrl = `${base}/manager/hr/employees?keyword=DEMO&sort=fullName%2Cdesc&page=1`;
    await page.goto(employeeUrl); await loadedList();
    await main.evaluate((el) => { el.scrollTop = 430; });
    await page.waitForTimeout(150);
    await click('Chi tiết');
    await main.getByRole('heading', { name: /Nhân sự giả lập/ }).waitFor();
    await click('Lịch sử');
    const historyUrl = page.url();
    await click('Chỉnh sửa thông tin');
    await main.getByRole('button', { name: 'Quay lại', exact: true }).waitFor();
    await click('Quay lại');
    await main.getByRole('heading', { name: /Nhân sự giả lập/ }).waitFor();
    ok(page.url() === historyUrl, 'Edit cancel restores employee detail tab and origin');
    await click('Danh sách'); await loadedList();
    ok(new URL(page.url()).searchParams.get('page') === '1' && new URL(page.url()).searchParams.get('sort') === 'fullName,desc', 'List button restores employee filters, sort and page');
    await page.waitForTimeout(120);
    ok(await main.evaluate((el) => el.scrollTop) > 250, 'Employee list restores scroll position');
    await page.goBack();
    await main.getByRole('heading', { name: /Nhân sự giả lập/ }).waitFor();
    await page.goForward(); await loadedList();
    ok(new URL(page.url()).searchParams.get('keyword') === 'DEMO', 'Browser Back/Forward keeps applied filters');
    await page.reload(); await loadedList();
    ok(new URL(page.url()).searchParams.get('page') === '1', 'Employee list reload keeps page');
    await page.waitForTimeout(120);
    ok(await main.evaluate((el) => el.scrollTop) > 250, 'Employee list reload restores saved scroll positions');
    await click('Chi tiết');
    await main.getByRole('heading', { name: /Nhân sự giả lập/ }).waitFor();
    await click('Chỉnh sửa thông tin');
    await main.getByRole('button', { name: 'Quay lại', exact: true }).waitFor();
    await click('Lưu thay đổi');
    await main.getByRole('heading', { name: /Nhân sự giả lập/ }).waitFor();
    await click('Danh sách'); await loadedList();
    ok(new URL(page.url()).searchParams.get('page') === '1', 'Employee save preserves original list paging');

    await page.goto(`${base}/manager/hr/employees?page=99&keyword=DEMO`);
    await page.waitForURL((url) => url.searchParams.get('page') === '3'); await loadedList();
    ok((await main.innerText()).includes('65 kết quả'), 'Out-of-range page moves to the last available page with fresh results');
    await page.goto(`${base}/manager/hr/employees/demo-1`);
    await main.getByRole('heading', { name: /Nhân sự giả lập/ }).waitFor();
    await click('Danh sách'); await loadedList();
    ok(new URL(page.url()).pathname === '/manager/hr/employees', 'Direct detail has a safe list fallback');

    await page.goto(`${base}/manager/hr/general-labor?keyword=DEMO&page=1&status=ACTIVE`); await loadedList();
    await click('Chi tiết'); await main.getByRole('heading', { name: /Nhân sự giả lập/ }).waitFor();
    await click('Danh sách'); await loadedList();
    ok(new URL(page.url()).pathname.endsWith('/general-labor') && new URL(page.url()).searchParams.get('status') === 'ACTIVE', 'General-labor detail returns to filtered general-labor list');
    await click('Thêm lao động phổ thông');
    await main.getByRole('heading', { name: /lao động phổ thông/i }).waitFor();
    await click('Quay lại'); await loadedList();
    ok(new URL(page.url()).searchParams.get('page') === '1', 'General-labor onboarding cancel restores page');

    await page.goto(`${base}/manager/hr/probation?status=CONVERTED&page=1&sort=fullName,asc`); await loadedList();
    await click('Sửa'); await main.getByRole('heading', { name: /thử việc/i }).waitFor();
    await click('Quay lại danh sách'); await loadedList();
    ok(new URL(page.url()).searchParams.get('page') === '1', 'Candidate edit cancel restores filters and page');
    await click('Xem hồ sơ nhân sự'); await main.getByRole('heading', { name: /Nhân sự giả lập/ }).waitFor();
    await click('Danh sách'); await loadedList();
    ok(new URL(page.url()).pathname.endsWith('/probation'), 'Converted candidate opens linked employee and returns to probation');
    await click('Mẫu công việc 1');
    await main.getByText('Mẫu giả lập', { exact: true }).filter({ visible: true }).waitFor();
    await click('Sửa'); await main.getByRole('heading', { name: /mẫu công việc/i }).waitFor();
    await click('Quay lại danh sách mẫu'); await main.getByText('Mẫu giả lập', { exact: true }).filter({ visible: true }).waitFor();
    ok(new URL(page.url()).searchParams.get('tab') === 'templates', 'Job-template cancel returns to selected tab');

    await page.goto(`${base}/manager/hr/rosters?page=1&year=2025`); await loadedList();
    await click('Xem danh sách');
    await page.waitForURL((url) => /\/rosters\/[^/]+$/.test(url.pathname));
    await main.getByRole('button', { name: 'Danh sách tháng', exact: true }).waitFor(); await loadedList();
    await main.getByRole('button', { name: 'Đến trang 2' }).click(); await loadedList();
    await click('Danh sách tháng'); await loadedList();
    ok(new URL(page.url()).searchParams.get('page') === '1' && new URL(page.url()).searchParams.get('year') === '2025', 'Roster detail returns to page and export year');

    await page.goto(`${base}/manager/hr/movements?keyword=DEMO&sort=employeeCode,desc&page=1`); await loadedList();
    ok(requests.some((r) => r.path === '/hr/movements' && r.params.page === '1' && r.params.sort === 'employeeCode,desc'), 'Movement API receives global sort and page');
    await main.getByRole('button', { name: /Nhân sự giả lập/ }).first().click(); await main.getByRole('heading', { name: /Nhân sự giả lập/ }).waitFor();
    await click('Danh sách'); await loadedList();
    ok(new URL(page.url()).pathname.endsWith('/movements') && new URL(page.url()).searchParams.get('page') === '1', 'Movement employee detail returns to filtered movement page');
    await main.getByRole('button', { name: /Người xử lý/ }).click(); await loadedList();
    ok(new URL(page.url()).searchParams.get('sort') === 'processedBy,asc' && !new URL(page.url()).searchParams.has('page'), 'Actor heading applies server sort and resets page');
    await click('Tạo Tăng/Giảm');
    const number = page.getByLabel('Số đầu chứng từ');
    await number.fill('50');
    await page.getByLabel('Phần đuôi chứng từ').fill('/HĐLĐ-DEMO/2027');
    ok((await main.innerText()).includes('Xem trước: 50/HĐLĐ-DEMO/2027'), 'Document number and editable suffix/year preview agree');
    page.once('dialog', (dialog) => dialog.accept());
    await page.getByRole('button', { name: 'Giảm nhân sự', exact: true }).click();
    ok(await page.getByLabel('Số chứng từ đầy đủ').inputValue() === '', 'Decrease defaults to blank after confirmed type switch');
    await page.getByLabel('Số chứng từ đầy đủ').fill('88/QĐ-DEMO/2026');
    ok((await main.innerText()).includes('88/QĐ-DEMO/2026'), 'Decrease accepts custom full document number');
    await page.getByRole('button', { name: 'Hủy', exact: true }).filter({ visible: true }).click();

    await click('Tạo Tăng/Giảm');
    await page.locator('#movement-employee-search').fill('DEMO000');
    await page.getByRole('button', { name: 'Tìm', exact: true }).click();
    await page.getByRole('button', { name: /Nhân sự giả lập 0.*DEMO000/ }).filter({ visible: true }).click();
    await page.getByLabel('Số đầu chứng từ').fill('51');
    await page.getByLabel('Phần đuôi chứng từ').fill('/HĐLĐ-DEMO/2027');
    await page.getByRole('button', { name: 'Lưu chờ xác nhận', exact: true }).click();
    await page.getByRole('heading', { name: 'Biến động nhân sự mới', exact: true }).waitFor({ state: 'hidden' });
    ok(requests.some((r) => r.path === '/hr/movements' && r.method === 'POST' && r.body.decisionNumber === '51/HĐLĐ-DEMO/2027'), 'Create submits the full edited document number to API');
    await loadedList();

    user.role = 'ADMIN'; probationCount = 0;
    await page.goto(`${base}/manager/hr`); await main.getByText('Đang thử việc', { exact: true }).waitFor();
    ok((await main.getByRole('link').filter({ hasText: 'Đang thử việc' }).innerText()).includes('0'), 'ADMIN sees a dynamic zero probation count');
    probationCount = 7;

    await page.setViewportSize({ width: 390, height: 844 });
    await page.goto(`${base}/manager/hr/movements?sort=employeeCode,desc&page=1`); await loadedList();
    ok(await main.getByLabel('Sắp xếp toàn bộ dữ liệu').isVisible(), 'Mobile exposes server sorting without hidden table headings');
    ok(await page.evaluate(() => document.documentElement.scrollWidth <= window.innerWidth + 1), 'Mobile has no horizontal document overflow');
    user.role = 'HR_STAFF';
    await context.addCookies([{ name: 'user', value: encodeURIComponent(JSON.stringify({ ...user, role: 'HR_STAFF' })), url: base }]);
    await page.goto(`${base}/manager/hr`); await main.getByText('Đang thử việc', { exact: true }).waitFor();
    ok(await main.getByRole('link').filter({ hasText: 'Đang thử việc' }).count() === 0, 'HR_STAFF sees count without unauthorized probation link');
    ok(errors.length === 0, `No browser runtime errors: ${errors.join('; ')}`);
    return { passed: results.length, results, fixtureOnly: true, requestCount: requests.length };
  } catch (error) {
    return { passed: results.length, results, failure: error.message, url: page.url(), navigation: navigation.slice(-10), visibleText: (await main.innerText()).slice(-5000), errors };
  } finally {
    await context.close();
  }
}
