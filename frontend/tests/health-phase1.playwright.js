// eslint-disable-next-line no-unused-expressions -- MCP executes this function expression as its entry point.
async (page) => {
  await page.goto('http://127.0.0.1:5195/tests/health-phase1.html');
  await page.getByText('Chưa có đợt khám phù hợp').waitFor();
  await page.getByRole('button', { name: 'Thêm đợt khám' }).click();
  await page.getByLabel('Mã', { exact: true }).fill('KSK_DEV');
  await page.getByLabel('Tên', { exact: true }).fill('Đợt khám giả lập');
  await page.getByLabel('Cơ sở khám', { exact: true }).fill('Cơ sở thử nghiệm');
  await page.getByRole('button', { name: 'Lưu', exact: true }).click();
  await page.getByRole('cell', { name: 'KSK_DEV', exact: true }).waitFor();
  await page.getByRole('button', { name: 'Sửa', exact: true }).click();
  if (!(await page.getByLabel('Mã', { exact: true }).isDisabled())) throw new Error('Stable code must be disabled');
  await page.getByLabel('Tên', { exact: true }).fill('Đợt khám đã sửa');
  await page.evaluate(() => { window.healthTest.failSave = true; });
  await page.getByRole('button', { name: 'Lưu', exact: true }).click();
  await page.getByText('Dữ liệu đã thay đổi. Vui lòng tải lại trước khi lưu.').waitFor();
  if (!(await page.getByLabel('Tên', { exact: true }).inputValue()).includes('đã sửa')) throw new Error('Conflict lost draft');
  await page.evaluate(() => { window.healthTest.failSave = false; });
  await page.getByRole('button', { name: 'Lưu', exact: true }).click();
  await page.getByRole('cell', { name: 'Đợt khám đã sửa', exact: true }).waitFor();
  await page.getByRole('tab', { name: 'Danh mục chỉ tiêu' }).click();
  if (await page.getByRole('tab', { name: 'Danh mục chỉ tiêu' }).evaluate((element) => {
    const style = getComputedStyle(element); return style.color === style.backgroundColor;
  })) throw new Error('Selected tab text must remain readable');
  await page.getByRole('button', { name: 'Thêm chỉ tiêu' }).click();
  await page.getByLabel('Mã', { exact: true }).fill('AST_DEV');
  await page.getByLabel('Tên', { exact: true }).fill('Chỉ tiêu giả lập');
  await page.getByLabel('Nhóm chỉ tiêu', { exact: true }).fill('Sinh hóa');
  await page.getByLabel('Tên cột tương đương', { exact: false }).fill('Tên máy A\nTên máy B\n');
  await page.getByRole('button', { name: 'Lưu', exact: true }).click();
  await page.getByRole('cell', { name: 'AST_DEV', exact: true }).waitFor();
  const aliases = await page.evaluate(() => window.healthTest.rows.indicators[0].aliases);
  if (aliases.length !== 2) throw new Error('Trailing alias line must be omitted');
  await page.evaluate(() => { window.healthTest.calls = []; });
  await page.getByRole('textbox', { name: 'Tìm mã hoặc tên' }).pressSequentially('AST_DEV', { delay: 40 });
  await page.waitForTimeout(500);
  const calls = await page.evaluate(() => window.healthTest.calls);
  if (calls.length !== 1 || calls[0].keyword !== 'AST_DEV') throw new Error('Search is not debounced: ' + JSON.stringify(calls));
  await page.getByRole('textbox', { name: 'Tìm mã hoặc tên' }).fill('OLD');
  await page.waitForTimeout(380);
  await page.getByRole('textbox', { name: 'Tìm mã hoặc tên' }).fill('AST_DEV');
  await page.waitForTimeout(1000);
  await page.getByRole('cell', { name: 'AST_DEV', exact: true }).waitFor();
  if ((await page.getByRole('textbox', { name: 'Tìm mã hoặc tên' }).inputValue()) !== 'AST_DEV') throw new Error('Old response rewound search');
  await page.setViewportSize({ width: 390, height: 844 });
  if (await page.evaluate(() => document.documentElement.scrollWidth > window.innerWidth)) throw new Error('Mobile page overflow');
  return { passed: ['create campaign', 'edit campaign', 'conflict preserves draft', 'create indicator and aliases', 'debounce', 'stale request cancellation', '390px responsive'] };
}
