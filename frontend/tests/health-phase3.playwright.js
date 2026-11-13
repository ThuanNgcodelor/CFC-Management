// eslint-disable-next-line no-unused-expressions -- MCP executes this isolated UI fixture.
async (page) => {
  const errors = []; page.on('pageerror', (error) => errors.push(error.message));
  await page.goto('http://127.0.0.1:5195/tests/health-phase2.html');
  await page.evaluate(() => { window.healthImportTest.classificationMode = true; });
  await page.getByRole('tab', { name: 'Nhập dữ liệu' }).click();
  await page.getByRole('button', { name: 'Chọn đợt khám', exact: true }).click();
  await page.getByRole('button', { name: /KSK_DEV — Đợt khám giả lập/ }).click();
  await page.getByLabel('File kết quả bệnh viện').setInputFiles({ name: 'classification-fixture.xlsx', mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', buffer: Buffer.from('UI fixture only') });
  await page.getByLabel('Trường đích cột E').selectOption('IGNORE');
  await page.getByLabel('Mã nguồn bệnh viện').fill('DEV_HOSPITAL');
  await page.getByLabel('Ngày khám chung').fill('2026-01-01');
  await page.getByLabel('Tôi đã kiểm tra sheet').check();
  await page.getByRole('button', { name: 'Tạo lô và xem trước' }).click();
  const checkbox = page.getByRole('checkbox', { name: 'Chọn dòng 2', exact: true });
  if (await checkbox.isEnabled()) throw new Error('Invalid classification must block confirmation');
  await page.getByRole('button', { name: 'Xem đối chiếu', exact: true }).first().click();
  await page.getByLabel('Phân loại đính chính').selectOption('II');
  const save = page.getByRole('button', { name: 'Lưu đính chính phân loại', exact: true });
  if (await save.isEnabled()) throw new Error('Classification correction requires a reason');
  await page.getByLabel('Lý do đính chính').fill('Đã kiểm tra nguồn giả lập');
  await save.click();
  await checkbox.check();
  await page.getByRole('button', { name: 'Xem đối chiếu', exact: true }).first().click();
  await page.getByText('Nội dung sai ô giả lập', { exact: false }).waitFor();
  await page.getByText('Đã kiểm tra nguồn giả lập', { exact: false }).waitFor();
  await page.keyboard.press('Escape');
  await page.getByRole('button', { name: 'Xác nhận 1 dòng', exact: true }).click();
  await page.getByRole('status').getByText('Đã nhập 1 dòng;', { exact: false }).waitFor();
  await page.getByLabel('Lọc trạng thái dòng nhập').selectOption('PENDING');
  await page.getByRole('checkbox', { name: 'Chọn dòng 3', exact: true }).waitFor();
  if (await page.getByRole('checkbox', { name: 'Chọn dòng 2', exact: true }).count()) throw new Error('Pending queue must omit imported rows');
  await page.getByLabel('Lọc trạng thái dòng nhập').selectOption('ALL');
  await page.getByRole('checkbox', { name: 'Chọn dòng 2', exact: true }).waitFor();
  if (errors.length) throw new Error('Browser errors: ' + errors.join('; '));
  return { passed: ['invalid classification blocks confirmation', 'reason required', 'source and correction history preserved', 'confirmation after correction', 'pending filter keeps imported rows'] };
}
