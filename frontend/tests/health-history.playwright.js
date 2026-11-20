// eslint-disable-next-line no-unused-expressions -- MCP exercises the real history API against a synthetic in-memory database.
async (page) => {
  const errors = []; page.on('pageerror', error => errors.push(error.message));
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto('http://127.0.0.1:5198/tests/health-history.html?tab=imports');
  await page.getByRole('heading', { name: 'Dữ liệu lịch sử 2023–2025', exact: true }).waitFor();
  const activate = page.getByRole('button', { name: 'Kích hoạt và tự nhập lịch sử', exact: true });
  if (await activate.isEnabled()) throw new Error('Cannot activate without a key file');
  await page.getByLabel('File khóa dữ liệu lịch sử').setInputFiles({ name: 'wrong.key', mimeType: 'application/octet-stream', buffer: Buffer.alloc(32, 1) });
  await activate.click();await page.getByText('Khóa không phù hợp hoặc gói dữ liệu lịch sử không hợp lệ.', { exact: true }).waitFor();
  const headers = { Authorization: 'Bearer ADMIN' }, root = 'http://127.0.0.1:18088/api/v1/hr/health/history-import';
  const locked = (await (await page.request.get(root, { headers })).json()).data;
  if (locked.state !== 'LOCKED' || locked.imported !== 0) throw new Error('Invalid key wrote data');
  await page.getByLabel('File khóa dữ liệu lịch sử').setInputFiles({ name: 'fake-history.key', mimeType: 'application/octet-stream', buffer: Buffer.alloc(32) });
  await activate.click();
  await page.getByText('Đã nhập 1 dòng · Còn chờ rà soát 0 dòng nguồn.', { exact: true }).waitFor();
  if (await page.getByLabel('File khóa dữ liệu lịch sử').count()) throw new Error('Activation retained the key picker');
  await page.reload();await page.getByText('Đã nhập 1 dòng · Còn chờ rà soát 0 dòng nguồn.', { exact: true }).waitFor();
  await page.getByRole('button', { name: 'Kiểm tra và tiếp tục nhập các dòng đủ điều kiện', exact: true }).click();
  await page.getByRole('button', { name: 'Mở lô rà soát', exact: true }).waitFor();
  await page.getByRole('button', { name: 'Mở lô rà soát', exact: true }).click();
  await page.getByRole('heading', { name: '2. Rà soát và xác nhận lưu', exact: true }).waitFor();
  await page.getByText('Đã nhập 1 · Còn chờ 0', { exact: false }).first().waitFor();
  await page.setViewportSize({ width: 390, height: 844 });
  if (await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)) throw new Error('History panel overflows mobile');
  await page.screenshot({ path: '/tmp/health-history-auto-mobile.png', fullPage: true });
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.screenshot({ path: '/tmp/health-history-auto-desktop.png', fullPage: true });
  const state = (await (await page.request.get(root, { headers })).json()).data;
  if (state.imported !== 1 || state.batches.length !== 1) throw new Error('Retry created duplicate history');
  const forbidden = await page.request.get(root, { headers: { Authorization: 'Bearer HR_STAFF' } });
  if (forbidden.status() !== 403) throw new Error('HR_STAFF can access packaged history');
  if (JSON.stringify(state).includes('secret') || JSON.stringify(state).includes('keyFile')) throw new Error('History status leaks private key metadata');
  if (errors.length) throw new Error(errors.join('; '));
  return { passed: ['key required', 'wrong key rejected without writes', 'one-time activation imports real API data', 'reload retains server state', 'retry idempotent', 'open batch without upload or mapping', '390px responsive', 'HR_STAFF forbidden', 'no key returned in status'], errors };
}
