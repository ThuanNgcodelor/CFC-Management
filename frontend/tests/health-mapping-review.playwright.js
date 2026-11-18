// eslint-disable-next-line no-unused-expressions -- MCP UI regression for columns on later mapping pages.
async (page) => {
  await page.goto('http://127.0.0.1:5195/tests/health-phase2.html');
  await page.getByRole('tab', { name: 'Nhập dữ liệu', exact: true }).click();
  await page.getByRole('button', { name: 'Chọn đợt khám', exact: true }).click();
  await page.getByRole('button', { name: /KSK_DEV — Đợt khám giả lập/ }).click();
  await page.evaluate(async () => {
    const { healthImportApi } = await import('/src/api/healthImportApi.js');
    const original = healthImportApi.inspect;
    healthImportApi.inspect = async (...args) => {
      const data = await original(...args);
      const columns = data.sheets[0].columns;
      for (let index = 5; index < 26; index += 1) columns.push({ index, letter: String.fromCharCode(65 + index), name: index === 25 ? 'Chỉ tiêu bệnh viện mới' : 'Ghi chú', suggested: index === 23 ? 'RESULT' : 'IGNORE' });
      return data;
    };
  });
  await page.getByLabel('File kết quả bệnh viện').setInputFiles({ name: 'new-layout.xlsx', mimeType: 'application/vnd.openxmlformats-officedocument.spreadsheetml.sheet', buffer: Buffer.from('UI contract fixture only') });
  await page.getByText('26 cột · 2 cột chưa chọn cách xử lý', { exact: true }).waitFor();
  await page.getByText('Còn 1 cột kết quả/tham chiếu chưa chọn chỉ tiêu.', { exact: true }).waitFor();
  await page.getByLabel('Trường đích cột E').selectOption('IGNORE');
  await page.getByRole('button', { name: 'Kiểm tra cột Z · Chỉ tiêu bệnh viện mới', exact: true }).click();
  await page.getByLabel('Trường đích cột Z').selectOption('IGNORE');
  await page.getByLabel('Mã nguồn bệnh viện').fill('QA');
  await page.getByLabel('Tôi đã kiểm tra sheet').check();
  if (await page.getByRole('button', { name: 'Tạo lô và xem trước' }).isEnabled()) throw new Error('Missing indicator on later page must block staging');
  await page.getByLabel('Trường đích cột X').selectOption('IGNORE');
  if (await page.getByLabel('Tôi đã kiểm tra sheet').isChecked()) throw new Error('Mapping change must clear acknowledgement');
  await page.getByLabel('Tôi đã kiểm tra sheet').check();
  if (!(await page.getByRole('button', { name: 'Tạo lô và xem trước' }).isEnabled())) throw new Error('Reviewed mapping remains blocked');
  await page.getByText(/Xem \d+ cột sẽ không nhập/).click();
  await page.getByRole('complementary', { name: 'Kiểm tra ánh xạ trước khi nhập' }).getByText('Z · Chỉ tiêu bệnh viện mới', { exact: true }).waitFor();
  await page.setViewportSize({ width: 390, height: 844 });
  if (await page.evaluate(() => document.documentElement.scrollWidth > innerWidth)) throw new Error('Mapping review overflows mobile');
  await page.screenshot({ path: '/tmp/health-mapping-review-mobile.png', fullPage: true });
  return { passed: ['unresolved column beyond first page', 'jump to source column', 'missing indicator blocks submit', 'mapping invalidates acknowledgement', 'ignored columns visible', '390px responsive'] };
}
