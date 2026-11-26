// eslint-disable-next-line no-unused-expressions -- MCP fixture regression, never production.
async page => {
  const errors = []; page.on('pageerror', error => errors.push(error.message));
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.goto('http://127.0.0.1:5199/tests/payroll-leadership.html');
  await page.getByRole('button', { name: 'Nhập file lương lãnh đạo', exact: true }).click();
  if (await page.getByRole('button', { name: 'Import & xem trước', exact: true }).isEnabled()) throw new Error('Empty upload allowed');
  await page.getByLabel('File lương Excel').setInputFiles({ name: 'fixture.xlsx', mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', buffer: Buffer.from('fixture only, API mocked') });
  await page.getByRole('button', { name: 'Import & xem trước', exact: true }).click();
  await page.getByText('Lãnh đạo · Kỳ lương 10/2026', { exact: true }).waitFor();
  const bad = page.locator('tr').filter({ hasText: 'Dòng lỗi giả lập' });
  if (await bad.getByRole('button', { name: 'Gửi thử' }).isEnabled()) throw new Error('Invalid row can send');
  if (await bad.getByRole('checkbox').isEnabled()) throw new Error('Invalid row selectable');
  const good = page.locator('tr').filter({ hasText: 'Người giả lập' });
  await good.getByText('Xem số liệu lương lãnh đạo').click();
  await good.getByText('10.000.000', { exact: true }).waitFor();
  await page.getByRole('button', { name: 'Lỗi dữ liệu', exact: true }).click();
  if (await page.getByText('Người giả lập', { exact: true }).count()) throw new Error('Invalid filter ignored');
  const state = await page.evaluate(() => window.payrollFixture);
  if (state.uploads[0].payrollType !== 'LEADERSHIP' || state.sends) throw new Error('Wrong upload type or unintended send');
  await page.setViewportSize({ width: 390, height: 844 });
  if (await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)) throw new Error('Mobile overflow');
  await page.screenshot({ path: '/tmp/payroll-leadership-mobile.png', fullPage: true });
  await page.setViewportSize({ width: 1440, height: 1000 });
  await page.screenshot({ path: '/tmp/payroll-leadership-desktop.png', fullPage: true });
  if (errors.length) throw new Error(errors.join('; '));
  return { passed: 7, fixtureOnly: true, errors };
}
