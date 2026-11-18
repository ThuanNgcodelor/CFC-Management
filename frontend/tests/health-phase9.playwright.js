// eslint-disable-next-line no-unused-expressions -- MCP entry point for the isolated HTTP preview.
async (page) => {
  const root = 'http://127.0.0.1:18086/api/v1';
  const admin = { Authorization: 'Bearer ADMIN' };
  const errors = [];
  page.on('pageerror', error => errors.push(error.message));
  const passed = [];
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto('http://127.0.0.1:5196/tests/health-phase9.html?tab=records');
  const search = page.getByRole('textbox', { name: 'Tìm hồ sơ sức khỏe', exact: true });
  await search.fill('HEALTHDEV01');
  await page.waitForURL(/recordSearch=HEALTHDEV01/);
  await page.getByRole('button', { name: 'Mở sổ sức khỏe', exact: true }).first().waitFor();
  await page.getByLabel('Sắp xếp hồ sơ sức khỏe').selectOption('nameDesc');
  await page.waitForURL(/recordSort=nameDesc/);
  await page.getByRole('button', { name: 'Mở sổ sức khỏe', exact: true }).first().click();
  await page.getByRole('checkbox', { name: 'Chọn lượt QA-2024-HEALTHDEV01', exact: true }).waitFor();
  for (const year of [2024, 2025, 2026]) {
    await page.getByRole('checkbox', { name: `Chọn lượt QA-${year}-HEALTHDEV01`, exact: true }).click();
    await page.locator(`input[aria-label="Chọn lượt QA-${year}-HEALTHDEV01"]:checked`).waitFor();
  }
  await page.getByRole('button', { name: 'So sánh 3 lượt đã chọn', exact: true }).click();
  await page.getByText('Chênh lệch cuối − đầu: 4', { exact: true }).waitFor();
  if (await page.getByRole('cell', { name: 'Không có dữ liệu', exact: true }).count() !== 2) throw new Error('Missing optional historical tests were not kept separate');
  await page.getByRole('button', { name: 'Xem xu hướng số học', exact: true }).click();
  await page.getByRole('img', { name: 'Xu hướng AST giả lập', exact: true }).waitFor();
  passed.push('real API: 2024–2026 comparison, optional missing cells, neutral numeric chart');
  await page.screenshot({ path: '/tmp/health-phase9-real-desktop.png', fullPage: true });
  await page.setViewportSize({ width: 390, height: 844 });
  if (await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)) throw new Error('Mobile horizontal overflow');
  await page.screenshot({ path: '/tmp/health-phase9-real-mobile.png', fullPage: true });
  passed.push('real shell and responsive 390px');
  await page.keyboard.press('Escape');
  await page.getByRole('button', { name: 'Quay lại danh sách hồ sơ', exact: true }).click();
  if (await search.inputValue() !== 'HEALTHDEV01' || await page.getByLabel('Sắp xếp hồ sơ sức khỏe').inputValue() !== 'nameDesc') throw new Error('Return lost record filters');
  await page.goBack();
  await page.getByRole('button', { name: 'Quay lại danh sách hồ sơ', exact: true }).waitFor();
  await page.goForward();
  await search.waitFor();
  passed.push('real URL filters and browser Back/Forward');
  const response = await page.request.get(`${root}/hr/health/records?keyword=HEALTHDEV&data=WITH_DATA&sort=codeAsc&size=20`, { headers: admin });
  const people = (await response.json()).data.content;
  const own = people.find(p => p.code === 'HEALTHDEV01');
  const other = people.find(p => p.code === 'HEALTHDEV02');
  const noHistory = (await (await page.request.get(`${root}/hr/health/records/${other.employeeId}/timeline?fromYear=2024&toYear=2026`, { headers: admin })).json()).data;
  if (noHistory.years.map(y => y.visits).join(',') !== '0,0,1') throw new Error('Newcomer was given invented history');
  passed.push('real API: newcomer has no fabricated 2024/2025 results');
  const report = { fromYear: 2024, toYear: 2026, keyword: 'HEALTHDEV', data: 'ALL', sort: 'codeAsc' };
  const excel = await page.request.post(`${root}/hr/health/reports/excel`, { headers: admin, data: report });
  if (excel.status() !== 200 || (await excel.body()).subarray(0, 2).toString() !== 'PK') throw new Error('Collective Excel failed');
  const pdf = await page.request.post(`${root}/hr/health/reports/pdf`, { headers: admin, data: { ...report, keyword: null, employeeId: own.employeeId } });
  if (pdf.status() !== 200 || (await pdf.body()).subarray(0, 4).toString() !== '%PDF') throw new Error('Personal PDF failed');
  passed.push('real API: collective Excel and personal PDF for 3 years');
  for (const role of ['MANAGER', 'HR_STAFF']) {
    const check = await page.request.get(`${root}/hr/health/records?keyword=HEALTHDEV&data=WITH_DATA&sort=codeAsc`, { headers: { Authorization: `Bearer ${role}` } });
    if (check.status() !== (role === 'MANAGER' ? 200 : 403)) throw new Error(`Unexpected access for ${role}`);
  }
  if ((await page.request.get(`${root}/hr/health/records`)).status() !== 401) throw new Error('Guest can browse health records');
  passed.push('real API: ADMIN/MANAGER allowed, HR_STAFF denied, guest denied');
  const campaigns = (await (await page.request.get(`${root}/hr/health/campaigns`, { headers: admin })).json()).data.content;
  const campaign = campaigns.find(c => c.reportYear === 2026);
  const batches = (await (await page.request.get(`${root}/hr/health/imports?campaignId=${campaign.id}`, { headers: admin })).json()).data.content;
  const pending = batches.filter(b => b.pendingRows > 0);
  let conflict = false, invalid = 0;
  for (const batch of pending) {
    const rows = (await (await page.request.get(`${root}/hr/health/imports/${batch.id}/rows`, { headers: admin })).json()).data.content;
    conflict ||= rows.some(r => r.cells.some(c => c.action === 'CONFLICT'));
    invalid += rows.filter(r => r.errors.length > 0).length;
  }
  if (!conflict || invalid !== 2) throw new Error('Synthetic late correction/errors missing');
  passed.push('real API: pending conflict and 2 invalid source rows remain unconfirmed');
  const grant = (await (await page.request.get(`${root}/hr/health/personal-access/${own.employeeId}`, { headers: admin })).json()).data;
  const issued = (await (await page.request.post(`${root}/hr/health/personal-access/${own.employeeId}/issue`, { headers: admin, data: { generation: grant.generation, reason: 'MCP kiểm thử dữ liệu giả' } })).json()).data;
  await page.goto('http://127.0.0.1:5196/health-lookup');
  await page.getByRole('radio', { name: 'Dùng mã tra cứu riêng', exact: true }).check();
  await page.getByLabel('Mã tra cứu riêng', { exact: true }).fill(issued.code);
  await page.getByRole('button', { name: 'Tra cứu kết quả của tôi', exact: true }).click();
  await page.getByRole('checkbox').first().waitFor();
  if (await page.getByRole('checkbox').count() !== 3) throw new Error('Self profile missing published history');
  await page.getByRole('button', { name: 'Xem kết quả 2026', exact: true }).click();
  await page.getByRole('heading', { name: 'AST giả lập', exact: true }).waitFor();
  const download = page.waitForEvent('download');
  await page.getByRole('button', { name: 'Tải phiếu PDF của tôi', exact: true }).click();
  if (await (await download).failure()) throw new Error('Self PDF download failed');
  await page.keyboard.press('Escape');
  await page.getByRole('button', { name: 'Thoát tra cứu', exact: true }).click();
  passed.push('real public UI: code alone, published 3-year history, PDF download, logout');
  if (await page.evaluate(() => Object.keys(localStorage).some(key => /health.*session/i.test(key)))) throw new Error('Private session persisted');
  if (errors.length) throw new Error(errors.join('; '));
  return { passed, errors, screenshots: ['/tmp/health-phase9-real-desktop.png', '/tmp/health-phase9-real-mobile.png'] };
}
